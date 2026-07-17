package me.tbsten.koma.strict.integrationtest.lce

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Build-time fail-fast behavior of the generated builder form (`actions { ... }`) against
 * the real generated code: missing and duplicate registrations throw [IllegalStateException]
 * with the actionable message (owner + entries + the named-argument hint).
 */
class LceBuilderFailFastTest {
    @Test
    fun `builderで宣言済みhandlerを登録しないと構築時にIllegalStateExceptionになる`() {
        // 網羅チェックは構築時 (builder ブロック終了時)。store どころか値の構築時点で fail-fast する
        val exception =
            assertFailsWith<IllegalStateException> {
                LceState.Content.actions { }
            }
        // メッセージは actionable: どの state のどの handler が不足か + named 形式ならコンパイル時に検出できる旨
        assertEquals(
            "koma-strict builder for LceState.Content: missing 'reload'. " +
                "The builder form (actions { ... } / states { ... }) checks exhaustiveness only at build time; " +
                "the named-argument form turns missing handlers into a compile-time error.",
            exception.message,
        )
    }

    @Test
    fun `builderで同じhandlerを二重登録すると即IllegalStateExceptionになる`() {
        val exception =
            assertFailsWith<IllegalStateException> {
                LceState.Content.actions {
                    reload { nextState.toLoading() }
                    reload { nextState.toLoading() }
                }
            }
        assertEquals(
            "koma-strict builder for LceState.Content: 'reload' is already registered. " +
                "Register each declared handler / child state exactly once in the builder block.",
            exception.message,
        )
    }

    @Test
    fun `builderでconfigureを二重登録すると即IllegalStateExceptionになる`() {
        val exception =
            assertFailsWith<IllegalStateException> {
                LceState.Content.actions {
                    reload { nextState.toLoading() }
                    configure { }
                    configure { }
                }
            }
        assertEquals(
            "koma-strict builder for LceState.Content: 'configure' is already registered. " +
                "Register each declared handler / child state exactly once in the builder block.",
            exception.message,
        )
    }
}
