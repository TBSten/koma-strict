## Input:SuspiciousCompanion.kt

```kt
package example.diag

import koma.core.Action
import koma.core.State
import me.tbsten.koma.strict.OnAction
import me.tbsten.koma.strict.StoreSpec

@StoreSpec
sealed interface SuspiciousState : State {
    companion object
    // kotlinc はこの `data` を companion の名前として消費する
    // (companion 名 = `data` + 非 data の `object Home`)
    data object Home : SuspiciousState

    @OnAction<ScAction.Go>(nextState = [Home::class])
    interface Away : SuspiciousState { companion object }
}

sealed interface ScAction : Action {
    data object Go : ScAction
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
w: [ksp] <TMPDIR>/Kotlin-Compilation<N>/sources/SuspiciousCompanion.kt:13: koma-strict: the companion object of 'example.diag.SuspiciousState' is named 'data'. An unnamed `companion object` immediately followed by a `data ...` declaration is parsed by kotlinc as a companion NAMED 'data' (the modifier is consumed by the parser). If this is unintended, declare the companion last or give it an explicit body: `companion object {}`.
```

## Output:Generated sources

```kt
// file: SuspiciousState.Away.generated.kt
package example.diag

import me.tbsten.koma.strict.*
import me.tbsten.koma.strict.dsl.*

private data object AwayImpl : SuspiciousState.Away

public operator fun SuspiciousState.Away.Companion.invoke(): SuspiciousState.Away = AwayImpl

public sealed interface AwayGoReaction {
    public class Transition internal constructor(
        internal val next: SuspiciousState,
    ) : AwayGoReaction
}

public class AwayGoTransitions internal constructor(
    @Suppress("unused") private val state: SuspiciousState.Away,
) {
    public fun toHome(): AwayGoReaction = AwayGoReaction.Transition(SuspiciousState.Home)
}

@KomaStrictDsl
@koma.core.KomaStoreDsl
@OptIn(InternalKomaStrictApi::class)
public class AwayGoScope internal constructor(
    public val state: SuspiciousState.Away,
    public val action: ScAction.Go,
    onClearPendingActions: () -> Unit,
) : HandlerScope(onClearPendingActions) {
    public val nextState: AwayGoTransitions = AwayGoTransitions(state)
}

public class AwayHandlers internal constructor(
    internal val go: suspend AwayGoScope.() -> AwayGoReaction,
    internal val configure: koma.core.StoreBuilder.StateHandlerConfig<SuspiciousState, ScAction, Nothing, SuspiciousState.Away>.() -> Unit,
) : (AwayHandlersScope) -> AwayHandlers {
    /** Returns this bundle itself, so an already-built value is assignable to the scope-lambda-typed `states(...)` parameter (value-passing / scope-lambda interchange). */
    override fun invoke(p1: AwayHandlersScope): AwayHandlers = this
}

/** Bundles this state's handlers. [configure] appends raw koma DSL at the end of the generated `state<...> {}` block (per-state escape hatch). */
public fun SuspiciousState.Away.Companion.actions(
    go: suspend AwayGoScope.() -> AwayGoReaction,
    configure: koma.core.StoreBuilder.StateHandlerConfig<SuspiciousState, ScAction, Nothing, SuspiciousState.Away>.() -> Unit = {},
): AwayHandlers = AwayHandlers(go, configure)

/**
 * Builder-form overload of `actions(...)` (see [AwayActionsBuilder]).
 *
 * Note: unlike the named-argument overload, exhaustiveness of the declared handlers is
 * checked **at build time** (fail-fast when the block finishes), not at compile time.
 * This overload exists only for states without enter / exit declarations (those handlers
 * have no builder member).
 */
public fun SuspiciousState.Away.Companion.actions(build: AwayActionsBuilder.() -> Unit): AwayHandlers = AwayActionsBuilder().apply(build).build()

/** Receiver of the scope-lambda form (`{ actions(...) }`) of the matching `states(...)` parameter. [actions] mirrors the companion `actions(...)` extension. */
@KomaStrictDsl
@koma.core.KomaStoreDsl
public class AwayHandlersScope internal constructor() {
    /** Bundles this state's handlers. [configure] appends raw koma DSL at the end of the generated `state<...> {}` block (per-state escape hatch). */
    public fun actions(
        go: suspend AwayGoScope.() -> AwayGoReaction,
        configure: koma.core.StoreBuilder.StateHandlerConfig<SuspiciousState, ScAction, Nothing, SuspiciousState.Away>.() -> Unit = {},
    ): AwayHandlers = AwayHandlers(go, configure)

    /**
     * Builder-form overload of `actions(...)` (see [AwayActionsBuilder]).
     *
     * Note: unlike the named-argument overload, exhaustiveness of the declared handlers is
     * checked **at build time** (fail-fast when the block finishes), not at compile time.
     * This overload exists only for states without enter / exit declarations (those handlers
     * have no builder member).
     */
    public fun actions(build: AwayActionsBuilder.() -> Unit): AwayHandlers = AwayActionsBuilder().apply(build).build()
}

/**
 * Builder receiver of the `actions { ... }` overload building [AwayHandlers].
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
public class AwayActionsBuilder internal constructor() {
    private val go = SetOnceSlot<suspend AwayGoScope.() -> AwayGoReaction>("SuspiciousState.Away", "go")

    private val configure = SetOnceSlot<koma.core.StoreBuilder.StateHandlerConfig<SuspiciousState, ScAction, Nothing, SuspiciousState.Away>.() -> Unit>("SuspiciousState.Away", "configure")

    /** Registers the handler of the same-named `actions(...)` parameter. Duplicate registration fails fast with [IllegalStateException]. */
    public fun go(handler: suspend AwayGoScope.() -> AwayGoReaction) { go.set(handler) }

    /** Appends raw koma DSL at the end of the generated `state<...> {}` block (the trailing `configure` parameter of `actions(...)`). Duplicate registration fails fast with [IllegalStateException]. */
    public fun configure(block: koma.core.StoreBuilder.StateHandlerConfig<SuspiciousState, ScAction, Nothing, SuspiciousState.Away>.() -> Unit) { configure.set(block) }

    internal fun build(): AwayHandlers {
        val missing = listOfNotNull(
            "go".takeIf { !go.isSet },
        )
        if (missing.isNotEmpty()) throwMissingBuilderEntries("SuspiciousState.Away", missing)
        return AwayHandlers(go.getOrNull()!!, configure.getOrNull() ?: {})
    }
}

// ----- next file -----

// file: SuspiciousState.storeSpec.generated.kt
package example.diag

import me.tbsten.koma.strict.*
import me.tbsten.koma.strict.dsl.*

@kotlin.jvm.JvmName("suspiciousStateStates")
@Suppress("NAME_SHADOWING")
public fun koma.core.StoreBuilder<SuspiciousState, ScAction, Nothing>.states(
    away: AwayHandlersScope.() -> AwayHandlers,
    configure: SuspiciousStateStatesConfigureScope.() -> Unit = {},
) {
    val away = AwayHandlersScope().away()
    val configure = SuspiciousStateStatesConfigureScope().apply(configure)

    state<SuspiciousState.Away> {
        action<ScAction.Go> {
            when (val r = away.go(AwayGoScope(state, action, ::clearPendingActions))) {
                is AwayGoReaction.Transition -> nextState { r.next }
            }
        }
        away.configure(this)
        configure.away?.invoke(this)
    }
}

/**
 * Builds a [koma.core.Store] for [SuspiciousState] without spelling the store type arguments.
 *
 * Sugar over the canonical koma entry point — `Store<SuspiciousState, ScAction, Nothing>(initialState) { states(...) }`
 * builds the exact same store. [configuration] appends raw koma DSL after the generated
 * handlers (store-level escape hatch).
 */
public fun restoreSuspiciousStore(
    initialState: SuspiciousState,
    away: AwayHandlersScope.() -> AwayHandlers,
    context: kotlin.coroutines.CoroutineContext? = null,
    configuration: koma.core.StoreBuilder<SuspiciousState, ScAction, Nothing>.() -> Unit = {},
): koma.core.Store<SuspiciousState, ScAction, Nothing> =
    koma.core.Store<SuspiciousState, ScAction, Nothing>(initialState = initialState, context = context) {
        states(
            away = away,
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
public class SuspiciousStateStatesConfigureScope internal constructor() {
    internal var away: (koma.core.StoreBuilder.StateHandlerConfig<SuspiciousState, ScAction, Nothing, SuspiciousState.Away>.() -> Unit)? = null

    /** Appends raw koma DSL at the end of the generated `state<SuspiciousState.Away> {}` block. Fails fast if called twice. */
    public fun away(block: koma.core.StoreBuilder.StateHandlerConfig<SuspiciousState, ScAction, Nothing, SuspiciousState.Away>.() -> Unit) {
        if (this.away != null) throwDuplicateBuilderEntry("SuspiciousState", "away")
        this.away = block
    }
}
```
