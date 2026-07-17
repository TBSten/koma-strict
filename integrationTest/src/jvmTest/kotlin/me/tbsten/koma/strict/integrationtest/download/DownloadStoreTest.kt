package me.tbsten.koma.strict.integrationtest.download

import koma.test.dispatchAndAwait
import koma.test.record
import koma.test.startAndAwait
import me.tbsten.koma.strict.integrationtest.runStoreTest
import me.tbsten.koma.strict.integrationtest.useStore
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Behavior tests for the plus-composed intermediate node (`actions(...) + states(...)`) and
 * the generated per-store factory, running on the real koma-core rc02.
 */
class DownloadStoreTest {
    @Test
    fun `factory経由で型引数なしにstoreが立ちstartでActiveRunningへ遷移する`() =
        runStoreTest {
            createDownloadStore().useStore {
                startAndAwait()
                dispatchAndAwait(DownloadAction.Start(url = "https://example.com/file"))
                assertEquals(DownloadState.Active.Running(url = "https://example.com/file"), currentState)
            }
        }

    @Test
    fun `statesで束ねた子handlerがpauseとresumeを処理しurlは同名propで持ち越される`() =
        runStoreTest {
            createDownloadStore().useStore {
                startAndAwait()
                dispatchAndAwait(DownloadAction.Start(url = "https://example.com/file"))
                dispatchAndAwait(DownloadAction.Pause)
                assertEquals(DownloadState.Active.Paused(url = "https://example.com/file"), currentState)
                dispatchAndAwait(DownloadAction.Resume)
                assertEquals(DownloadState.Active.Running(url = "https://example.com/file"), currentState)
            }
        }

    @Test
    fun `plus合成した共有cancelはRunningとPausedの両方で発火しemitしてIdleへ戻る`() =
        runStoreTest {
            createDownloadStore().useStore {
                record { recorder ->
                    startAndAwait()
                    dispatchAndAwait(DownloadAction.Start(url = "a"))
                    dispatchAndAwait(DownloadAction.Cancel) // Running からの共有 handler
                    assertEquals(DownloadState.Idle, currentState)
                    dispatchAndAwait(DownloadAction.Start(url = "b"))
                    dispatchAndAwait(DownloadAction.Pause)
                    dispatchAndAwait(DownloadAction.Cancel) // Paused からも同じ共有 handler
                    assertEquals(DownloadState.Idle, currentState)
                    assertEquals(2, recorder.events.count { it == DownloadEvent.Canceled })
                }
            }
        }

    @Test
    fun `configurationで登録した素のkoma enterがIdle進入ごとに発火する`() =
        runStoreTest {
            createDownloadStore().useStore {
                record { recorder ->
                    startAndAwait() // 初期 state = Idle の enter でも発火する
                    dispatchAndAwait(DownloadAction.Start(url = "a"))
                    dispatchAndAwait(DownloadAction.Cancel) // Idle へ戻る -> 再発火
                    assertEquals(
                        listOf(
                            DownloadEvent.ReturnedToIdle,
                            DownloadEvent.Canceled,
                            DownloadEvent.ReturnedToIdle,
                        ),
                        recorder.events,
                    )
                }
            }
        }
}
