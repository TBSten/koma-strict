package me.tbsten.koma.strict

import kotlin.reflect.KClass

/**
 * Declares a handler invoked when an exception [E] is caught. Can be applied to a state,
 * an intermediate sealed type, or the root.
 *
 * Same shape as [OnAction]: type argument [E] is the target exception (marker only,
 * dedicated to source-level analysis), and the [nextState] list determines the handler's
 * full capability. The handler scope exposes `error: E` (the caught exception) in the
 * position where `action` would be.
 *
 * - The upper bound is [Exception] (matching koma's `recover`; not `Throwable`).
 *   `CancellationException` / `Error` types are never caught
 * - Applied to the root / an intermediate sealed type = scope-wide error handling
 *   (expanded to every leaf). Cross-cutting error handling is the primary use case of recover
 * - Duplicate declarations of the same exception type on the same state, or on both an
 *   ancestor and a descendant, are KSP errors
 * - The handler's param name is `recover{Exception}` (e.g. `@OnRecover<FetchException>` →
 *   `recoverFetchException = { ... }`)
 * - Coexists with inline catch inside enter/action handlers: whether to catch yourself or
 *   throw and delegate to `@OnRecover` is the user's choice (not enforced)
 * - Use case: declaratively enforce "when exception E occurs in this state, always go to
 *   the Error state and emit an event"
 *
 * @property nextState Declares the states the handler may transition to (action capability rules).
 *   - `[]` (or omitted): `stayState()` only (an empty list is sugar for `[Stay::class]`)
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
@Repeatable
public annotation class OnRecover<E : Exception>(
    val nextState: Array<KClass<*>> = [],
    val emit: Array<KClass<*>> = [],
)
