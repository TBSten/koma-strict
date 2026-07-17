package me.tbsten.koma.strict.ksp.feature.storeSpec.scenario

import me.tbsten.koma.strict.ksp.testing.compile.SnapshotSource
import me.tbsten.koma.strict.ksp.testing.scenario.SnapshotScenario
import org.intellij.lang.annotations.Language

// doc/internal/samples.md ケース「認証 + セッション切れ」の忠実写経 (StoreSpecUseCasesTest 用)。
// (root @OnRecover の nextState qualify は samples.md 自体が注記コメント付きで記載する
// ようになったため、写経どおり = 調整ではない)
//
// samples.md からの調整点:
// - package 宣言 (samples.auth) と import を追加 (samples.md は誌面上省略の前提のため)
// - 利用側が呼ぶ restoreSession() / authenticate() とセッション型 Session を
//   suspend スタブ (AuthStubs.kt) として追加

@Language("kotlin")
private val samplesAuthDeclaration =
    """
    package samples.auth

    import koma.core.Action
    import koma.core.Event
    import koma.core.State
    import me.tbsten.koma.strict.OnAction
    import me.tbsten.koma.strict.OnEnter
    import me.tbsten.koma.strict.OnExit
    import me.tbsten.koma.strict.OnRecover
    import me.tbsten.koma.strict.StoreSpec

    class SessionExpiredException : Exception()

    @StoreSpec(initial = [AuthState.CheckingSession::class])
    @OnRecover<SessionExpiredException>(   // root 共有 recover = 全 leaf に展開
        nextState = [AuthState.LoggedOut::class],   // root 注釈からは bare 名解決不可のため qualify
        emit = [AuthEvent.SessionExpired::class],
    )
    sealed interface AuthState : State {
        companion object

        @OnEnter(nextState = [LoggedIn::class, LoggedOut::class])
        data object CheckingSession : AuthState   // initial(到達不能分析の起点・図の [*] エッジ)

        @OnAction<AuthAction.Login>(nextState = [Authenticating::class])
        data object LoggedOut : AuthState

        @OnEnter(nextState = [LoggedIn::class, LoggedOut::class], emit = [AuthEvent.LoginFailed::class])
        @OnExit(emit = [AuthEvent.AuthAttemptFinished::class])   // 離脱時に必ず通知
        interface Authenticating : AuthState { val userName: String; companion object }

        @OnAction<AuthAction.Logout>(nextState = [LoggedOut::class])
        interface LoggedIn : AuthState { val userName: String; companion object }
    }

    sealed interface AuthAction : Action {
        data class Login(val userName: String, val password: String) : AuthAction
        data object Logout : AuthAction
    }

    sealed interface AuthEvent : Event {
        data object SessionExpired : AuthEvent
        data class LoginFailed(val message: String?) : AuthEvent
        data object AuthAttemptFinished : AuthEvent
    }
    """.trimIndent()

@Language("kotlin")
private val samplesAuthUsage =
    """
    package samples.auth

    import koma.core.Store

    val store = Store<AuthState, AuthAction, AuthEvent>(initialState = AuthState.CheckingSession) {
        states(
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
                    if (session != null) nextState.toLoggedIn(userName = session.userName)
                    else nextState.toLoggedOut()
                },
            ),
            loggedOut = AuthState.LoggedOut.actions(
                login = { nextState.toAuthenticating(userName = action.userName) },  // 入力の取り込みは明示
            ),
            authenticating = AuthState.Authenticating.actions(
                enter = {
                    runCatching { authenticate(state.userName) }.fold(
                        onSuccess = { nextState.toLoggedIn() },   // userName は持ち越し
                        onFailure = {
                            emitLoginFailed(it.message)
                            nextState.toLoggedOut()
                        },
                    )
                },
                exit = { emitAuthAttemptFinished() },   // 戻り値なし(遷移能力がない)
            ),
            loggedIn = AuthState.LoggedIn.actions(
                logout = { nextState.toLoggedOut() },
            ),
        )
    }
    """.trimIndent()

@Language("kotlin")
private val samplesAuthStubs =
    """
    package samples.auth

    // samples.md には現れないダミー実装 (利用側コードのコンパイル証明用)

    class Session(val userName: String)

    suspend fun restoreSession(): Session? = null

    suspend fun authenticate(userName: String) {
        if (userName.isBlank()) throw SessionExpiredException()
    }
    """.trimIndent()

/** samples.md「認証 + セッション切れ」: 宣言 + 利用側 + restoreSession / authenticate スタブ。 */
internal fun samplesAuthUseCase(): SnapshotScenario =
    SnapshotScenario(
        SnapshotSource(fileName = "AuthState.kt", code = samplesAuthDeclaration),
        SnapshotSource(fileName = "AuthStoreUsage.kt", code = samplesAuthUsage),
        SnapshotSource(fileName = "AuthStubs.kt", code = samplesAuthStubs),
    )
