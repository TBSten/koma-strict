package me.tbsten.koma.strict.idea.layout.layered

import me.tbsten.koma.strict.idea.ir.DiagramGraph
import me.tbsten.koma.strict.idea.ir.NodeId

// ---- layering ----

internal fun assignLayers(graph: DiagramGraph, ids: List<NodeId>): Map<NodeId, Int> {
    val idSet = ids.toSet()
    val byBoxId = graph.composites.associateBy { it.id }
    // group を指すエッジは composite box (= 非ノード) を指すので、そのまま層決定に使うと
    // 非ノード id が層マップに混ざる。box を「その member ノード群への入場」に展開し、BFS 上は
    // box の中身へ向かうエッジとして扱う (UML の composite state entry と同型)。自己ループは除外。
    val links: List<Pair<NodeId, NodeId>> = buildList {
        for (edge in graph.edges) {
            if (edge.fromId == edge.toId) continue
            if (edge.fromId !in idSet) continue
            val toNodes = when {
                edge.toId in idSet -> listOf(edge.toId)
                edge.toId in byBoxId -> transitiveNodeMembers(edge.toId, byBoxId).filter { it in idSet }
                else -> emptyList()
            }
            for (to in toNodes) add(edge.fromId to to)
        }
    }
    val forward: Map<NodeId, List<NodeId>> =
        links.groupBy({ it.first }, { it.second }).mapValues { (_, v) -> v.distinct() }
    val incoming: Map<NodeId, Set<NodeId>> = buildMap<NodeId, MutableSet<NodeId>> {
        for (id in ids) put(id, mutableSetOf())
        for ((from, to) in links) getValue(to).add(from)
    }

    val layer = HashMap<NodeId, Int>()
    var base = 0
    while (layer.size < ids.size) {
        val remaining = ids.filter { it !in layer }
        val remainingSet = remaining.toSet()
        // remaining 部分グラフ内で入次数 0 のノードを seed に。無ければ cycle なので先頭を割る。
        var seeds = remaining.filter { id -> incoming[id].orEmpty().none { it in remainingSet } }
        if (seeds.isEmpty()) seeds = listOf(remaining.first())

        val queue = ArrayDeque<NodeId>()
        for (s in seeds) {
            if (s !in layer) {
                layer[s] = base
                queue.addLast(s)
            }
        }
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            val l = layer.getValue(n)
            for (m in forward[n].orEmpty()) {
                if (m !in layer) {
                    layer[m] = l + 1
                    queue.addLast(m)
                }
            }
        }
        base = (layer.values.maxOrNull() ?: base) + 1
    }
    relocateGroupAnyNodes(graph, layer)
    return layer
}

/**
 * A group-scoped `any <Group>` pseudo node has no incoming edge, so BFS parks it in layer 0.
 * Left there, its composite box would stretch back to the start and swallow everything in
 * between. Pull each non-root any-state node to the entrance layer of its own scope so the box
 * only wraps its group (`ide.md`: the group-shared scope is the any-state form nested inside its
 * composite). The root `any state` legitimately seeds layer 0 and is left untouched.
 */
private fun relocateGroupAnyNodes(graph: DiagramGraph, layer: HashMap<NodeId, Int>) {
    for (any in graph.anyStateNodes) {
        if (any.scope.isRoot) continue
        val scopeSegments = any.scope.segments
        val minScopeLayer = graph.stateNodes
            .filter { it.path.segments.take(scopeSegments.size) == scopeSegments }
            .mapNotNull { layer[it.id] }
            .minOrNull() ?: continue
        layer[any.id] = minScopeLayer
    }
}
