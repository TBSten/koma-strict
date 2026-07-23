## Input:CounterState.kt

```kt
package example.counter

import koma.core.Action
import koma.core.State
import me.tbsten.koma.strict.OnAction
import me.tbsten.koma.strict.StoreSpec

@StoreSpec(initial = [CounterState.Idle::class])
sealed interface CounterState : State {
    companion object

    @OnAction<CounterAction.Increment>(nextState = [Idle::class])       // 自己遷移
    @OnAction<CounterAction.Reset>(nextState = [Confirming::class])
    data class Idle(val count: Int) : CounterState { companion object } // data class 宣言 (従来通り可)

    @OnAction<CounterAction.Confirm>(nextState = [Idle::class])
    @OnAction<CounterAction.Cancel>                                     // nextState 省略 = stay のみ
    data class Confirming(val count: Int, val message: String? = null) : CounterState {
        companion object
    }
}

sealed interface CounterAction : Action {
    data object Increment : CounterAction
    data object Reset : CounterAction
    data object Confirm : CounterAction
    data object Cancel : CounterAction
}
```

## Input:CounterStoreUsage.kt

```kt
package example.counter

import koma.core.Store

fun buildCounterStore(): Store<CounterState, CounterAction, Nothing> =
    Store<CounterState, CounterAction, Nothing>(initialState = CounterState.Idle(count = 0)) {
        states(
            idle = CounterState.Idle.actions(
                increment = { nextState.toIdle(count = state.count + 1) },
                // count は持ち越し / message は新規 = 必須 (data class 宣言の
                // default 値は遷移関数へは伝播しない — デフォルト値の源は state のみ)
                reset = { nextState.toConfirming(message = null) },
            ),
            confirming = CounterState.Confirming.actions(
                confirm = { nextState.toIdle() },          // count は持ち越し
                cancel = { stayState() },                  // stay のみ宣言のアクション
            ),
        )
    }
```

## KSP options

```kt
ksp {
    arg("koma.strict.deadActionSeverity", "WARNING" /* default */)
}
```

## Output:ExitCode

```text
OK
```

## Output:Console

```text

```

## Output:Generated sources

```kt
// file: CounterState.Confirming.generated.kt
package example.counter

import me.tbsten.koma.strict.*
import me.tbsten.koma.strict.dsl.*

public sealed interface ConfirmingConfirmReaction {
    public class Transition internal constructor(
        internal val next: CounterState,
    ) : ConfirmingConfirmReaction
}

public class ConfirmingConfirmTransitions internal constructor(
    private val state: CounterState.Confirming,
) {
    public fun toIdle(
        count: Int = state.count,
    ): ConfirmingConfirmReaction = ConfirmingConfirmReaction.Transition(CounterState.Idle(count))
}

@KomaStrictDsl
@koma.core.KomaStoreDsl
@OptIn(InternalKomaStrictApi::class)
public class ConfirmingConfirmScope internal constructor(
    public val state: CounterState.Confirming,
    public val action: CounterAction.Confirm,
    onClearPendingActions: () -> Unit,
) : HandlerScope(onClearPendingActions) {
    public val nextState: ConfirmingConfirmTransitions = ConfirmingConfirmTransitions(state)
}

public sealed interface ConfirmingCancelReaction {
    public data object Stay : ConfirmingCancelReaction
}

@KomaStrictDsl
@koma.core.KomaStoreDsl
@OptIn(InternalKomaStrictApi::class)
public class ConfirmingCancelScope internal constructor(
    public val state: CounterState.Confirming,
    public val action: CounterAction.Cancel,
    onClearPendingActions: () -> Unit,
) : HandlerScope(onClearPendingActions) {
    /** Chooses to stay in the current state. This simply does not call koma's nextState: no instance is created and pending actions are not discarded. */
    public fun stayState(): ConfirmingCancelReaction = ConfirmingCancelReaction.Stay
}

public class ConfirmingHandlers internal constructor(
    internal val confirm: suspend ConfirmingConfirmScope.() -> ConfirmingConfirmReaction,
    internal val cancel: suspend ConfirmingCancelScope.() -> ConfirmingCancelReaction,
    internal val configure: koma.core.StoreBuilder.StateHandlerConfig<CounterState, CounterAction, Nothing, CounterState.Confirming>.() -> Unit,
) : (ConfirmingHandlersScope) -> ConfirmingHandlers {
    /** Returns this bundle itself, so an already-built value is assignable to the scope-lambda-typed `states(...)` parameter (value-passing / scope-lambda interchange). */
    override fun invoke(p1: ConfirmingHandlersScope): ConfirmingHandlers = this
}

/** Bundles this state's handlers. [configure] appends raw koma DSL at the end of the generated `state<...> {}` block (per-state escape hatch). */
public fun CounterState.Confirming.Companion.actions(
    confirm: suspend ConfirmingConfirmScope.() -> ConfirmingConfirmReaction,
    cancel: suspend ConfirmingCancelScope.() -> ConfirmingCancelReaction,
    configure: koma.core.StoreBuilder.StateHandlerConfig<CounterState, CounterAction, Nothing, CounterState.Confirming>.() -> Unit = {},
): ConfirmingHandlers = ConfirmingHandlers(confirm, cancel, configure)

/**
 * Builder-form overload of `actions(...)` (see [ConfirmingActionsBuilder]).
 *
 * Note: unlike the named-argument overload, exhaustiveness of the declared handlers is
 * checked **at build time** (fail-fast when the block finishes), not at compile time.
 * This overload exists only for states without enter / exit declarations (those handlers
 * have no builder member).
 */
public fun CounterState.Confirming.Companion.actions(build: ConfirmingActionsBuilder.() -> Unit): ConfirmingHandlers = ConfirmingActionsBuilder().apply(build).build()

/** Receiver of the scope-lambda form (`{ actions(...) }`) of the matching `states(...)` parameter. [actions] mirrors the companion `actions(...)` extension. */
@KomaStrictDsl
@koma.core.KomaStoreDsl
public class ConfirmingHandlersScope internal constructor() {
    /** Bundles this state's handlers. [configure] appends raw koma DSL at the end of the generated `state<...> {}` block (per-state escape hatch). */
    public fun actions(
        confirm: suspend ConfirmingConfirmScope.() -> ConfirmingConfirmReaction,
        cancel: suspend ConfirmingCancelScope.() -> ConfirmingCancelReaction,
        configure: koma.core.StoreBuilder.StateHandlerConfig<CounterState, CounterAction, Nothing, CounterState.Confirming>.() -> Unit = {},
    ): ConfirmingHandlers = ConfirmingHandlers(confirm, cancel, configure)

    /**
     * Builder-form overload of `actions(...)` (see [ConfirmingActionsBuilder]).
     *
     * Note: unlike the named-argument overload, exhaustiveness of the declared handlers is
     * checked **at build time** (fail-fast when the block finishes), not at compile time.
     * This overload exists only for states without enter / exit declarations (those handlers
     * have no builder member).
     */
    public fun actions(build: ConfirmingActionsBuilder.() -> Unit): ConfirmingHandlers = ConfirmingActionsBuilder().apply(build).build()
}

/**
 * Builder receiver of the `actions { ... }` overload building [ConfirmingHandlers].
 *
 * Each member function registers the handler of the same-named `actions(...)` parameter.
 * Exhaustiveness is checked when the block finishes (build-time fail-fast), not at compile
 * time: handlers left unregistered — and any duplicate registration — throw
 * [IllegalStateException]. Prefer the named-argument `actions(...)` overload to catch
 * missing handlers at compile time.
 */
@KomaStrictDsl
@koma.core.KomaStoreDsl
@OptIn(InternalKomaStrictApi::class)
public class ConfirmingActionsBuilder internal constructor() {
    private val confirm = SetOnceSlot<suspend ConfirmingConfirmScope.() -> ConfirmingConfirmReaction>("CounterState.Confirming", "confirm")

    private val cancel = SetOnceSlot<suspend ConfirmingCancelScope.() -> ConfirmingCancelReaction>("CounterState.Confirming", "cancel")

    private val configure = SetOnceSlot<koma.core.StoreBuilder.StateHandlerConfig<CounterState, CounterAction, Nothing, CounterState.Confirming>.() -> Unit>("CounterState.Confirming", "configure")

    /** Registers the handler of the same-named `actions(...)` parameter. Duplicate registration fails fast with [IllegalStateException]. */
    public fun confirm(handler: suspend ConfirmingConfirmScope.() -> ConfirmingConfirmReaction) { confirm.set(handler) }

    /** Registers the handler of the same-named `actions(...)` parameter. Duplicate registration fails fast with [IllegalStateException]. */
    public fun cancel(handler: suspend ConfirmingCancelScope.() -> ConfirmingCancelReaction) { cancel.set(handler) }

    /** Appends raw koma DSL at the end of the generated `state<...> {}` block (the trailing `configure` parameter of `actions(...)`). Duplicate registration fails fast with [IllegalStateException]. */
    public fun configure(block: koma.core.StoreBuilder.StateHandlerConfig<CounterState, CounterAction, Nothing, CounterState.Confirming>.() -> Unit) { configure.set(block) }

    internal fun build(): ConfirmingHandlers {
        val missing = listOfNotNull(
            "confirm".takeIf { !confirm.isSet },
            "cancel".takeIf { !cancel.isSet },
        )
        if (missing.isNotEmpty()) throwMissingBuilderEntries("CounterState.Confirming", missing)
        return ConfirmingHandlers(confirm.getOrNull()!!, cancel.getOrNull()!!, configure.getOrNull() ?: {})
    }
}

// ----- next file -----

// file: CounterState.Idle.generated.kt
package example.counter

import me.tbsten.koma.strict.*
import me.tbsten.koma.strict.dsl.*

public sealed interface IdleIncrementReaction {
    public class Transition internal constructor(
        internal val next: CounterState,
    ) : IdleIncrementReaction
}

public class IdleIncrementTransitions internal constructor(
    private val state: CounterState.Idle,
) {
    public fun toIdle(
        count: Int = state.count,
    ): IdleIncrementReaction = IdleIncrementReaction.Transition(CounterState.Idle(count))
}

@KomaStrictDsl
@koma.core.KomaStoreDsl
@OptIn(InternalKomaStrictApi::class)
public class IdleIncrementScope internal constructor(
    public val state: CounterState.Idle,
    public val action: CounterAction.Increment,
    onClearPendingActions: () -> Unit,
) : HandlerScope(onClearPendingActions) {
    public val nextState: IdleIncrementTransitions = IdleIncrementTransitions(state)
}

public sealed interface IdleResetReaction {
    public class Transition internal constructor(
        internal val next: CounterState,
    ) : IdleResetReaction
}

public class IdleResetTransitions internal constructor(
    private val state: CounterState.Idle,
) {
    public fun toConfirming(
        count: Int = state.count,
        message: String?,
    ): IdleResetReaction = IdleResetReaction.Transition(CounterState.Confirming(count, message))
}

@KomaStrictDsl
@koma.core.KomaStoreDsl
@OptIn(InternalKomaStrictApi::class)
public class IdleResetScope internal constructor(
    public val state: CounterState.Idle,
    public val action: CounterAction.Reset,
    onClearPendingActions: () -> Unit,
) : HandlerScope(onClearPendingActions) {
    public val nextState: IdleResetTransitions = IdleResetTransitions(state)
}

public class IdleHandlers internal constructor(
    internal val increment: suspend IdleIncrementScope.() -> IdleIncrementReaction,
    internal val reset: suspend IdleResetScope.() -> IdleResetReaction,
    internal val configure: koma.core.StoreBuilder.StateHandlerConfig<CounterState, CounterAction, Nothing, CounterState.Idle>.() -> Unit,
) : (IdleHandlersScope) -> IdleHandlers {
    /** Returns this bundle itself, so an already-built value is assignable to the scope-lambda-typed `states(...)` parameter (value-passing / scope-lambda interchange). */
    override fun invoke(p1: IdleHandlersScope): IdleHandlers = this
}

/** Bundles this state's handlers. [configure] appends raw koma DSL at the end of the generated `state<...> {}` block (per-state escape hatch). */
public fun CounterState.Idle.Companion.actions(
    increment: suspend IdleIncrementScope.() -> IdleIncrementReaction,
    reset: suspend IdleResetScope.() -> IdleResetReaction,
    configure: koma.core.StoreBuilder.StateHandlerConfig<CounterState, CounterAction, Nothing, CounterState.Idle>.() -> Unit = {},
): IdleHandlers = IdleHandlers(increment, reset, configure)

/**
 * Builder-form overload of `actions(...)` (see [IdleActionsBuilder]).
 *
 * Note: unlike the named-argument overload, exhaustiveness of the declared handlers is
 * checked **at build time** (fail-fast when the block finishes), not at compile time.
 * This overload exists only for states without enter / exit declarations (those handlers
 * have no builder member).
 */
public fun CounterState.Idle.Companion.actions(build: IdleActionsBuilder.() -> Unit): IdleHandlers = IdleActionsBuilder().apply(build).build()

/** Receiver of the scope-lambda form (`{ actions(...) }`) of the matching `states(...)` parameter. [actions] mirrors the companion `actions(...)` extension. */
@KomaStrictDsl
@koma.core.KomaStoreDsl
public class IdleHandlersScope internal constructor() {
    /** Bundles this state's handlers. [configure] appends raw koma DSL at the end of the generated `state<...> {}` block (per-state escape hatch). */
    public fun actions(
        increment: suspend IdleIncrementScope.() -> IdleIncrementReaction,
        reset: suspend IdleResetScope.() -> IdleResetReaction,
        configure: koma.core.StoreBuilder.StateHandlerConfig<CounterState, CounterAction, Nothing, CounterState.Idle>.() -> Unit = {},
    ): IdleHandlers = IdleHandlers(increment, reset, configure)

    /**
     * Builder-form overload of `actions(...)` (see [IdleActionsBuilder]).
     *
     * Note: unlike the named-argument overload, exhaustiveness of the declared handlers is
     * checked **at build time** (fail-fast when the block finishes), not at compile time.
     * This overload exists only for states without enter / exit declarations (those handlers
     * have no builder member).
     */
    public fun actions(build: IdleActionsBuilder.() -> Unit): IdleHandlers = IdleActionsBuilder().apply(build).build()
}

/**
 * Builder receiver of the `actions { ... }` overload building [IdleHandlers].
 *
 * Each member function registers the handler of the same-named `actions(...)` parameter.
 * Exhaustiveness is checked when the block finishes (build-time fail-fast), not at compile
 * time: handlers left unregistered — and any duplicate registration — throw
 * [IllegalStateException]. Prefer the named-argument `actions(...)` overload to catch
 * missing handlers at compile time.
 */
@KomaStrictDsl
@koma.core.KomaStoreDsl
@OptIn(InternalKomaStrictApi::class)
public class IdleActionsBuilder internal constructor() {
    private val increment = SetOnceSlot<suspend IdleIncrementScope.() -> IdleIncrementReaction>("CounterState.Idle", "increment")

    private val reset = SetOnceSlot<suspend IdleResetScope.() -> IdleResetReaction>("CounterState.Idle", "reset")

    private val configure = SetOnceSlot<koma.core.StoreBuilder.StateHandlerConfig<CounterState, CounterAction, Nothing, CounterState.Idle>.() -> Unit>("CounterState.Idle", "configure")

    /** Registers the handler of the same-named `actions(...)` parameter. Duplicate registration fails fast with [IllegalStateException]. */
    public fun increment(handler: suspend IdleIncrementScope.() -> IdleIncrementReaction) { increment.set(handler) }

    /** Registers the handler of the same-named `actions(...)` parameter. Duplicate registration fails fast with [IllegalStateException]. */
    public fun reset(handler: suspend IdleResetScope.() -> IdleResetReaction) { reset.set(handler) }

    /** Appends raw koma DSL at the end of the generated `state<...> {}` block (the trailing `configure` parameter of `actions(...)`). Duplicate registration fails fast with [IllegalStateException]. */
    public fun configure(block: koma.core.StoreBuilder.StateHandlerConfig<CounterState, CounterAction, Nothing, CounterState.Idle>.() -> Unit) { configure.set(block) }

    internal fun build(): IdleHandlers {
        val missing = listOfNotNull(
            "increment".takeIf { !increment.isSet },
            "reset".takeIf { !reset.isSet },
        )
        if (missing.isNotEmpty()) throwMissingBuilderEntries("CounterState.Idle", missing)
        return IdleHandlers(increment.getOrNull()!!, reset.getOrNull()!!, configure.getOrNull() ?: {})
    }
}

// ----- next file -----

// file: CounterState.storeSpec.generated.kt
package example.counter

import me.tbsten.koma.strict.*
import me.tbsten.koma.strict.dsl.*

@kotlin.jvm.JvmName("counterStateStates")
@Suppress("NAME_SHADOWING")
public fun koma.core.StoreBuilder<CounterState, CounterAction, Nothing>.states(
    idle: IdleHandlersScope.() -> IdleHandlers,
    confirming: ConfirmingHandlersScope.() -> ConfirmingHandlers,
    configure: CounterStateStatesConfigureScope.() -> Unit = {},
) {
    val idle = IdleHandlersScope().idle()
    val confirming = ConfirmingHandlersScope().confirming()
    val configure = CounterStateStatesConfigureScope().apply(configure)

    state<CounterState.Idle> {
        action<CounterAction.Increment> {
            when (val r = idle.increment(IdleIncrementScope(state, action, ::clearPendingActions))) {
                is IdleIncrementReaction.Transition -> nextState { r.next }
            }
        }
        action<CounterAction.Reset> {
            when (val r = idle.reset(IdleResetScope(state, action, ::clearPendingActions))) {
                is IdleResetReaction.Transition -> nextState { r.next }
            }
        }
        idle.configure(this)
        configure.idle?.invoke(this)
    }
    state<CounterState.Confirming> {
        action<CounterAction.Confirm> {
            when (val r = confirming.confirm(ConfirmingConfirmScope(state, action, ::clearPendingActions))) {
                is ConfirmingConfirmReaction.Transition -> nextState { r.next }
            }
        }
        action<CounterAction.Cancel> {
            confirming.cancel(ConfirmingCancelScope(state, action, ::clearPendingActions))
        }
        confirming.configure(this)
        configure.confirming?.invoke(this)
    }
}

/**
 * Builds a [koma.core.Store] for [CounterState] without spelling the store type arguments.
 *
 * Sugar over the canonical koma entry point — `Store<CounterState, CounterAction, Nothing>(initialState) { states(...) }`
 * builds the exact same store. [initialState] is narrowed to the declared
 * `@StoreSpec(initial = ...)` candidate [CounterState.Idle] — compile-time enforced.
 * [configuration] appends raw koma DSL after the generated handlers (store-level escape hatch).
 */
public fun createCounterStore(
    initialState: CounterState.Idle,
    idle: IdleHandlersScope.() -> IdleHandlers,
    confirming: ConfirmingHandlersScope.() -> ConfirmingHandlers,
    context: kotlin.coroutines.CoroutineContext? = null,
    configuration: koma.core.StoreBuilder<CounterState, CounterAction, Nothing>.() -> Unit = {},
): koma.core.Store<CounterState, CounterAction, Nothing> =
    koma.core.Store<CounterState, CounterAction, Nothing>(initialState = initialState, context = context) {
        states(
            idle = idle,
            confirming = confirming,
        )
        configuration()
    }

/**
 * Builds a [koma.core.Store] for [CounterState] without spelling the store type arguments.
 *
 * Sugar over the canonical koma entry point — `Store<CounterState, CounterAction, Nothing>(initialState) { states(...) }`
 * builds the exact same store. [initialState] accepts any [CounterState] — for
 * restoring a persisted state or starting mid-flow in tests, where [createCounterStore]'s
 * compile-time-narrowed initial state does not apply. [configuration] appends raw koma
 * DSL after the generated handlers (store-level escape hatch).
 */
public fun restoreCounterStore(
    initialState: CounterState,
    idle: IdleHandlersScope.() -> IdleHandlers,
    confirming: ConfirmingHandlersScope.() -> ConfirmingHandlers,
    context: kotlin.coroutines.CoroutineContext? = null,
    configuration: koma.core.StoreBuilder<CounterState, CounterAction, Nothing>.() -> Unit = {},
): koma.core.Store<CounterState, CounterAction, Nothing> =
    koma.core.Store<CounterState, CounterAction, Nothing>(initialState = initialState, context = context) {
        states(
            idle = idle,
            confirming = confirming,
        )
        configuration()
    }

/**
 * Receiver of the trailing `configure` block of the matching `states(...)` call — the
 * per-state escape hatch. Each member appends raw koma DSL to the generated `state<...> {}`
 * block(s) of the same-named child state, after the generated registrations (and after the
 * leaf's own `actions(configure = ...)` block). Calling the same member twice fails fast
 * with [IllegalStateException].
 *
 * Overlapping registrations (measured against koma-core rc02): koma dispatches at most one
 * handler per trigger — when multiple registered handlers match the same state and trigger,
 * the **first registered** one runs and later ones are silently ignored. Generated
 * registrations always precede this escape block, so a raw handler for a trigger already
 * declared on the state never runs; use this escape for what the declarations do not cover
 * (raw `enter` / `exit` / `action<A>` / `recover<T>` of undeclared triggers, `launch`,
 * dispatcher overrides, ...).
 */
@KomaStrictDsl
@koma.core.KomaStoreDsl
public class CounterStateStatesConfigureScope internal constructor() {
    internal var idle: (koma.core.StoreBuilder.StateHandlerConfig<CounterState, CounterAction, Nothing, CounterState.Idle>.() -> Unit)? = null

    internal var confirming: (koma.core.StoreBuilder.StateHandlerConfig<CounterState, CounterAction, Nothing, CounterState.Confirming>.() -> Unit)? = null

    /** Appends raw koma DSL at the end of the generated `state<CounterState.Idle> {}` block. Fails fast if called twice. */
    public fun idle(block: koma.core.StoreBuilder.StateHandlerConfig<CounterState, CounterAction, Nothing, CounterState.Idle>.() -> Unit) {
        if (this.idle != null) throwDuplicateBuilderEntry("CounterState", "idle")
        this.idle = block
    }

    /** Appends raw koma DSL at the end of the generated `state<CounterState.Confirming> {}` block. Fails fast if called twice. */
    public fun confirming(block: koma.core.StoreBuilder.StateHandlerConfig<CounterState, CounterAction, Nothing, CounterState.Confirming>.() -> Unit) {
        if (this.confirming != null) throwDuplicateBuilderEntry("CounterState", "confirming")
        this.confirming = block
    }
}
```
