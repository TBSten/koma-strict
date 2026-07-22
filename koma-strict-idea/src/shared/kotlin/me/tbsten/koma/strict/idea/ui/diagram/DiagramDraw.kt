package me.tbsten.koma.strict.idea.ui.diagram

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.tbsten.koma.strict.idea.ir.AnyStateNode
import me.tbsten.koma.strict.idea.ir.DiagramGraph
import me.tbsten.koma.strict.idea.ir.EdgeKind
import me.tbsten.koma.strict.idea.ir.GraphEdge
import me.tbsten.koma.strict.idea.ir.NodeId
import me.tbsten.koma.strict.idea.ir.ScopeStay
import me.tbsten.koma.strict.idea.ir.StartNode
import me.tbsten.koma.strict.idea.ir.StateGraphNode
import me.tbsten.koma.strict.idea.model.StateId
import me.tbsten.koma.strict.idea.layout.GraphLayout
import me.tbsten.koma.strict.idea.layout.LayoutDirection
import me.tbsten.koma.strict.idea.layout.Rect
import me.tbsten.koma.strict.idea.layout.edge.EdgeRouting
import me.tbsten.koma.strict.idea.layout.edge.endpointRect
import kotlin.math.abs

/**
 * Pure [DrawScope] rendering of a lowered [DiagramGraph] positioned by a [GraphLayout]
 * (`ide.md` semantics table). Draw order is back-to-front: composite boxes, then edges (so their
 * arrow tips land on node borders), then nodes, then all text on top so labels never sit under a
 * line. Layout coordinates are density-independent; every value is taken through `.dp.toPx()` so the
 * figure scales with the ambient density.
 */
internal fun DrawScope.drawDiagram(
    graph: DiagramGraph,
    layout: GraphLayout,
    colors: DiagramColors,
    tm: TextMeasurer,
    focus: FocusSet? = null,
    sink: DiagramInteractionSink? = null,
) {
    // 描画のたびにラベル/弧の当たり判定ジオメトリを取り直す (`ide-3.md`)。
    sink?.clear()
    // フォーカス対象があるとき、対象外の要素は透明度を落とす (`ide-2.md`)。focus==null は「未選択」で
    // 全要素を通常描画する。ノード/エッジ/stay ごとの alpha を素朴な述語で決め、各描画へ乗算で伝える
    // (既存の STAY_ARC_ALPHA 等と二重適用にならないよう Color.dim は元の alpha に乗算する)。
    fun nodeAlpha(id: NodeId): Float = if (focus == null || focus.isNodeFocused(id)) 1f else FOCUS_DIM_ALPHA
    fun edgeAlpha(edge: GraphEdge): Float = if (focus == null || focus.isEdgeFocused(edge)) 1f else FOCUS_DIM_ALPHA
    // composite 箱は「focus 集合に入っている (focus ノードを内包する / 選択された箱の subtree)」なら通常、
    // そうでなければ減光する (`ide-3.md`。帰属は focusFrom が path ベースで解決済み)。
    fun compositeAlpha(id: NodeId): Float = if (focus == null || focus.isCompositeFocused(id)) 1f else FOCUS_DIM_ALPHA
    val canvasPx = Size(layout.canvasSize.width.dp.toPx(), layout.canvasSize.height.dp.toPx())
    // root frame: store 全体を常に囲う角丸枠 (stay が無くても描く)。root scope の共有 stay は
    // この枠への self-loop として掛かる (any-state ノードの代替表現)。
    // 開始点 ([*] 黒丸) は bbox から除外して枠の外 (LR=左 / TB=上) へ出す (`ide-2.md` #6)。
    // initial の矢印は枠を跨いで最初の State に刺さる。
    val contentPx = graph.nodes.filter { it !is StartNode }
        .mapNotNull { layout.nodeRects[it.id]?.let { r -> px(r) } } +
        graph.composites.mapNotNull { layout.compositeRects[it.id]?.let { r -> px(r) } }
    // 枠と content bbox の間隔。LayeredLayout.placeStartOutsideFrame と同一値を使い、start が必ず枠外に出る。
    val framePad = me.tbsten.koma.strict.idea.layout.layered.LayeredLayout.ROOT_FRAME_PAD.toFloat().dp.toPx()
    val rootFrame: PxRect? = if (contentPx.isEmpty()) {
        null
    } else {
        val minX = contentPx.minOf { it.topLeft.x } - framePad
        val minY = contentPx.minOf { it.topLeft.y } - framePad
        val maxX = contentPx.maxOf { it.right } + framePad
        val maxY = contentPx.maxOf { it.bottom } + framePad
        PxRect(Offset(minX, minY), Size(maxX - minX, maxY - minY))
    }
    rootFrame?.let { f ->
        drawRoundRect(
            color = colors.compositeBorder,
            topLeft = f.topLeft,
            size = f.size,
            cornerRadius = corner(14f),
            style = Stroke(width = 1.2f.dp.toPx()),
        )
    }
    for (box in graph.composites) {
        val r = layout.compositeRects[box.id] ?: continue
        drawComposite(px(r), box.simpleName, colors, tm, compositeAlpha(box.id), focus?.isCompositeSelected(box.id) == true)
    }
    // エッジの線・矢印を先に描き、ラベルは最後にまとめて描く。
    // こうすることで長い recover ラベル等が発生源ノードの箱に隠れず、常にノードの上に読める
    // (ide.md「labels never sit under a line」の徹底: ノードにも隠されない)。
    val labels = mutableListOf<PendingLabel>()
    // 矢印ヘッドはノード描画の後にまとめて描く (ノードの枠で先端が隠れないよう常に最前面へ)。線本体は
    // ノードの下に置いたままにして「線がノードへ入る」見た目を保つ。
    val arrows = mutableListOf<PendingArrow>()
    // 端点分散 + 障害物回避ルーティング (P1-07)。各エッジの経路 (source ポート→target ポート) を
    // layout 座標で純関数計算する。直線が無関係な node interior / composite title strip を貫くなら
    // 折れ線で迂回し、貫かないなら従来通り直線 1 本 (2 点) を返す。ここでは dp 座標のまま受け取り、
    // 描画時に px へ変換する。
    val routes = EdgeRouting.routeAll(graph, layout)
    // px 変換はここで 1 回だけ行い、描画とラベル配置 (自分の線の同一性判定) で同じインスタンスを共有する。
    val pxRoutes = routes.mapValues { (_, pts) -> pts.map { Offset(it.x.dp.toPx(), it.y.dp.toPx()) } }
    // エッジが接続している面を node ごとに集める。self-loop はその面を避けて張る (ループ弧が
    // 遷移線と交差しないように — selfloops サンプルの右面ループ × finish 線)。
    // エッジは 2 点の直線なので、面はポートが乗っている境界辺から判定する。
    val usedFaces = HashMap<NodeId, MutableSet<SelfLoopFace>>()
    for ((edge, pts) in pxRoutes) {
        if (pts.size < 2) continue
        layout.nodeRects[edge.fromId]?.let { r ->
            borderFace(px(r), pts.first())?.let { usedFaces.getOrPut(edge.fromId) { mutableSetOf() }.add(it) }
        }
        layout.nodeRects[edge.toId]?.let { r ->
            borderFace(px(r), pts.last())?.let { usedFaces.getOrPut(edge.toId) { mutableSetOf() }.add(it) }
        }
    }
    for (edge in graph.edges) {
        if (edge.fromId == edge.toId) continue
        drawEdge(edge, colors, labels, arrows, pxRoutes, edgeAlpha(edge), focus?.isEdgeSelected(edge) == true)
    }
    // self-loop は「同一ノード × 同一種別 (enter/action/recover)」で 1 本の弧に集約し、ラベルは
    // 複数行で 1 枚にまとめる (5 本の stay ループが 1 ノードに密集して弧とラベルが絡む問題の対策)。
    // 種別が違うループは色・破線の意味論を保つため別の弧のまま (ordinal で別の面に張られる)。
    val loopGroups = LinkedHashMap<Pair<NodeId, EdgeKind>, MutableList<GraphEdge>>()
    for (edge in graph.edges) {
        if (edge.fromId != edge.toId) continue
        loopGroups.getOrPut(edge.fromId to edge.kind) { mutableListOf() }.add(edge)
    }
    val loopOrdinal = HashMap<NodeId, Int>()
    // self-loop の弧もラベル配置の障害物にする (ピルが弧の胴体を覆って足だけ残るのを防ぐ)。
    // 自分の弧は PendingLabel.pts と同一インスタンスなので identity で除外される。
    val loopPolylines = mutableListOf<List<Offset>>()
    for ((key, groupEdges) in loopGroups) {
        val nodeId = key.first
        val ordinal = loopOrdinal.getOrDefault(nodeId, 0)
        loopOrdinal[nodeId] = ordinal + 1
        val fromR = layout.endpointRect(nodeId)?.let { px(it) } ?: continue
        // ループラベルの折返し幅は所属 composite の内幅にクランプ (枠線の突き抜け防止)。
        val nodeRect = layout.nodeRects[nodeId]
        val boxW = graph.composites.mapNotNull { box ->
            val br = layout.compositeRects[box.id] ?: return@mapNotNull null
            val nr = nodeRect ?: return@mapNotNull null
            val c = nr.center
            if (c.x > br.x && c.x < br.right && c.y > br.y && c.y < br.bottom) br.width else null
        }.minOrNull()
        val maxW = boxW?.let { (it - 16.0).dp.toPx() }
        // self-loop 群は「そのノードに接続するエッジ」なので、群のいずれかが focus なら群ごと通常描画。
        val loopAlpha = if (focus == null || groupEdges.any { focus.isEdgeFocused(it) }) 1f else FOCUS_DIM_ALPHA
        // 群のいずれかが選択されていれば弧ごと強調 (`ide-3.md`)。
        val loopEmph = focus != null && groupEdges.any { focus.isEdgeSelected(it) }
        drawSelfLoop(
            fromR, groupEdges, colors, labels, arrows, ordinal, usedFaces[nodeId].orEmpty(),
            loopPolylines, maxW, loopAlpha, loopEmph, sink,
        )
    }
    // scope 共有の stay は scope の囲い (root frame / composite box) の右辺への self-loop。
    // any-state ノードの stay self-loop の代替表現 (ide.md)。種別ごとに 1 本の弧 + 集約ラベル。
    val scopeStayGroups = LinkedHashMap<Pair<StateId, EdgeKind>, MutableList<ScopeStay>>()
    for (stay in graph.scopeStays) {
        scopeStayGroups.getOrPut(stay.scope to stay.kind) { mutableListOf() }.add(stay)
    }
    val scopeOrdinal = HashMap<StateId, Int>()
    for ((key, stays) in scopeStayGroups) {
        val (scope, kind) = key
        val target: PxRect = when {
            scope.isRoot -> rootFrame
            else -> layout.compositeRects[NodeId.Composite(scope)]?.let { px(it) }
        } ?: continue
        val ordinal = scopeOrdinal.getOrDefault(scope, 0)
        scopeOrdinal[scope] = ordinal + 1
        // scope 共有 stay 群は、群のいずれかが focus なら通常描画、そうでなければ減光する。
        val stayAlpha = if (focus == null || stays.any { focus.isScopeStayFocused(it) }) 1f else FOCUS_DIM_ALPHA
        // 群のいずれかが選択されていれば弧ごと強調 (`ide-3.md`)。選択は代表 stay (群の先頭) に紐づける。
        val stayEmph = focus != null && stays.any { focus.isStaySelected(it) }
        val staySelection = DiagramSelection.Stay(stays.first())
        val color = colors.edgeColor(kind).copy(alpha = STAY_ARC_ALPHA).dim(stayAlpha)
        val dash = if (kind == EdgeKind.RECOVER) PathEffect.dashPathEffect(floatArrayOf(7f, 5f)) else null
        val arrowScale = if (stayEmph) SELECTED_ARROW_SCALE else 1f
        // 右辺固定 (他 3 面を used 扱いにする)。枠が低い時は開口を面に収まる範囲へクランプ。
        val h = target.size.height
        val arc = selfLoopArc(
            left = target.topLeft.x,
            top = target.topLeft.y,
            width = target.size.width,
            height = h,
            ordinal = ordinal,
            opening = minOf(26f.dp.toPx(), h / 3f),
            spread = minOf(60f.dp.toPx(), h / 2.2f),
            lift = 44f.dp.toPx(),
            labelGap = 4f.dp.toPx(),
            usedFaces = setOf(SelfLoopFace.TOP, SelfLoopFace.BOTTOM, SelfLoopFace.LEFT),
        )
        // 弧の終端を barb 手前で止め、矢じりとの二重描画を無くす (`ide-2.md` #3)。選択時は太く。
        val stayStroke = 1.5f * (if (stayEmph) SELECTED_EDGE_WIDTH_MULT else 1f)
        drawPath(
            trimmedArcPath(arc, ARROW_BARB.dp.toPx() * arrowScale),
            color = color,
            style = Stroke(width = stayStroke.dp.toPx(), pathEffect = dash),
        )
        arrows += PendingArrow(Offset(arc.endX, arc.endY), Offset(arc.arrowFromX, arc.arrowFromY), color, arrowScale)
        val arcPoly = (0..8).map { i ->
            val t = i / 8f
            val u = 1f - t
            Offset(
                u * u * u * arc.startX + 3f * u * u * t * arc.ctrl1X + 3f * u * t * t * arc.ctrl2X + t * t * t * arc.endX,
                u * u * u * arc.startY + 3f * u * u * t * arc.ctrl1Y + 3f * u * t * t * arc.ctrl2Y + t * t * t * arc.endY,
            )
        }
        loopPolylines += arcPoly
        // 弧を当たり判定に登録し、scope-stay もクリックで選択できるようにする (`ide-3.md`)。
        sink?.arcs?.add(staySelection to arcPoly)
        val text = stays.joinToString("\n") { it.label }
        if (text.isNotBlank()) {
            labels += PendingLabel(
                text,
                arcPoly.subList(2, 7),
                labelColorOf(kind, colors).copy(alpha = STAY_LABEL_ALPHA).dim(stayAlpha),
                ownLine = arcPoly,
                onLineAllowed = false,
                // 弧は囲いの右辺に付くので、ラベルは弧の外側 (右) へ置き、必ずリーダー線で繋ぐ。
                // これで囲い / State の border を貫通せず、どの弧のラベルかも辿れる (`ide-2.md` #2)。
                outwardDir = Offset(1f, 0f),
                forceLeader = true,
                selection = staySelection,
                emphasized = stayEmph,
            )
        }
    }
    for (node in graph.nodes) {
        val r = layout.nodeRects[node.id]?.let { px(it) } ?: continue
        val a = nodeAlpha(node.id)
        val selected = focus?.isNodeSelected(node.id) == true
        when (node) {
            is StartNode -> drawStart(r, colors, a)
            is StateGraphNode -> {
                drawStateNode(r, node, colors, tm, a, selected)
                node.exitBadge?.let { drawExitBadge(r, it, colors, tm, layout.direction, a) }
            }
            is AnyStateNode -> {
                drawAnyNode(r, node.label, colors, tm, a, selected)
                node.exitBadge?.let { drawExitBadge(r, it, colors, tm, layout.direction, a) }
            }
        }
    }
    // ノードの後に矢印ヘッドを描く = 先端がノードの枠に隠れず常に最前面に見える。選択エッジは scale で拡大。
    for (a in arrows) drawArrowHead(a.tip, a.from, a.color, a.scale)
    // 既に置いたラベル矩形を覚えておき、後続ラベルが重なるなら縦にずらして退避させる
    // (対向ペアの法線オフセットに加え、交差エッジ由来の団子状の重なりも解消する)。
    // ノード矩形はラベル配置の禁止領域: 不透明ピルが state 名やスタブ (線の根本) を覆わないようにする。
    // composite の見出しテキスト矩形 (drawComposite と同じ位置 + 実測サイズ) も禁止領域: ピルが箱の
    // 見出し (sealed 名) を覆わないようにする。帯全体ではなく文字の矩形だけに絞ることで、上が詰まった
    // 図でもラベルが横に滑って逃げる余地を残す。
    val nodeBoxes = graph.nodes.mapNotNull { node ->
        layout.nodeRects[node.id]?.let { px(it) }?.let { LabelBox(it.topLeft.x, it.topLeft.y, it.size.width, it.size.height) }
    } + graph.composites.mapNotNull { box ->
        layout.compositeRects[box.id]?.let { px(it) }?.let { r ->
            val laid = tm.measure(box.simpleName, TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold))
            LabelBox(r.topLeft.x + 8f.dp.toPx(), r.topLeft.y + 5f.dp.toPx(), laid.size.width.toFloat(), laid.size.height.toFloat())
        }
    } + graph.nodes.mapNotNull { node ->
        // @OnExit バッジのピルも禁止領域 (ラベルが「exit / …」を覆った auth-tb-canvas の退行対策)。
        // 矩形は drawExitBadge と同じ計算で導出する。
        val badge = when (node) {
            is StateGraphNode -> node.exitBadge
            is AnyStateNode -> node.exitBadge
            else -> null
        } ?: return@mapNotNull null
        layout.nodeRects[node.id]?.let { px(it) }?.let { r ->
            val laid = tm.measure(badge, TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Medium))
            val w = laid.size.width + 12f.dp.toPx()
            val h = laid.size.height + 6f.dp.toPx()
            when (layout.direction) {
                LayoutDirection.LR -> LabelBox(r.right + 8f.dp.toPx(), r.center.y - h / 2, w, h)
                LayoutDirection.TB -> LabelBox(r.center.x - w / 2, r.bottom + 8f.dp.toPx(), w, h)
            }
        }
    }
    // 他エッジの折れ線 (px) + self-loop の弧はラベルの禁止領域候補: 不透明ピルが自分以外の線や弧を
    // 覆い隠さないようにする。pxRoutes / loopPolylines と同一インスタンスなので、drawEdgeLabel は
    // 参照同一性で「自分の線 (pts) / 自分の弧 (ownLine)」を除外できる。
    val allPolylines = pxRoutes.values.toList() + loopPolylines
    val placed = mutableListOf<LabelBox>()
    for (label in labels) {
        drawEdgeLabel(
            label.text, label.pts, label.color, colors, tm, canvasPx, placed, nodeBoxes, allPolylines,
            label.maxWidthPx, label.ownLine, label.onLineAllowed, label.outwardDir, label.forceLeader,
            label.emphasized, label.selection, sink,
        )
    }
}

// stay (self transition) の弧・矢じりの透明度。本流の遷移より一段引かせるが、薄すぎて潰れない程度に
// ほんの少し濃くする (`ide-2.md` #1)。
internal const val STAY_ARC_ALPHA = 0.5f

// stay のラベル文字の透明度 (可読性優先で弧より濃いめ)。
internal const val STAY_LABEL_ALPHA = 0.62f

// 矢じり (三角形) の付け根までの長さ (dp)。線はこの手前で止め、tip での二重描画を無くす (`ide-2.md` #3)。
internal const val ARROW_BARB = 9f

// フォーカス対象外の要素の減光係数 (`ide-2.md`: 透明度を落とす)。元 alpha に乗算する。
internal const val FOCUS_DIM_ALPHA = 0.15f

// 選択そのもの (tier 1) の強調パラメータ (`ide-3.md`)。強調色はテーマ accent。
// State / composite は accent border で囲い、Transition は線を太く + ラベルを accent 枠で囲う。
internal const val SELECTED_NODE_BORDER_DP = 2.5f
internal const val SELECTED_EDGE_WIDTH_MULT = 2.5f
internal const val SELECTED_ARROW_SCALE = 1.6f

/**
 * Draw-time geometry captured so the tap handler can hit-test labels and self-loop / scope-stay arcs,
 * whose final placement is only known while drawing (collision-avoided labels, arc apexes). All
 * coordinates are the pre-scale px of [drawDiagram]; the caller compares against `offset / renderZoom`.
 * Cleared and refilled on every draw pass.
 */
internal class DiagramInteractionSink {
    /** Label pill rectangles → the selection a click inside makes (an [DiagramSelection.Edge] / [DiagramSelection.Stay]). */
    val labelBoxes = mutableListOf<Pair<DiagramSelection, PxBox>>()

    /** Self-loop / scope-stay arc poly-lines → their selection (distance-tested like an edge line). */
    val arcs = mutableListOf<Pair<DiagramSelection, List<Offset>>>()

    fun clear() {
        labelBoxes.clear()
        arcs.clear()
    }
}

/** An axis-aligned px rectangle for sink hit-testing. */
internal class PxBox(val left: Float, val top: Float, val w: Float, val h: Float) {
    fun contains(x: Float, y: Float): Boolean = x >= left && x <= left + w && y >= top && y <= top + h
}

/** Multiplies this color's existing alpha by [factor] (1f = unchanged) — the focus dimming step. */
internal fun Color.dim(factor: Float): Color = if (factor >= 1f) this else copy(alpha = alpha * factor)

/** The node face a border port [p] sits on (null when the point is not on [r]'s border). */
private fun borderFace(r: PxRect, p: Offset): SelfLoopFace? {
    val eps = 1.5f
    return when {
        abs(p.y - r.topLeft.y) < eps -> SelfLoopFace.TOP
        abs(p.y - r.bottom) < eps -> SelfLoopFace.BOTTOM
        abs(p.x - r.topLeft.x) < eps -> SelfLoopFace.LEFT
        abs(p.x - r.right) < eps -> SelfLoopFace.RIGHT
        else -> null
    }
}

private fun DrawScope.px(r: Rect): PxRect =
    PxRect(Offset(r.x.dp.toPx(), r.y.dp.toPx()), Size(r.width.dp.toPx(), r.height.dp.toPx()))
