package me.tbsten.koma.strict

import kotlin.reflect.KClass

/**
 * The entry-point declaration of a store spec. Applied to a sealed state root.
 *
 * The sealed hierarchy annotated with this becomes koma-strict's analysis target, and a
 * `states()` extension on `StoreBuilder` plus supporting types (Impl / factory / Scope /
 * Transitions / Reaction / Handlers) are generated.
 *
 * Design doc: doc/internal/generate-strict-store-factory-dsl.md
 *
 * @property actions The sealed root of the actions handled by the store.
 *   When omitted (= [Nothing]::class), the common sealed supertype of all [OnAction]
 *   type arguments is inferred. With no action declarations at all, inference is
 *   impossible → KSP error (an explicit value is required). A KSP error is also reported
 *   when the explicit value contradicts the inference result.
 * @property events The sealed root of the events emitted by the store.
 *   When omitted (= [Nothing]::class), it is inferred from all emit declarations.
 *   With zero emit declarations, code is generated with `E = Nothing`.
 * @property initial Initial state candidates (optional, multiple allowed; empty = undeclared).
 *   They serve as (1) the starting points of reachability analysis (all elements) and
 *   (2) the `[*] -->` edges of the transition diagram.
 *   Since initialState is passed directly to koma's standard `Store` factory, no default
 *   argument is generated and no runtime type enforcement is performed
 *   (keeping process restore and starting from an intermediate state in tests possible).
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
public annotation class StoreSpec(
    val actions: KClass<*> = Nothing::class,
    val events: KClass<*> = Nothing::class,
    val initial: Array<KClass<*>> = [],
)
