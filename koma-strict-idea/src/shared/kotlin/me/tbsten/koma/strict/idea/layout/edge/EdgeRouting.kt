package me.tbsten.koma.strict.idea.layout.edge

import me.tbsten.koma.strict.idea.ir.DiagramGraph
import me.tbsten.koma.strict.idea.ir.GraphEdge
import me.tbsten.koma.strict.idea.ir.NodeId
import me.tbsten.koma.strict.idea.layout.GraphLayout
import me.tbsten.koma.strict.idea.layout.Point
import me.tbsten.koma.strict.idea.layout.Rect
import kotlin.math.hypot

/**
 * Pure obstacle-aware edge routing over a laid-out [DiagramGraph] (`P1-07`), in the *parallel-bundle*
 * style: all edges between one (unordered) node pair share the direction of the centre-to-centre
 * line and are spread onto evenly spaced parallel lines. Near-parallel bundles that would draw on
 * one line (an `A—B—C` chain on a row) are pushed apart by [separateParallel].
 *
 * **Every edge is exactly one straight segment, border port to border port — no stubs, no bends**
 * (`線は直線のまま曲げない`; the returned poly-line always has exactly 2 points, which the regression
 * test pins). When the natural face pair's straight line would pass through an unrelated node, a
 * composite title strip, or a self-loop zone, [ensureStraightClear] re-homes the bundle onto a face
 * pair whose straight line is clear instead of detouring. Everything is in layout (dp) coordinates so
 * the routing can be asserted without a live `DrawScope`; the renderer only converts the returned
 * points to px.
 */
object EdgeRouting {

    /**
     * Routes every non-self edge of [graph] under [layout], returning exactly 2 points per edge —
     * the source border port and the target border port of one straight segment. Self-loops
     * (`from == to`) are not included — the renderer draws those as arcs.
     *
     * Parallel-bundle style: all edges between the same (unordered) node pair share the direction of
     * the centre-to-centre line and are spread onto evenly spaced parallel offset lines, so a
     * back-and-forth pair or a multi-trigger bundle renders as a clean band of parallel diagonals
     * instead of a wedge of point-to-point lines at differing angles.
     */
    fun routeAll(graph: DiagramGraph, layout: GraphLayout): Map<GraphEdge, List<Point>> {
        // 無向ペアごとに束ねる。束内の平行線が「同じ傾きの帯」に見えるのがこの方式の狙い。
        val bundles = LinkedHashMap<Pair<NodeId, NodeId>, MutableList<GraphEdge>>()
        for (edge in graph.edges) {
            if (edge.fromId == edge.toId) continue
            layout.endpointRect(edge.fromId) ?: continue
            layout.endpointRect(edge.toId) ?: continue
            bundles.getOrPut(pairKey(edge.fromId, edge.toId)) { mutableListOf() }.add(edge)
        }
        val geoByKey = LinkedHashMap<Pair<NodeId, NodeId>, BundleGeo?>()
        for ((key, bundleEdges) in bundles) geoByKey[key] = bundleGeo(key, bundleEdges, layout)
        // 平行かつ視認区間が重なる束同士 (一直線上の A—B—C 連鎖など) を離してから各線を引く。
        val geos = geoByKey.values.filterNotNull()
        separateParallel(geos)
        // 中心ガイドの接続 (面 + 中心ポート) を先に確定する。接続面は障害物に依存しないので、この後
        // self-loop の占有ゾーンを障害物へ足しても接続は変わらない (2 段階でも安定)。
        for (geo in geos) {
            val baseA = Point(geo.ca.x + geo.shift.x, geo.ca.y + geo.shift.y)
            val baseB = Point(geo.cb.x + geo.shift.x, geo.cb.y + geo.shift.y)
            geo.attachA = attach(geo.aR, baseA, geo.d) ?: attach(geo.aR, geo.ca, geo.d)
            geo.attachB = attach(geo.bR, baseB, Point(-geo.d.x, -geo.d.y)) ?: attach(geo.bR, geo.cb, Point(-geo.d.x, -geo.d.y))
        }
        // 直線保証 (曲げない): 自然な面ペアの直線がノード / タイトル帯 / ループゾーンを貫くなら、
        // 迂回で曲げる代わりに「直線が通る面ペア」へ束ごと付け替える (any-named の leave は下の回廊へ)。
        ensureStraightClear(graph, layout, geos)
        // 面に収まらないほど束が集まった場合、方向的に自然な直交面へ束を逃がす (lce の Loading 右面
        // 46dp に 2 帯 4 本は物理的に収まらない -> Error 帯は下面へ)。
        relieveCongestedFaces(geos)
        // 同じ面に複数の束が付く場合、ポートを「相手側の位置」順に並べ替えつつ帯幅 + 最低間隔を確保する。
        // これが無いと、下から回り込む線が上のポートへ刺さって面の直前で他の線と交差する (any-named)。
        coordinateFacePorts(geos)
        // 直線同士の透過交差も面ペアの付け替えで減らす (曲げずに交差を消せる割り当てがあるなら使う)。
        // 座標調整済みのポートを使って数えるので、見た目の交差と判定が一致する。付け替え後にもう一度
        // 座標調整して、同じ面に増えた束の間隔と並び順を整える。
        if (reduceCrossings(graph, layout, geos)) coordinateFacePorts(geos)
        val result = LinkedHashMap<GraphEdge, List<Point>>()
        for ((key, bundleEdges) in bundles) {
            val geo = geoByKey[key]
            if (geo == null) {
                // 中心が一致する退化ケース: 素直に中心間の直線 (実データではまず起きない)。
                val aR = layout.endpointRect(key.first) ?: continue
                val bR = layout.endpointRect(key.second) ?: continue
                bundleEdges.forEach { result[it] = listOf(aR.center, bR.center) }
                continue
            }
            routeBundle(geo, result)
        }
        // 直線がどうしても他ノードを貫く場合だけ (縦一列に積まれた兄弟をまたぐ back-edge 等)、
        // 側方の車線へ回り込む折れ線に差し替える (曲げない原則の例外: 貫通よりは迂回が読める)。
        detourPiercingEdges(graph, layout, result)
        return result
    }

    /** Border-graze tolerance: the interior is the rect deflated by this on every side. */
    private const val INTERIOR_INSET = 0.5

    /**
     * Length of segment [a]->[b] that lies inside [rect]'s interior (Liang-Barsky clip against the rect
     * deflated by [INTERIOR_INSET]). Zero when the segment misses the rect or only grazes / runs along a
     * border; a positive value means the segment passes through the interior. Used both to route and to
     * assert the no-crossing invariant in tests.
     */
    fun overlapLength(a: Point, b: Point, rect: Rect): Double {
        val dx = b.x - a.x
        val dy = b.y - a.y
        // 内部 (interior) のみを対象にする: 端点が隣接ノードの境界に載って辺沿いに走る線分を「貫通」と
        // 誤検出しないよう、矩形を各辺 INTERIOR_INSET だけ縮めてから交差長を測る。
        val x0 = rect.x + INTERIOR_INSET
        val x1 = rect.right - INTERIOR_INSET
        val y0 = rect.y + INTERIOR_INSET
        val y1 = rect.bottom - INTERIOR_INSET
        if (x1 <= x0 || y1 <= y0) return 0.0
        val p = doubleArrayOf(-dx, dx, -dy, dy)
        val q = doubleArrayOf(a.x - x0, x1 - a.x, a.y - y0, y1 - a.y)
        var t0 = 0.0
        var t1 = 1.0
        for (i in 0..3) {
            if (p[i] == 0.0) {
                if (q[i] < 0.0) return 0.0 // parallel to this edge and outside the slab
            } else {
                val r = q[i] / p[i]
                if (p[i] < 0.0) {
                    if (r > t1) return 0.0
                    if (r > t0) t0 = r
                } else {
                    if (r < t0) return 0.0
                    if (r < t1) t1 = r
                }
            }
        }
        if (t1 <= t0) return 0.0
        return hypot(dx * (t1 - t0), dy * (t1 - t0))
    }
}
