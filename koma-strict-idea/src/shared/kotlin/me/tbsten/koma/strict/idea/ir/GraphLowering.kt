package me.tbsten.koma.strict.idea.ir

import me.tbsten.koma.strict.idea.model.ActionTrigger
import me.tbsten.koma.strict.idea.model.DiagramStateNode
import me.tbsten.koma.strict.idea.model.DiagramTrigger
import me.tbsten.koma.strict.idea.model.EnterTrigger
import me.tbsten.koma.strict.idea.model.ExitInfo
import me.tbsten.koma.strict.idea.model.GroupState
import me.tbsten.koma.strict.idea.model.LeafState
import me.tbsten.koma.strict.idea.model.RecoverTrigger
import me.tbsten.koma.strict.idea.model.RootState
import me.tbsten.koma.strict.idea.model.StateId
import me.tbsten.koma.strict.idea.model.StoreDiagramModel
import me.tbsten.koma.strict.idea.model.leaves
import me.tbsten.koma.strict.idea.model.walk

/**
 * Lowers the slim [StoreDiagramModel] (a sealed-state *tree*) into a renderer-independent
 * [DiagramGraph] (flat *nodes + edges*), the one place the model's shape is turned into figure
 * primitives:
 *
 * ```
 *   StoreDiagramModel (tree)                 GraphLowering.lower           DiagramGraph (flat)
 *   ────────────────────────                 ───────────────────          ───────────────────
 *   root                                                                  nodes:
 *    ├─ initial = [A]              ─────────────────────────────────▶       StartNode  [*]  + INITIAL edge
 *    ├─ leaf A (enter/actions)     ─── leaf ─────────────────────────▶      StateGraphNode
 *    ├─ group G (intermediate)     ─── intermediate sealed ──────────▶      CompositeBox  (box border)
 *    │   ├─ leaf B                 ─── leaf ─────────────────────────▶      StateGraphNode
 *    │   └─ shared trigger …       ─┬─ targeted (nextState) / @OnExit ▶      AnyStateNode  (source of shared edges)
 *    │                              └─ stay-only ────────────────────▶      ScopeStay     (arc on the enclosure, no any-node)
 *    └─ every trigger              ─── (state, trigger) ─────────────▶      GraphEdge(s)  (+ Stay self-loop when staying allowed)
 * ```
 *
 * So: leaves -> [StateGraphNode], `initial` -> a single [StartNode], intermediate sealed nodes ->
 * [CompositeBox]es, scope-shared triggers that *go somewhere* (a `nextState`) or carry an `@OnExit` ->
 * an [AnyStateNode] pseudo node the shared edges emanate from, scope-shared **stays** -> a [ScopeStay]
 * arc on the scope enclosure (no any-node — see [anyNodeNeeded]), and every trigger -> one or more
 * [GraphEdge]s.
 *
 * A transition whose target is an intermediate sealed node (a *group* rather than a concrete leaf)
 * is kept: its edge carries the group's dotted id as [GraphEdge.toId] (matching the [CompositeBox]
 * id) so the renderer can land the arrow on the composite box border (UML composite-state entry).
 * koma's `nextState` is leaf-only, but the tool window analyzes arbitrary code, so group targets are
 * drawn instead of silently dropped.
 */
object GraphLowering {

    fun lower(model: StoreDiagramModel): DiagramGraph {
        val root = model.root
        val leafPaths: Set<StateId> = root.leaves().map { it.id }.toSet()
        // group (中間 sealed) の path 集合。group を指す遷移エッジは composite box を指すので、
        // その id (NodeId.Composite) を target に持つエッジとして emit する。
        val groupPaths: Set<StateId> = root.walk().filterIsInstance<GroupState>().map { it.id }.toSet()
        // 遷移 target の StateId を、leaf なら NodeId.State・group なら NodeId.Composite へ解決する。
        // どちらでもない (未解決 / foreign) なら null = そのエッジは作らない。
        fun targetNode(path: StateId): NodeId? = when (path) {
            in leafPaths -> NodeId.State(path)
            in groupPaths -> NodeId.Composite(path)
            else -> null
        }

        val nodes = mutableListOf<GraphNode>()
        val edges = mutableListOf<GraphEdge>()
        val composites = mutableListOf<CompositeBox>()

        // [*] 起点ノードと initial エッジ。
        if (model.initial.isNotEmpty()) {
            nodes += StartNode()
            for (target in model.initial) {
                if (target in leafPaths) {
                    edges += GraphEdge(NodeId.Start, NodeId.State(target), EdgeKind.INITIAL, trigger = "")
                }
            }
        }

        // leaf ノード。
        for (leaf in root.leaves()) {
            nodes += StateGraphNode(
                id = NodeId.State(leaf.id),
                simpleName = leaf.simpleName,
                path = leaf.id,
                reachable = model.isReachable(leaf.id),
                exitBadge = leaf.exit?.let(::exitBadge),
                source = leaf.source,
            )
        }

        // scope-shared trigger を持つ scope の any-state 擬似ノード。
        // 共有 trigger の stay 成分は any ノードの self-loop ではなく **scope の囲い (root frame /
        // composite box) への self-loop** として描く (ScopeStay)。そのため any ノードは
        // 「ターゲット付きの共有遷移 or exit を持つ」場合にだけ生成する — stay しか無い scope の
        // any ノードは図から消え、その stay は囲いの弧が引き受ける。
        val scopeStays = mutableListOf<ScopeStay>()
        for (node in root.walk()) {
            if (node is LeafState) continue
            if (!anyNodeNeeded(node)) continue
            nodes += AnyStateNode(
                id = AnyStateNode.idFor(node.id),
                label = anyLabel(node),
                scope = node.id,
                exitBadge = node.exit?.let(::exitBadge),
                source = node.source,
            )
        }

        // 各ノードの trigger -> エッジ。leaf は自ノード発、root/group の共有は any-state 発。
        // 共有 trigger の stay は ScopeStay へ (leaf の stay は従来どおり自ノードの self-loop)。
        for (node in root.walk()) {
            when (node) {
                is LeafState -> {
                    for (trigger in triggersOf(node)) {
                        edges += edgesFor(NodeId.State(node.id), trigger, includeStay = true, ::targetNode)
                    }
                }
                else -> {
                    val fromId = AnyStateNode.idFor(node.id)
                    for (trigger in triggersOf(node)) {
                        edges += edgesFor(fromId, trigger, includeStay = false, ::targetNode)
                        if (trigger.stay) {
                            scopeStays += ScopeStay(
                                scope = node.id,
                                kind = kindOf(trigger),
                                trigger = triggerToken(trigger),
                                emits = trigger.emits,
                                source = trigger.source,
                            )
                        }
                    }
                }
            }
        }

        // 中間 sealed の composite box(入れ子)。
        for (node in root.walk()) {
            if (node is GroupState) composites += compositeOf(node)
        }

        return DiagramGraph(nodes = nodes, edges = edges, composites = composites, scopeStays = scopeStays)
    }

    /** True when the scope's any-state pseudo node must exist: it has targeted shared edges or an exit. */
    private fun anyNodeNeeded(node: DiagramStateNode): Boolean =
        node.exit != null || triggersOf(node).any { it.targets.isNotEmpty() }

    private fun triggersOf(node: DiagramStateNode): List<DiagramTrigger> = buildList {
        when (node) {
            is LeafState -> node.enter?.let { add(it) }
            // 中間 sealed の @OnEnter (scope 共有) も共有 trigger として扱う。
            is GroupState -> node.enter?.let { add(it) }
            else -> Unit
        }
        addAll(node.actions)
        addAll(node.recovers)
    }

    private fun kindOf(trigger: DiagramTrigger): EdgeKind = when (trigger) {
        is EnterTrigger -> EdgeKind.ENTER
        is ActionTrigger -> EdgeKind.ACTION
        is RecoverTrigger -> EdgeKind.RECOVER
    }

    private fun edgesFor(
        fromId: NodeId,
        trigger: DiagramTrigger,
        includeStay: Boolean,
        targetNode: (StateId) -> NodeId?,
    ): List<GraphEdge> {
        val token = triggerToken(trigger)
        val kind = kindOf(trigger)
        val result = mutableListOf<GraphEdge>()
        for (target in trigger.targets) {
            // toId は leaf の NodeId.State、または group の NodeId.Composite (= composite box id)。
            val toId = targetNode(target) ?: continue
            result += GraphEdge(fromId, toId, kind, token, trigger.emits, stay = false, source = trigger.source)
        }
        if (includeStay && trigger.stay) {
            result += GraphEdge(fromId, fromId, kind, token, trigger.emits, stay = true, source = trigger.source)
        }
        return result
    }

    private fun compositeOf(group: GroupState): CompositeBox {
        val memberIds = buildList {
            for (child in group.children) {
                when (child) {
                    is LeafState -> add(NodeId.State(child.id))
                    is GroupState -> add(NodeId.Composite(child.id))
                    is RootState -> Unit // 到達しない
                }
            }
            // any ノードは生成される場合 (ターゲット付き共有 or exit) だけ member に含める。
            // stay しか無い scope の any は存在しない (ScopeStay が箱の弧として引き受ける)。
            if (anyNodeNeeded(group)) add(AnyStateNode.idFor(group.id))
        }
        return CompositeBox(
            id = NodeId.Composite(group.id),
            simpleName = group.simpleName,
            path = group.id,
            memberIds = memberIds,
            source = group.source,
        )
    }

    private fun triggerToken(trigger: DiagramTrigger): String = when (trigger) {
        is EnterTrigger -> "onEnter"
        is ActionTrigger -> trigger.actionName.replaceFirstChar { it.lowercase() }
        is RecoverTrigger -> "on ${trigger.exceptionName}"
    }

    private fun anyLabel(node: DiagramStateNode): String =
        if (node.id == StateId.Root) "any state" else "any ${node.simpleName}"

    private fun exitBadge(exit: ExitInfo): String =
        if (exit.emits.isEmpty()) "exit" else "exit / ${exit.emits.joinToString(", ")}"
}
