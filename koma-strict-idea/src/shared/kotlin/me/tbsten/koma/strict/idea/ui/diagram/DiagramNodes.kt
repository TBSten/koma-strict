package me.tbsten.koma.strict.idea.ui.diagram

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.tbsten.koma.strict.idea.ir.StateGraphNode
import me.tbsten.koma.strict.idea.layout.LayoutDirection

// ---- nodes ----

internal fun DrawScope.drawStateNode(
    r: PxRect,
    node: StateGraphNode,
    colors: DiagramColors,
    tm: TextMeasurer,
    alpha: Float = 1f,
    selected: Boolean = false,
) {
    val fill = (if (node.reachable) colors.nodeFill else colors.warningFill).dim(alpha)
    // 選択時は accent border で太く囲う (`ide-3.md` tier 1)。選択ノードは常に alpha=1 なので dim しない。
    val border = if (selected) colors.accent else (if (node.reachable) colors.nodeBorder else colors.warningBorder).dim(alpha)
    val borderW = if (selected) SELECTED_NODE_BORDER_DP else 1.5f
    val text = (if (node.reachable) colors.nodeText else colors.warningText).dim(alpha)
    drawRoundRect(color = fill, topLeft = r.topLeft, size = r.size, cornerRadius = corner(10f))
    drawRoundRect(color = border, topLeft = r.topLeft, size = r.size, cornerRadius = corner(10f), style = Stroke(width = borderW.dp.toPx()))
    drawCentered(tm, node.simpleName, r, text, 12.sp, FontWeight.Medium)
}

/**
 * The `@OnExit` badge: a small pill beside the node (`ide.all.md` §5 — exit cannot transition, so it
 * is a tag rather than an edge). In `LR` it sits to the node's right (where the next layer is far
 * away); in `TB` it sits directly below the node, because the right neighbor is a same-layer sibling
 * only `siblingGap` away and a right-side pill would collide with it. The layout reserves matching
 * canvas room on that side so the pill never clips.
 */
internal fun DrawScope.drawExitBadge(
    nodeRect: PxRect,
    text: String,
    colors: DiagramColors,
    tm: TextMeasurer,
    direction: LayoutDirection,
    alpha: Float = 1f,
) {
    val laid = tm.measure(text, TextStyle(color = colors.exitText.dim(alpha), fontSize = 9.sp, fontWeight = FontWeight.Medium))
    val padX = 6f.dp.toPx()
    val padY = 3f.dp.toPx()
    val w = laid.size.width + padX * 2
    val h = laid.size.height + padY * 2
    val gap = 8f.dp.toPx()
    val topLeft = when (direction) {
        LayoutDirection.LR -> Offset(nodeRect.right + gap, nodeRect.center.y - h / 2)
        LayoutDirection.TB -> Offset(nodeRect.center.x - w / 2, nodeRect.bottom + gap)
    }
    drawRoundRect(color = colors.exitFill.dim(alpha), topLeft = topLeft, size = Size(w, h), cornerRadius = corner(6f))
    drawText(laid, topLeft = Offset(topLeft.x + padX, topLeft.y + padY))
}

internal fun DrawScope.drawStart(r: PxRect, colors: DiagramColors, alpha: Float = 1f) {
    // UML initial marker: a solid filled dot.
    val radius = minOf(r.size.width, r.size.height) / 2
    drawCircle(color = colors.startFill.dim(alpha), radius = radius, center = r.center)
}

internal fun DrawScope.drawAnyNode(
    r: PxRect,
    label: String,
    colors: DiagramColors,
    tm: TextMeasurer,
    alpha: Float = 1f,
    selected: Boolean = false,
) {
    // any-state 擬似ノードは枠なし (塗りと文字だけ)。実 state との違いは「枠が無い」ことで表す。
    drawRoundRect(color = colors.anyFill.dim(alpha), topLeft = r.topLeft, size = r.size, cornerRadius = corner(24f))
    // 選択時だけ accent border を足して強調 (`ide-3.md` tier 1)。
    if (selected) {
        drawRoundRect(
            color = colors.accent,
            topLeft = r.topLeft,
            size = r.size,
            cornerRadius = corner(24f),
            style = Stroke(width = SELECTED_NODE_BORDER_DP.dp.toPx()),
        )
    }
    drawCentered(tm, label, r, colors.anyText.dim(alpha), 11.sp, FontWeight.Normal)
}

internal fun DrawScope.drawComposite(
    r: PxRect,
    name: String,
    colors: DiagramColors,
    tm: TextMeasurer,
    alpha: Float = 1f,
    selected: Boolean = false,
) {
    // nest state (composite) は半透明の灰背景で「領域」を面として見せる (入れ子は自然に濃くなる)。
    drawRoundRect(
        color = colors.compositeBorder.copy(alpha = 0.08f).dim(alpha),
        topLeft = r.topLeft,
        size = r.size,
        cornerRadius = corner(12f),
    )
    // 選択時は accent border で太く囲う (`ide-3.md` tier 1)。
    val border = if (selected) colors.accent else colors.compositeBorder.dim(alpha)
    val borderW = if (selected) SELECTED_NODE_BORDER_DP else 1.2f
    drawRoundRect(
        color = border,
        topLeft = r.topLeft,
        size = r.size,
        cornerRadius = corner(12f),
        style = Stroke(width = borderW.dp.toPx()),
    )
    val laid = tm.measure(name, TextStyle(color = colors.compositeLabel.dim(alpha), fontSize = 11.sp, fontWeight = FontWeight.SemiBold))
    drawText(laid, topLeft = Offset(r.topLeft.x + 8f.dp.toPx(), r.topLeft.y + 5f.dp.toPx()))
}
