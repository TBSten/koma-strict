package me.tbsten.koma.strict.integrationtest.auth

import koma.core.Store

// doc/internal/samples.md ケース「認証 + セッション切れ」の利用側の 1:1 移植。
// restoreSession / authenticate はテストから成功 / 失敗 / 例外を注入できるよう関数パラメータにしている。

/** Restored session returned by the injected session loader. */
class Session(val userName: String)

/**
 * Builds the auth sample store against the generated `states()` extension.
 *
 * @param restoreSession Injected session loader. Throwing [SessionExpiredException] exercises
 *   the root `@OnRecover` handler.
 * @param authenticate Injected authenticator so tests can drive success / failure paths.
 */
internal fun createAuthStore(
    restoreSession: suspend () -> Session?,
    authenticate: suspend (userName: String) -> Unit,
): Store<AuthState, AuthAction, AuthEvent> =
    authStore(
        initialState = AuthState.CheckingSession(),
        default = AuthState.actions(
            // param 名は recover{Exception}(仮確定)。scope には error: SessionExpiredException
            recoverSessionExpiredException = {
                emitSessionExpired()
                nextState.toLoggedOut()
            },
        ),
        checkingSession = AuthState.CheckingSession.actions(
            enter = {
                // restoreSession() が SessionExpiredException を投げたら root の @OnRecover が拾う
                val session = restoreSession()
                if (session != null) {
                    nextState.toLoggedIn(userName = session.userName)
                } else {
                    nextState.toLoggedOut()
                }
            },
        ),
        loggedOut = AuthState.LoggedOut.actions(
            login = { nextState.toAuthenticating(userName = action.userName) }, // 入力の取り込みは明示
        ),
        authenticating = AuthState.Authenticating.actions(
            enter = {
                runCatching { authenticate(state.userName) }.fold(
                    onSuccess = { nextState.toLoggedIn() }, // userName は持ち越し
                    onFailure = {
                        emitLoginFailed(it.message)
                        nextState.toLoggedOut()
                    },
                )
            },
            exit = { emitAuthAttemptFinished() }, // 戻り値なし(遷移能力がない)
        ),
        loggedIn = AuthState.LoggedIn.actions(
            logout = { nextState.toLoggedOut() },
        ),
    )
