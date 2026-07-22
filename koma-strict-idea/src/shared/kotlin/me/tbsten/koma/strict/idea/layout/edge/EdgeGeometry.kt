package me.tbsten.koma.strict.idea.layout.edge

import me.tbsten.koma.strict.idea.ir.NodeId
import me.tbsten.koma.strict.idea.layout.GraphLayout
import me.tbsten.koma.strict.idea.layout.Point
import me.tbsten.koma.strict.idea.layout.Rect
import kotlin.math.abs

/** Which side of a node an edge attaches to. */
internal enum class Face { RIGHT, LEFT, BOTTOM, TOP }

/** Keeps a port off a node's rounded corner (ports live on the flat part of a face). */
internal const val CORNER_INSET = 6.0

/** A bundle's centre connection to one node: the on-border centre [port] and its [face]. */
internal class Attach(val port: Point, val face: Face)

/** A guide-line / rect exit intersection: the boundary [point] and which [face] it lies on. */
internal class FaceHit(val point: Point, val face: Face)

/** Faces to try for one end when the natural straight middle is blocked (natural face first). */
internal fun faceCandidates(natural: Face, dir: Point): List<Face> = when (natural) {
    Face.RIGHT, Face.LEFT ->
        if (dir.y >= 0.0) listOf(natural, Face.BOTTOM, Face.TOP) else listOf(natural, Face.TOP, Face.BOTTOM)
    Face.TOP, Face.BOTTOM ->
        if (dir.x >= 0.0) listOf(natural, Face.RIGHT, Face.LEFT) else listOf(natural, Face.LEFT, Face.RIGHT)
}

/** The centre port where the guide through [base] (direction [dir]) meets [face]'s border line. */
internal fun portOnFace(rect: Rect, face: Face, base: Point, dir: Point): Point = when (face) {
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

/** Unit tangent of a face (the direction ports are distributed along). */
internal fun tangent(face: Face): Point = when (face) {
    Face.RIGHT, Face.LEFT -> Point(0.0, 1.0)
    Face.TOP, Face.BOTTOM -> Point(1.0, 0.0)
}

/** Length of [face]'s side of [rect] (the room available for distributing ports). */
internal fun faceLength(rect: Rect, face: Face): Double = when (face) {
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
internal fun tangentSpacing(spacing: Double, t: Point, d: Point, faceLen: Double, k: Int): Double {
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
internal fun recentre(rect: Rect, face: Face, port: Point, halfSpread: Double): Point = when (face) {
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
internal fun clampToFace(rect: Rect, face: Face, p: Point): Point {
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

/**
 * Where the guide line through [base] leaving in direction [dir] exits [rect]: the exit face
 * becomes the bundle's attachment face and the exit point (clamped off the rounded corners) its
 * centre port. Null when the guide misses the rect.
 */
internal fun attach(rect: Rect, base: Point, dir: Point): Attach? {
    val hit = exitHit(rect, base, dir) ?: return null
    return Attach(clampToFace(rect, hit.face, hit.point), hit.face)
}

/**
 * Liang-Barsky style clip of the infinite line `[base] + t * [dir]` against [rect], returning the
 * exit-side intersection (largest `t` still inside) and the face that bounds it. Null when the line
 * misses the rect entirely.
 */
internal fun exitHit(rect: Rect, base: Point, dir: Point): FaceHit? {
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

/** Deterministic unordered key so `A->B` and `B->A` land in the same bundle. */
internal fun pairKey(a: NodeId, b: NodeId): Pair<NodeId, NodeId> =
    if (a.toString() <= b.toString()) a to b else b to a

/** The rect of an edge endpoint: its node rect, or the composite box for a group-target transition. */
internal fun GraphLayout.endpointRect(id: NodeId): Rect? = nodeRects[id] ?: compositeRects[id]
