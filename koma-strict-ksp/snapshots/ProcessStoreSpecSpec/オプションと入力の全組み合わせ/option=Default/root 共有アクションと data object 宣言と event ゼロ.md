## Input:TabsState.kt

```kt
package example.tabs

import koma.core.Action
import koma.core.State
import me.tbsten.koma.strict.OnAction
import me.tbsten.koma.strict.Stay
import me.tbsten.koma.strict.StoreSpec

@StoreSpec(initial = [TabsState.Home::class])
@OnAction<TabsAction.SelectTab>(   // root 共有アクション = default ブロック
    // Note: root 自身に付く annotation からは入れ子 state を bare 名で参照できない
    // (Unresolved reference)。samples.md ケース 3 の bare 記法とのギャップは報告済み。
    nextState = [Stay::class, TabsState.Home::class, TabsState.Search::class, TabsState.Profile::class],
)
sealed interface TabsState : State {
    companion object

    data object Home : TabsState                       // data object 宣言 (従来通り可)

    @OnAction<TabsAction.UpdateQuery>(nextState = [Search::class])   // 自己遷移
    interface Search : TabsState { val query: String; companion object }

    data object Profile : TabsState                    // 宣言ゼロ -> facade に引数が生えない
}

sealed interface TabsAction : Action {
    data class SelectTab(val tab: Tab) : TabsAction {
        enum class Tab { Home, Search, Profile }
    }
    data class UpdateQuery(val query: String) : TabsAction
}
// event なし -> E = Nothing として生成
```

## Input:TabsStoreUsage.kt

```kt
package example.tabs

import koma.core.Store

fun buildTabsStore(): Store<TabsState, TabsAction, Nothing> =
    Store<TabsState, TabsAction, Nothing>(initialState = TabsState.Home) {
        states(
            default = TabsState.actions(      // root 共有アクション = default ブロック (先頭)
                selectTab = {
                    when (action.tab) {
                        TabsAction.SelectTab.Tab.Home -> nextState.toHome()
                        TabsAction.SelectTab.Tab.Search -> nextState.toSearch(query = "")
                        TabsAction.SelectTab.Tab.Profile -> nextState.toProfile()
                    }
                },
            ),
            search = TabsState.Search.actions(updateQuery = { nextState.toSearch(query = action.query) }),
            // Home / Profile は宣言ゼロ -> param 自体が生えない
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
w: [ksp] <TMPDIR>/Kotlin-Compilation<N>/sources/TabsState.kt:18: koma-strict: the companion object of 'example.tabs.TabsState' is named 'data'. An unnamed `companion object` immediately followed by a `data ...` declaration is parsed by kotlinc as a companion NAMED 'data' (the modifier is consumed by the parser). If this is unintended, declare the companion last or give it an explicit body: `companion object {}`.
```

## Output:Generated sources

```kt
// file: TabsState.Search.generated.kt
package example.tabs

import me.tbsten.koma.strict.*
import me.tbsten.koma.strict.dsl.*

private data class SearchImpl(override val query: String) : TabsState.Search

public operator fun TabsState.Search.Companion.invoke(query: String): TabsState.Search = SearchImpl(query)

public sealed interface SearchUpdateQueryReaction {
    public class Transition internal constructor(
        internal val next: TabsState,
    ) : SearchUpdateQueryReaction
}

public class SearchUpdateQueryTransitions internal constructor(
    private val state: TabsState.Search,
) {
    public fun toSearch(
        query: String = state.query,
    ): SearchUpdateQueryReaction = SearchUpdateQueryReaction.Transition(TabsState.Search(query))
}

@KomaStrictDsl
@koma.core.KomaStoreDsl
@OptIn(InternalKomaStrictApi::class)
public class SearchUpdateQueryScope internal constructor(
    public val state: TabsState.Search,
    public val action: TabsAction.UpdateQuery,
    onClearPendingActions: () -> Unit,
) : HandlerScope(onClearPendingActions) {
    public val nextState: SearchUpdateQueryTransitions = SearchUpdateQueryTransitions(state)
}

public class SearchHandlers internal constructor(
    internal val updateQuery: suspend SearchUpdateQueryScope.() -> SearchUpdateQueryReaction,
    internal val configure: koma.core.StoreBuilder.StateHandlerConfig<TabsState, TabsAction, Nothing, TabsState.Search>.() -> Unit,
) : (SearchHandlersScope) -> SearchHandlers {
    /** Returns this bundle itself, so an already-built value is assignable to the scope-lambda-typed `states(...)` parameter (value-passing / scope-lambda interchange). */
    override fun invoke(p1: SearchHandlersScope): SearchHandlers = this
}

/** Bundles this state's handlers. [configure] appends raw koma DSL at the end of the generated `state<...> {}` block (per-state escape hatch). */
public fun TabsState.Search.Companion.actions(
    updateQuery: suspend SearchUpdateQueryScope.() -> SearchUpdateQueryReaction,
    configure: koma.core.StoreBuilder.StateHandlerConfig<TabsState, TabsAction, Nothing, TabsState.Search>.() -> Unit = {},
): SearchHandlers = SearchHandlers(updateQuery, configure)

/**
 * Builder-form overload of `actions(...)` (see [SearchActionsBuilder]).
 *
 * Note: unlike the named-argument overload, exhaustiveness of the declared handlers is
 * checked **at build time** (fail-fast when the block finishes), not at compile time.
 * This overload exists only for states without enter / exit declarations (those handlers
 * have no builder member).
 */
public fun TabsState.Search.Companion.actions(build: SearchActionsBuilder.() -> Unit): SearchHandlers = SearchActionsBuilder().apply(build).build()

/** Receiver of the scope-lambda form (`{ actions(...) }`) of the matching `states(...)` parameter. [actions] mirrors the companion `actions(...)` extension. */
@KomaStrictDsl
@koma.core.KomaStoreDsl
public class SearchHandlersScope internal constructor() {
    /** Bundles this state's handlers. [configure] appends raw koma DSL at the end of the generated `state<...> {}` block (per-state escape hatch). */
    public fun actions(
        updateQuery: suspend SearchUpdateQueryScope.() -> SearchUpdateQueryReaction,
        configure: koma.core.StoreBuilder.StateHandlerConfig<TabsState, TabsAction, Nothing, TabsState.Search>.() -> Unit = {},
    ): SearchHandlers = SearchHandlers(updateQuery, configure)

    /**
     * Builder-form overload of `actions(...)` (see [SearchActionsBuilder]).
     *
     * Note: unlike the named-argument overload, exhaustiveness of the declared handlers is
     * checked **at build time** (fail-fast when the block finishes), not at compile time.
     * This overload exists only for states without enter / exit declarations (those handlers
     * have no builder member).
     */
    public fun actions(build: SearchActionsBuilder.() -> Unit): SearchHandlers = SearchActionsBuilder().apply(build).build()
}

/**
 * Builder receiver of the `actions { ... }` overload building [SearchHandlers].
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
public class SearchActionsBuilder internal constructor() {
    private val updateQuery = SetOnceSlot<suspend SearchUpdateQueryScope.() -> SearchUpdateQueryReaction>("TabsState.Search", "updateQuery")

    private val configure = SetOnceSlot<koma.core.StoreBuilder.StateHandlerConfig<TabsState, TabsAction, Nothing, TabsState.Search>.() -> Unit>("TabsState.Search", "configure")

    /** Registers the handler of the same-named `actions(...)` parameter. Duplicate registration fails fast with [IllegalStateException]. */
    public fun updateQuery(handler: suspend SearchUpdateQueryScope.() -> SearchUpdateQueryReaction) { updateQuery.set(handler) }

    /** Appends raw koma DSL at the end of the generated `state<...> {}` block (the trailing `configure` parameter of `actions(...)`). Duplicate registration fails fast with [IllegalStateException]. */
    public fun configure(block: koma.core.StoreBuilder.StateHandlerConfig<TabsState, TabsAction, Nothing, TabsState.Search>.() -> Unit) { configure.set(block) }

    internal fun build(): SearchHandlers {
        val missing = listOfNotNull(
            "updateQuery".takeIf { !updateQuery.isSet },
        )
        if (missing.isNotEmpty()) throwMissingBuilderEntries("TabsState.Search", missing)
        return SearchHandlers(updateQuery.getOrNull()!!, configure.getOrNull() ?: {})
    }
}

// ----- next file -----

// file: TabsState.generated.kt
package example.tabs

import me.tbsten.koma.strict.*
import me.tbsten.koma.strict.dsl.*

public sealed interface TabsStateSelectTabReaction {
    public class Transition internal constructor(
        internal val next: TabsState,
    ) : TabsStateSelectTabReaction

    public data object Stay : TabsStateSelectTabReaction
}

public class TabsStateSelectTabTransitions internal constructor(
    @Suppress("unused") private val state: TabsState,
) {
    public fun toHome(): TabsStateSelectTabReaction = TabsStateSelectTabReaction.Transition(TabsState.Home)

    public fun toSearch(
        query: String,
    ): TabsStateSelectTabReaction = TabsStateSelectTabReaction.Transition(TabsState.Search(query))

    public fun toProfile(): TabsStateSelectTabReaction = TabsStateSelectTabReaction.Transition(TabsState.Profile)
}

@KomaStrictDsl
@koma.core.KomaStoreDsl
@OptIn(InternalKomaStrictApi::class)
public class TabsStateSelectTabScope internal constructor(
    public val state: TabsState,
    public val action: TabsAction.SelectTab,
    onClearPendingActions: () -> Unit,
) : HandlerScope(onClearPendingActions) {
    public val nextState: TabsStateSelectTabTransitions = TabsStateSelectTabTransitions(state)

    /** Chooses to stay in the current state. This simply does not call koma's nextState: no instance is created and pending actions are not discarded. */
    public fun stayState(): TabsStateSelectTabReaction = TabsStateSelectTabReaction.Stay
}

public class TabsStateDefaultHandlers internal constructor(
    internal val selectTab: suspend TabsStateSelectTabScope.() -> TabsStateSelectTabReaction,
) : (TabsStateDefaultHandlersScope) -> TabsStateDefaultHandlers {
    /** Returns this bundle itself, so an already-built value is assignable to the scope-lambda-typed `states(...)` parameter (value-passing / scope-lambda interchange). */
    override fun invoke(p1: TabsStateDefaultHandlersScope): TabsStateDefaultHandlers = this
}

public fun TabsState.data.actions(
    selectTab: suspend TabsStateSelectTabScope.() -> TabsStateSelectTabReaction,
    preventTrailingLambda: PreventTrailingLambda = PreventTrailingLambda,
): TabsStateDefaultHandlers = TabsStateDefaultHandlers(selectTab)

/**
 * Builder-form overload of `actions(...)` (see [TabsStateDefaultActionsBuilder]).
 *
 * Note: unlike the named-argument overload, exhaustiveness of the declared handlers is
 * checked **at build time** (fail-fast when the block finishes), not at compile time.
 * This overload exists only for states without enter / exit declarations (those handlers
 * have no builder member).
 */
public fun TabsState.data.actions(build: TabsStateDefaultActionsBuilder.() -> Unit): TabsStateDefaultHandlers = TabsStateDefaultActionsBuilder().apply(build).build()

/** Receiver of the scope-lambda form (`{ actions(...) }`) of the matching `states(...)` parameter. [actions] mirrors the companion `actions(...)` extension. */
@KomaStrictDsl
@koma.core.KomaStoreDsl
public class TabsStateDefaultHandlersScope internal constructor() {
    public fun actions(
        selectTab: suspend TabsStateSelectTabScope.() -> TabsStateSelectTabReaction,
        preventTrailingLambda: PreventTrailingLambda = PreventTrailingLambda,
    ): TabsStateDefaultHandlers = TabsStateDefaultHandlers(selectTab)

    /**
     * Builder-form overload of `actions(...)` (see [TabsStateDefaultActionsBuilder]).
     *
     * Note: unlike the named-argument overload, exhaustiveness of the declared handlers is
     * checked **at build time** (fail-fast when the block finishes), not at compile time.
     * This overload exists only for states without enter / exit declarations (those handlers
     * have no builder member).
     */
    public fun actions(build: TabsStateDefaultActionsBuilder.() -> Unit): TabsStateDefaultHandlers = TabsStateDefaultActionsBuilder().apply(build).build()
}

/**
 * Builder receiver of the `actions { ... }` overload building [TabsStateDefaultHandlers].
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
public class TabsStateDefaultActionsBuilder internal constructor() {
    private val selectTab = SetOnceSlot<suspend TabsStateSelectTabScope.() -> TabsStateSelectTabReaction>("TabsState.default", "selectTab")

    /** Registers the handler of the same-named `actions(...)` parameter. Duplicate registration fails fast with [IllegalStateException]. */
    public fun selectTab(handler: suspend TabsStateSelectTabScope.() -> TabsStateSelectTabReaction) { selectTab.set(handler) }

    internal fun build(): TabsStateDefaultHandlers {
        val missing = listOfNotNull(
            "selectTab".takeIf { !selectTab.isSet },
        )
        if (missing.isNotEmpty()) throwMissingBuilderEntries("TabsState.default", missing)
        return TabsStateDefaultHandlers(selectTab.getOrNull()!!)
    }
}

// ----- next file -----

// file: TabsState.storeSpec.generated.kt
package example.tabs

import me.tbsten.koma.strict.*
import me.tbsten.koma.strict.dsl.*

@kotlin.jvm.JvmName("tabsStateStates")
@Suppress("NAME_SHADOWING")
public fun koma.core.StoreBuilder<TabsState, TabsAction, Nothing>.states(
    default: TabsStateDefaultHandlersScope.() -> TabsStateDefaultHandlers,
    search: SearchHandlersScope.() -> SearchHandlers,
    configure: TabsStateStatesConfigureScope.() -> Unit = {},
) {
    val default = TabsStateDefaultHandlersScope().default()
    val search = SearchHandlersScope().search()
    val configure = TabsStateStatesConfigureScope().apply(configure)

    state<TabsState.Home> {
        action<TabsAction.SelectTab> {
            when (val r = default.selectTab(TabsStateSelectTabScope(state, action, ::clearPendingActions))) {
                is TabsStateSelectTabReaction.Transition -> nextState { r.next }
                is TabsStateSelectTabReaction.Stay -> Unit
            }
        }
    }
    state<TabsState.Search> {
        action<TabsAction.SelectTab> {
            when (val r = default.selectTab(TabsStateSelectTabScope(state, action, ::clearPendingActions))) {
                is TabsStateSelectTabReaction.Transition -> nextState { r.next }
                is TabsStateSelectTabReaction.Stay -> Unit
            }
        }
        action<TabsAction.UpdateQuery> {
            when (val r = search.updateQuery(SearchUpdateQueryScope(state, action, ::clearPendingActions))) {
                is SearchUpdateQueryReaction.Transition -> nextState { r.next }
            }
        }
        search.configure(this)
        configure.search?.invoke(this)
    }
    state<TabsState.Profile> {
        action<TabsAction.SelectTab> {
            when (val r = default.selectTab(TabsStateSelectTabScope(state, action, ::clearPendingActions))) {
                is TabsStateSelectTabReaction.Transition -> nextState { r.next }
                is TabsStateSelectTabReaction.Stay -> Unit
            }
        }
    }
}

/**
 * Builds a [koma.core.Store] for [TabsState] without spelling the store type arguments.
 *
 * Sugar over the canonical koma entry point — `Store<TabsState, TabsAction, Nothing>(initialState) { states(...) }`
 * builds the exact same store. [configuration] appends raw koma DSL after the generated
 * handlers (store-level escape hatch).
 */
public fun tabsStore(
    initialState: TabsState,
    default: TabsStateDefaultHandlersScope.() -> TabsStateDefaultHandlers,
    search: SearchHandlersScope.() -> SearchHandlers,
    context: kotlin.coroutines.CoroutineContext? = null,
    configuration: koma.core.StoreBuilder<TabsState, TabsAction, Nothing>.() -> Unit = {},
): koma.core.Store<TabsState, TabsAction, Nothing> =
    koma.core.Store<TabsState, TabsAction, Nothing>(initialState = initialState, context = context) {
        states(
            default = default,
            search = search,
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
public class TabsStateStatesConfigureScope internal constructor() {
    internal var search: (koma.core.StoreBuilder.StateHandlerConfig<TabsState, TabsAction, Nothing, TabsState.Search>.() -> Unit)? = null

    /** Appends raw koma DSL at the end of the generated `state<TabsState.Search> {}` block. Fails fast if called twice. */
    public fun search(block: koma.core.StoreBuilder.StateHandlerConfig<TabsState, TabsAction, Nothing, TabsState.Search>.() -> Unit) {
        if (this.search != null) throwDuplicateBuilderEntry("TabsState", "search")
        this.search = block
    }
}
```
