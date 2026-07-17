package me.tbsten.koma.strict

import kotlin.reflect.KClass

/**
 * Declares a handler invoked when entering the state.
 *
 * The declared state's handler (param name `enter`) becomes a required named parameter of
 * the generated `actions(...)` (= it cannot be forgotten). The [nextState] / [emit] rules
 * are the same as [OnAction].
 *
 * Note: koma also fires enter for the initial state at startup
 * (`initializeIfNeeded` → `onStateEntered`).
 *
 * @property nextState Declares the states the handler may transition to (action capability rules).
 *   - `[]` (or omitted): `stayState()` only (for handlers that only stay + emit;
 *     an empty list is sugar for `[Stay::class]`)
 *   - `[X::class]`: `nextState.toX()` only; staying is not allowed
 *   - `[Stay::class, X::class]`: either `stayState()` or `nextState.toX()` (conditional transition)
 *
 *   Elements must be concrete leaves of the same sealed hierarchy or [Stay]::class
 *   (intermediate sealed types are a KSP error).
 * @property emit Whitelist of events the handler may emit.
 *   For each declared event, an `emit{Event}(...)` function is generated on the handler scope.
 *   Undeclared events have no function at all.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
public annotation class OnEnter(
    val nextState: Array<KClass<*>> = [],
    val emit: Array<KClass<*>> = [],
)
