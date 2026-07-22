package me.tbsten.koma.strict.integrationtest.auth

import koma.test.dispatchAndAwait
import koma.test.record
import koma.test.startAndAwait
import me.tbsten.koma.strict.integrationtest.runStoreTest
import me.tbsten.koma.strict.integrationtest.useStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Behavior tests for the auth sample store running on the real koma-core rc02.
 *
 * Gate A of spike (d): the generated `exit {}` / `recover<E> {}` compile-down targets the real
 * rc02 API here, and these tests exercise the runtime behavior of both hooks.
 */
class AuthStoreTest {
    // 認証は常に成功するスタブ (セッション無しから開始)
    private fun createStore(
        restoreSession: suspend () -> Session? = { null },
        authenticate: suspend (String) -> Unit = { },
    ) = createAuthStore(restoreSession = restoreSession, authenticate = authenticate)

    @Test
    fun `セッションが有効なら初期enterでLoggedInへ遷移する`() =
        runStoreTest {
            createStore(restoreSession = { Session(userName = "alice") }).useStore {
                startAndAwait()
                assertEquals(AuthState.LoggedIn(userName = "alice"), currentState)
            }
        }

    @Test
    fun `セッションが無ければ初期enterでLoggedOutへ遷移する`() =
        runStoreTest {
            createStore(restoreSession = { null }).useStore {
                startAndAwait()
                assertEquals(AuthState.LoggedOut(), currentState)
            }
        }

    @Test
    fun `loginでAuthenticatingを経てuserNameを持ち越したLoggedInへ遷移する`() =
        runStoreTest {
            createStore().useStore {
                record { recorder ->
                    startAndAwait()
                    dispatchAndAwait(AuthAction.Login(userName = "alice", password = "pw"))
                    // userName は Authenticating → LoggedIn で同名 prop から持ち越し
                    assertEquals(AuthState.LoggedIn(userName = "alice"), currentState)
                    // 中間 state (Authenticating) を通過したことは recorder で検証できる
                    assertTrue(recorder.states.any { it is AuthState.Authenticating })
                }
            }
        }

    @Test
    fun `Authenticatingからの離脱時にexitが発火しAuthAttemptFinishedをemitする`() =
        runStoreTest {
            createStore(authenticate = { error("wrong password") }).useStore {
                record { recorder ->
                    startAndAwait()
                    dispatchAndAwait(AuthAction.Login(userName = "alice", password = "pw"))
                    assertEquals(AuthState.LoggedOut(), currentState)
                    // enter 内の emit (LoginFailed) → 離脱時の exit emit (AuthAttemptFinished) の順
                    assertEquals(
                        listOf(
                            AuthEvent.LoginFailed(message = "wrong password"),
                            AuthEvent.AuthAttemptFinished,
                        ),
                        recorder.events,
                    )
                }
            }
        }

    @Test
    fun `login成功時もAuthenticatingからの離脱でexitが発火する`() =
        runStoreTest {
            createStore().useStore {
                record { recorder ->
                    startAndAwait()
                    dispatchAndAwait(AuthAction.Login(userName = "alice", password = "pw"))
                    assertEquals(listOf<AuthEvent>(AuthEvent.AuthAttemptFinished), recorder.events)
                }
            }
        }

    @Test
    fun `SessionExpiredExceptionをroot共有のrecoverが拾いSessionExpiredをemitしてLoggedOutへ遷移する`() =
        runStoreTest {
            createStore(restoreSession = { throw SessionExpiredException() }).useStore {
                record { recorder ->
                    startAndAwait() // CheckingSession の enter が投げ、recover が同期チェーンで処理する
                    assertEquals(AuthState.LoggedOut(), currentState)
                    assertEquals(listOf<AuthEvent>(AuthEvent.SessionExpired), recorder.events)
                }
            }
        }

    @Test
    fun `logoutでLoggedOutへ遷移する`() =
        runStoreTest {
            createStore(restoreSession = { Session(userName = "alice") }).useStore {
                startAndAwait()
                dispatchAndAwait(AuthAction.Logout)
                assertEquals(AuthState.LoggedOut(), currentState)
            }
        }
}
