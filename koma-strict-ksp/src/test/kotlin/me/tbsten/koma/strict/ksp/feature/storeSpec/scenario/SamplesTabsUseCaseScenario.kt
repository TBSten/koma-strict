package me.tbsten.koma.strict.ksp.feature.storeSpec.scenario

import me.tbsten.koma.strict.ksp.testing.compile.SnapshotSource
import me.tbsten.koma.strict.ksp.testing.scenario.SnapshotScenario
import org.intellij.lang.annotations.Language

// doc/internal/samples.md ケース「タブ切替」の忠実写経 (StoreSpecUseCasesTest 用)。
// (root @OnAction の nextState qualify / companion object の末尾配置は samples.md 自体が
// 注記コメント付きで記載するようになったため、写経どおり = 調整ではない)
//
// samples.md からの調整点:
// - package 宣言 (samples.tabs) と import を追加 (samples.md は誌面上省略の前提のため)
// - 利用側の `Tab.Home` 等の bare 参照のため `TabsAction.SelectTab.Tab` の import を追加

@Language("kotlin")
private val samplesTabsDeclaration =
    """
    package samples.tabs

    import koma.core.Action
    import koma.core.State
    import me.tbsten.koma.strict.OnAction
    import me.tbsten.koma.strict.Stay
    import me.tbsten.koma.strict.StoreSpec

    @StoreSpec(initial = [TabsState.Home::class])
    @OnAction<TabsAction.SelectTab>(   // root 共有アクション = default ブロック
        // root 自身の annotation からは入れ子 state を bare 名で解決できないため qualify が必要
        nextState = [Stay::class, TabsState.Home::class, TabsState.Search::class, TabsState.Profile::class],
    )
    sealed interface TabsState : State {
        data object Home : TabsState                       // data object 宣言(従来通り可)

        @OnAction<TabsAction.UpdateQuery>(nextState = [Search::class])   // 自己遷移
        interface Search : TabsState { val query: String; companion object }

        data object Profile : TabsState                    // 宣言ゼロ → facade に引数が生えない

        // companion object の直後に修飾子付き宣言(data object 等)を置くと kotlinc の
        // parser quirk で companion が壊れる(koma-strict が警告診断を出す)ため末尾に置く
        companion object
    }

    sealed interface TabsAction : Action {
        data class SelectTab(val tab: Tab) : TabsAction {
            enum class Tab { Home, Search, Profile }
        }
        data class UpdateQuery(val query: String) : TabsAction
    }
    // event なし → E = Nothing として生成
    """.trimIndent()

@Language("kotlin")
private val samplesTabsUsage =
    """
    package samples.tabs

    import koma.core.Store
    import samples.tabs.TabsAction.SelectTab.Tab

    val store = Store<TabsState, TabsAction, Nothing>(initialState = TabsState.Home) {
        states(
            default = TabsState.actions(
                selectTab = {
                    when (action.tab) {
                        Tab.Home -> if (state is TabsState.Home) stayState() else nextState.toHome()
                        Tab.Search -> if (state is TabsState.Search) stayState() else nextState.toSearch(query = "")
                        Tab.Profile -> if (state is TabsState.Profile) stayState() else nextState.toProfile()
                    }
                },
            ),
            search = TabsState.Search.actions(
                updateQuery = { nextState.toSearch(query = action.query) },   // 入力の取り込みは明示(原則 2)
            ),
        )
    }
    """.trimIndent()

/** samples.md「タブ切替」: 宣言 + 利用側 (E = Nothing のためダミー実装は不要)。 */
internal fun samplesTabsUseCase(): SnapshotScenario =
    SnapshotScenario(
        SnapshotSource(fileName = "TabsState.kt", code = samplesTabsDeclaration),
        SnapshotSource(fileName = "TabsStoreUsage.kt", code = samplesTabsUsage),
    )
