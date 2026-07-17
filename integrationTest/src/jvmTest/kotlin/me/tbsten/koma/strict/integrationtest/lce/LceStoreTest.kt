package me.tbsten.koma.strict.integrationtest.lce

import koma.test.dispatchAndAwait
import koma.test.record
import koma.test.startAndAwait
import me.tbsten.koma.strict.integrationtest.runStoreTest
import me.tbsten.koma.strict.integrationtest.useStore
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Behavior tests for the LCE sample store running on the real koma-core rc02.
 */
class LceStoreTest {
    @Test
    fun `全stateのhandlerを揃えるとstoreが構築でき初期stateはfactory構築のLoadingと等価になる`() =
        runStoreTest {
            createLceStore(fetchData = { "fetched" }).useStore {
                // currentState の読み取りは store を start しない (koma 仕様)。
                // interface 宣言 state は factory (companion invoke) 構築 + impl の data class 等価性で比較できる
                assertEquals(LceState.Loading(), currentState)
            }
        }

    @Test
    fun `初期stateのonEnterが実行され成功するとContentへ遷移する`() =
        runStoreTest {
            createLceStore(fetchData = { "fetched" }).useStore {
                startAndAwait() // 起動時の同期 enter チェーンまで待つ
                assertEquals(LceState.Content(data = "fetched"), currentState)
            }
        }

    @Test
    fun `fetch失敗時はErrorへ遷移しLoadFailedをemitする`() =
        runStoreTest {
            createLceStore(fetchData = { error("boom") }).useStore {
                record { recorder ->
                    startAndAwait()
                    assertEquals(LceState.Error(message = "boom"), currentState)
                    assertEquals(listOf(LceEvent.LoadFailed(message = "boom")), recorder.events)
                }
            }
        }

    @Test
    fun `Errorからのretryで再フェッチし成功すればContentへ遷移する`() =
        runStoreTest {
            // 初回は失敗・2 回目以降は成功する fetch を注入する
            var failing = true
            createLceStore(
                fetchData = { if (failing) error("boom") else "recovered" },
            ).useStore {
                startAndAwait()
                assertEquals(LceState.Error(message = "boom"), currentState)

                failing = false
                dispatchAndAwait(LceAction.Retry) // Loading の enter (同期チェーン) まで待つ
                assertEquals(LceState.Content(data = "recovered"), currentState)
            }
        }

    @Test
    fun `ContentからのreloadはLoadingを通過して新しいdataのContentになる`() =
        runStoreTest {
            var callCount = 0
            createLceStore(fetchData = { "fetched:${++callCount}" }).useStore {
                record { recorder ->
                    startAndAwait()
                    dispatchAndAwait(LceAction.Reload)
                    // 中間 state (Loading) の通過も含めて recorder の状態列で検証できる
                    assertEquals(
                        listOf(
                            LceState.Loading(),
                            LceState.Content(data = "fetched:1"),
                            LceState.Loading(),
                            LceState.Content(data = "fetched:2"),
                        ),
                        recorder.states,
                    )
                }
            }
        }
}
