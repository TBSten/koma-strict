package me.tbsten.koma.strict.idea.model

/**
 * Path to a state node inside one store, relative to the `@StoreSpec` root (source nesting order).
 * The root itself is [Root] (the empty path).
 *
 * Example: `LceState.Stable.Idle` -> `StateId("Stable", "Idle")`. The root-relative form matches the
 * "path concatenated without the root" convention used by the generated code, and keeps the diagram
 * model free of package noise.
 */
data class StateId(val segments: List<String>) {
    constructor(vararg segments: String) : this(segments.toList())

    /** Whether this id points to the root itself. */
    val isRoot: Boolean get() = segments.isEmpty()

    /** simpleName of the pointed node (`Stable.Idle` -> `Idle`). Null for the root. */
    val simpleName: String? get() = segments.lastOrNull()

    /** Dot-joined form (`Stable.Idle`). Empty string for the root. */
    val dotted: String get() = segments.joinToString(".")

    /** Returns the id of a child node. */
    operator fun plus(segment: String): StateId = StateId(segments + segment)

    override fun toString(): String = if (isRoot) "<root>" else dotted

    companion object {
        /** The empty path pointing to the root itself. */
        val Root: StateId = StateId(emptyList())
    }
}

/**
 * Opaque handle from a diagram node back to its source declaration.
 *
 * Kept as a marker interface so the pure diagram model (and the lowering / layout that consume it)
 * never depends on IntelliJ PSI. The Analysis-API frontend implements it with a PSI-backed value
 * (`PsiSourceAnchor`, wrapping a `SmartPsiElementPointer<KtClass>`) that the click-to-declaration
 * feature resolves; pure unit tests leave it null or supply a fake.
 */
interface SourceAnchor
