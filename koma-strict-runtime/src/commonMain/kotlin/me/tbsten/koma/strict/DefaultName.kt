package me.tbsten.koma.strict

/**
 * Changes the parameter name of the shared action block (default).
 *
 * Can be applied **the same way** to the root (the sealed root annotated with [StoreSpec])
 * and to intermediate sealed interfaces (a single unified mechanism). Defaults to `"default"`.
 * Use it to avoid collisions with leaf state names, or for per-scope readability.
 *
 * ```kotlin
 * @DefaultName("refreshCommon")
 * @OnAction<MyAction.CancelRefresh>(nextState = [Idle::class])
 * sealed interface Refresh : Stable { ... }
 * // → the generated states() param name becomes refreshCommon:
 * //    refresh = { states(refreshCommon = { actions(...) }, ...) }
 * ```
 *
 * KSP validates collisions against state-derived param names in the same scope.
 *
 * @property name Parameter name of the default block.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
public annotation class DefaultName(
    val name: String,
)
