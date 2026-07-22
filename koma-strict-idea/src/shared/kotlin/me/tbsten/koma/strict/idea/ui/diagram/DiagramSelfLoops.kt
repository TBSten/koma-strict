package me.tbsten.koma.strict.idea.ui.diagram

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import me.tbsten.koma.strict.idea.ir.EdgeKind
import me.tbsten.koma.strict.idea.ir.GraphEdge
import kotlin.math.hypot

/**
 * Draws the merged self-loop arc of one (node, kind) group: **one arc per trigger kind**, with every
 * member's label stacked into one multi-line pill at the arc apex. Merging same-kind loops keeps a
 * node with many stay actions from sprouting a tangle of arcs, while different kinds (enter / action
 * / recover) stay separate arcs so the colour / dash semantics survive. Groups are told apart by
 * [ordinal] which fans them onto different free faces via [selfLoopArc].
 */
internal fun DrawScope.drawSelfLoop(
    r: PxRect,
    edges: List<GraphEdge>,
    colors: DiagramColors,
    labels: MutableList<PendingLabel>,
    arrows: MutableList<PendingArrow>,
    ordinal: Int,
    usedFaces: Set<SelfLoopFace>,
    loopPolylines: MutableList<List<Offset>>,
    labelMaxWidthPx: Float?,
    alpha: Float = 1f,
    emphasized: Boolean = false,
    sink: DiagramInteractionSink? = null,
) {
    val kind = edges.first().kind
    // 選択された self-loop (tier 1) は弧を太く・矢じりを拡大する (`ide-3.md`)。選択はこの弧の代表エッジに紐づく。
    val loopSelection = DiagramSelection.Edge(edges.first())
    val arrowScale = if (emphasized) SELECTED_ARROW_SCALE else 1f
    // stay (self-loop) は本流の遷移より一段引かせる: 弧・矢じりをうっすら半透明にして、
    // 図の骨格 (state 間遷移) が先に目に入るようにする (透明度は STAY_ARC_ALPHA / STAY_LABEL_ALPHA)。
    // focus 減光 (alpha) は元の stay 透明度に乗算する (二重適用を避ける)。
    val color = colors.edgeColor(kind).copy(alpha = STAY_ARC_ALPHA).dim(alpha)
    val dash = if (kind == EdgeKind.RECOVER) PathEffect.dashPathEffect(floatArrayOf(7f, 5f)) else null
    // ループを大きめ・丸めにして「クルッ」と自己遷移だと分かるようにする。制御点 (spread) を開口 (opening)
    // より外へ広げると弧が外側へ膨らんで丸くなる。lift は compositePadding (箱の内余白) 以下に保つ
    // (top-row member のループが箱の上辺を突き抜けないように)。
    val arc = selfLoopArc(
        left = r.topLeft.x,
        top = r.topLeft.y,
        width = r.size.width,
        height = r.size.height,
        ordinal = ordinal,
        opening = 18f.dp.toPx(),
        spread = 48f.dp.toPx(),
        lift = 32f.dp.toPx(),
        labelGap = 4f.dp.toPx(),
        usedFaces = usedFaces,
    )
    // 弧も終端を barb 手前で止め、矢じりとの二重描画を無くす (`ide-2.md` #3)。矢じりは元の tip で描く。選択時は太く。
    val loopStroke = 1.5f * (if (emphasized) SELECTED_EDGE_WIDTH_MULT else 1f)
    drawPath(
        trimmedArcPath(arc, ARROW_BARB.dp.toPx() * arrowScale),
        color = color,
        style = Stroke(width = loopStroke.dp.toPx(), pathEffect = dash),
    )
    arrows += PendingArrow(Offset(arc.endX, arc.endY), Offset(arc.arrowFromX, arc.arrowFromY), color, arrowScale)
    // 弧を折れ線に近似してラベル配置の障害物に登録する (他のラベルのピルが弧を覆い隠さないように)。
    val arcPoly = (0..8).map { i ->
        val t = i / 8f
        val u = 1f - t
        Offset(
            u * u * u * arc.startX + 3f * u * u * t * arc.ctrl1X + 3f * u * t * t * arc.ctrl2X + t * t * t * arc.endX,
            u * u * u * arc.startY + 3f * u * u * t * arc.ctrl1Y + 3f * u * t * t * arc.ctrl2Y + t * t * t * arc.endY,
        )
    }
    loopPolylines += arcPoly
    // 弧を当たり判定に登録し、node self-loop もクリックで選択できるようにする (`ide-3.md`)。
    sink?.arcs?.add(loopSelection to arcPoly)
    // 上下面 (TOP/BOTTOM) のループはピルが弧より横に広く、内側 (ノード寄り) に置くと弧の脚を覆って
    // 扁平に見える。弧の頂点方向 = ノードから離れる向きを outward として渡し、ピルを常に弧の外側へ
    // 逃がす (左右面は開口が縦なので 1 行ピルが脚を覆わず、従来通り null = 現状維持)。
    val face = selfLoopFace(ordinal, usedFaces)
    val outward: Offset? = when (face) {
        SelfLoopFace.TOP, SelfLoopFace.BOTTOM -> {
            val footMid = Offset((arc.startX + arc.endX) / 2f, (arc.startY + arc.endY) / 2f)
            val apex = arcPoly[4]
            val dx = apex.x - footMid.x
            val dy = apex.y - footMid.y
            val len = hypot(dx, dy)
            if (len > 0.0001f) Offset(dx / len, dy / len) else null
        }
        // 右/左面ループは弧がノードの外 (右/左) へ膨らむ。ラベルもその外側へ逃がす。
        SelfLoopFace.RIGHT -> Offset(1f, 0f)
        SelfLoopFace.LEFT -> Offset(-1f, 0f)
    }
    // 右/左面のラベルはノード border の脇に付くので必ずリーダー線を出して帰属と border 回避を明示する
    // (`ide-2.md` #2)。上下面は従来通り距離ベース (近接時は描かない)。
    val forceLeader = face == SelfLoopFace.RIGHT || face == SelfLoopFace.LEFT
    // 同一種別の全ループのラベルを 1 枚の複数行ピルに積む (弧は 1 本なのでラベルも 1 箇所)。
    // pts に弧の折れ線そのものを渡す: ラベルはエッジラベルと同じ機構で「弧に沿って滑り、弧の脇に
    // 退避する」ため、常に自分の弧の近くに置かれる (点アンカー時代の「混雑で弧から 100px 浮いて
    // 帰属不明」を構造的に防ぐ)。arcPoly は loopPolylines と同一インスタンスなので障害物判定からは
    // 参照同一性で除外される。折返し幅は所属 composite の幅にクランプし、枠線の突き抜けを防ぐ。
    val text = edges.mapNotNull { e -> e.label.takeIf { it.isNotBlank() } }.joinToString("\n")
    if (text.isNotBlank()) {
        // pts は弧の頂点区間 (t=0.25..0.75) のみ: 足 (ノード境界上の点) を含めると全候補が塞がった時の
        // fallback がノードの真上に落ち、state 名やラベル自身が欠ける。
        // onLineAllowed=false で「弧上」配置も禁止し、ピルは常に弧の脇 = 弧が隠れない。
        labels += PendingLabel(
            text,
            arcPoly.subList(2, 7),
            // ラベルも弧に合わせて少しだけ引かせる (文字は可読性を優先して弧より濃いめ)。
            labelColorOf(kind, colors).copy(alpha = STAY_LABEL_ALPHA).dim(alpha),
            maxWidthPx = labelMaxWidthPx,
            ownLine = arcPoly,
            onLineAllowed = false,
            outwardDir = outward,
            forceLeader = forceLeader,
            selection = loopSelection,
            emphasized = emphasized,
        )
    }
}

/** Which side of a node a self-loop attaches to. Successive loops on one node cycle through the faces. */
internal enum class SelfLoopFace { TOP, BOTTOM, RIGHT, LEFT }

// self-loop の面の優先順。エッジが接続していない面から順に使う。
private val SELF_LOOP_FACE_ORDER = listOf(SelfLoopFace.TOP, SelfLoopFace.BOTTOM, SelfLoopFace.RIGHT, SelfLoopFace.LEFT)

/**
 * The face the [ordinal]-th self-loop of a node uses: top / bottom / right / left in that order,
 * skipping the faces in [usedFaces] (faces where transition edges attach) so a loop arc never crosses
 * an edge line leaving the same node. When every face is used, all four stay available (wrapping).
 */
internal fun selfLoopFace(ordinal: Int, usedFaces: Set<SelfLoopFace> = emptySet()): SelfLoopFace {
    val free = SELF_LOOP_FACE_ORDER.filter { it !in usedFaces }.ifEmpty { SELF_LOOP_FACE_ORDER }
    return free[ordinal.mod(free.size)]
}

/**
 * Pure geometry of one self-loop arc (all values in px), so the placement can be asserted without a
 * live `DrawScope` (`P1-05` regression test). [startX]/[startY]..[endX]/[endY] are the loop's feet on
 * the node border, [ctrl1X]/[ctrl1Y]/[ctrl2X]/[ctrl2Y] the cubic control points that bulge the arc
 * outward, ([arrowFromX],[arrowFromY])->([endX],[endY]) the incoming arrow direction, and
 * ([labelX],[labelY]) where the trigger label centers.
 */
internal class SelfLoopArc(
    val startX: Float,
    val startY: Float,
    val ctrl1X: Float,
    val ctrl1Y: Float,
    val ctrl2X: Float,
    val ctrl2Y: Float,
    val endX: Float,
    val endY: Float,
    val arrowFromX: Float,
    val arrowFromY: Float,
    val labelX: Float,
    val labelY: Float,
)

/**
 * Builds the [ordinal]-th self-loop arc for the node rect ([left],[top],[width],[height]). The face is
 * chosen by [selfLoopFace] so multiple loops on one node don't overlap; every 4th loop nests one ring
 * further out ([opening]/[spread]/[lift] grow) so even 5+ loops stay distinct. All values are px.
 */
internal fun selfLoopArc(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    ordinal: Int,
    opening: Float,
    spread: Float,
    lift: Float,
    labelGap: Float,
    usedFaces: Set<SelfLoopFace> = emptySet(),
): SelfLoopArc {
    val right = left + width
    val bottom = top + height
    val freeCount = SELF_LOOP_FACE_ORDER.count { it !in usedFaces }.takeIf { it > 0 } ?: SELF_LOOP_FACE_ORDER.size
    val slot = ordinal / freeCount
    val face = selfLoopFace(ordinal, usedFaces)
    // 同一面の 2 本目以降は「外へ重ねるリング」ではなく面に沿って横へずらした耳にする (重ねると弧・
    // 矢印・ラベルが密集して読めない)。左右面は面が短く耳を並べる余地がないので従来のリングで逃がす。
    val horizontal = face == SelfLoopFace.TOP || face == SelfLoopFace.BOTTOM
    val earStep = 2f * opening + 14f
    val rawShift = if (slot == 0) 0f else (if (slot % 2 == 1) -1f else 1f) * ((slot + 1) / 2).toFloat() * earStep
    val maxShift = ((if (horizontal) width else height) / 2f - opening - 8f).coerceAtLeast(0f)
    val shift = rawShift.coerceIn(-maxShift, maxShift)
    val ring = if (horizontal) 0 else slot
    val op = opening + ring * 8f
    val sp = spread + ring * 14f
    val lf = lift + ring * 22f
    val cx = left + width / 2f + (if (horizontal) shift else 0f)
    val cy = top + height / 2f + (if (!horizontal) shift else 0f)
    return when (face) {
        SelfLoopFace.TOP -> SelfLoopArc(
            startX = cx - op, startY = top,
            ctrl1X = cx - sp, ctrl1Y = top - lf,
            ctrl2X = cx + sp, ctrl2Y = top - lf,
            endX = cx + op, endY = top,
            arrowFromX = cx + op + 3f, arrowFromY = top - lf * 0.35f,
            labelX = cx, labelY = top - lf - labelGap,
        )
        SelfLoopFace.BOTTOM -> SelfLoopArc(
            startX = cx - op, startY = bottom,
            ctrl1X = cx - sp, ctrl1Y = bottom + lf,
            ctrl2X = cx + sp, ctrl2Y = bottom + lf,
            endX = cx + op, endY = bottom,
            arrowFromX = cx + op + 3f, arrowFromY = bottom + lf * 0.35f,
            labelX = cx, labelY = bottom + lf + labelGap,
        )
        SelfLoopFace.RIGHT -> SelfLoopArc(
            startX = right, startY = cy - op,
            ctrl1X = right + lf, ctrl1Y = cy - sp,
            ctrl2X = right + lf, ctrl2Y = cy + sp,
            endX = right, endY = cy + op,
            arrowFromX = right + lf * 0.35f, arrowFromY = cy + op + 3f,
            labelX = right + lf + labelGap, labelY = cy,
        )
        SelfLoopFace.LEFT -> SelfLoopArc(
            startX = left, startY = cy - op,
            ctrl1X = left - lf, ctrl1Y = cy - sp,
            ctrl2X = left - lf, ctrl2Y = cy + sp,
            endX = left, endY = cy + op,
            arrowFromX = left - lf * 0.35f, arrowFromY = cy + op + 3f,
            labelX = left - lf - labelGap, labelY = cy,
        )
    }
}

/**
 * Returns [pts] with its final point pulled back [cut] px along the last segment, so a polyline edge
 * stops at the arrow base instead of running under the arrow head (`ide-2.md` #3: no doubled alpha at
 * the tip). Only the last segment is affected; a segment shorter than [cut] collapses to zero length.
 */
internal fun trimPolylineEnd(pts: List<Offset>, cut: Float): List<Offset> {
    if (pts.size < 2) return pts
    val tip = pts.last()
    val prev = pts[pts.size - 2]
    val len = hypot(tip.x - prev.x, tip.y - prev.y)
    if (len <= 0.0001f) return pts
    val c = minOf(cut, len)
    val end = Offset(tip.x - (tip.x - prev.x) / len * c, tip.y - (tip.y - prev.y) / len * c)
    return pts.dropLast(1) + end
}

/**
 * Trims [cut] px of *arc length* off the end of the cubic [p0]..[p3] and returns the left sub-cubic's
 * four control points (via De Casteljau split). Keeps the curve smooth while stopping it short of the
 * arrow tip (`ide-2.md` #3). If the whole curve is shorter than [cut], the original points are kept.
 */
internal fun trimCubicEnd(p0: Offset, p1: Offset, p2: Offset, p3: Offset, cut: Float): List<Offset> {
    val samples = 32
    val ts = FloatArray(samples + 1)
    val cum = FloatArray(samples + 1)
    var prev = p0
    var total = 0f
    for (i in 0..samples) {
        val t = i / samples.toFloat()
        val pt = cubicPoint(p0, p1, p2, p3, t)
        if (i > 0) total += hypot(pt.x - prev.x, pt.y - prev.y)
        ts[i] = t
        cum[i] = total
        prev = pt
    }
    if (total <= cut) return listOf(p0, p1, p2, p3)
    val target = total - cut
    var tEnd = 1f
    for (i in 1..samples) {
        if (cum[i] >= target) {
            val span = cum[i] - cum[i - 1]
            val f = if (span > 0f) (target - cum[i - 1]) / span else 0f
            tEnd = ts[i - 1] + (ts[i] - ts[i - 1]) * f
            break
        }
    }
    val a = lerp(p0, p1, tEnd)
    val b = lerp(p1, p2, tEnd)
    val c = lerp(p2, p3, tEnd)
    val d = lerp(a, b, tEnd)
    val e = lerp(b, c, tEnd)
    return listOf(p0, a, d, lerp(d, e, tEnd))
}

/** Point on the cubic [p0]..[p3] at parameter [t]. */
private fun cubicPoint(p0: Offset, p1: Offset, p2: Offset, p3: Offset, t: Float): Offset {
    val u = 1f - t
    val a = u * u * u
    val b = 3f * u * u * t
    val c = 3f * u * t * t
    val d = t * t * t
    return Offset(a * p0.x + b * p1.x + c * p2.x + d * p3.x, a * p0.y + b * p1.y + c * p2.y + d * p3.y)
}

/** The cubic path of [arc] with its end trimmed [cut] px short of the tip (feeds the arrow head). */
internal fun trimmedArcPath(arc: SelfLoopArc, cut: Float): Path {
    val t = trimCubicEnd(
        Offset(arc.startX, arc.startY),
        Offset(arc.ctrl1X, arc.ctrl1Y),
        Offset(arc.ctrl2X, arc.ctrl2Y),
        Offset(arc.endX, arc.endY),
        cut,
    )
    return Path().apply {
        moveTo(t[0].x, t[0].y)
        cubicTo(t[1].x, t[1].y, t[2].x, t[2].y, t[3].x, t[3].y)
    }
}
