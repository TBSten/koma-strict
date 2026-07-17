package me.tbsten.koma.strict.integrationtest.auth

import koma.core.State
import me.tbsten.koma.strict.OnAction
import me.tbsten.koma.strict.OnEnter
import me.tbsten.koma.strict.OnExit
import me.tbsten.koma.strict.OnRecover
import me.tbsten.koma.strict.StoreSpec

// doc/internal/samples.md ケース「認証 + セッション切れ」の宣言の 1:1 移植
// (ゲート A: @OnExit / @OnRecover の生成コードが実物 koma rc02 でコンパイルできるかの実測対象。
// Action / Event / 例外は AuthAction.kt / AuthEvent.kt / SessionExpiredException.kt に分割)
//
// internal root: 支援型・factory・states() 拡張が internal に継承されることの実機確認も兼ねる

@StoreSpec(initial = [AuthState.CheckingSession::class])
@OnRecover<SessionExpiredException>( // root 共有 recover = 全 leaf に展開
    nextState = [AuthState.LoggedOut::class], // root 注釈からは bare 名解決不可のため qualify
    emit = [AuthEvent.SessionExpired::class],
)
internal sealed interface AuthState : State {
    companion object

    @OnEnter(nextState = [LoggedIn::class, LoggedOut::class])
    data object CheckingSession : AuthState // initial(到達不能分析の起点)

    @OnAction<AuthAction.Login>(nextState = [Authenticating::class])
    data object LoggedOut : AuthState

    @OnEnter(nextState = [LoggedIn::class, LoggedOut::class], emit = [AuthEvent.LoginFailed::class])
    @OnExit(emit = [AuthEvent.AuthAttemptFinished::class]) // 離脱時に必ず通知
    interface Authenticating : AuthState { val userName: String; companion object }

    @OnAction<AuthAction.Logout>(nextState = [LoggedOut::class])
    interface LoggedIn : AuthState { val userName: String; companion object }
}
