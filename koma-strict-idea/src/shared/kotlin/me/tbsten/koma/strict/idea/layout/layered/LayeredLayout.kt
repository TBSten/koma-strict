package me.tbsten.koma.strict.idea.layout.layered

import me.tbsten.koma.strict.idea.ir.DiagramGraph
import me.tbsten.koma.strict.idea.ir.NodeId
import me.tbsten.koma.strict.idea.layout.GraphLayout
import me.tbsten.koma.strict.idea.layout.LayoutConfig
import me.tbsten.koma.strict.idea.layout.LayoutDirection
import me.tbsten.koma.strict.idea.layout.Rect
import me.tbsten.koma.strict.idea.layout.Size

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
}
