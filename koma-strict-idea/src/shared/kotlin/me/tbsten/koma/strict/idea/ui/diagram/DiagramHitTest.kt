package me.tbsten.koma.strict.idea.ui.diagram

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
 * A user selection in the diagram that drives focus (`ide-2.md` / `ide-3.md`). Four kinds of focus
 * target: a state frame ([Node]), a transition arrow ([Edge], including a node `Stay` self-loop), a
 * nest-state box ([Composite]), or a scope-shared stay arc ([Stay]). The `[*]` start dot is not a
 * focus target. Resolved from a click by [hitElement] (nodes/composites) and the draw-time sink
 * (labels/arcs); a set of these is folded into a [FocusSet] by [focusFrom]. Multiple selections
 * coexist (Shift-click).
 */
sealed interface DiagramSelection {
    /** A state / any-state frame was selected. */
    data class Node(val id: NodeId) : DiagramSelection

    /** A transition arrow was selected (a routed edge, including a node `Stay` self-loop). */
    data class Edge(val edge: GraphEdge) : DiagramSelection

    /** A nest-state (composite) box was selected — [id] is its [NodeId.Composite]. */
    data class Composite(val id: NodeId) : DiagramSelection

    /** A scope-shared stay arc (`@On…(stay)` on a scope enclosure) was selected. */
    data class Stay(val stay: ScopeStay) : DiagramSelection
}

/**
 * The elements to keep bright while something is focused, plus the elements that are actually
 * *selected* (`ide-3.md` three tiers). `isXxxFocused` = tier 2 (selected **or** directly connected →
 * full opacity); `isXxxSelected` = tier 1 (the click target → emphasized on top). Everything in
 * neither is tier 3 (dimmed). Built by [focusFrom]. A `null` `FocusSet` (never an empty one) is the
 * caller's signal for "no focus — draw everything normally".
 */
internal class FocusSet(
    val nodeIds: Set<NodeId>,
    val edges: Set<GraphEdge>,
    val scopeStays: Set<ScopeStay>,
    /** Composite boxes kept bright (contain a focused node, or are the selected box / its subtree). */
    val compositeIds: Set<NodeId> = emptySet(),
    val selectedNodes: Set<NodeId> = emptySet(),
    val selectedEdges: Set<GraphEdge> = emptySet(),
    val selectedComposites: Set<NodeId> = emptySet(),
    val selectedStays: Set<ScopeStay> = emptySet(),
) {
    fun isNodeFocused(id: NodeId): Boolean = id in nodeIds
    fun isEdgeFocused(edge: GraphEdge): Boolean = edge in edges
    fun isScopeStayFocused(stay: ScopeStay): Boolean = stay in scopeStays
    fun isCompositeFocused(id: NodeId): Boolean = id in compositeIds

    fun isNodeSelected(id: NodeId): Boolean = id in selectedNodes
    fun isEdgeSelected(edge: GraphEdge): Boolean = edge in selectedEdges
    fun isCompositeSelected(id: NodeId): Boolean = id in selectedComposites
    fun isStaySelected(stay: ScopeStay): Boolean = stay in selectedStays
}

/** Convenience for a single selection (folds through the set-based [focusFrom]). */
internal fun DiagramGraph.focusFrom(selection: DiagramSelection): FocusSet = focusFrom(setOf(selection))

/**
 * Pure focus-set computation for a set of [selections] (`ide-3.md`), independent of layout so it can
 * be unit tested. The result is the **union** of each selection's contribution; each selection also
 * records itself into the `selected*` sets (tier 1 emphasis). Per kind:
 * - **[DiagramSelection.Node]**: every edge incident to the node (in / out, including its own `Stay`
 *   self-loops) plus each of those edges' counterpart nodes, plus the scope-shared stays of every
 *   scope that contains the node, plus the composite boxes enclosing any of those nodes.
 * - **[DiagramSelection.Edge]**: that edge, its two endpoint nodes, and their enclosing boxes.
 * - **[DiagramSelection.Composite]**: the box, all descendant state / any nodes, every edge incident
 *   to a descendant (so an edge leaving the group stays bright; its outside counterpart node does
 *   **not**), the scope-shared stays inside the subtree, and the descendant boxes.
 * - **[DiagramSelection.Stay]**: the stay itself, plus the states inside its scope and the boxes of
 *   that scope subtree (the states the shared stay fires on).
 */
internal fun DiagramGraph.focusFrom(selections: Set<DiagramSelection>): FocusSet {
    val nodeIds = mutableSetOf<NodeId>()
    val edgeSet = mutableSetOf<GraphEdge>()
    val stays = mutableSetOf<ScopeStay>()
    val compositeIds = mutableSetOf<NodeId>()
    val selNodes = mutableSetOf<NodeId>()
    val selEdges = mutableSetOf<GraphEdge>()
    val selComposites = mutableSetOf<NodeId>()
    val selStays = mutableSetOf<ScopeStay>()

    for (selection in selections) {
        when (selection) {
            is DiagramSelection.Node -> {
                val id = selection.id
                selNodes += id
                val incident = edges.filter { it.fromId == id || it.toId == id }
                val branch = incident.flatMapTo(mutableSetOf()) { listOf(it.fromId, it.toId) }.apply { add(id) }
                nodeIds += branch
                edgeSet += incident
                nodePath(id)?.let { p -> stays += scopeStays.filter { it.scope.containsOrEquals(p) } }
                compositeIds += compositesCovering(branch.mapNotNull { nodePath(it) })
            }

            is DiagramSelection.Edge -> {
                val e = selection.edge
                selEdges += e
                edgeSet += e
                nodeIds += e.fromId
                nodeIds += e.toId
                compositeIds += compositesCovering(listOf(e.fromId, e.toId).mapNotNull { nodePath(it) })
            }

            is DiagramSelection.Composite -> {
                val boxId = selection.id
                selComposites += boxId
                (boxId as? NodeId.Composite)?.path?.let { boxPath ->
                    val desc = nodes.filterTo(mutableSetOf()) { n -> nodePath(n.id)?.let { boxPath.containsOrEquals(it) } == true }
                        .mapTo(mutableSetOf()) { it.id }
                    nodeIds += desc
                    edgeSet += edges.filter { it.fromId in desc || it.toId in desc }
                    stays += scopeStays.filter { boxPath.containsOrEquals(it.scope) }
                    compositeIds += composites.filter { boxPath.containsOrEquals(it.path) }.map { it.id }
                }
            }

            is DiagramSelection.Stay -> {
                val s = selection.stay
                selStays += s
                stays += s
                nodeIds += nodes.filter { n -> nodePath(n.id)?.let { s.scope.containsOrEquals(it) } == true }.map { it.id }
                compositeIds += composites.filter { s.scope.containsOrEquals(it.path) }.map { it.id }
            }
        }
    }
    return FocusSet(
        nodeIds = nodeIds,
        edges = edgeSet,
        scopeStays = stays,
        compositeIds = compositeIds,
        selectedNodes = selNodes,
        selectedEdges = selEdges,
        selectedComposites = selComposites,
        selectedStays = selStays,
    )
}

/** Composite boxes whose path is an ancestor of (or equal to) any of [paths] — the boxes to keep bright. */
private fun DiagramGraph.compositesCovering(paths: Collection<StateId>): Set<NodeId> =
    composites.filter { box -> paths.any { box.path.containsOrEquals(it) } }.mapTo(mutableSetOf()) { it.id }

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
    // 2. composite box のラベル帯 (箱の上端) を選択対象にする (`ide-3.md`)。入れ子は deepest-first。
    //    帯だけに絞るのは hitSource と同じ理由: 箱の内部は member ノードのクリックに譲る。
    for (box in composites.sortedByDescending { it.path.segments.size }) {
        val r = layout.compositeRects[box.id] ?: continue
        if (x >= r.x && x <= r.right && y >= r.y && y <= r.y + COMPOSITE_LABEL_STRIP_HEIGHT) {
            return DiagramSelection.Composite(box.id)
        }
    }
    // 3. 最も近い routed edge の折れ線 (許容距離以内)。斜め線・折れ線でも線に沿った帯だけがヒットする。
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
