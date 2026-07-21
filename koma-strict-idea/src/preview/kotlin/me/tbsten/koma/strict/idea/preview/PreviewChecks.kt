package me.tbsten.koma.strict.idea.preview

import java.io.File
import javax.imageio.ImageIO

/**
 * Pure gates over the headless preview output, split out of [main] so they can be unit tested without
 * rendering anything (`ide-review.md` P2-15 alpha / P3-07 stale-file cleanup). Nothing here touches
 * Compose, so the plugin distribution never depends on it and the test source set can call it directly.
 * Public (not `internal`) so the separately-compiled test module can reach it.
 */
object PreviewChecks {
    // scenario の rename / remove で残った古い PNG が gallery に混ざらないよう、毎回作り直す対象。
    private fun isManaged(file: File): Boolean =
        file.isFile && ((file.name.startsWith("koma-") && file.name.endsWith(".png")) || file.name == "index.html")

    /** Deletes the managed preview artifacts (`koma-*.png` + `index.html`); other files are left alone. */
    fun cleanManagedOutput(outDir: File) {
        val managed = outDir.listFiles { file -> isManaged(file) } ?: return
        for (file in managed) file.delete()
    }

    /**
     * Names of `koma-*.png` under [outDir] whose four corner pixels are not fully opaque (`alpha < 255`),
     * sorted by name. A normal shot is painted over the Jewel panel background, so a transparent corner
     * means the shot would vanish into a dark viewer.
     */
    fun transparentCornerPngs(outDir: File): List<String> {
        val pngs = outDir.listFiles { file -> file.isFile && file.name.startsWith("koma-") && file.name.endsWith(".png") }
            ?: return emptyList()
        return pngs.sortedBy { it.name }.filter { hasTransparentCorner(it) }.map { it.name }
    }

    /** True if any of the four corner pixels of the PNG at [png] has `alpha < 255`. */
    fun hasTransparentCorner(png: File): Boolean {
        val image = ImageIO.read(png) ?: return false
        val maxX = image.width - 1
        val maxY = image.height - 1
        val corners = listOf(0 to 0, maxX to 0, 0 to maxY, maxX to maxY)
        return corners.any { (x, y) -> (image.getRGB(x, y) ushr 24 and 0xFF) < 255 }
    }
}
