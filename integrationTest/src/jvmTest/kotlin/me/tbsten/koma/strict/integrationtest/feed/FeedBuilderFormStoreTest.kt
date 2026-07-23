package me.tbsten.koma.strict.integrationtest.feed

import koma.core.Store
import koma.test.dispatchAndAwait
import koma.test.startAndAwait
import me.tbsten.koma.strict.integrationtest.runStoreTest
import me.tbsten.koma.strict.integrationtest.useStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The builder form of the intermediate-sealed bundle (`states { ... }`) against the real
 * koma-core: normal behavior (value-passing and nested-builder registrations mixed) and the
 * build-time fail-fast for missing child states.
 */
class FeedBuilderFormStoreTest {
    /** states builder で束ねた feed store (値渡しと builder ネストの混在)。挙動は createFeedStore と同一になる想定。 */
    private fun createBuilderFormFeedStore(fetchPage: suspend (offset: Int) -> FeedPage): Store<FeedState, FeedAction, FeedEvent> =
        createFeedStore(
            initialState = FeedState.Loading(),
            loading = FeedState.Loading.actions(
                enter = {
                    runCatching { fetchPage(0) }.fold(
                        onSuccess = { nextState.toStableIdle(items = it.items, hasMore = it.hasMore) },
                        onFailure = {
                            emitLoadFailed(it.message)
                            nextState.toError(message = it.message)
                        },
                    )
                },
            ),
            // 中間 sealed の builder 形式: 子 state 名の member で登録する
            stable = FeedState.Stable.states {
                // builder ネスト (Idle は enter/exit 宣言なし -> actions builder が生えている)
                idle {
                    refresh { nextState.toRefreshing() }
                    loadMore { if (state.hasMore) nextState.toLoadingMore() else stayState() }
                }
                // enter 宣言つきの子は builder overload が無いため値渡しで登録する
                refreshing(
                    FeedState.Stable.Refreshing.actions(
                        enter = { nextState.toIdle(items = listOf("refreshed"), hasMore = false) },
                    ),
                )
                loadingMore(
                    FeedState.Stable.LoadingMore.actions(
                        enter = { nextState.toIdle(items = state.items + "more", hasMore = false) },
                    ),
                )
            },
            error = FeedState.Error.actions(retry = { nextState.toLoading() }),
        )

    @Test
    fun `statesビルダーで束ねたstoreが値渡しと同様に構築でき遷移も動作する`() =
        runStoreTest {
            createBuilderFormFeedStore(
                fetchPage = { FeedPage(items = listOf("first"), hasMore = true) },
            ).useStore {
                startAndAwait()
                assertEquals(
                    FeedState.Stable.Idle(items = listOf("first"), hasMore = true),
                    currentState,
                )

                // builder ネストで登録した idle の refresh handler -> 値渡しで登録した Refreshing の enter
                dispatchAndAwait(FeedAction.Refresh)
                assertEquals(
                    FeedState.Stable.Idle(items = listOf("refreshed"), hasMore = false),
                    currentState,
                )
            }
        }

    @Test
    fun `statesビルダーで子stateの登録が不足すると構築時にIllegalStateExceptionになる`() {
        val exception =
            assertFailsWith<IllegalStateException> {
                FeedState.Stable.states {
                    idle {
                        refresh { nextState.toRefreshing() }
                        loadMore { stayState() }
                    }
                    // refreshing / loadingMore を登録し忘れる
                }
            }
        // 不足は宣言順に全列挙される
        assertEquals(
            "koma-strict builder for FeedState.Stable: missing 'refreshing', 'loadingMore'. " +
                "The builder form (actions { ... } / states { ... }) checks exhaustiveness only at build time; " +
                "the named-argument form turns missing handlers into a compile-time error.",
            exception.message,
        )
    }

    @Test
    fun `statesビルダーで同じ子stateを二重登録すると即IllegalStateExceptionになる`() {
        val handlers =
            FeedState.Stable.Idle.actions {
                refresh { nextState.toRefreshing() }
                loadMore { stayState() }
            }
        val exception =
            assertFailsWith<IllegalStateException> {
                FeedState.Stable.states {
                    idle(handlers)
                    idle(handlers)
                }
            }
        assertEquals(
            "koma-strict builder for FeedState.Stable: 'idle' is already registered. " +
                "Register each declared handler / child state exactly once in the builder block.",
            exception.message,
        )
    }
}
