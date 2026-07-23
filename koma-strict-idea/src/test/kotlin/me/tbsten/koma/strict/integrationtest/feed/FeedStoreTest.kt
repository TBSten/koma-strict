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

    }
}