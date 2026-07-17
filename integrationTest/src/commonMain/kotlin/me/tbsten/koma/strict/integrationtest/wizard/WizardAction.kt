package me.tbsten.koma.strict.integrationtest.wizard

import koma.core.Action

/** Actions of the wizard sample store. */
sealed interface WizardAction : Action {
    data class InputName(val name: String) : WizardAction

    data object Next : WizardAction

    data object Back : WizardAction

    data object Submit : WizardAction
}
