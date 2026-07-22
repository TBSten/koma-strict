package me.tbsten.koma.strict.idea.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import me.tbsten.koma.strict.idea.ir.GraphLowering
import me.tbsten.koma.strict.idea.layout.LayeredLayout
import me.tbsten.koma.strict.idea.layout.LayoutConfig
import me.tbsten.koma.strict.idea.model.SourceAnchor
import me.tbsten.koma.strict.idea.model.StoreDiagramModel
import me.tbsten.koma.strict.idea.ui.component.DegradedBanner
import me.tbsten.koma.strict.idea.ui.component.DiagramZoomControls
import me.tbsten.koma.strict.idea.ui.component.Header
import me.tbsten.koma.strict.idea.ui.component.IndexingGuidance
import me.tbsten.koma.strict.idea.ui.component.RenderErrorGuidance
import me.tbsten.koma.strict.idea.ui.component.SetupGuidance
import me.tbsten.koma.strict.idea.ui.component.UnresolvedBanner
import org.jetbrains.jewel.foundation.theme.JewelTheme
import kotlin.coroutines.cancellation.CancellationException

/**
 * Wider layer / sibling gaps than the layout defaults so the Mealy edge labels (`trigger / Event`)
 * have room in the corridor between columns instead of piling up. Tuned against the headless PNGs.
 */
private val UiLayoutConfig = LayoutConfig(layerGap = 208.0, siblingGap = 60.0)

/**
 * Shared tool-window content for the Koma Strict plugin: the live state diagram (`ide.md`).
 * Framework-neutral — only Compose Foundation and Jewel APIs — so the identical source compiles
 * against the bundled Jewel in the IDE plugin and the standalone Jewel Int UI in the headless
 * `renderComposeScene` preview. A Jewel theme must already be installed by the caller
 * (`addComposeTab` / `IntUiTheme`).
 *
 * All interactive state lives in [KomaStrictToolWindowContentState]; this composable only wires that
 * state to the UI. When [stores] is empty it shows setup guidance; otherwise it renders a [Header]
 * (store selector + LR/TB toggle + Copy image + Reload), the diagram with floating zoom controls at
 * its bottom-right ([DiagramZoomControls]), and — for a degraded model — a [DegradedBanner] while
 * still showing whatever names resolved.
 */
@Composable
fun KomaStrictToolWindowContent(
    stores: List<StoreDiagramModel> = emptyList(),
    onNavigate: (SourceAnchor) -> Unit = {},
    indexing: Boolean = false,
    onReload: () -> Unit = {},
) {
    val colors = rememberDiagramColors()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JewelTheme.globalColors.panelBackground),
    ) {
        if (indexing) {
            IndexingGuidance(colors)
            return@Column
        }
        if (stores.isEmpty()) {
            SetupGuidance(colors)
            return@Column
        }

        val state = rememberKomaStrictToolWindowContentState(stores)
        val model = state.model
        // 図の構築 (lowering + layout) を保護する。半端/異常なモデルで例外が飛んでも composition を
        // 巻き添えにして固まらせない。失敗時は Diagram にだけエラーを出し、ヘッダ (Reload / 向き切替) は
        // 生かして復帰の導線を残す。ただし runCatching (= Throwable 全捕捉) は使わない: 協調キャンセル
        // (CancellationException) は握りつぶさず再送出し、VirtualMachineError (OOM/StackOverflow) 等の
        // Error は捕えず伝播させる (壊れた JVM 状態で UI を続行しない)。回復可能な Exception だけ Render
        // エラーへ縮退する。
        val prepared = remember(model, state.direction.direction) {
            try {
                val g = GraphLowering.lower(model)
                Result.success(g to LayeredLayout.layout(g, state.direction.direction, UiLayoutConfig))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        // "Copy image": 現在の図をオフスクリーン描画してクリップボードへ。図が描けている時だけ出す。
        // density / layoutDirection / TextMeasurer は composition から取り、描画は画面と同一パス (drawDiagram) を使う。
        val density = LocalDensity.current
        val composeLayoutDirection = LocalLayoutDirection.current
        val copyTextMeasurer = rememberTextMeasurer()
        val onCopyImage: (() -> Boolean)? = prepared.getOrNull()?.let { (graph, graphLayout) ->
            // 選択があれば focus を焼き込む (フォーカス状態のままコピー)。無ければ従来通り全体を通常描画。
            val copyFocus = state.focus.selection.takeIf { it.isNotEmpty() }?.let { sel -> graph.focusFrom(sel) }
            val action: () -> Boolean = {
                copyDiagramImageToClipboard(
                    graph = graph,
                    layout = graphLayout,
                    colors = colors,
                    density = density,
                    layoutDirection = composeLayoutDirection,
                    textMeasurer = copyTextMeasurer,
                    focus = copyFocus,
                )
            }
            action
        }

        Header(
            stores = stores,
            selected = state.store.selectedIndex,
            onSelect = state::selectStore,
            direction = state.direction.direction,
            onToggleDirection = state.direction::toggle,
            onReload = onReload,
            colors = colors,
            onCopyImage = onCopyImage,
        )
        Divider(colors)
        // 全 degrade (名前のみ) と partial (一部の参照が未解決) を区別して明示する。
        if (model.degraded) DegradedBanner(model.error, colors)
        else if (model.unresolved) UnresolvedBanner(colors)

        Box(Modifier.fillMaxSize()) {
            prepared.fold(
                onSuccess = { (graph, layout) ->
                    StoreDiagram(
                        graph = graph,
                        layout = layout,
                        colors = colors,
                        onNavigate = onNavigate,
                        zoomState = state.zoom,
                        selectionState = state.focus,
                    )
                },
                onFailure = { RenderErrorGuidance(it, colors) },
            )
            // 図の拡大縮小は図が描けている時だけ、canvas の右下に浮かせる (map / draw.io 風)。
            if (prepared.isSuccess) {
                DiagramZoomControls(
                    zoomState = state.zoom,
                    colors = colors,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                )
            }
        }
    }
}

/** A 1dp horizontal rule between the header and the content. */
@Composable
private fun Divider(colors: DiagramColors) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.compositeBorder))
}
