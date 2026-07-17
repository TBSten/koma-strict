package me.tbsten.koma.strict.ksp.model

import me.tbsten.koma.strict.InternalKomaStrictApi

/**
 * An event reference for one element of an `emit = [...]` declaration.
 *
 * Because the generated scope's `emit{Event}(...)` takes the event's construction
 * arguments as its own parameters, the frontend resolves and carries construction
 * info ([isObject] / [params]) in addition to the type reference ([type]).
 */
@InternalKomaStrictApi
public data class EventRef(
    /** Reference to the event type. */
    val type: TypeRef,
    /** Event declared as a data object (no construction arguments; emitted via the instance reference). */
    val isObject: Boolean = false,
    /**
     * The event's primary constructor parameters (become the arguments of `emit{Event}`).
     * Empty when [isObject] is true. [StateProp] was defined for states, but its shape
     * ("name + rendered type + nullability") is identical, so it is reused here.
     */
    val params: List<StateProp> = emptyList(),
)
