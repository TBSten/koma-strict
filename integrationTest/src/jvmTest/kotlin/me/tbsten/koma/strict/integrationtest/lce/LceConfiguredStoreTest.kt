package me.tbsten.koma.strict.integrationtest.lce

import koma.test.dispatchAndAwait
import koma.test.record
import koma.test.startAndAwait
import me.tbsten.koma.strict.integrationtest.runStoreTest
import me.tbsten.koma.strict.integrationtest.useStore
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Behavior tests for the per-state `configure` escape hatch and the generated
 * `clearPendingActions()` passthrough, running on the real koma-core rc02.
 */
class LceConfiguredStoreTest {
    @Test
    fun `configureで登録した素のkoma enterがContent遷移時に実際に発火する`() =
        runStoreTest {
            createConfiguredLceStore(fetchData = { "fetched" }).useStore {
                record { recorder ->
                    startAndAwait() // Loading の enter -> Content 遷移 -> configure 登録の enter まで同期チェーン
                    assertEquals(LceState.Content(data = "fetched"), currentState)
                    assertEquals(
                        listOf(LceEvent.LoadFailed(message = "configured:fetched")),
                        recorder.events,
                    )
                }
            }
        }

    @Test
    fun `clearPendingActionsを呼ぶhandler経由でも正常に遷移しconfigureのenterは再遷移ごとに発火する`() =
        runStoreTest {
            var callCount = 0
            createConfiguredLceStore(fetchData = { "fetched:${++callCount}" }).useStore {
                record { recorder ->
                    startAndAwait()
                    dispatchAndAwait(LceAction.Reload) // reload handler が clearPendingActions() を呼ぶ
                    assertEquals(LceState.Content(data = "fetched:2"), currentState)
                    // configure の enter は Content へ入るたびに発火する (escape hatch が本物の登録である証明)
                    assertEquals(
                        listOf(
                            LceEvent.LoadFailed(message = "configured:fetched:1"),
                            LceEvent.LoadFailed(message = "configured:fetched:2"),
                        ),
                        recorder.events,
                    )
                }
            }
        }
}
