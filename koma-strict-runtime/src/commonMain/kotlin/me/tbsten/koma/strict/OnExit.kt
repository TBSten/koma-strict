package me.tbsten.koma.strict

import kotlin.reflect.KClass

/**
 * Declares a handler invoked when exiting the state. Can be applied to a state,
 * an intermediate sealed type, or the root.
 *
 * **There is no `nextState` parameter** — koma's `ExitScope` cannot transition
 * (nor cancel or override a transition), so the transition capability itself does not exist.
 * The koma-side constraint is mirrored directly into the types: no API surface is created
 * that would even look like it accepts `nextState`.
 *
 * - The handler type is `suspend <State>ExitScope.() -> Unit` (no Reaction return value).
 *   With no transition choices, the "forced reaction" layer does not apply
 * - The handler param name is `exit` (symmetric with [OnEnter]'s `enter`)
 * - Applied to an intermediate sealed type / root = scope-shared exit
 *   (expanded to every leaf, same shape as shared actions)
 * - `@OnExit()` (declared without emit) is also allowed — its only effect is making
 *   the handler mandatory (consistent with "declared = must be handled")
 * - Use cases: cleanup notifications, telemetry, enforcing "always emit on leave"
 *
 * @property emit Whitelist of events the handler may emit.
 *   For each declared event, an `emit{Event}(...)` function is generated on the handler scope.
 *   Undeclared events have no function at all.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
public annotation class OnExit(
    val emit: Array<KClass<*>> = [],
)
