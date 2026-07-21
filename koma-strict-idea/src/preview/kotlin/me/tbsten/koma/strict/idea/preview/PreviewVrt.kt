package me.tbsten.koma.strict.idea.preview

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** Status of one rendered preview PNG compared against its golden counterpart. */
enum class VrtStatus {
    /** Pixel-identical to the golden. */
    Unchanged,

    /** Pixel content differs from the golden (a size mismatch also counts as changed). */
    Changed,

    /** Rendered now but no golden exists yet. */
    New,

    /** A golden exists but the scenario is no longer rendered. */
    Missing,
}

/**
 * Comparison result for a single preview PNG (`koma-<scenario>-<theme>.png`).
 *
 * Pixels are compared on the union canvas `max(width) x max(height)`, so any pixel covered by only
 * one of the two images counts as different. [diffPixelCount] / [comparedPixelCount] are only
 * meaningful when both images exist ([VrtStatus.Unchanged] / [VrtStatus.Changed]) and are `0`
 * otherwise. Dimensions are `null` for the side that does not exist ([VrtStatus.New] has no golden,
 * [VrtStatus.Missing] has no actual).
 */
data class VrtResult(
    val name: String,
    val status: VrtStatus,
    val diffPixelCount: Long,
    val comparedPixelCount: Long,
    val actualWidth: Int?,
    val actualHeight: Int?,
    val goldenWidth: Int?,
    val goldenHeight: Int?,
) {
    /** True when both images exist but their dimensions differ (always reported as [VrtStatus.Changed]). */
    val sizeMismatch: Boolean
        get() = actualWidth != null && goldenWidth != null &&
            (actualWidth != goldenWidth || actualHeight != goldenHeight)
}

/** Golden-directory sync summary returned by [PreviewVrt.updateGoldens]. */
data class GoldenSync(val copied: Int, val removed: Int)

/**
 * Pure-JVM (javax.imageio / [BufferedImage]) visual-regression engine for the headless preview.
 *
 * Rendering is deterministic on a given machine, so the golden PNGs under `snapshots/preview` can be
 * compared byte-for-byte pixel-by-pixel. Nothing here touches Compose (same policy as
 * [PreviewChecks]); public so the separately-compiled test module could reach it.
 */
object PreviewVrt {
    // 相違ピクセルの強調色 (マゼンタ・不透明)。
    private val DIFF_HIGHLIGHT = 0xFFFF00FF.toInt()

    // 一致ピクセルのグレースケール半透明化に使う alpha。
    private const val UNCHANGED_ALPHA = 0x55

    /**
     * Compares every `koma-*.png` in [actualDir] against [goldenDir] and materialises a
     * self-contained report under [reportDir]:
     *
     * - `diff/<name>.png` for each [VrtStatus.Changed] image (matching pixels greyed out and made
     *   translucent, differing pixels highlighted in magenta),
     * - `actual/` and `golden/` copies of every non-[VrtStatus.Unchanged] image,
     * - `index.html` via [writeVerifyReport].
     *
     * [reportDir] is recreated from scratch on every run. Returns one [VrtResult] per union file
     * name, sorted by name.
     */
    fun verify(actualDir: File, goldenDir: File, reportDir: File): List<VrtResult> {
        // 前回の検証結果が混ざらないよう report はまっさらに作り直す。
        reportDir.deleteRecursively()
        val diffDir = File(reportDir, "diff").apply { mkdirs() }
        val actualCopyDir = File(reportDir, "actual").apply { mkdirs() }
        val goldenCopyDir = File(reportDir, "golden").apply { mkdirs() }

        val names = (previewPngNames(actualDir) + previewPngNames(goldenDir)).toSortedSet()
        val results = names.map { name ->
            val actualFile = File(actualDir, name)
            val goldenFile = File(goldenDir, name)
            when {
                !goldenFile.isFile -> {
                    val actual = ImageIO.read(actualFile)
                    VrtResult(name, VrtStatus.New, 0, 0, actual.width, actual.height, null, null)
                }
                !actualFile.isFile -> {
                    val golden = ImageIO.read(goldenFile)
                    VrtResult(name, VrtStatus.Missing, 0, 0, null, null, golden.width, golden.height)
                }
                else -> compare(name, actualFile, goldenFile, diffDir)
            }
        }

        // Changed/New/Missing の該当 PNG だけコピーして、report ディレクトリ単体で内容を追えるようにする。
        for (result in results) {
            if (result.status == VrtStatus.Unchanged) continue
            File(actualDir, result.name).takeIf { it.isFile }
                ?.copyTo(File(actualCopyDir, result.name), overwrite = true)
            File(goldenDir, result.name).takeIf { it.isFile }
                ?.copyTo(File(goldenCopyDir, result.name), overwrite = true)
        }

        writeVerifyReport(results, reportDir)
        return results
    }

    /**
     * Force-syncs [goldenDir] to the freshly rendered PNGs in [actualDir]: every rendered
     * `koma-*.png` is copied over (add + overwrite) and goldens whose scenario is no longer
     * rendered are deleted.
     */
    fun updateGoldens(actualDir: File, goldenDir: File): GoldenSync {
        goldenDir.mkdirs()
        val rendered = previewPngNames(actualDir).toSortedSet()
        for (name in rendered) {
            File(actualDir, name).copyTo(File(goldenDir, name), overwrite = true)
        }
        val stale = previewPngNames(goldenDir).filterNot { it in rendered }
        for (name in stale) File(goldenDir, name).delete()
        return GoldenSync(copied = rendered.size, removed = stale.size)
    }

    /** Compares two existing PNGs; writes `diffDir/<name>.png` when they differ. */
    private fun compare(name: String, actualFile: File, goldenFile: File, diffDir: File): VrtResult {
        val actual = ImageIO.read(actualFile)
        val golden = ImageIO.read(goldenFile)
        val width = maxOf(actual.width, golden.width)
        val height = maxOf(actual.height, golden.height)
        val total = width.toLong() * height

        // getRGB をピクセル毎に呼ぶと遅いので一括で ARGB 配列に読む。
        val actualPixels = argbPixels(actual)
        val goldenPixels = argbPixels(golden)

        val diff = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        var diffCount = 0L
        for (y in 0 until height) {
            for (x in 0 until width) {
                val a = pixelAt(actualPixels, actual, x, y)
                val g = pixelAt(goldenPixels, golden, x, y)
                if (a != null && g != null && a == g) {
                    diff.setRGB(x, y, grayscale(a))
                } else {
                    // 片方にしか無い領域 (サイズ違い) も相違ピクセルとして数える。
                    diffCount++
                    diff.setRGB(x, y, DIFF_HIGHLIGHT)
                }
            }
        }

        if (diffCount == 0L) {
            return VrtResult(name, VrtStatus.Unchanged, 0, total, actual.width, actual.height, golden.width, golden.height)
        }
        ImageIO.write(diff, "png", File(diffDir, name))
        return VrtResult(name, VrtStatus.Changed, diffCount, total, actual.width, actual.height, golden.width, golden.height)
    }

    private fun previewPngNames(dir: File): List<String> =
        (dir.listFiles() ?: emptyArray())
            .filter { it.isFile && it.name.startsWith("koma-") && it.name.endsWith(".png") }
            .map { it.name }

    // 範囲外 (union キャンバスのうち画像が無い領域) は null。
    private fun pixelAt(pixels: IntArray, image: BufferedImage, x: Int, y: Int): Int? =
        if (x < image.width && y < image.height) pixels[y * image.width + x] else null

    private fun argbPixels(image: BufferedImage): IntArray =
        image.getRGB(0, 0, image.width, image.height, null, 0, image.width)

    // 一致ピクセル: 輝度でグレースケール化し半透明にする (相違箇所のマゼンタを際立たせる)。
    private fun grayscale(argb: Int): Int {
        val r = argb ushr 16 and 0xFF
        val g = argb ushr 8 and 0xFF
        val b = argb and 0xFF
        val lum = (r * 299 + g * 587 + b * 114) / 1000
        return (UNCHANGED_ALPHA shl 24) or (lum shl 16) or (lum shl 8) or lum
    }
}
