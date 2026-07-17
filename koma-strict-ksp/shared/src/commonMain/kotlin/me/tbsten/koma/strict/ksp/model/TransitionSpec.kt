package me.tbsten.koma.strict.ksp.model

import me.tbsten.koma.strict.InternalKomaStrictApi

/**
 * Normalized representation of a `nextState` declaration — the list of transition-target
 * leaves and whether Stay is allowed. These two determine the handler's full capability
 * (doc/internal/generate-strict-store-factory-dsl.md, action capability rules).
 *
 * Normalization: an empty `nextState` declaration (or omission) is sugar for `[Stay::class]`.
 * The frontend constructs already-desugared values via [of].
 */
@InternalKomaStrictApi
public data class TransitionSpec(
    /** Paths to the target concrete leaves (source declaration order). `Stay::class` is not included (separated into [canStay]). */
    val targets: List<StatePath>,
    /** Derived from a `Stay::class` declaration (or the empty-list sugar). When true, `stayState()` is generated. */
    val canStay: Boolean,
) {
    public companion object {
        /** The normalized form of stay-only (`nextState = []` / omitted). */
        public val stayOnly: TransitionSpec = TransitionSpec(targets = emptyList(), canStay = true)

        /**
         * Normalizes from the declaration's raw values.
         * [targets] must not contain `Stay::class`; pass its presence separately as [declaredStay].
         */
        public fun of(
            targets: List<StatePath>,
            declaredStay: Boolean,
        ): TransitionSpec =
            TransitionSpec(
                targets = targets,
                canStay = declaredStay || targets.isEmpty(),
            )
    }
}
