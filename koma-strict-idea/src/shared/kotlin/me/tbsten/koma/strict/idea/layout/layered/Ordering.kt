package me.tbsten.koma.strict.idea.layout.layered

import me.tbsten.koma.strict.idea.ir.DiagramGraph
import me.tbsten.koma.strict.idea.ir.NodeId

// ---- sibling ordering (median heuristic) ----

/** How many up/down median sweeps to run over the layers. */
private const val MEDIAN_SWEEPS = 4

/**
 * Orders the siblings within every layer by the Sugiyama *median heuristic* to cut edge crossings.
 *
 * Starting from declaration order, the layers are swept top-down and bottom-up [MEDIAN_SWEEPS]
 * times. On each sweep the moving layer's nodes are re-sorted by the median index of their
 * neighbours in the already-fixed adjacent layer; a node with no neighbour in that layer keeps its
 * current slot (`元の相対位置を保持`). Only the sibling order changes — layer assignment, cell
 * geometry, and every downstream step are untouched.
 */
internal fun orderLayers(
    graph: DiagramGraph,
    ids: List<NodeId>,
    layers: Map<NodeId, Int>,
): Map<Int, List<NodeId>> {
    val idSet = ids.toSet()
    val adjacency = buildAdjacency(graph, idSet)
    val order: MutableMap<Int, MutableList<NodeId>> = ids
        .groupBy { layers.getValue(it) }
        .toSortedMap()
        .mapValues { (_, v) -> v.toMutableList() }
        .toMutableMap()
    val layerIndices = order.keys.sorted()
    if (layerIndices.size < 2) return order

    repeat(MEDIAN_SWEEPS) { sweep ->
        val downward = sweep % 2 == 0
        val sequence = if (downward) layerIndices else layerIndices.asReversed()
        for (li in sequence) {
            val adjLayer = if (downward) li - 1 else li + 1
            val fixed = order[adjLayer] ?: continue
            // 隣接層のノード -> その並び位置 index。median のキーになる。
            val pos = HashMap<NodeId, Int>(fixed.size)
            fixed.forEachIndexed { i, id -> pos[id] = i }
            order[li] = medianReorder(order.getValue(li), adjacency, pos).toMutableList()
        }
    }
    return order.toSortedMap()
}

/** Undirected node<->node adjacency, expanding composite-box targets to their member nodes. */
private fun buildAdjacency(graph: DiagramGraph, idSet: Set<NodeId>): Map<NodeId, List<NodeId>> {
    val byBoxId = graph.composites.associateBy { it.id }
    val adj = HashMap<NodeId, MutableList<NodeId>>()
    fun link(a: NodeId, b: NodeId) {
        adj.getOrPut(a) { mutableListOf() }.add(b)
        adj.getOrPut(b) { mutableListOf() }.add(a)
    }
    for (edge in graph.edges) {
        if (edge.fromId == edge.toId) continue
        if (edge.fromId !in idSet) continue
        val toNodes = when {
            edge.toId in idSet -> listOf(edge.toId)
            edge.toId in byBoxId -> transitiveNodeMembers(edge.toId, byBoxId).filter { it in idSet }
            else -> emptyList()
        }
        for (to in toNodes) link(edge.fromId, to)
    }
    return adj
}

/**
 * Re-sorts one layer by the median of each node's neighbour positions in the fixed adjacent layer.
 * Nodes without a neighbour in that layer (median < 0) keep their original slot; the rest are
 * sorted by median and threaded back through the remaining slots, preserving the fixed nodes.
 */
private fun medianReorder(
    nodes: List<NodeId>,
    adjacency: Map<NodeId, List<NodeId>>,
    pos: Map<NodeId, Int>,
): List<NodeId> {
    if (nodes.size < 2) return nodes
    val medians = nodes.map { id ->
        val neighbourPos = adjacency[id].orEmpty().mapNotNull { pos[it] }.sorted()
        id to medianOf(neighbourPos)
    }
    // median < 0 のノード (接続先が無い) は元のスロットに固定。
    val fixedSlots = HashMap<Int, NodeId>()
    medians.forEachIndexed { slot, (id, med) -> if (med < 0.0) fixedSlots[slot] = id }
    val movable = medians.filter { it.second >= 0.0 }.sortedBy { it.second }.map { it.first }
    val result = arrayOfNulls<NodeId>(nodes.size)
    for ((slot, id) in fixedSlots) result[slot] = id
    var m = 0
    for (i in result.indices) {
        if (result[i] == null) result[i] = movable[m++]
    }
    @Suppress("UNCHECKED_CAST")
    return (result as Array<NodeId>).toList()
}

/** Median of sorted neighbour positions: middle when odd, mean of the two middles when even, -1 if none. */
private fun medianOf(sortedPos: List<Int>): Double {
    val m = sortedPos.size
    if (m == 0) return -1.0
    return if (m % 2 == 1) {
        sortedPos[m / 2].toDouble()
    } else {
        (sortedPos[m / 2 - 1] + sortedPos[m / 2]) / 2.0
    }
}
