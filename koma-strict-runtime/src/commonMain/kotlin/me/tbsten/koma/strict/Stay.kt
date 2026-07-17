package me.tbsten.koma.strict

/**
 * Marker object placed in the elements of [OnAction.nextState] / [OnEnter.nextState] /
 * [OnRecover.nextState] to declare that "staying in the current state is also allowed".
 *
 * Action capability rules (the `nextState` list determines the handler's full capability):
 *
 * - `nextState = []` (or omitted): `stayState()` only — **an empty list is sugar for `[Stay::class]`**
 * - `nextState = [X::class]`: `nextState.toX()` only; staying is not allowed (`stayState()` is not generated)
 * - `nextState = [Stay::class, X::class]`: either `stayState()` or `nextState.toX()` (conditional transition)
 *
 * Staying is implemented by simply not calling koma's `nextState` (documented behavior:
 * no instance is created and identity is preserved). A self-transition (including the state's
 * own class in `nextState`) is **not** the same as staying — it recreates the state.
 *
 * Note that under koma's default policy, pending actions are discarded only on a transition
 * to a different class; they survive both staying and self-transitions.
 */
public object Stay
