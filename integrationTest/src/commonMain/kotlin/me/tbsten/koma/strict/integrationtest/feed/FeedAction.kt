package me.tbsten.koma.strict.integrationtest.feed

import koma.core.Action

/** Actions of the feed sample store. */
sealed interface FeedAction : Action {
    data object Refresh : FeedAction

    data object LoadMore : FeedAction

    data object Retry : FeedAction
}
