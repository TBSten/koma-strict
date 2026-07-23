## Input:Unreachable.kt

```kt
package example.diag

import koma.core.Action
import koma.core.State
import me.tbsten.koma.strict.OnAction
import me.tbsten.koma.strict.StoreSpec

@StoreSpec(initial = [ReachState.Start::class])
sealed interface ReachState : State {
    companion object

    @OnAction<ReachAction.Go>(nextState = [Done::class])
    interface Start : ReachState { companion object }

    interface Done : ReachState { companion object }

    @OnAction<ReachAction.Go>(nextState = [Done::class])
    interface Orphan : ReachState { companion object }
}

sealed interface ReachAction : Action {
    data object Go : ReachAction
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
w: [ksp] <TMPDIR>/Kotlin-Compilation<N>/sources/Unreachable.kt:18: koma-strict: state 'example.diag.ReachState.Orphan' is unreachable from the declared initial state(s) 'Start'.
```

## Output:Generated sources

```kt
// file: ReachState.Done.generated.kt
package example.diag

import me.tbsten.koma.strict.*
import me.tbsten.koma.strict.dsl.*

private data object DoneImpl : ReachState.Done

public operator fun ReachState.Done.Companion.invoke(): ReachState.Done = DoneImpl

// ----- next file -----

// file: ReachState.Orphan.generated.kt
package example.diag

import me.tbsten.koma.strict.*
import me.tbsten.koma.strict.dsl.*

private data object OrphanImpl : ReachState.Orphan

public operator fun ReachState.Orphan.Companion.invoke(): ReachState.Orphan = OrphanImpl

public sealed interface OrphanGoReaction {
    public class Transition internal constructor(
        internal val next: ReachState,
    ) : OrphanGoReaction
}

public class OrphanGoTransitions internal constructor(
    @Suppress("unused") private val state: ReachState.Orphan,
) {
    public fun toDone(): OrphanGoReaction = OrphanGoReaction.Transition(ReachState.Done())
}

@KomaStrictDsl
@koma.core.KomaStoreDsl
@OptIn(InternalKomaStrictApi::class)
public class OrphanGoScope internal constructor(
    public val state: ReachState.Orphan,
    public val action: ReachAction.Go,
    onClearPendingActions: () -> Unit,
) : HandlerScope(onClearPendingActions) {
    public val nextState: OrphanGoTransitions = OrphanGoTransitions(state)
}

public class OrphanHandlers internal constructor(
    internal val go: suspend OrphanGoScope.() -> OrphanGoReaction,
    internal val configure: koma.core.StoreBuilder.StateHandlerConfig<ReachState, ReachAction, Nothing, ReachState.Orphan>.() -> Unit,
) : (OrphanHandlersScope) -> OrphanHandlers {
    /** Returns this bundle itself, so an already-built value is assignable to the scope-lambda-typed `states(...)` parameter (value-passing / scope-lambda interchange). */
    override fun invoke(p1: OrphanHandlersScope): OrphanHandlers = this
}

/** Bundles this state's handlers. [configure] appends raw koma DSL at the end of the generated `state<...> {}` block (per-state escape hatch). */
public fun ReachState.Orphan.Companion.actions(
    go: suspend OrphanGoScope.() -> OrphanGoReaction,
    configure: koma.core.StoreBuilder.StateHandlerConfig<ReachState, ReachAction, Nothing, ReachState.Orphan>.() -> Unit = {},
): OrphanHandlers = OrphanHandlers(go, configure)

/**
 * Builder-form overload of `actions(...)` (see [OrphanActionsBuilder]).
 *
 * Note: unlike the named-argument overload, exhaustiveness of the declared handlers is
 * checked **at build time** (fail-fast when the block finishes), not at compile time.
 * This overload exists only for states without enter / exit declarations (those handlers
 * have no builder member).
 */
public fun ReachState.Orphan.Companion.actions(build: OrphanActionsBuilder.() -> Unit): OrphanHandlers = OrphanActionsBuilder().apply(build).build()

/** Receiver of the scope-lambda form (`{ actions(...) }`) of the matching `states(...)` parameter. [actions] mirrors the companion `actions(...)` extension. */
@KomaStrictDsl
@koma.core.KomaStoreDsl
public class OrphanHandlersScope internal constructor() {
    /** Bundles this state's handlers. [configure] appends raw koma DSL at the end of the generated `state<...> {}` block (per-state escape hatch). */
    public fun actions(
        go: suspend OrphanGoScope.() -> OrphanGoReaction,
        configure: koma.core.StoreBuilder.StateHandlerConfig<ReachState, ReachAction, Nothing, ReachState.Orphan>.() -> Unit = {},
    ): OrphanHandlers = OrphanHandlers(go, configure)

    /**
     * Builder-form overload of `actions(...)` (see [OrphanActionsBuilder]).
     *
     * Note: unlike the named-argument overload, exhaustiveness of the declared handlers is
     * checked **at build time** (fail-fast when the block finishes), not at compile time.
     * This overload exists only for states without enter / exit declarations (those handlers
     * have no builder member).
     */
    public fun actions(build: OrphanActionsBuilder.() -> Unit): OrphanHandlers = OrphanActionsBuilder().apply(build).build()
}

/**
 * Builder receiver of the `actions { ... }` overload building [OrphanHandlers].
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
public class OrphanActionsBuilder internal constructor() {
    private val go = SetOnceSlot<suspend OrphanGoScope.() -> OrphanGoReaction>("ReachState.Orphan", "go")

    private val configure = SetOnceSlot<koma.core.StoreBuilder.StateHandlerConfig<ReachState, ReachAction, Nothing, ReachState.Orphan>.() -> Unit>("ReachState.Orphan", "configure")

    /** Registers the handler of the same-named `actions(...)` parameter. Duplicate registration fails fast with [IllegalStateException]. */
    public fun go(handler: suspend OrphanGoScope.() -> OrphanGoReaction) { go.set(handler) }

    /** Appends raw koma DSL at the end of the generated `state<...> {}` block (the trailing `configure` parameter of `actions(...)`). Duplicate registration fails fast with [IllegalStateException]. */
    public fun configure(block: koma.core.StoreBuilder.StateHandlerConfig<ReachState, ReachAction, Nothing, ReachState.Orphan>.() -> Unit) { configure.set(block) }

    internal fun build(): OrphanHandlers {
        val missing = listOfNotNull(
            "go".takeIf { !go.isSet },
        )
        if (missing.isNotEmpty()) throwMissingBuilderEntries("ReachState.Orphan", missing)
        return OrphanHandlers(go.getOrNull()!!, configure.getOrNull() ?: {})
    }
}

// ----- next file -----

// file: ReachState.Start.generated.kt
package example.diag

import me.tbsten.koma.strict.*
import me.tbsten.koma.strict.dsl.*

private data object StartImpl : ReachState.Start

public operator fun ReachState.Start.Companion.invoke(): ReachState.Start = StartImpl

public sealed interface StartGoReaction {
    public class Transition internal constructor(
        internal val next: ReachState,
    ) : StartGoReaction
}

public class StartGoTransitions internal constructor(
    @Suppress("unused") private val state: ReachState.Start,
) {
    public fun toDone(): StartGoReaction = StartGoReaction.Transition(ReachState.Done())
}

@KomaStrictDsl
@koma.core.KomaStoreDsl
@OptIn(InternalKomaStrictApi::class)
public class StartGoScope internal constructor(
    public val state: ReachState.Start,
    public val action: ReachAction.Go,
    onClearPendingActions: () -> Unit,
) : HandlerScope(onClearPendingActions) {
    public val nextState: StartGoTransitions = StartGoTransitions(state)
}

public class StartHandlers internal constructor(
    internal val go: suspend StartGoScope.() -> StartGoReaction,
    internal val configure: koma.core.StoreBuilder.StateHandlerConfig<ReachState, ReachAction, Nothing, ReachState.Start>.() -> Unit,
) : (StartHandlersScope) -> StartHandlers {
    /** Returns this bundle itself, so an already-built value is assignable to the scope-lambda-typed `states(...)` parameter (value-passing / scope-lambda interchange). */
    override fun invoke(p1: StartHandlersScope): StartHandlers = this
}

/** Bundles this state's handlers. [configure] appends raw koma DSL at the end of the generated `state<...> {}` block (per-state escape hatch). */
public fun ReachState.Start.Companion.actions(
    go: suspend StartGoScope.() -> StartGoReaction,
    configure: koma.core.StoreBuilder.StateHandlerConfig<ReachState, ReachAction, Nothing, ReachState.Start>.() -> Unit = {},
): StartHandlers = StartHandlers(go, configure)

/**
 * Builder-form overload of `actions(...)` (see [StartActionsBuilder]).
 *
 * Note: unlike the named-argument overload, exhaustiveness of the declared handlers is
 * checked **at build time** (fail-fast when the block finishes), not at compile time.
 * This overload exists only for states without enter / exit declarations (those handlers
 * have no builder member).
 */
public fun ReachState.Start.Companion.actions(build: StartActionsBuilder.() -> Unit): StartHandlers = StartActionsBuilder().apply(build).build()

/** Receiver of the scope-lambda form (`{ actions(...) }`) of the matching `states(...)` parameter. [actions] mirrors the companion `actions(...)` extension. */
@KomaStrictDsl
@koma.core.KomaStoreDsl
public class StartHandlersScope internal constructor() {
    /** Bundles this state's handlers. [configure] appends raw koma DSL at the end of the generated `state<...> {}` block (per-state escape hatch). */
    public fun actions(
        go: suspend StartGoScope.() -> StartGoReaction,
        configure: koma.core.StoreBuilder.StateHandlerConfig<ReachState, ReachAction, Nothing, ReachState.Start>.() -> Unit = {},
    ): StartHandlers = StartHandlers(go, configure)

    /**
     * Builder-form overload of `actions(...)` (see [StartActionsBuilder]).
     *
     * Note: unlike the named-argument overload, exhaustiveness of the declared handlers is
     * checked **at build time** (fail-fast when the block finishes), not at compile time.
     * This overload exists only for states without enter / exit declarations (those handlers
     * have no builder member).
     */
    public fun actions(build: StartActionsBuilder.() -> Unit): StartHandlers = StartActionsBuilder().apply(build).build()
}

/**
 * Builder receiver of the `actions { ... }` overload building [StartHandlers].
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
public class StartActionsBuilder internal constructor() {
    private val go = SetOnceSlot<suspend StartGoScope.() -> StartGoReaction>("ReachState.Start", "go")

    private val configure = SetOnceSlot<koma.core.StoreBuilder.StateHandlerConfig<ReachState, ReachAction, Nothing, ReachState.Start>.() -> Unit>("ReachState.Start", "configure")

    /** Registers the handler of the same-named `actions(...)` parameter. Duplicate registration fails fast with [IllegalStateException]. */
    public fun go(handler: suspend StartGoScope.() -> StartGoReaction) { go.set(handler) }

    /** Appends raw koma DSL at the end of the generated `state<...> {}` block (the trailing `configure` parameter of `actions(...)`). Duplicate registration fails fast with [IllegalStateException]. */
    public fun configure(block: koma.core.StoreBuilder.StateHandlerConfig<ReachState, ReachAction, Nothing, ReachState.Start>.() -> Unit) { configure.set(block) }

    internal fun build(): StartHandlers {
        val missing = listOfNotNull(
            "go".takeIf { !go.isSet },
        )
        if (missing.isNotEmpty()) throwMissingBuilderEntries("ReachState.Start", missing)
        return StartHandlers(go.getOrNull()!!, configure.getOrNull() ?: {})
    }
}

// ----- next file -----

// file: ReachState.storeSpec.generated.kt
package example.diag

import me.tbsten.koma.strict.*
import me.tbsten.koma.strict.dsl.*

@kotlin.jvm.JvmName("reachStateStates")
@Suppress("NAME_SHADOWING")
public fun koma.core.StoreBuilder<ReachState, ReachAction, Nothing>.states(
    start: StartHandlersScope.() -> StartHandlers,
    orphan: OrphanHandlersScope.() -> OrphanHandlers,
    configure: ReachStateStatesConfigureScope.() -> Unit = {},
) {
    val start = StartHandlersScope().start()
    val orphan = OrphanHandlersScope().orphan()
    val configure = ReachStateStatesConfigureScope().apply(configure)

    state<ReachState.Start> {
        action<ReachAction.Go> {
            when (val r = start.go(StartGoScope(state, action, ::clearPendingActions))) {
                is StartGoReaction.Transition -> nextState { r.next }
            }
        }
        start.configure(this)
        configure.start?.invoke(this)
    }
    state<ReachState.Orphan> {
        action<ReachAction.Go> {
            when (val r = orphan.go(OrphanGoScope(state, action, ::clearPendingActions))) {
                is OrphanGoReaction.Transition -> nextState { r.next }
            }
        }
        orphan.configure(this)
        configure.orphan?.invoke(this)
    }
}

/**
 * Builds a [koma.core.Store] for [ReachState] without spelling the store type arguments.
 *
 * Sugar over the canonical koma entry point — `Store<ReachState, ReachAction, Nothing>(initialState) { states(...) }`
 * builds the exact same store. [initialState] is narrowed to the declared
 * `@StoreSpec(initial = ...)` candidate [ReachState.Start] — compile-time enforced.
 * [configuration] appends raw koma DSL after the generated handlers (store-level escape hatch).
 */
public fun createReachStore(
    initialState: ReachState.Start,
    start: StartHandlersScope.() -> StartHandlers,
    orphan: OrphanHandlersScope.() -> OrphanHandlers,
    context: kotlin.coroutines.CoroutineContext? = null,
    configuration: koma.core.StoreBuilder<ReachState, ReachAction, Nothing>.() -> Unit = {},
): koma.core.Store<ReachState, ReachAction, Nothing> =
    koma.core.Store<ReachState, ReachAction, Nothing>(initialState = initialState, context = context) {
        states(
            start = start,
            orphan = orphan,
        )
        configuration()
    }

/**
 * Builds a [koma.core.Store] for [ReachState] without spelling the store type arguments.
 *
 * Sugar over the canonical koma entry point — `Store<ReachState, ReachAction, Nothing>(initialState) { states(...) }`
 * builds the exact same store. [initialState] accepts any [ReachState] — for
 * restoring a persisted state or starting mid-flow in tests, where [createReachStore]'s
 * compile-time-narrowed initial state does not apply. [configuration] appends raw koma
 * DSL after the generated handlers (store-level escape hatch).
 */
public fun restoreReachStore(
    initialState: ReachState,
    start: StartHandlersScope.() -> StartHandlers,
    orphan: OrphanHandlersScope.() -> OrphanHandlers,
    context: kotlin.coroutines.CoroutineContext? = null,
    configuration: koma.core.StoreBuilder<ReachState, ReachAction, Nothing>.() -> Unit = {},
): koma.core.Store<ReachState, ReachAction, Nothing> =
    koma.core.Store<ReachState, ReachAction, Nothing>(initialState = initialState, context = context) {
        states(
            start = start,
            orphan = orphan,
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
public class ReachStateStatesConfigureScope internal constructor() {
    internal var start: (koma.core.StoreBuilder.StateHandlerConfig<ReachState, ReachAction, Nothing, ReachState.Start>.() -> Unit)? = null

    internal var orphan: (koma.core.StoreBuilder.StateHandlerConfig<ReachState, ReachAction, Nothing, ReachState.Orphan>.() -> Unit)? = null

    /** Appends raw koma DSL at the end of the generated `state<ReachState.Start> {}` block. Fails fast if called twice. */
    public fun start(block: koma.core.StoreBuilder.StateHandlerConfig<ReachState, ReachAction, Nothing, ReachState.Start>.() -> Unit) {
        if (this.start != null) throwDuplicateBuilderEntry("ReachState", "start")
        this.start = block
    }

    /** Appends raw koma DSL at the end of the generated `state<ReachState.Orphan> {}` block. Fails fast if called twice. */
    public fun orphan(block: koma.core.StoreBuilder.StateHandlerConfig<ReachState, ReachAction, Nothing, ReachState.Orphan>.() -> Unit) {
        if (this.orphan != null) throwDuplicateBuilderEntry("ReachState", "orphan")
        this.orphan = block
    }
}
```
