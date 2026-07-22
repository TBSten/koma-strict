package me.tbsten.koma.strict.idea.model

/**
 * A single transition of the store, flattened out of the model tree. Formerly backed the Transitions
 * table UI (removed in `ide-3.md`); kept as a pure model projection because it is the convenient
 * assertion surface for the frontend model-builder tests (the raw tree is verbose to traverse).
 */
data class TransitionRow(
    val from: String,
    val trigger: String,
    val stay: Boolean,
    val to: String,
    val emit: String,
    val reachable: Boolean,
    /**
     * Declaration of the `from` node. A leaf row points to the leaf; a scope-shared `any` /
     * `any <Group>` row points to the root / group declaration.
     */
    val source: SourceAnchor? = null,
)

/**
 * Flattens the model tree into transition rows. Leaf triggers read as the leaf name; a scope-shared
 * trigger on the root / an intermediate sealed node reads as `any` / `any <Group>` (mirroring the
 * any-state pseudo node). Trigger tokens match the figure (`onEnter` / decapitalized action /
 * `on <Exception>`). A stay-only trigger yields a single `(self)` row.
 */
fun StoreDiagramModel.transitionRows(): List<TransitionRow> {
    val out = mutableListOf<TransitionRow>()
    for (node in root.walk()) {
        val from = when (node) {
            is RootState -> "any"
            is GroupState -> "any ${node.simpleName}"
            is LeafState -> node.simpleName
        }
        // scope-shared (root / group) 行も宣言へ飛べるよう、node 種別を問わず自身の source を運ぶ。
        val source = node.source
        val reachable = node !is LeafState || isReachable(node.id)
        val triggers = buildList {
            if (node is LeafState) node.enter?.let { add(it) }
            addAll(node.actions)
            addAll(node.recovers)
        }
        for (trigger in triggers) {
            val token = triggerToken(trigger)
            val emit = trigger.emits.joinToString(", ")
            for (target in trigger.targets) {
                out += TransitionRow(from, token, stay = false, to = target.simpleName ?: target.dotted, emit = emit, reachable = reachable, source = source)
            }
            // 未解決 target (foreign / error type) も隠さない。図は leaf でないので描かないが、
            // 「宣言はあるが解決できていない」ことを ?付きで残す (silent truncation を許さない)。
            for (unresolved in trigger.unresolvedTargets) {
                out += TransitionRow(from, token, stay = false, to = unresolved, emit = emit, reachable = reachable, source = source)
            }
            if (trigger.stay) {
                // Stay は独立列ではなく To に畳む: 同じ state に留まる = 「<from> (stay)」。
                out += TransitionRow(from, token, stay = true, to = "$from (stay)", emit = emit, reachable = reachable, source = source)
            }
            if (trigger.targets.isEmpty() && trigger.unresolvedTargets.isEmpty() && !trigger.stay) {
                out += TransitionRow(from, token, stay = false, to = "—", emit = emit, reachable = reachable, source = source)
            }
        }
        // @OnExit は遷移を持たない (To = —) が emit するので隠さない。
        node.exit?.let { exit ->
            out += TransitionRow(from, "exit", stay = false, to = "—", emit = exit.emits.joinToString(", "), reachable = reachable, source = source)
        }
    }
    return out
}

private fun triggerToken(trigger: DiagramTrigger): String = when (trigger) {
    is EnterTrigger -> "onEnter"
    is ActionTrigger -> trigger.actionName.replaceFirstChar { it.lowercase() }
    is RecoverTrigger -> "on ${trigger.exceptionName}"
}
