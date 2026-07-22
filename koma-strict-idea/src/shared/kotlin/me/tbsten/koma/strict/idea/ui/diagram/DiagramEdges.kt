package me.tbsten.koma.strict.idea.ui.diagram

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.unit.dp
import me.tbsten.koma.strict.idea.ir.EdgeKind
import me.tbsten.koma.strict.idea.ir.GraphEdge
import kotlin.math.hypot

/** An arrow head deferred until after the nodes are drawn so a node box never hides its tip. [scale] enlarges a selected edge's head. */
internal class PendingArrow(val tip: Offset, val from: Offset, val color: Color, val scale: Float = 1f)

// ---- edges ----

internal fun DrawScope.drawEdge(
    edge: GraphEdge,
    colors: DiagramColors,
    labels: MutableList<PendingLabel>,
    arrows: MutableList<PendingArrow>,
    pxRoutes: Map<GraphEdge, List<Offset>>,
    alpha: Float = 1f,
    emphasized: Boolean = false,
) {
    val color = colors.edgeColor(edge.kind).dim(alpha)
    // recover (@OnRecover) は横断的なエラー処理なので破線で通常遷移と見分ける (ide.all.md §5)。
    val dash = if (edge.kind == EdgeKind.RECOVER) PathEffect.dashPathEffect(floatArrayOf(7f, 5f)) else null
    // 障害物回避ルーティング済みの折れ線 (px 変換済み) の各区間を描く。無関係な node interior /
    // composite title strip を貫かない (直線で足りるエッジは 2 点 = 従来と同一の直線)。
    val pts = pxRoutes[edge] ?: return
    if (pts.size < 2) return
    // 選択エッジ (tier 1) は線を太く・矢じりを拡大する (`ide-3.md`)。色は kind 色のまま (accent は border/ラベル枠のみ)。
    val arrowScale = if (emphasized) SELECTED_ARROW_SCALE else 1f
    val strokeW = 1.5f * (if (emphasized) SELECTED_EDGE_WIDTH_MULT else 1f)
    // 線は arrowhead の付け根 (tip から barb 手前) で止める: 矢じり三角形と線本体の二重描画を無くし、
    // 半透明 stay でも tip の重なりが濃くならない (`ide-2.md` #3)。矢じりは元の tip/向きで描くので、
    // 不透明エッジの見た目 (矢印がノード境界にきっちり刺さる) は不変。
    val drawPts = trimPolylineEnd(pts, ARROW_BARB.dp.toPx() * arrowScale)
    for (i in 0 until drawPts.size - 1) {
        drawLine(color = color, start = drawPts[i], end = drawPts[i + 1], strokeWidth = strokeW.dp.toPx(), pathEffect = dash)
    }
    val end = pts.last()
    val beforeEnd = pts[pts.size - 2]
    // 矢印は最終区間の向きで target 面に刺す (arrowhead-on-top は node 描画後に描かれる)。
    arrows += PendingArrow(end, beforeEnd, color, arrowScale)
    edge.label.takeIf { it.isNotBlank() }?.let { text ->
        // ラベルは全エッジに付ける (fan-out も 1 本ずつ別の直線なので各自のラベルが要る)。
        // 位置決め・衝突回避 (ノード / 他の線 / 既置ラベル) は drawEdgeLabel 側。ラベルクリックでこのエッジを選択。
        labels += PendingLabel(
            text,
            pts,
            labelColor(edge, colors).dim(alpha),
            selection = DiagramSelection.Edge(edge),
            emphasized = emphasized,
        )
    }
}

// ラベル色は線の色 (edgeColor) にそのまま揃える (`ide-2.md` #4): recover=紫・action=水色・
// enter/initial=neutral edge。ENTER も線と同じ muted edge 色にして「線とラベルが一体」に見せる。
private fun labelColor(edge: GraphEdge, colors: DiagramColors): Color = labelColorOf(edge.kind, colors)

internal fun labelColorOf(kind: EdgeKind, colors: DiagramColors): Color = colors.edgeColor(kind)

internal fun DrawScope.drawArrowHead(tip: Offset, from: Offset, color: Color, scale: Float = 1f) {
    val dx = tip.x - from.x
    val dy = tip.y - from.y
    val len = hypot(dx, dy).takeIf { it > 0.0001f } ?: return
    val ux = dx / len
    val uy = dy / len
    // 選択エッジ (`ide-3.md`) は矢じりを scale で拡大する (太線と釣り合わせる)。
    val barb = ARROW_BARB.dp.toPx() * scale
    val half = 4.5f.dp.toPx() * scale
    val baseX = tip.x - ux * barb
    val baseY = tip.y - uy * barb
    val px = -uy
    val py = ux
    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(baseX + px * half, baseY + py * half)
        lineTo(baseX - px * half, baseY - py * half)
        close()
    }
    drawPath(path, color = color, style = Fill)
}
