package me.tbsten.koma.strict.idea.model

/**
 * A node of the state declaration tree in the slim diagram model: root ([RootState]) /
 * intermediate sealed ([GroupState]) / concrete leaf ([LeafState]).
 *
 * Only the information the diagram needs is kept (names, nesting, triggers, exit). Codegen-only
 * details of the KSP StoreSpec model (props, visibility, declaration kind, event constructor params)
 * are intentionally dropped — this is the "figure-oriented slim model" of `ide.md`.
 */
sealed interface DiagramStateNode {
    /** Short display name (`Idle`). */
    val simpleName: String

    /** Root-relative id of this node. */
    val id: StateId

    /** `@OnAction` triggers. On the root / an intermediate sealed node these are scope-shared actions. */
    val actions: List<ActionTrigger>

    /** `@OnRecover` triggers. On the root / an intermediate sealed node these are scope-shared recovers. */
    val recovers: List<RecoverTrigger>

    /** `@OnExit` declaration, if any. */
    val exit: ExitInfo?

    /**
     * Back-reference to this node's source declaration for click-to-declaration. Set for every node
     * kind (root / group / leaf) by the Analysis-API frontend; null in pure tests.
     */
    val source: SourceAnchor?
}

/** A node with child states (root / intermediate sealed). */
sealed interface DiagramStateParent : DiagramStateNode {
    /** Child nodes (source declaration order). */
    val children: List<DiagramStateNode>
}

/** The sealed root annotated with `@StoreSpec`. */
data class RootState(
    override val simpleName: String,
    override val children: List<DiagramStateNode>,
    override val actions: List<ActionTrigger> = emptyList(),
    override val recovers: List<RecoverTrigger> = emptyList(),
    override val exit: ExitInfo? = null,
    override val source: SourceAnchor? = null,
) : DiagramStateParent {
    override val id: StateId get() = StateId.Root
}

/** An intermediate sealed node. */
data class GroupState(
    override val simpleName: String,
    override val id: StateId,
    override val children: List<DiagramStateNode>,
    /**
     * Scope-shared `@OnEnter` declared on the intermediate sealed node itself (koma copies it to
     * each leaf; the spec keeps it on the group). Lowered like the group's shared actions: targets
     * leave the any-state node, `Stay` becomes the group's own scope stay.
     */
    val enter: EnterTrigger? = null,
    override val actions: List<ActionTrigger> = emptyList(),
    override val recovers: List<RecoverTrigger> = emptyList(),
    override val exit: ExitInfo? = null,
    override val source: SourceAnchor? = null,
) : DiagramStateParent

/** A concrete leaf state. The only node kind that may declare `@OnEnter`. */
data class LeafState(
    override val simpleName: String,
    override val id: StateId,
    /** `@OnEnter` trigger, if any. */
    val enter: EnterTrigger? = null,
    override val actions: List<ActionTrigger> = emptyList(),
    override val recovers: List<RecoverTrigger> = emptyList(),
    override val exit: ExitInfo? = null,
    override val source: SourceAnchor? = null,
) : DiagramStateNode

/**
 * Depth-first walk over the whole tree (pre-order: node before its children, children in source
 * order). Implemented iteratively with an explicit stack so a pathologically deep declaration tree
 * cannot blow the JVM stack (a recursive walk risks StackOverflowError, which must never be caught
 * and turned into a "degraded" diagram — see the frontend's fatal-error handling).
 */
fun DiagramStateNode.walk(): Sequence<DiagramStateNode> = sequence {
    val stack = ArrayDeque<DiagramStateNode>()
    stack.addLast(this@walk)
    while (stack.isNotEmpty()) {
        val node = stack.removeLast()
        yield(node)
        if (node is DiagramStateParent) {
            // 子は逆順で push すると pop 時に元の宣言順で yield される (pre-order を保つ)。
            for (i in node.children.indices.reversed()) stack.addLast(node.children[i])
        }
    }
}

/** All concrete leaves under this node (source order). */
fun DiagramStateNode.leaves(): List<LeafState> = walk().filterIsInstance<LeafState>().toList()

/** Finds a node by its root-relative id, or null. */
fun DiagramStateNode.findById(target: StateId): DiagramStateNode? =
    walk().firstOrNull { it.id == target }

/**
 * True when this node declares any trigger / exit value the frontend could not resolve: a foreign or
 * error-type `nextState` element, an unresolvable `emit` element, or an unresolvable `@OnAction` /
 * `@OnRecover` type argument (surfaced as [UNRESOLVED_MARKER]). Drives the "analysis incomplete"
 * banner so partially-unresolved code is never shown as if it declared nothing.
 */
fun DiagramStateNode.hasUnresolvedDeclarations(): Boolean {
    val triggers = buildList {
        if (this@hasUnresolvedDeclarations is LeafState) enter?.let(::add)
        addAll(actions)
        addAll(recovers)
    }
    return triggers.any { it.hasUnresolvedValue } ||
        actions.any { it.actionName == UNRESOLVED_MARKER } ||
        recovers.any { it.exceptionName == UNRESOLVED_MARKER } ||
        exit?.emits?.any { it == UNRESOLVED_MARKER } == true
}
