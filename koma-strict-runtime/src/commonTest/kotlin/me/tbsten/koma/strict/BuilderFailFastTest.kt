package me.tbsten.koma.strict

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 生成 builder の構築時 fail-fast ヘルパのメッセージ仕様 (SSoT はこの runtime 側)。
 * 生成コードは owner / entry 名を渡すだけで、文言はここで固定される。
 */
class BuilderFailFastTest {
    @Test
    fun `重複登録メッセージはownerとentryを含みactionableである`() {
        val exception =
            assertFailsWith<IllegalStateException> {
                throwDuplicateBuilderEntry(owner = "LceState.Content", entry = "reload")
            }
        assertEquals(
            "koma-strict builder for LceState.Content: 'reload' is already registered. " +
                "Register each declared handler / child state exactly once in the builder block.",
            exception.message,
        )
    }

    @Test
    fun `不足メッセージは不足entryを宣言順に列挙しnamed形式への誘導を含む`() {
        val exception =
            assertFailsWith<IllegalStateException> {
                throwMissingBuilderEntries(
                    owner = "FeedState.Stable",
                    missing = listOf("refreshing", "loadingMore"),
                )
            }
        assertEquals(
            "koma-strict builder for FeedState.Stable: missing 'refreshing', 'loadingMore'. " +
                "The builder form (actions { ... } / states { ... }) checks exhaustiveness only at build time; " +
                "the named-argument form turns missing handlers into a compile-time error.",
            exception.message,
        )
    }

    @Test
    fun `default名つきownerの不足メッセージも同形式になる`() {
        val exception =
            assertFailsWith<IllegalStateException> {
                throwMissingBuilderEntries(owner = "FlowState.Refresh.refreshCommon", missing = listOf("cancel"))
            }
        assertEquals(
            "koma-strict builder for FlowState.Refresh.refreshCommon: missing 'cancel'. " +
                "The builder form (actions { ... } / states { ... }) checks exhaustiveness only at build time; " +
                "the named-argument form turns missing handlers into a compile-time error.",
            exception.message,
        )
    }
}
