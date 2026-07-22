package me.tbsten.koma.strict.idea.layout.edge

import me.tbsten.koma.strict.idea.ir.GraphEdge
import me.tbsten.koma.strict.idea.ir.NodeId
import me.tbsten.koma.strict.idea.layout.GraphLayout
import me.tbsten.koma.strict.idea.layout.Point
import me.tbsten.koma.strict.idea.layout.Rect
import kotlin.math.abs
import kotlin.math.hypot

/** Gap between neighbouring parallel lines of one node-pair bundle. */
internal const val BUNDLE_SPACING = 20.0

/**
 * Pre-computed geometry of one node-pair bundle: endpoint rects/centres, the unit guide direction
 * [d] (from `a` towards `b`) with its perpendicular [perp], the members sorted onto stable offset
 * slots, and the [spacing] between neighbouring parallel lines. [shift] is the anti-overlap
 * translation added by [separateParallel]; [visA]/[visB] approximate the *visible* span (guide
 * clipped to the endpoint borders) so overlap detection ignores the part hidden inside a node or
 * composite box.
 */
internal class BundleGeo(
    val aId: NodeId,
    val bId: NodeId,
    val aR: Rect,
    val bR: Rect,
    val ca: Point,
    val cb: Point,
    val d: Point,
    val perp: Point,
    val sortedEdges: List<GraphEdge>,
    val spacing: Double,
    val visA: Point,
    val visB: Point,
) {
    /** Width of the whole band, first to last parallel line. */
    val spread: Double get() = (sortedEdges.size - 1) * spacing
    var shift: Point = Point(0.0, 0.0)

    /** Centre attachments, resolved once after [separateParallel] (faces don't depend on obstacles). */
    var attachA: Attach? = null
    var attachB: Attach? = null
}

internal fun bundleGeo(
    key: Pair<NodeId, NodeId>,
    edges: List<GraphEdge>,
    layout: GraphLayout,
): BundleGeo? {
    val aR = layout.endpointRect(key.first) ?: return null
    val bR = layout.endpointRect(key.second) ?: return null
    val ca = aR.center
    val cb = bR.center
    val len = hypot(cb.x - ca.x, cb.y - ca.y)
    if (len < 1e-6) return null
    val d = Point((cb.x - ca.x) / len, (cb.y - ca.y) / len)
    // 同方向の線が隣り合うよう向き -> トリガ名で安定ソートしてからオフセット slot を割り当てる。
    val sorted = edges.sortedWith(
        compareBy({ it.fromId != key.first }, { it.trigger }, { it.kind.ordinal }, { it.emits.joinToString(",") }),
    )
    // 帯の全幅 (spread) が両端の最短の面に corner inset 込みで収まるよう spacing を頭打ちにする。
    val maxHalf = (0.5 * minOf(aR.width, aR.height, bR.width, bR.height) - CORNER_INSET).coerceAtLeast(0.0)
    val spacing = if (sorted.size <= 1) 0.0 else minOf(BUNDLE_SPACING, (2.0 * maxHalf) / (sorted.size - 1).toDouble())
    // 視認区間 = 中心線から両端ノード内部を除いた部分。composite が端点の束 (境界止まりの矢印) で
    // 「箱の中は描かれていない」ことを重なり判定へ正しく伝えるのに効く。
    val visA = exitHit(aR, ca, d)?.point ?: ca
    val visB = exitHit(bR, cb, Point(-d.x, -d.y))?.point ?: cb
    return BundleGeo(key.first, key.second, aR, bR, ca, cb, d, Point(-d.y, d.x), sorted, spacing, visA, visB)
}

// 平行判定: 単位方向ベクトル同士の外積 (= sin θ)。約 10° 未満を「同じ向きの帯」とみなす。
private const val PARALLEL_SIN = 0.17

/** Two near-parallel guide segments must overlap along their axis by more than this to collide. */
private const val OVERLAP_EPS = 1.0

/**
 * Pushes apart bundles whose guide lines are near-parallel, closer than one line gap, and actually
 * overlapping along the shared axis. Without this, an `A—B—C` chain on one row draws `B–C` exactly
 * on top of `A–B`'s line (`session`'s `signIn` / `signOut` collapsing into what looks like a single
 * arrow). Each collision moves both bundles half the missing distance in opposite perpendicular
 * directions; the ports move with the guides, so the bands stay parallel but become distinct.
 * Detection uses the *visible* spans, so segments that merely share an endpoint node (leaving from
 * opposite faces) are not pushed apart. Single pass in deterministic bundle order.
 */
internal fun separateParallel(geos: List<BundleGeo>) {
    for (i in geos.indices) {
        for (j in i + 1 until geos.size) {
            val a = geos[i]
            val b = geos[j]
            val sin = abs(a.d.x * b.d.y - a.d.y * b.d.x)
            if (sin > PARALLEL_SIN) continue
            val midBx = (b.visA.x + b.visB.x) / 2.0
            val midBy = (b.visA.y + b.visB.y) / 2.0
            val signedDist = (midBx - a.ca.x) * a.perp.x + (midBy - a.ca.y) * a.perp.y
            val need = a.spread / 2.0 + b.spread / 2.0 + BUNDLE_SPACING
            if (abs(signedDist) >= need) continue
            // a の視認区間を軸 (a.d, 原点 a.visA) に取り、b の視認区間の射影との重なり長を測る。
            val lenA = (a.visB.x - a.visA.x) * a.d.x + (a.visB.y - a.visA.y) * a.d.y
            val u0 = (b.visA.x - a.visA.x) * a.d.x + (b.visA.y - a.visA.y) * a.d.y
            val u1 = (b.visB.x - a.visA.x) * a.d.x + (b.visB.y - a.visA.y) * a.d.y
            val overlap = minOf(lenA, maxOf(u0, u1)) - maxOf(0.0, minOf(u0, u1))
            if (overlap <= OVERLAP_EPS) continue
            val push = (need - abs(signedDist)) / 2.0
            val s = if (signedDist >= 0.0) 1.0 else -1.0
            a.shift = Point(a.shift.x - a.perp.x * s * push, a.shift.y - a.perp.y * s * push)
            b.shift = Point(b.shift.x + a.perp.x * s * push, b.shift.y + a.perp.y * s * push)
        }
    }
}

/**
 * Routes all members of one bundle. The *centre* guide (through both centres, plus the bundle's
 * anti-overlap shift) picks one face and one centre port per endpoint; the members' ports are then
 * distributed **along that face** around the centre port, `spacing` apart, in a consistent
 * rotational sense at both ends. Deriving every port from the centre attachment (instead of
 * clipping each member's own offset guide) keeps the band parallel and evenly spaced even when the
 * guides leave near a corner — per-member clipping used to collapse all ports onto the same corner
 * clamp, drawing the lines exactly on top of each other (`lce`'s `onEnter` / `retry`).
 */
internal fun routeBundle(
    geo: BundleGeo,
    result: MutableMap<GraphEdge, List<Point>>,
) {
    val centreA = geo.attachA
    val centreB = geo.attachB
    if (centreA == null || centreB == null) {
        geo.sortedEdges.forEach { result[it] = listOf(geo.ca, geo.cb) }
        return
    }
    val tA = tangent(centreA.face)
    val tB = tangent(centreB.face)
    // 面に沿ってずらす向きは perp との内積の符号で両端を揃える (揃えないと帯の並び順が端で反転し
    // 線同士が交差する)。内積 ~0 は d が面と平行 = その面から出ない配置なので実質起きない。
    val sA = if ((geo.perp.x * tA.x + geo.perp.y * tA.y) >= 0.0) 1.0 else -1.0
    val sB = if ((geo.perp.x * tB.x + geo.perp.y * tB.y) >= 0.0) 1.0 else -1.0
    // 面に沿った配布幅は角度補正する: 線と直角に測った隙間 = 接線方向の間隔 × |cross(t, d)| なので、
    // 補正なしだと急な斜めの帯ほど飛行中の間隔が spacing より狭く見える。目標の spacing (垂直距離)
    // を保つよう接線方向は spacing / |cross| に広げる (かすめ角の爆発は下限 0.35 と面の長さで抑える)。
    val k = geo.sortedEdges.size
    val tsA = tangentSpacing(geo.spacing, tA, geo.d, faceLength(geo.aR, centreA.face), k)
    val tsB = tangentSpacing(geo.spacing, tB, geo.d, faceLength(geo.bR, centreB.face), k)
    // 帯全体 (centre ± spread/2) が面に収まるよう中心接続点を内側へ寄せる。寄せずに配布すると
    // 角の近くから出る帯は外側ポートだけ角クランプに潰され、線の途中の間隔が spacing より狭くなる。
    val cpA = recentre(geo.aR, centreA.face, centreA.port, (k - 1) * tsA / 2.0)
    val cpB = recentre(geo.bR, centreB.face, centreB.port, (k - 1) * tsB / 2.0)
    geo.sortedEdges.forEachIndexed { i, edge ->
        val slot = i - (k - 1) / 2.0
        val offA = slot * tsA
        val offB = slot * tsB
        val portA = clampToFace(geo.aR, centreA.face, Point(cpA.x + tA.x * sA * offA, cpA.y + tA.y * sA * offA))
        val portB = clampToFace(geo.bR, centreB.face, Point(cpB.x + tB.x * sB * offB, cpB.y + tB.y * sB * offB))
        // エッジは常に「境界ポート 2 点の 1 本の直線」(曲げない)。障害物との干渉は
        // ensureStraightClear / reduceCrossings が面ペア選択で解消済み。
        result[edge] = if (edge.fromId == geo.aId) {
            listOf(portA, portB)
        } else {
            listOf(portB, portA)
        }
    }
}
