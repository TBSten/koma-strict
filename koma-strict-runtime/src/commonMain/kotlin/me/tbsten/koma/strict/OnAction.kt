package me.tbsten.koma.strict

import koma.core.Event
import koma.core.State
import kotlin.reflect.KClass

/**
 * Declares a handler for a (state, action) pair. Can be applied to a leaf, an intermediate
 * sealed type, or the root.
 *
 * - Type argument [A] is the target action (marker only; generic annotations are verified
 *   to compile on Kotlin 2.2+. The type argument is not retained in bytecode — it exists
 *   in source/metadata only = dedicated to KSP/FIR source-level analysis)
 * - Applied to an intermediate sealed type / root, it becomes a scope-shared action
 *   (= the default block) and is expanded to every leaf under the scope at generation time.
 *   The default block's param name can be changed with [DefaultName]
 * - Duplicate declarations for the same (state, action) pair, or the same action declared
 *   on both an ancestor and a descendant, are KSP errors
 * - The handler's param name is the decapitalized action name (e.g. `Refresh` → `refresh = { ... }`)
 *
 * @property nextState Declares the states the handler may transition to (action capability rules).
 *   - `[]` (or omitted): `stayState()` only (for actions that only stay + emit;
 *     an empty list is sugar for `[Stay::class]`)
 *   - `[X::class]`: `nextState.toX()` only; staying is not allowed
 *   - `[Stay::class, X::class]`: either `stayState()` or `nextState.toX()` (conditional transition)
 *
 *   Elements must be concrete leaves of the same sealed hierarchy or [Stay]::class
 *   (intermediate sealed types are a KSP error; non-[State] classes are rejected by the
 *   `KClass<out State>` bound before KSP runs — [Stay] implements [State] to satisfy it).
 *   "Deliberately doing nothing" must also be declared explicitly (`nextState` undeclared +
 *   `{ stayState() }`) — never create inputs that are silently ignored.
 * @property emit Whitelist of events the handler may emit.
 *   For each declared event, an `emit{Event}(...)` function is generated on the handler scope.
 *   Undeclared events have no function at all.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
@Repeatable
public annotation class OnAction<A : Any>(
    val nextState: Array<KClass<out State>> = [],
    val emit: Array<KClass<out Event>> = [],
)
