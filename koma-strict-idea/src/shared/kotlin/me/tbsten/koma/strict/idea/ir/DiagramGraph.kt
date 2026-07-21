package me.tbsten.koma.strict.idea.ir

import me.tbsten.koma.strict.idea.model.SourceAnchor
import me.tbsten.koma.strict.idea.model.StateId

/**
 * Stable identifier of a graph node (also used as a map key by the layout, draw, and hit-test).
 *
 * The id carries the node *kind* so a pseudo node (start / any-state / composite box) can never share
 * a key with a concrete state, even when a leaf is literally named `any` or `[*]`. A plain string
 * namespace collapsed all kinds together: the root any-state used the key `"any"`, which a legal leaf
 * named `any` also produced, giving two graph nodes the same key and crashing the layout's layering
 * loop (`P1-04`). Keeping the kind in the type makes every key unique by construction.
 *
 * [display] is a human-readable form for debugging / tests only — it is deliberately *not* the map
 * key (two different kinds may render the same string, e.g. a `State` and a `Composite` on one path).
 */
sealed interface NodeId {
    /** Human-readable form for debugging / tests. Never used as a map key. */
    val display: String

    /** The `[*]` initial pseudo node (there is at most one per graph). */
    data object Start : NodeId {
        override val display: String get() = "[*]"
    }

    /** A concrete leaf state, keyed by its root-relative [path]. */
    data class State(val path: StateId) : NodeId {
        override val display: String get() = path.dotted
    }

    /** An any-state pseudo node for a [scope] (root or an intermediate sealed group). */
    data class Any(val scope: StateId) : NodeId {
        override val display: String get() = if (scope.isRoot) "any" else "any:${scope.dotted}"
    }

    /** A composite box for an intermediate sealed group, keyed by its [path]. */
    data class Composite(val path: StateId) : NodeId {
        override val display: String get() = path.dotted
    }

    companion object {
        /** Convenience for a leaf id from path segments (`state("Stable", "Idle")`). */
        fun state(vararg segments: String): State = State(StateId(*segments))

        /** Convenience for a composite-box id from path segments (`composite("Stable")`). */
        fun composite(vararg segments: String): Composite = Composite(StateId(*segments))
    }
}

/**
 * A node of the lowered diagram graph.
 *
 * The tree of the slim model is flattened here into a drawable node/edge graph: leaves become
 * [StateGraphNode]s, `@StoreSpec.initial` gets a single [StartNode] (`[*]`), and each scope with
 * shared actions / recovers gets an [AnyStateNode] pseudo node.
 */
sealed interface GraphNode {
    val id: NodeId
}

/** The `[*]` initial pseudo node (present only when the store declares `initial`). */
data class StartNode(override val id: NodeId = START_ID) : GraphNode {
    companion object {
        val START_ID: NodeId = NodeId.Start
    }
}

/** A concrete leaf state node. */
data class StateGraphNode(
    override val id: NodeId,
    val simpleName: String,
    val path: StateId,
    /** Whether the leaf is reachable from `initial` (drawn in a warning color when false). */
    val reachable: Boolean,
    /** `@OnExit` badge text beside the node, if the leaf declares exit. */
    val exitBadge: String? = null,
    /** Back-reference to the source declaration for click-to-declaration. */
    val source: SourceAnchor? = null,
) : GraphNode

/**
 * An any-state pseudo node for scope-shared actions / recovers. The root scope renders as
 * "any state"; an intermediate sealed scope renders as "any <Group>" inside its composite.
 */
data class AnyStateNode(
    override val id: NodeId,
    val label: String,
    /** Scope the pseudo node stands for (root = [StateId.Root]). */
    val scope: StateId,
    /** `@OnExit` badge text for a scope-shared exit, if any. */
    val exitBadge: String? = null,
    /** Declaration of the scope (root / group) for click-to-declaration from the pseudo node. */
    val source: SourceAnchor? = null,
) : GraphNode {
    companion object {
        val ROOT_ANY_ID: NodeId = NodeId.Any(StateId.Root)
        fun idFor(scope: StateId): NodeId = NodeId.Any(scope)
    }
}

/** Kind of edge, so the renderer can style each trigger family. */
enum class EdgeKind { INITIAL, ENTER, ACTION, RECOVER }

/**
 * A directed edge. Labels follow the Mealy convention `trigger / Event`; a [stay] edge is a
 * self-loop and reads `trigger (stay)`.
 */
data class GraphEdge(
    val fromId: NodeId,
    val toId: NodeId,
    val kind: EdgeKind,
    /** Trigger token: `onEnter` / decapitalized action name / `on <Exception>`. Empty for INITIAL. */
    val trigger: String,
    val emits: List<String> = emptyList(),
    /** True for a `Stay` self-loop ([fromId] == [toId]). */
    val stay: Boolean = false,
) {
    /** Rendered edge label, e.g. `onEnter / LoadFailed`, `loadMore (stay)`. Empty for INITIAL. */
    val label: String
        get() = buildString {
            append(trigger)
            if (stay) append(" (stay)")
            if (emits.isNotEmpty()) {
                append(" / ")
                append(emits.joinToString(", "))
            }
        }.trim()
}

/**
 * A scope-shared `Stay` transition drawn as a self-loop **on the scope's enclosure border** (the
 * root frame for the root scope, the composite box for a group scope) instead of on an any-state
 * pseudo node. [scope] is the owning scope path ([StateId.Root] for the whole store).
 */
data class ScopeStay(
    val scope: StateId,
    val kind: EdgeKind,
    val trigger: String,
    val emits: List<String> = emptyList(),
) {
    /** Rendered label, same format as [GraphEdge.label] with the implicit `(stay)` marker. */
    val label: String
        get() = buildString {
            append(trigger)
            append(" (stay)")
            if (emits.isNotEmpty()) {
                append(" / ")
                append(emits.joinToString(", "))
            }
        }.trim()
}

/** An intermediate sealed composite box enclosing its member nodes. */
data class CompositeBox(
    val id: NodeId,
    val simpleName: String,
    val path: StateId,
    /** Ids of the nodes drawn inside this box (leaves + nested any-state + nested boxes). */
    val memberIds: List<NodeId>,
    /** Declaration of the group for click-to-declaration from the box label strip. */
    val source: SourceAnchor? = null,
)

/** The lowered, renderer-independent diagram graph (`ide.md` graph IR). */
data class DiagramGraph(
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>,
    val composites: List<CompositeBox> = emptyList(),
    /** Scope-shared stays drawn on the scope enclosure border ([ScopeStay.scope] = root or group). */
    val scopeStays: List<ScopeStay> = emptyList(),
) {
    fun node(id: NodeId): GraphNode? = nodes.firstOrNull { it.id == id }
    val stateNodes: List<StateGraphNode> get() = nodes.filterIsInstance<StateGraphNode>()
    val anyStateNodes: List<AnyStateNode> get() = nodes.filterIsInstance<AnyStateNode>()
}
