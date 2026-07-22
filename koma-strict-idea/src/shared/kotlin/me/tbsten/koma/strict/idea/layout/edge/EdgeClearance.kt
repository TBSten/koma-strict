package me.tbsten.koma.strict.idea.layout.edge

import me.tbsten.koma.strict.idea.ir.DiagramGraph
import me.tbsten.koma.strict.idea.ir.GraphEdge
import me.tbsten.koma.strict.idea.ir.StartNode
import me.tbsten.koma.strict.idea.layout.GraphLayout
import me.tbsten.koma.strict.idea.layout.LayoutDirection
import me.tbsten.koma.strict.idea.layout.Point
import me.tbsten.koma.strict.idea.layout.Rect

/** A segment counts as crossing an obstacle only if the overlap inside it exceeds this length. */
private const val CROSS_EPS = 0.5

/** Height of the reserved composite title strip (11sp label + padding) treated as an obstacle. */
private const val TITLE_STRIP_HEIGHT = 20.0

// ---- obstacles ----

/**
 * The rects an edge must avoid: every node rect except its own two endpoints, plus the title strip
 * of every composite that does not contain (or equal) either endpoint — an edge entering or leaving
 * a group legitimately crosses that group's border, so its own composite's strip is never a full
 * obstacle. The **title text** rect (estimated from the sealed name) is avoided even for the own
 * composite: crossing the border is fine, striking through the group heading is not.
 */
internal fun obstaclesFor(edge: GraphEdge, graph: DiagramGraph, layout: GraphLayout): List<Rect> {
    val out = ArrayList<Rect>()
    // start マーカーは小さな装飾ドットなので障害物にしない (枠外へ出た start の脇を通る線が
    // 不要に迂回するのを防ぐ)。
    val startId = graph.nodes.filterIsInstance<StartNode>().firstOrNull()?.id
    for ((id, r) in layout.nodeRects) {
        if (id == edge.fromId || id == edge.toId || id == startId) continue
        out += r
    }
    val nameById = graph.composites.associate { it.id to it.simpleName }
    val fromR = layout.endpointRect(edge.fromId)
    val toR = layout.endpointRect(edge.toId)
    for ((id, boxRect) in layout.compositeRects) {
        if (id == edge.fromId || id == edge.toId) continue
        val ownBox = (fromR != null && boxRect.contains(fromR.center)) ||
            (toR != null && boxRect.contains(toR.center))
        if (ownBox) {
            // 自箱でも見出し文字の矩形だけは避ける (幅は 11sp SemiBold の概算 6.6dp/文字)。
            val name = nameById[id] ?: continue
            val estW = (8.0 + name.length * 6.6).coerceAtMost(boxRect.width - 16.0)
            if (estW > 0) out += Rect(boxRect.x + 8.0, boxRect.y + 4.0, estW, 14.0)
        } else {
            out += Rect(boxRect.x, boxRect.y, boxRect.width, TITLE_STRIP_HEIGHT)
        }
    }
    return out
}

/** True when [p] lies strictly inside this rect (border excluded). */
private fun Rect.contains(p: Point): Boolean =
    p.x > x && p.x < right && p.y > y && p.y < bottom

// ---- straight-segment clearance ----

/** True when segment [a]->[b] passes through none of [obstacles] (used by the face-pair search). */
internal fun isClear(a: Point, b: Point, obstacles: List<Rect>): Boolean =
    obstacles.none { EdgeRouting.overlapLength(a, b, it) > CROSS_EPS }

/**
 * True when segment [a]->[b] does not clip the interior of either endpoint's own rect ([aR]/[bR]).
 * A port on a side face approached at a steep angle can make the segment graze the box corner, so
 * the arrowhead / tail ends up drawn *under* the node box. Face re-homings that do this are
 * rejected (the natural top/bottom port for adjacent vertical neighbours is kept instead).
 */
internal fun endpointClear(a: Point, b: Point, aR: Rect, bR: Rect): Boolean =
    EdgeRouting.overlapLength(a, b, aR) <= CROSS_EPS && EdgeRouting.overlapLength(a, b, bR) <= CROSS_EPS

/** True when segments a1-a2 and b1-b2 properly cross (interiors intersect; touching ends don't count). */
internal fun segmentsCross(a1: Point, a2: Point, b1: Point, b2: Point): Boolean {
    fun cross(o: Point, p: Point, q: Point): Double = (p.x - o.x) * (q.y - o.y) - (p.y - o.y) * (q.x - o.x)
    val eps = 1e-9
    val d1 = cross(b1, b2, a1)
    val d2 = cross(b1, b2, a2)
    val d3 = cross(a1, a2, b1)
    val d4 = cross(a1, a2, b2)
    return ((d1 > eps && d2 < -eps) || (d1 < -eps && d2 > eps)) &&
        ((d3 > eps && d4 < -eps) || (d3 < -eps && d4 > eps))
}

// ---- go-around detour ----

/** Clearance kept between a go-around lane and the obstacles / boxes it routes past. */
private const val DETOUR_CLEARANCE = 28.0

/**
 * Replaces any straight edge whose segment pierces a non-endpoint node with an orthogonal
 * "go-around": both ports move to the same side face (left or right in LR / top or bottom in TB)
 * and the edge runs out to a side lane clear of every obstacle, along it, then back in — the C
 * shape a reader expects when a transition skips over a stacked sibling. Straight lines that
 * already clear everything are left untouched; if neither side lane is clear the straight line
 * stays (piercing is rare and better than an unroutable tangle).
 */
internal fun detourPiercingEdges(graph: DiagramGraph, layout: GraphLayout, result: MutableMap<GraphEdge, List<Point>>) {
    val startId = graph.nodes.filterIsInstance<StartNode>().firstOrNull()?.id
    for ((edge, pts) in result.toList()) {
        if (edge.fromId == edge.toId || pts.size != 2) continue
        // 迂回のトリガは「非端点ノード矩形の貫通」のみ (縦一列の兄弟をまたぐ back-edge 用)。
        // composite の見出し帯を横切る程度で大きな迂回はしない (帯は薄く、面付け替えで足りる)。
        val nodeObstacles = layout.nodeRects.filterKeys {
            it != edge.fromId && it != edge.toId && it != startId
        }.values.toList()
        val pierced = nodeObstacles.filter { EdgeRouting.overlapLength(pts[0], pts[1], it) > CROSS_EPS }
        if (pierced.isEmpty()) continue
        val aR = layout.endpointRect(edge.fromId) ?: continue
        val bR = layout.endpointRect(edge.toId) ?: continue
        // 車線のクリア判定は全障害物 (見出し帯含む) で行い、迂回自体は帯も避ける。
        buildDetour(aR, bR, obstaclesFor(edge, graph, layout), pierced, layout.direction)?.let { result[edge] = it }
    }
}

private fun buildDetour(
    aR: Rect,
    bR: Rect,
    obstacles: List<Rect>,
    pierced: List<Rect>,
    direction: LayoutDirection,
): List<Point>? {
    // 貫通は主に「同一レイヤの縦 (LR) / 横 (TB) 一列」で起きる。列の直交方向 (LR=左右, TB=上下) の
    // 車線へ出て回り込む。近い側から順に、全障害物をクリアする車線を探す。
    val vertical = direction == LayoutDirection.LR
    val spanLo = pierced.minOf { if (vertical) it.x else it.y }
    val spanHi = pierced.maxOf { if (vertical) it.right else it.bottom }
    val nearLo = minOf(if (vertical) aR.x else aR.y, if (vertical) bR.x else bR.y)
    val nearHi = maxOf(if (vertical) aR.right else aR.right, if (vertical) bR.right else bR.bottom)
    val loLane = minOf(spanLo, nearLo) - DETOUR_CLEARANCE
    val hiLane = maxOf(spanHi, nearHi) + DETOUR_CLEARANCE
    for (lane in listOf(hiLane, loLane)) {
        val poly = if (vertical) {
            val ax = if (lane >= aR.right) aR.right else aR.x
            val bx = if (lane >= bR.right) bR.right else bR.x
            listOf(
                Point(ax, aR.center.y),
                Point(lane, aR.center.y),
                Point(lane, bR.center.y),
                Point(bx, bR.center.y),
            )
        } else {
            val ay = if (lane >= aR.bottom) aR.bottom else aR.y
            val by = if (lane >= bR.bottom) bR.bottom else bR.y
            listOf(
                Point(aR.center.x, ay),
                Point(aR.center.x, lane),
                Point(bR.center.x, lane),
                Point(bR.center.x, by),
            )
        }
        val clear = (0 until poly.size - 1).all { i ->
            obstacles.none { EdgeRouting.overlapLength(poly[i], poly[i + 1], it) > CROSS_EPS }
        }
        if (clear) return poly
    }
    return null
}
