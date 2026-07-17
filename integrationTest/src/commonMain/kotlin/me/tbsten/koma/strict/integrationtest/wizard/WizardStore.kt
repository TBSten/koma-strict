package me.tbsten.koma.strict.integrationtest.wizard

import koma.core.Store

// doc/internal/samples.md ケース「フォームウィザード」の利用側の 1:1 移植。
// submitForm はテストから成功 / 失敗を注入できるよう関数パラメータにしている。

/**
 * Builds the wizard sample store against the generated `states()` extension.
 *
 * @param submitForm Injected submitter so tests can drive the success / failure paths.
 * @param initialState Startup state. Defaults to the declared initial (`Step1`). koma-strict
 *   intentionally does not type-enforce the initial state, so tests can start mid-wizard.
 */
fun createWizardStore(
    submitForm: suspend (name: String, email: String) -> Unit,
    initialState: WizardState = WizardState.Step1(name = ""),
): Store<WizardState, WizardAction, WizardEvent> =
    Store<WizardState, WizardAction, WizardEvent>(initialState = initialState) {
        states(
            step1 = WizardState.Step1.actions(
                inputName = { nextState.toStep1(name = action.name) },
                next = {
                    if (state.name.isBlank()) {
                        emitValidationFailed(reason = "name is required")
                        stayState()
                    } else {
                        nextState.toStep2(email = "") // name は同名 prop から持ち越し。email は新規 = 必須
                    }
                },
            ),
            step2 = WizardState.Step2.actions(
                next = {
                    if ("@" !in state.email) {
                        emitValidationFailed(reason = "invalid email")
                        stayState()
                    } else {
                        nextState.toStep3() // name / email とも持ち越し
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
