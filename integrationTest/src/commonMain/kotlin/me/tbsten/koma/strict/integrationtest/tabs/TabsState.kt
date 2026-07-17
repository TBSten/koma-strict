package me.tbsten.koma.strict.integrationtest.tabs

import koma.core.State
import me.tbsten.koma.strict.OnAction
import me.tbsten.koma.strict.Stay
import me.tbsten.koma.strict.StoreSpec

// doc/internal/samples.md ケース「タブ切替」の宣言の 1:1 移植
// (Action は TabsAction.kt に分割。event なし → E = Nothing として生成)

@StoreSpec(initial = [TabsState.Home::class])
@OnAction<TabsAction.SelectTab>( // root 共有アクション = default ブロック
    // root 自身の annotation からは入れ子 state を bare 名で解決できないため qualify が必要
    nextState = [Stay::class, TabsState.Home::class, TabsState.Search::class, TabsState.Profile::class],
)
sealed interface TabsState : State {
    data object Home : TabsState // data object 宣言(従来通り可)

    @OnAction<TabsAction.UpdateQuery>(nextState = [Search::class]) // 自己遷移
    interface Search : TabsState { val query: String; companion object }

    data object Profile : TabsState // 宣言ゼロ → facade に引数が生えない

    // companion object の直後に修飾子付き宣言(data object 等)を置くと kotlinc の
    // parser quirk で companion が壊れる(koma-strict が警告診断を出す)ため末尾に置く
    companion object
}
