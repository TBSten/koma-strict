package me.tbsten.koma.strict.idea.layout

import me.tbsten.koma.strict.idea.ir.AnyStateNode
import me.tbsten.koma.strict.idea.ir.CompositeBox
import me.tbsten.koma.strict.idea.ir.DiagramGraph
import me.tbsten.koma.strict.idea.ir.NodeId
import me.tbsten.koma.strict.idea.ir.StartNode
import me.tbsten.koma.strict.idea.ir.StateGraphNode

/** Result of the layered layout: a rectangle for every node and composite box, plus the canvas size. */
data class GraphLayout(
    val direction: LayoutDirection,
    /** Layer index (0-based, along the direction axis) of each node. Exposed for testing / debugging. */
    val layers: Map<NodeId, Int>,
    val nodeRects: Map<NodeId, Rect>,
    val compositeRects: Map<NodeId, Rect>,
    val canvasSize: Size,
) {
    fun rect(id: NodeId): Rect? = nodeRects[id]
}

/**
 * A hand-written BFS layered layout (`ide.md`: "self-made BFS layers decide each node's position").
 *
 * Nodes are assigned to layers by shortest-path BFS from the in-degree-0 seeds (the `[*]` start and
 * any-state pseudo nodes usually seed layer 0); self-loops (`Stay` / self-transitions) are ignored
 * for layering. Layers grow along the [LayoutDirection] axis, siblings stack across it. Composite
 * boxes are then sized bottom-up as the padded bounding box of their members.
 */
object LayeredLayout {

    /**
     * Clearance reserved around a node that has self-loops: the loop arc lift (32) plus its outside
     * label row (~18) and a small gap, so arcs / labels near a canvas edge are never clamped onto
     * each other or clipped.
     */
    private const val LOOP_HALO = 56.0

    /**
     * Padding between the content bounding box and the drawn root frame (`DiagramDraw` uses the same
     * value in px). Exposed so [placeStartOutsideFrame] can seat the `[*]` start marker just *outside*
     * that frame — the renderer's frame excludes the start node, so a start placed beyond
     * `bbox ± ROOT_FRAME_PAD` is guaranteed to sit outside the visible frame.
     */
    const val ROOT_FRAME_PAD = 16.0

    fun layout(
        graph: DiagramGraph,
        direction: LayoutDirection = LayoutDirection.LR,
        config: LayoutConfig = LayoutConfig(),
    ): GraphLayout {
        // 典型的には node id は一意 (NodeId が種別を型に持つので pseudo/state が衝突しない、P1-04)。
        // 万一 model が重複 path の leaf を持っても層決定ループ (while layer.size < ids.size) が
        // 停止するよう distinct で潰しておく (NoSuchElementException を二度と踏まないための保険)。
        val ids: List<NodeId> = graph.nodes.map { it.id }.distinct()
        val layers = assignLayers(graph, ids)
        val placedGrid = placeNodes(graph, ids, layers, direction, config)
        // 均一グリッドのままだと隣接レイヤの相手と交差軸位置が揃わず、長い斜め線と交差が生まれる。
        // 並び順 (median) は変えずに、各ノードを隣接ノードの
        // 中央値位置へ引き寄せて線を立てる (Sugiyama の座標割当に相当)。
        val pulled = pullTowardNeighbors(graph, ids, layers, placedGrid, direction, config)
        // 引き寄せは行を揃える一方で、1 つのノードへ収束する fan の始点同士まで同じ帯に潰し、
        // 戻り線が浅い角度の平行束になって相互交差する。共通の相手を持つ
        // **同一レイヤ内の**ノード同士に限り交差軸の最小間隔を強制して角度の多様性を回復する
        // (単調押し下げのみ。レイヤ跨ぎまで離すと全エッジが長い対角線の階段になり逆効果)。
        val placed = spreadFanSiblings(graph, layers, pulled, direction, config)
        // 中間 sealed の入れ子: BFS 配置は grouping を無視するため、composite box が
        // 自分の member でないノードを内包してしまう (feed の Error が Stable 箱に入る等)。
        // 最終 placeComposites と同一の box rect を使って非 member を押し出し、確定後に非 member が
        // 再交差しないようにする (箱がちょうど自分の member だけを囲む)。
        val resolved = resolveCompositeOverlaps(graph.composites, placed, direction, config)
        // [*] start ノードを initial state の近く (startGap) へ寄せる (BFS の full セル + layerGap で線が伸びすぎるのを抑える)。
        // initial が composite 内にあっても start が box 境界を跨がないよう、箱の手前で止める。
        val repositioned = repositionStart(graph, resolved, direction, config)
        // 押し出し後に同一列で重なったノード (box 下へ落とした非 member が、既に box 下にいた別の
        // 非 member を飛び越して着地する等) を走査軸方向にほどく。これが無いと Loading から複数の
        // root leaf へ分岐したとき Test と Error が隙間ゼロで重なる。
        val separated0 = separateOverlaps(repositioned, direction, config)
        // 直線ルーティング (曲げない) の前提づくり: レイヤを 2 つ以上跨ぐエッジの通り道に、両端と
        // 同じ行 (列) の中間ノードが居座ると、直線が中間ノードの縁を這うしかなくなる (any-named の
        // leave が any の下辺に張り付く)。中間ノードを直交方向へ避けて「直線の車線」を空ける。
        val laned = nudgeSkipLanes(graph, layers, separated0, direction)
        // 避けた結果の新たな重なりは再度ほどく。
        val separated1 = separateOverlaps(laned, direction, config)
        // self-loop の弧 (lift 32) + 集約ラベル (複数行) は交差軸方向へ張り出す。一律セルピッチ/
        // siblingGap では隣接ノードのピル同士が覆い合うため、最終配置が定まったこの段で実所要量ぶん交差軸の隙間を広げる。押し下げで
        // composite 整合が崩れた分は resolve + separate をもう一巡して回収する (全パスが単調な
        // 押し広げのみなので発散しない)。
        val cleared = reserveLoopClearance(graph, separated1, direction, config)
        val resolved2 = resolveCompositeOverlaps(graph.composites, cleared, direction, config)
        val separated = separateOverlaps(resolved2, direction, config)
        val composites0 = placeComposites(graph.composites, separated, config)
        // composite box は member を compositePadding だけ外側へ膨らませるので、上端/左端が margin より
        // 手前 (負) に出て見切れうる (入れ子 group の上辺・ラベルが切れる)。node だけ見る repositionStart
        // では拾えないため、composite も含めた全 rect の最小端を margin に合わせて平行移動する。
        val (normNodes, normComposites) = normalizeAll(separated, composites0, config)
        // self-loop の弧 + ラベルはノードの外側 (上下/左右) に張り出す。キャンバス端のノードでは
        // ラベル位置が canvas 外になり、端クランプで他のラベルと同じ行へ押し込まれて重なるため、
        // loop を持つノードの周りに halo を確保して全体を平行移動 + キャンバスを拡張する。
        val loopExtents = loopClearanceExtents(graph)
        fun halo(id: NodeId, r: Rect): Rect = loopExtents[id]?.let { r.inflate(it) } ?: r
        val extents0 = normNodes.map { (id, r) -> halo(id, r) } + normComposites.values
        val dx = if (extents0.isEmpty()) 0.0 else (config.margin - extents0.minOf { it.x }).coerceAtLeast(0.0)
        val dy = if (extents0.isEmpty()) 0.0 else (config.margin - extents0.minOf { it.y }).coerceAtLeast(0.0)
        val shiftedNodes = if (dx > 0.0 || dy > 0.0) {
            normNodes.mapValues { (_, r) -> Rect(r.x + dx, r.y + dy, r.width, r.height) }
        } else {
            normNodes
        }
        val compositeRects0 = if (dx > 0.0 || dy > 0.0) {
            normComposites.mapValues { (_, r) -> Rect(r.x + dx, r.y + dy, r.width, r.height) }
        } else {
            normComposites
        }
        // [*] start は「root frame の外」に必ず出す (initial state が図の内側にある auth 等でも一貫)。
        // 最終 bbox が確定したこの段で、initial state に最も近い枠の辺の外側へ start を寄せ、負座標に
        // 出た分は全 rect を平行移動して吸収する。
        val (nodeRects, compositeRects) = placeStartOutsideFrame(graph, shiftedNodes, compositeRects0, direction, config)

        val allRects = nodeRects.map { (id, r) -> halo(id, r) } + compositeRects.values
        val canvasSize = if (allRects.isEmpty()) {
            Size(config.margin * 2, config.margin * 2)
        } else {
            // @OnExit バッジは LR ではノード右脇・TB ではノード下に描くので、その分だけ
            // 対応する軸にキャンバスを広げてクリップを防ぐ。
            val (exitRight, exitBottom) = exitBadgeExtent(graph, nodeRects, config, direction)
            // scope 共有 stay は scope の囲い (root frame / composite box) の右辺に弧 + ラベルとして
            // 掛かる。弧の張り出し (lift 44) + ラベル推定幅ぶんだけ右へ広げてクリップを防ぐ。
            val contentRight = allRects.maxOf { it.right }
            val scopeStayRight = graph.scopeStays.maxOfOrNull { stay ->
                val base = if (stay.scope.isRoot) {
                    contentRight + 16.0 // root frame は content bbox + 16dp
                } else {
                    compositeRects[NodeId.Composite(stay.scope)]?.right ?: contentRight
                }
                val labelW = minOf(stay.label.length * 5.6, 220.0)
                base + 44.0 + 8.0 + labelW + 12.0
            } ?: 0.0
            Size(
                width = maxOf(allRects.maxOf { it.right } + 16.0, exitRight, scopeStayRight) + config.margin,
                height = maxOf(allRects.maxOf { it.bottom } + 16.0, exitBottom) + config.margin,
            )
        }
        return GraphLayout(direction, layers, nodeRects, compositeRects, canvasSize)
    }

    // ---- layering ----

    private fun assignLayers(graph: DiagramGraph, ids: List<NodeId>): Map<NodeId, Int> {
        val idSet = ids.toSet()
        val byBoxId = graph.composites.associateBy { it.id }
        // group を指すエッジは composite box (= 非ノード) を指すので、そのまま層決定に使うと
        // 非ノード id が層マップに混ざる。box を「その member ノード群への入場」に展開し、BFS 上は
        // box の中身へ向かうエッジとして扱う (UML の composite state entry と同型)。自己ループは除外。
        val links: List<Pair<NodeId, NodeId>> = buildList {
            for (edge in graph.edges) {
                if (edge.fromId == edge.toId) continue
                if (edge.fromId !in idSet) continue
                val toNodes = when {
                    edge.toId in idSet -> listOf(edge.toId)
                    edge.toId in byBoxId -> transitiveNodeMembers(edge.toId, byBoxId).filter { it in idSet }
                    else -> emptyList()
                }
                for (to in toNodes) add(edge.fromId to to)
            }
        }
        val forward: Map<NodeId, List<NodeId>> =
            links.groupBy({ it.first }, { it.second }).mapValues { (_, v) -> v.distinct() }
        val incoming: Map<NodeId, Set<NodeId>> = buildMap<NodeId, MutableSet<NodeId>> {
            for (id in ids) put(id, mutableSetOf())
            for ((from, to) in links) getValue(to).add(from)
        }

        val layer = HashMap<NodeId, Int>()
        var base = 0
        while (layer.size < ids.size) {
            val remaining = ids.filter { it !in layer }
            val remainingSet = remaining.toSet()
            // remaining 部分グラフ内で入次数 0 のノードを seed に。無ければ cycle なので先頭を割る。
            var seeds = remaining.filter { id -> incoming[id].orEmpty().none { it in remainingSet } }
            if (seeds.isEmpty()) seeds = listOf(remaining.first())

            val queue = ArrayDeque<NodeId>()
            for (s in seeds) {
                if (s !in layer) {
                    layer[s] = base
                    queue.addLast(s)
                }
            }
            while (queue.isNotEmpty()) {
                val n = queue.removeFirst()
                val l = layer.getValue(n)
                for (m in forward[n].orEmpty()) {
                    if (m !in layer) {
                        layer[m] = l + 1
                        queue.addLast(m)
                    }
                }
            }
            base = (layer.values.maxOrNull() ?: base) + 1
        }
        relocateGroupAnyNodes(graph, layer)
        return layer
    }

    /**
     * A group-scoped `any <Group>` pseudo node has no incoming edge, so BFS parks it in layer 0.
     * Left there, its composite box would stretch back to the start and swallow everything in
     * between. Pull each non-root any-state node to the entrance layer of its own scope so the box
     * only wraps its group (`ide.md`: the group-shared scope is the any-state form nested inside its
     * composite). The root `any state` legitimately seeds layer 0 and is left untouched.
     */
    private fun relocateGroupAnyNodes(graph: DiagramGraph, layer: HashMap<NodeId, Int>) {
        for (any in graph.anyStateNodes) {
            if (any.scope.isRoot) continue
            val scopeSegments = any.scope.segments
            val minScopeLayer = graph.stateNodes
                .filter { it.path.segments.take(scopeSegments.size) == scopeSegments }
                .mapNotNull { layer[it.id] }
                .minOrNull() ?: continue
            layer[any.id] = minScopeLayer
        }
    }

    // ---- sibling ordering (median heuristic) ----

    /** How many up/down median sweeps to run over the layers. */
    private const val MEDIAN_SWEEPS = 4

    /**
     * Orders the siblings within every layer by the Sugiyama *median heuristic* to cut edge crossings.
     *
     * Starting from declaration order, the layers are swept top-down and bottom-up [MEDIAN_SWEEPS]
     * times. On each sweep the moving layer's nodes are re-sorted by the median index of their
     * neighbours in the already-fixed adjacent layer; a node with no neighbour in that layer keeps its
     * current slot (`元の相対位置を保持`). Only the sibling order changes — layer assignment, cell
     * geometry, and every downstream step are untouched.
     */
    private fun orderLayers(
        graph: DiagramGraph,
        ids: List<NodeId>,
        layers: Map<NodeId, Int>,
    ): Map<Int, List<NodeId>> {
        val idSet = ids.toSet()
        val adjacency = buildAdjacency(graph, idSet)
        val order: MutableMap<Int, MutableList<NodeId>> = ids
            .groupBy { layers.getValue(it) }
            .toSortedMap()
            .mapValues { (_, v) -> v.toMutableList() }
            .toMutableMap()
        val layerIndices = order.keys.sorted()
        if (layerIndices.size < 2) return order

        repeat(MEDIAN_SWEEPS) { sweep ->
            val downward = sweep % 2 == 0
            val sequence = if (downward) layerIndices else layerIndices.asReversed()
            for (li in sequence) {
                val adjLayer = if (downward) li - 1 else li + 1
                val fixed = order[adjLayer] ?: continue
                // 隣接層のノード -> その並び位置 index。median のキーになる。
                val pos = HashMap<NodeId, Int>(fixed.size)
                fixed.forEachIndexed { i, id -> pos[id] = i }
                order[li] = medianReorder(order.getValue(li), adjacency, pos).toMutableList()
            }
        }
        return order.toSortedMap()
    }

    /** Undirected node<->node adjacency, expanding composite-box targets to their member nodes. */
    private fun buildAdjacency(graph: DiagramGraph, idSet: Set<NodeId>): Map<NodeId, List<NodeId>> {
        val byBoxId = graph.composites.associateBy { it.id }
        val adj = HashMap<NodeId, MutableList<NodeId>>()
        fun link(a: NodeId, b: NodeId) {
            adj.getOrPut(a) { mutableListOf() }.add(b)
            adj.getOrPut(b) { mutableListOf() }.add(a)
        }
        for (edge in graph.edges) {
            if (edge.fromId == edge.toId) continue
            if (edge.fromId !in idSet) continue
            val toNodes = when {
                edge.toId in idSet -> listOf(edge.toId)
                edge.toId in byBoxId -> transitiveNodeMembers(edge.toId, byBoxId).filter { it in idSet }
                else -> emptyList()
            }
            for (to in toNodes) link(edge.fromId, to)
        }
        return adj
    }

    /**
     * Re-sorts one layer by the median of each node's neighbour positions in the fixed adjacent layer.
     * Nodes without a neighbour in that layer (median < 0) keep their original slot; the rest are
     * sorted by median and threaded back through the remaining slots, preserving the fixed nodes.
     */
    private fun medianReorder(
        nodes: List<NodeId>,
        adjacency: Map<NodeId, List<NodeId>>,
        pos: Map<NodeId, Int>,
    ): List<NodeId> {
        if (nodes.size < 2) return nodes
        val medians = nodes.map { id ->
            val neighbourPos = adjacency[id].orEmpty().mapNotNull { pos[it] }.sorted()
            id to medianOf(neighbourPos)
        }
        // median < 0 のノード (接続先が無い) は元のスロットに固定。
        val fixedSlots = HashMap<Int, NodeId>()
        medians.forEachIndexed { slot, (id, med) -> if (med < 0.0) fixedSlots[slot] = id }
        val movable = medians.filter { it.second >= 0.0 }.sortedBy { it.second }.map { it.first }
        val result = arrayOfNulls<NodeId>(nodes.size)
        for ((slot, id) in fixedSlots) result[slot] = id
        var m = 0
        for (i in result.indices) {
            if (result[i] == null) result[i] = movable[m++]
        }
        @Suppress("UNCHECKED_CAST")
        return (result as Array<NodeId>).toList()
    }

    /** Median of sorted neighbour positions: middle when odd, mean of the two middles when even, -1 if none. */
    private fun medianOf(sortedPos: List<Int>): Double {
        val m = sortedPos.size
        if (m == 0) return -1.0
        return if (m % 2 == 1) {
            sortedPos[m / 2].toDouble()
        } else {
            (sortedPos[m / 2 - 1] + sortedPos[m / 2]) / 2.0
        }
    }

    // ---- placement ----

    private fun placeNodes(
        graph: DiagramGraph,
        ids: List<NodeId>,
        layers: Map<NodeId, Int>,
        direction: LayoutDirection,
        config: LayoutConfig,
    ): Map<NodeId, Rect> {
        val startIds = graph.nodes.filterIsInstance<StartNode>().map { it.id }.toSet()
        // 層ごとに、ノードを縦 (LR) / 横 (TB) に積む。宣言順から始め、median heuristic の
        // 上下 sweep で兄弟順を交差が減る向きへ並べ替える (Sugiyama median sort)。
        val byLayer: Map<Int, List<NodeId>> = orderLayers(graph, ids, layers)

        val rects = LinkedHashMap<NodeId, Rect>()
        for ((layerIndex, nodesInLayer) in byLayer) {
            nodesInLayer.forEachIndexed { indexInLayer, id ->
                val isStart = id in startIds
                val cell = cellRect(layerIndex, indexInLayer, direction, config)
                rects[id] = if (isStart) centerSquare(cell, config.startSize) else cell
            }
        }
        return rects
    }

    /**
     * Pulls each node's cross-axis position toward the median of its neighbours' centers while
     * keeping the in-layer order (decided by the median heuristic) and a [LayoutConfig.siblingGap]
     * minimum spacing. Straightens edges the grid placement leaves diagonal: two nodes connected
     * across layers end up on (nearly) the same row, so long full-canvas diagonals and their
     * crossings shrink. Runs a few Gauss–Seidel sweeps (ascending then descending layer order);
     * downstream passes (eviction / skip lanes / loop clearance) only push apart, so the alignment
     * survives them. Order is never changed, so the median crossing regressions (auth = 0) hold.
     */
    private fun pullTowardNeighbors(
        graph: DiagramGraph,
        ids: List<NodeId>,
        layers: Map<NodeId, Int>,
        rects: Map<NodeId, Rect>,
        direction: LayoutDirection,
        config: LayoutConfig,
    ): Map<NodeId, Rect> {
        val result = LinkedHashMap(rects)
        val neighbors = HashMap<NodeId, MutableList<NodeId>>()
        for (edge in graph.edges) {
            if (edge.fromId == edge.toId) continue
            if (edge.fromId !in result || edge.toId !in result) continue
            neighbors.getOrPut(edge.fromId) { mutableListOf() }.add(edge.toId)
            neighbors.getOrPut(edge.toId) { mutableListOf() }.add(edge.fromId)
        }
        if (neighbors.isEmpty()) return result
        fun cross(r: Rect): Double = if (direction == LayoutDirection.LR) r.y + r.height / 2 else r.x + r.width / 2
        fun withCrossTop(r: Rect, top: Double): Rect =
            if (direction == LayoutDirection.LR) r.copy(y = top) else r.copy(x = top)
        fun crossSize(r: Rect): Double = if (direction == LayoutDirection.LR) r.height else r.width

        val byLayer = ids.groupBy { layers[it] ?: 0 }.toSortedMap()
        val layerOrder = byLayer.values.map { nodesInLayer ->
            nodesInLayer.sortedBy { cross(result.getValue(it)) }
        }
        // 前進 sweep は「前のレイヤの隣接」、後退 sweep は「次のレイヤの隣接」に整列する古典
        // Sugiyama の交互整列。全隣接の中央値だと hub (fan の中心) が fan メンバーの帯へ引き込まれ、
        // 幹線 (spine) が折れて浅い平行束を作る。方向を絞ると
        // 「親に揃える」= 直線の背骨ができ、fan は spread パスが開く。
        data class Sweep(val order: List<List<NodeId>>, val towardPrev: Boolean)
        val sweeps = listOf(
            Sweep(layerOrder, towardPrev = true),
            Sweep(layerOrder.asReversed(), towardPrev = false),
            Sweep(layerOrder, towardPrev = true),
        )
        for ((pass, towardPrev) in sweeps) {
            for (layerNodes in pass) {
                if (layerNodes.isEmpty()) continue
                val layerOf = { id: NodeId -> layers[id] ?: 0 }
                // 各ノードの目標 = sweep 方向側レイヤの隣接中心の中央値 (無ければ反対側レイヤの隣接)。
                // **同一レイヤの隣接は絶対にターゲットにしない**: 同層ノードへ「整列」すると相手の
                // 位置へ潜り込もうとして前進クランプで押し下げられ、sweep のたびに下がるラチェット
                // 発散を起こす。層跨ぎの
                // 隣接が無いノードは現位置を維持する。
                val targets = layerNodes.map { id ->
                    val nsCross = neighbors[id].orEmpty().filter { layerOf(it) != layerOf(id) }
                    if (nsCross.isEmpty()) {
                        cross(result.getValue(id))
                    } else {
                        val side = nsCross.filter {
                            if (towardPrev) layerOf(it) < layerOf(id) else layerOf(it) > layerOf(id)
                        }.ifEmpty { nsCross }
                        val centers = side.map { cross(result.getValue(it)) }.sorted()
                        // 偶数個は中央 2 点の平均 (上寄り単独選択だと片側へ寄り続ける)。
                        if (centers.size % 2 == 1) {
                            centers[centers.size / 2]
                        } else {
                            (centers[centers.size / 2 - 1] + centers[centers.size / 2]) / 2
                        }
                    }
                }
                // 並び順を保ったまま前進クランプで再配置 (target 順が並びと矛盾する分は gap で解消)。
                var cursor = Double.NEGATIVE_INFINITY
                for (i in layerNodes.indices) {
                    val id = layerNodes[i]
                    val r = result.getValue(id)
                    val size = crossSize(r)
                    val top = maxOf(targets[i] - size / 2, cursor)
                    result[id] = withCrossTop(r, top)
                    cursor = top + size + config.siblingGap
                }
            }
        }
        return result
    }

    /**
     * Ensures the sources/targets fanning into a common node keep a minimum cross-axis distance from
     * each other, so their straight edges approach the shared node at visibly different angles
     * instead of forming a near-parallel shallow bundle. Only groups of 3+ fan siblings are spread,
     * only by pushing later (in cross order) members further along the cross axis — monotone, so it
     * cannot oscillate; the follow-up separation/eviction passes absorb any new overlaps.
     */
    private fun spreadFanSiblings(
        graph: DiagramGraph,
        layers: Map<NodeId, Int>,
        rects: Map<NodeId, Rect>,
        direction: LayoutDirection,
        config: LayoutConfig,
    ): Map<NodeId, Rect> {
        val neighbors = HashMap<NodeId, MutableSet<NodeId>>()
        for (edge in graph.edges) {
            if (edge.fromId == edge.toId) continue
            if (edge.fromId !in rects || edge.toId !in rects) continue
            neighbors.getOrPut(edge.fromId) { mutableSetOf() }.add(edge.toId)
            neighbors.getOrPut(edge.toId) { mutableSetOf() }.add(edge.fromId)
        }
        val result = LinkedHashMap(rects)
        fun cross(r: Rect): Double = if (direction == LayoutDirection.LR) r.y else r.x
        val minGap = (if (direction == LayoutDirection.LR) config.nodeHeight else config.nodeWidth) / 2 + 32.0
        // hub (相手が 3 以上) ごとに、同一レイヤの fan メンバー同士だけを交差軸順に走査して間隔を
        // enforce する (別レイヤの相手は既に流れ軸で離れており、交差軸まで離すと対角線が伸びるだけ)。
        for ((_, fan) in neighbors) {
            if (fan.size < 3) continue
            for ((_, sameLayer) in fan.groupBy { layers[it] ?: 0 }) {
                if (sameLayer.size < 2) continue
                val ordered = sameLayer.sortedBy { cross(result.getValue(it)) }
                for (i in 1 until ordered.size) {
                    val prev = result.getValue(ordered[i - 1])
                    val cur = result.getValue(ordered[i])
                    val minTop = (if (direction == LayoutDirection.LR) prev.y + prev.height else prev.x + prev.width) + minGap
                    if (direction == LayoutDirection.LR) {
                        if (cur.y < minTop) result[ordered[i]] = cur.copy(y = minTop)
                    } else {
                        if (cur.x < minTop) result[ordered[i]] = cur.copy(x = minTop)
                    }
                }
            }
        }
        return result
    }

    /**
     * Estimated cross-axis clearance each loop-bearing node needs for its self-loop arcs and merged
     * multi-line labels, keyed by node. The estimate mirrors the draw side: loops group by
     * (node, kind), each group renders one arc (lift 32) plus one label pill whose line count is the
     * sum of the member labels wrapped at 220dp (~5.6dp per char at 10sp, 14dp per line). The
     * largest group governs. Nodes without self-loops are absent from the map.
     */
    internal fun loopClearanceExtents(graph: DiagramGraph): Map<NodeId, Double> {
        val groups = LinkedHashMap<Pair<NodeId, Any>, MutableList<String>>()
        for (edge in graph.edges) {
            if (edge.fromId != edge.toId) continue
            groups.getOrPut(edge.fromId to edge.kind) { mutableListOf() }
                .apply { if (edge.label.isNotBlank()) add(edge.label) }
        }
        val extents = HashMap<NodeId, Double>()
        for ((key, labels) in groups) {
            val lines = labels.sumOf { maxOf(1, kotlin.math.ceil(it.length * 5.6 / 220.0).toInt()) }
            // 32 = 弧の lift, 4 = ラベルgap, 14 = 1 行の高さ, 6 = ピルの余白。ラベル無し group は halo のみ。
            val extent = if (lines == 0) LOOP_HALO else maxOf(LOOP_HALO, 32.0 + 4.0 + lines * 14.0 + 6.0)
            extents.merge(key.first, extent, ::maxOf)
        }
        return extents
    }

    /**
     * Widens the cross-axis gap between nodes so each pair of vertically (LR) / horizontally (TB)
     * adjacent, overlapping nodes keeps room for both sides' loop arcs + labels
     * ([loopClearanceExtents]). Only pushes nodes further along the cross axis (monotone — never
     * reorders, never pulls together), so it cannot oscillate; downstream passes (eviction, skip
     * lanes, separation) all preserve or increase gaps.
     */
    private fun reserveLoopClearance(
        graph: DiagramGraph,
        rects: Map<NodeId, Rect>,
        direction: LayoutDirection,
        config: LayoutConfig,
    ): Map<NodeId, Rect> {
        val extents = loopClearanceExtents(graph)
        if (extents.isEmpty()) return rects
        val result = LinkedHashMap(rects)
        val ordered = result.keys.sortedWith(
            compareBy(
                { if (direction == LayoutDirection.LR) result.getValue(it).y else result.getValue(it).x },
                { if (direction == LayoutDirection.LR) result.getValue(it).x else result.getValue(it).y },
            ),
        )
        for (j in ordered.indices) {
            val idJ = ordered[j]
            var rj = result.getValue(idJ)
            for (i in 0 until j) {
                val ri = result.getValue(ordered[i])
                val required = maxOf(
                    config.siblingGap,
                    (extents[ordered[i]] ?: 0.0) + (extents[idJ] ?: 0.0),
                )
                // ソート順 (元の交差軸順) を正とし、i が先行するペアは常に隙間を enforce する。
                // 「現在すでに上に居るか」を条件にすると、先行ペアの押し下げで生じた一時的な重なりが
                // ペアをスキップさせ、後段の separateOverlaps が siblingGap だけで再スタックしてしまう。
                when (direction) {
                    LayoutDirection.LR -> {
                        if (ri.x < rj.right && ri.right > rj.x) {
                            val minTop = ri.bottom + required
                            if (rj.y < minTop) rj = rj.copy(y = minTop)
                        }
                    }
                    LayoutDirection.TB -> {
                        if (ri.y < rj.bottom && ri.bottom > rj.y) {
                            val minLeft = ri.right + required
                            if (rj.x < minLeft) rj = rj.copy(x = minLeft)
                        }
                    }
                }
            }
            result[idJ] = rj
        }
        return result
    }

    private fun cellRect(
        layerIndex: Int,
        indexInLayer: Int,
        direction: LayoutDirection,
        config: LayoutConfig,
    ): Rect = when (direction) {
        LayoutDirection.LR -> Rect(
            x = config.margin + layerIndex * (config.nodeWidth + config.layerGap),
            y = config.margin + indexInLayer * (config.nodeHeight + config.siblingGap),
            width = config.nodeWidth,
            height = config.nodeHeight,
        )
        LayoutDirection.TB -> Rect(
            x = config.margin + indexInLayer * (config.nodeWidth + config.siblingGap),
            y = config.margin + layerIndex * (config.nodeHeight + config.layerGap),
            width = config.nodeWidth,
            height = config.nodeHeight,
        )
    }

    private fun centerSquare(cell: Rect, side: Double): Rect =
        Rect(
            x = cell.x + (cell.width - side) / 2,
            y = cell.y + (cell.height - side) / 2,
            width = side,
            height = side,
        )

    /**
     * Pulls the `[*]` start dot toward the initial state(s) it points to so the start edge is only
     * [LayoutConfig.startGap] long — the BFS otherwise puts the dot a full node cell + [layerGap] away,
     * which reads as an over-long line. The dot is moved next to the nearest initial, then the origin
     * is re-normalized so the shift doesn't leave dead space on the leading edge.
     *
     * When an initial state sits inside a composite box, moving the dot right up against it would put
     * the dot across the box border; the move is clamped to stop just outside the box so the start
     * marker never crosses a composite border (the entry edge still lands on the inner initial).
     */
    private fun repositionStart(
        graph: DiagramGraph,
        nodeRects: Map<NodeId, Rect>,
        direction: LayoutDirection,
        config: LayoutConfig,
    ): Map<NodeId, Rect> {
        val startId = graph.nodes.filterIsInstance<StartNode>().firstOrNull()?.id ?: return nodeRects
        val startRect = nodeRects[startId] ?: return nodeRects
        val targets = graph.edges.filter { it.fromId == startId }.mapNotNull { nodeRects[it.toId] }
        if (targets.isEmpty()) return nodeRects
        // 最終描画と同一形状の composite rect。initial state が box の中にある (session の
        // SignedIn.Home 等) と start を initial 直前へ寄せると box 境界を跨ぐため、跨ぐ box があれば
        // その leading 側の手前まで戻して border を跨がせない (P2-12)。
        val boxRects = placeComposites(graph.composites, nodeRects, config).values
        val moved = when (direction) {
            LayoutDirection.LR -> {
                var x = targets.minOf { it.x } - config.startGap - startRect.width
                val blocking = boxRects.filter { startRect.copy(x = x).intersects(it) }
                if (blocking.isNotEmpty()) x = minOf(x, blocking.minOf { it.x } - config.startGap - startRect.width)
                startRect.copy(x = x)
            }
            LayoutDirection.TB -> {
                var y = targets.minOf { it.y } - config.startGap - startRect.height
                val blocking = boxRects.filter { startRect.copy(y = y).intersects(it) }
                if (blocking.isNotEmpty()) y = minOf(y, blocking.minOf { it.y } - config.startGap - startRect.height)
                startRect.copy(y = y)
            }
        }
        return normalizeOrigin(nodeRects + (startId to moved), direction, config)
    }

    /**
     * Seats the `[*]` start marker just outside the root frame (`content bbox ± [ROOT_FRAME_PAD]`),
     * on the **leading edge** of the layout direction (left in LR / top in TB), aligned to the initial
     * state's centre on the other axis. The renderer draws the frame from the same content bbox (start
     * excluded), so the dot always sits outside the visible frame — including stores like `auth` whose
     * initial state is interior (the dot still exits to the frame's left/top for a consistent "enters
     * from outside" read). Any negative coordinate the move introduces is absorbed by shifting *all*
     * rects back to the margin.
     */
    private fun placeStartOutsideFrame(
        graph: DiagramGraph,
        nodeRects: Map<NodeId, Rect>,
        compositeRects: Map<NodeId, Rect>,
        direction: LayoutDirection,
        config: LayoutConfig,
    ): Pair<Map<NodeId, Rect>, Map<NodeId, Rect>> {
        val startId = graph.nodes.filterIsInstance<StartNode>().firstOrNull()?.id ?: return nodeRects to compositeRects
        val startRect = nodeRects[startId] ?: return nodeRects to compositeRects
        val initial = graph.edges.filter { it.fromId == startId }.mapNotNull { nodeRects[it.toId] }.firstOrNull()
            ?: return nodeRects to compositeRects
        // content bbox = start 以外の全ノード + composite (= 描画側 root frame の元)。
        val content = nodeRects.filterKeys { it != startId }.values + compositeRects.values
        if (content.isEmpty()) return nodeRects to compositeRects
        val cx = initial.x + initial.width / 2
        val cy = initial.y + initial.height / 2
        val moved = when (direction) {
            // LR は枠の左外・initial の y に揃える / TB は枠の上外・initial の x に揃える。
            LayoutDirection.LR -> {
                val fl = content.minOf { it.x } - ROOT_FRAME_PAD
                startRect.copy(x = fl - config.startGap - startRect.width, y = cy - startRect.height / 2)
            }
            LayoutDirection.TB -> {
                val ft = content.minOf { it.y } - ROOT_FRAME_PAD
                startRect.copy(x = cx - startRect.width / 2, y = ft - config.startGap - startRect.height)
            }
        }
        val movedNodes = nodeRects + (startId to moved)
        // start が margin より手前 (負側) に出たら全 rect を平行移動して吸収。
        val minX = (movedNodes.values + compositeRects.values).minOf { it.x }
        val minY = (movedNodes.values + compositeRects.values).minOf { it.y }
        val sx = (config.margin - minX).coerceAtLeast(0.0)
        val sy = (config.margin - minY).coerceAtLeast(0.0)
        if (sx == 0.0 && sy == 0.0) return movedNodes to compositeRects
        fun shift(r: Rect) = Rect(r.x + sx, r.y + sy, r.width, r.height)
        return movedNodes.mapValues { shift(it.value) } to compositeRects.mapValues { shift(it.value) }
    }

    /** Shifts every rect so the leading edge (left in LR / top in TB) sits back at [LayoutConfig.margin]. */
    private fun normalizeOrigin(
        rects: Map<NodeId, Rect>,
        direction: LayoutDirection,
        config: LayoutConfig,
    ): Map<NodeId, Rect> {
        if (rects.isEmpty()) return rects
        val shift = when (direction) {
            LayoutDirection.LR -> rects.values.minOf { it.x } - config.margin
            LayoutDirection.TB -> rects.values.minOf { it.y } - config.margin
        }
        if (shift == 0.0) return rects
        return when (direction) {
            LayoutDirection.LR -> rects.mapValues { (_, r) -> r.copy(x = r.x - shift) }
            LayoutDirection.TB -> rects.mapValues { (_, r) -> r.copy(y = r.y - shift) }
        }
    }

    /**
     * Un-stacks nodes that share a column (same leading-axis coordinate) and ended up overlapping —
     * chiefly the composite push-out landing a node on top of one that already sat below the box.
     * Within each lane the nodes are ordered along the cross axis and any that overlaps its predecessor
     * is nudged down (LR) / right (TB) by [LayoutConfig.siblingGap]. A pure top-down sweep; nodes that
     * already clear each other are left untouched, so normal layouts are unaffected.
     */
    // 車線空けで中間ノードを避ける量の余白 (ノード半分 + これだけ直交方向へ動かす)。
    // 避けたノードの縁と直線車線の間に「エッジラベル 1 行分の回廊」(~26dp) を確保する余白込み。
    // 20dp だと避けたノードの下縁が車線に接し、車線上のラベルがノードに密着して読めない。
    private const val SKIP_LANE_CLEARANCE = 48.0

    /**
     * Clears a straight "lane" for edges that span 2+ layers: an in-between node sitting on the same
     * row (LR) / column (TB) as both endpoints would force the straight line to ride along its border,
     * so it is nudged perpendicular to the sweep axis. Each node moves at most once.
     */
    private fun nudgeSkipLanes(
        graph: DiagramGraph,
        layers: Map<NodeId, Int>,
        rects: Map<NodeId, Rect>,
        direction: LayoutDirection,
    ): Map<NodeId, Rect> {
        val result = rects.toMutableMap()
        val nudged = HashSet<NodeId>()
        for (edge in graph.edges) {
            if (edge.fromId == edge.toId) continue
            val la = layers[edge.fromId] ?: continue
            val lb = layers[edge.toId] ?: continue
            if (kotlin.math.abs(la - lb) < 2) continue
            val a = result[edge.fromId] ?: continue
            val b = result[edge.toId] ?: continue
            for ((id, r) in result.entries.toList()) {
                if (id == edge.fromId || id == edge.toId || id in nudged) continue
                val li = layers[id] ?: continue
                if (li <= minOf(la, lb) || li >= maxOf(la, lb)) continue
                when (direction) {
                    LayoutDirection.LR -> {
                        val sameRow = kotlin.math.abs(r.center.y - a.center.y) < r.height / 2 &&
                            kotlin.math.abs(r.center.y - b.center.y) < r.height / 2
                        if (sameRow) {
                            result[id] = Rect(r.x, r.y - (r.height / 2 + SKIP_LANE_CLEARANCE), r.width, r.height)
                            nudged += id
                        }
                    }
                    LayoutDirection.TB -> {
                        val sameCol = kotlin.math.abs(r.center.x - a.center.x) < r.width / 2 &&
                            kotlin.math.abs(r.center.x - b.center.x) < r.width / 2
                        if (sameCol) {
                            result[id] = Rect(r.x - (r.width / 2 + SKIP_LANE_CLEARANCE), r.y, r.width, r.height)
                            nudged += id
                        }
                    }
                }
            }
        }
        return result
    }

    private fun separateOverlaps(
        rects: Map<NodeId, Rect>,
        direction: LayoutDirection,
        config: LayoutConfig,
    ): Map<NodeId, Rect> {
        if (rects.size < 2) return rects
        val result = LinkedHashMap(rects)
        val laneKey: (Rect) -> Long = { r ->
            when (direction) {
                LayoutDirection.LR -> r.x.toRawBits()
                LayoutDirection.TB -> r.y.toRawBits()
            }
        }
        val lanes = result.keys.groupBy { laneKey(result.getValue(it)) }
        for ((_, laneIds) in lanes) {
            if (laneIds.size < 2) continue
            val ordered = laneIds.sortedBy {
                val r = result.getValue(it)
                if (direction == LayoutDirection.LR) r.y else r.x
            }
            for (i in 1 until ordered.size) {
                val prev = result.getValue(ordered[i - 1])
                val cur = result.getValue(ordered[i])
                result[ordered[i]] = when (direction) {
                    LayoutDirection.LR -> {
                        val minTop = prev.bottom + config.siblingGap
                        if (cur.y < minTop) cur.copy(y = minTop) else cur
                    }
                    LayoutDirection.TB -> {
                        val minLeft = prev.right + config.siblingGap
                        if (cur.x < minLeft) cur.copy(x = minLeft) else cur
                    }
                }
            }
        }
        return result
    }

    /**
     * Shifts node + composite rects together so the smallest leading/top edge across *all* of them
     * lands back at [LayoutConfig.margin]. Needed because a composite box inflates [compositePadding]
     * beyond its members, so its top/left can go negative and clip; [repositionStart] only normalizes
     * node rects and runs before boxes exist, so it can't see that.
     */
    private fun normalizeAll(
        nodeRects: Map<NodeId, Rect>,
        compositeRects: Map<NodeId, Rect>,
        config: LayoutConfig,
    ): Pair<Map<NodeId, Rect>, Map<NodeId, Rect>> {
        val all = nodeRects.values + compositeRects.values
        if (all.isEmpty()) return nodeRects to compositeRects
        val dx = config.margin - all.minOf { it.x }
        val dy = config.margin - all.minOf { it.y }
        if (dx == 0.0 && dy == 0.0) return nodeRects to compositeRects
        fun shift(m: Map<NodeId, Rect>): Map<NodeId, Rect> =
            m.mapValues { (_, r) -> r.copy(x = r.x + dx, y = r.y + dy) }
        return shift(nodeRects) to shift(compositeRects)
    }

    private fun placeComposites(
        composites: List<CompositeBox>,
        nodeRects: Map<NodeId, Rect>,
        config: LayoutConfig,
    ): Map<NodeId, Rect> {
        val result = LinkedHashMap<NodeId, Rect>()
        // 深い box から先に確定させ、親 box が子 box の rect を取り込めるようにする。
        val deepestFirst = composites.sortedByDescending { it.path.segments.size }
        for (box in deepestFirst) {
            result[box.id] = compositeRectOf(box, nodeRects, result, config) ?: continue
        }
        return result
    }

    /**
     * The exact rect of one composite box: the union of its direct members, padded by
     * [LayoutConfig.compositePadding]. A *nested-box* member contributes its own already-padded rect
     * from [boxRects] (deepest-first), so each nesting level adds one padding, bottom-up. Shared by
     * [resolveCompositeOverlaps] and [placeComposites] so both agree on identical bounds — otherwise
     * the resolver pushes non-members out of a box smaller than the one finally drawn and they
     * re-intersect the final outer box.
     */
    private fun compositeRectOf(
        box: CompositeBox,
        nodeRects: Map<NodeId, Rect>,
        boxRects: Map<NodeId, Rect>,
        config: LayoutConfig,
    ): Rect? {
        var acc: Rect? = null
        for (memberId in box.memberIds) {
            val memberRect = nodeRects[memberId] ?: boxRects[memberId] ?: continue
            acc = acc?.union(memberRect) ?: memberRect
        }
        return acc?.inflate(config.compositePadding)
    }

    // ---- composite band cleanup ----

    /**
     * Makes every composite box wrap exactly its own members, in two convergent moves:
     *
     * 1. **Free nodes** (members of no composite) that sit inside a box are pushed out along the
     *    cross axis. Box rects depend only on members, so pushing free nodes never resizes any box —
     *    no feedback.
     * 2. **Box-vs-box overlaps** (siblings — never an ancestor/descendant pair) are resolved by
     *    **rigidly translating** the downstream box's whole member tree past the upstream box.
     *    A rigid move keeps the box's size, so boxes can cascade downstream at most once each and the
     *    sweep terminates.
     *
     * The previous implementation pushed *other boxes' members* out individually, which stretched the
     * neighbouring box downward, which pushed back, ping-ponging forever — the pass cap then became
     * the canvas height. Members are therefore never
     * pushed one-by-one any more. If the safety cap is still hit, a `LAYOUT_WARN` is printed instead
     * of failing silently.
     */
    private fun resolveCompositeOverlaps(
        composites: List<CompositeBox>,
        placed: Map<NodeId, Rect>,
        direction: LayoutDirection,
        config: LayoutConfig,
    ): Map<NodeId, Rect> {
        if (composites.isEmpty()) return placed
        val rects = LinkedHashMap(placed)
        val byId = composites.associateBy { it.id }
        val deepestFirst = composites.sortedByDescending { it.path.segments.size }
        // node -> 直属の箱。兄弟箱のメンバーを個別に押すと所属先の箱が伸びて押し合いが発散するため、
        // 個別押し出しは「自由ノード」か「押し出し元の箱が自分の箱の内側 (子孫) にある場合」だけに限る
        // (親メンバーが入れ子の子箱に飲まれた時は押してよい — 子箱の rect は変わらないので発散しない)。
        val directOwner = HashMap<NodeId, NodeId>()
        for (box in composites) {
            for (m in box.memberIds) {
                if (m !in byId) directOwner[m] = box.id
            }
        }

        fun currentBoxRects(): Map<NodeId, Rect> {
            val boxRects = HashMap<NodeId, Rect>()
            for (box in deepestFirst) {
                compositeRectOf(box, rects, boxRects, config)?.let { boxRects[box.id] = it }
            }
            return boxRects
        }

        // (1) ノード単位の押し出し。自由ノードは常に、箱持ちノードは「押し出し元が自分の箱の子孫箱」の
        // 時だけ (このとき押し出し元の rect は不変なのでフィードバックしない)。1 パス分。
        fun evictNodes(): Boolean {
            var moved = false
            val boxRects = HashMap<NodeId, Rect>()
            for (box in deepestFirst) {
                val boxRect = compositeRectOf(box, rects, boxRects, config) ?: continue
                boxRects[box.id] = boxRect
                val members = transitiveNodeMembers(box.id, byId)
                // 押し出し後の重なりを避けるため、同一「列」ごとに箱の外側から順に積む。
                val cursorByLane = HashMap<Long, Double>()
                for (id in rects.keys.toList()) {
                    if (id in members) continue
                    val owner = directOwner[id]
                    if (owner != null && !isAncestorBox(owner, box.id, byId)) continue
                    val nr = rects[id] ?: continue
                    if (!nr.intersects(boxRect)) continue
                    val pushed = pushOutOfBox(nr, boxRect, direction, config, cursorByLane)
                    if (pushed != nr) {
                        rects[id] = pushed
                        moved = true
                    }
                }
            }
            return moved
        }

        // (2) 先祖子孫関係にない箱同士の重なりを、下流側の箱のメンバー全体の剛体移動で解く。1 パス分。
        fun separateBoxes(): Boolean {
            var moved = false
            var boxRects = currentBoxRects()
            val ordered = composites.sortedBy { box ->
                boxRects[box.id]?.let { if (direction == LayoutDirection.LR) it.y else it.x } ?: Double.MAX_VALUE
            }
            for (i in ordered.indices) {
                for (j in i + 1 until ordered.size) {
                    val a = ordered[i]
                    val b = ordered[j]
                    if (isAncestorBox(a.id, b.id, byId) || isAncestorBox(b.id, a.id, byId)) continue
                    val ra = boxRects[a.id] ?: continue
                    val rb = boxRects[b.id] ?: continue
                    if (!ra.intersects(rb)) continue
                    val delta = when (direction) {
                        LayoutDirection.LR -> ra.bottom + config.siblingGap - rb.y
                        LayoutDirection.TB -> ra.right + config.siblingGap - rb.x
                    }
                    if (delta <= 0.0) continue
                    for (m in transitiveNodeMembers(b.id, byId)) {
                        val r = rects[m] ?: continue
                        rects[m] = when (direction) {
                            LayoutDirection.LR -> r.copy(y = r.y + delta)
                            LayoutDirection.TB -> r.copy(x = r.x + delta)
                        }
                    }
                    boxRects = currentBoxRects()
                    moved = true
                }
            }
            return moved
        }

        val maxPasses = composites.size + 2
        var pass = 0
        while (pass < maxPasses) {
            pass++
            val movedNodes = evictNodes()
            val movedBoxes = separateBoxes()
            if (!movedNodes && !movedBoxes) break
        }
        if (pass >= maxPasses) {
            // 収束保証が破れた時は黙って歪んだ図を出さず、原因調査の手がかりを残す。
            System.err.println("LAYOUT_WARN resolveCompositeOverlaps hit the pass cap ($maxPasses); layout may contain unresolved composite overlaps")
        }
        return rects
    }

    /** True when composite [ancestor] transitively contains composite [descendant] as a member. */
    private fun isAncestorBox(ancestor: NodeId, descendant: NodeId, byId: Map<NodeId, CompositeBox>): Boolean {
        val box = byId[ancestor] ?: return false
        return box.memberIds.any { it == descendant || isAncestorBox(it, descendant, byId) }
    }

    /** Node ids inside [boxId], expanding nested box members (box ids themselves are not node rects). */
    private fun transitiveNodeMembers(boxId: NodeId, byId: Map<NodeId, CompositeBox>): Set<NodeId> {
        val out = LinkedHashSet<NodeId>()
        fun visit(id: NodeId) {
            val box = byId[id]
            if (box == null) { out += id; return }
            box.memberIds.forEach(::visit)
        }
        byId.getValue(boxId).memberIds.forEach(::visit)
        return out
    }

    /** Moves [nr] just past [box] along the cross axis (down in LR, right in TB), stacking per lane. */
    private fun pushOutOfBox(
        nr: Rect,
        box: Rect,
        direction: LayoutDirection,
        config: LayoutConfig,
        cursorByLane: HashMap<Long, Double>,
    ): Rect = when (direction) {
        LayoutDirection.LR -> {
            val lane = nr.x.toRawBits()
            val top = cursorByLane.getOrDefault(lane, box.bottom + config.siblingGap)
            cursorByLane[lane] = top + nr.height + config.siblingGap
            nr.copy(y = top)
        }
        LayoutDirection.TB -> {
            val lane = nr.y.toRawBits()
            val left = cursorByLane.getOrDefault(lane, box.right + config.siblingGap)
            cursorByLane[lane] = left + nr.width + config.siblingGap
            nr.copy(x = left)
        }
    }

    /**
     * The extra (right, bottom) canvas extent any `@OnExit` badge reaches. In `LR` a badge extends to
     * the node's right; in `TB` it extends below the node (and may overhang horizontally when wider
     * than the node). Sizes are estimated from the badge text length so the canvas reserves enough
     * room to not clip it.
     */
    private fun exitBadgeExtent(
        graph: DiagramGraph,
        rects: Map<NodeId, Rect>,
        config: LayoutConfig,
        direction: LayoutDirection,
    ): Pair<Double, Double> {
        var maxRight = 0.0
        var maxBottom = 0.0
        for (node in graph.nodes) {
            val badge = when (node) {
                is StateGraphNode -> node.exitBadge
                is AnyStateNode -> node.exitBadge
                else -> null
            } ?: continue
            val r = rects[node.id] ?: continue
            // 9sp のバッジ幅を文字数から強めに見積もる (実測幅がこれを超えてクリップしないよう余裕を取る)。
            val estWidth = badge.length * 7.0 + 20.0
            when (direction) {
                LayoutDirection.LR -> maxRight = maxOf(maxRight, r.right + config.exitBadgeGap + estWidth)
                LayoutDirection.TB -> {
                    maxBottom = maxOf(maxBottom, r.bottom + config.exitBadgeGap + BADGE_HEIGHT)
                    // 中央寄せバッジがノードより広いと横にはみ出すので右端も確保する。
                    maxRight = maxOf(maxRight, r.center.x + estWidth / 2)
                }
            }
        }
        return maxRight to maxBottom
    }

    /** Estimated pixel height of an `@OnExit` badge pill (9sp text + vertical padding). */
    private const val BADGE_HEIGHT = 22.0
}
