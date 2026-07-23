package me.tbsten.koma.strict.integrationtest.feed

import koma.test.dispatchAndAwait
import koma.test.startAndAwait
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class FeedStoreTest {
    @Test
    fun `recorded flow`() = runTest {
        val store: FeedStore = TODO("construct FeedStore with initialState = FeedState.Loading")
        store.startAndAwait()
        assertEquals(FeedState.Error(/* TODO: expected state props */), store.currentState)
        store.dispatchAndAwait(FeedAction.Retry)
        assertEquals(FeedState.Loading(/* TODO: expected state props */), store.currentState)
        assertEquals(FeedState.Stable.Idle(/* TODO: expected state props */), store.currentState)
        store.dispatchAndAwait(FeedAction.LoadMore)
        assertEquals(FeedState.Stable.Idle(/* TODO: expected state props */), store.currentState)
        store.dispatchAndAwait(FeedAction.LoadMore)
        assertEquals(FeedState.Stable.LoadingMore(/* TODO: expected state props */), store.currentState)
        assertEquals(FeedState.Stable.Idle(/* TODO: expected state props */), store.currentState)
        store.dispatchAndAwait(FeedAction.Refresh)
        assertEquals(FeedState.Stable.Refreshing(/* TODO: expected state props */), store.currentState)
        assertEquals(FeedState.Stable.Idle(/* TODO: expected state props */), store.currentState)
    }
}