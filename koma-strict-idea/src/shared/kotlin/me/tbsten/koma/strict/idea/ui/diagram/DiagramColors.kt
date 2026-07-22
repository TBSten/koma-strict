package me.tbsten.koma.strict.idea.ui.diagram

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import me.tbsten.koma.strict.idea.ir.EdgeKind
import org.jetbrains.jewel.foundation.theme.JewelTheme

/**
 * The palette the state-diagram canvas draws with.
 *
 * The neutral chrome — background, node / any fills, borders, text, the connector line, the `[*]`
 * start dot — is read from the **active Jewel theme** ([JewelTheme.globalColors]) in
 * [rememberDiagramColors], so the figure follows the real IntelliJ theme (including custom theme
 * plugins) instead of a hardcoded dark/light pair that would look out of place under a re-themed IDE.
 *
 * The semantic accents that have no theme-color token stay tuned per light/dark so they keep a
 * distinct, recognizable hue regardless of theme: the reachability [warningText]/[warningFill] amber,
 * the initial-edge [accent], the `@OnAction` [action] teal, the `@OnRecover` [recover] purple, and the
 * `@OnExit` badge ([exitFill]/[exitText]).
 */
data class DiagramColors(
    val background: Color,
    val nodeFill: Color,
    val nodeBorder: Color,
    val nodeText: Color,
    val warningFill: Color,
    val warningBorder: Color,
    val warningText: Color,
    val startFill: Color,
    val anyFill: Color,
    val anyBorder: Color,
    val anyText: Color,
    val compositeBorder: Color,
    val compositeLabel: Color,
    val edge: Color,
    val edgeLabel: Color,
    val accent: Color,
    val action: Color,
    /** Recover edges (`@OnRecover`) — a distinct hue from the amber unreachable warning so the two never read the same. */
    val recover: Color,
    /** `@OnExit` badge pill background. */
    val exitFill: Color,
    /** `@OnExit` badge pill text. */
    val exitText: Color,
) {
    /** Line color for an edge of the given [kind] (`ide.md`: edges are styled per trigger family). */
    fun edgeColor(kind: EdgeKind): Color = when (kind) {
        EdgeKind.INITIAL -> accent
        EdgeKind.ENTER -> edge
        EdgeKind.ACTION -> action
        EdgeKind.RECOVER -> recover
    }
}

/**
 * Builds the diagram palette from the ambient Jewel theme: the neutral chrome tracks
 * [JewelTheme.globalColors] (so custom IDE themes carry through), while the semantic accents are tuned
 * per [JewelTheme.isDark] since they have no theme-color token.
 */
@Composable
fun rememberDiagramColors(): DiagramColors {
    val globals = JewelTheme.globalColors
    val dark = JewelTheme.isDark
    val background = globals.panelBackground
    val text = globals.text.normal
    val muted = globals.text.info
    val border = globals.borders.normal
    return remember(background, text, muted, border, dark) {
        DiagramColors(
            background = background,
            // node は前景色の半透明塗り (`ide-3.md`)。panel 上では従来と同じ淡いカード地に見え、composite
            // (半透明の灰) の上では下地が透けて入れ子の重なりが自然に見える。any は従来通り不透明の subtle fill。
            nodeFill = text.copy(alpha = 0.06f),
            nodeBorder = border,
            nodeText = text,
            anyFill = lerp(background, text, 0.04f),
            anyBorder = border,
            anyText = muted,
            compositeBorder = border,
            compositeLabel = muted,
            edge = muted,
            edgeLabel = text,
            startFill = text,
            // --- 意味を持つアクセント (theme token 無し): light/dark で hue を固定して識別性を保つ ---
            warningFill = if (dark) Color(0xFF4A3B28) else Color(0xFFFFF4E5),
            warningBorder = if (dark) Color(0xFFD4A24A) else Color(0xFFCC7A00),
            warningText = if (dark) Color(0xFFF0C674) else Color(0xFF7A4E00),
            accent = if (dark) Color(0xFF548AF7) else Color(0xFF3574F0),
            action = if (dark) Color(0xFF5CB8DE) else Color(0xFF2A7DA3),
            recover = if (dark) Color(0xFFB392F0) else Color(0xFF8250DF),
            exitFill = if (dark) Color(0xFF3A3D40) else Color(0xFFECEFF1),
            exitText = if (dark) Color(0xFFB0BEC5) else Color(0xFF455A64),
        )
    }
}
