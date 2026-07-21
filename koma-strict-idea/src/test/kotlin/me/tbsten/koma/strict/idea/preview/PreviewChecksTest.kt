package me.tbsten.koma.strict.idea.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Unit test for the pure preview-output gates ([PreviewChecks]) behind `ide-review.md` P2-15 (every
 * normal shot must have opaque corners) and P3-07 (stale artifacts are cleaned before regenerating).
 * It renders nothing — it fabricates tiny PNGs with ImageIO and asserts the checks directly.
 */
class PreviewChecksTest {
    @get:Rule
    val tmp = TemporaryFolder()

    // 8x8 の PNG を書き出す。transparentCorner=true なら右下角だけ完全透明にする。
    private fun writePng(name: String, transparentCorner: Boolean): File {
        val type = if (transparentCorner) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB
        val image = BufferedImage(8, 8, type)
        val g = image.createGraphics()
        g.color = Color(0x30, 0x35, 0x40)
        g.fillRect(0, 0, 8, 8)
        g.dispose()
        if (transparentCorner) image.setRGB(7, 7, 0x00000000)
        val file = File(tmp.root, name)
        ImageIO.write(image, "png", file)
        return file
    }

    @Test
    fun `四隅が不透明な PNG は透明角として検出されない`() {
        val opaque = writePng("koma-opaque-light.png", transparentCorner = false)
        assertFalse("全隅 alpha=255 の PNG は透明扱いされない", PreviewChecks.hasTransparentCorner(opaque))
    }

    @Test
    fun `角が透明な PNG は透明角として検出される`() {
        val transparent = writePng("koma-holed-light.png", transparentCorner = true)
        assertTrue("いずれかの隅 alpha<255 の PNG は透明として検出される", PreviewChecks.hasTransparentCorner(transparent))
    }

    @Test
    fun `ディレクトリ走査は透明な角を持つ koma png の名前だけ返す`() {
        writePng("koma-opaque-light.png", transparentCorner = false)
        writePng("koma-holed-dark.png", transparentCorner = true)
        assertEquals(listOf("koma-holed-dark.png"), PreviewChecks.transparentCornerPngs(tmp.root))
    }

    @Test
    fun `クリーンは管理下の生成物だけ消し他のファイルは残す`() {
        val managedPng = writePng("koma-lce-light.png", transparentCorner = false)
        val managedHtml = File(tmp.root, "index.html").apply { writeText("<html></html>") }
        val foreignPng = File(tmp.root, "screenshot.png").apply { writeText("x") }
        val foreignFile = File(tmp.root, "notes.txt").apply { writeText("keep") }

        PreviewChecks.cleanManagedOutput(tmp.root)

        assertFalse("koma-*.png は削除される", managedPng.exists())
        assertFalse("index.html は削除される", managedHtml.exists())
        assertTrue("koma- で始まらない png は残す", foreignPng.exists())
        assertTrue("生成物でない手書きファイルは残す", foreignFile.exists())
    }
}
