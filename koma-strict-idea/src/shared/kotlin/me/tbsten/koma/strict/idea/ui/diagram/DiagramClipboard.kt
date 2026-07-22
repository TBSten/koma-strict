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
import kotlin.math.ceil

/**
 * Renders the current diagram offscreen (the same [drawDiagram] pass the canvas uses, over an opaque
 * [DiagramColors.background] fill) and puts the resulting image on the AWT system clipboard as
 * [DataFlavor.imageFlavor], so it can be pasted into chats / docs / issue trackers ("Copy image" in
 * the header). Returns `true` on success; a headless environment (the preview renderer) or any other
 * clipboard failure is swallowed and reported as `false` — copying is a convenience, never worth
 * crashing the tool window for.
 */
internal fun copyDiagramImageToClipboard(
    graph: DiagramGraph,
    layout: GraphLayout,
    colors: DiagramColors,
    density: Density,
    layoutDirection: LayoutDirection,
    textMeasurer: TextMeasurer,
    focus: FocusSet? = null,
): Boolean = try {
    val image = renderDiagramImage(graph, layout, colors, density, layoutDirection, textMeasurer, focus)
    // ヘッドレス環境 (preview) では Toolkit / clipboard アクセスが例外を投げるので try 全体で握りつぶす。
    Toolkit.getDefaultToolkit().systemClipboard.setContents(ImageTransferable(image), null)
    true
} catch (e: Exception) {
    false
}

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
): Image {
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

/** A minimal [Transferable] exposing one image as [DataFlavor.imageFlavor] (what AWT clipboards expect). */
private class ImageTransferable(private val image: Image) : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.imageFlavor)
    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DataFlavor.imageFlavor
    override fun getTransferData(flavor: DataFlavor): Any =
        if (flavor == DataFlavor.imageFlavor) image else throw UnsupportedFlavorException(flavor)
}
