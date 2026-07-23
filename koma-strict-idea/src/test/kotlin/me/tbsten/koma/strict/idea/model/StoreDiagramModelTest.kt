package me.tbsten.koma.strict.idea.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** Store-name derivation used for the "Copy image" file name (`<Store>.png`). */
class StoreDiagramModelTest {

    @Test
    fun `storeName strips the State suffix and appends Store`() {
        assertEquals("FeedStore", storeNameOf("FeedState"))
        assertEquals("AuthStore", storeNameOf("AuthState"))
        // State で終わらない root はそのまま + Store。
        assertEquals("TabsStore", storeNameOf("Tabs"))
    }

    @Test
    fun `model exposes storeName from its root`() {
        val model = StoreDiagramModel(root = RootState("FeedState", emptyList()))
        assertEquals("FeedStore", model.storeName)
    }
}
