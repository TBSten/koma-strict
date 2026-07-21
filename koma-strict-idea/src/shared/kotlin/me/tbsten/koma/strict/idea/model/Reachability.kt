package me.tbsten.koma.strict.idea.model

/**
 * Pure reachability analysis over the slim model: which leaves are reachable from `@StoreSpec.initial`
 * following enter / action / recover transitions (exit has no target).
 *
 * Semantics (mirrors the KSP StoreSpec model):
 * - the seeds are the [StoreDiagramModel.initial] leaves,
 * - a leaf's own triggers make their targets reachable once that leaf is reachable,
 * - a scope-shared trigger on the root / an intermediate sealed node fires from every leaf under
 *   that scope, so its targets become reachable once *any* in-scope leaf is reachable,
 * - `Stay` and self-transitions add nothing new; exit contributes no target.
 *
 * When `initial` is empty, analysis is skipped (no starting point) and an empty set is returned;
 * callers then treat every leaf as reachable (see [StoreDiagramModel.isReachable]).
 */
object Reachability {

    fun compute(root: RootState, initial: List<StateId>): Set<StateId> {
        if (initial.isEmpty()) return emptySet()

        val allLeafIds = root.leaves().map { it.id }.toSet()
        val reachable = initial.filterTo(mutableSetOf()) { it in allLeafIds }

        // 各ノードの trigger を「発火元 leaf 群 -> target 群」に平坦化しておく。
        val rules: List<Rule> = buildRules(root)

        var changed = true
        while (changed) {
            changed = false
            for (rule in rules) {
                if (rule.sources.none { it in reachable }) continue
                for (target in rule.targets) {
                    if (target in allLeafIds && reachable.add(target)) changed = true
                }
            }
        }
        return reachable
    }

    private class Rule(val sources: List<StateId>, val targets: List<StateId>)

    private fun buildRules(root: RootState): List<Rule> {
        val rules = mutableListOf<Rule>()
        for (node in root.walk()) {
            val sources = node.leaves().map { it.id }
            if (sources.isEmpty()) continue
            val triggers = buildList {
                if (node is LeafState) node.enter?.let { add(it) }
                addAll(node.actions)
                addAll(node.recovers)
            }
            for (trigger in triggers) {
                if (trigger.targets.isNotEmpty()) rules += Rule(sources, trigger.targets)
            }
        }
        return rules
    }
}
