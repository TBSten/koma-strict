package me.tbsten.koma.strict.idea.ui.diagram

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.hypot

/**
 * An edge label deferred to the final draw pass (drawn on top of nodes). Carries the edge's routed
 * poly-line [pts] so placement can sit the label on the line and slide it *along the whole edge* (by
 * arc length, not just the first segment — which is only the short port stub) to dodge collisions.
 * A self-loop passes a single point.
 */
internal class PendingLabel(
    val text: String,
    val pts: List<Offset>,
    val color: Color,
    /** Wrap-width cap in px (composite width clamp for loop labels); null = the global 220dp only. */
    val maxWidthPx: Float? = null,
    /** The full own line (self-loop arc) — obstacle-excluded by identity and leader-line target. */
    val ownLine: List<Offset>? = null,
    /** False for loop labels: the pill must sit beside the arc, never on it (dist=0 is skipped). */
    val onLineAllowed: Boolean = true,
    /**
     * Unit vector pointing *away from the node* for a horizontal-face (TOP/BOTTOM) self-loop; null
     * otherwise. Placement tries this side first so the opaque pill never lands over the loop's legs
     * (a pill wider than the 18dp foot-gap would otherwise hide the arc and flatten it).
     */
    val outwardDir: Offset? = null,
    /**
     * Forces a leader line from the pill to the arc regardless of distance (`ide-2.md` #2). Set for
     * scope-shared stays and right/left-face self-loops whose labels sit beside the enclosure border:
     * the thin leader keeps the pill off the border while still tying it to its arc.
     */
    val forceLeader: Boolean = false,
    /** The selection a click on this pill makes (`ide-3.md`); null for a non-selectable label. */
    val selection: DiagramSelection? = null,
    /** True when this label's transition / stay is selected — the pill gets an accent border. */
    val emphasized: Boolean = false,
)

/** An axis-aligned label rectangle used for the final-pass collision avoidance. */
internal class LabelBox(val left: Float, val top: Float, val w: Float, val h: Float) {
    fun intersects(o: LabelBox): Boolean =
        left < o.left + o.w && left + w > o.left && top < o.top + o.h && top + h > o.top
}

internal fun DrawScope.drawEdgeLabel(
    text: String,
    pts: List<Offset>,
    textColor: Color,
    colors: DiagramColors,
    tm: TextMeasurer,
    canvasPx: Size,
    placed: MutableList<LabelBox>,
    nodeBoxes: List<LabelBox>,
    allPolylines: List<List<Offset>>,
    maxWidthPx: Float? = null,
    ownLine: List<Offset>? = null,
    onLineAllowed: Boolean = true,
    outwardDir: Offset? = null,
    forceLeader: Boolean = false,
    emphasized: Boolean = false,
    selection: DiagramSelection? = null,
    sink: DiagramInteractionSink? = null,
) {
    if (pts.isEmpty()) return
    // ラベル中心は線の開始点 (source ポート = pts[0]) から最低 12dp 離す (`ide-2.md` #7): 開始点に
    // 近すぎると読みにくく、self-loop では脚に被って弧が潰れる。線長に応じた下限 fraction を課す。
    val minStartFrac = polylineLength(pts).takeIf { it > 0f }
        ?.let { (LABEL_MIN_START_GAP.dp.toPx() / it).coerceIn(0f, 0.5f) } ?: 0f
    // ラベルは最大幅を決めて折り返す (長いトリガ名や複数 emit で 1 行が伸びすぎないように)。
    // camelCase 境界にも折り返し点を入れ、複数行は中央揃えで積む (self-loop の集約ラベルも同経路)。
    val wrapWidth = minOf(LABEL_MAX_WIDTH_DP.dp.toPx(), maxWidthPx ?: Float.MAX_VALUE).toInt().coerceAtLeast(60)
    val laid = tm.measure(
        text = softBreakable(text),
        style = TextStyle(color = textColor, fontSize = 10.sp, textAlign = TextAlign.Center),
        softWrap = true,
        constraints = Constraints(maxWidth = wrapWidth),
    )
    val w = laid.size.width.toFloat()
    val h = laid.size.height.toFloat()
    val padX = 4f.dp.toPx()
    val padY = 2f.dp.toPx()
    // ラベルは矢印の出始め (source 寄り) に、線がラベル中心を通る形で置く: -[onEnter]-----> のイメージ。
    // 位置は折れ線全体の弧長比で取る (最初の区間だけだと今は 16dp のスタブなので境界に張り付いてしまう)。
    // ノード矩形は禁止領域: 不透明ピルが state 名やスタブを覆わない。自分以外のエッジの線も禁止領域:
    // ピルが他の線を覆い隠すと遷移が消えたように見える。衝突したらエッジに沿って中央側へ滑らせて空きを
    // 探し、それでも埋まっている時は「他ラベルだけ避けた位置 (線は諦める)」→「他の線だけ」→「ノード
    // だけ」の順に妥協する。線は途切れても前後から辿れるが、ラベルにピルが乗ると文字ごと消えるため、
    // 既置ラベル回避を線回避より優先する。
    // self-loop (点 1 個) は弧の頂点にそのまま置く。端はキャンバス内にクランプしてクリップを防ぐ。
    var box: LabelBox? = null
    var bx = 0f
    var by = 0f
    var nodeClearBox: LabelBox? = null
    var ncx = 0f
    var ncy = 0f
    var lineClearBox: LabelBox? = null
    var lcx = 0f
    var lcy = 0f
    var placedClearBox: LabelBox? = null
    var pcx = 0f
    var pcy = 0f
    // 候補は距離を段階的に増やしながら探す: まず線上 (ラベル中心を線が通る)、次に線の脇 (1 ラベル分)、
    // 最後にノード半高ぶん離れた脇。後段は「ラベル幅 > ノード間ギャップ」で線上のどこにもノードと
    // 被らず置けない時の退避先 (auth TB の LoggedIn—Authenticating のような近接ペア)。
    val sideDistances = floatArrayOf(
        0f,
        h / 2 + padY + 6f.dp.toPx(),
        24f.dp.toPx() + h / 2 + padY + 2f.dp.toPx(),
        // 混雑時の最終段: 近距離が全部 (ノード / 見出し / 既置ラベル) に塞がれた時にさらに外へ逃げる
        // (self-loop が同一面に複数集まるケースで、無理に重ねず 1 段外へ積むための距離)。
        48f.dp.toPx() + h / 2 + padY + 2f.dp.toPx(),
    ) + if (!onLineAllowed) {
        // loop ラベル専用の遠距離段: 箱上辺と弧の間にピルが物理的に入らない多行スタックは、
        // 見出しの向こう側 = 箱の外まで逃がす。帰属はリーダー線が示す。
        // 弧上 fallback でノード枠を消すくらいなら遠くでも完全に読める方が良い。
        floatArrayOf(
            76f.dp.toPx() + h / 2 + padY + 2f.dp.toPx(),
            108f.dp.toPx() + h / 2 + padY + 2f.dp.toPx(),
        )
    } else {
        floatArrayOf()
    }
    outer@ for (dist in sideDistances) {
        // loop ラベルは「弧上」(dist=0) を使わない: ピルが自分の弧を隠して環形状が消えるため。
        if (!onLineAllowed && dist == 0f) continue
        for (f in LABEL_FRACTIONS) {
            // 開始点近くの候補は最低 12dp ぶんの下限 fraction まで押し出す (`ide-2.md` #7)。
            val ff = f.coerceAtLeast(minStartFrac)
            val on = pointAt(pts, ff)
            val sides = if (dist == 0f) {
                listOf(Offset.Zero)
            } else if (pts.size < 2) {
                // self-loop (点 1 個) は線方向が定義できないので上下左右の 4 方向へ退避を試す。
                listOf(Offset(0f, dist), Offset(0f, -dist), Offset(dist, 0f), Offset(-dist, 0f))
            } else {
                val dir = directionAt(pts, ff)
                val n = Offset(-dir.y, dir.x) * dist
                // self-loop (outwardDir 指定) は弧の外側を先に試す: 内側に不透明ピルを置くと弧の脚を
                // 覆って環が扁平に見えるため (TOP 面 save (stay) / next (stay) 潰れの直接原因)。
                if (outwardDir != null && (n.x * outwardDir.x + n.y * outwardDir.y) < 0f) {
                    listOf(-n, n)
                } else {
                    listOf(n, -n)
                }
            }
            for (side in sides) {
                val cx = clampToCanvas(on.x + side.x, w / 2 + padX, canvasPx.width)
                val cy = clampToCanvas(on.y + side.y, h / 2 + padY, canvasPx.height)
                val candidate = labelBox(cx, cy, w, h, padX, padY)
                val nodeClear = nodeBoxes.none { it.intersects(candidate) }
                val otherLineClear = allPolylines.none { line ->
                    line !== pts && line !== ownLine &&
                        (0 until line.size - 1).any { candidate.intersectsSegment(line[it], line[it + 1]) }
                }
                val placedClear = placed.none { it.intersects(candidate) }
                if (nodeClear && nodeClearBox == null) {
                    nodeClearBox = candidate; ncx = cx; ncy = cy
                }
                if (nodeClear && otherLineClear && lineClearBox == null) {
                    lineClearBox = candidate; lcx = cx; lcy = cy
                }
                if (nodeClear && placedClear && placedClearBox == null) {
                    placedClearBox = candidate; pcx = cx; pcy = cy
                }
                if (nodeClear && otherLineClear && placedClear) {
                    box = candidate; bx = cx; by = cy; break@outer
                }
            }
        }
    }
    if (box == null && placedClearBox != null) {
        box = placedClearBox; bx = pcx; by = pcy
    }
    if (box == null && lineClearBox != null) {
        box = lineClearBox; bx = lcx; by = lcy
    }
    if (box == null && nodeClearBox != null) {
        box = nodeClearBox; bx = ncx; by = ncy
    }
    if (box == null) {
        val on = pointAt(pts, LABEL_FRACTIONS[0].coerceAtLeast(minStartFrac))
        bx = clampToCanvas(on.x, w / 2 + padX, canvasPx.width)
        by = clampToCanvas(on.y, h / 2 + padY, canvasPx.height)
        box = labelBox(bx, by, w, h, padX, padY)
    }
    placed += box
    // 自線から離れた位置に退避したラベルは、細いリーダー線で帰属を明示する (同文ラベルが複数
    // 並んだ時も「どの線のラベルか」が引き出し線で追える)。近接配置では描かない。
    // 右/左面 stay (forceLeader) は距離に関わらず必ずリーダー線を出す (`ide-2.md` #2)。
    val nearest = nearestPointOnPolyline(ownLine ?: pts, Offset(bx, by))
    val leadDist = hypot(nearest.x - bx, nearest.y - by)
    val leaderThreshold = if (forceLeader) 0f else 30f.dp.toPx()
    if (leadDist > leaderThreshold) {
        val dirX = (nearest.x - bx) / leadDist
        val dirY = (nearest.y - by) / leadDist
        // ピルの縁からピル外へ出た地点を起点にする (中心からだと文字に被る)。矩形境界との交点は
        // 各軸の半径をその方向成分で割った小さい方 (max だと斜め方向で内側に留まり数 px の断片になる)。
        val tx = if (abs(dirX) > 1e-3f) (box.w / 2 + 2f) / abs(dirX) else Float.MAX_VALUE
        val ty = if (abs(dirY) > 1e-3f) (box.h / 2 + 2f) / abs(dirY) else Float.MAX_VALUE
        val startT = minOf(tx, ty).coerceAtMost(leadDist)
        // 起点から線までの残りが短すぎるゴミ状の断片は描かない。
        if (leadDist - startT > 6f.dp.toPx()) {
            drawLine(
                color = textColor.copy(alpha = 0.45f),
                start = Offset(bx + dirX * startT, by + dirY * startT),
                end = nearest,
                strokeWidth = 1f.dp.toPx(),
            )
        }
    }
    drawRoundRect(
        color = colors.background,
        topLeft = Offset(box.left, box.top),
        size = Size(box.w, box.h),
        cornerRadius = corner(4f),
    )
    // 選択された Transition / stay のラベルは accent border で囲う (`ide-3.md` tier 1)。
    if (emphasized) {
        drawRoundRect(
            color = colors.accent,
            topLeft = Offset(box.left, box.top),
            size = Size(box.w, box.h),
            cornerRadius = corner(4f),
            style = Stroke(width = 1.5f.dp.toPx()),
        )
    }
    drawText(laid, topLeft = Offset(bx - w / 2, by - h / 2))
    // 確定したラベル矩形を当たり判定に登録し、ラベルクリックでその遷移を選択できるようにする (`ide-3.md`)。
    if (selection != null) sink?.labelBoxes?.add(selection to PxBox(box.left, box.top, box.w, box.h))
}

/** Total length of poly-line [pts] (0 for a single point). */
private fun polylineLength(pts: List<Offset>): Float {
    if (pts.size < 2) return 0f
    var total = 0f
    for (i in 0 until pts.size - 1) total += hypot(pts[i + 1].x - pts[i].x, pts[i + 1].y - pts[i].y)
    return total
}

/** The point on poly-line [pts] closest to [p] (segment-wise projection). */
private fun nearestPointOnPolyline(pts: List<Offset>, p: Offset): Offset {
    if (pts.size < 2) return pts.firstOrNull() ?: p
    var best = pts[0]
    var bestD = Float.MAX_VALUE
    for (i in 0 until pts.size - 1) {
        val a = pts[i]
        val b = pts[i + 1]
        val abx = b.x - a.x
        val aby = b.y - a.y
        val len2 = abx * abx + aby * aby
        val t = if (len2 < 1e-6f) 0f else (((p.x - a.x) * abx + (p.y - a.y) * aby) / len2).coerceIn(0f, 1f)
        val qx = a.x + abx * t
        val qy = a.y + aby * t
        val d = hypot(p.x - qx, p.y - qy)
        if (d < bestD) {
            bestD = d
            best = Offset(qx, qy)
        }
    }
    return best
}

/** The unit direction of the poly-line at arc-length fraction [f] (for the side-of-line label offset). */
private fun directionAt(pts: List<Offset>, f: Float): Offset {
    if (pts.size < 2) return Offset(1f, 0f)
    val a = pointAt(pts, (f - 0.01f).coerceAtLeast(0f))
    val b = pointAt(pts, (f + 0.01f).coerceAtMost(1f))
    val len = hypot(b.x - a.x, b.y - a.y)
    return if (len > 0.0001f) Offset((b.x - a.x) / len, (b.y - a.y) / len) else Offset(1f, 0f)
}

/** The point at arc-length fraction [f] (0..1) along the poly-line [pts]; a single point returns itself. */
private fun pointAt(pts: List<Offset>, f: Float): Offset {
    if (pts.size < 2) return pts[0]
    var total = 0f
    val segs = FloatArray(pts.size - 1)
    for (i in 0 until pts.size - 1) {
        segs[i] = hypot(pts[i + 1].x - pts[i].x, pts[i + 1].y - pts[i].y)
        total += segs[i]
    }
    if (total <= 0f) return pts[0]
    var target = total * f
    for (i in segs.indices) {
        if (target <= segs[i] || i == segs.lastIndex) {
            val t = if (segs[i] > 0f) (target / segs[i]).coerceIn(0f, 1f) else 0f
            return lerp(pts[i], pts[i + 1], t)
        }
        target -= segs[i]
    }
    return pts.last()
}

private fun labelBox(cx: Float, cy: Float, w: Float, h: Float, padX: Float, padY: Float): LabelBox =
    LabelBox(left = cx - w / 2 - padX, top = cy - h / 2 - padY, w = w + padX * 2, h = h + padY * 2)

/** True when segment [a]->[b] passes through this box (Liang-Barsky clip; touching the border counts). */
private fun LabelBox.intersectsSegment(a: Offset, b: Offset): Boolean {
    var t0 = 0f
    var t1 = 1f
    val dx = b.x - a.x
    val dy = b.y - a.y
    val p = floatArrayOf(-dx, dx, -dy, dy)
    val q = floatArrayOf(a.x - left, left + w - a.x, a.y - top, top + h - a.y)
    for (i in 0..3) {
        if (p[i] == 0f) {
            if (q[i] < 0f) return false
        } else {
            val r = q[i] / p[i]
            if (p[i] < 0f) {
                if (r > t1) return false
                if (r > t0) t0 = r
            } else {
                if (r < t0) return false
                if (r < t1) t1 = r
            }
        }
    }
    return t1 > t0
}

// ラベルを置くエッジ上の位置 (source からの比率)。矢印の出始め寄りを優先し、詰まったら中央側へ滑らせる。
private val LABEL_FRACTIONS = floatArrayOf(0.2f, 0.28f, 0.14f, 0.36f, 0.44f, 0.52f)

// ラベル中心と線の開始点 (source ポート) の最低間隔 (dp)。これ未満に寄る候補は下限 fraction で押し出す。
private const val LABEL_MIN_START_GAP = 12f

// エッジラベルの最大幅 (dp)。これを超えるテキストは折り返す (表示領域を決めて改行する方針)。
private const val LABEL_MAX_WIDTH_DP = 220

/** Keeps a label center within [[margin], [extent] - [margin]] so its box stays on-canvas. */
private fun clampToCanvas(value: Float, margin: Float, extent: Float): Float {
    val lo = margin
    val hi = extent - margin
    return if (lo >= hi) value else value.coerceIn(lo, hi)
}
