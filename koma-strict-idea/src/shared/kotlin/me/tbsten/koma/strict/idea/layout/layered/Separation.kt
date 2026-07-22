package me.tbsten.koma.strict.idea.layout.layered

import me.tbsten.koma.strict.idea.ir.DiagramGraph
import me.tbsten.koma.strict.idea.ir.NodeId
import me.tbsten.koma.strict.idea.layout.LayoutConfig
import me.tbsten.koma.strict.idea.layout.LayoutDirection
import me.tbsten.koma.strict.idea.layout.Rect

internal fun separateOverlaps(
    rects: Map<NodeId, Rect>,
    direction: LayoutDirection,
    config: LayoutConfig,
): Map<NodeId, Rect> {
    if (rects.size < 2) return rects
    val result = LinkedHashMap(rects)
    val laneKey: (Rect) -> Long = { r ->
        when (direction) {
            LayoutDirection.LR -> r.x.toRawBits()
            LayoutDirection.TB -> r.y.toRawBits()
        }
    }
    val lanes = result.keys.groupBy { laneKey(result.getValue(it)) }
    for ((_, laneIds) in lanes) {
        if (laneIds.size < 2) continue
        val ordered = laneIds.sortedBy {
            val r = result.getValue(it)
            if (direction == LayoutDirection.LR) r.y else r.x
        }
        for (i in 1 until ordered.size) {
            val prev = result.getValue(ordered[i - 1])
            val cur = result.getValue(ordered[i])
            result[ordered[i]] = when (direction) {
                LayoutDirection.LR -> {
                    val minTop = prev.bottom + config.siblingGap
                    if (cur.y < minTop) cur.copy(y = minTop) else cur
                }
                LayoutDirection.TB -> {
                    val minLeft = prev.right + config.siblingGap
                    if (cur.x < minLeft) cur.copy(x = minLeft) else cur
                }
            }
        }
    }
    return result
}

/** Shifts every rect so the leading edge (left in LR / top in TB) sits back at [LayoutConfig.margin]. */
internal fun normalizeOrigin(
    rects: Map<NodeId, Rect>,
    direction: LayoutDirection,
    config: LayoutConfig,
): Map<NodeId, Rect> {
    if (rects.isEmpty()) return rects
    val shift = when (direction) {
        LayoutDirection.LR -> rects.values.minOf { it.x } - config.margin
        LayoutDirection.TB -> rects.values.minOf { it.y } - config.margin
    }
    if (shift == 0.0) return rects
    return when (direction) {
        LayoutDirection.LR -> rects.mapValues { (_, r) -> r.copy(x = r.x - shift) }
        LayoutDirection.TB -> rects.mapValues { (_, r) -> r.copy(y = r.y - shift) }
    }
}

/**
 * Shifts node + composite rects together so the smallest leading/top edge across *all* of them
 * lands back at [LayoutConfig.margin]. Needed because a composite box inflates [compositePadding]
 * beyond its members, so its top/left can go negative and clip; [repositionStart] only normalizes
 * node rects and runs before boxes exist, so it can't see that.
 */
internal fun normalizeAll(
    nodeRects: Map<NodeId, Rect>,
    compositeRects: Map<NodeId, Rect>,
    config: LayoutConfig,
): Pair<Map<NodeId, Rect>, Map<NodeId, Rect>> {
    val all = nodeRects.values + compositeRects.values
    if (all.isEmpty()) return nodeRects to compositeRects
    val dx = config.margin - all.minOf { it.x }
    val dy = config.margin - all.minOf { it.y }
    if (dx == 0.0 && dy == 0.0) return nodeRects to compositeRects
    fun shift(m: Map<NodeId, Rect>): Map<NodeId, Rect> =
        m.mapValues { (_, r) -> r.copy(x = r.x + dx, y = r.y + dy) }
    return shift(nodeRects) to shift(compositeRects)
}

// 車線空けで中間ノードを避ける量の余白 (ノード半分 + これだけ直交方向へ動かす)。
// 避けたノードの縁と直線車線の間に「エッジラベル 1 行分の回廊」(~26dp) を確保する余白込み。
// 20dp だと避けたノードの下縁が車線に接し、車線上のラベルがノードに密着して読めない。
private const val SKIP_LANE_CLEARANCE = 48.0

/**
 * Clears a straight "lane" for edges that span 2+ layers: an in-between node sitting on the same
 * row (LR) / column (TB) as both endpoints would force the straight line to ride along its border,
 * so it is nudged perpendicular to the sweep axis. Each node moves at most once.
 */
internal fun nudgeSkipLanes(
    graph: DiagramGraph,
    layers: Map<NodeId, Int>,
    rects: Map<NodeId, Rect>,
    direction: LayoutDirection,
): Map<NodeId, Rect> {
    val result = rects.toMutableMap()
    val nudged = HashSet<NodeId>()
    for (edge in graph.edges) {
        if (edge.fromId == edge.toId) continue
        val la = layers[edge.fromId] ?: continue
        val lb = layers[edge.toId] ?: continue
        if (kotlin.math.abs(la - lb) < 2) continue
        val a = result[edge.fromId] ?: continue
        val b = result[edge.toId] ?: continue
        for ((id, r) in result.entries.toList()) {
            if (id == edge.fromId || id == edge.toId || id in nudged) continue
            val li = layers[id] ?: continue
            if (li <= minOf(la, lb) || li >= maxOf(la, lb)) continue
            when (direction) {
                LayoutDirection.LR -> {
                    val sameRow = kotlin.math.abs(r.center.y - a.center.y) < r.height / 2 &&
                        kotlin.math.abs(r.center.y - b.center.y) < r.height / 2
                    if (sameRow) {
                        result[id] = Rect(r.x, r.y - (r.height / 2 + SKIP_LANE_CLEARANCE), r.width, r.height)
                        nudged += id
                    }
                }
                LayoutDirection.TB -> {
                    val sameCol = kotlin.math.abs(r.center.x - a.center.x) < r.width / 2 &&
                        kotlin.math.abs(r.center.x - b.center.x) < r.width / 2
                    if (sameCol) {
                        result[id] = Rect(r.x - (r.width / 2 + SKIP_LANE_CLEARANCE), r.y, r.width, r.height)
                        nudged += id
                    }
                }
            }
        }
    }
    return result
}
