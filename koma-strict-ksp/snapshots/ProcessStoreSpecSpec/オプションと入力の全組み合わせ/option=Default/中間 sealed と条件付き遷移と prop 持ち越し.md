## Input:FeedState.kt

```kt
package example.feed

import koma.core.Action
import koma.core.Event
import koma.core.State
import me.tbsten.koma.strict.OnAction
import me.tbsten.koma.strict.OnEnter
import me.tbsten.koma.strict.Stay
import me.tbsten.koma.strict.StoreSpec

@StoreSpec(initial = [FeedState.Loading::class])
sealed interface FeedState : State {
    companion object

    @OnEnter(nextState = [Stable.Idle::class, Error::class], emit = [FeedEvent.LoadFailed::class])
    interface Loading : FeedState { companion object }

    sealed interface Stable : FeedState {          // 共有 prop は 1 回だけ宣言
        val items: List<String>
        companion object

        @OnAction<FeedAction.Refresh>(nextState = [Refreshing::class])
        @OnAction<FeedAction.LoadMore>(nextState = [Stay::class, LoadingMore::class]) // 条件付き遷移
        interface Idle : Stable { val hasMore: Boolean; companion object }

        @OnEnter(nextState = [Idle::class], emit = [FeedEvent.RefreshFailed::class])
        interface Refreshing : Stable { companion object }

        @OnEnter(nextState = [Idle::class], emit = [FeedEvent.LoadMoreFailed::class])
        interface LoadingMore : Stable { companion object }
    }

    @OnAction<FeedAction.Retry>(nextState = [Loading::class])
    interface Error : FeedState { val message: String?; companion object }
}

sealed interface FeedAction : Action {
    data object Refresh : FeedAction
    data object LoadMore : FeedAction
    data object Retry : FeedAction
}

sealed interface FeedEvent : Event {
    data class LoadFailed(val message: String?) : FeedEvent
    data class RefreshFailed(val message: String?) : FeedEvent
    data class LoadMoreFailed(val message: String?) : FeedEvent
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
// file: FeedState.Error.generated.kt
package example.feed

import me.tbsten.koma.strict.*
import me.tbsten.koma.strict.dsl.*

private data class ErrorImpl(override val message: String?) : FeedState.Error

public operator fun FeedState.Error.Companion.invoke(message: String?): FeedState.Error = ErrorImpl(message)

public sealed interface ErrorRetryReaction {
    public class Transition internal constructor(
        internal val next: FeedState,
    ) : ErrorRetryReaction
}

public class ErrorRetryTransitions internal constructor(
    @Suppress("unused") private val state: FeedState.Error,
) {
    public fun toLoading(): ErrorRetryReaction = ErrorRetryReaction.Transition(FeedState.Loading())
}

@KomaStrictDsl
@koma.core.KomaStoreDsl
@OptIn(InternalKomaStrictApi::class)
public class ErrorRetryScope internal constructor(
    public val state: FeedState.Error,
    public val action: FeedAction.Retry,
    onClearPendingActions: () -> Unit,
) : HandlerScope(onClearPendingActions) {
    public val nextState: ErrorRetryTransitions = ErrorRetryTransitions(state)
}

public class ErrorHandlers internal constructor(
    internal val retry: suspend ErrorRetryScope.() -> ErrorRetryReaction,
    internal val configure: koma.core.StoreBuilder.StateHandlerConfig<FeedState, FeedAction, FeedEvent, FeedState.Error>.() -> Unit,
) : (ErrorHandlersScope) -> ErrorHandlers {
    /** Returns this bundle itself, so an already-built value is assignable to the scope-lambda-typed `states(...)` parameter (value-passing / scope-lambda interchange). */
    override fun invoke(p1: ErrorHandlersScope): ErrorHandlers = this
}

/** Bundles this state's handlers. [configure] appends raw koma DSL at the end of the generated `state<...> {}` block (per-state escape hatch). */
public fun FeedState.Error.Companion.actions(
    retry: suspend ErrorRetryScope.() -> ErrorRetryReaction,
    configure: koma.core.StoreBuilder.StateHandlerConfig<FeedState, FeedAction, FeedEvent, FeedState.Error>.() -> Unit = {},
): ErrorHandlers = ErrorHandlers(retry, configure)

/**
 * Builder-form overload of `actions(...)` (see [ErrorActionsBuilder]).
 *
 * Note: unlike the named-argument overload, exhaustiveness of the declared handlers is
 * checked **at build time** (fail-fast when the block finishes), not at compile time.
 * This overload exists only for states without enter / exit declarations (those handlers
 * have no builder member).
 */
public fun FeedState.Error.Companion.actions(build: ErrorActionsBuilder.() -> Unit): ErrorHandlers = ErrorActionsBuilder().apply(build).build()

/** Receiver of the scope-lambda form (`{ actions(...) }`) of the matching `states(...)` parameter. [actions] mirrors the companion `actions(...)` extension. */
@KomaStrictDsl
@koma.core.KomaStoreDsl
public class ErrorHandlersScope internal constructor() {
    /** Bundles this state's handlers. [configure] appends raw koma DSL at the end of the generated `state<...> {}` block (per-state escape hatch). */
    public fun actions(
        retry: suspend ErrorRetryScope.() -> ErrorRetryReaction,
        configure: koma.core.StoreBuilder.StateHandlerConfig<FeedState, FeedAction, FeedEvent, FeedState.Error>.() -> Unit = {},
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
    private val retry = SetOnceSlot<suspend ErrorRetryScope.() -> ErrorRetryReaction>("FeedState.Error", "retry")

    private val configure = SetOnceSlot<koma.core.StoreBuilder.StateHandlerConfig<FeedState, FeedAction, FeedEvent, FeedState.Error>.() -> Unit>("FeedState.Error", "configure")

    /** Registers the handler of the same-named `actions(...)` parameter. Duplicate registration fails fast with [IllegalStateException]. */
    public fun retry(handler: suspend ErrorRetryScope.() -> ErrorRetryReaction) { retry.set(handler) }

    /** Appends raw koma DSL at the end of the generated `state<...> {}` block (the trailing `configure` parameter of `actions(...)`). Duplicate registration fails fast with [IllegalStateException]. */
    public fun configure(block: koma.core.StoreBuilder.StateHandlerConfig<FeedState, FeedAction, FeedEvent, FeedState.Error>.() -> Unit) { configure.set(block) }

    internal fun build(): ErrorHandlers {
        val missing = listOfNotNull(
            "retry".takeIf { !retry.isSet },
        )
        if (missing.isNotEmpty()) throwMissingBuilderEntries("FeedState.Error", missing)
        return ErrorHandlers(retry.getOrNull()!!, configure.getOrNull() ?: {})
    }
}

// ----- next file -----

// file: FeedState.Loading.generated.kt
package example.feed

import me.tbsten.koma.strict.*
import me.tbsten.koma.strict.dsl.*

private data object LoadingImpl : FeedState.Loading

public operator fun FeedState.Loading.Companion.invoke(): FeedState.Loading = LoadingImpl

public sealed interface LoadingEnterReaction {
    public class Transition internal constructor(
        internal val next: FeedState,
    ) : LoadingEnterReaction
}

public class LoadingEnterTransitions internal constructor(
    @Suppress("unused") private val state: FeedState.Loading,
) {
    public fun toStableIdle(
        items: List<String>,
        hasMore: Boolean,
    ): LoadingEnterReaction = LoadingEnterReaction.Transition(FeedState.Stable.Idle(items, hasMore))

    public fun toError(
        message: String?,
    ): LoadingEnterReaction = LoadingEnterReaction.Transition(FeedState.Error(message))
}

@KomaStrictDsl
@koma.core.KomaStoreDsl
@OptIn(InternalKomaStrictApi::class)
public class LoadingEnterScope internal constructor(
    public val state: FeedState.Loading,
    private val eventSink: suspend (FeedEvent) -> Unit,
    onClearPendingActions: () -> Unit,
) : HandlerScope(onClearPendingActions) {
    public val nextState: LoadingEnterTransitions = LoadingEnterTransitions(state)

    public suspend fun emitLoadFailed(message: String?) {
        eventSink(FeedEvent.LoadFailed(message))
    }
}

public class LoadingHandlers internal constructor(
    internal val enter: suspend LoadingEnterScope.() -> LoadingEnterReaction,
    internal val configure: koma.core.StoreBuilder.StateHandlerConfig<FeedState, FeedAction, FeedEvent, FeedState.Loading>.() -> Unit,
) : (LoadingHandlersScope) -> LoadingHandlers {
    /** Returns this bundle itself, so an already-built value is assignable to the scope-lambda-typed `states(...)` parameter (value-passing / scope-lambda interchange). */
    override fun invoke(p1: LoadingHandlersScope): LoadingHandlers = this
}

/** Bundles this state's handlers. [configure] appends raw koma DSL at the end of the generated `state<...> {}` block (per-state escape hatch). */
public fun FeedState.Loading.Companion.actions(
    enter: suspend LoadingEnterScope.() -> LoadingEnterReaction,
    configure: koma.core.StoreBuilder.StateHandlerConfig<FeedState, FeedAction, FeedEvent, FeedState.Loading>.() -> Unit = {},
): LoadingHandlers = LoadingHandlers(enter, configure)

/** Receiver of the scope-lambda form (`{ actions(...) }`) of the matching `states(...)` parameter. [actions] mirrors the companion `actions(...)` extension. */
@KomaStrictDsl
@koma.core.KomaStoreDsl
public class LoadingHandlersScope internal constructor() {
    /** Bundles this state's handlers. [configure] appends raw koma DSL at the end of the generated `state<...> {}` block (per-state escape hatch). */
    public fun actions(
        enter: suspend LoadingEnterScope.() -> LoadingEnterReaction,
        configure: koma.core.StoreBuilder.StateHandlerConfig<FeedState, FeedAction, FeedEvent, FeedState.Loading>.() -> Unit = {},
    ): LoadingHandlers = LoadingHandlers(enter, configure)
}

// ----- next file -----

// file: FeedState.Stable.Idle.generated.kt
package example.feed

import me.tbsten.koma.strict.*
import me.tbsten.koma.strict.dsl.*

private data class StableIdleImpl(
    override val items: List<String>,
    override val hasMore: Boolean,
) : FeedState.Stable.Idle

public operator fun FeedState.Stable.Idle.Companion.invoke(
    items: List<String>,
    hasMore: Boolean,
): FeedState.Stable.Idle = StableIdleImpl(items, hasMore)

public sealed interface StableIdleRefreshReaction {
    public class Transition internal constructor(
        internal val next: FeedState,
    ) : StableIdleRefreshReaction
}

public class StableIdleRefreshTransitions internal constructor(
    private val state: FeedState.Stable.Idle,
) {
    public fun toRefreshing(
        items: List<String> = state.items,
    ): StableIdleRefreshReaction = StableIdleRefreshReaction.Transition(FeedState.Stable.Refreshing(items))
}

@KomaStrictDsl
@koma.core.KomaStoreDsl
@OptIn(InternalKomaStrictApi::class)
public class StableIdleRefreshScope internal constructor(
    public val state: FeedState.Stable.Idle,
    public val action: FeedAction.Refresh,
    onClearPendingActions: () -> Unit,
) : HandlerScope(onClearPendingActions) {
    public val nextState: StableIdleRefreshTransitions = StableIdleRefreshTransitions(state)
}

public sealed interface StableIdleLoadMoreReaction {
    public class Transition internal constructor(
        internal val next: FeedState,
    ) : StableIdleLoadMoreReaction

    public data object Stay : StableIdleLoadMoreReaction
}

public class StableIdleLoadMoreTransitions internal constructor(
    private val state: FeedState.Stable.Idle,
) {
    public fun toLoadingMore(
        items: List<String> = state.items,
    ): StableIdleLoadMoreReaction = StableIdleLoadMoreReaction.Transition(FeedState.Stable.LoadingMore(items))
}

@KomaStrictDsl
@koma.core.KomaStoreDsl
@OptIn(InternalKomaStrictApi::class)
public class StableIdleLoadMoreScope internal constructor(
    public val state: FeedState.Stable.Idle,
    public val action: FeedAction.LoadMore,
    onClearPendingActions: () -> Unit,
) : HandlerScope(onClearPendingActions) {
    public val nextState: StableIdleLoadMoreTransitions = StableIdleLoadMoreTransitions(state)

    /** Chooses to stay in the current state. This simply does not call koma's nextState: no instance is created and pending actions are not discarded. */
    public fun stayState(): StableIdleLoadMoreReaction = StableIdleLoadMoreReaction.Stay
}

public class StableIdleHandlers internal constructor(
    internal val refresh: suspend StableIdleRefreshScope.() -> StableIdleRefreshReaction,
    internal val loadMore: suspend StableIdleLoadMoreScope.() -> StableIdleLoadMoreReaction,
    internal val configure: koma.core.StoreBuilder.StateHandlerConfig<FeedState, FeedAction, FeedEvent, FeedState.Stable.Idle>.() -> Unit,
) : (StableIdleHandlersScope) -> StableIdleHandlers {
    /** Returns this bundle itself, so an already-built value is assignable to the scope-lambda-typed `states(...)` parameter (value-passing / scope-lambda interchange). */
    override fun invoke(p1: StableIdleHandlersScope): StableIdleHandlers = this
}

/** Bundles this state's handlers. [configure] appends raw koma DSL at the end of the generated `state<...> {}` block (per-state escape hatch). */
public fun FeedState.Stable.Idle.Companion.actions(
    refresh: suspend StableIdleRefreshScope.() -> StableIdleRefreshReaction,
    loadMore: suspend StableIdleLoadMoreScope.() -> StableIdleLoadMoreReaction,
    configure: koma.core.StoreBuilder.StateHandlerConfig<FeedState, FeedAction, FeedEvent, FeedState.Stable.Idle>.() -> Unit = {},
): StableIdleHandlers = StableIdleHandlers(refresh, loadMore, configure)

/**
 * Builder-form overload of `actions(...)` (see [StableIdleActionsBuilder]).
 *
 * Note: unlike the named-argument overload, exhaustiveness of the declared handlers is
 * checked **at build time** (fail-fast when the block finishes), not at compile time.
 * This overload exists only for states without enter / exit declarations (those handlers
 * have no builder member).
 */
public fun FeedState.Stable.Idle.Companion.actions(build: StableIdleActionsBuilder.() -> Unit): StableIdleHandlers = StableIdleActionsBuilder().apply(build).build()

/** Receiver of the scope-lambda form (`{ actions(...) }`) of the matching `states(...)` parameter. [actions] mirrors the companion `actions(...)` extension. */
@KomaStrictDsl
@koma.core.KomaStoreDsl
public class StableIdleHandlersScope internal constructor() {
    /** Bundles this state's handlers. [configure] appends raw koma DSL at the end of the generated `state<...> {}` block (per-state escape hatch). */
    public fun actions(
        refresh: suspend StableIdleRefreshScope.() -> StableIdleRefreshReaction,
        loadMore: suspend StableIdleLoadMoreScope.() -> StableIdleLoadMoreReaction,
        configure: koma.core.StoreBuilder.StateHandlerConfig<FeedState, FeedAction, FeedEvent, FeedState.Stable.Idle>.() -> Unit = {},
    ): StableIdleHandlers = StableIdleHandlers(refresh, loadMore, configure)

    /**
     * Builder-form overload of `actions(...)` (see [StableIdleActionsBuilder]).
     *
     * Note: unlike the named-argument overload, exhaustiveness of the declared handlers is
     * checked **at build time** (fail-fast when the block finishes), not at compile time.
     * This overload exists only for states without enter / exit declarations (those handlers
     * have no builder member).
     */
    public fun actions(build: StableIdleActionsBuilder.() -> Unit): StableIdleHandlers = StableIdleActionsBuilder().apply(build).build()
}

/**
 * Builder receiver of the `actions { ... }` overload building [StableIdleHandlers].
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
public class StableIdleActionsBuilder internal constructor() {
    private val refresh = SetOnceSlot<suspend StableIdleRefreshScope.() -> StableIdleRefreshReaction>("FeedState.Stable.Idle", "refresh")

    private val loadMore = SetOnceSlot<suspend StableIdleLoadMoreScope.() -> StableIdleLoadMoreReaction>("FeedState.Stable.Idle", "loadMore")

    private val configure = SetOnceSlot<koma.core.StoreBuilder.StateHandlerConfig<FeedState, FeedAction, FeedEvent, FeedState.Stable.Idle>.() -> Unit>("FeedState.Stable.Idle", "configure")

    /** Registers the handler of the same-named `actions(...)` parameter. Duplicate registration fails fast with [IllegalStateException]. */
    public fun refresh(handler: suspend StableIdleRefreshScope.() -> StableIdleRefreshReaction) { refresh.set(handler) }

    /** Registers the handler of the same-named `actions(...)` parameter. Duplicate registration fails fast with [IllegalStateException]. */
    public fun loadMore(handler: suspend StableIdleLoadMoreScope.() -> StableIdleLoadMoreReaction) { loadMore.set(handler) }

    /** Appends raw koma DSL at the end of the generated `state<...> {}` block (the trailing `configure` parameter of `actions(...)`). Duplicate registration fails fast with [IllegalStateException]. */
    public fun configure(block: koma.core.StoreBuilder.StateHandlerConfig<FeedState, FeedAction, FeedEvent, FeedState.Stable.Idle>.() -> Unit) { configure.set(block) }

    internal fun build(): StableIdleHandlers {
        val missing = listOfNotNull(
            "refresh".takeIf { !refresh.isSet },
            "loadMore".takeIf { !loadMore.isSet },
        )
        if (missing.isNotEmpty()) throwMissingBuilderEntries("FeedState.Stable.Idle", missing)
        return StableIdleHandlers(refresh.getOrNull()!!, loadMore.getOrNull()!!, configure.getOrNull() ?: {})
    }
}

// ----- next file -----

// file: FeedState.Stable.LoadingMore.generated.kt
package example.feed

import me.tbsten.koma.strict.*
import me.tbsten.koma.strict.dsl.*

private data class StableLoadingMoreImpl(override val items: List<String>) : FeedState.Stable.LoadingMore

public operator fun FeedState.Stable.LoadingMore.Companion.invoke(items: List<String>): FeedState.Stable.LoadingMore = StableLoadingMoreImpl(items)

public sealed interface StableLoadingMoreEnterReaction {
    public class Transition internal constructor(
        internal val next: FeedState,
    ) : StableLoadingMoreEnterReaction
}

public class StableLoadingMoreEnterTransitions internal constructor(
    private val state: FeedState.Stable.LoadingMore,
) {
    public fun toIdle(
        items: List<String> = state.items,
        hasMore: Boolean,
    ): StableLoadingMoreEnterReaction = StableLoadingMoreEnterReaction.Transition(FeedState.Stable.Idle(items, hasMore))
}

@KomaStrictDsl
@koma.core.KomaStoreDsl
@OptIn(InternalKomaStrictApi::class)
public class StableLoadingMoreEnterScope internal constructor(
    public val state: FeedState.Stable.LoadingMore,
    private val eventSink: suspend (FeedEvent) -> Unit,
    onClearPendingActions: () -> Unit,
) : HandlerScope(onClearPendingActions) {
    public val nextState: StableLoadingMoreEnterTransitions = StableLoadingMoreEnterTransitions(state)

    public suspend fun emitLoadMoreFailed(message: String?) {
        eventSink(FeedEvent.LoadMoreFailed(message))
    }
}

public class StableLoadingMoreHandlers internal constructor(
    internal val enter: suspend StableLoadingMoreEnterScope.() -> StableLoadingMoreEnterReaction,
    internal val configure: koma.core.StoreBuilder.StateHandlerConfig<FeedState, FeedAction, FeedEvent, FeedState.Stable.LoadingMore>.() -> Unit,
) : (StableLoadingMoreHandlersScope) -> StableLoadingMoreHandlers {
    /** Returns this bundle itself, so an already-built value is assignable to the scope-lambda-typed `states(...)` parameter (value-passing / scope-lambda interchange). */
    override fun invoke(p1: StableLoadingMoreHandlersScope): StableLoadingMoreHandlers = this
}

/** Bundles this state's handlers. [configure] appends raw koma DSL at the end of the generated `state<...> {}` block (per-state escape hatch). */
public fun FeedState.Stable.LoadingMore.Companion.actions(
    enter: suspend StableLoadingMoreEnterScope.() -> StableLoadingMoreEnterReaction,
    configure: koma.core.StoreBuilder.StateHandlerConfig<FeedState, FeedAction, FeedEvent, FeedState.Stable.LoadingMore>.() -> Unit = {},
): StableLoadingMoreHandlers = StableLoadingMoreHandlers(enter, configure)

/** Receiver of the scope-lambda form (`{ actions(...) }`) of the matching `states(...)` parameter. [actions] mirrors the companion `actions(...)` extension. */
@KomaStrictDsl
@koma.core.KomaStoreDsl
public class StableLoadingMoreHandlersScope internal constructor() {
    /** Bundles this state's handlers. [configure] appends raw koma DSL at the end of the generated `state<...> {}` block (per-state escape hatch). */
    public fun actions(
        enter: suspend StableLoadingMoreEnterScope.() -> StableLoadingMoreEnterReaction,
        configure: koma.core.StoreBuilder.StateHandlerConfig<FeedState, FeedAction, FeedEvent, FeedState.Stable.LoadingMore>.() -> Unit = {},
    ): StableLoadingMoreHandlers = StableLoadingMoreHandlers(enter, configure)
}

// ----- next file -----

// file: FeedState.Stable.Refreshing.generated.kt
package example.feed

import me.tbsten.koma.strict.*
import me.tbsten.koma.strict.dsl.*

private data class StableRefreshingImpl(override val items: List<String>) : FeedState.Stable.Refreshing

public operator fun FeedState.Stable.Refreshing.Companion.invoke(items: List<String>): FeedState.Stable.Refreshing = StableRefreshingImpl(items)

public sealed interface StableRefreshingEnterReaction {
    public class Transition internal constructor(
        internal val next: FeedState,
    ) : StableRefreshingEnterReaction
}

public class StableRefreshingEnterTransitions internal constructor(
    private val state: FeedState.Stable.Refreshing,
) {
    public fun toIdle(
        items: List<String> = state.items,
        hasMore: Boolean,
    ): StableRefreshingEnterReaction = StableRefreshingEnterReaction.Transition(FeedState.Stable.Idle(items, hasMore))
}

@KomaStrictDsl
@koma.core.KomaStoreDsl
@OptIn(InternalKomaStrictApi::class)
public class StableRefreshingEnterScope internal constructor(
    public val state: FeedState.Stable.Refreshing,
    private val eventSink: suspend (FeedEvent) -> Unit,
    onClearPendingActions: () -> Unit,
) : HandlerScope(onClearPendingActions) {
    public val nextState: StableRefreshingEnterTransitions = StableRefreshingEnterTransitions(state)

    public suspend fun emitRefreshFailed(message: String?) {
        eventSink(FeedEvent.RefreshFailed(message))
    }
}

public class StableRefreshingHandlers internal constructor(
    internal val enter: suspend StableRefreshingEnterScope.() -> StableRefreshingEnterReaction,
    internal val configure: koma.core.StoreBuilder.StateHandlerConfig<FeedState, FeedAction, FeedEvent, FeedState.Stable.Refreshing>.() -> Unit,
) : (StableRefreshingHandlersScope) -> StableRefreshingHandlers {
    /** Returns this bundle itself, so an already-built value is assignable to the scope-lambda-typed `states(...)` parameter (value-passing / scope-lambda interchange). */
    override fun invoke(p1: StableRefreshingHandlersScope): StableRefreshingHandlers = this
}

/** Bundles this state's handlers. [configure] appends raw koma DSL at the end of the generated `state<...> {}` block (per-state escape hatch). */
public fun FeedState.Stable.Refreshing.Companion.actions(
    enter: suspend StableRefreshingEnterScope.() -> StableRefreshingEnterReaction,
    configure: koma.core.StoreBuilder.StateHandlerConfig<FeedState, FeedAction, FeedEvent, FeedState.Stable.Refreshing>.() -> Unit = {},
): StableRefreshingHandlers = StableRefreshingHandlers(enter, configure)

/** Receiver of the scope-lambda form (`{ actions(...) }`) of the matching `states(...)` parameter. [actions] mirrors the companion `actions(...)` extension. */
@KomaStrictDsl
@koma.core.KomaStoreDsl
public class StableRefreshingHandlersScope internal constructor() {
    /** Bundles this state's handlers. [configure] appends raw koma DSL at the end of the generated `state<...> {}` block (per-state escape hatch). */
    public fun actions(
        enter: suspend StableRefreshingEnterScope.() -> StableRefreshingEnterReaction,
        configure: koma.core.StoreBuilder.StateHandlerConfig<FeedState, FeedAction, FeedEvent, FeedState.Stable.Refreshing>.() -> Unit = {},
    ): StableRefreshingHandlers = StableRefreshingHandlers(enter, configure)
}

// ----- next file -----

// file: FeedState.Stable.generated.kt
package example.feed

import me.tbsten.koma.strict.*
import me.tbsten.koma.strict.dsl.*

public class StableGroupHandlers internal constructor(
    internal val idle: StableIdleHandlers,
    internal val refreshing: StableRefreshingHandlers,
    internal val loadingMore: StableLoadingMoreHandlers,
    internal val configure: StableStatesConfigureScope,
) : (StableGroupHandlersScope) -> StableGroupHandlers {
    /** Returns this bundle itself, so an already-built value is assignable to the scope-lambda-typed `states(...)` parameter (value-passing / scope-lambda interchange). */
    override fun invoke(p1: StableGroupHandlersScope): StableGroupHandlers = this
}

public fun FeedState.Stable.Companion.states(
    idle: StableIdleHandlersScope.() -> StableIdleHandlers,
    refreshing: StableRefreshingHandlersScope.() -> StableRefreshingHandlers,
    loadingMore: StableLoadingMoreHandlersScope.() -> StableLoadingMoreHandlers,
    configure: StableStatesConfigureScope.() -> Unit = {},
): StableGroupHandlers =
    StableGroupHandlers(
        StableIdleHandlersScope().idle(),
        StableRefreshingHandlersScope().refreshing(),
        StableLoadingMoreHandlersScope().loadingMore(),
        StableStatesConfigureScope().apply(configure),
    )

/**
 * Builder-form overload of `states(...)` (see [StableGroupBuilder]).
 *
 * Note: unlike the named-argument overload, exhaustiveness of the child states (and the
 * default block) is checked **at build time** (fail-fast when the block finishes), not at
 * compile time.
 */
public fun FeedState.Stable.Companion.states(build: StableGroupBuilder.() -> Unit): StableGroupHandlers = StableGroupBuilder().apply(build).build()

/** Receiver of the scope-lambda form (`{ states(...) }`) of the matching `states(...)` parameter. [states] mirrors the companion `states(...)` extension. */
@KomaStrictDsl
@koma.core.KomaStoreDsl
public class StableGroupHandlersScope internal constructor() {
    public fun states(
        idle: StableIdleHandlersScope.() -> StableIdleHandlers,
        refreshing: StableRefreshingHandlersScope.() -> StableRefreshingHandlers,
        loadingMore: StableLoadingMoreHandlersScope.() -> StableLoadingMoreHandlers,
        configure: StableStatesConfigureScope.() -> Unit = {},
    ): StableGroupHandlers =
        StableGroupHandlers(
            StableIdleHandlersScope().idle(),
            StableRefreshingHandlersScope().refreshing(),
            StableLoadingMoreHandlersScope().loadingMore(),
            StableStatesConfigureScope().apply(configure),
        )

    /**
     * Builder-form overload of `states(...)` (see [StableGroupBuilder]).
     *
     * Note: unlike the named-argument overload, exhaustiveness of the child states (and the
     * default block) is checked **at build time** (fail-fast when the block finishes), not at
     * compile time.
     */
    public fun states(build: StableGroupBuilder.() -> Unit): StableGroupHandlers = StableGroupBuilder().apply(build).build()
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
public class StableStatesConfigureScope internal constructor() {
    internal var idle: (koma.core.StoreBuilder.StateHandlerConfig<FeedState, FeedAction, FeedEvent, FeedState.Stable.Idle>.() -> Unit)? = null

    internal var refreshing: (koma.core.StoreBuilder.StateHandlerConfig<FeedState, FeedAction, FeedEvent, FeedState.Stable.Refreshing>.() -> Unit)? = null

    internal var loadingMore: (koma.core.StoreBuilder.StateHandlerConfig<FeedState, FeedAction, FeedEvent, FeedState.Stable.LoadingMore>.() -> Unit)? = null

    /** Appends raw koma DSL at the end of the generated `state<FeedState.Stable.Idle> {}` block. Fails fast if called twice. */
    public fun idle(block: koma.core.StoreBuilder.StateHandlerConfig<FeedState, FeedAction, FeedEvent, FeedState.Stable.Idle>.() -> Unit) {
        if (this.idle != null) throwDuplicateBuilderEntry("FeedState.Stable", "idle")
        this.idle = block
    }

    /** Appends raw koma DSL at the end of the generated `state<FeedState.Stable.Refreshing> {}` block. Fails fast if called twice. */
    public fun refreshing(block: koma.core.StoreBuilder.StateHandlerConfig<FeedState, FeedAction, FeedEvent, FeedState.Stable.Refreshing>.() -> Unit) {
        if (this.refreshing != null) throwDuplicateBuilderEntry("FeedState.Stable", "refreshing")
        this.refreshing = block
    }

    /** Appends raw koma DSL at the end of the generated `state<FeedState.Stable.LoadingMore> {}` block. Fails fast if called twice. */
    public fun loadingMore(block: koma.core.StoreBuilder.StateHandlerConfig<FeedState, FeedAction, FeedEvent, FeedState.Stable.LoadingMore>.() -> Unit) {
        if (this.loadingMore != null) throwDuplicateBuilderEntry("FeedState.Stable", "loadingMore")
        this.loadingMore = block
    }
}

/**
 * Builder receiver of the `states { ... }` overload building [StableGroupHandlers].
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
public class StableGroupBuilder internal constructor() {
    private val idle = SetOnceSlot<StableIdleHandlers>("FeedState.Stable", "idle")

    private val refreshing = SetOnceSlot<StableRefreshingHandlers>("FeedState.Stable", "refreshing")

    private val loadingMore = SetOnceSlot<StableLoadingMoreHandlers>("FeedState.Stable", "loadingMore")

    /** Registers this child state's handlers by an already-built value. Duplicate registration fails fast with [IllegalStateException]. */
    public fun idle(handlers: StableIdleHandlers) { idle.set(handlers) }

    /** Registers this child state's handlers with a nested builder block (fails fast if already registered). */
    public fun idle(build: StableIdleActionsBuilder.() -> Unit) {
        idle(StableIdleActionsBuilder().apply(build).build())
    }

    /** Registers this child state's handlers by an already-built value. Duplicate registration fails fast with [IllegalStateException]. */
    public fun refreshing(handlers: StableRefreshingHandlers) { refreshing.set(handlers) }

    /** Registers this child state's handlers by an already-built value. Duplicate registration fails fast with [IllegalStateException]. */
    public fun loadingMore(handlers: StableLoadingMoreHandlers) { loadingMore.set(handlers) }

    internal fun build(): StableGroupHandlers {
        val missing = listOfNotNull(
            "idle".takeIf { !idle.isSet },
            "refreshing".takeIf { !refreshing.isSet },
            "loadingMore".takeIf { !loadingMore.isSet },
        )
        if (missing.isNotEmpty()) throwMissingBuilderEntries("FeedState.Stable", missing)
        return StableGroupHandlers(idle.getOrNull()!!, refreshing.getOrNull()!!, loadingMore.getOrNull()!!, StableStatesConfigureScope())
    }
}

// ----- next file -----

// file: FeedState.storeSpec.generated.kt
package example.feed

import me.tbsten.koma.strict.*
import me.tbsten.koma.strict.dsl.*

@kotlin.jvm.JvmName("feedStateStates")
@Suppress("NAME_SHADOWING", "UNCHECKED_CAST")
public fun koma.core.StoreBuilder<FeedState, FeedAction, FeedEvent>.states(
    loading: LoadingHandlersScope.() -> LoadingHandlers,
    stable: StableGroupHandlersScope.() -> StableGroupHandlers,
    error: ErrorHandlersScope.() -> ErrorHandlers,
    configure: FeedStateStatesConfigureScope.() -> Unit = {},
) {
    val loading = LoadingHandlersScope().loading()
    val stable = StableGroupHandlersScope().stable()
    val error = ErrorHandlersScope().error()
    val configure = FeedStateStatesConfigureScope().apply(configure)

    state<FeedState.Loading> {
        enter {
            when (val r = loading.enter(LoadingEnterScope(state, ::event, ::clearPendingActions))) {
                is LoadingEnterReaction.Transition -> nextState { r.next }
            }
        }
        loading.configure(this)
        configure.loading?.invoke(this)
    }
    state<FeedState.Stable.Idle> {
        action<FeedAction.Refresh> {
            when (val r = stable.idle.refresh(StableIdleRefreshScope(state, action, ::clearPendingActions))) {
                is StableIdleRefreshReaction.Transition -> nextState { r.next }
            }
        }
        action<FeedAction.LoadMore> {
            when (val r = stable.idle.loadMore(StableIdleLoadMoreScope(state, action, ::clearPendingActions))) {
                is StableIdleLoadMoreReaction.Transition -> nextState { r.next }
                is StableIdleLoadMoreReaction.Stay -> Unit
            }
        }
        stable.idle.configure(this)
        stable.configure.idle?.invoke(this)
        configure.stable?.invoke(this as koma.core.StoreBuilder.StateHandlerConfig<FeedState, FeedAction, FeedEvent, FeedState.Stable>)
    }
    state<FeedState.Stable.Refreshing> {
        enter {
            when (val r = stable.refreshing.enter(StableRefreshingEnterScope(state, ::event, ::clearPendingActions))) {
                is StableRefreshingEnterReaction.Transition -> nextState { r.next }
            }
        }
        stable.refreshing.configure(this)
        stable.configure.refreshing?.invoke(this)
        configure.stable?.invoke(this as koma.core.StoreBuilder.StateHandlerConfig<FeedState, FeedAction, FeedEvent, FeedState.Stable>)
    }
    state<FeedState.Stable.LoadingMore> {
        enter {
            when (val r = stable.loadingMore.enter(StableLoadingMoreEnterScope(state, ::event, ::clearPendingActions))) {
                is StableLoadingMoreEnterReaction.Transition -> nextState { r.next }
            }
        }
        stable.loadingMore.configure(this)
        stable.configure.loadingMore?.invoke(this)
        configure.stable?.invoke(this as koma.core.StoreBuilder.StateHandlerConfig<FeedState, FeedAction, FeedEvent, FeedState.Stable>)
    }
    state<FeedState.Error> {
        action<FeedAction.Retry> {
            when (val r = error.retry(ErrorRetryScope(state, action, ::clearPendingActions))) {
                is ErrorRetryReaction.Transition -> nextState { r.next }
            }
        }
        error.configure(this)
        configure.error?.invoke(this)
    }
}

/**
 * Builds a [koma.core.Store] for [FeedState] without spelling the store type arguments.
 *
 * Sugar over the canonical koma entry point — `Store<FeedState, FeedAction, FeedEvent>(initialState) { states(...) }`
 * builds the exact same store. [configuration] appends raw koma DSL after the generated
 * handlers (store-level escape hatch).
 */
public fun feedStore(
    initialState: FeedState,
    loading: LoadingHandlersScope.() -> LoadingHandlers,
    stable: StableGroupHandlersScope.() -> StableGroupHandlers,
    error: ErrorHandlersScope.() -> ErrorHandlers,
    context: kotlin.coroutines.CoroutineContext? = null,
    configuration: koma.core.StoreBuilder<FeedState, FeedAction, FeedEvent>.() -> Unit = {},
): koma.core.Store<FeedState, FeedAction, FeedEvent> =
    koma.core.Store<FeedState, FeedAction, FeedEvent>(initialState = initialState, context = context) {
        states(
            loading = loading,
            stable = stable,
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
public class FeedStateStatesConfigureScope internal constructor() {
    internal var loading: (koma.core.StoreBuilder.StateHandlerConfig<FeedState, FeedAction, FeedEvent, FeedState.Loading>.() -> Unit)? = null

    internal var stable: (koma.core.StoreBuilder.StateHandlerConfig<FeedState, FeedAction, FeedEvent, FeedState.Stable>.() -> Unit)? = null

    internal var error: (koma.core.StoreBuilder.StateHandlerConfig<FeedState, FeedAction, FeedEvent, FeedState.Error>.() -> Unit)? = null

    /** Appends raw koma DSL at the end of the generated `state<FeedState.Loading> {}` block. Fails fast if called twice. */
    public fun loading(block: koma.core.StoreBuilder.StateHandlerConfig<FeedState, FeedAction, FeedEvent, FeedState.Loading>.() -> Unit) {
        if (this.loading != null) throwDuplicateBuilderEntry("FeedState", "loading")
        this.loading = block
    }

    /** Appends raw koma DSL to every generated `state<...> {}` block under [FeedState.Stable] (a shared escape, expanded like shared declarations). Fails fast if called twice. */
    public fun stable(block: koma.core.StoreBuilder.StateHandlerConfig<FeedState, FeedAction, FeedEvent, FeedState.Stable>.() -> Unit) {
        if (this.stable != null) throwDuplicateBuilderEntry("FeedState", "stable")
        this.stable = block
    }

    /** Appends raw koma DSL at the end of the generated `state<FeedState.Error> {}` block. Fails fast if called twice. */
    public fun error(block: koma.core.StoreBuilder.StateHandlerConfig<FeedState, FeedAction, FeedEvent, FeedState.Error>.() -> Unit) {
        if (this.error != null) throwDuplicateBuilderEntry("FeedState", "error")
        this.error = block
    }
}
```
