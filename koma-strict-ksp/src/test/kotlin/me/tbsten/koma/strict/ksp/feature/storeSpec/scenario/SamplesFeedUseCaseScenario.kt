package me.tbsten.koma.strict.ksp.feature.storeSpec.scenario

import me.tbsten.koma.strict.ksp.testing.compile.SnapshotSource
import me.tbsten.koma.strict.ksp.testing.scenario.SnapshotScenario
import org.intellij.lang.annotations.Language

// doc/internal/samples.md ケース「LCE + pull-to-refresh + additional load」の忠実写経
// (StoreSpecUseCasesTest 用)。
//
// samples.md からの調整点:
// - package 宣言 (samples.feed) と import を追加 (samples.md は誌面上省略の前提のため)
// - 利用側が呼ぶ fetchPage() とその戻り値型 FeedPage を suspend スタブ (FeedStubs.kt) として追加

@Language("kotlin")
private val samplesFeedDeclaration =
    """
    package samples.feed

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
    """.trimIndent()

@Language("kotlin")
private val samplesFeedUsage =
    """
    package samples.feed

    import koma.core.Store

    val store = Store<FeedState, FeedAction, FeedEvent>(initialState = FeedState.Loading()) {
        states(
            loading = FeedState.Loading.actions(
                enter = {
                    runCatching { fetchPage(offset = 0) }.fold(
                        onSuccess = { nextState.toStableIdle(items = it.items, hasMore = it.hasMore) },
                        onFailure = {
                            emitLoadFailed(it.message)
                            nextState.toError(message = it.message)
                        },
                    )
                },
            ),
            stable = FeedState.Stable.states(
                idle = FeedState.Stable.Idle.actions(
                    refresh = { nextState.toRefreshing() },   // items は同名 prop から持ち越し
                    loadMore = { if (state.hasMore) nextState.toLoadingMore() else stayState() },
                ),
                refreshing = FeedState.Stable.Refreshing.actions(
                    enter = {
                        runCatching { fetchPage(offset = 0) }.fold(
                            onSuccess = { nextState.toIdle(items = it.items, hasMore = it.hasMore) },
                            onFailure = {
                                emitRefreshFailed(it.message)
                                nextState.toIdle(hasMore = true)   // items は持ち越し = 旧一覧を温存
                            },
                        )
                    },
                ),
                loadingMore = FeedState.Stable.LoadingMore.actions(
                    enter = {
                        runCatching { fetchPage(offset = state.items.size) }.fold(
                            onSuccess = { nextState.toIdle(items = state.items + it.items, hasMore = it.hasMore) },
                            onFailure = {
                                emitLoadMoreFailed(it.message)
                                nextState.toIdle(hasMore = true)
                            },
                        )
                    },
                ),
            ),
            error = FeedState.Error.actions(retry = { nextState.toLoading() }),
        ) {
            // states() の trailing block = per-state escape(素の koma DSL を state 単位で差し込む)。
            // 同一 trigger は先勝ち(生成 handler が常に先)なので、宣言でカバーしない trigger 用
            loading {
                // Loading では Retry を宣言していない → 素の handler が発火できる(escape の実例)
                action<FeedAction.Retry> { }
            }
            stable {
                // 中間 sealed member = subtree の全 leaf の state ブロックへ展開される共有 escape
            }
            error {
            }
        }
    }
    """.trimIndent()

@Language("kotlin")
private val samplesFeedStubs =
    """
    package samples.feed

    // samples.md には現れないダミー実装 (利用側コードのコンパイル証明用)

    class FeedPage(val items: List<String>, val hasMore: Boolean)

    suspend fun fetchPage(offset: Int): FeedPage = FeedPage(items = emptyList(), hasMore = false)
    """.trimIndent()

/** samples.md「LCE + pull-to-refresh + additional load」: 宣言 + 利用側 + fetchPage スタブ。 */
internal fun samplesFeedUseCase(): SnapshotScenario =
    SnapshotScenario(
        SnapshotSource(fileName = "FeedState.kt", code = samplesFeedDeclaration),
        SnapshotSource(fileName = "FeedStoreUsage.kt", code = samplesFeedUsage),
        SnapshotSource(fileName = "FeedStubs.kt", code = samplesFeedStubs),
    )
