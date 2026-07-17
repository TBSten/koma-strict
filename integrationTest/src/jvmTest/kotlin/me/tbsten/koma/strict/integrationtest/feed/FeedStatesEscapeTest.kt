package me.tbsten.koma.strict.integrationtest.feed

import koma.core.Store
import koma.test.dispatchAndAwait
import koma.test.record
import koma.test.startAndAwait
import me.tbsten.koma.strict.integrationtest.runStoreTest
import me.tbsten.koma.strict.integrationtest.useStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Behavior of the states() trailing escape block (per-state raw koma DSL) against the real
 * koma-core rc02: leaf members, the shared expansion of an intermediate-sealed member, the
 * registration order (inner states() escape before the root escape; generated registrations
 * first), and the build-time duplicate fail-fast.
 */
class FeedStatesEscapeTest {
    /**
     * escape を重ねた feed store:
     * - 中間 states() escape の `idle {}` に宣言外 action<Retry> (root 側より先に登録される)
     * - root escape の `stable {}` = 共有 escape (subtree の全 leaf へ展開) に enter + 同じ action<Retry>
     */
    private fun createEscapeFeedStore(): Store<FeedState, FeedAction, FeedEvent> =
        Store<FeedState, FeedAction, FeedEvent>(initialState = FeedState.Loading()) {
            states(
                loading = FeedState.Loading.actions(
                    enter = { nextState.toStableIdle(items = listOf("first"), hasMore = true) },
                ),
                stable = FeedState.Stable.states(
                    idle = FeedState.Stable.Idle.actions(
                        refresh = { nextState.toRefreshing() },
                        loadMore = { stayState() },
                    ),
                    refreshing = FeedState.Stable.Refreshing.actions(
                        enter = { nextState.toIdle(items = listOf("refreshed"), hasMore = false) },
                    ),
                    loadingMore = FeedState.Stable.LoadingMore.actions(
                        enter = { nextState.toIdle(hasMore = false) },
                    ),
                ) {
                    // 中間 states() の escape (leaf member)。Retry は Idle に宣言されていない
                    idle {
                        action<FeedAction.Retry> {
                            event(FeedEvent.RefreshFailed(message = "escape:inner-retry"))
                        }
                    }
                },
                error = FeedState.Error.actions(retry = { nextState.toLoading() }),
            ) {
                // root escape の中間 sealed member = subtree 全 leaf へ展開される共有 escape
                stable {
                    enter {
                        event(FeedEvent.LoadFailed(message = "escape:stable-enter"))
                    }
                    action<FeedAction.Retry> {
                        event(FeedEvent.RefreshFailed(message = "escape:outer-retry"))
                    }
                }
            }
        }

    @Test
    fun `共有escapeはsubtreeの全leafに展開され生成handlerの無いtriggerで発火する`() =
        runStoreTest {
            createEscapeFeedStore().useStore {
                record { recorder ->
                    startAndAwait()
                    // Idle には生成 enter が無い -> 共有 escape の素の enter が発火する
                    assertEquals(
                        FeedState.Stable.Idle(items = listOf("first"), hasMore = true),
                        currentState,
                    )
                    assertEquals(
                        listOf<FeedEvent>(FeedEvent.LoadFailed(message = "escape:stable-enter")),
                        recorder.events,
                    )
                }
            }
        }

    @Test
    fun `同一triggerのescape同士は内側のstatesのescapeが先に登録され先勝ちする`() =
        runStoreTest {
            createEscapeFeedStore().useStore {
                record { recorder ->
                    startAndAwait()
                    dispatchAndAwait(FeedAction.Retry)
                    // inner (中間 states() escape) -> outer (root escape) の登録順なので inner が勝つ
                    assertEquals(
                        listOf<FeedEvent>(
                            FeedEvent.LoadFailed(message = "escape:stable-enter"),
                            FeedEvent.RefreshFailed(message = "escape:inner-retry"),
                        ),
                        recorder.events,
                    )
                }
            }
        }

    @Test
    fun `生成enterを持つleafでは共有escapeの素のenterは先勝ちで走らない`() =
        runStoreTest {
            createEscapeFeedStore().useStore {
                record { recorder ->
                    startAndAwait()
                    dispatchAndAwait(FeedAction.Refresh)
                    // Refreshing の生成 enter (先に登録) だけが走り、共有 escape の enter は無視される。
                    // 遷移先 Idle への再入では (生成 enter が無いので) 共有 escape が再び発火する
                    assertEquals(
                        FeedState.Stable.Idle(items = listOf("refreshed"), hasMore = false),
                        currentState,
                    )
                    assertEquals(
                        listOf<FeedEvent>(
                            FeedEvent.LoadFailed(message = "escape:stable-enter"),
                            FeedEvent.LoadFailed(message = "escape:stable-enter"),
                        ),
                        recorder.events,
                    )
                }
            }
        }

    @Test
    fun `rootのescapeで同じmemberを二重に呼ぶと構築時にIllegalStateExceptionになる`() {
        val exception =
            assertFailsWith<IllegalStateException> {
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
                        ),
                        error = FeedState.Error.actions(retry = { nextState.toLoading() }),
                    ) {
                        loading { }
                        loading { }
                    }
                }
            }
        assertEquals(
            "koma-strict builder for FeedState: 'loading' is already registered. " +
                "Register each declared handler / child state exactly once in the builder block.",
            exception.message,
        )
    }

    @Test
    fun `中間statesのescapeでも同じmemberの二重呼び出しは即IllegalStateExceptionになる`() {
        val exception =
            assertFailsWith<IllegalStateException> {
                FeedState.Stable.states(
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
                    idle { }
                    idle { }
                }
            }
        assertEquals(
            "koma-strict builder for FeedState.Stable: 'idle' is already registered. " +
                "Register each declared handler / child state exactly once in the builder block.",
            exception.message,
        )
    }
}
