package me.tbsten.koma.strict.idea.layout.layered

import me.tbsten.koma.strict.idea.ir.DiagramGraph
import me.tbsten.koma.strict.idea.ir.NodeId
import me.tbsten.koma.strict.idea.ir.StartNode
import me.tbsten.koma.strict.idea.layout.LayoutConfig
import me.tbsten.koma.strict.idea.layout.LayoutDirection
import me.tbsten.koma.strict.idea.layout.Rect

// ---- placement ----

internal fun placeNodes(
    graph: DiagramGraph,
    ids: List<NodeId>,
    layers: Map<NodeId, Int>,
    direction: LayoutDirection,
    config: LayoutConfig,
): Map<NodeId, Rect> {
    val startIds = graph.nodes.filterIsInstance<StartNode>().map { it.id }.toSet()
    // 層ごとに、ノードを縦 (LR) / 横 (TB) に積む。宣言順から始め、median heuristic の
    // 上下 sweep で兄弟順を交差が減る向きへ並べ替える (Sugiyama median sort)。
    val byLayer: Map<Int, List<NodeId>> = orderLayers(graph, ids, layers)

    val rects = LinkedHashMap<NodeId, Rect>()
    for ((layerIndex, nodesInLayer) in byLayer) {
        nodesInLayer.forEachIndexed { indexInLayer, id ->
            val isStart = id in startIds
            val cell = cellRect(layerIndex, indexInLayer, direction, config)
            rects[id] = if (isStart) centerSquare(cell, config.startSize) else cell
        }
    }
    return rects
}

/**
 * Pulls each node's cross-axis position toward the median of its neighbours' centers while
 * keeping the in-layer order (decided by the median heuristic) and a [LayoutConfig.siblingGap]
 * minimum spacing. Straightens edges the grid placement leaves diagonal: two nodes connected
 * across layers end up on (nearly) the same row, so long full-canvas diagonals and their
 * crossings shrink. Runs a few Gauss–Seidel sweeps (ascending then descending layer order);
 * downstream passes (eviction / skip lanes / loop clearance) only push apart, so the alignment
 * survives them. Order is never changed, so the median crossing regressions (auth = 0) hold.
 */
internal fun pullTowardNeighbors(
    graph: DiagramGraph,
    ids: List<NodeId>,
    layers: Map<NodeId, Int>,
    rects: Map<NodeId, Rect>,
    direction: LayoutDirection,
    config: LayoutConfig,
): Map<NodeId, Rect> {
    val result = LinkedHashMap(rects)
    val neighbors = HashMap<NodeId, MutableList<NodeId>>()
    for (edge in graph.edges) {
        if (edge.fromId == edge.toId) continue
        if (edge.fromId !in result || edge.toId !in result) continue
        neighbors.getOrPut(edge.fromId) { mutableListOf() }.add(edge.toId)
        neighbors.getOrPut(edge.toId) { mutableListOf() }.add(edge.fromId)
    }
    if (neighbors.isEmpty()) return result
    fun cross(r: Rect): Double = if (direction == LayoutDirection.LR) r.y + r.height / 2 else r.x + r.width / 2
    fun withCrossTop(r: Rect, top: Double): Rect =
        if (direction == LayoutDirection.LR) r.copy(y = top) else r.copy(x = top)
    fun crossSize(r: Rect): Double = if (direction == LayoutDirection.LR) r.height else r.width

    val byLayer = ids.groupBy { layers[it] ?: 0 }.toSortedMap()
    val layerOrder = byLayer.values.map { nodesInLayer ->
        nodesInLayer.sortedBy { cross(result.getValue(it)) }
    }
    // 前進 sweep は「前のレイヤの隣接」、後退 sweep は「次のレイヤの隣接」に整列する古典
    // Sugiyama の交互整列。全隣接の中央値だと hub (fan の中心) が fan メンバーの帯へ引き込まれ、
    // 幹線 (spine) が折れて浅い平行束を作る。方向を絞ると
    // 「親に揃える」= 直線の背骨ができ、fan は spread パスが開く。
    data class Sweep(val order: List<List<NodeId>>, val towardPrev: Boolean)
    val sweeps = listOf(
        Sweep(layerOrder, towardPrev = true),
        Sweep(layerOrder.asReversed(), towardPrev = false),
        Sweep(layerOrder, towardPrev = true),
    )
    for ((pass, towardPrev) in sweeps) {
        for (layerNodes in pass) {
            if (layerNodes.isEmpty()) continue
            val layerOf = { id: NodeId -> layers[id] ?: 0 }
            // 各ノードの目標 = sweep 方向側レイヤの隣接中心の中央値 (無ければ反対側レイヤの隣接)。
            // **同一レイヤの隣接は絶対にターゲットにしない**: 同層ノードへ「整列」すると相手の
            // 位置へ潜り込もうとして前進クランプで押し下げられ、sweep のたびに下がるラチェット
            // 発散を起こす。層跨ぎの
            // 隣接が無いノードは現位置を維持する。
            val targets = layerNodes.map { id ->
                val nsCross = neighbors[id].orEmpty().filter { layerOf(it) != layerOf(id) }
                if (nsCross.isEmpty()) {
                    cross(result.getValue(id))
                } else {
                    val side = nsCross.filter {
                        if (towardPrev) layerOf(it) < layerOf(id) else layerOf(it) > layerOf(id)
                    }.ifEmpty { nsCross }
                    val centers = side.map { cross(result.getValue(it)) }.sorted()
                    // 偶数個は中央 2 点の平均 (上寄り単独選択だと片側へ寄り続ける)。
                    if (centers.size % 2 == 1) {
                        centers[centers.size / 2]
                    } else {
                        (centers[centers.size / 2 - 1] + centers[centers.size / 2]) / 2
                    }
                }
            }
            // 並び順を保ったまま前進クランプで再配置 (target 順が並びと矛盾する分は gap で解消)。
            var cursor = Double.NEGATIVE_INFINITY
            for (i in layerNodes.indices) {
                val id = layerNodes[i]
                val r = result.getValue(id)
                val size = crossSize(r)
                val top = maxOf(targets[i] - size / 2, cursor)
                result[id] = withCrossTop(r, top)
                cursor = top + size + config.siblingGap
            }
        }
    }
    return result
}

/**
 * Ensures the sources/targets fanning into a common node keep a minimum cross-axis distance from
 * each other, so their straight edges approach the shared node at visibly different angles
 * instead of forming a near-parallel shallow bundle. Only groups of 3+ fan siblings are spread,
 * only by pushing later (in cross order) members further along the cross axis — monotone, so it
 * cannot oscillate; the follow-up separation/eviction passes absorb any new overlaps.
 */
internal fun spreadFanSiblings(
    graph: DiagramGraph,
    layers: Map<NodeId, Int>,
    rects: Map<NodeId, Rect>,
    direction: LayoutDirection,
    config: LayoutConfig,
): Map<NodeId, Rect> {
    val neighbors = HashMap<NodeId, MutableSet<NodeId>>()
    for (edge in graph.edges) {
        if (edge.fromId == edge.toId) continue
        if (edge.fromId !in rects || edge.toId !in rects) continue
        neighbors.getOrPut(edge.fromId) { mutableSetOf() }.add(edge.toId)
        neighbors.getOrPut(edge.toId) { mutableSetOf() }.add(edge.fromId)
    }
    val result = LinkedHashMap(rects)
    fun cross(r: Rect): Double = if (direction == LayoutDirection.LR) r.y else r.x
    val minGap = (if (direction == LayoutDirection.LR) config.nodeHeight else config.nodeWidth) / 2 + 32.0
    // hub (相手が 3 以上) ごとに、同一レイヤの fan メンバー同士だけを交差軸順に走査して間隔を
    // enforce する (別レイヤの相手は既に流れ軸で離れており、交差軸まで離すと対角線が伸びるだけ)。
    for ((_, fan) in neighbors) {
        if (fan.size < 3) continue
        for ((_, sameLayer) in fan.groupBy { layers[it] ?: 0 }) {
            if (sameLayer.size < 2) continue
            val ordered = sameLayer.sortedBy { cross(result.getValue(it)) }
            for (i in 1 until ordered.size) {
                val prev = result.getValue(ordered[i - 1])
                val cur = result.getValue(ordered[i])
                val minTop = (if (direction == LayoutDirection.LR) prev.y + prev.height else prev.x + prev.width) + minGap
                if (direction == LayoutDirection.LR) {
                    if (cur.y < minTop) result[ordered[i]] = cur.copy(y = minTop)
                } else {
                    if (cur.x < minTop) result[ordered[i]] = cur.copy(x = minTop)
                }
            }
        }
    }
    return result
}

private fun cellRect(
    layerIndex: Int,
    indexInLayer: Int,
    direction: LayoutDirection,
    config: LayoutConfig,
): Rect = when (direction) {
    LayoutDirection.LR -> Rect(
        x = config.margin + layerIndex * (config.nodeWidth + config.layerGap),
        y = config.margin + indexInLayer * (config.nodeHeight + config.siblingGap),
        width = config.nodeWidth,
        height = config.nodeHeight,
    )
    LayoutDirection.TB -> Rect(
        x = config.margin + indexInLayer * (config.nodeWidth + config.siblingGap),
        y = config.margin + layerIndex * (config.nodeHeight + config.layerGap),
        width = config.nodeWidth,
        height = config.nodeHeight,
    )
}

private fun centerSquare(cell: Rect, side: Double): Rect =
    Rect(
        x = cell.x + (cell.width - side) / 2,
        y = cell.y + (cell.height - side) / 2,
        width = side,
        height = side,
    )
