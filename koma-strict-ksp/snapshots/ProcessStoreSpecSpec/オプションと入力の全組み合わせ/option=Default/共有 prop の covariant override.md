## Input:DetailState.kt

```kt
package example.detail

import koma.core.Action
import koma.core.State
import me.tbsten.koma.strict.OnAction
import me.tbsten.koma.strict.OnEnter
import me.tbsten.koma.strict.StoreSpec

@StoreSpec(initial = [DetailState.Loading::class])
sealed interface DetailState : State {
    companion object

    @OnEnter(nextState = [Loaded.Full::class])
    interface Loading : DetailState { companion object }

    sealed interface Loaded : DetailState {
        val content: CharSequence          // 祖先は広い型で共有宣言
        val loadedAt: Long
        companion object

        @OnAction<DetailAction.Refine>(nextState = [Full::class])
        interface Full : Loaded {
            override val content: String   // covariant override で狭める
            companion object
        }
    }
}

sealed interface DetailAction : Action {
    data object Refine : DetailAction
}
```

## Input:DetailStoreUsage.kt

```kt
package example.detail

import koma.core.Store

fun buildDetailStore(): Store<DetailState, DetailAction, Nothing> =
    Store<DetailState, DetailAction, Nothing>(initialState = DetailState.Loading()) {
        states(
            loading = DetailState.Loading.actions(
                enter = { nextState.toLoadedFull(content = "fetched", loadedAt = 0L) },
            ),
            loaded = DetailState.Loaded.states(
                full = DetailState.Loaded.Full.actions(
                    refine = {
                        val refined: String = state.content.trim() // override 後の狭い型
                        nextState.toFull(content = refined)        // loadedAt は持ち越し
                    },
                ),
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
// file: DetailState.Loaded.Full.generated.kt
package example.detail

import me.tbsten.koma.strict.*
import me.tbsten.koma.strict.dsl.*

private data class LoadedFullImpl(
    override val content: String,
    override val loadedAt: Long,
) : DetailState.Loaded.Full

public operator fun DetailState.Loaded.Full.Companion.invoke(
    content: String,
    loadedAt: Long,
): DetailState.Loaded.Full = LoadedFullImpl(content, loadedAt)

public sealed interface LoadedFullRefineReaction {
    public class Transition internal constructor(
        internal val next: DetailState,
    ) : LoadedFullRefineReaction
}

public class LoadedFullRefineTransitions internal constructor(
    private val state: DetailState.Loaded.Full,
) {
    public fun toFull(
        content: String = state.content,
        loadedAt: Long = state.loadedAt,
    ): LoadedFullRefineReaction = LoadedFullRefineReaction.Transition(DetailState.Loaded.Full(content, loadedAt))
}

@KomaStrictDsl
@koma.core.KomaStoreDsl
@OptIn(InternalKomaStrictApi::class)
public class LoadedFullRefineScope internal constructor(
    public val state: DetailState.Loaded.Full,
    public val action: DetailAction.Refine,
    onClearPendingActions: () -> Unit,
) : HandlerScope(onClearPendingActions) {
    public val nextState: LoadedFullRefineTransitions = LoadedFullRefineTransitions(state)
}

public class LoadedFullHandlers internal constructor(
    internal val refine: suspend LoadedFullRefineScope.() -> LoadedFullRefineReaction,
    internal val configure: koma.core.StoreBuilder.StateHandlerConfig<DetailState, DetailAction, Nothing, DetailState.Loaded.Full>.() -> Unit,
) : (LoadedFullHandlersScope) -> LoadedFullHandlers {
    /** Returns this bundle itself, so an already-built value is assignable to the scope-lambda-typed `states(...)` parameter (value-passing / scope-lambda interchange). */
    override fun invoke(p1: LoadedFullHandlersScope): LoadedFullHandlers = this
}

/** Bundles this state's handlers. [configure] appends raw koma DSL at the end of the generated `state<...> {}` block (per-state escape hatch). */
public fun DetailState.Loaded.Full.Companion.actions(
    refine: suspend LoadedFullRefineScope.() -> LoadedFullRefineReaction,
    configure: koma.core.StoreBuilder.StateHandlerConfig<DetailState, DetailAction, Nothing, DetailState.Loaded.Full>.() -> Unit = {},
): LoadedFullHandlers = LoadedFullHandlers(refine, configure)

/**
 * Builder-form overload of `actions(...)` (see [LoadedFullActionsBuilder]).
 *
 * Note: unlike the named-argument overload, exhaustiveness of the declared handlers is
 * checked **at build time** (fail-fast when the block finishes), not at compile time.
 * This overload exists only for states without enter / exit declarations (those handlers
 * have no builder member).
 */
public fun DetailState.Loaded.Full.Companion.actions(build: LoadedFullActionsBuilder.() -> Unit): LoadedFullHandlers = LoadedFullActionsBuilder().apply(build).build()

/** Receiver of the scope-lambda form (`{ actions(...) }`) of the matching `states(...)` parameter. [actions] mirrors the companion `actions(...)` extension. */
@KomaStrictDsl
@koma.core.KomaStoreDsl
public class LoadedFullHandlersScope internal constructor() {
    /** Bundles this state's handlers. [configure] appends raw koma DSL at the end of the generated `state<...> {}` block (per-state escape hatch). */
    public fun actions(
        refine: suspend LoadedFullRefineScope.() -> LoadedFullRefineReaction,
        configure: koma.core.StoreBuilder.StateHandlerConfig<DetailState, DetailAction, Nothing, DetailState.Loaded.Full>.() -> Unit = {},
    ): LoadedFullHandlers = LoadedFullHandlers(refine, configure)

    /**
     * Builder-form overload of `actions(...)` (see [LoadedFullActionsBuilder]).
     *
     * Note: unlike the named-argument overload, exhaustiveness of the declared handlers is
     * checked **at build time** (fail-fast when the block finishes), not at compile time.
     * This overload exists only for states without enter / exit declarations (those handlers
     * have no builder member).
     */
    public fun actions(build: LoadedFullActionsBuilder.() -> Unit): LoadedFullHandlers = LoadedFullActionsBuilder().apply(build).build()
}

/**
 * Builder receiver of the `actions { ... }` overload building [LoadedFullHandlers].
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
public class LoadedFullActionsBuilder internal constructor() {
    private val refine = SetOnceSlot<suspend LoadedFullRefineScope.() -> LoadedFullRefineReaction>("DetailState.Loaded.Full", "refine")

    private val configure = SetOnceSlot<koma.core.StoreBuilder.StateHandlerConfig<DetailState, DetailAction, Nothing, DetailState.Loaded.Full>.() -> Unit>("DetailState.Loaded.Full", "configure")

    /** Registers the handler of the same-named `actions(...)` parameter. Duplicate registration fails fast with [IllegalStateException]. */
    public fun refine(handler: suspend LoadedFullRefineScope.() -> LoadedFullRefineReaction) { refine.set(handler) }

    /** Appends raw koma DSL at the end of the generated `state<...> {}` block (the trailing `configure` parameter of `actions(...)`). Duplicate registration fails fast with [IllegalStateException]. */
    public fun configure(block: koma.core.StoreBuilder.StateHandlerConfig<DetailState, DetailAction, Nothing, DetailState.Loaded.Full>.() -> Unit) { configure.set(block) }

    internal fun build(): LoadedFullHandlers {
        val missing = listOfNotNull(
            "refine".takeIf { !refine.isSet },
        )
        if (missing.isNotEmpty()) throwMissingBuilderEntries("DetailState.Loaded.Full", missing)
        return LoadedFullHandlers(refine.getOrNull()!!, configure.getOrNull() ?: {})
    }
}

// ----- next file -----

// file: DetailState.Loaded.generated.kt
package example.detail

import me.tbsten.koma.strict.*
import me.tbsten.koma.strict.dsl.*

public class LoadedGroupHandlers internal constructor(
    internal val full: LoadedFullHandlers,
    internal val configure: LoadedStatesConfigureScope,
) : (LoadedGroupHandlersScope) -> LoadedGroupHandlers {
    /** Returns this bundle itself, so an already-built value is assignable to the scope-lambda-typed `states(...)` parameter (value-passing / scope-lambda interchange). */
    override fun invoke(p1: LoadedGroupHandlersScope): LoadedGroupHandlers = this
}

public fun DetailState.Loaded.Companion.states(
    full: LoadedFullHandlersScope.() -> LoadedFullHandlers,
    configure: LoadedStatesConfigureScope.() -> Unit = {},
): LoadedGroupHandlers =
    LoadedGroupHandlers(
        LoadedFullHandlersScope().full(),
        LoadedStatesConfigureScope().apply(configure),
    )

/**
 * Builder-form overload of `states(...)` (see [LoadedGroupBuilder]).
 *
 * Note: unlike the named-argument overload, exhaustiveness of the child states (and the
 * default block) is checked **at build time** (fail-fast when the block finishes), not at
 * compile time.
 */
public fun DetailState.Loaded.Companion.states(build: LoadedGroupBuilder.() -> Unit): LoadedGroupHandlers = LoadedGroupBuilder().apply(build).build()

/** Receiver of the scope-lambda form (`{ states(...) }`) of the matching `states(...)` parameter. [states] mirrors the companion `states(...)` extension. */
@KomaStrictDsl
@koma.core.KomaStoreDsl
public class LoadedGroupHandlersScope internal constructor() {
    public fun states(
        full: LoadedFullHandlersScope.() -> LoadedFullHandlers,
        configure: LoadedStatesConfigureScope.() -> Unit = {},
    ): LoadedGroupHandlers =
        LoadedGroupHandlers(
            LoadedFullHandlersScope().full(),
            LoadedStatesConfigureScope().apply(configure),
        )

    /**
     * Builder-form overload of `states(...)` (see [LoadedGroupBuilder]).
     *
     * Note: unlike the named-argument overload, exhaustiveness of the child states (and the
     * default block) is checked **at build time** (fail-fast when the block finishes), not at
     * compile time.
     */
    public fun states(build: LoadedGroupBuilder.() -> Unit): LoadedGroupHandlers = LoadedGroupBuilder().apply(build).build()
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
public class LoadedStatesConfigureScope internal constructor() {
    internal var full: (koma.core.StoreBuilder.StateHandlerConfig<DetailState, DetailAction, Nothing, DetailState.Loaded.Full>.() -> Unit)? = null

    /** Appends raw koma DSL at the end of the generated `state<DetailState.Loaded.Full> {}` block. Fails fast if called twice. */
    public fun full(block: koma.core.StoreBuilder.StateHandlerConfig<DetailState, DetailAction, Nothing, DetailState.Loaded.Full>.() -> Unit) {
        if (this.full != null) throwDuplicateBuilderEntry("DetailState.Loaded", "full")
        this.full = block
    }
}

/**
 * Builder receiver of the `states { ... }` overload building [LoadedGroupHandlers].
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
public class LoadedGroupBuilder internal constructor() {
    private val full = SetOnceSlot<LoadedFullHandlers>("DetailState.Loaded", "full")

    /** Registers this child state's handlers by an already-built value. Duplicate registration fails fast with [IllegalStateException]. */
    public fun full(handlers: LoadedFullHandlers) { full.set(handlers) }

    /** Registers this child state's handlers with a nested builder block (fails fast if already registered). */
    public fun full(build: LoadedFullActionsBuilder.() -> Unit) {
        full(LoadedFullActionsBuilder().apply(build).build())
    }

    internal fun build(): LoadedGroupHandlers {
        val missing = listOfNotNull(
            "full".takeIf { !full.isSet },
        )
        if (missing.isNotEmpty()) throwMissingBuilderEntries("DetailState.Loaded", missing)
        return LoadedGroupHandlers(full.getOrNull()!!, LoadedStatesConfigureScope())
    }
}

// ----- next file -----

// file: DetailState.Loading.generated.kt
package example.detail

import me.tbsten.koma.strict.*
import me.tbsten.koma.strict.dsl.*

private data object LoadingImpl : DetailState.Loading

public operator fun DetailState.Loading.Companion.invoke(): DetailState.Loading = LoadingImpl

public sealed interface LoadingEnterReaction {
    public class Transition internal constructor(
        internal val next: DetailState,
    ) : LoadingEnterReaction
}

public class LoadingEnterTransitions internal constructor(
    @Suppress("unused") private val state: DetailState.Loading,
) {
    public fun toLoadedFull(
        content: String,
        loadedAt: Long,
    ): LoadingEnterReaction = LoadingEnterReaction.Transition(DetailState.Loaded.Full(content, loadedAt))
}

@KomaStrictDsl
@koma.core.KomaStoreDsl
@OptIn(InternalKomaStrictApi::class)
public class LoadingEnterScope internal constructor(
    public val state: DetailState.Loading,
    onClearPendingActions: () -> Unit,
) : HandlerScope(onClearPendingActions) {
    public val nextState: LoadingEnterTransitions = LoadingEnterTransitions(state)
}

public class LoadingHandlers internal constructor(
    internal val enter: suspend LoadingEnterScope.() -> LoadingEnterReaction,
    internal val configure: koma.core.StoreBuilder.StateHandlerConfig<DetailState, DetailAction, Nothing, DetailState.Loading>.() -> Unit,
) : (LoadingHandlersScope) -> LoadingHandlers {
    /** Returns this bundle itself, so an already-built value is assignable to the scope-lambda-typed `states(...)` parameter (value-passing / scope-lambda interchange). */
    override fun invoke(p1: LoadingHandlersScope): LoadingHandlers = this
}

/** Bundles this state's handlers. [configure] appends raw koma DSL at the end of the generated `state<...> {}` block (per-state escape hatch). */
public fun DetailState.Loading.Companion.actions(
    enter: suspend LoadingEnterScope.() -> LoadingEnterReaction,
    configure: koma.core.StoreBuilder.StateHandlerConfig<DetailState, DetailAction, Nothing, DetailState.Loading>.() -> Unit = {},
): LoadingHandlers = LoadingHandlers(enter, configure)

/** Receiver of the scope-lambda form (`{ actions(...) }`) of the matching `states(...)` parameter. [actions] mirrors the companion `actions(...)` extension. */
@KomaStrictDsl
@koma.core.KomaStoreDsl
public class LoadingHandlersScope internal constructor() {
    /** Bundles this state's handlers. [configure] appends raw koma DSL at the end of the generated `state<...> {}` block (per-state escape hatch). */
    public fun actions(
        enter: suspend LoadingEnterScope.() -> LoadingEnterReaction,
        configure: koma.core.StoreBuilder.StateHandlerConfig<DetailState, DetailAction, Nothing, DetailState.Loading>.() -> Unit = {},
    ): LoadingHandlers = LoadingHandlers(enter, configure)
}

// ----- next file -----

// file: DetailState.storeSpec.generated.kt
package example.detail

import me.tbsten.koma.strict.*
import me.tbsten.koma.strict.dsl.*

@kotlin.jvm.JvmName("detailStateStates")
@Suppress("NAME_SHADOWING", "UNCHECKED_CAST")
public fun koma.core.StoreBuilder<DetailState, DetailAction, Nothing>.states(
    loading: LoadingHandlersScope.() -> LoadingHandlers,
    loaded: LoadedGroupHandlersScope.() -> LoadedGroupHandlers,
    configure: DetailStateStatesConfigureScope.() -> Unit = {},
) {
    val loading = LoadingHandlersScope().loading()
    val loaded = LoadedGroupHandlersScope().loaded()
    val configure = DetailStateStatesConfigureScope().apply(configure)

    state<DetailState.Loading> {
        enter {
            when (val r = loading.enter(LoadingEnterScope(state, ::clearPendingActions))) {
                is LoadingEnterReaction.Transition -> nextState { r.next }
            }
        }
        loading.configure(this)
        configure.loading?.invoke(this)
    }
    state<DetailState.Loaded.Full> {
        action<DetailAction.Refine> {
            when (val r = loaded.full.refine(LoadedFullRefineScope(state, action, ::clearPendingActions))) {
                is LoadedFullRefineReaction.Transition -> nextState { r.next }
            }
        }
        loaded.full.configure(this)
        loaded.configure.full?.invoke(this)
        configure.loaded?.invoke(this as koma.core.StoreBuilder.StateHandlerConfig<DetailState, DetailAction, Nothing, DetailState.Loaded>)
    }
}

/**
 * Builds a [koma.core.Store] for [DetailState] without spelling the store type arguments.
 *
 * Sugar over the canonical koma entry point — `Store<DetailState, DetailAction, Nothing>(initialState) { states(...) }`
 * builds the exact same store. [initialState] is narrowed to the declared
 * `@StoreSpec(initial = ...)` candidate [DetailState.Loading] — compile-time enforced.
 * [configuration] appends raw koma DSL after the generated handlers (store-level escape hatch).
 */
public fun createDetailStore(
    initialState: DetailState.Loading,
    loading: LoadingHandlersScope.() -> LoadingHandlers,
    loaded: LoadedGroupHandlersScope.() -> LoadedGroupHandlers,
    context: kotlin.coroutines.CoroutineContext? = null,
    configuration: koma.core.StoreBuilder<DetailState, DetailAction, Nothing>.() -> Unit = {},
): koma.core.Store<DetailState, DetailAction, Nothing> =
    koma.core.Store<DetailState, DetailAction, Nothing>(initialState = initialState, context = context) {
        states(
            loading = loading,
            loaded = loaded,
        )
        configuration()
    }

/**
 * Builds a [koma.core.Store] for [DetailState] without spelling the store type arguments.
 *
 * Sugar over the canonical koma entry point — `Store<DetailState, DetailAction, Nothing>(initialState) { states(...) }`
 * builds the exact same store. [initialState] accepts any [DetailState] — for
 * restoring a persisted state or starting mid-flow in tests, where [createDetailStore]'s
 * compile-time-narrowed initial state does not apply. [configuration] appends raw koma
 * DSL after the generated handlers (store-level escape hatch).
 */
public fun restoreDetailStore(
    initialState: DetailState,
    loading: LoadingHandlersScope.() -> LoadingHandlers,
    loaded: LoadedGroupHandlersScope.() -> LoadedGroupHandlers,
    context: kotlin.coroutines.CoroutineContext? = null,
    configuration: koma.core.StoreBuilder<DetailState, DetailAction, Nothing>.() -> Unit = {},
): koma.core.Store<DetailState, DetailAction, Nothing> =
    koma.core.Store<DetailState, DetailAction, Nothing>(initialState = initialState, context = context) {
        states(
            loading = loading,
            loaded = loaded,
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
public class DetailStateStatesConfigureScope internal constructor() {
    internal var loading: (koma.core.StoreBuilder.StateHandlerConfig<DetailState, DetailAction, Nothing, DetailState.Loading>.() -> Unit)? = null

    internal var loaded: (koma.core.StoreBuilder.StateHandlerConfig<DetailState, DetailAction, Nothing, DetailState.Loaded>.() -> Unit)? = null

    /** Appends raw koma DSL at the end of the generated `state<DetailState.Loading> {}` block. Fails fast if called twice. */
    public fun loading(block: koma.core.StoreBuilder.StateHandlerConfig<DetailState, DetailAction, Nothing, DetailState.Loading>.() -> Unit) {
        if (this.loading != null) throwDuplicateBuilderEntry("DetailState", "loading")
        this.loading = block
    }

    /** Appends raw koma DSL to every generated `state<...> {}` block under [DetailState.Loaded] (a shared escape, expanded like shared declarations). Fails fast if called twice. */
    public fun loaded(block: koma.core.StoreBuilder.StateHandlerConfig<DetailState, DetailAction, Nothing, DetailState.Loaded>.() -> Unit) {
        if (this.loaded != null) throwDuplicateBuilderEntry("DetailState", "loaded")
        this.loaded = block
    }
}
```
