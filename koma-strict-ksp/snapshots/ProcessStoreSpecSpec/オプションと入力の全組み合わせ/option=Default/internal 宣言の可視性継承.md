## Input:HiddenState.kt

```kt
package example.hidden

import koma.core.Action
import koma.core.State
import me.tbsten.koma.strict.OnAction
import me.tbsten.koma.strict.StoreSpec

@StoreSpec(initial = [HiddenState.Idle::class])
internal sealed interface HiddenState : State {   // internal 宣言 -> 生成物も internal
    companion object

    @OnAction<HiddenAction.Toggle>(nextState = [Active::class])
    interface Idle : HiddenState { companion object }

    @OnAction<HiddenAction.Toggle>(nextState = [Idle::class])
    interface Active : HiddenState { val startedAt: Long; companion object }
}

internal sealed interface HiddenAction : Action {
    data object Toggle : HiddenAction
}
```

## Input:HiddenStoreUsage.kt

```kt
package example.hidden

import koma.core.Store

internal fun buildHiddenStore(): Store<HiddenState, HiddenAction, Nothing> =
    Store<HiddenState, HiddenAction, Nothing>(initialState = HiddenState.Idle()) {
        states(
            idle = HiddenState.Idle.actions(toggle = { nextState.toActive(startedAt = 0L) }),
            active = HiddenState.Active.actions(toggle = { nextState.toIdle() }),
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
// file: HiddenState.Active.generated.kt
package example.hidden

import me.tbsten.koma.strict.*
import me.tbsten.koma.strict.dsl.*

private data class ActiveImpl(override val startedAt: Long) : HiddenState.Active

internal operator fun HiddenState.Active.Companion.invoke(startedAt: Long): HiddenState.Active = ActiveImpl(startedAt)

internal sealed interface ActiveToggleReaction {
    public class Transition internal constructor(
        internal val next: HiddenState,
    ) : ActiveToggleReaction
}

internal class ActiveToggleTransitions internal constructor(
    @Suppress("unused") private val state: HiddenState.Active,
) {
    public fun toIdle(): ActiveToggleReaction = ActiveToggleReaction.Transition(HiddenState.Idle())
}

@KomaStrictDsl
@koma.core.KomaStoreDsl
@OptIn(InternalKomaStrictApi::class)
internal class ActiveToggleScope internal constructor(
    public val state: HiddenState.Active,
    public val action: HiddenAction.Toggle,
    onClearPendingActions: () -> Unit,
) : HandlerScope(onClearPendingActions) {
    public val nextState: ActiveToggleTransitions = ActiveToggleTransitions(state)
}

internal class ActiveHandlers internal constructor(
    internal val toggle: suspend ActiveToggleScope.() -> ActiveToggleReaction,
    internal val configure: koma.core.StoreBuilder.StateHandlerConfig<HiddenState, HiddenAction, Nothing, HiddenState.Active>.() -> Unit,
) : (ActiveHandlersScope) -> ActiveHandlers {
    /** Returns this bundle itself, so an already-built value is assignable to the scope-lambda-typed `states(...)` parameter (value-passing / scope-lambda interchange). */
    override fun invoke(p1: ActiveHandlersScope): ActiveHandlers = this
}

/** Bundles this state's handlers. [configure] appends raw koma DSL at the end of the generated `state<...> {}` block (per-state escape hatch). */
internal fun HiddenState.Active.Companion.actions(
    toggle: suspend ActiveToggleScope.() -> ActiveToggleReaction,
    configure: koma.core.StoreBuilder.StateHandlerConfig<HiddenState, HiddenAction, Nothing, HiddenState.Active>.() -> Unit = {},
): ActiveHandlers = ActiveHandlers(toggle, configure)

/**
 * Builder-form overload of `actions(...)` (see [ActiveActionsBuilder]).
 *
 * Note: unlike the named-argument overload, exhaustiveness of the declared handlers is
 * checked **at build time** (fail-fast when the block finishes), not at compile time.
 * This overload exists only for states without enter / exit declarations (those handlers
 * have no builder member).
 */
internal fun HiddenState.Active.Companion.actions(build: ActiveActionsBuilder.() -> Unit): ActiveHandlers = ActiveActionsBuilder().apply(build).build()

/** Receiver of the scope-lambda form (`{ actions(...) }`) of the matching `states(...)` parameter. [actions] mirrors the companion `actions(...)` extension. */
@KomaStrictDsl
@koma.core.KomaStoreDsl
internal class ActiveHandlersScope internal constructor() {
    /** Bundles this state's handlers. [configure] appends raw koma DSL at the end of the generated `state<...> {}` block (per-state escape hatch). */
    public fun actions(
        toggle: suspend ActiveToggleScope.() -> ActiveToggleReaction,
        configure: koma.core.StoreBuilder.StateHandlerConfig<HiddenState, HiddenAction, Nothing, HiddenState.Active>.() -> Unit = {},
    ): ActiveHandlers = ActiveHandlers(toggle, configure)

    /**
     * Builder-form overload of `actions(...)` (see [ActiveActionsBuilder]).
     *
     * Note: unlike the named-argument overload, exhaustiveness of the declared handlers is
     * checked **at build time** (fail-fast when the block finishes), not at compile time.
     * This overload exists only for states without enter / exit declarations (those handlers
     * have no builder member).
     */
    public fun actions(build: ActiveActionsBuilder.() -> Unit): ActiveHandlers = ActiveActionsBuilder().apply(build).build()
}

/**
 * Builder receiver of the `actions { ... }` overload building [ActiveHandlers].
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
internal class ActiveActionsBuilder internal constructor() {
    private val toggle = SetOnceSlot<suspend ActiveToggleScope.() -> ActiveToggleReaction>("HiddenState.Active", "toggle")

    private val configure = SetOnceSlot<koma.core.StoreBuilder.StateHandlerConfig<HiddenState, HiddenAction, Nothing, HiddenState.Active>.() -> Unit>("HiddenState.Active", "configure")

    /** Registers the handler of the same-named `actions(...)` parameter. Duplicate registration fails fast with [IllegalStateException]. */
    public fun toggle(handler: suspend ActiveToggleScope.() -> ActiveToggleReaction) { toggle.set(handler) }

    /** Appends raw koma DSL at the end of the generated `state<...> {}` block (the trailing `configure` parameter of `actions(...)`). Duplicate registration fails fast with [IllegalStateException]. */
    public fun configure(block: koma.core.StoreBuilder.StateHandlerConfig<HiddenState, HiddenAction, Nothing, HiddenState.Active>.() -> Unit) { configure.set(block) }

    internal fun build(): ActiveHandlers {
        val missing = listOfNotNull(
            "toggle".takeIf { !toggle.isSet },
        )
        if (missing.isNotEmpty()) throwMissingBuilderEntries("HiddenState.Active", missing)
        return ActiveHandlers(toggle.getOrNull()!!, configure.getOrNull() ?: {})
    }
}

// ----- next file -----

// file: HiddenState.Idle.generated.kt
package example.hidden

import me.tbsten.koma.strict.*
import me.tbsten.koma.strict.dsl.*

private data object IdleImpl : HiddenState.Idle

internal operator fun HiddenState.Idle.Companion.invoke(): HiddenState.Idle = IdleImpl

internal sealed interface IdleToggleReaction {
    public class Transition internal constructor(
        internal val next: HiddenState,
    ) : IdleToggleReaction
}

internal class IdleToggleTransitions internal constructor(
    @Suppress("unused") private val state: HiddenState.Idle,
) {
    public fun toActive(
        startedAt: Long,
    ): IdleToggleReaction = IdleToggleReaction.Transition(HiddenState.Active(startedAt))
}

@KomaStrictDsl
@koma.core.KomaStoreDsl
@OptIn(InternalKomaStrictApi::class)
internal class IdleToggleScope internal constructor(
    public val state: HiddenState.Idle,
    public val action: HiddenAction.Toggle,
    onClearPendingActions: () -> Unit,
) : HandlerScope(onClearPendingActions) {
    public val nextState: IdleToggleTransitions = IdleToggleTransitions(state)
}

internal class IdleHandlers internal constructor(
    internal val toggle: suspend IdleToggleScope.() -> IdleToggleReaction,
    internal val configure: koma.core.StoreBuilder.StateHandlerConfig<HiddenState, HiddenAction, Nothing, HiddenState.Idle>.() -> Unit,
) : (IdleHandlersScope) -> IdleHandlers {
    /** Returns this bundle itself, so an already-built value is assignable to the scope-lambda-typed `states(...)` parameter (value-passing / scope-lambda interchange). */
    override fun invoke(p1: IdleHandlersScope): IdleHandlers = this
}

/** Bundles this state's handlers. [configure] appends raw koma DSL at the end of the generated `state<...> {}` block (per-state escape hatch). */
internal fun HiddenState.Idle.Companion.actions(
    toggle: suspend IdleToggleScope.() -> IdleToggleReaction,
    configure: koma.core.StoreBuilder.StateHandlerConfig<HiddenState, HiddenAction, Nothing, HiddenState.Idle>.() -> Unit = {},
): IdleHandlers = IdleHandlers(toggle, configure)

/**
 * Builder-form overload of `actions(...)` (see [IdleActionsBuilder]).
 *
 * Note: unlike the named-argument overload, exhaustiveness of the declared handlers is
 * checked **at build time** (fail-fast when the block finishes), not at compile time.
 * This overload exists only for states without enter / exit declarations (those handlers
 * have no builder member).
 */
internal fun HiddenState.Idle.Companion.actions(build: IdleActionsBuilder.() -> Unit): IdleHandlers = IdleActionsBuilder().apply(build).build()

/** Receiver of the scope-lambda form (`{ actions(...) }`) of the matching `states(...)` parameter. [actions] mirrors the companion `actions(...)` extension. */
@KomaStrictDsl
@koma.core.KomaStoreDsl
internal class IdleHandlersScope internal constructor() {
    /** Bundles this state's handlers. [configure] appends raw koma DSL at the end of the generated `state<...> {}` block (per-state escape hatch). */
    public fun actions(
        toggle: suspend IdleToggleScope.() -> IdleToggleReaction,
        configure: koma.core.StoreBuilder.StateHandlerConfig<HiddenState, HiddenAction, Nothing, HiddenState.Idle>.() -> Unit = {},
    ): IdleHandlers = IdleHandlers(toggle, configure)

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
internal class IdleActionsBuilder internal constructor() {
    private val toggle = SetOnceSlot<suspend IdleToggleScope.() -> IdleToggleReaction>("HiddenState.Idle", "toggle")

    private val configure = SetOnceSlot<koma.core.StoreBuilder.StateHandlerConfig<HiddenState, HiddenAction, Nothing, HiddenState.Idle>.() -> Unit>("HiddenState.Idle", "configure")

    /** Registers the handler of the same-named `actions(...)` parameter. Duplicate registration fails fast with [IllegalStateException]. */
    public fun toggle(handler: suspend IdleToggleScope.() -> IdleToggleReaction) { toggle.set(handler) }

    /** Appends raw koma DSL at the end of the generated `state<...> {}` block (the trailing `configure` parameter of `actions(...)`). Duplicate registration fails fast with [IllegalStateException]. */
    public fun configure(block: koma.core.StoreBuilder.StateHandlerConfig<HiddenState, HiddenAction, Nothing, HiddenState.Idle>.() -> Unit) { configure.set(block) }

    internal fun build(): IdleHandlers {
        val missing = listOfNotNull(
            "toggle".takeIf { !toggle.isSet },
        )
        if (missing.isNotEmpty()) throwMissingBuilderEntries("HiddenState.Idle", missing)
        return IdleHandlers(toggle.getOrNull()!!, configure.getOrNull() ?: {})
    }
}

// ----- next file -----

// file: HiddenState.storeSpec.generated.kt
package example.hidden

import me.tbsten.koma.strict.*
import me.tbsten.koma.strict.dsl.*

@kotlin.jvm.JvmName("hiddenStateStates")
@Suppress("NAME_SHADOWING")
internal fun koma.core.StoreBuilder<HiddenState, HiddenAction, Nothing>.states(
    idle: IdleHandlersScope.() -> IdleHandlers,
    active: ActiveHandlersScope.() -> ActiveHandlers,
    configure: HiddenStateStatesConfigureScope.() -> Unit = {},
) {
    val idle = IdleHandlersScope().idle()
    val active = ActiveHandlersScope().active()
    val configure = HiddenStateStatesConfigureScope().apply(configure)

    state<HiddenState.Idle> {
        action<HiddenAction.Toggle> {
            when (val r = idle.toggle(IdleToggleScope(state, action, ::clearPendingActions))) {
                is IdleToggleReaction.Transition -> nextState { r.next }
            }
        }
        idle.configure(this)
        configure.idle?.invoke(this)
    }
    state<HiddenState.Active> {
        action<HiddenAction.Toggle> {
            when (val r = active.toggle(ActiveToggleScope(state, action, ::clearPendingActions))) {
                is ActiveToggleReaction.Transition -> nextState { r.next }
            }
        }
        active.configure(this)
        configure.active?.invoke(this)
    }
}

/**
 * Builds a [koma.core.Store] for [HiddenState] without spelling the store type arguments.
 *
 * Sugar over the canonical koma entry point — `Store<HiddenState, HiddenAction, Nothing>(initialState) { states(...) }`
 * builds the exact same store. [initialState] is narrowed to the declared
 * `@StoreSpec(initial = ...)` candidate [HiddenState.Idle] — compile-time enforced.
 * [configuration] appends raw koma DSL after the generated handlers (store-level escape hatch).
 */
internal fun createHiddenStore(
    initialState: HiddenState.Idle,
    idle: IdleHandlersScope.() -> IdleHandlers,
    active: ActiveHandlersScope.() -> ActiveHandlers,
    context: kotlin.coroutines.CoroutineContext? = null,
    configuration: koma.core.StoreBuilder<HiddenState, HiddenAction, Nothing>.() -> Unit = {},
): koma.core.Store<HiddenState, HiddenAction, Nothing> =
    koma.core.Store<HiddenState, HiddenAction, Nothing>(initialState = initialState, context = context) {
        states(
            idle = idle,
            active = active,
        )
        configuration()
    }

/**
 * Builds a [koma.core.Store] for [HiddenState] without spelling the store type arguments.
 *
 * Sugar over the canonical koma entry point — `Store<HiddenState, HiddenAction, Nothing>(initialState) { states(...) }`
 * builds the exact same store. [initialState] accepts any [HiddenState] — for
 * restoring a persisted state or starting mid-flow in tests, where [createHiddenStore]'s
 * compile-time-narrowed initial state does not apply. [configuration] appends raw koma
 * DSL after the generated handlers (store-level escape hatch).
 */
internal fun restoreHiddenStore(
    initialState: HiddenState,
    idle: IdleHandlersScope.() -> IdleHandlers,
    active: ActiveHandlersScope.() -> ActiveHandlers,
    context: kotlin.coroutines.CoroutineContext? = null,
    configuration: koma.core.StoreBuilder<HiddenState, HiddenAction, Nothing>.() -> Unit = {},
): koma.core.Store<HiddenState, HiddenAction, Nothing> =
    koma.core.Store<HiddenState, HiddenAction, Nothing>(initialState = initialState, context = context) {
        states(
            idle = idle,
            active = active,
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
internal class HiddenStateStatesConfigureScope internal constructor() {
    internal var idle: (koma.core.StoreBuilder.StateHandlerConfig<HiddenState, HiddenAction, Nothing, HiddenState.Idle>.() -> Unit)? = null

    internal var active: (koma.core.StoreBuilder.StateHandlerConfig<HiddenState, HiddenAction, Nothing, HiddenState.Active>.() -> Unit)? = null

    /** Appends raw koma DSL at the end of the generated `state<HiddenState.Idle> {}` block. Fails fast if called twice. */
    public fun idle(block: koma.core.StoreBuilder.StateHandlerConfig<HiddenState, HiddenAction, Nothing, HiddenState.Idle>.() -> Unit) {
        if (this.idle != null) throwDuplicateBuilderEntry("HiddenState", "idle")
        this.idle = block
    }

    /** Appends raw koma DSL at the end of the generated `state<HiddenState.Active> {}` block. Fails fast if called twice. */
    public fun active(block: koma.core.StoreBuilder.StateHandlerConfig<HiddenState, HiddenAction, Nothing, HiddenState.Active>.() -> Unit) {
        if (this.active != null) throwDuplicateBuilderEntry("HiddenState", "active")
        this.active = block
    }
}
```
