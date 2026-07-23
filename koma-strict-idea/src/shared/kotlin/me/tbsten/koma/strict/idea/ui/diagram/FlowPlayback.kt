package me.tbsten.koma.strict.idea.ui.diagram

import me.tbsten.koma.strict.idea.ir.DiagramGraph
import me.tbsten.koma.strict.idea.ir.EdgeKind
import me.tbsten.koma.strict.idea.ir.GraphEdge
import me.tbsten.koma.strict.idea.ir.NodeId
import me.tbsten.koma.strict.idea.ir.StateGraphNode
import me.tbsten.koma.strict.idea.model.DiagramFlow
import me.tbsten.koma.strict.idea.model.DiagramFlowStep
import me.tbsten.koma.strict.idea.model.StateId

/**
 * The ordered [DiagramSelection]s a [flow] reveals when played (`flows-design.md` IDE section): one entry
 * per resolvable step, in declaration order — a node step lights the state frame, an edge step
 * (`OnEnter` / a trigger) lights the matching transition arrow. A step that cannot be resolved against
 * this graph (a foreign / half-typed ref, or a transition that is not present as an edge) is skipped, so
 * the player only ever highlights elements that really exist. Pure (graph + flow), so it is unit-testable.
 *
 * The caller accumulates the list one entry at a time (`take(revealedCount)`) to build the path up step
 * by step; [flowFocusFrom] turns each accumulated set into a focus set.
 */
internal fun DiagramGraph.flowReveal(flow: DiagramFlow): List<DiagramSelection> {
    val result = mutableListOf<DiagramSelection>()
    var current: StateId? = null
    val steps = flow.steps
    steps.forEachIndexed { i, step ->
        when (step) {
            is DiagramFlowStep.Node -> {
                current = step.id
                nodeSelection(step.id)?.let { result += it }
            }
            // Stay = 現状維持: 現在ノードを (冪等に) 再強調するだけで新しい要素は足さない。
            DiagramFlowStep.Stay -> current?.let { c -> nodeSelection(c)?.let { result += it } }
            // edge ステップは直前ノード -> 次ノード の間の遷移。IR の GraphEdge に解決して矢印を強調する。
            DiagramFlowStep.Enter, is DiagramFlowStep.Trigger -> {
                val to = nextNodeStateId(steps, i, current)
                flowEdge(current, to, step)?.let { result += DiagramSelection.Edge(it) }
            }
            DiagramFlowStep.Unresolved -> Unit
        }
    }
    return result
}

/** The node selection for a state id, or null when this graph has no such concrete leaf node. */
private fun DiagramGraph.nodeSelection(id: StateId): DiagramSelection.Node? =
    (node(NodeId.State(id)) as? StateGraphNode)?.let { DiagramSelection.Node(it.id) }

/** State id of the next node step after [from] — a [DiagramFlowStep.Node], or [current] for a `Stay`. */
private fun nextNodeStateId(steps: List<DiagramFlowStep>, from: Int, current: StateId?): StateId? {
    for (j in from + 1 until steps.size) {
        when (val s = steps[j]) {
            is DiagramFlowStep.Node -> return s.id
            DiagramFlowStep.Stay -> return current
            else -> Unit
        }
    }
    return null
}

/** The graph edge from [from] to [to] matching [step] — `OnEnter` by [EdgeKind.ENTER], a trigger by its type ref. */
private fun DiagramGraph.flowEdge(from: StateId?, to: StateId?, step: DiagramFlowStep): GraphEdge? {
    val fromId = from?.let { NodeId.State(it) }
    val toId = to?.let { NodeId.State(it) }
    return edges.firstOrNull { e ->
        (fromId == null || e.fromId == fromId) &&
            (toId == null || e.toId == toId) &&
            when (step) {
                DiagramFlowStep.Enter -> e.kind == EdgeKind.ENTER
                is DiagramFlowStep.Trigger -> e.triggerTypeRef == step.ref
                else -> false
            }
    }
}
