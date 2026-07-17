package me.tbsten.koma.strict.ksp.model

import me.tbsten.koma.strict.InternalKomaStrictApi

/**
 * Visibility of the generated artifacts, inherited from the state declarations
 * (doc/internal/generate-strict-store-factory-dsl.md, visibility policy:
 * "supporting types are public (inheriting the state declaration's visibility)").
 *
 * v1 supports public / internal only (private / protected states are explicitly rejected
 * by the frontend with a KSP error). If even one state declaration in the hierarchy is
 * internal, the whole store spec is lowered to internal — generated types cross-reference
 * the root type and other leaf types, so mixed per-node visibility would produce the
 * "public generated type exposes an internal type in its signature" compile error.
 */
@InternalKomaStrictApi
public enum class StateVisibility(
    /** Visibility modifier prepended to generated declarations. */
    public val keyword: String,
) {
    PUBLIC("public"),
    INTERNAL("internal"),
}
