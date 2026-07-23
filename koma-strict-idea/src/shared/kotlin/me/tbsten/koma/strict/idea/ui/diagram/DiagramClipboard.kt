package me.tbsten.koma.strict.idea.ui.diagram

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import me.tbsten.koma.strict.idea.ir.DiagramGraph
import me.tbsten.koma.strict.idea.layout.GraphLayout
import java.awt.Image
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.math.ceil

/**
 * Renders the current diagram offscreen (the same [drawDiagram] pass the canvas uses, over an opaque
 * [DiagramColors.background] fill) and puts the result on the AWT system clipboard so it can be pasted
 * into chats / docs / issue trackers ("Copy image" in the header). Two flavors are offered: the image
 * itself ([DataFlavor.imageFlavor]) for inline paste, and — so a paste into a *directory* lands as
 * `<storeName>.png` rather than a generic "clipboard.png" — a one-element file list
 * ([DataFlavor.javaFileListFlavor]) backed by a temp PNG named [storeName]`.png`. Returns `true` on
 * success; a headless environment (the preview renderer) or any other clipboard / IO failure is
 * swallowed and reported as `false` — copying is a convenience, never worth crashing the tool window.
 */
internal fun copyDiagramImageToClipboard(
    graph: DiagramGraph,
    layout: GraphLayout,
    colors: DiagramColors,
    density: Density,
    layoutDirection: LayoutDirection,
    textMeasurer: TextMeasurer,
    storeName: String,
    focus: FocusSet? = null,
): Boolean = try {
    val image = renderDiagramImage(graph, layout, colors, density, layoutDirection, textMeasurer, focus)
    // ディレクトリへ貼ると <storeName>.png になるよう、その名前の temp PNG を file flavor で載せる。
    val file = writeTempPng(image, storeName)
    // ヘッドレス環境 (preview) では Toolkit / clipboard アクセスが例外を投げるので try 全体で握りつぶす。
    Toolkit.getDefaultToolkit().systemClipboard.setContents(DiagramImageTransferable(image, file), null)
    true
} catch (e: Exception) {
    false
}

/** Writes [image] to a temp file literally named `<storeName>.png` (its name becomes the pasted file name), or null. */
private fun writeTempPng(image: BufferedImage, storeName: String): File? = try {
    // 名前が貼り付け後のファイル名になるので、衝突しない専用の temp ディレクトリ内に正確な名前で置く。
    val dir = Files.createTempDirectory("koma-diagram").toFile().apply { deleteOnExit() }
    File(dir, "${sanitizeFileName(storeName)}.png").apply {
        ImageIO.write(image, "png", this)
        deleteOnExit()
    }
} catch (e: Exception) {
    null
}

/** Keeps only file-name-safe chars; falls back to `diagram` if nothing usable remains. */
private fun sanitizeFileName(name: String): String =
    name.filter { it.isLetterOrDigit() || it == '_' || it == '-' }.ifBlank { "diagram" }

/**
 * Draws [graph]/[layout] into a fresh [ImageBitmap] at the ambient [density] (100 % zoom) and converts
 * it to an AWT image. The bitmap is first filled with [DiagramColors.background] so the copied image is
 * opaque — a transparent PNG pasted on a dark surface would lose the light-theme labels (and vice
 * versa). A pathological layout bigger than [MAX_IMAGE_EXTENT_PX] px on either axis is scaled down to
 * fit (same spirit as [fitDiagram]) instead of allocating an absurd bitmap.
 */
private fun renderDiagramImage(
    graph: DiagramGraph,
    layout: GraphLayout,
    colors: DiagramColors,
    density: Density,
    layoutDirection: LayoutDirection,
    textMeasurer: TextMeasurer,
    focus: FocusSet? = null,
): BufferedImage {
    // 異常モデルでも巨大/非有限サイズで落ちないよう canvas と同じ流儀でクランプする (dp 単位)。
    val wDp = clampDp(layout.canvasSize.width)
    val hDp = clampDp(layout.canvasSize.height)
    val wPxRaw = wDp * density.density
    val hPxRaw = hDp * density.density
    // 上限超過時だけ縮小して全体を収める (clip はしない)。
    val fit = minOf(1.0, MAX_IMAGE_EXTENT_PX / wPxRaw, MAX_IMAGE_EXTENT_PX / hPxRaw).toFloat()
    val widthPx = ceil(wPxRaw * fit).toInt().coerceAtLeast(1)
    val heightPx = ceil(hPxRaw * fit).toInt().coerceAtLeast(1)
    val bitmap = ImageBitmap(widthPx, heightPx)
    CanvasDrawScope().draw(density, layoutDirection, Canvas(bitmap), Size(widthPx.toFloat(), heightPx.toFloat())) {
        // 透明のままにせず必ず背景色で塗る (貼り付け先の地色に依存させない)。
        drawRect(color = colors.background)
        scale(fit, pivot = Offset.Zero) {
            // focus を渡すと選択強調 + 減光まで焼き込む (`ide-3.md`)。sink は copy では不要。
            drawDiagram(graph, layout, colors, textMeasurer, focus)
        }
    }
    return bitmap.toAwtImage()
}

/** Folds a possibly non-finite / non-positive layout extent (dp) into a safe positive value. */
private fun clampDp(value: Double): Double =
    if (value.isFinite()) value.coerceIn(1.0, MAX_CANVAS_EXTENT) else 1.0

// クリップボード画像の 1 辺の上限 (px)。実用図は遥かに下だが、病的モデルでの巨大 allocate を防ぐ。
private const val MAX_IMAGE_EXTENT_PX = 8192.0

/**
 * Exposes the diagram both as an inline image ([DataFlavor.imageFlavor]) and — when [file] is non-null —
 * as a one-element file list ([DataFlavor.javaFileListFlavor]) so a paste into a directory keeps the
 * file's name (`<storeName>.png`). Image flavor is listed first so image-preferring targets (chats,
 * docs) still inline it; file managers pick up the file-list flavor regardless of order.
 */
private class DiagramImageTransferable(private val image: Image, private val file: File?) : Transferable {
    private val flavors: Array<DataFlavor> =
        if (file != null) arrayOf(DataFlavor.imageFlavor, DataFlavor.javaFileListFlavor) else arrayOf(DataFlavor.imageFlavor)

    override fun getTransferDataFlavors(): Array<DataFlavor> = flavors
    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavors.any { it == flavor }
    override fun getTransferData(flavor: DataFlavor): Any = when {
        flavor == DataFlavor.imageFlavor -> image
        flavor == DataFlavor.javaFileListFlavor && file != null -> listOf(file)
        else -> throw UnsupportedFlavorException(flavor)
    }
}
