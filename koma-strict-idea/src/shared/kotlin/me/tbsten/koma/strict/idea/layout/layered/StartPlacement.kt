package me.tbsten.koma.strict.idea.layout.layered

import me.tbsten.koma.strict.idea.ir.DiagramGraph
import me.tbsten.koma.strict.idea.ir.NodeId
import me.tbsten.koma.strict.idea.ir.StartNode
import me.tbsten.koma.strict.idea.layout.LayoutConfig
import me.tbsten.koma.strict.idea.layout.LayoutDirection
import me.tbsten.koma.strict.idea.layout.Rect

/**
 * Pulls the `[*]` start dot toward the initial state(s) it points to so the start edge is only
 * [LayoutConfig.startGap] long — the BFS otherwise puts the dot a full node cell + [layerGap] away,
 * which reads as an over-long line. The dot is moved next to the nearest initial, then the origin
 * is re-normalized so the shift doesn't leave dead space on the leading edge.
 *
 * When an initial state sits inside a composite box, moving the dot right up against it would put
 * the dot across the box border; the move is clamped to stop just outside the box so the start
 * marker never crosses a composite border (the entry edge still lands on the inner initial).
 */
internal fun repositionStart(
    graph: DiagramGraph,
    nodeRects: Map<NodeId, Rect>,
    direction: LayoutDirection,
    config: LayoutConfig,
): Map<NodeId, Rect> {
    val startId = graph.nodes.filterIsInstance<StartNode>().firstOrNull()?.id ?: return nodeRects
    val startRect = nodeRects[startId] ?: return nodeRects
    val targets = graph.edges.filter { it.fromId == startId }.mapNotNull { nodeRects[it.toId] }
    if (targets.isEmpty()) return nodeRects
    // 最終描画と同一形状の composite rect。initial state が box の中にある (session の
    // SignedIn.Home 等) と start を initial 直前へ寄せると box 境界を跨ぐため、跨ぐ box があれば
    // その leading 側の手前まで戻して border を跨がせない (P2-12)。
    val boxRects = placeComposites(graph.composites, nodeRects, config).values
    val moved = when (direction) {
        LayoutDirection.LR -> {
            var x = targets.minOf { it.x } - config.startGap - startRect.width
            val blocking = boxRects.filter { startRect.copy(x = x).intersects(it) }
            if (blocking.isNotEmpty()) x = minOf(x, blocking.minOf { it.x } - config.startGap - startRect.width)
            startRect.copy(x = x)
        }
        LayoutDirection.TB -> {
            var y = targets.minOf { it.y } - config.startGap - startRect.height
            val blocking = boxRects.filter { startRect.copy(y = y).intersects(it) }
            if (blocking.isNotEmpty()) y = minOf(y, blocking.minOf { it.y } - config.startGap - startRect.height)
            startRect.copy(y = y)
        }
    }
    return normalizeOrigin(nodeRects + (startId to moved), direction, config)
}

/**
 * Seats the `[*]` start marker just outside the root frame (`content bbox ± [ROOT_FRAME_PAD]`),
 * on the **leading edge** of the layout direction (left in LR / top in TB), aligned to the initial
 * state's centre on the other axis. The renderer draws the frame from the same content bbox (start
 * excluded), so the dot always sits outside the visible frame — including stores like `auth` whose
 * initial state is interior (the dot still exits to the frame's left/top for a consistent "enters
 * from outside" read). Any negative coordinate the move introduces is absorbed by shifting *all*
 * rects back to the margin.
 */
internal fun placeStartOutsideFrame(
    graph: DiagramGraph,
    nodeRects: Map<NodeId, Rect>,
    compositeRects: Map<NodeId, Rect>,
    direction: LayoutDirection,
    config: LayoutConfig,
): Pair<Map<NodeId, Rect>, Map<NodeId, Rect>> {
    val startId = graph.nodes.filterIsInstance<StartNode>().firstOrNull()?.id ?: return nodeRects to compositeRects
    val startRect = nodeRects[startId] ?: return nodeRects to compositeRects
    val initial = graph.edges.filter { it.fromId == startId }.mapNotNull { nodeRects[it.toId] }.firstOrNull()
        ?: return nodeRects to compositeRects
    // content bbox = start 以外の全ノード + composite (= 描画側 root frame の元)。
    val content = nodeRects.filterKeys { it != startId }.values + compositeRects.values
    if (content.isEmpty()) return nodeRects to compositeRects
    val cx = initial.x + initial.width / 2
    val cy = initial.y + initial.height / 2
    val moved = when (direction) {
        // LR は枠の左外・initial の y に揃える / TB は枠の上外・initial の x に揃える。
        LayoutDirection.LR -> {
            val fl = content.minOf { it.x } - LayeredLayout.ROOT_FRAME_PAD
            startRect.copy(x = fl - config.startGap - startRect.width, y = cy - startRect.height / 2)
        }
        LayoutDirection.TB -> {
            val ft = content.minOf { it.y } - LayeredLayout.ROOT_FRAME_PAD
            startRect.copy(x = cx - startRect.width / 2, y = ft - config.startGap - startRect.height)
        }
    }
    val movedNodes = nodeRects + (startId to moved)
    // start が margin より手前 (負側) に出たら全 rect を平行移動して吸収。
    val minX = (movedNodes.values + compositeRects.values).minOf { it.x }
    val minY = (movedNodes.values + compositeRects.values).minOf { it.y }
    val sx = (config.margin - minX).coerceAtLeast(0.0)
    val sy = (config.margin - minY).coerceAtLeast(0.0)
    if (sx == 0.0 && sy == 0.0) return movedNodes to compositeRects
    fun shift(r: Rect) = Rect(r.x + sx, r.y + sy, r.width, r.height)
    return movedNodes.mapValues { shift(it.value) } to compositeRects.mapValues { shift(it.value) }
}
