package me.tbsten.koma.strict.idea.layout.layered

import me.tbsten.koma.strict.idea.ir.AnyStateNode
import me.tbsten.koma.strict.idea.ir.DiagramGraph
import me.tbsten.koma.strict.idea.ir.NodeId
import me.tbsten.koma.strict.idea.ir.StateGraphNode
import me.tbsten.koma.strict.idea.layout.LayoutConfig
import me.tbsten.koma.strict.idea.layout.LayoutDirection
import me.tbsten.koma.strict.idea.layout.Rect

/**
 * Clearance reserved around a node that has self-loops: the loop arc lift (32) plus its outside
 * label row (~18) and a small gap, so arcs / labels near a canvas edge are never clamped onto
 * each other or clipped.
 */
private const val LOOP_HALO = 56.0

/**
 * Estimated cross-axis clearance each loop-bearing node needs for its self-loop arcs and merged
 * multi-line labels, keyed by node. The estimate mirrors the draw side: loops group by
 * (node, kind), each group renders one arc (lift 32) plus one label pill whose line count is the
 * sum of the member labels wrapped at 220dp (~5.6dp per char at 10sp, 14dp per line). The
 * largest group governs. Nodes without self-loops are absent from the map.
 */
internal fun loopClearanceExtents(graph: DiagramGraph): Map<NodeId, Double> {
    val groups = LinkedHashMap<Pair<NodeId, Any>, MutableList<String>>()
    for (edge in graph.edges) {
        if (edge.fromId != edge.toId) continue
        groups.getOrPut(edge.fromId to edge.kind) { mutableListOf() }
            .apply { if (edge.label.isNotBlank()) add(edge.label) }
    }
    val extents = HashMap<NodeId, Double>()
    for ((key, labels) in groups) {
        val lines = labels.sumOf { maxOf(1, kotlin.math.ceil(it.length * 5.6 / 220.0).toInt()) }
        // 32 = 弧の lift, 4 = ラベルgap, 14 = 1 行の高さ, 6 = ピルの余白。ラベル無し group は halo のみ。
        val extent = if (lines == 0) LOOP_HALO else maxOf(LOOP_HALO, 32.0 + 4.0 + lines * 14.0 + 6.0)
        extents.merge(key.first, extent, ::maxOf)
    }
    return extents
}

/**
 * Widens the cross-axis gap between nodes so each pair of vertically (LR) / horizontally (TB)
 * adjacent, overlapping nodes keeps room for both sides' loop arcs + labels
 * ([loopClearanceExtents]). Only pushes nodes further along the cross axis (monotone — never
 * reorders, never pulls together), so it cannot oscillate; downstream passes (eviction, skip
 * lanes, separation) all preserve or increase gaps.
 */
internal fun reserveLoopClearance(
    graph: DiagramGraph,
    rects: Map<NodeId, Rect>,
    direction: LayoutDirection,
    config: LayoutConfig,
): Map<NodeId, Rect> {
    val extents = loopClearanceExtents(graph)
    if (extents.isEmpty()) return rects
    val result = LinkedHashMap(rects)
    val ordered = result.keys.sortedWith(
        compareBy(
            { if (direction == LayoutDirection.LR) result.getValue(it).y else result.getValue(it).x },
            { if (direction == LayoutDirection.LR) result.getValue(it).x else result.getValue(it).y },
        ),
    )
    for (j in ordered.indices) {
        val idJ = ordered[j]
        var rj = result.getValue(idJ)
        for (i in 0 until j) {
            val ri = result.getValue(ordered[i])
            val required = maxOf(
                config.siblingGap,
                (extents[ordered[i]] ?: 0.0) + (extents[idJ] ?: 0.0),
            )
            // ソート順 (元の交差軸順) を正とし、i が先行するペアは常に隙間を enforce する。
            // 「現在すでに上に居るか」を条件にすると、先行ペアの押し下げで生じた一時的な重なりが
            // ペアをスキップさせ、後段の separateOverlaps が siblingGap だけで再スタックしてしまう。
            when (direction) {
                LayoutDirection.LR -> {
                    if (ri.x < rj.right && ri.right > rj.x) {
                        val minTop = ri.bottom + required
                        if (rj.y < minTop) rj = rj.copy(y = minTop)
                    }
                }
                LayoutDirection.TB -> {
                    if (ri.y < rj.bottom && ri.bottom > rj.y) {
                        val minLeft = ri.right + required
                        if (rj.x < minLeft) rj = rj.copy(x = minLeft)
                    }
                }
            }
        }
        result[idJ] = rj
    }
    return result
}

/**
 * The extra (right, bottom) canvas extent any `@OnExit` badge reaches. In `LR` a badge extends to
 * the node's right; in `TB` it extends below the node (and may overhang horizontally when wider
 * than the node). Sizes are estimated from the badge text length so the canvas reserves enough
 * room to not clip it.
 */
internal fun exitBadgeExtent(
    graph: DiagramGraph,
    rects: Map<NodeId, Rect>,
    config: LayoutConfig,
    direction: LayoutDirection,
): Pair<Double, Double> {
    var maxRight = 0.0
    var maxBottom = 0.0
    for (node in graph.nodes) {
        val badge = when (node) {
            is StateGraphNode -> node.exitBadge
            is AnyStateNode -> node.exitBadge
            else -> null
        } ?: continue
        val r = rects[node.id] ?: continue
        // 9sp のバッジ幅を文字数から強めに見積もる (実測幅がこれを超えてクリップしないよう余裕を取る)。
        val estWidth = badge.length * 7.0 + 20.0
        when (direction) {
            LayoutDirection.LR -> maxRight = maxOf(maxRight, r.right + config.exitBadgeGap + estWidth)
            LayoutDirection.TB -> {
                maxBottom = maxOf(maxBottom, r.bottom + config.exitBadgeGap + BADGE_HEIGHT)
                // 中央寄せバッジがノードより広いと横にはみ出すので右端も確保する。
                maxRight = maxOf(maxRight, r.center.x + estWidth / 2)
            }
        }
    }
    return maxRight to maxBottom
}

/** Estimated pixel height of an `@OnExit` badge pill (9sp text + vertical padding). */
private const val BADGE_HEIGHT = 22.0
