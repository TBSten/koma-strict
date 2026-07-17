package me.tbsten.koma.strict.integrationtest.lce

import koma.core.Action

/** Actions of the LCE sample store. */
sealed interface LceAction : Action {
    data object Reload : LceAction

    data object Retry : LceAction
}
