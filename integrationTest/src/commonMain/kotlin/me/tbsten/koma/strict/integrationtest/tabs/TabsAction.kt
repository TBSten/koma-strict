package me.tbsten.koma.strict.integrationtest.tabs

import koma.core.Action

/** Actions of the tabs sample store. */
sealed interface TabsAction : Action {
    data class SelectTab(val tab: Tab) : TabsAction {
        enum class Tab { Home, Search, Profile }
    }

    data class UpdateQuery(val query: String) : TabsAction
}
