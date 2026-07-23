package me.tbsten.koma.strict.idea.model

/**
 * The slim, figure-oriented model of a single `@StoreSpec` store (`ide.md` architecture).
 *
 * Built by the Analysis-API frontend from a `KtClass`, then consumed by the graph lowering and the
 * BFS layout. It holds only what the diagram + transition table need: the state tree, the initial
 * targets, and the reachable-leaf set. When the frontend cannot fully resolve the declarations it
 * still returns a model with [degraded] = true (state simple names only, no triggers) rather than
 * failing — the tool window degrades gracefully instead of going blank.
 */
data class StoreDiagramModel(
    /** State declaration tree. */
    val root: RootState,
    /** `@StoreSpec(initial = [...])` targets (declaration order) — the `[*]` edges and reachability seeds. */
    val initial: List<StateId> = emptyList(),
    /** Ids of the leaves reachable from [initial] (computed by [Reachability]). */
    val reachableLeafIds: Set<StateId> = emptySet(),
    /** True when the frontend fell back to a names-only model (analysis failed). */
    val degraded: Boolean = false,
    /**
     * True when analysis succeeded but some `nextState` / `emit` / type-argument values could not be
     * resolved (foreign or error types). The tree still carries whatever resolved, with the
     * unresolved values kept as [UNRESOLVED_MARKER]; the UI flags this as incomplete analysis so a
     * half-typed or cross-store declaration is never shown as if it declared nothing. Distinct from
     * [degraded], which drops all triggers.
     */
    val unresolved: Boolean = false,
    /** Human-readable reason for a degraded / error model, if any. */
    val error: String? = null,
    /** Package of the `@StoreSpec` root's file, e.g. `com.example.feed` (for generated test files); empty in pure tests. */
    val packageName: String = "",
    /**
     * Named `@FlowSpec` paths declared on the root (`flows-design.md`), for the diagram's flow dropdown /
     * step playback. Empty when the store declares none, and always empty for a [degraded] model (flows
     * need resolved triggers). Each flow's steps carry model references; the diagram layer resolves them
     * to concrete nodes / edges at playback time.
     */
    val flows: List<DiagramFlow> = emptyList(),
) {
    /** All concrete leaves of the store (source order). */
    val leaves: List<LeafState> get() = root.leaves()

    /** Leaf lookup by id. */
    fun leaf(id: StateId): LeafState? = leaves.firstOrNull { it.id == id }

    /** Whether the given leaf is reachable from [initial]. Unknown / unresolved leaves count as reachable. */
    fun isReachable(id: StateId): Boolean =
        reachableLeafIds.isEmpty() || id in reachableLeafIds

    /** Conventional store class name of this diagram: `FeedState` -> `FeedStore`, `Tabs` -> `TabsStore`. */
    val storeName: String get() = storeNameOf(root.simpleName)
}

/** Conventional store class name for a state-root simple name: `FeedState` -> `FeedStore`, `Tabs` -> `TabsStore`. */
fun storeNameOf(rootSimpleName: String): String =
    if (rootSimpleName.endsWith("State")) rootSimpleName.removeSuffix("State") + "Store" else rootSimpleName + "Store"

/**
 * Concrete leaves that a trigger declared on [scope] applies to:
 * - a leaf scope applies to that leaf only,
 * - the root / an intermediate sealed scope applies to every leaf under it (shared action / recover).
 */
fun StoreDiagramModel.leavesInScope(scope: DiagramStateNode): List<LeafState> =
    scope.leaves()
