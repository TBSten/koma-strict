package me.tbsten.koma.strict.ksp.model

import me.tbsten.koma.strict.InternalKomaStrictApi

/**
 * A property declared by a state node.
 *
 * Input to Impl / factory generation for interface-declared states, and to transition
 * default values (carrying over same-named props = cream-style property matching).
 */
@InternalKomaStrictApi
public data class StateProp(
    val name: String,
    /**
     * Rendered type string (e.g. `kotlin.String` / `kotlin.collections.List<kotlin.String>`).
     * Nullability is not included here; it is held by [isNullable] (the frontend renders
     * this from the KSP type and passes it in).
     */
    val type: String,
    val isNullable: Boolean = false,
)
