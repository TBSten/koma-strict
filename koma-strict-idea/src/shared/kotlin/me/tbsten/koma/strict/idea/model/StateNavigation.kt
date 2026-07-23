package me.tbsten.koma.strict.idea.model

/**
 * Gutter-navigation model (`ide-gutter.md`): "from state X, the next states you can transition to".
 *
 * Pure functions over the slim [StoreDiagramModel] so the editor gutter line-marker provider stays a
 * thin PSI adapter and the reachability rules are unit-testable without the IDE.
 */

/** One outgoing transition from a state: its [target] leaf/group and the trigger that fires it ([via]). */
data class NextStateTransition(
    /** Root-relative id of the state transitioned to (a leaf or an intermediate group). */
    val target: StateId,
    /** Human label of the firing trigger for the popup, e.g. `enter`, `Retry Action`, `Expired recover`. */
    val via: String,
)

/** The triggers *declared on this node itself* (its own `@OnEnter` / `@OnAction` / `@OnRecover`). */
fun DiagramStateNode.ownTriggers(): List<DiagramTrigger> = buildList {
    when (this@ownTriggers) {
        is LeafState -> enter?.let(::add)
        is GroupState -> enter?.let(::add)
        is RootState -> Unit // root has no @OnEnter
    }
    addAll(actions)
    addAll(recovers)
}

/** Popup label for the trigger that produces a transition (the `by …` part). */
private fun viaLabel(trigger: DiagramTrigger): String = when (trigger) {
    is EnterTrigger -> "enter"
    is ActionTrigger -> "${trigger.actionName} Action"
    is RecoverTrigger -> "${trigger.exceptionName} recover"
}

/**
 * For every state node, the transitions that can fire *while in that state* — its own triggers plus the
 * scope-shared triggers of every ancestor (root / intermediate sealed), since koma applies those to each
 * leaf under the scope (case B: inherited transitions are included). Stay-only triggers contribute no
 * entry (they don't reach a *next* state); unresolved / foreign targets are already excluded from
 * [DiagramTrigger.targets]. Keyed by [DiagramStateNode.id]; a state with no outgoing transition is absent.
 *
 * Walked with an explicit stack (never recursion) so a pathologically deep tree can't overflow — the same
 * rule the rest of the model walking follows.
 */
fun StoreDiagramModel.outgoingTransitionsByState(): Map<StateId, List<NextStateTransition>> {
    val result = LinkedHashMap<StateId, List<NextStateTransition>>()
    val stack = ArrayDeque<Pair<DiagramStateNode, List<DiagramStateNode>>>()
    stack.addLast(root to emptyList())
    while (stack.isNotEmpty()) {
        val (node, ancestors) = stack.removeLast()
        // 自前トリガを先に、続いて祖先の scope 共有トリガ (継承) を並べる。
        val triggers = node.ownTriggers() + ancestors.flatMap { it.ownTriggers() }
        val transitions = triggers.flatMap { trigger ->
            trigger.targets.map { NextStateTransition(it, viaLabel(trigger)) }
        }
        if (transitions.isNotEmpty()) result[node.id] = transitions
        if (node is DiagramStateParent) {
            // 逆順 push で pop 時に宣言順を保つ。
            for (i in node.children.indices.reversed()) stack.addLast(node.children[i] to (ancestors + node))
        }
    }
    return result
}
