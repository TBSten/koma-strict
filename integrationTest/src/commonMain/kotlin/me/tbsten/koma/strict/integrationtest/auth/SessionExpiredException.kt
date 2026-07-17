package me.tbsten.koma.strict.integrationtest.auth

/**
 * Thrown when the session has expired. Caught by the root `@OnRecover` of [AuthState].
 */
class SessionExpiredException : Exception()
