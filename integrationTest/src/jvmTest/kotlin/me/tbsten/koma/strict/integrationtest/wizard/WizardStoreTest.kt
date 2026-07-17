package me.tbsten.koma.strict.integrationtest.wizard

import koma.test.dispatchAndAwait
import koma.test.record
import koma.test.startAndAwait
import me.tbsten.koma.strict.integrationtest.runStoreTest
import me.tbsten.koma.strict.integrationtest.useStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/**
 * Behavior tests for the wizard sample store (stay + emit / self transition / prop carry-over)
 * running on the real koma-core rc02.
 */
class WizardStoreTest {
    // 検証はしない submit スタブ (成功パス用)
    private val alwaysSucceedSubmit: suspend (String, String) -> Unit = { _, _ -> }

    @Test
    fun `検証NGのnextはstayしValidationFailedをemitする`() =
        runStoreTest {
            createWizardStore(submitForm = alwaysSucceedSubmit).useStore {
                record { recorder ->
                    startAndAwait()
                    val before = currentState
                    dispatchAndAwait(WizardAction.Next) // name が空なので検証 NG
                    // stay + emit: 状態は同一インスタンスのまま event だけが飛ぶ
                    assertSame(before, currentState)
                    assertEquals(
                        listOf(WizardEvent.ValidationFailed(reason = "name is required")),
                        recorder.events,
                    )
                }
            }
        }

    @Test
    fun `inputNameの自己遷移は状態を作り直しnameを差し替える`() =
        runStoreTest {
            createWizardStore(submitForm = alwaysSucceedSubmit).useStore {
                startAndAwait()
                dispatchAndAwait(WizardAction.InputName(name = "alice"))
                val before = currentState
                dispatchAndAwait(WizardAction.InputName(name = "bob")) // 自己遷移 (値が変わる)
                // 注意 (koma 実測): 同値の自己遷移は StateFlow の equality conflation で
                // インスタンスが差し替わらないため、値が変わるケースで観測する
                assertNotSame(before, currentState)
                assertEquals(WizardState.Step1(name = "bob"), currentState)
            }
        }

    @Test
    fun `検証OKのnextはnameを持ち越してStep2へ進む`() =
        runStoreTest {
            createWizardStore(submitForm = alwaysSucceedSubmit).useStore {
                startAndAwait()
                dispatchAndAwait(WizardAction.InputName(name = "alice"))
                dispatchAndAwait(WizardAction.Next)
                // name は同名 prop の持ち越し (デフォルト引数)。email は新規 prop = 呼び出し側の明示 ("")
                assertEquals(WizardState.Step2(name = "alice", email = ""), currentState)
            }
        }

    @Test
    fun `backで前のstepへ戻りpropを持ち越す`() =
        runStoreTest {
            createWizardStore(
                submitForm = alwaysSucceedSubmit,
                initialState = WizardState.Step2(name = "alice", email = "a@example.com"),
            ).useStore {
                startAndAwait()
                dispatchAndAwait(WizardAction.Back)
                assertEquals(WizardState.Step1(name = "alice"), currentState)
            }
        }

    @Test
    fun `Step3からのsubmit成功でSubmittingを経てDoneへ遷移する`() =
        runStoreTest {
            createWizardStore(
                submitForm = alwaysSucceedSubmit,
                initialState = WizardState.Step3(name = "alice", email = "a@example.com"),
            ).useStore {
                startAndAwait()
                dispatchAndAwait(WizardAction.Submit) // Submitting の enter (同期チェーン) まで待つ
                // Done は companion なし = factory なしなので型で検証する
                assertIs<WizardState.Done>(currentState)
            }
        }

    @Test
    fun `submit失敗時はSubmitFailedをemitしてpropを持ち越したStep3へ戻る`() =
        runStoreTest {
            createWizardStore(
                submitForm = { _, _ -> error("submit boom") },
                initialState = WizardState.Step3(name = "alice", email = "a@example.com"),
            ).useStore {
                record { recorder ->
                    startAndAwait()
                    dispatchAndAwait(WizardAction.Submit)
                    assertEquals(
                        WizardState.Step3(name = "alice", email = "a@example.com"),
                        currentState,
                    )
                    assertEquals(
                        listOf(WizardEvent.SubmitFailed(message = "submit boom")),
                        recorder.events,
                    )
                }
            }
        }
}
