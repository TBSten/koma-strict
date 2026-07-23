package me.tbsten.koma.strict.idea.ui.diagram

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Recording indicator on the diagram viewport (`flows-design.md`): while a flow is being recorded, a
 * soft red glow breathes in from the four edges — an "inner shadow" that pulses slowly rather than
 * blinking, so the record state stays visible without competing with the diagram content. [insertGlow]
 * lives on the viewport [Modifier], not inside the scrollable canvas, so it stays fixed in place while
 * the diagram scrolls underneath it.
 */

/** How deep the glow reaches in from each edge. */
private val RECORDING_GLOW_DEPTH = 20.dp

/** Pulse range / period ("じわぁっと" = a slow, soft breathing fade, not a sharp blink). */
private const val RECORDING_GLOW_MIN_ALPHA = 0.22f
private const val RECORDING_GLOW_MAX_ALPHA = 0.6f
private const val RECORDING_GLOW_PERIOD_MS = 2400

/**
 * The glow's current intensity: `0f` while not [recording], otherwise a slow
 * [RECORDING_GLOW_MIN_ALPHA]..[RECORDING_GLOW_MAX_ALPHA] pulse. The underlying animation keeps running
 * regardless of [recording] (cheap, and avoids a restart glitch each time recording toggles) — only the
 * returned value gates to zero.
 */
@Composable
internal fun rememberRecordingGlowIntensity(recording: Boolean): Float {
    val transition = rememberInfiniteTransition(label = "recordingGlow")
    val pulse by transition.animateFloat(
        initialValue = RECORDING_GLOW_MIN_ALPHA,
        targetValue = RECORDING_GLOW_MAX_ALPHA,
        animationSpec = infiniteRepeatable(
            animation = tween(RECORDING_GLOW_PERIOD_MS, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "recordingGlowPulse",
    )
    return if (recording) pulse else 0f
}

/**
 * Draws a four-edge inner glow of [color] at [intensity] (`<= 0f` draws nothing); [depth] is how deep it
 * reaches in from each edge, clamped to half the shorter side so it can never meet itself and wash out
 * a small viewport. The corners get double coverage where two edges overlap — matching a real inset
 * shadow, where both contributing edges naturally deepen the corner, so this is not smoothed away.
 */
internal fun DrawScope.drawRecordingInnerGlow(color: Color, intensity: Float, depth: Dp = RECORDING_GLOW_DEPTH) {
    if (intensity <= 0f) return
    val w = size.width
    val h = size.height
    val d = depth.toPx().coerceAtMost(minOf(w, h) / 2f)
    if (d <= 0f) return
    val edge = color.copy(alpha = color.alpha * intensity)
    val clear = color.copy(alpha = 0f)
    drawRect(Brush.verticalGradient(listOf(edge, clear), startY = 0f, endY = d), size = Size(w, d))
    drawRect(
        Brush.verticalGradient(listOf(clear, edge), startY = h - d, endY = h),
        topLeft = Offset(0f, h - d),
        size = Size(w, d),
    )
    drawRect(Brush.horizontalGradient(listOf(edge, clear), startX = 0f, endX = d), size = Size(d, h))
    drawRect(
        Brush.horizontalGradient(listOf(clear, edge), startX = w - d, endX = w),
        topLeft = Offset(w - d, 0f),
        size = Size(d, h),
    )
}

/** Applies [drawRecordingInnerGlow] as a viewport overlay drawn after the content ([Modifier.drawWithContent]). */
internal fun Modifier.recordingInnerGlow(color: Color, intensity: Float): Modifier =
    drawWithContent {
        drawContent()
        drawRecordingInnerGlow(color, intensity)
    }
