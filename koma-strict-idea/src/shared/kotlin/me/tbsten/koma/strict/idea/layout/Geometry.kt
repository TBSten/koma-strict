package me.tbsten.koma.strict.idea.layout

/** Direction the BFS layers grow. `LR` = left-to-right (the `ide.md` default), `TB` = top-to-bottom. */
enum class LayoutDirection { LR, TB }

/** A point in diagram coordinates (origin top-left, y grows downward — screen convention). */
data class Point(val x: Double, val y: Double)

/** A size in diagram coordinates. */
data class Size(val width: Double, val height: Double)

/** An axis-aligned rectangle in diagram coordinates. */
data class Rect(val x: Double, val y: Double, val width: Double, val height: Double) {
    val right: Double get() = x + width
    val bottom: Double get() = y + height
    val center: Point get() = Point(x + width / 2, y + height / 2)

    /** Smallest rectangle that contains both this and [other]. */
    fun union(other: Rect): Rect {
        val minX = minOf(x, other.x)
        val minY = minOf(y, other.y)
        val maxX = maxOf(right, other.right)
        val maxY = maxOf(bottom, other.bottom)
        return Rect(minX, minY, maxX - minX, maxY - minY)
    }

    /** Grows the rectangle outward by [padding] on every side. */
    fun inflate(padding: Double): Rect =
        Rect(x - padding, y - padding, width + padding * 2, height + padding * 2)

    /** True when this rectangle and [other] have a positive-area overlap (edge-touching does not count). */
    fun intersects(other: Rect): Boolean =
        x < other.right && right > other.x && y < other.bottom && bottom > other.y
}

/**
 * Tunable spacing for the layered layout. Values are in the same coordinate units the Compose Canvas
 * later draws in (density-independent pixels); tests assert against them so keep them explicit.
 */
data class LayoutConfig(
    val nodeWidth: Double = 140.0,
    val nodeHeight: Double = 48.0,
    /** Gap between consecutive layers (along the direction axis). */
    val layerGap: Double = 72.0,
    /** Gap between siblings within a layer (across the direction axis). */
    val siblingGap: Double = 28.0,
    /** Outer margin around the whole diagram. */
    val margin: Double = 24.0,
    /** Side length of the square `[*]` start node. */
    val startSize: Double = 28.0,
    /** Length of the `[*]` start dot -> initial-state edge. Deliberately tiny (independent of [layerGap]) so the initial marker sits right next to its state. */
    val startGap: Double = 24.0,
    /**
     * Padding between a composite box border and its members. Kept >= the self-loop arc lift (32dp in
     * [me.tbsten.koma.strict.idea.ui] `drawSelfLoop`) so a top-row member's `(stay)` loop stays inside
     * the box instead of poking through its top edge.
     */
    val compositePadding: Double = 34.0,
    /** Horizontal gap between a node and its `@OnExit` badge (used only for canvas reservation). */
    val exitBadgeGap: Double = 8.0,
)
