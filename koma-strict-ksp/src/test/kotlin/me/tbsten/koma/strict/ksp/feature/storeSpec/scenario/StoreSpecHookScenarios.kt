package me.tbsten.koma.strict.ksp.feature.storeSpec.scenario

import me.tbsten.koma.strict.ksp.testing.compile.SnapshotSource

// フック系ケース: @OnExit / @OnRecover (samples.md ケース 5)、死にアクション診断。

/** samples.md ケース 5: root 共有の @OnRecover / @OnExit / data object への handler 宣言。 */
internal fun authScenarioSource(): SnapshotSource =
    SnapshotSource(
        fileName = "AuthState.kt",
        code =
            """
            package example.auth

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
                // Note: root 自身に付く annotation からは入れ子 state を bare 名で参照できない
                nextState = [AuthState.LoggedOut::class],
                emit = [AuthEvent.SessionExpired::class],
            )
            sealed interface AuthState : State {
                companion object

                @OnEnter(nextState = [LoggedIn::class, LoggedOut::class])
                data object CheckingSession : AuthState   // initial (到達不能分析の起点)

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
            """.trimIndent(),
    )

/**
 * samples.md ケース 5 の利用側コード。root 共有 recover の named param
 * (`recoverSessionExpiredException`、Scope の `error` アクセス込み) と exit handler
 * (`exit = { ... }`) が「利用側から書ける」ことのコンパイル証明。
 */
internal fun authUsageSource(): SnapshotSource =
    SnapshotSource(
        fileName = "AuthStoreUsage.kt",
        code =
            """
            package example.auth

            import koma.core.Store

            private fun findSessionUser(): String? = null

            fun buildAuthStore(): Store<AuthState, AuthAction, AuthEvent> =
                Store<AuthState, AuthAction, AuthEvent>(initialState = AuthState.CheckingSession) {
                    states(
                        default = AuthState.actions(                   // root 共有 recover = default ブロック
                            recoverSessionExpiredException = {
                                if (error.message != null) {           // 捕捉した例外は error で読める
                                    emitSessionExpired()
                                }
                                nextState.toLoggedOut()                // 宣言済み遷移のみ呼べる
                            },
                        ),
                        checkingSession = AuthState.CheckingSession.actions(
                            enter = {
                                val userName = findSessionUser()
                                if (userName != null) nextState.toLoggedIn(userName = userName) else nextState.toLoggedOut()
                            },
                        ),
                        loggedOut = AuthState.LoggedOut.actions(
                            login = { nextState.toAuthenticating(userName = action.userName) },
                        ),
                        authenticating = AuthState.Authenticating.actions(
                            enter = {
                                runCatching { state.userName }.fold(
                                    onSuccess = { nextState.toLoggedIn(userName = it) },
                                    onFailure = {
                                        emitLoginFailed(it.message)
                                        nextState.toLoggedOut()
                                    },
                                )
                            },
                            exit = { emitAuthAttemptFinished() },      // 離脱時に必ず通知 (遷移能力なし)
                        ),
                        loggedIn = AuthState.LoggedIn.actions(
                            logout = { nextState.toLoggedOut() },
                        ),
                    )
                }
            """.trimIndent(),
    )

/**
 * 死にアクション (どの state もハンドルしないアクション) を含む宣言。
 * options 直積で deadActionSeverity=WARNING (既定) と ERROR の挙動差が golden に出る。
 */
internal fun deadActionScenarioSource(): SnapshotSource =
    SnapshotSource(
        fileName = "DeadState.kt",
        code =
            """
            package example.dead

            import koma.core.Action
            import koma.core.State
            import me.tbsten.koma.strict.OnAction
            import me.tbsten.koma.strict.StoreSpec

            @StoreSpec
            sealed interface DeadState : State {
                companion object

                @OnAction<DeadAction.Used>(nextState = [Done::class])
                interface Idle : DeadState { companion object }

                interface Done : DeadState { companion object }
            }

            sealed interface DeadAction : Action {
                data object Used : DeadAction
                data object Unused : DeadAction   // どの state もハンドルしない
            }
            """.trimIndent(),
    )
