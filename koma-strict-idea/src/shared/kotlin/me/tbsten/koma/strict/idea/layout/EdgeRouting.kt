package me.tbsten.koma.strict.idea.layout

import me.tbsten.koma.strict.idea.ir.DiagramGraph
import me.tbsten.koma.strict.idea.ir.GraphEdge
import me.tbsten.koma.strict.idea.ir.NodeId
import me.tbsten.koma.strict.idea.ir.StartNode
import kotlin.math.abs
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

    /** A segment counts as crossing an obstacle only if the overlap inside it exceeds this length. */
    private const val CROSS_EPS = 0.5

    /** Height of the reserved composite title strip (11sp label + padding) treated as an obstacle. */
    private const val TITLE_STRIP_HEIGHT = 20.0

    /** Which side of a node an edge attaches to. */
    internal enum class Face { RIGHT, LEFT, BOTTOM, TOP }

    /** Gap between neighbouring parallel lines of one node-pair bundle. */
    private const val BUNDLE_SPACING = 20.0

    /** Keeps a port off a node's rounded corner (ports live on the flat part of a face). */
    private const val CORNER_INSET = 6.0

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
    private fun detourPiercingEdges(graph: DiagramGraph, layout: GraphLayout, result: MutableMap<GraphEdge, List<Point>>) {
        val startId = graph.nodes.filterIsInstance<StartNode>().firstOrNull()?.id
        for ((edge, pts) in result.toList()) {
            if (edge.fromId == edge.toId || pts.size != 2) continue
            // 迂回のトリガは「非端点ノード矩形の貫通」のみ (縦一列の兄弟をまたぐ back-edge 用)。
            // composite の見出し帯を横切る程度で大きな迂回はしない (帯は薄く、面付け替えで足りる)。
            val nodeObstacles = layout.nodeRects.filterKeys {
                it != edge.fromId && it != edge.toId && it != startId
            }.values.toList()
            val pierced = nodeObstacles.filter { overlapLength(pts[0], pts[1], it) > CROSS_EPS }
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
                obstacles.none { overlapLength(poly[i], poly[i + 1], it) > CROSS_EPS }
            }
            if (clear) return poly
        }
        return null
    }

    // ---- straight-middle guarantee (曲げない) ----

    /** Faces to try for one end when the natural straight middle is blocked (natural face first). */
    private fun faceCandidates(natural: Face, dir: Point): List<Face> = when (natural) {
        Face.RIGHT, Face.LEFT ->
            if (dir.y >= 0.0) listOf(natural, Face.BOTTOM, Face.TOP) else listOf(natural, Face.TOP, Face.BOTTOM)
        Face.TOP, Face.BOTTOM ->
            if (dir.x >= 0.0) listOf(natural, Face.RIGHT, Face.LEFT) else listOf(natural, Face.LEFT, Face.RIGHT)
    }

    /** The centre port where the guide through [base] (direction [dir]) meets [face]'s border line. */
    private fun portOnFace(rect: Rect, face: Face, base: Point, dir: Point): Point = when (face) {
        Face.BOTTOM -> clampToFace(
            rect, face,
            if (abs(dir.y) < 1e-9) Point(base.x, rect.bottom) else Point(base.x + dir.x * (rect.bottom - base.y) / dir.y, rect.bottom),
        )
        Face.TOP -> clampToFace(
            rect, face,
            if (abs(dir.y) < 1e-9) Point(base.x, rect.y) else Point(base.x + dir.x * (rect.y - base.y) / dir.y, rect.y),
        )
        Face.RIGHT -> clampToFace(
            rect, face,
            if (abs(dir.x) < 1e-9) Point(rect.right, base.y) else Point(rect.right, base.y + dir.y * (rect.right - base.x) / dir.x),
        )
        Face.LEFT -> clampToFace(
            rect, face,
            if (abs(dir.x) < 1e-9) Point(rect.x, base.y) else Point(rect.x, base.y + dir.y * (rect.x - base.x) / dir.x),
        )
    }

    /**
     * Greedy transparent-crossing reduction: with straight-only lines, two bundles can cross in an
     * `X`. When an alternative *clear* face pair for one bundle strictly lowers the total number of
     * segment crossings, the bundle re-homes there. Two rounds, first-improvement greedy.
     */
    private fun reduceCrossings(graph: DiagramGraph, layout: GraphLayout, geos: List<BundleGeo>): Boolean {
        val zones = loopZones(graph, layout, geos)

        fun middles(): List<Pair<Point, Point>> {
            val segs = mutableListOf<Pair<Point, Point>>()
            for (g in geos) {
                val a = g.attachA ?: continue
                val b = g.attachB ?: continue
                segs += a.port to b.port
            }
            return segs
        }

        fun countCrossings(): Int {
            val segs = middles()
            var count = 0
            for (i in segs.indices) {
                for (j in i + 1 until segs.size) {
                    if (segmentsCross(segs[i].first, segs[i].second, segs[j].first, segs[j].second)) count++
                }
            }
            return count
        }

        var changed = false
        repeat(2) {
            var current = countCrossings()
            if (current == 0) return changed
            var improved = false
            for (geo in geos) {
                val a0 = geo.attachA ?: continue
                val b0 = geo.attachB ?: continue
                val edge = geo.sortedEdges.first()
                val obstacles = obstaclesFor(edge, graph, layout) +
                    zones.filterKeys { it != geo.aId && it != geo.bId }.values.flatten()
                val baseA = Point(geo.ca.x + geo.shift.x, geo.ca.y + geo.shift.y)
                val baseB = Point(geo.cb.x + geo.shift.x, geo.cb.y + geo.shift.y)
                val dOut = geo.d
                val dIn = Point(-geo.d.x, -geo.d.y)
                var bestCount = current
                var bestPair: Pair<Attach, Attach>? = null
                for (fa in faceCandidates(a0.face, dOut)) {
                    for (fb in faceCandidates(b0.face, dIn)) {
                        if (fa == a0.face && fb == b0.face) continue
                        val na = Attach(portOnFace(geo.aR, fa, baseA, dOut), fa)
                        val nb = Attach(portOnFace(geo.bR, fb, baseB, dIn), fb)
                        if (!isClear(na.port, nb.port, obstacles)) continue
                        if (!endpointClear(na.port, nb.port, geo.aR, geo.bR)) continue
                        geo.attachA = na
                        geo.attachB = nb
                        val c = countCrossings()
                        geo.attachA = a0
                        geo.attachB = b0
                        if (c < bestCount) {
                            bestCount = c
                            bestPair = na to nb
                        }
                    }
                }
                bestPair?.let {
                    geo.attachA = it.first
                    geo.attachB = it.second
                    current = bestCount
                    improved = true
                    changed = true
                }
            }
            if (!improved) return changed
        }
        return changed
    }

    /** True when segments a1-a2 and b1-b2 properly cross (interiors intersect; touching ends don't count). */
    private fun segmentsCross(a1: Point, a2: Point, b1: Point, b2: Point): Boolean {
        fun cross(o: Point, p: Point, q: Point): Double = (p.x - o.x) * (q.y - o.y) - (p.y - o.y) * (q.x - o.x)
        val eps = 1e-9
        val d1 = cross(b1, b2, a1)
        val d2 = cross(b1, b2, a2)
        val d3 = cross(a1, a2, b1)
        val d4 = cross(a1, a2, b2)
        return ((d1 > eps && d2 < -eps) || (d1 < -eps && d2 > eps)) &&
            ((d3 > eps && d4 < -eps) || (d3 < -eps && d4 > eps))
    }

    /**
     * Straight-line guarantee: an edge is never bent. When the natural face pair's straight line
     * would pass through a node, a composite title strip, or a self-loop zone, the whole bundle
     * re-homes onto the first face pair whose straight line is clear (shortest wins) — e.g.
     * `any-named`'s `leave` runs through the clear corridor below the middle node instead of bending
     * around it. If no pair is clear, the natural straight line stays as-is: bending is never an
     * option.
     */
    private fun ensureStraightClear(graph: DiagramGraph, layout: GraphLayout, geos: List<BundleGeo>) {
        val zones = loopZones(graph, layout, geos)
        for (geo in geos) {
            val a0 = geo.attachA ?: continue
            val b0 = geo.attachB ?: continue
            val edge = geo.sortedEdges.first()
            val obstacles = obstaclesFor(edge, graph, layout) +
                zones.filterKeys { it != geo.aId && it != geo.bId }.values.flatten()
            if (isClear(a0.port, b0.port, obstacles)) continue
            val baseA = Point(geo.ca.x + geo.shift.x, geo.ca.y + geo.shift.y)
            val baseB = Point(geo.cb.x + geo.shift.x, geo.cb.y + geo.shift.y)
            val dOut = geo.d
            val dIn = Point(-geo.d.x, -geo.d.y)
            var best: Pair<Attach, Attach>? = null
            var bestLen = Double.MAX_VALUE
            for (fa in faceCandidates(a0.face, dOut)) {
                for (fb in faceCandidates(b0.face, dIn)) {
                    if (fa == a0.face && fb == b0.face) continue
                    val na = Attach(portOnFace(geo.aR, fa, baseA, dOut), fa)
                    val nb = Attach(portOnFace(geo.bR, fb, baseB, dIn), fb)
                    if (!isClear(na.port, nb.port, obstacles)) continue
                    if (!endpointClear(na.port, nb.port, geo.aR, geo.bR)) continue
                    val len = hypot(nb.port.x - na.port.x, nb.port.y - na.port.y)
                    if (len < bestLen) {
                        bestLen = len
                        best = na to nb
                    }
                }
            }
            best?.let {
                geo.attachA = it.first
                geo.attachB = it.second
            }
        }
    }

    /** Depth of the strip a self-loop arc occupies outside its face (arc lift + stroke slack). */
    private const val LOOP_ZONE = 36.0

    /** How far a self-loop zone extends past the node corners along the face (keeps lines off corners). */
    private const val LOOP_ZONE_MARGIN = 16.0

    // self-loop の面割り当て順 (描画側 SelfLoopFace と同じ順序を保つこと)。
    private val LOOP_FACE_ORDER = listOf(Face.TOP, Face.BOTTOM, Face.RIGHT, Face.LEFT)

    /**
     * The face strips self-loop arcs will occupy, per node. Loops go onto faces with no edge
     * attachment (mirroring the renderer's assignment), so routed edges must treat those strips as
     * obstacles — otherwise a detour around the node can cut straight through a loop arc.
     */
    private fun loopZones(graph: DiagramGraph, layout: GraphLayout, geos: List<BundleGeo>): Map<NodeId, List<Rect>> {
        val loopCounts = HashMap<NodeId, Int>()
        for (edge in graph.edges) {
            if (edge.fromId == edge.toId) loopCounts.merge(edge.fromId, 1, Int::plus)
        }
        if (loopCounts.isEmpty()) return emptyMap()
        val used = HashMap<NodeId, MutableSet<Face>>()
        for (geo in geos) {
            geo.attachA?.let { used.getOrPut(geo.aId) { mutableSetOf() }.add(it.face) }
            geo.attachB?.let { used.getOrPut(geo.bId) { mutableSetOf() }.add(it.face) }
        }
        val zones = HashMap<NodeId, List<Rect>>()
        for ((id, count) in loopCounts) {
            val r = layout.nodeRects[id] ?: continue
            val free = LOOP_FACE_ORDER.filter { it !in used[id].orEmpty() }.ifEmpty { LOOP_FACE_ORDER }
            val faces = (0 until count).map { free[it % free.size] }.toSet()
            // 面の接線方向にも少し広げ、ノードの角スレスレを通る直線もゾーン外へ追い出す。
            zones[id] = faces.map { face ->
                when (face) {
                    Face.TOP -> Rect(r.x - LOOP_ZONE_MARGIN, r.y - LOOP_ZONE, r.width + 2 * LOOP_ZONE_MARGIN, LOOP_ZONE)
                    Face.BOTTOM -> Rect(r.x - LOOP_ZONE_MARGIN, r.bottom, r.width + 2 * LOOP_ZONE_MARGIN, LOOP_ZONE)
                    Face.LEFT -> Rect(r.x - LOOP_ZONE, r.y - LOOP_ZONE_MARGIN, LOOP_ZONE, r.height + 2 * LOOP_ZONE_MARGIN)
                    Face.RIGHT -> Rect(r.right, r.y - LOOP_ZONE_MARGIN, LOOP_ZONE, r.height + 2 * LOOP_ZONE_MARGIN)
                }
            }
        }
        return zones
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
    private fun routeBundle(
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

    /** Unit tangent of a face (the direction ports are distributed along). */
    private fun tangent(face: Face): Point = when (face) {
        Face.RIGHT, Face.LEFT -> Point(0.0, 1.0)
        Face.TOP, Face.BOTTOM -> Point(1.0, 0.0)
    }

    /** Length of [face]'s side of [rect] (the room available for distributing ports). */
    private fun faceLength(rect: Rect, face: Face): Double = when (face) {
        Face.RIGHT, Face.LEFT -> rect.height
        Face.TOP, Face.BOTTOM -> rect.width
    }

    // 接線方向の配布幅を広げる角度補正の下限 (かすめ角で無限大に発散しないためのガード)。
    private const val MIN_CROSS = 0.35

    /**
     * The along-face port spacing that yields a *perpendicular* line gap of [spacing]: the perpendicular
     * gap between two parallel lines whose ports differ by `ts` along tangent [t] is `ts * |cross(t, d)|`,
     * so `ts = spacing / |cross|` — a steep band needs wider along-face distribution to look as far
     * apart in flight as a shallow one. Capped so the whole band still fits on the face.
     */
    private fun tangentSpacing(spacing: Double, t: Point, d: Point, faceLen: Double, k: Int): Double {
        if (k <= 1) return 0.0
        val cross = abs(t.x * d.y - t.y * d.x).coerceAtLeast(MIN_CROSS)
        val fit = ((faceLen - 2.0 * CORNER_INSET) / (k - 1).toDouble()).coerceAtLeast(0.0)
        return minOf(spacing / cross, fit)
    }

    /**
     * Moves a band's centre port inward along [face] so the whole band (centre +- [halfSpread]) fits
     * between the face's corner insets — the flanking ports then keep their full spacing instead of
     * being crushed onto the corner clamp. Falls back to the face middle when the face is too short.
     */
    private fun recentre(rect: Rect, face: Face, port: Point, halfSpread: Double): Point = when (face) {
        Face.RIGHT, Face.LEFT -> {
            val lo = rect.y + CORNER_INSET + halfSpread
            val hi = rect.bottom - CORNER_INSET - halfSpread
            Point(port.x, if (lo <= hi) port.y.coerceIn(lo, hi) else (rect.y + rect.bottom) / 2.0)
        }
        Face.TOP, Face.BOTTOM -> {
            val lo = rect.x + CORNER_INSET + halfSpread
            val hi = rect.right - CORNER_INSET - halfSpread
            Point(if (lo <= hi) port.x.coerceIn(lo, hi) else (rect.x + rect.right) / 2.0, port.y)
        }
    }

    /** Clamps a port onto [face] of [rect], kept off the rounded corners (tiny rects fall to the middle). */
    private fun clampToFace(rect: Rect, face: Face, p: Point): Point {
        val yLo = rect.y + CORNER_INSET
        val yHi = rect.bottom - CORNER_INSET
        val xLo = rect.x + CORNER_INSET
        val xHi = rect.right - CORNER_INSET
        val cy = if (yLo <= yHi) p.y.coerceIn(yLo, yHi) else (rect.y + rect.bottom) / 2.0
        val cx = if (xLo <= xHi) p.x.coerceIn(xLo, xHi) else (rect.x + rect.right) / 2.0
        return when (face) {
            Face.RIGHT -> Point(rect.right, cy)
            Face.LEFT -> Point(rect.x, cy)
            Face.BOTTOM -> Point(cx, rect.bottom)
            Face.TOP -> Point(cx, rect.y)
        }
    }

    /** Deterministic unordered key so `A->B` and `B->A` land in the same bundle. */
    private fun pairKey(a: NodeId, b: NodeId): Pair<NodeId, NodeId> =
        if (a.toString() <= b.toString()) a to b else b to a

    /**
     * Pre-computed geometry of one node-pair bundle: endpoint rects/centres, the unit guide direction
     * [d] (from `a` towards `b`) with its perpendicular [perp], the members sorted onto stable offset
     * slots, and the [spacing] between neighbouring parallel lines. [shift] is the anti-overlap
     * translation added by [separateParallel]; [visA]/[visB] approximate the *visible* span (guide
     * clipped to the endpoint borders) so overlap detection ignores the part hidden inside a node or
     * composite box.
     */
    private class BundleGeo(
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

    private fun bundleGeo(
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
    private fun separateParallel(geos: List<BundleGeo>) {
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

    /** One bundle end sitting on a shared (node, face); [atA] tells which end of [geo] it is. */
    private class FaceUse(val geo: BundleGeo, val atA: Boolean) {
        val attach: Attach get() = (if (atA) geo.attachA else geo.attachB)!!
        val rect: Rect get() = if (atA) geo.aR else geo.bR

        /** The outgoing direction of the guide at this end (away from the node). */
        val outDir: Point get() = if (atA) geo.d else Point(-geo.d.x, -geo.d.y)
    }

    /** A bundle needs at least this direction component towards a face to be re-homed onto it. */
    private const val REASSIGN_MIN_COMPONENT = 0.25

    /** Iteration cap for the congestion-relief loop (a generous bound; real graphs settle in 1-2). */
    private const val RELIEF_ROUNDS = 8

    /**
     * Moves bundles off faces that physically cannot hold every attached band at full width, onto the
     * orthogonal face their direction naturally supports (`lce`: `Loading`'s right face can't hold the
     * `Content` and `Error` bands - the down-leaning `Error` band re-homes to the bottom face). Without
     * this the bands get compressed into each other and their member lines cross right at the face.
     */
    private fun relieveCongestedFaces(geos: List<BundleGeo>) {
        repeat(RELIEF_ROUNDS) {
            val groups = HashMap<Pair<NodeId, Face>, MutableList<FaceUse>>()
            for (g in geos) {
                g.attachA?.let { groups.getOrPut(g.aId to it.face) { mutableListOf() }.add(FaceUse(g, true)) }
                g.attachB?.let { groups.getOrPut(g.bId to it.face) { mutableListOf() }.add(FaceUse(g, false)) }
            }
            var moved = false
            for ((key, uses) in groups) {
                if (uses.size < 2) continue
                val face = key.second
                val rect = uses.first().rect
                val available = faceLength(rect, face) - 2.0 * CORNER_INSET
                val need = uses.sumOf { 2.0 * endHalfSpread(it.geo, it.rect, face) } + (uses.size - 1) * BUNDLE_SPACING
                if (need <= available) continue
                // 直交成分が最も大きい束を 1 つだけ直交面へ逃がし、次周で再評価する。
                val candidate = uses.maxByOrNull { orthComponent(it.outDir, face) } ?: continue
                if (orthComponent(candidate.outDir, face) < REASSIGN_MIN_COMPONENT) continue
                if (reassignToOrthogonalFace(candidate, face)) moved = true
            }
            if (!moved) return
        }
    }

    /** The direction component perpendicular to [face]'s tangent (how strongly [dir] leans off-face). */
    private fun orthComponent(dir: Point, face: Face): Double = when (face) {
        Face.RIGHT, Face.LEFT -> abs(dir.y)
        Face.TOP, Face.BOTTOM -> abs(dir.x)
    }

    /** Re-homes [use]'s attachment from [from] onto the orthogonal face its direction leans towards. */
    private fun reassignToOrthogonalFace(use: FaceUse, from: Face): Boolean {
        val dir = use.outDir
        val rect = use.rect
        val newFace = when (from) {
            Face.RIGHT, Face.LEFT -> if (dir.y > 0.0) Face.BOTTOM else Face.TOP
            Face.TOP, Face.BOTTOM -> if (dir.x > 0.0) Face.RIGHT else Face.LEFT
        }
        // 中心ガイドと新しい面の直線との交点をポートにする (面の範囲へは clamp)。
        val base = if (use.atA) {
            Point(use.geo.ca.x + use.geo.shift.x, use.geo.ca.y + use.geo.shift.y)
        } else {
            Point(use.geo.cb.x + use.geo.shift.x, use.geo.cb.y + use.geo.shift.y)
        }
        val port = when (newFace) {
            Face.BOTTOM -> {
                if (abs(dir.y) < 1e-6) return false
                val t = (rect.bottom - base.y) / dir.y
                clampToFace(rect, newFace, Point(base.x + dir.x * t, rect.bottom))
            }
            Face.TOP -> {
                if (abs(dir.y) < 1e-6) return false
                val t = (rect.y - base.y) / dir.y
                clampToFace(rect, newFace, Point(base.x + dir.x * t, rect.y))
            }
            Face.RIGHT -> {
                if (abs(dir.x) < 1e-6) return false
                val t = (rect.right - base.x) / dir.x
                clampToFace(rect, newFace, Point(rect.right, base.y + dir.y * t))
            }
            Face.LEFT -> {
                if (abs(dir.x) < 1e-6) return false
                val t = (rect.x - base.x) / dir.x
                clampToFace(rect, newFace, Point(rect.x, base.y + dir.y * t))
            }
        }
        val na = Attach(port, newFace)
        if (use.atA) use.geo.attachA = na else use.geo.attachB = na
        return true
    }

    /**
     * Coordinates the centre ports of all bundles sharing one (node, face): sorts them by the
     * counterpart's position along the face tangent and re-spaces them monotonically with at least
     * [BUNDLE_SPACING] between neighbouring bands. Without this each bundle clamps its own guide exit
     * independently, so a line approaching from below can get a port *above* its neighbour's and cross
     * that neighbour right in front of the face (`any-named`'s `leave` x `enter`).
     */
    private fun coordinateFacePorts(geos: List<BundleGeo>) {
        val groups = HashMap<Pair<NodeId, Face>, MutableList<FaceUse>>()
        for (g in geos) {
            g.attachA?.let { groups.getOrPut(g.aId to it.face) { mutableListOf() }.add(FaceUse(g, true)) }
            g.attachB?.let { groups.getOrPut(g.bId to it.face) { mutableListOf() }.add(FaceUse(g, false)) }
        }
        for ((key, uses) in groups) {
            if (uses.size < 2) continue
            val face = key.second
            val t = tangent(face)
            val sorted = uses.sortedBy { u ->
                val other = if (u.atA) u.geo.cb else u.geo.ca
                (other.x + u.geo.shift.x) * t.x + (other.y + u.geo.shift.y) * t.y
            }
            val half = sorted.map { endHalfSpread(it.geo, it.rect, face) }
            val rect = sorted.first().rect
            val faceLo = if (face == Face.RIGHT || face == Face.LEFT) rect.y else rect.x
            val faceHi = faceLo + faceLength(rect, face)
            val pos = DoubleArray(sorted.size) { i ->
                val p = sorted[i].attach.port
                p.x * t.x + p.y * t.y
            }
            // 前進: 相手順の単調性と最小間隔 (帯半幅 + BUNDLE_SPACING + 帯半幅) を確保する。
            for (i in 1 until pos.size) {
                val need = half[i - 1] + BUNDLE_SPACING + half[i]
                if (pos[i] < pos[i - 1] + need) pos[i] = pos[i - 1] + need
            }
            // 後退: 面の上限からはみ出した分を、間隔を保ったまま前へ押し戻す。
            for (i in pos.indices.reversed()) {
                val cap = faceHi - CORNER_INSET - half[i]
                if (pos[i] > cap) pos[i] = cap
                if (i < pos.size - 1) {
                    val need = half[i] + BUNDLE_SPACING + half[i + 1]
                    if (pos[i] > pos[i + 1] - need) pos[i] = pos[i + 1] - need
                }
            }
            // 面に収まり切らない場合は多少の圧縮を許容しつつ下限だけ守る (境界は clampToFace が最終保証)。
            for (i in pos.indices) {
                val floor = faceLo + CORNER_INSET + half[i]
                if (pos[i] < floor) pos[i] = floor
            }
            sorted.forEachIndexed { i, u ->
                val p = u.attach.port
                val np = if (face == Face.RIGHT || face == Face.LEFT) Point(p.x, pos[i]) else Point(pos[i], p.y)
                val na = Attach(np, face)
                if (u.atA) u.geo.attachA = na else u.geo.attachB = na
            }
        }
    }

    /** Half of the tangent-direction band width bundle [geo] occupies on [face] of [rect]. */
    private fun endHalfSpread(geo: BundleGeo, rect: Rect, face: Face): Double {
        val k = geo.sortedEdges.size
        if (k <= 1) return 0.0
        return (k - 1) * tangentSpacing(geo.spacing, tangent(face), geo.d, faceLength(rect, face), k) / 2.0
    }

    /** A bundle's centre connection to one node: the on-border centre [port] and its [face]. */
    private class Attach(val port: Point, val face: Face)

    /**
     * Where the guide line through [base] leaving in direction [dir] exits [rect]: the exit face
     * becomes the bundle's attachment face and the exit point (clamped off the rounded corners) its
     * centre port. Null when the guide misses the rect.
     */
    private fun attach(rect: Rect, base: Point, dir: Point): Attach? {
        val hit = exitHit(rect, base, dir) ?: return null
        return Attach(clampToFace(rect, hit.face, hit.point), hit.face)
    }

    /** A guide-line / rect exit intersection: the boundary [point] and which [face] it lies on. */
    private class FaceHit(val point: Point, val face: Face)

    /**
     * Liang-Barsky style clip of the infinite line `[base] + t * [dir]` against [rect], returning the
     * exit-side intersection (largest `t` still inside) and the face that bounds it. Null when the line
     * misses the rect entirely.
     */
    private fun exitHit(rect: Rect, base: Point, dir: Point): FaceHit? {
        var tEnter = Double.NEGATIVE_INFINITY
        var tExit = Double.POSITIVE_INFINITY
        var face: Face? = null
        if (dir.x == 0.0) {
            if (base.x < rect.x || base.x > rect.right) return null
        } else {
            val tLeft = (rect.x - base.x) / dir.x
            val tRight = (rect.right - base.x) / dir.x
            if (dir.x > 0.0) {
                tEnter = maxOf(tEnter, tLeft)
                if (tRight < tExit) {
                    tExit = tRight
                    face = Face.RIGHT
                }
            } else {
                tEnter = maxOf(tEnter, tRight)
                if (tLeft < tExit) {
                    tExit = tLeft
                    face = Face.LEFT
                }
            }
        }
        if (dir.y == 0.0) {
            if (base.y < rect.y || base.y > rect.bottom) return null
        } else {
            val tTop = (rect.y - base.y) / dir.y
            val tBottom = (rect.bottom - base.y) / dir.y
            if (dir.y > 0.0) {
                tEnter = maxOf(tEnter, tTop)
                if (tBottom < tExit) {
                    tExit = tBottom
                    face = Face.BOTTOM
                }
            } else {
                tEnter = maxOf(tEnter, tBottom)
                if (tTop < tExit) {
                    tExit = tTop
                    face = Face.TOP
                }
            }
        }
        if (face == null || tExit < tEnter) return null
        return FaceHit(Point(base.x + dir.x * tExit, base.y + dir.y * tExit), face)
    }

    // ---- obstacles ----

    /**
     * The rects an edge must avoid: every node rect except its own two endpoints, plus the title strip
     * of every composite that does not contain (or equal) either endpoint — an edge entering or leaving
     * a group legitimately crosses that group's border, so its own composite's strip is never a full
     * obstacle. The **title text** rect (estimated from the sealed name) is avoided even for the own
     * composite: crossing the border is fine, striking through the group heading is not.
     */
    private fun obstaclesFor(edge: GraphEdge, graph: DiagramGraph, layout: GraphLayout): List<Rect> {
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

    // ---- straight-segment clearance ----

    /** True when segment [a]->[b] passes through none of [obstacles] (used by the face-pair search). */
    private fun isClear(a: Point, b: Point, obstacles: List<Rect>): Boolean =
        obstacles.none { overlapLength(a, b, it) > CROSS_EPS }

    /**
     * True when segment [a]->[b] does not clip the interior of either endpoint's own rect ([aR]/[bR]).
     * A port on a side face approached at a steep angle can make the segment graze the box corner, so
     * the arrowhead / tail ends up drawn *under* the node box. Face re-homings that do this are
     * rejected (the natural top/bottom port for adjacent vertical neighbours is kept instead).
     */
    private fun endpointClear(a: Point, b: Point, aR: Rect, bR: Rect): Boolean =
        overlapLength(a, b, aR) <= CROSS_EPS && overlapLength(a, b, bR) <= CROSS_EPS

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

/** True when [p] lies strictly inside this rect (border excluded). */
private fun Rect.contains(p: Point): Boolean =
    p.x > x && p.x < right && p.y > y && p.y < bottom

/** The rect of an edge endpoint: its node rect, or the composite box for a group-target transition. */
internal fun GraphLayout.endpointRect(id: NodeId): Rect? = nodeRects[id] ?: compositeRects[id]
