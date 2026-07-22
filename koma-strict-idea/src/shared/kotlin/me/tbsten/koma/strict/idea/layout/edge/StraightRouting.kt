package me.tbsten.koma.strict.idea.layout.edge

import me.tbsten.koma.strict.idea.ir.DiagramGraph
import me.tbsten.koma.strict.idea.ir.NodeId
import me.tbsten.koma.strict.idea.layout.GraphLayout
import me.tbsten.koma.strict.idea.layout.Point
import me.tbsten.koma.strict.idea.layout.Rect
import kotlin.math.hypot

// ---- straight-middle guarantee (曲げない) ----

/**
 * Greedy transparent-crossing reduction: with straight-only lines, two bundles can cross in an
 * `X`. When an alternative *clear* face pair for one bundle strictly lowers the total number of
 * segment crossings, the bundle re-homes there. Two rounds, first-improvement greedy.
 */
internal fun reduceCrossings(graph: DiagramGraph, layout: GraphLayout, geos: List<BundleGeo>): Boolean {
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

/**
 * Straight-line guarantee: an edge is never bent. When the natural face pair's straight line
 * would pass through a node, a composite title strip, or a self-loop zone, the whole bundle
 * re-homes onto the first face pair whose straight line is clear (shortest wins) — e.g.
 * `any-named`'s `leave` runs through the clear corridor below the middle node instead of bending
 * around it. If no pair is clear, the natural straight line stays as-is: bending is never an
 * option.
 */
internal fun ensureStraightClear(graph: DiagramGraph, layout: GraphLayout, geos: List<BundleGeo>) {
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
