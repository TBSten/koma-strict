## Input:LceState.kt

```kt
package example.lce

import koma.core.Action
import koma.core.Event
import koma.core.State
import me.tbsten.koma.strict.OnAction
import me.tbsten.koma.strict.OnEnter
import me.tbsten.koma.strict.StoreSpec

@StoreSpec(initial = [LceState.Loading::class])   // actions / events は宣言から推論
sealed interface LceState : State {
    companion object

    @OnEnter(nextState = [Content::class, Error::class], emit = [LceEvent.LoadFailed::class])
    interface Loading : LceState { companion object }

    @OnAction<LceAction.Reload>(nextState = [Loading::class])
    interface Content : LceState { val data: String; companion object }

    @OnAction<LceAction.Retry>(nextState = [Loading::class])
    interface Error : LceState { val message: String?; companion object }
}

sealed interface LceAction : Action {
    data object Reload : LceAction
    data object Retry : LceAction
}

sealed interface LceEvent : Event {
    data class LoadFailed(val message: String?) : LceEvent
}
```

## Input:LceConfigureUsage.kt

```kt
package example.lce

import koma.core.Store

fun buildConfiguredLceStore(): Store<LceState, LceAction, LceEvent> =
    Store<LceState, LceAction, LceEvent>(initialState = LceState.Loading()) {
        states(
            loading = LceState.Loading.actions(
                enter = {
                    clearPendingActions()   // 生成 Scope の koma passthrough
                    nextState.toContent(data = "fetched")
                },
            ),
            content = LceState.Content.actions(
                reload = { nextState.toLoading() },
            ) {
                // per-state escape hatch: 素の koma DSL (StateHandlerConfig) を trailing lambda で書ける
                exit { }
            },
            error = LceState.Error.actions(retry = { nextState.toLoading() }),
        )
    }
```

## KSP options

```kt
ksp {
    arg("koma.strict.deadActionSeverity", "ERROR")
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
// file: LceState.Content.generated.kt
package example.lce

import me.tbsten.koma.strict.*
import me.tbsten.koma.strict.dsl.*

private data class ContentImpl(override val data: String) : LceState.Content

public operator fun LceState.Content.Companion.invoke(data: String): LceState.Content = ContentImpl(data)

public sealed interface ContentReloadReaction {
    public class Transition internal constructor(
        internal val next: LceState,
    ) : ContentReloadReaction
}

public class ContentReloadTransitions internal constructor(
    @Suppress("unused") private val state: LceState.Content,
) {
    public fun toLoading(): ContentReloadReaction = ContentReloadReaction.Transition(LceState.Loading())
}

@KomaStrictDsl
@koma.core.KomaStoreDsl
@OptIn(InternalKomaStrictApi::class)
public class ContentReloadScope internal constructor(
    public val state: LceState.Content,
    public val action: LceAction.Reload,
    onClearPendingActions: () -> Unit,
) : HandlerScope(onClearPendingActions) {
    public val nextState: ContentReloadTransitions = ContentReloadTransitions(state)
}

public class ContentHandlers internal constructor(
    internal val reload: suspend ContentReloadScope.() -> ContentReloadReaction,
    internal val configure: koma.core.StoreBuilder.StateHandlerConfig<LceState, LceAction, LceEvent, LceState.Content>.() -> Unit,
) : (ContentHandlersScope) -> ContentHandlers {
    /** Returns this bundle itself, so an already-built value is assignable to the scope-lambda-typed `states(...)` parameter (value-passing / scope-lambda interchange). */
    override fun invoke(p1: ContentHandlersScope): ContentHandlers = this
}

/** Bundles this state's handlers. [configure] appends raw koma DSL at the end of the generated `state<...> {}` block (per-state escape hatch). */
public fun LceState.Content.Companion.actions(
    reload: suspend ContentReloadScope.() -> ContentReloadReaction,
    configure: koma.core.StoreBuilder.StateHandlerConfig<LceState, LceAction, LceEvent, LceState.Content>.() -> Unit = {},
): ContentHandlers = ContentHandlers(reload, configure)

/**
 * Builder-form overload of `actions(...)` (see [ContentActionsBuilder]).
 *
 * Note: unlike the named-argument overload, exhaustiveness of the declared handlers is
 * checked **at build time** (fail-fast when the block finishes), not at compile time.
 * This overload exists only for states without enter / exit declarations (those handlers
 * have no builder member).
 */
public fun LceState.Content.Companion.actions(build: ContentActionsBuilder.() -> Unit): ContentHandlers = ContentActionsBuilder().apply(build).build()

/** Receiver of the scope-lambda form (`{ actions(...) }`) of the matching `states(...)` parameter. [actions] mirrors the companion `actions(...)` extension. */
@KomaStrictDsl
@koma.core.KomaStoreDsl
public class ContentHandlersScope internal constructor() {
    /** Bundles this state's handlers. [configure] appends raw koma DSL at the end of the generated `state<...> {}` block (per-state escape hatch). */
    public fun actions(
        reload: suspend ContentReloadScope.() -> ContentReloadReaction,
        configure: koma.core.StoreBuilder.StateHandlerConfig<LceState, LceAction, LceEvent, LceState.Content>.() -> Unit = {},
    ): ContentHandlers = ContentHandlers(reload, configure)

    /**
     * Builder-form overload of `actions(...)` (see [ContentActionsBuilder]).
     *
     * Note: unlike the named-argument overload, exhaustiveness of the declared handlers is
     * checked **at build time** (fail-fast when the block finishes), not at compile time.
     * This overload exists only for states without enter / exit declarations (those handlers
     * have no builder member).
     */
    public fun actions(build: ContentActionsBuilder.() -> Unit): ContentHandlers = ContentActionsBuilder().apply(build).build()
}

/**
 * Builder receiver of the `actions { ... }` overload building [ContentHandlers].
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
public class ContentActionsBuilder internal constructor() {
    private val reload = SetOnceSlot<suspend ContentReloadScope.() -> ContentReloadReaction>("LceState.Content", "reload")

    private val configure = SetOnceSlot<koma.core.StoreBuilder.StateHandlerConfig<LceState, LceAction, LceEvent, LceState.Content>.() -> Unit>("LceState.Content", "configure")

    /** Registers the handler of the same-named `actions(...)` parameter. Duplicate registration fails fast with [IllegalStateException]. */
    public fun reload(handler: suspend ContentReloadScope.() -> ContentReloadReaction) { reload.set(handler) }

    /** Appends raw koma DSL at the end of the generated `state<...> {}` block (the trailing `configure` parameter of `actions(...)`). Duplicate registration fails fast with [IllegalStateException]. */
    public fun configure(block: koma.core.StoreBuilder.StateHandlerConfig<LceState, LceAction, LceEvent, LceState.Content>.() -> Unit) { configure.set(block) }

    internal fun build(): ContentHandlers {
        val missing = listOfNotNull(
            "reload".takeIf { !reload.isSet },
        )
        if (missing.isNotEmpty()) throwMissingBuilderEntries("LceState.Content", missing)
        return ContentHandlers(reload.getOrNull()!!, configure.getOrNull() ?: {})
    }
}

// ----- next file -----

// file: LceState.Error.generated.kt
package example.lce

import me.tbsten.koma.strict.*
import me.tbsten.koma.strict.dsl.*

private data class ErrorImpl(override val message: String?) : LceState.Error

public operator fun LceState.Error.Companion.invoke(message: String?): LceState.Error = ErrorImpl(message)

public sealed interface ErrorRetryReaction {
    public class Transition internal constructor(
        internal val next: LceState,
    ) : ErrorRetryReaction
}

public class ErrorRetryTransitions internal constructor(
    @Suppress("unused") private val state: LceState.Error,
) {
    public fun toLoading(): ErrorRetryReaction = ErrorRetryReaction.Transition(LceState.Loading())
}

@KomaStrictDsl
@koma.core.KomaStoreDsl
@OptIn(InternalKomaStrictApi::class)
public class ErrorRetryScope internal constructor(
    public val state: LceState.Error,
    public val action: LceAction.Retry,
    onClearPendingActions: () -> Unit,
) : HandlerScope(onClearPendingActions) {
    public val nextState: ErrorRetryTransitions = ErrorRetryTransitions(state)
}

public class ErrorHandlers internal constructor(
    internal val retry: suspend ErrorRetryScope.() -> ErrorRetryReaction,
    internal val configure: koma.core.StoreBuilder.StateHandlerConfig<LceState, LceAction, LceEvent, LceState.Error>.() -> Unit,
) : (ErrorHandlersScope) -> ErrorHandlers {
    /** Returns this bundle itself, so an already-built value is assignable to the scope-lambda-typed `states(...)` parameter (value-passing / scope-lambda interchange). */
    override fun invoke(p1: ErrorHandlersScope): ErrorHandlers = this
}

/** Bundles this state's handlers. [configure] appends raw koma DSL at the end of the generated `state<...> {}` block (per-state escape hatch). */
public fun LceState.Error.Companion.actions(
    retry: suspend ErrorRetryScope.() -> ErrorRetryReaction,
    configure: koma.core.StoreBuilder.StateHandlerConfig<LceState, LceAction, LceEvent, LceState.Error>.() -> Unit = {},
): ErrorHandlers = ErrorHandlers(retry, configure)

/**
 * Builder-form overload of `actions(...)` (see [ErrorActionsBuilder]).
 *
 * Note: unlike the named-argument overload, exhaustiveness of the declared handlers is
 * checked **at build time** (fail-fast when the block finishes), not at compile time.
 * This overload exists only for states without enter / exit declarations (those handlers
 * have no builder member).
 */
public fun LceState.Error.Companion.actions(build: ErrorActionsBuilder.() -> Unit): ErrorHandlers = ErrorActionsBuilder().apply(build).build()

/** Receiver of the scope-lambda form (`{ actions(...) }`) of the matching `states(...)` parameter. [actions] mirrors the companion `actions(...)` extension. */
@KomaStrictDsl
@koma.core.KomaStoreDsl
public class ErrorHandlersScope internal constructor() {
    /** Bundles this state's handlers. [configure] appends raw koma DSL at the end of the generated `state<...> {}` block (per-state escape hatch). */
    public fun actions(
        retry: suspend ErrorRetryScope.() -> ErrorRetryReaction,
        configure: koma.core.StoreBuilder.StateHandlerConfig<LceState, LceAction, LceEvent, LceState.Error>.() -> Unit = {},
    ): ErrorHandlers = ErrorHandlers(retry, configure)

    /**
     * Builder-form overload of `actions(...)` (see [ErrorActionsBuilder]).
     *
     * Note: unlike the named-argument overload, exhaustiveness of the declared handlers is
     * checked **at build time** (fail-fast when the block finishes), not at compile time.
     * This overload exists only for states without enter / exit declarations (those handlers
     * have no builder member).
     */
    public fun actions(build: ErrorActionsBuilder.() -> Unit): ErrorHandlers = ErrorActionsBuilder().apply(build).build()
}

/**
 * Builder receiver of the `actions { ... }` overload building [ErrorHandlers].
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
public class ErrorActionsBuilder internal constructor() {
    private val retry = SetOnceSlot<suspend ErrorRetryScope.() -> ErrorRetryReaction>("LceState.Error", "retry")

    private val configure = SetOnceSlot<koma.core.StoreBuilder.StateHandlerConfig<LceState, LceAction, LceEvent, LceState.Error>.() -> Unit>("LceState.Error", "configure")

    /** Registers the handler of the same-named `actions(...)` parameter. Duplicate registration fails fast with [IllegalStateException]. */
    public fun retry(handler: suspend ErrorRetryScope.() -> ErrorRetryReaction) { retry.set(handler) }

    /** Appends raw koma DSL at the end of the generated `state<...> {}` block (the trailing `configure` parameter of `actions(...)`). Duplicate registration fails fast with [IllegalStateException]. */
    public fun configure(block: koma.core.StoreBuilder.StateHandlerConfig<LceState, LceAction, LceEvent, LceState.Error>.() -> Unit) { configure.set(block) }

    internal fun build(): ErrorHandlers {
        val missing = listOfNotNull(
            "retry".takeIf { !retry.isSet },
        )
        if (missing.isNotEmpty()) throwMissingBuilderEntries("LceState.Error", missing)
        return ErrorHandlers(retry.getOrNull()!!, configure.getOrNull() ?: {})
    }
}

// ----- next file -----

// file: LceState.Loading.generated.kt
package example.lce

import me.tbsten.koma.strict.*
import me.tbsten.koma.strict.dsl.*

private data object LoadingImpl : LceState.Loading

public operator fun LceState.Loading.Companion.invoke(): LceState.Loading = LoadingImpl

public sealed interface LoadingEnterReaction {
    public class Transition internal constructor(
        internal val next: LceState,
    ) : LoadingEnterReaction
}

public class LoadingEnterTransitions internal constructor(
    @Suppress("unused") private val state: LceState.Loading,
) {
    public fun toContent(
        data: String,
    ): LoadingEnterReaction = LoadingEnterReaction.Transition(LceState.Content(data))

    public fun toError(
        message: String?,
    ): LoadingEnterReaction = LoadingEnterReaction.Transition(LceState.Error(message))
}

@KomaStrictDsl
@koma.core.KomaStoreDsl
@OptIn(InternalKomaStrictApi::class)
public class LoadingEnterScope internal constructor(
    public val state: LceState.Loading,
    private val eventSink: suspend (LceEvent) -> Unit,
    onClearPendingActions: () -> Unit,
) : HandlerScope(onClearPendingActions) {
    public val nextState: LoadingEnterTransitions = LoadingEnterTransitions(state)

    public suspend fun emitLoadFailed(message: String?) {
        eventSink(LceEvent.LoadFailed(message))
    }
}

public class LoadingHandlers internal constructor(
    internal val enter: suspend LoadingEnterScope.() -> LoadingEnterReaction,
    internal val configure: koma.core.StoreBuilder.StateHandlerConfig<LceState, LceAction, LceEvent, LceState.Loading>.() -> Unit,
) : (LoadingHandlersScope) -> LoadingHandlers {
    /** Returns this bundle itself, so an already-built value is assignable to the scope-lambda-typed `states(...)` parameter (value-passing / scope-lambda interchange). */
    override fun invoke(p1: LoadingHandlersScope): LoadingHandlers = this
}

/** Bundles this state's handlers. [configure] appends raw koma DSL at the end of the generated `state<...> {}` block (per-state escape hatch). */
public fun LceState.Loading.Companion.actions(
    enter: suspend LoadingEnterScope.() -> LoadingEnterReaction,
    configure: koma.core.StoreBuilder.StateHandlerConfig<LceState, LceAction, LceEvent, LceState.Loading>.() -> Unit = {},
): LoadingHandlers = LoadingHandlers(enter, configure)

/** Receiver of the scope-lambda form (`{ actions(...) }`) of the matching `states(...)` parameter. [actions] mirrors the companion `actions(...)` extension. */
@KomaStrictDsl
@koma.core.KomaStoreDsl
public class LoadingHandlersScope internal constructor() {
    /** Bundles this state's handlers. [configure] appends raw koma DSL at the end of the generated `state<...> {}` block (per-state escape hatch). */
    public fun actions(
        enter: suspend LoadingEnterScope.() -> LoadingEnterReaction,
        configure: koma.core.StoreBuilder.StateHandlerConfig<LceState, LceAction, LceEvent, LceState.Loading>.() -> Unit = {},
    ): LoadingHandlers = LoadingHandlers(enter, configure)
}

// ----- next file -----

// file: LceState.storeSpec.generated.kt
package example.lce

import me.tbsten.koma.strict.*
import me.tbsten.koma.strict.dsl.*

@kotlin.jvm.JvmName("lceStateStates")
@Suppress("NAME_SHADOWING")
public fun koma.core.StoreBuilder<LceState, LceAction, LceEvent>.states(
    loading: LoadingHandlersScope.() -> LoadingHandlers,
    content: ContentHandlersScope.() -> ContentHandlers,
    error: ErrorHandlersScope.() -> ErrorHandlers,
    configure: LceStateStatesConfigureScope.() -> Unit = {},
) {
    val loading = LoadingHandlersScope().loading()
    val content = ContentHandlersScope().content()
    val error = ErrorHandlersScope().error()
    val configure = LceStateStatesConfigureScope().apply(configure)

    state<LceState.Loading> {
        enter {
            when (val r = loading.enter(LoadingEnterScope(state, ::event, ::clearPendingActions))) {
                is LoadingEnterReaction.Transition -> nextState { r.next }
            }
        }
        loading.configure(this)
        configure.loading?.invoke(this)
    }
    state<LceState.Content> {
        action<LceAction.Reload> {
            when (val r = content.reload(ContentReloadScope(state, action, ::clearPendingActions))) {
                is ContentReloadReaction.Transition -> nextState { r.next }
            }
        }
        content.configure(this)
        configure.content?.invoke(this)
    }
    state<LceState.Error> {
        action<LceAction.Retry> {
            when (val r = error.retry(ErrorRetryScope(state, action, ::clearPendingActions))) {
                is ErrorRetryReaction.Transition -> nextState { r.next }
            }
        }
        error.configure(this)
        configure.error?.invoke(this)
    }
}

/**
 * Builds a [koma.core.Store] for [LceState] without spelling the store type arguments.
 *
 * Sugar over the canonical koma entry point — `Store<LceState, LceAction, LceEvent>(initialState) { states(...) }`
 * builds the exact same store. [initialState] is narrowed to the declared
 * `@StoreSpec(initial = ...)` candidate [LceState.Loading] — compile-time enforced.
 * [configuration] appends raw koma DSL after the generated handlers (store-level escape hatch).
 */
public fun createLceStore(
    initialState: LceState.Loading,
    loading: LoadingHandlersScope.() -> LoadingHandlers,
    content: ContentHandlersScope.() -> ContentHandlers,
    error: ErrorHandlersScope.() -> ErrorHandlers,
    context: kotlin.coroutines.CoroutineContext? = null,
    configuration: koma.core.StoreBuilder<LceState, LceAction, LceEvent>.() -> Unit = {},
): koma.core.Store<LceState, LceAction, LceEvent> =
    koma.core.Store<LceState, LceAction, LceEvent>(initialState = initialState, context = context) {
        states(
            loading = loading,
            content = content,
            error = error,
        )
        configuration()
    }

/**
 * Builds a [koma.core.Store] for [LceState] without spelling the store type arguments.
 *
 * Sugar over the canonical koma entry point — `Store<LceState, LceAction, LceEvent>(initialState) { states(...) }`
 * builds the exact same store. [initialState] accepts any [LceState] — for
 * restoring a persisted state or starting mid-flow in tests, where [createLceStore]'s
 * compile-time-narrowed initial state does not apply. [configuration] appends raw koma
 * DSL after the generated handlers (store-level escape hatch).
 */
public fun restoreLceStore(
    initialState: LceState,
    loading: LoadingHandlersScope.() -> LoadingHandlers,
    content: ContentHandlersScope.() -> ContentHandlers,
    error: ErrorHandlersScope.() -> ErrorHandlers,
    context: kotlin.coroutines.CoroutineContext? = null,
    configuration: koma.core.StoreBuilder<LceState, LceAction, LceEvent>.() -> Unit = {},
): koma.core.Store<LceState, LceAction, LceEvent> =
    koma.core.Store<LceState, LceAction, LceEvent>(initialState = initialState, context = context) {
        states(
            loading = loading,
            content = content,
            error = error,
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
public class LceStateStatesConfigureScope internal constructor() {
    internal var loading: (koma.core.StoreBuilder.StateHandlerConfig<LceState, LceAction, LceEvent, LceState.Loading>.() -> Unit)? = null

    internal var content: (koma.core.StoreBuilder.StateHandlerConfig<LceState, LceAction, LceEvent, LceState.Content>.() -> Unit)? = null

    internal var error: (koma.core.StoreBuilder.StateHandlerConfig<LceState, LceAction, LceEvent, LceState.Error>.() -> Unit)? = null

    /** Appends raw koma DSL at the end of the generated `state<LceState.Loading> {}` block. Fails fast if called twice. */
    public fun loading(block: koma.core.StoreBuilder.StateHandlerConfig<LceState, LceAction, LceEvent, LceState.Loading>.() -> Unit) {
        if (this.loading != null) throwDuplicateBuilderEntry("LceState", "loading")
        this.loading = block
    }

    /** Appends raw koma DSL at the end of the generated `state<LceState.Content> {}` block. Fails fast if called twice. */
    public fun content(block: koma.core.StoreBuilder.StateHandlerConfig<LceState, LceAction, LceEvent, LceState.Content>.() -> Unit) {
        if (this.content != null) throwDuplicateBuilderEntry("LceState", "content")
        this.content = block
    }

    /** Appends raw koma DSL at the end of the generated `state<LceState.Error> {}` block. Fails fast if called twice. */
    public fun error(block: koma.core.StoreBuilder.StateHandlerConfig<LceState, LceAction, LceEvent, LceState.Error>.() -> Unit) {
        if (this.error != null) throwDuplicateBuilderEntry("LceState", "error")
        this.error = block
    }
}
```
