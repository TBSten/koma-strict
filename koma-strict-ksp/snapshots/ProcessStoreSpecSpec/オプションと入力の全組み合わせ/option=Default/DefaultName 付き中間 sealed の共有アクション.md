## Input:FlowState.kt

```kt
package example.flow

import koma.core.Action
import koma.core.State
import me.tbsten.koma.strict.DefaultName
import me.tbsten.koma.strict.OnAction
import me.tbsten.koma.strict.StoreSpec

@StoreSpec(initial = [FlowState.Idle::class])
sealed interface FlowState : State {
    companion object

    @OnAction<FlowAction.Start>(nextState = [Refresh.Running::class])
    interface Idle : FlowState { companion object }

    @DefaultName("refreshCommon")
    @OnAction<FlowAction.Cancel>(nextState = [Idle::class])   // scope 共有アクション
    sealed interface Refresh : FlowState {
        companion object

        interface Running : Refresh { companion object }

        @OnAction<FlowAction.Retry>(nextState = [Running::class])
        interface Failed : Refresh { val message: String?; companion object }
    }
}

sealed interface FlowAction : Action {
    data object Start : FlowAction
    data object Cancel : FlowAction
    data object Retry : FlowAction
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
w: [ksp] <TMPDIR>/Kotlin-Compilation<N>/sources/FlowState.kt:24: koma-strict: state 'example.flow.FlowState.Refresh.Failed' is unreachable from the declared initial state(s) 'Idle'.
```

## Output:Generated sources

```kt
// file: FlowState.Idle.generated.kt
package example.flow

import me.tbsten.koma.strict.*
import me.tbsten.koma.strict.dsl.*

private data object IdleImpl : FlowState.Idle

public operator fun FlowState.Idle.Companion.invoke(): FlowState.Idle = IdleImpl

public sealed interface IdleStartReaction {
    public class Transition internal constructor(
        internal val next: FlowState,
    ) : IdleStartReaction
}

public class IdleStartTransitions internal constructor(
    @Suppress("unused") private val state: FlowState.Idle,
) {
    public fun toRefreshRunning(): IdleStartReaction = IdleStartReaction.Transition(FlowState.Refresh.Running())
}

@KomaStrictDsl
@koma.core.KomaStoreDsl
@OptIn(InternalKomaStrictApi::class)
public class IdleStartScope internal constructor(
    public val state: FlowState.Idle,
    public val action: FlowAction.Start,
    onClearPendingActions: () -> Unit,
) : HandlerScope(onClearPendingActions) {
    public val nextState: IdleStartTransitions = IdleStartTransitions(state)
}

public class IdleHandlers internal constructor(
    internal val start: suspend IdleStartScope.() -> IdleStartReaction,
    internal val configure: koma.core.StoreBuilder.StateHandlerConfig<FlowState, FlowAction, Nothing, FlowState.Idle>.() -> Unit,
) : (IdleHandlersScope) -> IdleHandlers {
    /** Returns this bundle itself, so an already-built value is assignable to the scope-lambda-typed `states(...)` parameter (value-passing / scope-lambda interchange). */
    override fun invoke(p1: IdleHandlersScope): IdleHandlers = this
}

/** Bundles this state's handlers. [configure] appends raw koma DSL at the end of the generated `state<...> {}` block (per-state escape hatch). */
public fun FlowState.Idle.Companion.actions(
    start: suspend IdleStartScope.() -> IdleStartReaction,
    configure: koma.core.StoreBuilder.StateHandlerConfig<FlowState, FlowAction, Nothing, FlowState.Idle>.() -> Unit = {},
): IdleHandlers = IdleHandlers(start, configure)

/**
 * Builder-form overload of `actions(...)` (see [IdleActionsBuilder]).
 *
 * Note: unlike the named-argument overload, exhaustiveness of the declared handlers is
 * checked **at build time** (fail-fast when the block finishes), not at compile time.
 * This overload exists only for states without enter / exit declarations (those handlers
 * have no builder member).
 */
public fun FlowState.Idle.Companion.actions(build: IdleActionsBuilder.() -> Unit): IdleHandlers = IdleActionsBuilder().apply(build).build()

/** Receiver of the scope-lambda form (`{ actions(...) }`) of the matching `states(...)` parameter. [actions] mirrors the companion `actions(...)` extension. */
@KomaStrictDsl
@koma.core.KomaStoreDsl
public class IdleHandlersScope internal constructor() {
    /** Bundles this state's handlers. [configure] appends raw koma DSL at the end of the generated `state<...> {}` block (per-state escape hatch). */
    public fun actions(
        start: suspend IdleStartScope.() -> IdleStartReaction,
        configure: koma.core.StoreBuilder.StateHandlerConfig<FlowState, FlowAction, Nothing, FlowState.Idle>.() -> Unit = {},
    ): IdleHandlers = IdleHandlers(start, configure)

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
    private val start = SetOnceSlot<suspend IdleStartScope.() -> IdleStartReaction>("FlowState.Idle", "start")

    private val configure = SetOnceSlot<koma.core.StoreBuilder.StateHandlerConfig<FlowState, FlowAction, Nothing, FlowState.Idle>.() -> Unit>("FlowState.Idle", "configure")

    /** Registers the handler of the same-named `actions(...)` parameter. Duplicate registration fails fast with [IllegalStateException]. */
    public fun start(handler: suspend IdleStartScope.() -> IdleStartReaction) { start.set(handler) }

    /** Appends raw koma DSL at the end of the generated `state<...> {}` block (the trailing `configure` parameter of `actions(...)`). Duplicate registration fails fast with [IllegalStateException]. */
    public fun configure(block: koma.core.StoreBuilder.StateHandlerConfig<FlowState, FlowAction, Nothing, FlowState.Idle>.() -> Unit) { configure.set(block) }

    internal fun build(): IdleHandlers {
        val missing = listOfNotNull(
            "start".takeIf { !start.isSet },
        )
        if (missing.isNotEmpty()) throwMissingBuilderEntries("FlowState.Idle", missing)
        return IdleHandlers(start.getOrNull()!!, configure.getOrNull() ?: {})
    }
}

// ----- next file -----

// file: FlowState.Refresh.Failed.generated.kt
package example.flow

import me.tbsten.koma.strict.*
import me.tbsten.koma.strict.dsl.*

private data class RefreshFailedImpl(override val message: String?) : FlowState.Refresh.Failed

public operator fun FlowState.Refresh.Failed.Companion.invoke(message: String?): FlowState.Refresh.Failed = RefreshFailedImpl(message)

public sealed interface RefreshFailedRetryReaction {
    public class Transition internal constructor(
        internal val next: FlowState,
    ) : RefreshFailedRetryReaction
}

public class RefreshFailedRetryTransitions internal constructor(
    @Suppress("unused") private val state: FlowState.Refresh.Failed,
) {
    public fun toRunning(): RefreshFailedRetryReaction = RefreshFailedRetryReaction.Transition(FlowState.Refresh.Running())
}

@KomaStrictDsl
@koma.core.KomaStoreDsl
@OptIn(InternalKomaStrictApi::class)
public class RefreshFailedRetryScope internal constructor(
    public val state: FlowState.Refresh.Failed,
    public val action: FlowAction.Retry,
    onClearPendingActions: () -> Unit,
) : HandlerScope(onClearPendingActions) {
    public val nextState: RefreshFailedRetryTransitions = RefreshFailedRetryTransitions(state)
}

public class RefreshFailedHandlers internal constructor(
    internal val retry: suspend RefreshFailedRetryScope.() -> RefreshFailedRetryReaction,
    internal val configure: koma.core.StoreBuilder.StateHandlerConfig<FlowState, FlowAction, Nothing, FlowState.Refresh.Failed>.() -> Unit,
) : (RefreshFailedHandlersScope) -> RefreshFailedHandlers {
    /** Returns this bundle itself, so an already-built value is assignable to the scope-lambda-typed `states(...)` parameter (value-passing / scope-lambda interchange). */
    override fun invoke(p1: RefreshFailedHandlersScope): RefreshFailedHandlers = this
}

/** Bundles this state's handlers. [configure] appends raw koma DSL at the end of the generated `state<...> {}` block (per-state escape hatch). */
public fun FlowState.Refresh.Failed.Companion.actions(
    retry: suspend RefreshFailedRetryScope.() -> RefreshFailedRetryReaction,
    configure: koma.core.StoreBuilder.StateHandlerConfig<FlowState, FlowAction, Nothing, FlowState.Refresh.Failed>.() -> Unit = {},
): RefreshFailedHandlers = RefreshFailedHandlers(retry, configure)

/**
 * Builder-form overload of `actions(...)` (see [RefreshFailedActionsBuilder]).
 *
 * Note: unlike the named-argument overload, exhaustiveness of the declared handlers is
 * checked **at build time** (fail-fast when the block finishes), not at compile time.
 * This overload exists only for states without enter / exit declarations (those handlers
 * have no builder member).
 */
public fun FlowState.Refresh.Failed.Companion.actions(build: RefreshFailedActionsBuilder.() -> Unit): RefreshFailedHandlers = RefreshFailedActionsBuilder().apply(build).build()

/** Receiver of the scope-lambda form (`{ actions(...) }`) of the matching `states(...)` parameter. [actions] mirrors the companion `actions(...)` extension. */
@KomaStrictDsl
@koma.core.KomaStoreDsl
public class RefreshFailedHandlersScope internal constructor() {
    /** Bundles this state's handlers. [configure] appends raw koma DSL at the end of the generated `state<...> {}` block (per-state escape hatch). */
    public fun actions(
        retry: suspend RefreshFailedRetryScope.() -> RefreshFailedRetryReaction,
        configure: koma.core.StoreBuilder.StateHandlerConfig<FlowState, FlowAction, Nothing, FlowState.Refresh.Failed>.() -> Unit = {},
    ): RefreshFailedHandlers = RefreshFailedHandlers(retry, configure)

    /**
     * Builder-form overload of `actions(...)` (see [RefreshFailedActionsBuilder]).
     *
     * Note: unlike the named-argument overload, exhaustiveness of the declared handlers is
     * checked **at build time** (fail-fast when the block finishes), not at compile time.
     * This overload exists only for states without enter / exit declarations (those handlers
     * have no builder member).
     */
    public fun actions(build: RefreshFailedActionsBuilder.() -> Unit): RefreshFailedHandlers = RefreshFailedActionsBuilder().apply(build).build()
}

/**
 * Builder receiver of the `actions { ... }` overload building [RefreshFailedHandlers].
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
public class RefreshFailedActionsBuilder internal constructor() {
    private val retry = SetOnceSlot<suspend RefreshFailedRetryScope.() -> RefreshFailedRetryReaction>("FlowState.Refresh.Failed", "retry")

    private val configure = SetOnceSlot<koma.core.StoreBuilder.StateHandlerConfig<FlowState, FlowAction, Nothing, FlowState.Refresh.Failed>.() -> Unit>("FlowState.Refresh.Failed", "configure")

    /** Registers the handler of the same-named `actions(...)` parameter. Duplicate registration fails fast with [IllegalStateException]. */
    public fun retry(handler: suspend RefreshFailedRetryScope.() -> RefreshFailedRetryReaction) { retry.set(handler) }

    /** Appends raw koma DSL at the end of the generated `state<...> {}` block (the trailing `configure` parameter of `actions(...)`). Duplicate registration fails fast with [IllegalStateException]. */
    public fun configure(block: koma.core.StoreBuilder.StateHandlerConfig<FlowState, FlowAction, Nothing, FlowState.Refresh.Failed>.() -> Unit) { configure.set(block) }

    internal fun build(): RefreshFailedHandlers {
        val missing = listOfNotNull(
            "retry".takeIf { !retry.isSet },
        )
        if (missing.isNotEmpty()) throwMissingBuilderEntries("FlowState.Refresh.Failed", missing)
        return RefreshFailedHandlers(retry.getOrNull()!!, configure.getOrNull() ?: {})
    }
}

// ----- next file -----

// file: FlowState.Refresh.Running.generated.kt
package example.flow

import me.tbsten.koma.strict.*
import me.tbsten.koma.strict.dsl.*

private data object RefreshRunningImpl : FlowState.Refresh.Running

public operator fun FlowState.Refresh.Running.Companion.invoke(): FlowState.Refresh.Running = RefreshRunningImpl

// ----- next file -----

// file: FlowState.Refresh.generated.kt
package example.flow

import me.tbsten.koma.strict.*
import me.tbsten.koma.strict.dsl.*

public sealed interface RefreshCancelReaction {
    public class Transition internal constructor(
        internal val next: FlowState,
    ) : RefreshCancelReaction
}

public class RefreshCancelTransitions internal constructor(
    @Suppress("unused") private val state: FlowState.Refresh,
) {
    public fun toIdle(): RefreshCancelReaction = RefreshCancelReaction.Transition(FlowState.Idle())
}

@KomaStrictDsl
@koma.core.KomaStoreDsl
@OptIn(InternalKomaStrictApi::class)
public class RefreshCancelScope internal constructor(
    public val state: FlowState.Refresh,
    public val action: FlowAction.Cancel,
    onClearPendingActions: () -> Unit,
) : HandlerScope(onClearPendingActions) {
    public val nextState: RefreshCancelTransitions = RefreshCancelTransitions(state)
}

public class RefreshRefreshCommonHandlers internal constructor(
    internal val cancel: suspend RefreshCancelScope.() -> RefreshCancelReaction,
) : (RefreshRefreshCommonHandlersScope) -> RefreshRefreshCommonHandlers {
    /** Returns this bundle itself, so an already-built value is assignable to the scope-lambda-typed `states(...)` parameter (value-passing / scope-lambda interchange). */
    override fun invoke(p1: RefreshRefreshCommonHandlersScope): RefreshRefreshCommonHandlers = this
}

public fun FlowState.Refresh.Companion.actions(
    cancel: suspend RefreshCancelScope.() -> RefreshCancelReaction,
    preventTrailingLambda: PreventTrailingLambda = PreventTrailingLambda,
): RefreshRefreshCommonHandlers = RefreshRefreshCommonHandlers(cancel)

/**
 * Builder-form overload of `actions(...)` (see [RefreshRefreshCommonActionsBuilder]).
 *
 * Note: unlike the named-argument overload, exhaustiveness of the declared handlers is
 * checked **at build time** (fail-fast when the block finishes), not at compile time.
 * This overload exists only for states without enter / exit declarations (those handlers
 * have no builder member).
 */
public fun FlowState.Refresh.Companion.actions(build: RefreshRefreshCommonActionsBuilder.() -> Unit): RefreshRefreshCommonHandlers = RefreshRefreshCommonActionsBuilder().apply(build).build()

/** Receiver of the scope-lambda form (`{ actions(...) }`) of the matching `states(...)` parameter. [actions] mirrors the companion `actions(...)` extension. */
@KomaStrictDsl
@koma.core.KomaStoreDsl
public class RefreshRefreshCommonHandlersScope internal constructor() {
    public fun actions(
        cancel: suspend RefreshCancelScope.() -> RefreshCancelReaction,
        preventTrailingLambda: PreventTrailingLambda = PreventTrailingLambda,
    ): RefreshRefreshCommonHandlers = RefreshRefreshCommonHandlers(cancel)

    /**
     * Builder-form overload of `actions(...)` (see [RefreshRefreshCommonActionsBuilder]).
     *
     * Note: unlike the named-argument overload, exhaustiveness of the declared handlers is
     * checked **at build time** (fail-fast when the block finishes), not at compile time.
     * This overload exists only for states without enter / exit declarations (those handlers
     * have no builder member).
     */
    public fun actions(build: RefreshRefreshCommonActionsBuilder.() -> Unit): RefreshRefreshCommonHandlers = RefreshRefreshCommonActionsBuilder().apply(build).build()
}

/**
 * Builder receiver of the `actions { ... }` overload building [RefreshRefreshCommonHandlers].
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
public class RefreshRefreshCommonActionsBuilder internal constructor() {
    private val cancel = SetOnceSlot<suspend RefreshCancelScope.() -> RefreshCancelReaction>("FlowState.Refresh.refreshCommon", "cancel")

    /** Registers the handler of the same-named `actions(...)` parameter. Duplicate registration fails fast with [IllegalStateException]. */
    public fun cancel(handler: suspend RefreshCancelScope.() -> RefreshCancelReaction) { cancel.set(handler) }

    internal fun build(): RefreshRefreshCommonHandlers {
        val missing = listOfNotNull(
            "cancel".takeIf { !cancel.isSet },
        )
        if (missing.isNotEmpty()) throwMissingBuilderEntries("FlowState.Refresh.refreshCommon", missing)
        return RefreshRefreshCommonHandlers(cancel.getOrNull()!!)
    }
}

public class RefreshGroupHandlers internal constructor(
    internal val failed: RefreshFailedHandlers,
    internal val configure: RefreshStatesConfigureScope,
) : (RefreshGroupHandlersScope) -> RefreshGroupHandlers {
    /** Returns this bundle itself, so an already-built value is assignable to the scope-lambda-typed `states(...)` parameter (value-passing / scope-lambda interchange). */
    override fun invoke(p1: RefreshGroupHandlersScope): RefreshGroupHandlers = this
}

/** Bundles the child states only. Compose with the shared `actions(...)` via `+` to obtain the [RefreshHandlers] the parent parameter requires. */
public fun FlowState.Refresh.Companion.states(
    failed: RefreshFailedHandlersScope.() -> RefreshFailedHandlers,
    configure: RefreshStatesConfigureScope.() -> Unit = {},
): RefreshGroupHandlers =
    RefreshGroupHandlers(
        RefreshFailedHandlersScope().failed(),
        RefreshStatesConfigureScope().apply(configure),
    )

/** Receiver of the scope-lambda form (`{ states(...) }`) of the matching `states(...)` parameter. [states] mirrors the companion `states(...)` extension. */
@KomaStrictDsl
@koma.core.KomaStoreDsl
public class RefreshGroupHandlersScope internal constructor() {
    /** Bundles the child states only. Compose with the shared `actions(...)` via `+` to obtain the [RefreshHandlers] the parent parameter requires. */
    public fun states(
        failed: RefreshFailedHandlersScope.() -> RefreshFailedHandlers,
        configure: RefreshStatesConfigureScope.() -> Unit = {},
    ): RefreshGroupHandlers =
        RefreshGroupHandlers(
            RefreshFailedHandlersScope().failed(),
            RefreshStatesConfigureScope().apply(configure),
        )
}

public class RefreshHandlers internal constructor(
    internal val refreshCommon: RefreshRefreshCommonHandlers,
    internal val failed: RefreshFailedHandlers,
    internal val configure: RefreshStatesConfigureScope,
) : (RefreshHandlersScope) -> RefreshHandlers {
    /** Returns this bundle itself, so an already-built value is assignable to the scope-lambda-typed `states(...)` parameter (value-passing / scope-lambda interchange). */
    override fun invoke(p1: RefreshHandlersScope): RefreshHandlers = this
}

/**
 * Composes this node's own shared handlers (built with `actions(...)`) with its child
 * states' bundle (built with `states(...)`) into the [RefreshHandlers] the parent `states(...)`
 * parameter requires. Both sides are required by type: forgetting either the shared
 * handlers or the child bundle is a compile error.
 */
public operator fun RefreshRefreshCommonHandlers.plus(children: RefreshGroupHandlers): RefreshHandlers =
    RefreshHandlers(this, children.failed, children.configure)

/** Bundles the shared default block (`refreshCommon`) and the child states in one call — the same [RefreshHandlers] as `actions(...) + states(...)`. */
public fun FlowState.Refresh.Companion.states(
    refreshCommon: RefreshRefreshCommonHandlersScope.() -> RefreshRefreshCommonHandlers,
    failed: RefreshFailedHandlersScope.() -> RefreshFailedHandlers,
    configure: RefreshStatesConfigureScope.() -> Unit = {},
): RefreshHandlers =
    RefreshHandlers(
        RefreshRefreshCommonHandlersScope().refreshCommon(),
        RefreshFailedHandlersScope().failed(),
        RefreshStatesConfigureScope().apply(configure),
    )

/**
 * Builder-form overload of `states(...)` (see [RefreshGroupBuilder]).
 *
 * Note: unlike the named-argument overload, exhaustiveness of the child states (and the
 * default block) is checked **at build time** (fail-fast when the block finishes), not at
 * compile time.
 */
public fun FlowState.Refresh.Companion.states(build: RefreshGroupBuilder.() -> Unit): RefreshHandlers = RefreshGroupBuilder().apply(build).build()

/** Receiver of the scope-lambda form (`{ actions(...) + states(...) }`) of the matching `states(...)` parameter. [actions] / [states] mirror the companion extensions. */
@KomaStrictDsl
@koma.core.KomaStoreDsl
public class RefreshHandlersScope internal constructor() {
    public fun actions(
        cancel: suspend RefreshCancelScope.() -> RefreshCancelReaction,
        preventTrailingLambda: PreventTrailingLambda = PreventTrailingLambda,
    ): RefreshRefreshCommonHandlers = RefreshRefreshCommonHandlers(cancel)

    /**
     * Builder-form overload of `actions(...)` (see [RefreshRefreshCommonActionsBuilder]).
     *
     * Note: unlike the named-argument overload, exhaustiveness of the declared handlers is
     * checked **at build time** (fail-fast when the block finishes), not at compile time.
     * This overload exists only for states without enter / exit declarations (those handlers
     * have no builder member).
     */
    public fun actions(build: RefreshRefreshCommonActionsBuilder.() -> Unit): RefreshRefreshCommonHandlers = RefreshRefreshCommonActionsBuilder().apply(build).build()

    /** Bundles the child states only. Compose with the shared `actions(...)` via `+` to obtain the [RefreshHandlers] the parent parameter requires. */
    public fun states(
        failed: RefreshFailedHandlersScope.() -> RefreshFailedHandlers,
        configure: RefreshStatesConfigureScope.() -> Unit = {},
    ): RefreshGroupHandlers =
        RefreshGroupHandlers(
            RefreshFailedHandlersScope().failed(),
            RefreshStatesConfigureScope().apply(configure),
        )

    /** Bundles the shared default block (`refreshCommon`) and the child states in one call — the same [RefreshHandlers] as `actions(...) + states(...)`. */
    public fun states(
        refreshCommon: RefreshRefreshCommonHandlersScope.() -> RefreshRefreshCommonHandlers,
        failed: RefreshFailedHandlersScope.() -> RefreshFailedHandlers,
        configure: RefreshStatesConfigureScope.() -> Unit = {},
    ): RefreshHandlers =
        RefreshHandlers(
            RefreshRefreshCommonHandlersScope().refreshCommon(),
            RefreshFailedHandlersScope().failed(),
            RefreshStatesConfigureScope().apply(configure),
        )

    /**
     * Builder-form overload of `states(...)` (see [RefreshGroupBuilder]).
     *
     * Note: unlike the named-argument overload, exhaustiveness of the child states (and the
     * default block) is checked **at build time** (fail-fast when the block finishes), not at
     * compile time.
     */
    public fun states(build: RefreshGroupBuilder.() -> Unit): RefreshHandlers = RefreshGroupBuilder().apply(build).build()
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
public class RefreshStatesConfigureScope internal constructor() {
    internal var failed: (koma.core.StoreBuilder.StateHandlerConfig<FlowState, FlowAction, Nothing, FlowState.Refresh.Failed>.() -> Unit)? = null

    /** Appends raw koma DSL at the end of the generated `state<FlowState.Refresh.Failed> {}` block. Fails fast if called twice. */
    public fun failed(block: koma.core.StoreBuilder.StateHandlerConfig<FlowState, FlowAction, Nothing, FlowState.Refresh.Failed>.() -> Unit) {
        if (this.failed != null) throwDuplicateBuilderEntry("FlowState.Refresh", "failed")
        this.failed = block
    }
}

/**
 * Builder receiver of the `states { ... }` overload building [RefreshHandlers].
 *
 * Each member function registers the same-named `states(...)` parameter, either by an
 * already-built value or with a nested builder block. Exhaustiveness is checked when the
 * block finishes (build-time fail-fast), not at compile time: entries left unregistered —
 * and any duplicate registration — throw [IllegalStateException]. Prefer the named-argument
 * `states(...)` overload to catch missing entries at compile time.
 */
@KomaStrictDsl
@koma.core.KomaStoreDsl
@OptIn(InternalKomaStrictApi::class)
public class RefreshGroupBuilder internal constructor() {
    private val refreshCommon = SetOnceSlot<RefreshRefreshCommonHandlers>("FlowState.Refresh", "refreshCommon")

    private val failed = SetOnceSlot<RefreshFailedHandlers>("FlowState.Refresh", "failed")

    /** Registers the shared default block by an already-built value. Duplicate registration fails fast with [IllegalStateException]. */
    public fun refreshCommon(handlers: RefreshRefreshCommonHandlers) { refreshCommon.set(handlers) }

    /** Registers the shared default block with a nested builder block (fails fast if already registered). */
    public fun refreshCommon(build: RefreshRefreshCommonActionsBuilder.() -> Unit) {
        refreshCommon(RefreshRefreshCommonActionsBuilder().apply(build).build())
    }

    /** Registers this child state's handlers by an already-built value. Duplicate registration fails fast with [IllegalStateException]. */
    public fun failed(handlers: RefreshFailedHandlers) { failed.set(handlers) }

    /** Registers this child state's handlers with a nested builder block (fails fast if already registered). */
    public fun failed(build: RefreshFailedActionsBuilder.() -> Unit) {
        failed(RefreshFailedActionsBuilder().apply(build).build())
    }

    internal fun build(): RefreshHandlers {
        val missing = listOfNotNull(
            "refreshCommon".takeIf { !refreshCommon.isSet },
            "failed".takeIf { !failed.isSet },
        )
        if (missing.isNotEmpty()) throwMissingBuilderEntries("FlowState.Refresh", missing)
        return RefreshHandlers(refreshCommon.getOrNull()!!, failed.getOrNull()!!, RefreshStatesConfigureScope())
    }
}

// ----- next file -----

// file: FlowState.storeSpec.generated.kt
package example.flow

import me.tbsten.koma.strict.*
import me.tbsten.koma.strict.dsl.*

@kotlin.jvm.JvmName("flowStateStates")
@Suppress("NAME_SHADOWING", "UNCHECKED_CAST")
public fun koma.core.StoreBuilder<FlowState, FlowAction, Nothing>.states(
    idle: IdleHandlersScope.() -> IdleHandlers,
    refresh: RefreshHandlersScope.() -> RefreshHandlers,
    configure: FlowStateStatesConfigureScope.() -> Unit = {},
) {
    val idle = IdleHandlersScope().idle()
    val refresh = RefreshHandlersScope().refresh()
    val configure = FlowStateStatesConfigureScope().apply(configure)

    state<FlowState.Idle> {
        action<FlowAction.Start> {
            when (val r = idle.start(IdleStartScope(state, action, ::clearPendingActions))) {
                is IdleStartReaction.Transition -> nextState { r.next }
            }
        }
        idle.configure(this)
        configure.idle?.invoke(this)
    }
    state<FlowState.Refresh.Running> {
        action<FlowAction.Cancel> {
            when (val r = refresh.refreshCommon.cancel(RefreshCancelScope(state, action, ::clearPendingActions))) {
                is RefreshCancelReaction.Transition -> nextState { r.next }
            }
        }
        configure.refresh?.invoke(this as koma.core.StoreBuilder.StateHandlerConfig<FlowState, FlowAction, Nothing, FlowState.Refresh>)
    }
    state<FlowState.Refresh.Failed> {
        action<FlowAction.Cancel> {
            when (val r = refresh.refreshCommon.cancel(RefreshCancelScope(state, action, ::clearPendingActions))) {
                is RefreshCancelReaction.Transition -> nextState { r.next }
            }
        }
        action<FlowAction.Retry> {
            when (val r = refresh.failed.retry(RefreshFailedRetryScope(state, action, ::clearPendingActions))) {
                is RefreshFailedRetryReaction.Transition -> nextState { r.next }
            }
        }
        refresh.failed.configure(this)
        refresh.configure.failed?.invoke(this)
        configure.refresh?.invoke(this as koma.core.StoreBuilder.StateHandlerConfig<FlowState, FlowAction, Nothing, FlowState.Refresh>)
    }
}

/**
 * Builds a [koma.core.Store] for [FlowState] without spelling the store type arguments.
 *
 * Sugar over the canonical koma entry point — `Store<FlowState, FlowAction, Nothing>(initialState) { states(...) }`
 * builds the exact same store. [initialState] is narrowed to the declared
 * `@StoreSpec(initial = ...)` candidate [FlowState.Idle] — compile-time enforced.
 * [configuration] appends raw koma DSL after the generated handlers (store-level escape hatch).
 */
public fun createFlowStore(
    initialState: FlowState.Idle,
    idle: IdleHandlersScope.() -> IdleHandlers,
    refresh: RefreshHandlersScope.() -> RefreshHandlers,
    context: kotlin.coroutines.CoroutineContext? = null,
    configuration: koma.core.StoreBuilder<FlowState, FlowAction, Nothing>.() -> Unit = {},
): koma.core.Store<FlowState, FlowAction, Nothing> =
    koma.core.Store<FlowState, FlowAction, Nothing>(initialState = initialState, context = context) {
        states(
            idle = idle,
            refresh = refresh,
        )
        configuration()
    }

/**
 * Builds a [koma.core.Store] for [FlowState] without spelling the store type arguments.
 *
 * Sugar over the canonical koma entry point — `Store<FlowState, FlowAction, Nothing>(initialState) { states(...) }`
 * builds the exact same store. [initialState] accepts any [FlowState] — for
 * restoring a persisted state or starting mid-flow in tests, where [createFlowStore]'s
 * compile-time-narrowed initial state does not apply. [configuration] appends raw koma
 * DSL after the generated handlers (store-level escape hatch).
 */
public fun restoreFlowStore(
    initialState: FlowState,
    idle: IdleHandlersScope.() -> IdleHandlers,
    refresh: RefreshHandlersScope.() -> RefreshHandlers,
    context: kotlin.coroutines.CoroutineContext? = null,
    configuration: koma.core.StoreBuilder<FlowState, FlowAction, Nothing>.() -> Unit = {},
): koma.core.Store<FlowState, FlowAction, Nothing> =
    koma.core.Store<FlowState, FlowAction, Nothing>(initialState = initialState, context = context) {
        states(
            idle = idle,
            refresh = refresh,
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
public class FlowStateStatesConfigureScope internal constructor() {
    internal var idle: (koma.core.StoreBuilder.StateHandlerConfig<FlowState, FlowAction, Nothing, FlowState.Idle>.() -> Unit)? = null

    internal var refresh: (koma.core.StoreBuilder.StateHandlerConfig<FlowState, FlowAction, Nothing, FlowState.Refresh>.() -> Unit)? = null

    /** Appends raw koma DSL at the end of the generated `state<FlowState.Idle> {}` block. Fails fast if called twice. */
    public fun idle(block: koma.core.StoreBuilder.StateHandlerConfig<FlowState, FlowAction, Nothing, FlowState.Idle>.() -> Unit) {
        if (this.idle != null) throwDuplicateBuilderEntry("FlowState", "idle")
        this.idle = block
    }

    /** Appends raw koma DSL to every generated `state<...> {}` block under [FlowState.Refresh] (a shared escape, expanded like shared declarations). Fails fast if called twice. */
    public fun refresh(block: koma.core.StoreBuilder.StateHandlerConfig<FlowState, FlowAction, Nothing, FlowState.Refresh>.() -> Unit) {
        if (this.refresh != null) throwDuplicateBuilderEntry("FlowState", "refresh")
        this.refresh = block
    }
}
```
