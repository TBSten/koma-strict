package me.tbsten.koma.strict.idea.ui.diagram

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---- text / geometry helpers ----

internal fun DrawScope.drawCentered(tm: TextMeasurer, text: String, r: PxRect, color: Color, size: TextUnit, weight: FontWeight) {
    // 長い state 名は枠からはみ出すので、枠内幅で折り返し (camelCase 境界でも折れる)、それでも
    // 高さ/幅に収まらなければフォントを段階的に縮める (autosize)。
    val padX = 8f.dp.toPx()
    val padY = 4f.dp.toPx()
    val maxW = (r.size.width - padX * 2).coerceAtLeast(1f)
    val maxH = (r.size.height - padY * 2).coerceAtLeast(1f)
    val laid = fitText(tm, softBreakable(text), color, size, weight, maxW, maxH)
    val x = r.topLeft.x + (r.size.width - laid.size.width) / 2
    val y = r.topLeft.y + (r.size.height - laid.size.height) / 2
    drawText(laid, topLeft = Offset(x, y))
}

/**
 * Lays [text] to fit inside [maxW] x [maxH]: wraps at [maxW] at the base [size]; if the wrapped block
 * is still too tall (or an unbreakable run overflows the width), steps the font down toward
 * [MIN_LABEL_SP] until it fits (autosize). Always returns a result — at the floor it may overflow
 * slightly rather than disappear.
 */
private fun DrawScope.fitText(
    tm: TextMeasurer,
    text: String,
    color: Color,
    size: TextUnit,
    weight: FontWeight,
    maxW: Float,
    maxH: Float,
): TextLayoutResult {
    val widthPx = maxW.toInt().coerceAtLeast(1)
    var sp = size.value
    var last: TextLayoutResult? = null
    while (sp >= MIN_LABEL_SP) {
        val laid = tm.measure(
            text = text,
            // 折り返し時も各行を中央揃えにする (state 名の複数行表示が左に寄らないように)。
            style = TextStyle(color = color, fontSize = sp.sp, fontWeight = weight, textAlign = TextAlign.Center),
            overflow = TextOverflow.Clip,
            softWrap = true,
            constraints = Constraints(maxWidth = widthPx),
        )
        last = laid
        if (laid.size.height <= maxH && laid.size.width <= maxW + 0.5f) return laid
        sp -= 1f
    }
    return last!!
}

// camelCase の内部大文字前に zero-width space を入れ、長い識別子でも枠内で折り返せるようにする。
// コードポイントから生成する (ソースに不可視文字/エスケープを埋めない)。
private val CAMEL_BOUNDARY = Regex("(?<=[a-z0-9])(?=[A-Z])")
private val ZERO_WIDTH_SPACE = 0x200B.toChar().toString()

internal fun softBreakable(text: String): String = text.replace(CAMEL_BOUNDARY, ZERO_WIDTH_SPACE)

// autosize の下限フォントサイズ (sp)。これ以上は縮めない (可読性優先、僅かなはみ出しは許容)。
private const val MIN_LABEL_SP = 8f

internal fun DrawScope.corner(radiusDp: Float) =
    androidx.compose.ui.geometry.CornerRadius(radiusDp.dp.toPx(), radiusDp.dp.toPx())

/** A rectangle already converted to pixels, with cached center. */
internal class PxRect(val topLeft: Offset, val size: Size) {
    val center: Offset get() = Offset(topLeft.x + size.width / 2, topLeft.y + size.height / 2)
    val right: Float get() = topLeft.x + size.width
    val bottom: Float get() = topLeft.y + size.height
}

internal fun lerp(a: Offset, b: Offset, t: Float): Offset = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
