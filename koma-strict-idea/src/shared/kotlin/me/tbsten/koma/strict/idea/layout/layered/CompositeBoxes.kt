package me.tbsten.koma.strict.idea.layout.layered

import me.tbsten.koma.strict.idea.ir.CompositeBox
import me.tbsten.koma.strict.idea.ir.NodeId
import me.tbsten.koma.strict.idea.layout.LayoutConfig
import me.tbsten.koma.strict.idea.layout.LayoutDirection
import me.tbsten.koma.strict.idea.layout.Rect

internal fun placeComposites(
    composites: List<CompositeBox>,
    nodeRects: Map<NodeId, Rect>,
    config: LayoutConfig,
): Map<NodeId, Rect> {
    val result = LinkedHashMap<NodeId, Rect>()
    // 深い box から先に確定させ、親 box が子 box の rect を取り込めるようにする。
    val deepestFirst = composites.sortedByDescending { it.path.segments.size }
    for (box in deepestFirst) {
        result[box.id] = compositeRectOf(box, nodeRects, result, config) ?: continue
    }
    return result
}

/**
 * The exact rect of one composite box: the union of its direct members, padded by
 * [LayoutConfig.compositePadding]. A *nested-box* member contributes its own already-padded rect
 * from [boxRects] (deepest-first), so each nesting level adds one padding, bottom-up. Shared by
 * [resolveCompositeOverlaps] and [placeComposites] so both agree on identical bounds — otherwise
 * the resolver pushes non-members out of a box smaller than the one finally drawn and they
 * re-intersect the final outer box.
 */
private fun compositeRectOf(
    box: CompositeBox,
    nodeRects: Map<NodeId, Rect>,
    boxRects: Map<NodeId, Rect>,
    config: LayoutConfig,
): Rect? {
    var acc: Rect? = null
    for (memberId in box.memberIds) {
        val memberRect = nodeRects[memberId] ?: boxRects[memberId] ?: continue
        acc = acc?.union(memberRect) ?: memberRect
    }
    return acc?.inflate(config.compositePadding)
}

// ---- composite band cleanup ----

/**
 * Makes every composite box wrap exactly its own members, in two convergent moves:
 *
 * 1. **Free nodes** (members of no composite) that sit inside a box are pushed out along the
 *    cross axis. Box rects depend only on members, so pushing free nodes never resizes any box —
 *    no feedback.
 * 2. **Box-vs-box overlaps** (siblings — never an ancestor/descendant pair) are resolved by
 *    **rigidly translating** the downstream box's whole member tree past the upstream box.
 *    A rigid move keeps the box's size, so boxes can cascade downstream at most once each and the
 *    sweep terminates.
 *
 * The previous implementation pushed *other boxes' members* out individually, which stretched the
 * neighbouring box downward, which pushed back, ping-ponging forever — the pass cap then became
 * the canvas height. Members are therefore never
 * pushed one-by-one any more. If the safety cap is still hit, a `LAYOUT_WARN` is printed instead
 * of failing silently.
 */
internal fun resolveCompositeOverlaps(
    composites: List<CompositeBox>,
    placed: Map<NodeId, Rect>,
    direction: LayoutDirection,
    config: LayoutConfig,
): Map<NodeId, Rect> {
    if (composites.isEmpty()) return placed
    val rects = LinkedHashMap(placed)
    val byId = composites.associateBy { it.id }
    val deepestFirst = composites.sortedByDescending { it.path.segments.size }
    // node -> 直属の箱。兄弟箱のメンバーを個別に押すと所属先の箱が伸びて押し合いが発散するため、
    // 個別押し出しは「自由ノード」か「押し出し元の箱が自分の箱の内側 (子孫) にある場合」だけに限る
    // (親メンバーが入れ子の子箱に飲まれた時は押してよい — 子箱の rect は変わらないので発散しない)。
    val directOwner = HashMap<NodeId, NodeId>()
    for (box in composites) {
        for (m in box.memberIds) {
            if (m !in byId) directOwner[m] = box.id
        }
    }

    fun currentBoxRects(): Map<NodeId, Rect> {
        val boxRects = HashMap<NodeId, Rect>()
        for (box in deepestFirst) {
            compositeRectOf(box, rects, boxRects, config)?.let { boxRects[box.id] = it }
        }
        return boxRects
    }

    // (1) ノード単位の押し出し。自由ノードは常に、箱持ちノードは「押し出し元が自分の箱の子孫箱」の
    // 時だけ (このとき押し出し元の rect は不変なのでフィードバックしない)。1 パス分。
    fun evictNodes(): Boolean {
        var moved = false
        val boxRects = HashMap<NodeId, Rect>()
        for (box in deepestFirst) {
            val boxRect = compositeRectOf(box, rects, boxRects, config) ?: continue
            boxRects[box.id] = boxRect
            val members = transitiveNodeMembers(box.id, byId)
            // 押し出し後の重なりを避けるため、同一「列」ごとに箱の外側から順に積む。
            val cursorByLane = HashMap<Long, Double>()
            for (id in rects.keys.toList()) {
                if (id in members) continue
                val owner = directOwner[id]
                if (owner != null && !isAncestorBox(owner, box.id, byId)) continue
                val nr = rects[id] ?: continue
                if (!nr.intersects(boxRect)) continue
                val pushed = pushOutOfBox(nr, boxRect, direction, config, cursorByLane)
                if (pushed != nr) {
                    rects[id] = pushed
                    moved = true
                }
            }
        }
        return moved
    }

    // (2) 先祖子孫関係にない箱同士の重なりを、下流側の箱のメンバー全体の剛体移動で解く。1 パス分。
    fun separateBoxes(): Boolean {
        var moved = false
        var boxRects = currentBoxRects()
        val ordered = composites.sortedBy { box ->
            boxRects[box.id]?.let { if (direction == LayoutDirection.LR) it.y else it.x } ?: Double.MAX_VALUE
        }
        for (i in ordered.indices) {
            for (j in i + 1 until ordered.size) {
                val a = ordered[i]
                val b = ordered[j]
                if (isAncestorBox(a.id, b.id, byId) || isAncestorBox(b.id, a.id, byId)) continue
                val ra = boxRects[a.id] ?: continue
                val rb = boxRects[b.id] ?: continue
                if (!ra.intersects(rb)) continue
                val delta = when (direction) {
                    LayoutDirection.LR -> ra.bottom + config.siblingGap - rb.y
                    LayoutDirection.TB -> ra.right + config.siblingGap - rb.x
                }
                if (delta <= 0.0) continue
                for (m in transitiveNodeMembers(b.id, byId)) {
                    val r = rects[m] ?: continue
                    rects[m] = when (direction) {
                        LayoutDirection.LR -> r.copy(y = r.y + delta)
                        LayoutDirection.TB -> r.copy(x = r.x + delta)
                    }
                }
                boxRects = currentBoxRects()
                moved = true
            }
        }
        return moved
    }

    val maxPasses = composites.size + 2
    var pass = 0
    while (pass < maxPasses) {
        pass++
        val movedNodes = evictNodes()
        val movedBoxes = separateBoxes()
        if (!movedNodes && !movedBoxes) break
    }
    if (pass >= maxPasses) {
        // 収束保証が破れた時は黙って歪んだ図を出さず、原因調査の手がかりを残す。
        System.err.println("LAYOUT_WARN resolveCompositeOverlaps hit the pass cap ($maxPasses); layout may contain unresolved composite overlaps")
    }
    return rects
}

/** True when composite [ancestor] transitively contains composite [descendant] as a member. */
private fun isAncestorBox(ancestor: NodeId, descendant: NodeId, byId: Map<NodeId, CompositeBox>): Boolean {
    val box = byId[ancestor] ?: return false
    return box.memberIds.any { it == descendant || isAncestorBox(it, descendant, byId) }
}

/** Node ids inside [boxId], expanding nested box members (box ids themselves are not node rects). */
internal fun transitiveNodeMembers(boxId: NodeId, byId: Map<NodeId, CompositeBox>): Set<NodeId> {
    val out = LinkedHashSet<NodeId>()
    fun visit(id: NodeId) {
        val box = byId[id]
        if (box == null) { out += id; return }
        box.memberIds.forEach(::visit)
    }
    byId.getValue(boxId).memberIds.forEach(::visit)
    return out
}

/** Moves [nr] just past [box] along the cross axis (down in LR, right in TB), stacking per lane. */
private fun pushOutOfBox(
    nr: Rect,
    box: Rect,
    direction: LayoutDirection,
    config: LayoutConfig,
    cursorByLane: HashMap<Long, Double>,
): Rect = when (direction) {
    LayoutDirection.LR -> {
        val lane = nr.x.toRawBits()
        val top = cursorByLane.getOrDefault(lane, box.bottom + config.siblingGap)
        cursorByLane[lane] = top + nr.height + config.siblingGap
        nr.copy(y = top)
    }
    LayoutDirection.TB -> {
        val lane = nr.y.toRawBits()
        val left = cursorByLane.getOrDefault(lane, box.right + config.siblingGap)
        cursorByLane[lane] = left + nr.width + config.siblingGap
        nr.copy(x = left)
    }
}
