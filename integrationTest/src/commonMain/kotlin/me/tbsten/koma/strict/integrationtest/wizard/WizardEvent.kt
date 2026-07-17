package me.tbsten.koma.strict.integrationtest.wizard

import koma.core.Event

/** One-off events of the wizard sample store. */
sealed interface WizardEvent : Event {
    data class ValidationFailed(val reason: String) : WizardEvent

    data class SubmitFailed(val message: String?) : WizardEvent
}
