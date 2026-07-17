package me.tbsten.koma.strict.ksp.feature.storeSpec.scenario

import me.tbsten.koma.strict.ksp.testing.compile.SnapshotSource

// states() の trailing escape block (per-state の素の koma DSL) の e2e。
// 宣言は既存の feed (StoreSpecHierarchyScenarios) を共用する。

/**
 * root `states()` と中間 companion `states()` の trailing escape block のコンパイル証明
 * (ユーザースケッチ FeedStore.kt 準拠)。
 *
 * overload 解決の実証ポイント:
 * - named 完全指定 + trailing lambda -> escape param (`configure`) に束縛される
 * - leaf member (`loading {}`) / 中間 sealed member (`stable {}` = subtree 共有 escape) の両方が書ける
 * - 中間 companion states() の escape は bundle が運搬し root states() が leaf ブロックへ適用する
 */
internal fun statesEscapeUsageSource(): SnapshotSource =
    SnapshotSource(
        fileName = "FeedStatesEscapeUsage.kt",
        code =
            """
            package example.feed

            import koma.core.Store

            fun buildFeedStoreWithStatesEscape(): Store<FeedState, FeedAction, FeedEvent> =
                Store<FeedState, FeedAction, FeedEvent>(initialState = FeedState.Loading()) {
                    states(
                        loading = FeedState.Loading.actions(
                            enter = { nextState.toStableIdle(items = listOf(), hasMore = false) },
                        ),
                        stable = FeedState.Stable.states(
                            idle = FeedState.Stable.Idle.actions(
                                refresh = { nextState.toRefreshing() },
                                loadMore = { stayState() },
                            ),
                            refreshing = FeedState.Stable.Refreshing.actions(
                                enter = { nextState.toIdle(hasMore = false) },
                            ),
                            loadingMore = FeedState.Stable.LoadingMore.actions(
                                enter = { nextState.toIdle(hasMore = false) },
                            ),
                        ) {
                            // 中間 companion states() の escape (leaf member)
                            idle {
                                action<FeedAction.Retry> { }
                            }
                        },
                        error = FeedState.Error.actions(retry = { nextState.toLoading() }),
                    ) {
                        // root states() の escape: leaf member と中間 sealed member (共有 escape)
                        loading { exit { } }
                        stable { enter { } }
                        error { }
                    }
                }
            """.trimIndent(),
    )
