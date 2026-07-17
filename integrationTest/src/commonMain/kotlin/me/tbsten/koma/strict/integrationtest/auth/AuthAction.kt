package me.tbsten.koma.strict.integrationtest.auth

import koma.core.Action

/** Actions of the auth sample store. */
sealed interface AuthAction : Action {
    data class Login(val userName: String, val password: String) : AuthAction

    data object Logout : AuthAction
}
