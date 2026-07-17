package me.tbsten.koma.strict.integrationtest.wizard

import koma.core.State
import me.tbsten.koma.strict.OnAction
import me.tbsten.koma.strict.OnEnter
import me.tbsten.koma.strict.Stay
import me.tbsten.koma.strict.StoreSpec

// doc/internal/samples.md ケース「フォームウィザード」の宣言の 1:1 移植
// (Action / Event は WizardAction.kt / WizardEvent.kt に分割)

@StoreSpec(initial = [WizardState.Step1::class])
sealed interface WizardState : State {
    companion object

    @OnAction<WizardAction.InputName>(nextState = [Step1::class]) // 自己遷移
    @OnAction<WizardAction.Next>(
        nextState = [Stay::class, Step2::class], // 検証 NG は stay
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

    interface Done : WizardState // 宣言ゼロ + companion なし → 引数も factory も生えない
}
