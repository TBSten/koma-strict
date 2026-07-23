package me.tbsten.koma.strict.idea.ui.diagram

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import me.tbsten.koma.strict.idea.ir.DiagramGraph
import me.tbsten.koma.strict.idea.ir.GraphEdge
import me.tbsten.koma.strict.idea.layout.edge.EdgeRouting
import me.tbsten.koma.strict.idea.layout.GraphLayout
import me.tbsten.koma.strict.idea.layout.Point
import me.tbsten.koma.strict.idea.model.SourceAnchor
import me.tbsten.koma.strict.idea.ui.component.OversizeBanner
import kotlin.math.hypot

/**
 * The state-diagram canvas: draws a lowered [DiagramGraph] at the coordinates from [GraphLayout] and
 * hit-tests taps back to the tapped node's [SourceAnchor] for click-to-declaration (`ide.md` v1) —
 * a concrete leaf, an any-state pseudo node, or a composite box's label strip (see [hitSource]). The
 * canvas is sized to the layout's own extent and lives inside both scroll axes, so a diagram larger
 * than the tool window scrolls rather than clipping. All rendering is delegated to [drawDiagram].
 *
 * [zoomState] is the *requested* zoom (buttons + pinch). The Compose/Skiko canvas can't grow past
 * [MAX_CANVAS_EXTENT], so a figure whose `layout × zoom` would exceed it is **auto-fit** ([fitDiagram]):
 * the render zoom is lowered until the whole figure fits, so no node/edge is ever drawn past the
 * scrollable area and silently clipped (`ide-review.md` P1-08). When that happens an [OversizeBanner]
 * tells the user the figure was scaled to fit. Pinch / `Ctrl`+wheel nudge [zoomState] via [pinchZoom] /
 * [ctrlWheelZoom].
 *
 * A single tap also drives **focus** (`ide-2.md` / `ide-3.md`) via [diagramTapSelection]: tapping a
 * state frame, a transition arrow, a nest-state box, or a stay arc selects it (Shift toggles a
 * multi-selection; a plain tap on empty space clears it) and dims everything outside its focus set.
 * [selectionState] is hoisted (the tool window owns it so "Copy image" can render the focused figure);
 * focus and click-to-declaration coexist — a tap updates [selectionState] *and* still fires [onNavigate]
 * on a navigable hit.
 */
@Composable
fun StoreDiagram(
    graph: DiagramGraph,
    layout: GraphLayout,
    colors: DiagramColors,
    modifier: Modifier = Modifier,
    onNavigate: (SourceAnchor) -> Unit = {},
    zoomState: ZoomState = rememberZoomState(),
    selectionState: SelectionState = rememberSelectionState(),
    recording: Boolean = false,
    onRecordTap: (DiagramSelection) -> Unit = {},
) {
    val tm = rememberTextMeasurer()
    // エッジ当たり判定用のルート (dp)。描画側 drawDiagram も同じ routeAll を使うので判定と描画が一致する
    // (routeAll は純関数で決定的なので別計算でも同一結果)。tap 当たり判定にも渡す。
    val routes = remember(graph, layout) { EdgeRouting.routeAll(graph, layout) }
    // フォーカス集合。選択が空なら null = 全要素を通常描画 (既存の見た目)。
    val focus = remember(graph, selectionState.selection) {
        selectionState.selection.takeIf { it.isNotEmpty() }?.let { graph.focusFrom(it) }
    }
    // ラベル矩形・自己ループ/scope-stay 弧の描画時ジオメトリを毎フレーム受け取り、tap の当たり判定に使う。
    val sink = remember(graph, layout) { DiagramInteractionSink() }
    // cap を超える図は auto-fit で全体を canvas 内へ収める。以降は renderZoom (= 実描画倍率) で統一し、
    // 描画・hit-test・canvas サイズが一致するようにする (無言 clip を禁止)。
    val fit = fitDiagram(layout.canvasSize.width, layout.canvasSize.height, zoomState.zoom)
    val renderZoom = fit.renderZoom
    // 異常なモデルでも canvas 生成で落ちない/固まらないよう、寸法を有限・正の範囲にクランプする。
    val canvasW = safeExtent(layout.canvasSize.width * renderZoom)
    val canvasH = safeExtent(layout.canvasSize.height * renderZoom)
    // tap の当たり判定に要る入力を 1 つにまとめて引数を読みやすくする。
    val hitContext = DiagramHitContext(graph, layout, routes, sink, renderZoom)
    Column(modifier = modifier.fillMaxSize()) {
        // auto-fit で要求 zoom より縮めたら、縮小理由と「情報は欠落していない」ことを明示する。
        if (fit.capped) OversizeBanner(requestedZoom = zoomState.zoom, renderZoom = renderZoom, colors = colors)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
                .padding(8.dp),
        ) {
            Canvas(
                modifier = Modifier
                    // scroll 領域は renderZoom 後のサイズで確保する (常に cap 以内)。
                    .size(canvasW.dp, canvasH.dp)
                    .pinchZoom(zoomState)
                    .ctrlWheelZoom(zoomState)
                    .diagramTapSelection(hitContext, selectionState, onNavigate, recording, onRecordTap),
            ) {
                // DrawScope 全体を renderZoom 倍 (原点固定) して描く。テキストも一緒にスケールする。
                // sink には pre-scale px でラベル/弧が記録される (tap 側で offset/renderZoom と突き合わせる)。
                scale(renderZoom, pivot = Offset.Zero) {
                    drawDiagram(graph, layout, colors, tm, focus, sink)
                }
            }
        }
    }
}

/**
 * Trackpad / touch pinch → zoom via [ZoomState.nudge]. `pointerInput` keys on the stable [zoomState]
 * holder; a `zoomChange` of 1 (pure pan / tap) is ignored so single-finger drags stay with the scroll
 * containers.
 */
private fun Modifier.pinchZoom(zoomState: ZoomState): Modifier =
    pointerInput(zoomState) {
        detectTransformGestures { _, _, zoomChange, _ ->
            if (zoomChange != 1f) zoomState.nudge(zoomChange)
        }
    }

/**
 * `Ctrl` / `Cmd` + wheel → zoom (desktop standard + a fallback where pinch never arrives). One notch
 * ≈ 10 %; scrolling up (dy < 0) zooms in. The event is consumed so it does not also scroll the canvas.
 */
private fun Modifier.ctrlWheelZoom(zoomState: ZoomState): Modifier =
    pointerInput(zoomState) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type != PointerEventType.Scroll) continue
                val mod = event.keyboardModifiers
                if (!mod.isCtrlPressed && !mod.isMetaPressed) continue
                val dy = event.changes.fold(0f) { acc, c -> acc + c.scrollDelta.y }
                if (dy != 0f) {
                    zoomState.nudge(1f - dy * 0.1f)
                    event.changes.forEach { it.consume() }
                }
            }
        }
    }

/**
 * Tap → focus/selection (`ide-3.md`) + click-to-declaration. Resolves the tapped target via
 * [resolveTap], folds it into [selectionState] ([SelectionState.onTap]; Shift = toggle), and still
 * fires [onNavigate] on a navigable hit. `pointerInput` keys on the coordinate-relevant inputs;
 * [onNavigate] is tracked through [rememberUpdatedState] so the persistent tap lambda always calls the
 * latest.
 */
@Composable
private fun Modifier.diagramTapSelection(
    hit: DiagramHitContext,
    selectionState: SelectionState,
    onNavigate: (SourceAnchor) -> Unit,
    recording: Boolean,
    onRecordTap: (DiagramSelection) -> Unit,
): Modifier {
    val latestOnNavigate by rememberUpdatedState(onNavigate)
    // recording / onRecordTap は toggle で変わるので rememberUpdatedState 経由で最新を読む
    // (pointerInput は座標系入力だけで re-key し、モード変更で判定器を作り直さない)。
    val latestRecording by rememberUpdatedState(recording)
    val latestOnRecordTap by rememberUpdatedState(onRecordTap)
    // Shift 併用 (複数選択) 判定のため window の keyboard modifiers を tap 時に読む。
    val windowInfo = LocalWindowInfo.current
    return pointerInput(hit.graph, hit.layout, hit.renderZoom) {
        detectTapGestures { offset ->
            val tap = resolveTap(hit, offset)
            if (latestRecording) {
                // 記録中は選択の確定を content 側 (cursor から記録可能か判定) に委ねる。矢印/stay だけ渡し、
                // 記録できない (cursor から出ない) クリックは選択も記録もしない = 非選択。宣言ジャンプも抑止。
                val sel = tap.selection
                if (sel is DiagramSelection.Edge || sel is DiagramSelection.Stay) latestOnRecordTap(sel)
            } else {
                selectionState.onTap(
                    hit = tap.selection,
                    shift = windowInfo.keyboardModifiers.isShiftPressed,
                    clickedEmpty = tap.source == null,
                )
                // ジャンプ (click-to-declaration) はフォーカスと併存させる (既存挙動を壊さない)。
                tap.source?.let { latestOnNavigate(it) }
            }
        }
    }
}

/** Everything a tap needs to resolve a pointer offset back to a focus selection / navigation target. */
private class DiagramHitContext(
    val graph: DiagramGraph,
    val layout: GraphLayout,
    val routes: Map<GraphEdge, List<Point>>,
    val sink: DiagramInteractionSink,
    val renderZoom: Float,
)

/** What a tap resolved to: the element to focus ([selection]) and the declaration to jump to ([source]). */
private class TapHit(val selection: DiagramSelection?, val source: SourceAnchor?)

/**
 * Resolves a pointer [offset] (screen px) against [ctx]. Priority mirrors the draw order: a node
 * rectangle / composite label strip (pure [hitElement]) wins, then a label pill, then a self-loop /
 * scope-stay arc, then an edge line. Labels and arcs are positioned only while drawing, so they come
 * from the draw-time [DiagramInteractionSink] and are matched in pre-scale px (`offset / renderZoom`),
 * the same space [drawDiagram] records them in.
 *
 * [TapHit.source] is the click-to-declaration target: a node / composite box resolves to its state
 * declaration via [hitSource]; a transition (arrow / label / stay arc) has no declaration of its own,
 * so it navigates to the trigger's `@On…` annotation carried on the selected edge / stay arc
 * ([DiagramSelection.Edge] / [DiagramSelection.Stay], `ide-4.md`).
 */
private fun PointerInputScope.resolveTap(ctx: DiagramHitContext, offset: Offset): TapHit {
    // px -> renderZoom を戻して -> レイアウト単位 (dp)。auto-fit 時も実描画倍率で戻すので図とずれない。
    val ux = (offset.x / ctx.renderZoom).toDp().value.toDouble()
    val uy = (offset.y / ctx.renderZoom).toDp().value.toDouble()
    // sink (ラベル矩形 / 弧) は drawDiagram の pre-scale px = offset/renderZoom と同空間。
    val pxx = offset.x / ctx.renderZoom
    val pxy = offset.y / ctx.renderZoom
    val tolPx = EDGE_HIT_TOLERANCE.toFloat().dp.toPx()
    // 優先順位: node 矩形 / composite ラベル帯 (純 hitElement) > ラベル矩形 > 弧 > edge 線。
    val el = ctx.graph.hitElement(ctx.layout, ctx.routes, ux, uy)
    val selection: DiagramSelection? = when {
        el is DiagramSelection.Node || el is DiagramSelection.Composite -> el
        else -> ctx.sink.labelBoxes.firstOrNull { it.second.contains(pxx, pxy) }?.first
            ?: ctx.sink.arcs.firstOrNull { distanceToPolyline(it.second, pxx, pxy) <= tolPx }?.first
            ?: el
    }
    // node/composite は hitSource が宣言を返す。transition (edge/stay) は宣言を持たないので、
    // 選択した要素が運ぶトリガ注釈のアンカーへ飛ばす。
    val source = ctx.graph.hitSource(ctx.layout, ux, uy)
        ?: (selection as? DiagramSelection.Edge)?.edge?.source
        ?: (selection as? DiagramSelection.Stay)?.stay?.source
    return TapHit(selection, source)
}

/**
 * The zoom actually used to draw the figure, and whether it was reduced from the requested zoom.
 * [capped] is what drives the [OversizeBanner] — `true` means the figure was auto-fit smaller than
 * asked so the whole thing stays on-canvas.
 */
internal data class DiagramFit(val renderZoom: Float, val capped: Boolean)

/**
 * Auto-fit logic for the canvas extent cap (`ide-review.md` P1-08), pure so it can be asserted without
 * a live canvas. Given the layout's own extent ([layoutWidth] x [layoutHeight], density-independent)
 * and the [requestedZoom], returns the zoom to actually render at: the requested zoom for a normal
 * figure, or — when `layout × requestedZoom` would blow past [maxExtent] on either axis — the largest
 * zoom that still fits both axes inside the cap, flagged [DiagramFit.capped]. Because the returned zoom
 * keeps `layout × zoom <= maxExtent` on both axes, the canvas never has to clip content off the
 * scrollable area: every node and transition stays reachable at every zoom (50 % / 100 % / 250 %).
 * Degenerate inputs (`NaN`, zero, negative) are folded to safe positives so the caller never divides by
 * zero or sizes a canvas from a bad model.
 */
internal fun fitDiagram(
    layoutWidth: Double,
    layoutHeight: Double,
    requestedZoom: Float,
    maxExtent: Double = MAX_CANVAS_EXTENT,
): DiagramFit {
    val w = if (layoutWidth.isFinite() && layoutWidth > 0.0) layoutWidth else 1.0
    val h = if (layoutHeight.isFinite() && layoutHeight > 0.0) layoutHeight else 1.0
    val req = if (requestedZoom.isFinite() && requestedZoom > 0f) requestedZoom.toDouble() else 1.0
    // 両軸が cap 内に収まる最大倍率 (fit ceiling)。req がそれ以下なら通常図なのでそのまま。
    val ceiling = minOf(maxExtent / w, maxExtent / h)
    val render = minOf(req, ceiling)
    // ceiling を要求が上回った時だけ縮小 = capped。微小な FP 差でバナーが出ないよう eps を挟む。
    val capped = req > ceiling + FIT_EPSILON
    return DiagramFit(renderZoom = render.toFloat(), capped = capped)
}

// ceiling との比較に使う許容誤差 (割り算の丸めで capped が誤判定されないための緩衝)。
private const val FIT_EPSILON = 1e-4

/** Clamps a canvas extent to a finite, positive, sanely-bounded value so a bad model can't freeze layout. */
private fun safeExtent(value: Double): Double =
    if (value.isFinite()) value.coerceIn(1.0, MAX_CANVAS_EXTENT) else 1.0

// 何万ノードの病的モデルでも Skiko/レイアウトが破綻しない上限 (実用図は遥かに下)。
// auto-fit ([fitDiagram]) がこの上限を超えないよう倍率を下げるので、cap は clip ではなく縮小で効く。
internal const val MAX_CANVAS_EXTENT = 20_000.0

/** Minimum distance from ([x],[y]) to poly-line [pts] (px) — used to hit-test self-loop / scope-stay arcs. */
private fun distanceToPolyline(pts: List<Offset>, x: Float, y: Float): Float {
    if (pts.size < 2) return Float.MAX_VALUE
    var best = Float.MAX_VALUE
    for (i in 0 until pts.size - 1) best = minOf(best, distanceToSegment(pts[i], pts[i + 1], x, y))
    return best
}

/** Distance from ([x],[y]) to segment [a]->[b] (projection clamped to the segment ends). */
private fun distanceToSegment(a: Offset, b: Offset, x: Float, y: Float): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val len2 = dx * dx + dy * dy
    val t = if (len2 < 1e-6f) 0f else (((x - a.x) * dx + (y - a.y) * dy) / len2).coerceIn(0f, 1f)
    return hypot(x - (a.x + dx * t), y - (a.y + dy * t))
}
