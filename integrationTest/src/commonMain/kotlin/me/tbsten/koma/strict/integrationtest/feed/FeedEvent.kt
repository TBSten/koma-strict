package me.tbsten.koma.strict.integrationtest.feed

import koma.core.Event

/** One-off events of the feed sample store. */
sealed interface FeedEvent : Event {
    data class LoadFailed(val message: String?) : FeedEvent

    data class RefreshFailed(val message: String?) : FeedEvent

    data class LoadMoreFailed(val message: String?) : FeedEvent
}
