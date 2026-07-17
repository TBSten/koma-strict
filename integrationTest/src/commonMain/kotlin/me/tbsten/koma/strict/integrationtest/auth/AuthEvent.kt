package me.tbsten.koma.strict.integrationtest.auth

import koma.core.Event

/** One-off events of the auth sample store. */
sealed interface AuthEvent : Event {
    data object SessionExpired : AuthEvent

    data class LoginFailed(val message: String?) : AuthEvent

    data object AuthAttemptFinished : AuthEvent
}
