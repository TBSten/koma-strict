package me.tbsten.koma.strict.ksp.feature.storeSpec.scenario

import me.tbsten.koma.strict.ksp.testing.compile.SnapshotSource

// 階層系ケース: 中間 sealed / 条件付き遷移 (Stay) / prop 持ち越し (samples.md ケース 2)、
// root 共有アクション / data object / E = Nothing (ケース 3)、@DefaultName。

/** samples.md ケース 2: 中間 sealed (`Stable`) / 条件付き遷移 (Stay) / 同名 prop 持ち越し。 */
internal fun feedScenarioSource(): SnapshotSource =
    SnapshotSource(
        fileName = "FeedState.kt",
        code =
            """
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
            """.trimIndent(),
    )

/** samples.md ケース 3: root 共有アクション / 宣言ゼロ state / data object 宣言 / E = Nothing。 */
internal fun tabsScenarioSource(): SnapshotSource =
    SnapshotSource(
        fileName = "TabsState.kt",
        code =
            """
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
            """.trimIndent(),
    )

/**
 * samples.md ケース 3 の利用側コード。E = Nothing の `StoreBuilder<S, A, Nothing>.states()` と
 * root 共有 default ブロック (`default = { actions(selectTab = ...) }`) が
 * 「利用側から書ける」ことのコンパイル証明。
 */
internal fun tabsUsageSource(): SnapshotSource =
    SnapshotSource(
        fileName = "TabsStoreUsage.kt",
        code =
            """
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
            """.trimIndent(),
    )

/**
 * 中間 sealed の共有 prop を leaf が covariant override で狭めるケース。
 * 生成 Impl / factory / Transitions が leaf 側の狭い型 (`String`) を使うこと
 * (祖先勝ちだと `CharSequence` になりコンパイル不能) の e2e 証明。
 */
internal fun covariantOverrideScenarioSource(): SnapshotSource =
    SnapshotSource(
        fileName = "DetailState.kt",
        code =
            """
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
            """.trimIndent(),
    )

/** [covariantOverrideScenarioSource] の利用側コード (`state.content` が String として扱えること)。 */
internal fun covariantOverrideUsageSource(): SnapshotSource =
    SnapshotSource(
        fileName = "DetailStoreUsage.kt",
        code =
            """
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
            """.trimIndent(),
    )

/** @DefaultName 付き中間 sealed の共有アクション (default ブロック名の変更)。 */
internal fun defaultNameScenarioSource(): SnapshotSource =
    SnapshotSource(
        fileName = "FlowState.kt",
        code =
            """
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
            """.trimIndent(),
    )
