package me.tbsten.koma.strict.integrationtest.tabs

import koma.core.Store
import me.tbsten.koma.strict.integrationtest.tabs.TabsAction.SelectTab.Tab

// doc/internal/samples.md ケース「タブ切替」の利用側の移植 (E = Nothing・注入パラメータなし)。
// samples.md の値渡し形式から意図的に旧 scope lambda 形式 (`default = { actions(...) }`) に
// 書き換えてある — 両対応 (値渡し / scope lambda) の scope lambda 側の実機検証を担う
// (値渡し側は他ケースが担う)。

/** Builds the tabs sample store (root shared action + E = Nothing), written in the scope-lambda form. */
fun createTabsStore(): Store<TabsState, TabsAction, Nothing> =
    Store<TabsState, TabsAction, Nothing>(initialState = TabsState.Home) {
        states(
            default = {
                // DefaultHandlersScope の actions() ミラー (companion 拡張と同シグネチャ)
                actions(
                    selectTab = {
                        when (action.tab) {
                            Tab.Home -> if (state is TabsState.Home) stayState() else nextState.toHome()
                            Tab.Search -> if (state is TabsState.Search) stayState() else nextState.toSearch(query = "")
                            Tab.Profile -> if (state is TabsState.Profile) stayState() else nextState.toProfile()
                        }
                    },
                )
            },
            search = {
                actions(
                    updateQuery = { nextState.toSearch(query = action.query) }, // 入力の取り込みは明示(原則 2)
                )
            },
        )
    }
