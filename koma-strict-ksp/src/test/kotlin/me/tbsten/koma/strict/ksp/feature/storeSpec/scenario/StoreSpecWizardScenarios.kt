package me.tbsten.koma.strict.ksp.feature.storeSpec.scenario

import me.tbsten.koma.strict.ksp.testing.compile.SnapshotSource
import org.intellij.lang.annotations.Language

// フォームウィザードケース (doc/internal/samples.md ケース 4) の宣言と利用側コード。
// stay + emit (バリデーション) / 自己遷移 / prop の必須・デフォルト混在 /
// 宣言ゼロ + companion なし leaf (Done) が見どころ。

@Language("kotlin")
private val wizardDeclaration =
    """
    package example.wizard

    import koma.core.Action
    import koma.core.Event
    import koma.core.State
    import me.tbsten.koma.strict.OnAction
    import me.tbsten.koma.strict.OnEnter
    import me.tbsten.koma.strict.Stay
    import me.tbsten.koma.strict.StoreSpec

    @StoreSpec(initial = [WizardState.Step1::class])
    sealed interface WizardState : State {
        companion object

        @OnAction<WizardAction.InputName>(nextState = [Step1::class])  // 自己遷移
        @OnAction<WizardAction.Next>(
            nextState = [Stay::class, Step2::class],                   // 検証 NG は stay
            emit = [WizardEvent.ValidationFailed::class],
        )
        interface Step1 : WizardState { val name: String; companion object }

        @OnAction<WizardAction.Next>(
            nextState = [Stay::class, Step3::class],
            emit = [WizardEvent.ValidationFailed::class],
        )
        @OnAction<WizardAction.Back>(nextState = [Step1::class])
        interface Step2 : WizardState { val name: String; val email: String; companion object }

        @OnAction<WizardAction.Submit>(nextState = [Submitting::class])
        @OnAction<WizardAction.Back>(nextState = [Step2::class])
        interface Step3 : WizardState { val name: String; val email: String; companion object }

        @OnEnter(nextState = [Done::class, Step3::class], emit = [WizardEvent.SubmitFailed::class])
        interface Submitting : WizardState { val name: String; val email: String; companion object }

        interface Done : WizardState   // 宣言ゼロ + companion なし -> 引数も factory も生えない
    }

    sealed interface WizardAction : Action {
        data class InputName(val name: String) : WizardAction
        data object Next : WizardAction
        data object Back : WizardAction
        data object Submit : WizardAction
    }

    sealed interface WizardEvent : Event {
        data class ValidationFailed(val reason: String) : WizardEvent
        data class SubmitFailed(val message: String?) : WizardEvent
    }
    """.trimIndent()

/** samples.md ケース 4 の宣言。 */
internal fun wizardScenarioSource(): SnapshotSource = SnapshotSource(fileName = "WizardState.kt", code = wizardDeclaration)

/**
 * samples.md ケース 4 の利用側コード。stay + emit / 自己遷移 / 必須 (email) と
 * デフォルト (name 持ち越し) 混在の factory 引数が「利用側から書ける」ことのコンパイル証明。
 */
internal fun wizardUsageSource(): SnapshotSource =
    SnapshotSource(
        fileName = "WizardStoreUsage.kt",
        code =
            """
            package example.wizard

            import koma.core.Store

            private fun submitForm(name: String, email: String): Boolean = name.isNotBlank() && email.isNotBlank()

            fun buildWizardStore(): Store<WizardState, WizardAction, WizardEvent> =
                Store<WizardState, WizardAction, WizardEvent>(initialState = WizardState.Step1(name = "")) {
                    states(
                        step1 = WizardState.Step1.actions(
                            inputName = { nextState.toStep1(name = action.name) },
                            next = {
                                if (state.name.isBlank()) {
                                    emitValidationFailed(reason = "name is required")
                                    stayState()
                                } else {
                                    nextState.toStep2(email = "")   // name は同名 prop から持ち越し。email は新規 = 必須
                                }
                            },
                        ),
                        step2 = WizardState.Step2.actions(
                            next = {
                                if ("@" !in state.email) {
                                    emitValidationFailed(reason = "invalid email")
                                    stayState()
                                } else {
                                    nextState.toStep3()             // name / email とも持ち越し
                                }
                            },
                            back = { nextState.toStep1() },
                        ),
                        step3 = WizardState.Step3.actions(
                            submit = { nextState.toSubmitting() },
                            back = { nextState.toStep2() },
                        ),
                        submitting = WizardState.Submitting.actions(
                            enter = {
                                runCatching { submitForm(state.name, state.email) }.fold(
                                    onSuccess = { nextState.toDone() },
                                    onFailure = {
                                        emitSubmitFailed(it.message)
                                        nextState.toStep3()
                                    },
                                )
                            },
                        ),
                    )
                }
            """.trimIndent(),
    )
