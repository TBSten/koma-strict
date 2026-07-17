package me.tbsten.koma.strict.ksp.model

import me.tbsten.koma.strict.InternalKomaStrictApi

/**
 * Path to a state node, excluding the root (in source declaration nesting order).
 * The root itself is the empty path.
 *
 * Example: `MyState.Stable.Idle` -> `StatePath("Stable", "Idle")`.
 * Generated type names are "the path concatenated without the root", so this path is
 * the input to naming (doc/internal/generate-strict-store-factory-dsl.md, "generated artifacts").
 */
@InternalKomaStrictApi
public data class StatePath(
    val segments: List<String>,
) {
    public constructor(vararg segments: String) : this(segments.toList())

    /** Whether this path points to the root itself. */
    val isRoot: Boolean
        get() = segments.isEmpty()

    /** simpleName of the node this path points to. Null for the root (the root's name is held by [RootNode.type]). */
    val simpleName: String?
        get() = segments.lastOrNull()

    /** Returns the path to a child node. */
    public operator fun plus(segment: String): StatePath = StatePath(segments + segment)

    /** Dot-joined form (`Stable.Idle`). Empty string for the root. */
    public fun dotJoined(): String = segments.joinToString(".")

    public companion object {
        /** The empty path pointing to the root itself. */
        public val root: StatePath = StatePath(emptyList())
    }
}
