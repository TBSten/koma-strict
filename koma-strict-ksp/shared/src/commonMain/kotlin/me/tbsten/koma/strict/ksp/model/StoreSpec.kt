package me.tbsten.koma.strict.ksp.model

import me.tbsten.koma.strict.InternalKomaStrictApi

/**
 * KSP-independent model that fully represents one store's declaration info (the StoreSpec model).
 *
 * Validation, code generation, and diagram IR construction depend on this model only;
 * the KSP processor is strictly a frontend doing "symbol resolution → StoreSpec model
 * construction" (doc/internal/generate-strict-store-factory-dsl.md open issue 7 /
 * the layering in .local/design/migration-analysis-api.md).
 */
@InternalKomaStrictApi
public data class StoreSpec(
    /** State declaration tree (the sealed root annotated with `@StoreSpec`). */
    val root: RootNode,
    /**
     * Sealed root of the actions hierarchy.
     * The value already resolved by the frontend — either explicit `@StoreSpec(actions = ...)`
     * or inference from declarations.
     */
    val actionsType: TypeRef,
    /** Sealed root of the events hierarchy. Null = zero event declarations (generated with `E = Nothing`). */
    val eventsType: TypeRef?,
    /** `@StoreSpec(initial = [...])` (declaration order). Starting points of reachability analysis and `[*]` edges of the transition diagram. */
    val initial: List<StatePath> = emptyList(),
    /** Visibility of the generated artifacts (inherited from state declarations). See [StateVisibility]. */
    val visibility: StateVisibility = StateVisibility.PUBLIC,
)
