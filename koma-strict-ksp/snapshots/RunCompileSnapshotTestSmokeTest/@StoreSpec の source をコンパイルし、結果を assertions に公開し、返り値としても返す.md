## Input:MyState.kt

```kt
package smoke.compile

import koma.core.Action
import koma.core.State
import me.tbsten.koma.strict.OnAction
import me.tbsten.koma.strict.StoreSpec

@StoreSpec
sealed interface MyState : State {
    companion object

    @OnAction<MyAction.Load>(nextState = [Loaded::class])
    interface Idle : MyState { companion object }

    interface Loaded : MyState { companion object }
}

sealed interface MyAction : Action {
    data object Load : MyAction
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
// file: MyState.Idle.generated.kt
package smoke.compile

import me.tbsten.koma.strict.*
import me.tbsten.koma.strict.dsl.*

private data object IdleImpl : MyState.Idle

public operator fun MyState.Idle.Companion.invoke(): MyState.Idle = IdleImpl

public sealed interface IdleLoadReaction {
    public class Transition internal constructor(
        internal val next: MyState,
    ) : IdleLoadReaction
}

public class IdleLoadTransitions internal constructor(
    @Suppress("unused") private val state: MyState.Idle,
) {
    public fun toLoaded(): IdleLoadReaction = IdleLoadReaction.Transition(MyState.Loaded())
}

@KomaStrictDsl
@koma.core.KomaStoreDsl
@OptIn(InternalKomaStrictApi::class)
public class IdleLoadScope internal constructor(
    public val state: MyState.Idle,
    public val action: MyAction.Load,
    onClearPendingActions: () -> Unit,
) : HandlerScope(onClearPendingActions) {
    public val nextState: IdleLoadTransitions = IdleLoadTransitions(state)
}

public class IdleHandlers internal constructor(
    internal val load: suspend IdleLoadScope.() -> IdleLoadReaction,
    internal val configure: koma.core.StoreBuilder.StateHandlerConfig<MyState, MyAction, Nothing, MyState.Idle>.() -> Unit,
) : (IdleHandlersScope) -> IdleHandlers {
    /** Returns this bundle itself, so an already-built value is assignable to the scope-lambda-typed `states(...)` parameter (value-passing / scope-lambda interchange). */
    override fun invoke(p1: IdleHandlersScope): IdleHandlers = this
}

/** Bundles this state's handlers. [configure] appends raw koma DSL at the end of the generated `state<...> {}` block (per-state escape hatch). */
public fun MyState.Idle.Companion.actions(
    load: suspend IdleLoadScope.() -> IdleLoadReaction,
    configure: koma.core.StoreBuilder.StateHandlerConfig<MyState, MyAction, Nothing, MyState.Idle>.() -> Unit = {},
): IdleHandlers = IdleHandlers(load, configure)

/**
 * Builder-form overload of `actions(...)` (see [IdleActionsBuilder]).
 *
 * Note: unlike the named-argument overload, exhaustiveness of the declared handlers is
 * checked **at build time** (fail-fast when the block finishes), not at compile time.
 * This overload exists only for states without enter / exit declarations (those handlers
 * have no builder member).
 */
public fun MyState.Idle.Companion.actions(build: IdleActionsBuilder.() -> Unit): IdleHandlers = IdleActionsBuilder().apply(build).build()

/** Receiver of the scope-lambda form (`{ actions(...) }`) of the matching `states(...)` parameter. [actions] mirrors the companion `actions(...)` extension. */
@KomaStrictDsl
@koma.core.KomaStoreDsl
public class IdleHandlersScope internal constructor() {
    /** Bundles this state's handlers. [configure] appends raw koma DSL at the end of the generated `state<...> {}` block (per-state escape hatch). */
    public fun actions(
        load: suspend IdleLoadScope.() -> IdleLoadReaction,
        configure: koma.core.StoreBuilder.StateHandlerConfig<MyState, MyAction, Nothing, MyState.Idle>.() -> Unit = {},
    ): IdleHandlers = IdleHandlers(load, configure)

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
    private val load = SetOnceSlot<suspend IdleLoadScope.() -> IdleLoadReaction>("MyState.Idle", "load")

    private val configure = SetOnceSlot<koma.core.StoreBuilder.StateHandlerConfig<MyState, MyAction, Nothing, MyState.Idle>.() -> Unit>("MyState.Idle", "configure")

    /** Registers the handler of the same-named `actions(...)` parameter. Duplicate registration fails fast with [IllegalStateException]. */
    public fun load(handler: suspend IdleLoadScope.() -> IdleLoadReaction) { load.set(handler) }

    /** Appends raw koma DSL at the end of the generated `state<...> {}` block (the trailing `configure` parameter of `actions(...)`). Duplicate registration fails fast with [IllegalStateException]. */
    public fun configure(block: koma.core.StoreBuilder.StateHandlerConfig<MyState, MyAction, Nothing, MyState.Idle>.() -> Unit) { configure.set(block) }

    internal fun build(): IdleHandlers {
        val missing = listOfNotNull(
            "load".takeIf { !load.isSet },
        )
        if (missing.isNotEmpty()) throwMissingBuilderEntries("MyState.Idle", missing)
        return IdleHandlers(load.getOrNull()!!, configure.getOrNull() ?: {})
    }
}

// ----- next file -----

// file: MyState.Loaded.generated.kt
package smoke.compile

import me.tbsten.koma.strict.*
import me.tbsten.koma.strict.dsl.*

private data object LoadedImpl : MyState.Loaded

public operator fun MyState.Loaded.Companion.invoke(): MyState.Loaded = LoadedImpl

// ----- next file -----

// file: MyState.storeSpec.generated.kt
package smoke.compile

import me.tbsten.koma.strict.*
import me.tbsten.koma.strict.dsl.*

@kotlin.jvm.JvmName("myStateStates")
@Suppress("NAME_SHADOWING")
public fun koma.core.StoreBuilder<MyState, MyAction, Nothing>.states(
    idle: IdleHandlersScope.() -> IdleHandlers,
    configure: MyStateStatesConfigureScope.() -> Unit = {},
) {
    val idle = IdleHandlersScope().idle()
    val configure = MyStateStatesConfigureScope().apply(configure)

    state<MyState.Idle> {
        action<MyAction.Load> {
            when (val r = idle.load(IdleLoadScope(state, action, ::clearPendingActions))) {
                is IdleLoadReaction.Transition -> nextState { r.next }
            }
        }
        idle.configure(this)
        configure.idle?.invoke(this)
    }
}

/**
 * Builds a [koma.core.Store] for [MyState] without spelling the store type arguments.
 *
 * Sugar over the canonical koma entry point — `Store<MyState, MyAction, Nothing>(initialState) { states(...) }`
 * builds the exact same store. [configuration] appends raw koma DSL after the generated
 * handlers (store-level escape hatch).
 */
public fun myStore(
    initialState: MyState,
    idle: IdleHandlersScope.() -> IdleHandlers,
    context: kotlin.coroutines.CoroutineContext? = null,
    configuration: koma.core.StoreBuilder<MyState, MyAction, Nothing>.() -> Unit = {},
): koma.core.Store<MyState, MyAction, Nothing> =
    koma.core.Store<MyState, MyAction, Nothing>(initialState = initialState, context = context) {
        states(
            idle = idle,
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
public class MyStateStatesConfigureScope internal constructor() {
    internal var idle: (koma.core.StoreBuilder.StateHandlerConfig<MyState, MyAction, Nothing, MyState.Idle>.() -> Unit)? = null

    /** Appends raw koma DSL at the end of the generated `state<MyState.Idle> {}` block. Fails fast if called twice. */
    public fun idle(block: koma.core.StoreBuilder.StateHandlerConfig<MyState, MyAction, Nothing, MyState.Idle>.() -> Unit) {
        if (this.idle != null) throwDuplicateBuilderEntry("MyState", "idle")
        this.idle = block
    }
}
```
