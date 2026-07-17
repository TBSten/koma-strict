package me.tbsten.koma.strict.integrationtest.feed

import koma.test.dispatchAndAwait
import koma.test.record
import koma.test.startAndAwait
import me.tbsten.koma.strict.integrationtest.runStoreTest
import me.tbsten.koma.strict.integrationtest.useStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Behavior tests for the feed sample store (intermediate sealed + conditional transition)
 * running on the real koma-core rc02.
 */
class FeedStoreTest {
    @Test
    fun `初期ロード成功でitemsとhasMoreが入ったStableIdleへ遷移する`() =
        runStoreTest {
            createFeedStore(
                fetchPage = { FeedPage(items = listOf("a", "b"), hasMore = true) },
            ).useStore {
                startAndAwait()
                assertEquals(
                    FeedState.Stable.Idle(items = listOf("a", "b"), hasMore = true),
                    currentState,
                )
            }
        }

    @Test
    fun `初期ロード失敗時はErrorへ遷移しLoadFailedをemitする`() =
        runStoreTest {
            createFeedStore(fetchPage = { error("feed boom") }).useStore {
                record { recorder ->
                    startAndAwait()
                    assertEquals(FeedState.Error(message = "feed boom"), currentState)
                    assertEquals(listOf(FeedEvent.LoadFailed(message = "feed boom")), recorder.events)
                }
            }
        }

    @Test
    fun `hasMoreがfalseのloadMoreはstayで状態が同一インスタンスのまま`() =
        runStoreTest {
            createFeedStore(
                fetchPage = { FeedPage(items = listOf("a"), hasMore = false) },
            ).useStore {
                startAndAwait()
                val before = currentState
                dispatchAndAwait(FeedAction.LoadMore) // hasMore = false なので stayState()
                // stay = koma の nextState を呼ばないだけ → インスタンス生成なし・identity 保存
                assertSame(before, currentState)
            }
        }

    @Test
    fun `hasMoreがtrueのloadMoreはLoadingMoreを経てitemsが追記されたIdleへ戻る`() =
        runStoreTest {
            createFeedStore(
                fetchPage = { offset ->
                    if (offset == 0) {
                        FeedPage(items = listOf("a", "b"), hasMore = true)
                    } else {
                        FeedPage(items = listOf("c"), hasMore = false)
                    }
                },
            ).useStore {
                record { recorder ->
                    startAndAwait()
                    dispatchAndAwait(FeedAction.LoadMore)
                    assertEquals(
                        FeedState.Stable.Idle(items = listOf("a", "b", "c"), hasMore = false),
                        currentState,
                    )
                    // 中間 state (LoadingMore) を通過したことは recorder で検証できる
                    assertTrue(recorder.states.any { it is FeedState.Stable.LoadingMore })
                }
            }
        }

    @Test
    fun `refreshはRefreshingを経てitemsを差し替えたIdleへ戻る`() =
        runStoreTest {
            var refreshed = false
            createFeedStore(
                fetchPage = {
                    if (refreshed) {
                        FeedPage(items = listOf("new"), hasMore = false)
                    } else {
                        FeedPage(items = listOf("old"), hasMore = true)
                    }
                },
            ).useStore {
                record { recorder ->
                    startAndAwait()
                    refreshed = true
                    dispatchAndAwait(FeedAction.Refresh)
                    assertEquals(
                        FeedState.Stable.Idle(items = listOf("new"), hasMore = false),
                        currentState,
                    )
                    assertTrue(recorder.states.any { it is FeedState.Stable.Refreshing })
                }
            }
        }

    @Test
    fun `refresh失敗時はRefreshFailedをemitし旧itemsを温存したIdleへ戻る`() =
        runStoreTest {
            var failing = false
            createFeedStore(
                fetchPage = {
                    if (failing) error("refresh boom") else FeedPage(items = listOf("old"), hasMore = false)
                },
            ).useStore {
                record { recorder ->
                    startAndAwait()
                    failing = true
                    dispatchAndAwait(FeedAction.Refresh)
                    // items は Refreshing (中間型の同名 prop) から持ち越し = 旧一覧を温存
                    assertEquals(
                        FeedState.Stable.Idle(items = listOf("old"), hasMore = true),
                        currentState,
                    )
                    assertEquals(listOf(FeedEvent.RefreshFailed(message = "refresh boom")), recorder.events)
                }
            }
        }
}
