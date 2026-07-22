package me.tbsten.koma.strict.idea.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
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
import kotlin.math.hypot

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

/**
 * An edge label deferred to the final draw pass (drawn on top of nodes). Carries the edge's routed
 * poly-line [pts] so placement can sit the label on the line and slide it *along the whole edge* (by
 * arc length, not just the first segment — which is only the short port stub) to dodge collisions.
 * A self-loop passes a single point.
 */
private class PendingLabel(
    val text: String,
    val pts: List<Offset>,
    val color: Color,
    /** Wrap-width cap in px (composite width clamp for loop labels); null = the global 220dp only. */
    val maxWidthPx: Float? = null,
    /** The full own line (self-loop arc) — obstacle-excluded by identity and leader-line target. */
    val ownLine: List<Offset>? = null,
    /** False for loop labels: the pill must sit beside the arc, never on it (dist=0 is skipped). */
    val onLineAllowed: Boolean = true,
    /**
     * Unit vector pointing *away from the node* for a horizontal-face (TOP/BOTTOM) self-loop; null
     * otherwise. Placement tries this side first so the opaque pill never lands over the loop's legs
     * (a pill wider than the 18dp foot-gap would otherwise hide the arc and flatten it).
     */
    val outwardDir: Offset? = null,
    /**
     * Forces a leader line from the pill to the arc regardless of distance (`ide-2.md` #2). Set for
     * scope-shared stays and right/left-face self-loops whose labels sit beside the enclosure border:
     * the thin leader keeps the pill off the border while still tying it to its arc.
     */
    val forceLeader: Boolean = false,
    /** The selection a click on this pill makes (`ide-3.md`); null for a non-selectable label. */
    val selection: DiagramSelection? = null,
    /** True when this label's transition / stay is selected — the pill gets an accent border. */
    val emphasized: Boolean = false,
)

/** An arrow head deferred until after the nodes are drawn so a node box never hides its tip. [scale] enlarges a selected edge's head. */
private class PendingArrow(val tip: Offset, val from: Offset, val color: Color, val scale: Float = 1f)

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
private fun Color.dim(factor: Float): Color = if (factor >= 1f) this else copy(alpha = alpha * factor)

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

/** An axis-aligned label rectangle used for the final-pass collision avoidance. */
private class LabelBox(val left: Float, val top: Float, val w: Float, val h: Float) {
    fun intersects(o: LabelBox): Boolean =
        left < o.left + o.w && left + w > o.left && top < o.top + o.h && top + h > o.top
}

// ---- nodes ----

private fun DrawScope.drawStateNode(
    r: PxRect,
    node: StateGraphNode,
    colors: DiagramColors,
    tm: TextMeasurer,
    alpha: Float = 1f,
    selected: Boolean = false,
) {
    val fill = (if (node.reachable) colors.nodeFill else colors.warningFill).dim(alpha)
    // 選択時は accent border で太く囲う (`ide-3.md` tier 1)。選択ノードは常に alpha=1 なので dim しない。
    val border = if (selected) colors.accent else (if (node.reachable) colors.nodeBorder else colors.warningBorder).dim(alpha)
    val borderW = if (selected) SELECTED_NODE_BORDER_DP else 1.5f
    val text = (if (node.reachable) colors.nodeText else colors.warningText).dim(alpha)
    drawRoundRect(color = fill, topLeft = r.topLeft, size = r.size, cornerRadius = corner(10f))
    drawRoundRect(color = border, topLeft = r.topLeft, size = r.size, cornerRadius = corner(10f), style = Stroke(width = borderW.dp.toPx()))
    drawCentered(tm, node.simpleName, r, text, 12.sp, FontWeight.Medium)
}

/**
 * The `@OnExit` badge: a small pill beside the node (`ide.all.md` §5 — exit cannot transition, so it
 * is a tag rather than an edge). In `LR` it sits to the node's right (where the next layer is far
 * away); in `TB` it sits directly below the node, because the right neighbor is a same-layer sibling
 * only `siblingGap` away and a right-side pill would collide with it. The layout reserves matching
 * canvas room on that side so the pill never clips.
 */
private fun DrawScope.drawExitBadge(
    nodeRect: PxRect,
    text: String,
    colors: DiagramColors,
    tm: TextMeasurer,
    direction: LayoutDirection,
    alpha: Float = 1f,
) {
    val laid = tm.measure(text, TextStyle(color = colors.exitText.dim(alpha), fontSize = 9.sp, fontWeight = FontWeight.Medium))
    val padX = 6f.dp.toPx()
    val padY = 3f.dp.toPx()
    val w = laid.size.width + padX * 2
    val h = laid.size.height + padY * 2
    val gap = 8f.dp.toPx()
    val topLeft = when (direction) {
        LayoutDirection.LR -> Offset(nodeRect.right + gap, nodeRect.center.y - h / 2)
        LayoutDirection.TB -> Offset(nodeRect.center.x - w / 2, nodeRect.bottom + gap)
    }
    drawRoundRect(color = colors.exitFill.dim(alpha), topLeft = topLeft, size = Size(w, h), cornerRadius = corner(6f))
    drawText(laid, topLeft = Offset(topLeft.x + padX, topLeft.y + padY))
}

private fun DrawScope.drawStart(r: PxRect, colors: DiagramColors, alpha: Float = 1f) {
    // UML initial marker: a solid filled dot.
    val radius = minOf(r.size.width, r.size.height) / 2
    drawCircle(color = colors.startFill.dim(alpha), radius = radius, center = r.center)
}

private fun DrawScope.drawAnyNode(
    r: PxRect,
    label: String,
    colors: DiagramColors,
    tm: TextMeasurer,
    alpha: Float = 1f,
    selected: Boolean = false,
) {
    // any-state 擬似ノードは枠なし (塗りと文字だけ)。実 state との違いは「枠が無い」ことで表す。
    drawRoundRect(color = colors.anyFill.dim(alpha), topLeft = r.topLeft, size = r.size, cornerRadius = corner(24f))
    // 選択時だけ accent border を足して強調 (`ide-3.md` tier 1)。
    if (selected) {
        drawRoundRect(
            color = colors.accent,
            topLeft = r.topLeft,
            size = r.size,
            cornerRadius = corner(24f),
            style = Stroke(width = SELECTED_NODE_BORDER_DP.dp.toPx()),
        )
    }
    drawCentered(tm, label, r, colors.anyText.dim(alpha), 11.sp, FontWeight.Normal)
}

private fun DrawScope.drawComposite(
    r: PxRect,
    name: String,
    colors: DiagramColors,
    tm: TextMeasurer,
    alpha: Float = 1f,
    selected: Boolean = false,
) {
    // nest state (composite) は半透明の灰背景で「領域」を面として見せる (入れ子は自然に濃くなる)。
    drawRoundRect(
        color = colors.compositeBorder.copy(alpha = 0.08f).dim(alpha),
        topLeft = r.topLeft,
        size = r.size,
        cornerRadius = corner(12f),
    )
    // 選択時は accent border で太く囲う (`ide-3.md` tier 1)。
    val border = if (selected) colors.accent else colors.compositeBorder.dim(alpha)
    val borderW = if (selected) SELECTED_NODE_BORDER_DP else 1.2f
    drawRoundRect(
        color = border,
        topLeft = r.topLeft,
        size = r.size,
        cornerRadius = corner(12f),
        style = Stroke(width = borderW.dp.toPx()),
    )
    val laid = tm.measure(name, TextStyle(color = colors.compositeLabel.dim(alpha), fontSize = 11.sp, fontWeight = FontWeight.SemiBold))
    drawText(laid, topLeft = Offset(r.topLeft.x + 8f.dp.toPx(), r.topLeft.y + 5f.dp.toPx()))
}

// ---- edges ----

private fun DrawScope.drawEdge(
    edge: GraphEdge,
    colors: DiagramColors,
    labels: MutableList<PendingLabel>,
    arrows: MutableList<PendingArrow>,
    pxRoutes: Map<GraphEdge, List<Offset>>,
    alpha: Float = 1f,
    emphasized: Boolean = false,
) {
    val color = colors.edgeColor(edge.kind).dim(alpha)
    // recover (@OnRecover) は横断的なエラー処理なので破線で通常遷移と見分ける (ide.all.md §5)。
    val dash = if (edge.kind == EdgeKind.RECOVER) PathEffect.dashPathEffect(floatArrayOf(7f, 5f)) else null
    // 障害物回避ルーティング済みの折れ線 (px 変換済み) の各区間を描く。無関係な node interior /
    // composite title strip を貫かない (直線で足りるエッジは 2 点 = 従来と同一の直線)。
    val pts = pxRoutes[edge] ?: return
    if (pts.size < 2) return
    // 選択エッジ (tier 1) は線を太く・矢じりを拡大する (`ide-3.md`)。色は kind 色のまま (accent は border/ラベル枠のみ)。
    val arrowScale = if (emphasized) SELECTED_ARROW_SCALE else 1f
    val strokeW = 1.5f * (if (emphasized) SELECTED_EDGE_WIDTH_MULT else 1f)
    // 線は arrowhead の付け根 (tip から barb 手前) で止める: 矢じり三角形と線本体の二重描画を無くし、
    // 半透明 stay でも tip の重なりが濃くならない (`ide-2.md` #3)。矢じりは元の tip/向きで描くので、
    // 不透明エッジの見た目 (矢印がノード境界にきっちり刺さる) は不変。
    val drawPts = trimPolylineEnd(pts, ARROW_BARB.dp.toPx() * arrowScale)
    for (i in 0 until drawPts.size - 1) {
        drawLine(color = color, start = drawPts[i], end = drawPts[i + 1], strokeWidth = strokeW.dp.toPx(), pathEffect = dash)
    }
    val end = pts.last()
    val beforeEnd = pts[pts.size - 2]
    // 矢印は最終区間の向きで target 面に刺す (arrowhead-on-top は node 描画後に描かれる)。
    arrows += PendingArrow(end, beforeEnd, color, arrowScale)
    edge.label.takeIf { it.isNotBlank() }?.let { text ->
        // ラベルは全エッジに付ける (fan-out も 1 本ずつ別の直線なので各自のラベルが要る)。
        // 位置決め・衝突回避 (ノード / 他の線 / 既置ラベル) は drawEdgeLabel 側。ラベルクリックでこのエッジを選択。
        labels += PendingLabel(
            text,
            pts,
            labelColor(edge, colors).dim(alpha),
            selection = DiagramSelection.Edge(edge),
            emphasized = emphasized,
        )
    }
}

// ラベル色は線の色 (edgeColor) にそのまま揃える (`ide-2.md` #4): recover=紫・action=水色・
// enter/initial=neutral edge。ENTER も線と同じ muted edge 色にして「線とラベルが一体」に見せる。
private fun labelColor(edge: GraphEdge, colors: DiagramColors): Color = labelColorOf(edge.kind, colors)

private fun labelColorOf(kind: EdgeKind, colors: DiagramColors): Color = colors.edgeColor(kind)

/**
 * Draws the merged self-loop arc of one (node, kind) group: **one arc per trigger kind**, with every
 * member's label stacked into one multi-line pill at the arc apex. Merging same-kind loops keeps a
 * node with many stay actions from sprouting a tangle of arcs, while different kinds (enter / action
 * / recover) stay separate arcs so the colour / dash semantics survive. Groups are told apart by
 * [ordinal] which fans them onto different free faces via [selfLoopArc].
 */
private fun DrawScope.drawSelfLoop(
    r: PxRect,
    edges: List<GraphEdge>,
    colors: DiagramColors,
    labels: MutableList<PendingLabel>,
    arrows: MutableList<PendingArrow>,
    ordinal: Int,
    usedFaces: Set<SelfLoopFace>,
    loopPolylines: MutableList<List<Offset>>,
    labelMaxWidthPx: Float?,
    alpha: Float = 1f,
    emphasized: Boolean = false,
    sink: DiagramInteractionSink? = null,
) {
    val kind = edges.first().kind
    // 選択された self-loop (tier 1) は弧を太く・矢じりを拡大する (`ide-3.md`)。選択はこの弧の代表エッジに紐づく。
    val loopSelection = DiagramSelection.Edge(edges.first())
    val arrowScale = if (emphasized) SELECTED_ARROW_SCALE else 1f
    // stay (self-loop) は本流の遷移より一段引かせる: 弧・矢じりをうっすら半透明にして、
    // 図の骨格 (state 間遷移) が先に目に入るようにする (透明度は STAY_ARC_ALPHA / STAY_LABEL_ALPHA)。
    // focus 減光 (alpha) は元の stay 透明度に乗算する (二重適用を避ける)。
    val color = colors.edgeColor(kind).copy(alpha = STAY_ARC_ALPHA).dim(alpha)
    val dash = if (kind == EdgeKind.RECOVER) PathEffect.dashPathEffect(floatArrayOf(7f, 5f)) else null
    // ループを大きめ・丸めにして「クルッ」と自己遷移だと分かるようにする。制御点 (spread) を開口 (opening)
    // より外へ広げると弧が外側へ膨らんで丸くなる。lift は compositePadding (箱の内余白) 以下に保つ
    // (top-row member のループが箱の上辺を突き抜けないように)。
    val arc = selfLoopArc(
        left = r.topLeft.x,
        top = r.topLeft.y,
        width = r.size.width,
        height = r.size.height,
        ordinal = ordinal,
        opening = 18f.dp.toPx(),
        spread = 48f.dp.toPx(),
        lift = 32f.dp.toPx(),
        labelGap = 4f.dp.toPx(),
        usedFaces = usedFaces,
    )
    // 弧も終端を barb 手前で止め、矢じりとの二重描画を無くす (`ide-2.md` #3)。矢じりは元の tip で描く。選択時は太く。
    val loopStroke = 1.5f * (if (emphasized) SELECTED_EDGE_WIDTH_MULT else 1f)
    drawPath(
        trimmedArcPath(arc, ARROW_BARB.dp.toPx() * arrowScale),
        color = color,
        style = Stroke(width = loopStroke.dp.toPx(), pathEffect = dash),
    )
    arrows += PendingArrow(Offset(arc.endX, arc.endY), Offset(arc.arrowFromX, arc.arrowFromY), color, arrowScale)
    // 弧を折れ線に近似してラベル配置の障害物に登録する (他のラベルのピルが弧を覆い隠さないように)。
    val arcPoly = (0..8).map { i ->
        val t = i / 8f
        val u = 1f - t
        Offset(
            u * u * u * arc.startX + 3f * u * u * t * arc.ctrl1X + 3f * u * t * t * arc.ctrl2X + t * t * t * arc.endX,
            u * u * u * arc.startY + 3f * u * u * t * arc.ctrl1Y + 3f * u * t * t * arc.ctrl2Y + t * t * t * arc.endY,
        )
    }
    loopPolylines += arcPoly
    // 弧を当たり判定に登録し、node self-loop もクリックで選択できるようにする (`ide-3.md`)。
    sink?.arcs?.add(loopSelection to arcPoly)
    // 上下面 (TOP/BOTTOM) のループはピルが弧より横に広く、内側 (ノード寄り) に置くと弧の脚を覆って
    // 扁平に見える。弧の頂点方向 = ノードから離れる向きを outward として渡し、ピルを常に弧の外側へ
    // 逃がす (左右面は開口が縦なので 1 行ピルが脚を覆わず、従来通り null = 現状維持)。
    val face = selfLoopFace(ordinal, usedFaces)
    val outward: Offset? = when (face) {
        SelfLoopFace.TOP, SelfLoopFace.BOTTOM -> {
            val footMid = Offset((arc.startX + arc.endX) / 2f, (arc.startY + arc.endY) / 2f)
            val apex = arcPoly[4]
            val dx = apex.x - footMid.x
            val dy = apex.y - footMid.y
            val len = hypot(dx, dy)
            if (len > 0.0001f) Offset(dx / len, dy / len) else null
        }
        // 右/左面ループは弧がノードの外 (右/左) へ膨らむ。ラベルもその外側へ逃がす。
        SelfLoopFace.RIGHT -> Offset(1f, 0f)
        SelfLoopFace.LEFT -> Offset(-1f, 0f)
    }
    // 右/左面のラベルはノード border の脇に付くので必ずリーダー線を出して帰属と border 回避を明示する
    // (`ide-2.md` #2)。上下面は従来通り距離ベース (近接時は描かない)。
    val forceLeader = face == SelfLoopFace.RIGHT || face == SelfLoopFace.LEFT
    // 同一種別の全ループのラベルを 1 枚の複数行ピルに積む (弧は 1 本なのでラベルも 1 箇所)。
    // pts に弧の折れ線そのものを渡す: ラベルはエッジラベルと同じ機構で「弧に沿って滑り、弧の脇に
    // 退避する」ため、常に自分の弧の近くに置かれる (点アンカー時代の「混雑で弧から 100px 浮いて
    // 帰属不明」を構造的に防ぐ)。arcPoly は loopPolylines と同一インスタンスなので障害物判定からは
    // 参照同一性で除外される。折返し幅は所属 composite の幅にクランプし、枠線の突き抜けを防ぐ。
    val text = edges.mapNotNull { e -> e.label.takeIf { it.isNotBlank() } }.joinToString("\n")
    if (text.isNotBlank()) {
        // pts は弧の頂点区間 (t=0.25..0.75) のみ: 足 (ノード境界上の点) を含めると全候補が塞がった時の
        // fallback がノードの真上に落ち、state 名やラベル自身が欠ける。
        // onLineAllowed=false で「弧上」配置も禁止し、ピルは常に弧の脇 = 弧が隠れない。
        labels += PendingLabel(
            text,
            arcPoly.subList(2, 7),
            // ラベルも弧に合わせて少しだけ引かせる (文字は可読性を優先して弧より濃いめ)。
            labelColorOf(kind, colors).copy(alpha = STAY_LABEL_ALPHA).dim(alpha),
            maxWidthPx = labelMaxWidthPx,
            ownLine = arcPoly,
            onLineAllowed = false,
            outwardDir = outward,
            forceLeader = forceLeader,
            selection = loopSelection,
            emphasized = emphasized,
        )
    }
}

/** Which side of a node a self-loop attaches to. Successive loops on one node cycle through the faces. */
internal enum class SelfLoopFace { TOP, BOTTOM, RIGHT, LEFT }

// self-loop の面の優先順。エッジが接続していない面から順に使う。
private val SELF_LOOP_FACE_ORDER = listOf(SelfLoopFace.TOP, SelfLoopFace.BOTTOM, SelfLoopFace.RIGHT, SelfLoopFace.LEFT)

/**
 * The face the [ordinal]-th self-loop of a node uses: top / bottom / right / left in that order,
 * skipping the faces in [usedFaces] (faces where transition edges attach) so a loop arc never crosses
 * an edge line leaving the same node. When every face is used, all four stay available (wrapping).
 */
internal fun selfLoopFace(ordinal: Int, usedFaces: Set<SelfLoopFace> = emptySet()): SelfLoopFace {
    val free = SELF_LOOP_FACE_ORDER.filter { it !in usedFaces }.ifEmpty { SELF_LOOP_FACE_ORDER }
    return free[ordinal.mod(free.size)]
}

/**
 * Pure geometry of one self-loop arc (all values in px), so the placement can be asserted without a
 * live `DrawScope` (`P1-05` regression test). [startX]/[startY]..[endX]/[endY] are the loop's feet on
 * the node border, [ctrl1X]/[ctrl1Y]/[ctrl2X]/[ctrl2Y] the cubic control points that bulge the arc
 * outward, ([arrowFromX],[arrowFromY])->([endX],[endY]) the incoming arrow direction, and
 * ([labelX],[labelY]) where the trigger label centers.
 */
internal class SelfLoopArc(
    val startX: Float,
    val startY: Float,
    val ctrl1X: Float,
    val ctrl1Y: Float,
    val ctrl2X: Float,
    val ctrl2Y: Float,
    val endX: Float,
    val endY: Float,
    val arrowFromX: Float,
    val arrowFromY: Float,
    val labelX: Float,
    val labelY: Float,
)

/**
 * Builds the [ordinal]-th self-loop arc for the node rect ([left],[top],[width],[height]). The face is
 * chosen by [selfLoopFace] so multiple loops on one node don't overlap; every 4th loop nests one ring
 * further out ([opening]/[spread]/[lift] grow) so even 5+ loops stay distinct. All values are px.
 */
internal fun selfLoopArc(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    ordinal: Int,
    opening: Float,
    spread: Float,
    lift: Float,
    labelGap: Float,
    usedFaces: Set<SelfLoopFace> = emptySet(),
): SelfLoopArc {
    val right = left + width
    val bottom = top + height
    val freeCount = SELF_LOOP_FACE_ORDER.count { it !in usedFaces }.takeIf { it > 0 } ?: SELF_LOOP_FACE_ORDER.size
    val slot = ordinal / freeCount
    val face = selfLoopFace(ordinal, usedFaces)
    // 同一面の 2 本目以降は「外へ重ねるリング」ではなく面に沿って横へずらした耳にする (重ねると弧・
    // 矢印・ラベルが密集して読めない)。左右面は面が短く耳を並べる余地がないので従来のリングで逃がす。
    val horizontal = face == SelfLoopFace.TOP || face == SelfLoopFace.BOTTOM
    val earStep = 2f * opening + 14f
    val rawShift = if (slot == 0) 0f else (if (slot % 2 == 1) -1f else 1f) * ((slot + 1) / 2).toFloat() * earStep
    val maxShift = ((if (horizontal) width else height) / 2f - opening - 8f).coerceAtLeast(0f)
    val shift = rawShift.coerceIn(-maxShift, maxShift)
    val ring = if (horizontal) 0 else slot
    val op = opening + ring * 8f
    val sp = spread + ring * 14f
    val lf = lift + ring * 22f
    val cx = left + width / 2f + (if (horizontal) shift else 0f)
    val cy = top + height / 2f + (if (!horizontal) shift else 0f)
    return when (face) {
        SelfLoopFace.TOP -> SelfLoopArc(
            startX = cx - op, startY = top,
            ctrl1X = cx - sp, ctrl1Y = top - lf,
            ctrl2X = cx + sp, ctrl2Y = top - lf,
            endX = cx + op, endY = top,
            arrowFromX = cx + op + 3f, arrowFromY = top - lf * 0.35f,
            labelX = cx, labelY = top - lf - labelGap,
        )
        SelfLoopFace.BOTTOM -> SelfLoopArc(
            startX = cx - op, startY = bottom,
            ctrl1X = cx - sp, ctrl1Y = bottom + lf,
            ctrl2X = cx + sp, ctrl2Y = bottom + lf,
            endX = cx + op, endY = bottom,
            arrowFromX = cx + op + 3f, arrowFromY = bottom + lf * 0.35f,
            labelX = cx, labelY = bottom + lf + labelGap,
        )
        SelfLoopFace.RIGHT -> SelfLoopArc(
            startX = right, startY = cy - op,
            ctrl1X = right + lf, ctrl1Y = cy - sp,
            ctrl2X = right + lf, ctrl2Y = cy + sp,
            endX = right, endY = cy + op,
            arrowFromX = right + lf * 0.35f, arrowFromY = cy + op + 3f,
            labelX = right + lf + labelGap, labelY = cy,
        )
        SelfLoopFace.LEFT -> SelfLoopArc(
            startX = left, startY = cy - op,
            ctrl1X = left - lf, ctrl1Y = cy - sp,
            ctrl2X = left - lf, ctrl2Y = cy + sp,
            endX = left, endY = cy + op,
            arrowFromX = left - lf * 0.35f, arrowFromY = cy + op + 3f,
            labelX = left - lf - labelGap, labelY = cy,
        )
    }
}

/**
 * Returns [pts] with its final point pulled back [cut] px along the last segment, so a polyline edge
 * stops at the arrow base instead of running under the arrow head (`ide-2.md` #3: no doubled alpha at
 * the tip). Only the last segment is affected; a segment shorter than [cut] collapses to zero length.
 */
internal fun trimPolylineEnd(pts: List<Offset>, cut: Float): List<Offset> {
    if (pts.size < 2) return pts
    val tip = pts.last()
    val prev = pts[pts.size - 2]
    val len = hypot(tip.x - prev.x, tip.y - prev.y)
    if (len <= 0.0001f) return pts
    val c = minOf(cut, len)
    val end = Offset(tip.x - (tip.x - prev.x) / len * c, tip.y - (tip.y - prev.y) / len * c)
    return pts.dropLast(1) + end
}

/**
 * Trims [cut] px of *arc length* off the end of the cubic [p0]..[p3] and returns the left sub-cubic's
 * four control points (via De Casteljau split). Keeps the curve smooth while stopping it short of the
 * arrow tip (`ide-2.md` #3). If the whole curve is shorter than [cut], the original points are kept.
 */
internal fun trimCubicEnd(p0: Offset, p1: Offset, p2: Offset, p3: Offset, cut: Float): List<Offset> {
    val samples = 32
    val ts = FloatArray(samples + 1)
    val cum = FloatArray(samples + 1)
    var prev = p0
    var total = 0f
    for (i in 0..samples) {
        val t = i / samples.toFloat()
        val pt = cubicPoint(p0, p1, p2, p3, t)
        if (i > 0) total += hypot(pt.x - prev.x, pt.y - prev.y)
        ts[i] = t
        cum[i] = total
        prev = pt
    }
    if (total <= cut) return listOf(p0, p1, p2, p3)
    val target = total - cut
    var tEnd = 1f
    for (i in 1..samples) {
        if (cum[i] >= target) {
            val span = cum[i] - cum[i - 1]
            val f = if (span > 0f) (target - cum[i - 1]) / span else 0f
            tEnd = ts[i - 1] + (ts[i] - ts[i - 1]) * f
            break
        }
    }
    val a = lerp(p0, p1, tEnd)
    val b = lerp(p1, p2, tEnd)
    val c = lerp(p2, p3, tEnd)
    val d = lerp(a, b, tEnd)
    val e = lerp(b, c, tEnd)
    return listOf(p0, a, d, lerp(d, e, tEnd))
}

/** Point on the cubic [p0]..[p3] at parameter [t]. */
private fun cubicPoint(p0: Offset, p1: Offset, p2: Offset, p3: Offset, t: Float): Offset {
    val u = 1f - t
    val a = u * u * u
    val b = 3f * u * u * t
    val c = 3f * u * t * t
    val d = t * t * t
    return Offset(a * p0.x + b * p1.x + c * p2.x + d * p3.x, a * p0.y + b * p1.y + c * p2.y + d * p3.y)
}

/** The cubic path of [arc] with its end trimmed [cut] px short of the tip (feeds the arrow head). */
private fun trimmedArcPath(arc: SelfLoopArc, cut: Float): Path {
    val t = trimCubicEnd(
        Offset(arc.startX, arc.startY),
        Offset(arc.ctrl1X, arc.ctrl1Y),
        Offset(arc.ctrl2X, arc.ctrl2Y),
        Offset(arc.endX, arc.endY),
        cut,
    )
    return Path().apply {
        moveTo(t[0].x, t[0].y)
        cubicTo(t[1].x, t[1].y, t[2].x, t[2].y, t[3].x, t[3].y)
    }
}

private fun DrawScope.drawArrowHead(tip: Offset, from: Offset, color: Color, scale: Float = 1f) {
    val dx = tip.x - from.x
    val dy = tip.y - from.y
    val len = hypot(dx, dy).takeIf { it > 0.0001f } ?: return
    val ux = dx / len
    val uy = dy / len
    // 選択エッジ (`ide-3.md`) は矢じりを scale で拡大する (太線と釣り合わせる)。
    val barb = ARROW_BARB.dp.toPx() * scale
    val half = 4.5f.dp.toPx() * scale
    val baseX = tip.x - ux * barb
    val baseY = tip.y - uy * barb
    val px = -uy
    val py = ux
    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(baseX + px * half, baseY + py * half)
        lineTo(baseX - px * half, baseY - py * half)
        close()
    }
    drawPath(path, color = color, style = Fill)
}

private fun DrawScope.drawEdgeLabel(
    text: String,
    pts: List<Offset>,
    textColor: Color,
    colors: DiagramColors,
    tm: TextMeasurer,
    canvasPx: Size,
    placed: MutableList<LabelBox>,
    nodeBoxes: List<LabelBox>,
    allPolylines: List<List<Offset>>,
    maxWidthPx: Float? = null,
    ownLine: List<Offset>? = null,
    onLineAllowed: Boolean = true,
    outwardDir: Offset? = null,
    forceLeader: Boolean = false,
    emphasized: Boolean = false,
    selection: DiagramSelection? = null,
    sink: DiagramInteractionSink? = null,
) {
    if (pts.isEmpty()) return
    // ラベル中心は線の開始点 (source ポート = pts[0]) から最低 12dp 離す (`ide-2.md` #7): 開始点に
    // 近すぎると読みにくく、self-loop では脚に被って弧が潰れる。線長に応じた下限 fraction を課す。
    val minStartFrac = polylineLength(pts).takeIf { it > 0f }
        ?.let { (LABEL_MIN_START_GAP.dp.toPx() / it).coerceIn(0f, 0.5f) } ?: 0f
    // ラベルは最大幅を決めて折り返す (長いトリガ名や複数 emit で 1 行が伸びすぎないように)。
    // camelCase 境界にも折り返し点を入れ、複数行は中央揃えで積む (self-loop の集約ラベルも同経路)。
    val wrapWidth = minOf(LABEL_MAX_WIDTH_DP.dp.toPx(), maxWidthPx ?: Float.MAX_VALUE).toInt().coerceAtLeast(60)
    val laid = tm.measure(
        text = softBreakable(text),
        style = TextStyle(color = textColor, fontSize = 10.sp, textAlign = TextAlign.Center),
        softWrap = true,
        constraints = Constraints(maxWidth = wrapWidth),
    )
    val w = laid.size.width.toFloat()
    val h = laid.size.height.toFloat()
    val padX = 4f.dp.toPx()
    val padY = 2f.dp.toPx()
    // ラベルは矢印の出始め (source 寄り) に、線がラベル中心を通る形で置く: -[onEnter]-----> のイメージ。
    // 位置は折れ線全体の弧長比で取る (最初の区間だけだと今は 16dp のスタブなので境界に張り付いてしまう)。
    // ノード矩形は禁止領域: 不透明ピルが state 名やスタブを覆わない。自分以外のエッジの線も禁止領域:
    // ピルが他の線を覆い隠すと遷移が消えたように見える。衝突したらエッジに沿って中央側へ滑らせて空きを
    // 探し、それでも埋まっている時は「他ラベルだけ避けた位置 (線は諦める)」→「他の線だけ」→「ノード
    // だけ」の順に妥協する。線は途切れても前後から辿れるが、ラベルにピルが乗ると文字ごと消えるため、
    // 既置ラベル回避を線回避より優先する。
    // self-loop (点 1 個) は弧の頂点にそのまま置く。端はキャンバス内にクランプしてクリップを防ぐ。
    var box: LabelBox? = null
    var bx = 0f
    var by = 0f
    var nodeClearBox: LabelBox? = null
    var ncx = 0f
    var ncy = 0f
    var lineClearBox: LabelBox? = null
    var lcx = 0f
    var lcy = 0f
    var placedClearBox: LabelBox? = null
    var pcx = 0f
    var pcy = 0f
    // 候補は距離を段階的に増やしながら探す: まず線上 (ラベル中心を線が通る)、次に線の脇 (1 ラベル分)、
    // 最後にノード半高ぶん離れた脇。後段は「ラベル幅 > ノード間ギャップ」で線上のどこにもノードと
    // 被らず置けない時の退避先 (auth TB の LoggedIn—Authenticating のような近接ペア)。
    val sideDistances = floatArrayOf(
        0f,
        h / 2 + padY + 6f.dp.toPx(),
        24f.dp.toPx() + h / 2 + padY + 2f.dp.toPx(),
        // 混雑時の最終段: 近距離が全部 (ノード / 見出し / 既置ラベル) に塞がれた時にさらに外へ逃げる
        // (self-loop が同一面に複数集まるケースで、無理に重ねず 1 段外へ積むための距離)。
        48f.dp.toPx() + h / 2 + padY + 2f.dp.toPx(),
    ) + if (!onLineAllowed) {
        // loop ラベル専用の遠距離段: 箱上辺と弧の間にピルが物理的に入らない多行スタックは、
        // 見出しの向こう側 = 箱の外まで逃がす。帰属はリーダー線が示す。
        // 弧上 fallback でノード枠を消すくらいなら遠くでも完全に読める方が良い。
        floatArrayOf(
            76f.dp.toPx() + h / 2 + padY + 2f.dp.toPx(),
            108f.dp.toPx() + h / 2 + padY + 2f.dp.toPx(),
        )
    } else {
        floatArrayOf()
    }
    outer@ for (dist in sideDistances) {
        // loop ラベルは「弧上」(dist=0) を使わない: ピルが自分の弧を隠して環形状が消えるため。
        if (!onLineAllowed && dist == 0f) continue
        for (f in LABEL_FRACTIONS) {
            // 開始点近くの候補は最低 12dp ぶんの下限 fraction まで押し出す (`ide-2.md` #7)。
            val ff = f.coerceAtLeast(minStartFrac)
            val on = pointAt(pts, ff)
            val sides = if (dist == 0f) {
                listOf(Offset.Zero)
            } else if (pts.size < 2) {
                // self-loop (点 1 個) は線方向が定義できないので上下左右の 4 方向へ退避を試す。
                listOf(Offset(0f, dist), Offset(0f, -dist), Offset(dist, 0f), Offset(-dist, 0f))
            } else {
                val dir = directionAt(pts, ff)
                val n = Offset(-dir.y, dir.x) * dist
                // self-loop (outwardDir 指定) は弧の外側を先に試す: 内側に不透明ピルを置くと弧の脚を
                // 覆って環が扁平に見えるため (TOP 面 save (stay) / next (stay) 潰れの直接原因)。
                if (outwardDir != null && (n.x * outwardDir.x + n.y * outwardDir.y) < 0f) {
                    listOf(-n, n)
                } else {
                    listOf(n, -n)
                }
            }
            for (side in sides) {
                val cx = clampToCanvas(on.x + side.x, w / 2 + padX, canvasPx.width)
                val cy = clampToCanvas(on.y + side.y, h / 2 + padY, canvasPx.height)
                val candidate = labelBox(cx, cy, w, h, padX, padY)
                val nodeClear = nodeBoxes.none { it.intersects(candidate) }
                val otherLineClear = allPolylines.none { line ->
                    line !== pts && line !== ownLine &&
                        (0 until line.size - 1).any { candidate.intersectsSegment(line[it], line[it + 1]) }
                }
                val placedClear = placed.none { it.intersects(candidate) }
                if (nodeClear && nodeClearBox == null) {
                    nodeClearBox = candidate; ncx = cx; ncy = cy
                }
                if (nodeClear && otherLineClear && lineClearBox == null) {
                    lineClearBox = candidate; lcx = cx; lcy = cy
                }
                if (nodeClear && placedClear && placedClearBox == null) {
                    placedClearBox = candidate; pcx = cx; pcy = cy
                }
                if (nodeClear && otherLineClear && placedClear) {
                    box = candidate; bx = cx; by = cy; break@outer
                }
            }
        }
    }
    if (box == null && placedClearBox != null) {
        box = placedClearBox; bx = pcx; by = pcy
    }
    if (box == null && lineClearBox != null) {
        box = lineClearBox; bx = lcx; by = lcy
    }
    if (box == null && nodeClearBox != null) {
        box = nodeClearBox; bx = ncx; by = ncy
    }
    if (box == null) {
        val on = pointAt(pts, LABEL_FRACTIONS[0].coerceAtLeast(minStartFrac))
        bx = clampToCanvas(on.x, w / 2 + padX, canvasPx.width)
        by = clampToCanvas(on.y, h / 2 + padY, canvasPx.height)
        box = labelBox(bx, by, w, h, padX, padY)
    }
    placed += box
    // 自線から離れた位置に退避したラベルは、細いリーダー線で帰属を明示する (同文ラベルが複数
    // 並んだ時も「どの線のラベルか」が引き出し線で追える)。近接配置では描かない。
    // 右/左面 stay (forceLeader) は距離に関わらず必ずリーダー線を出す (`ide-2.md` #2)。
    val nearest = nearestPointOnPolyline(ownLine ?: pts, Offset(bx, by))
    val leadDist = hypot(nearest.x - bx, nearest.y - by)
    val leaderThreshold = if (forceLeader) 0f else 30f.dp.toPx()
    if (leadDist > leaderThreshold) {
        val dirX = (nearest.x - bx) / leadDist
        val dirY = (nearest.y - by) / leadDist
        // ピルの縁からピル外へ出た地点を起点にする (中心からだと文字に被る)。矩形境界との交点は
        // 各軸の半径をその方向成分で割った小さい方 (max だと斜め方向で内側に留まり数 px の断片になる)。
        val tx = if (abs(dirX) > 1e-3f) (box.w / 2 + 2f) / abs(dirX) else Float.MAX_VALUE
        val ty = if (abs(dirY) > 1e-3f) (box.h / 2 + 2f) / abs(dirY) else Float.MAX_VALUE
        val startT = minOf(tx, ty).coerceAtMost(leadDist)
        // 起点から線までの残りが短すぎるゴミ状の断片は描かない。
        if (leadDist - startT > 6f.dp.toPx()) {
            drawLine(
                color = textColor.copy(alpha = 0.45f),
                start = Offset(bx + dirX * startT, by + dirY * startT),
                end = nearest,
                strokeWidth = 1f.dp.toPx(),
            )
        }
    }
    drawRoundRect(
        color = colors.background,
        topLeft = Offset(box.left, box.top),
        size = Size(box.w, box.h),
        cornerRadius = corner(4f),
    )
    // 選択された Transition / stay のラベルは accent border で囲う (`ide-3.md` tier 1)。
    if (emphasized) {
        drawRoundRect(
            color = colors.accent,
            topLeft = Offset(box.left, box.top),
            size = Size(box.w, box.h),
            cornerRadius = corner(4f),
            style = Stroke(width = 1.5f.dp.toPx()),
        )
    }
    drawText(laid, topLeft = Offset(bx - w / 2, by - h / 2))
    // 確定したラベル矩形を当たり判定に登録し、ラベルクリックでその遷移を選択できるようにする (`ide-3.md`)。
    if (selection != null) sink?.labelBoxes?.add(selection to PxBox(box.left, box.top, box.w, box.h))
}

/** Total length of poly-line [pts] (0 for a single point). */
private fun polylineLength(pts: List<Offset>): Float {
    if (pts.size < 2) return 0f
    var total = 0f
    for (i in 0 until pts.size - 1) total += hypot(pts[i + 1].x - pts[i].x, pts[i + 1].y - pts[i].y)
    return total
}

/** The point on poly-line [pts] closest to [p] (segment-wise projection). */
private fun nearestPointOnPolyline(pts: List<Offset>, p: Offset): Offset {
    if (pts.size < 2) return pts.firstOrNull() ?: p
    var best = pts[0]
    var bestD = Float.MAX_VALUE
    for (i in 0 until pts.size - 1) {
        val a = pts[i]
        val b = pts[i + 1]
        val abx = b.x - a.x
        val aby = b.y - a.y
        val len2 = abx * abx + aby * aby
        val t = if (len2 < 1e-6f) 0f else (((p.x - a.x) * abx + (p.y - a.y) * aby) / len2).coerceIn(0f, 1f)
        val qx = a.x + abx * t
        val qy = a.y + aby * t
        val d = hypot(p.x - qx, p.y - qy)
        if (d < bestD) {
            bestD = d
            best = Offset(qx, qy)
        }
    }
    return best
}

/** The unit direction of the poly-line at arc-length fraction [f] (for the side-of-line label offset). */
private fun directionAt(pts: List<Offset>, f: Float): Offset {
    if (pts.size < 2) return Offset(1f, 0f)
    val a = pointAt(pts, (f - 0.01f).coerceAtLeast(0f))
    val b = pointAt(pts, (f + 0.01f).coerceAtMost(1f))
    val len = hypot(b.x - a.x, b.y - a.y)
    return if (len > 0.0001f) Offset((b.x - a.x) / len, (b.y - a.y) / len) else Offset(1f, 0f)
}

/** The point at arc-length fraction [f] (0..1) along the poly-line [pts]; a single point returns itself. */
private fun pointAt(pts: List<Offset>, f: Float): Offset {
    if (pts.size < 2) return pts[0]
    var total = 0f
    val segs = FloatArray(pts.size - 1)
    for (i in 0 until pts.size - 1) {
        segs[i] = hypot(pts[i + 1].x - pts[i].x, pts[i + 1].y - pts[i].y)
        total += segs[i]
    }
    if (total <= 0f) return pts[0]
    var target = total * f
    for (i in segs.indices) {
        if (target <= segs[i] || i == segs.lastIndex) {
            val t = if (segs[i] > 0f) (target / segs[i]).coerceIn(0f, 1f) else 0f
            return lerp(pts[i], pts[i + 1], t)
        }
        target -= segs[i]
    }
    return pts.last()
}

private fun labelBox(cx: Float, cy: Float, w: Float, h: Float, padX: Float, padY: Float): LabelBox =
    LabelBox(left = cx - w / 2 - padX, top = cy - h / 2 - padY, w = w + padX * 2, h = h + padY * 2)

/** True when segment [a]->[b] passes through this box (Liang-Barsky clip; touching the border counts). */
private fun LabelBox.intersectsSegment(a: Offset, b: Offset): Boolean {
    var t0 = 0f
    var t1 = 1f
    val dx = b.x - a.x
    val dy = b.y - a.y
    val p = floatArrayOf(-dx, dx, -dy, dy)
    val q = floatArrayOf(a.x - left, left + w - a.x, a.y - top, top + h - a.y)
    for (i in 0..3) {
        if (p[i] == 0f) {
            if (q[i] < 0f) return false
        } else {
            val r = q[i] / p[i]
            if (p[i] < 0f) {
                if (r > t1) return false
                if (r > t0) t0 = r
            } else {
                if (r < t0) return false
                if (r < t1) t1 = r
            }
        }
    }
    return t1 > t0
}

// ラベルを置くエッジ上の位置 (source からの比率)。矢印の出始め寄りを優先し、詰まったら中央側へ滑らせる。
private val LABEL_FRACTIONS = floatArrayOf(0.2f, 0.28f, 0.14f, 0.36f, 0.44f, 0.52f)

// ラベル中心と線の開始点 (source ポート) の最低間隔 (dp)。これ未満に寄る候補は下限 fraction で押し出す。
private const val LABEL_MIN_START_GAP = 12f

// エッジラベルの最大幅 (dp)。これを超えるテキストは折り返す (表示領域を決めて改行する方針)。
private const val LABEL_MAX_WIDTH_DP = 220

/** Keeps a label center within [[margin], [extent] - [margin]] so its box stays on-canvas. */
private fun clampToCanvas(value: Float, margin: Float, extent: Float): Float {
    val lo = margin
    val hi = extent - margin
    return if (lo >= hi) value else value.coerceIn(lo, hi)
}

// ---- text / geometry helpers ----

private fun DrawScope.drawCentered(tm: TextMeasurer, text: String, r: PxRect, color: Color, size: TextUnit, weight: FontWeight) {
    // 長い state 名は枠からはみ出すので、枠内幅で折り返し (camelCase 境界でも折れる)、それでも
    // 高さ/幅に収まらなければフォントを段階的に縮める (autosize)。
    val padX = 8f.dp.toPx()
    val padY = 4f.dp.toPx()
    val maxW = (r.size.width - padX * 2).coerceAtLeast(1f)
    val maxH = (r.size.height - padY * 2).coerceAtLeast(1f)
    val laid = fitText(tm, softBreakable(text), color, size, weight, maxW, maxH)
    val x = r.topLeft.x + (r.size.width - laid.size.width) / 2
    val y = r.topLeft.y + (r.size.height - laid.size.height) / 2
    drawText(laid, topLeft = Offset(x, y))
}

/**
 * Lays [text] to fit inside [maxW] x [maxH]: wraps at [maxW] at the base [size]; if the wrapped block
 * is still too tall (or an unbreakable run overflows the width), steps the font down toward
 * [MIN_LABEL_SP] until it fits (autosize). Always returns a result — at the floor it may overflow
 * slightly rather than disappear.
 */
private fun DrawScope.fitText(
    tm: TextMeasurer,
    text: String,
    color: Color,
    size: TextUnit,
    weight: FontWeight,
    maxW: Float,
    maxH: Float,
): TextLayoutResult {
    val widthPx = maxW.toInt().coerceAtLeast(1)
    var sp = size.value
    var last: TextLayoutResult? = null
    while (sp >= MIN_LABEL_SP) {
        val laid = tm.measure(
            text = text,
            // 折り返し時も各行を中央揃えにする (state 名の複数行表示が左に寄らないように)。
            style = TextStyle(color = color, fontSize = sp.sp, fontWeight = weight, textAlign = TextAlign.Center),
            overflow = TextOverflow.Clip,
            softWrap = true,
            constraints = Constraints(maxWidth = widthPx),
        )
        last = laid
        if (laid.size.height <= maxH && laid.size.width <= maxW + 0.5f) return laid
        sp -= 1f
    }
    return last!!
}

// camelCase の内部大文字前に zero-width space を入れ、長い識別子でも枠内で折り返せるようにする。
// コードポイントから生成する (ソースに不可視文字/エスケープを埋めない)。
private val CAMEL_BOUNDARY = Regex("(?<=[a-z0-9])(?=[A-Z])")
private val ZERO_WIDTH_SPACE = 0x200B.toChar().toString()

private fun softBreakable(text: String): String = text.replace(CAMEL_BOUNDARY, ZERO_WIDTH_SPACE)

// autosize の下限フォントサイズ (sp)。これ以上は縮めない (可読性優先、僅かなはみ出しは許容)。
private const val MIN_LABEL_SP = 8f

private fun DrawScope.corner(radiusDp: Float) =
    androidx.compose.ui.geometry.CornerRadius(radiusDp.dp.toPx(), radiusDp.dp.toPx())

/** A rectangle already converted to pixels, with cached center. */
private class PxRect(val topLeft: Offset, val size: Size) {
    val center: Offset get() = Offset(topLeft.x + size.width / 2, topLeft.y + size.height / 2)
    val right: Float get() = topLeft.x + size.width
    val bottom: Float get() = topLeft.y + size.height
}

private fun DrawScope.px(r: Rect): PxRect =
    PxRect(Offset(r.x.dp.toPx(), r.y.dp.toPx()), Size(r.width.dp.toPx(), r.height.dp.toPx()))

private fun lerp(a: Offset, b: Offset, t: Float): Offset = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
