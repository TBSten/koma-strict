package me.tbsten.koma.strict.idea.ui

import me.tbsten.koma.strict.idea.ir.AnyStateNode
import me.tbsten.koma.strict.idea.ir.DiagramGraph
import me.tbsten.koma.strict.idea.ir.GraphEdge
import me.tbsten.koma.strict.idea.ir.NodeId
import me.tbsten.koma.strict.idea.ir.ScopeStay
import me.tbsten.koma.strict.idea.ir.StateGraphNode
import me.tbsten.koma.strict.idea.layout.GraphLayout
import me.tbsten.koma.strict.idea.layout.Point
import me.tbsten.koma.strict.idea.model.SourceAnchor
import me.tbsten.koma.strict.idea.model.StateId
import kotlin.math.hypot

/**
 * Height (in layout / dp units) of the clickable label strip along the top of a composite box. Only
 * this band navigates to the group declaration; the box interior is left to its member nodes so a
 * whole-box hit region would never shadow the leaf / any-state clicks drawn inside it. Kept below the
 * composite padding (34dp) so the strip never overlaps a member node's rectangle.
 */
internal const val COMPOSITE_LABEL_STRIP_HEIGHT: Double = 24.0

/**
 * Resolves a diagram-space click (in layout / dp units, origin top-left) back to the [SourceAnchor]
 * of the declaration to navigate to, or null when the point hits nothing navigable.
 *
 * Priority mirrors the back-to-front draw order (`DiagramDraw`): a concrete [StateGraphNode] or an
 * [AnyStateNode] under the point wins over a composite box, and among nodes the front-most (last
 * drawn) wins so a pushed-out overlap never routes to a node behind it. Composite boxes are only
 * hittable on their top label strip and are checked deepest-first, so clicking a nested group's label
 * navigates to that group rather than its enclosing one. The `[*]` start node carries no source and
 * is ignored.
 */
internal fun DiagramGraph.hitSource(layout: GraphLayout, x: Double, y: Double): SourceAnchor? {
    // 1. 最前面のノード (leaf / any-state)。描画は nodes 順で back-to-front なので逆順で最前面を優先する。
    for (node in nodes.asReversed()) {
        val source = when (node) {
            is StateGraphNode -> node.source
            is AnyStateNode -> node.source
            else -> null
        } ?: continue
        val r = layout.nodeRects[node.id] ?: continue
        if (x >= r.x && x <= r.right && y >= r.y && y <= r.bottom) return source
    }
    // 2. composite box のラベル帯 (箱の上端)。入れ子は deepest-first で内側の group を優先する。
    for (box in composites.sortedByDescending { it.path.segments.size }) {
        val source = box.source ?: continue
        val r = layout.compositeRects[box.id] ?: continue
        if (x >= r.x && x <= r.right && y >= r.y && y <= r.y + COMPOSITE_LABEL_STRIP_HEIGHT) return source
    }
    return null
}

/**
 * A user selection in the diagram that drives focus (`ide-2.md`): either a state frame ([Node]) or a
 * transition arrow ([Edge]). Resolved from a click by [hitElement]; folded into a [FocusSet] by
 * [focusFrom]. Deliberately a two-case model — composite boxes and the `[*]` start dot are not
 * focus targets in the spec.
 */
sealed interface DiagramSelection {
    /** A state / any-state frame was selected. */
    data class Node(val id: NodeId) : DiagramSelection

    /** A transition arrow was selected (a routed edge, including a `Stay` self-loop). */
    data class Edge(val edge: GraphEdge) : DiagramSelection
}

/**
 * The elements kept at full opacity while a selection is focused; every element *not* in the set is
 * drawn dimmed (`ide-2.md`: focus-out elements go to half alpha). Built by [focusFrom]. A `null`
 * `FocusSet` (never an empty one) is the caller's signal for "no focus — draw everything normally".
 */
internal class FocusSet(
    val nodeIds: Set<NodeId>,
    val edges: Set<GraphEdge>,
    val scopeStays: Set<ScopeStay>,
) {
    fun isNodeFocused(id: NodeId): Boolean = id in nodeIds
    fun isEdgeFocused(edge: GraphEdge): Boolean = edge in edges
    fun isScopeStayFocused(stay: ScopeStay): Boolean = stay in scopeStays
}

/**
 * Pure focus-set computation for a [selection] (`ide-2.md`), independent of layout so it can be unit
 * tested:
 * - **[DiagramSelection.Node]**: every edge incident to the node (in / out, including its own `Stay`
 *   self-loops) plus each of those edges' counterpart nodes, plus the scope-shared stays of every
 *   scope that contains the node (a root-shared stay fires on every state, a group-shared stay on the
 *   states inside that group).
 * - **[DiagramSelection.Edge]**: just that edge and its two endpoint nodes.
 */
internal fun DiagramGraph.focusFrom(selection: DiagramSelection): FocusSet = when (selection) {
    is DiagramSelection.Edge -> {
        val e = selection.edge
        FocusSet(nodeIds = setOf(e.fromId, e.toId), edges = setOf(e), scopeStays = emptySet())
    }

    is DiagramSelection.Node -> {
        val id = selection.id
        val incident = edges.filter { it.fromId == id || it.toId == id }
        val nodeIds = incident.flatMapTo(mutableSetOf()) { listOf(it.fromId, it.toId) }.apply { add(id) }
        val path = nodePath(id)
        val stays = if (path == null) {
            emptySet()
        } else {
            scopeStays.filterTo(mutableSetOf()) { it.scope.containsOrEquals(path) }
        }
        FocusSet(nodeIds = nodeIds, edges = incident.toSet(), scopeStays = stays)
    }
}

/** The representative state path of a node for scope-stay ownership (null for the `[*]` start dot). */
private fun DiagramGraph.nodePath(id: NodeId): StateId? = when (val n = node(id)) {
    is StateGraphNode -> n.path
    is AnyStateNode -> n.scope
    else -> null
}

/** True when this scope path is an ancestor of (or equal to) [other] — root contains every path. */
private fun StateId.containsOrEquals(other: StateId): Boolean =
    other.segments.size >= segments.size && other.segments.subList(0, segments.size) == segments

/** Distance (layout units) within which a click counts as landing on an edge's drawn line. */
internal const val EDGE_HIT_TOLERANCE: Double = 6.0

/**
 * Resolves a diagram-space click (layout / dp units) to the [DiagramSelection] to focus, or null when
 * the point hits neither a node nor a routed edge line. A front-most node rectangle wins over an edge
 * (mirroring [hitSource]'s node-first priority); otherwise the click must land within
 * [edgeTolerance] of an edge's drawn poly-line to select it — a thin band along the actual line, so
 * diagonal segments and multi-point go-around detours are hit only where the arrow is really drawn.
 *
 * [routes] must be the same `EdgeRouting.routeAll` map the renderer draws with, so the hit band tracks
 * the drawn geometry exactly. Self-loops (not in [routes]) are reached via their owner node instead.
 */
internal fun DiagramGraph.hitElement(
    layout: GraphLayout,
    routes: Map<GraphEdge, List<Point>>,
    x: Double,
    y: Double,
    edgeTolerance: Double = EDGE_HIT_TOLERANCE,
): DiagramSelection? {
    // 1. 最前面のノード矩形。描画順 (back-to-front) の逆で最前面を優先する (hitSource と同じ規則)。
    for (node in nodes.asReversed()) {
        val r = layout.nodeRects[node.id] ?: continue
        if (x >= r.x && x <= r.right && y >= r.y && y <= r.bottom) return DiagramSelection.Node(node.id)
    }
    // 2. 最も近い routed edge の折れ線 (許容距離以内)。斜め線・折れ線でも線に沿った帯だけがヒットする。
    var best: GraphEdge? = null
    var bestDist = Double.MAX_VALUE
    for ((edge, pts) in routes) {
        val d = distanceToPolyline(pts, x, y)
        if (d < bestDist) {
            bestDist = d
            best = edge
        }
    }
    return if (best != null && bestDist <= edgeTolerance) DiagramSelection.Edge(best) else null
}

/** Minimum distance from ([x],[y]) to poly-line [pts] (each segment); `MAX_VALUE` for <2 points. */
private fun distanceToPolyline(pts: List<Point>, x: Double, y: Double): Double {
    if (pts.size < 2) return Double.MAX_VALUE
    var best = Double.MAX_VALUE
    for (i in 0 until pts.size - 1) {
        val d = distanceToSegment(pts[i], pts[i + 1], x, y)
        if (d < best) best = d
    }
    return best
}

/** Distance from ([x],[y]) to segment [a]->[b] (projection clamped to the segment ends). */
private fun distanceToSegment(a: Point, b: Point, x: Double, y: Double): Double {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val len2 = dx * dx + dy * dy
    val t = if (len2 < 1e-9) 0.0 else (((x - a.x) * dx + (y - a.y) * dy) / len2).coerceIn(0.0, 1.0)
    return hypot(x - (a.x + dx * t), y - (a.y + dy * t))
}
