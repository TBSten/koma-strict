package me.tbsten.koma.strict.integrationtest.lce

import koma.core.Event

/** One-off events of the LCE sample store. */
sealed interface LceEvent : Event {
    data class LoadFailed(val message: String?) : LceEvent
}
