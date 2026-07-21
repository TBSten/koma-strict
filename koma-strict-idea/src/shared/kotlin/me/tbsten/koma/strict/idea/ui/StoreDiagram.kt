package me.tbsten.koma.strict.idea.ui

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import me.tbsten.koma.strict.idea.ir.DiagramGraph
import me.tbsten.koma.strict.idea.layout.EdgeRouting
import me.tbsten.koma.strict.idea.layout.GraphLayout
import me.tbsten.koma.strict.idea.model.SourceAnchor
import me.tbsten.koma.strict.idea.ui.component.OversizeBanner

/**
 * The state-diagram canvas: draws a lowered [DiagramGraph] at the coordinates from [GraphLayout] and
 * hit-tests taps back to the tapped node's [SourceAnchor] for click-to-declaration (`ide.md` v1) —
 * a concrete leaf, an any-state pseudo node, or a composite box's label strip (see [hitSource]). The
 * canvas is sized to the layout's own extent and lives inside both scroll axes, so a diagram larger
 * than the tool window scrolls rather than clipping. All rendering is delegated to [drawDiagram].
 *
 * [zoom] is the *requested* zoom (buttons + pinch). The Compose/Skiko canvas can't grow past
 * [MAX_CANVAS_EXTENT], so a figure whose `layout × zoom` would exceed it is **auto-fit** ([fitDiagram]):
 * the render zoom is lowered until the whole figure fits, so no node/edge is ever drawn past the
 * scrollable area and silently clipped (`ide-review.md` P1-08). When that happens an [OversizeBanner]
 * tells the user the figure was scaled to fit rather than dropping part of it. [onZoomBy] reports a
 * multiplicative zoom delta from a trackpad/touch pinch or a `Ctrl`/`Cmd` + wheel, which the caller
 * folds into [zoom] (clamped).
 *
 * A single tap also drives **focus** (`ide-2.md`): tapping a state frame or a transition arrow selects
 * it and dims every element outside its focus set (see [focusFrom] / [hitElement]); tapping an empty
 * area clears the focus. Focus and click-to-declaration coexist — a tap sets focus *and* still fires
 * [onNavigate] when it lands on something navigable. [initialSelection] seeds the focus for a
 * non-interactive render (headless preview); interactive callers leave it null.
 */
@Composable
fun StoreDiagram(
    graph: DiagramGraph,
    layout: GraphLayout,
    colors: DiagramColors,
    modifier: Modifier = Modifier,
    onNavigate: (SourceAnchor) -> Unit = {},
    zoom: Float = 1f,
    onZoomBy: (Float) -> Unit = {},
    initialSelection: DiagramSelection? = null,
) {
    val tm = rememberTextMeasurer()
    // pointerInput は key(graph, layout, renderZoom) 変化時しか再起動しないので、コールバックは
    // rememberUpdatedState 経由で常に最新を呼ぶ (古いクロージャを掴む Compose フットガンを防ぐ)。
    val latestOnNavigate by rememberUpdatedState(onNavigate)
    val latestOnZoomBy by rememberUpdatedState(onZoomBy)
    // 選択状態 (フォーカス)。tap で更新する。graph/layout が変わったら選択をリセットする。
    var selection by remember(graph, layout) { mutableStateOf(initialSelection) }
    // エッジ当たり判定用のルート (dp)。描画側 drawDiagram も同じ routeAll を使うので判定と描画が一致する
    // (routeAll は純関数で決定的なので別計算でも同一結果)。
    val routes = remember(graph, layout) { EdgeRouting.routeAll(graph, layout) }
    // フォーカス集合。選択が無ければ null = 全要素を通常描画 (既存の見た目)。
    val focus = remember(graph, selection) { selection?.let { graph.focusFrom(it) } }
    // cap を超える図は auto-fit で全体を canvas 内へ収める。以降は renderZoom (= 実描画倍率) で統一し、
    // 描画・hit-test・canvas サイズが一致するようにする (無言 clip を禁止)。
    val fit = fitDiagram(layout.canvasSize.width, layout.canvasSize.height, zoom)
    val renderZoom = fit.renderZoom
    // 異常なモデルでも canvas 生成で落ちない/固まらないよう、寸法を有限・正の範囲にクランプする。
    val canvasW = safeExtent(layout.canvasSize.width * renderZoom)
    val canvasH = safeExtent(layout.canvasSize.height * renderZoom)
    Column(modifier = modifier.fillMaxSize()) {
        // auto-fit で要求 zoom より縮めたら、縮小理由と「情報は欠落していない」ことを明示する。
        if (fit.capped) OversizeBanner(requestedZoom = zoom, renderZoom = renderZoom, colors = colors)
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
                    // ピンチ (トラックパッド/タッチ) で拡大縮小。zoomChange は倍率。pan/tap は横取りしない
                    // よう zoomChange==1 の move では何もしない (単指ドラッグはスクロールに委ねる)。
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoomChange, _ ->
                            if (zoomChange != 1f) latestOnZoomBy(zoomChange)
                        }
                    }
                    // Ctrl / Cmd + ホイールでも拡大縮小 (デスクトップ標準・ピンチが届かない環境の保険)。
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.type != PointerEventType.Scroll) continue
                                val mod = event.keyboardModifiers
                                if (!mod.isCtrlPressed && !mod.isMetaPressed) continue
                                val dy = event.changes.fold(0f) { acc, c -> acc + c.scrollDelta.y }
                                if (dy != 0f) {
                                    // 上スクロール (dy<0) で拡大。1 ノッチ ≈ 10%。
                                    latestOnZoomBy(1f - dy * 0.1f)
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }
                    }
                    .pointerInput(graph, layout, renderZoom) {
                        detectTapGestures { offset ->
                            // px -> renderZoom を戻して -> レイアウト単位 (dp)。auto-fit 時も実描画倍率で
                            // 戻すので当たり判定が図とずれない。
                            val ux = (offset.x / renderZoom).toDp().value.toDouble()
                            val uy = (offset.y / renderZoom).toDp().value.toDouble()
                            // フォーカス: ノード/エッジにヒットしたら選択、何も無い所なら解除。composite ラベル帯
                            // など「focus 対象ではないが navigable」な場所は選択を変えない (誤解除を防ぐ)。
                            val element = graph.hitElement(layout, routes, ux, uy)
                            val source = graph.hitSource(layout, ux, uy)
                            selection = when {
                                element != null -> element
                                source == null -> null
                                else -> selection
                            }
                            // ジャンプ (click-to-declaration) はフォーカスと併存させる (既存挙動を壊さない)。
                            source?.let { latestOnNavigate(it) }
                        }
                    },
            ) {
                // DrawScope 全体を renderZoom 倍 (原点固定) して描く。テキストも一緒にスケールする。
                scale(renderZoom, pivot = Offset.Zero) {
                    drawDiagram(graph, layout, colors, tm, focus)
                }
            }
        }
    }
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
