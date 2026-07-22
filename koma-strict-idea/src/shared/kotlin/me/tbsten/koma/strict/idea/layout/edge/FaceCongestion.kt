package me.tbsten.koma.strict.idea.layout.edge

import me.tbsten.koma.strict.idea.ir.NodeId
import me.tbsten.koma.strict.idea.layout.Point
import me.tbsten.koma.strict.idea.layout.Rect
import kotlin.math.abs

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
internal fun relieveCongestedFaces(geos: List<BundleGeo>) {
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
internal fun coordinateFacePorts(geos: List<BundleGeo>) {
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
