package me.tbsten.koma.strict.idea.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import me.tbsten.koma.strict.idea.ir.GraphLowering
import me.tbsten.koma.strict.idea.layout.LayeredLayout
import me.tbsten.koma.strict.idea.layout.LayoutConfig
import me.tbsten.koma.strict.idea.layout.LayoutDirection
import me.tbsten.koma.strict.idea.model.SourceAnchor
import me.tbsten.koma.strict.idea.model.StoreDiagramModel
import me.tbsten.koma.strict.idea.ui.component.DegradedBanner
import me.tbsten.koma.strict.idea.ui.component.Header
import me.tbsten.koma.strict.idea.ui.component.IndexingGuidance
import me.tbsten.koma.strict.idea.ui.component.RenderErrorGuidance
import me.tbsten.koma.strict.idea.ui.component.SetupGuidance
import me.tbsten.koma.strict.idea.ui.component.TabBar
import me.tbsten.koma.strict.idea.ui.component.UnresolvedBanner
import org.jetbrains.jewel.foundation.theme.JewelTheme
import kotlin.coroutines.cancellation.CancellationException

/** The tool-window tabs: the figure and its ground-truth transition table (`ide.md` "図 + 遷移表"). */
enum class DiagramTab(val label: String) { Diagram("Diagram"), Transitions("Transitions") }

/**
 * Wider layer / sibling gaps than the layout defaults so the Mealy edge labels (`trigger / Event`)
 * have room in the corridor between columns instead of piling up. Tuned against the headless PNGs.
 */
private val UiLayoutConfig = LayoutConfig(layerGap = 208.0, siblingGap = 60.0)

/**
 * Shared tool-window content for the Koma Strict plugin: the live state diagram + its transition
 * table (`ide.md` "図 + 遷移表" pair). Framework-neutral — only Compose Foundation and Jewel APIs —
 * so the identical source compiles against the bundled Jewel in the IDE plugin and the standalone
 * Jewel Int UI in the headless `renderComposeScene` preview. A Jewel theme must already be installed
 * by the caller (`addComposeTab` / `IntUiTheme`).
 *
 * When [stores] is empty the window shows setup guidance; with one or more it renders a [Header]
 * (store selector + zoom + LR/TB toggle + Copy image + Reload), the Diagram / Transitions [TabBar], and — for a
 * degraded model — a [DegradedBanner] while still showing whatever names resolved. The composables
 * for each surface live in their own files (`Header`, `TabBar`, `Guidance`).
 */
@Composable
fun KomaStrictToolWindowContent(
    stores: List<StoreDiagramModel> = emptyList(),
    onNavigate: (SourceAnchor) -> Unit = {},
    initialTab: DiagramTab = DiagramTab.Diagram,
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

        // 選択は store の識別名リストで安定化する。再解析ごとに stores 参照は変わるが、
        // 中身 (store の集合) が同じなら選択を保持し、編集中に表示 store が先頭へ戻らないようにする。
        var selected by remember(stores.map { it.root.simpleName }) { mutableStateOf(0) }
        var direction by remember { mutableStateOf(LayoutDirection.LR) }
        var tab by remember { mutableStateOf(initialTab) }
        var zoom by remember { mutableStateOf(1f) }

        val model = stores[selected.coerceIn(0, stores.lastIndex)]
        // 図の構築 (lowering + layout) を保護する。半端/異常なモデルで例外が飛んでも composition を
        // 巻き添えにして固まらせない。失敗時は Diagram タブにだけエラーを出し、ヘッダ (Reload / 表切替)
        // は生かして復帰の導線を残す。ただし runCatching (= Throwable 全捕捉) は使わない: 協調キャンセル
        // (CancellationException) は握りつぶさず再送出し、VirtualMachineError (OOM/StackOverflow) 等の
        // Error は捕えず伝播させる (壊れた JVM 状態で UI を続行しない)。回復可能な Exception だけ Render
        // エラーへ縮退する。
        val prepared = remember(model, direction) {
            try {
                val g = GraphLowering.lower(model)
                Result.success(g to LayeredLayout.layout(g, direction, UiLayoutConfig))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        // "Copy image": 現在の図をオフスクリーン描画してクリップボードへ。図が描けていて Diagram タブを
        // 表示している時だけ出す (zoom コントロールと同じ条件。Transitions 表示中に出しても紛らわしく、
        // 狭ドックで Reload を押し出すだけ)。density / layoutDirection / TextMeasurer は composition から
        // 取り、描画は画面と同一パス (drawDiagram) を使う。
        val density = LocalDensity.current
        val composeLayoutDirection = LocalLayoutDirection.current
        val copyTextMeasurer = rememberTextMeasurer()
        val onCopyImage: (() -> Boolean)? = if (tab == DiagramTab.Diagram) {
            prepared.getOrNull()?.let { (graph, graphLayout) ->
                {
                    copyDiagramImageToClipboard(
                        graph = graph,
                        layout = graphLayout,
                        colors = colors,
                        density = density,
                        layoutDirection = composeLayoutDirection,
                        textMeasurer = copyTextMeasurer,
                    )
                }
            }
        } else {
            null
        }

        Header(
            stores = stores,
            selected = selected,
            onSelect = { selected = it },
            direction = direction,
            onToggleDirection = {
                direction = if (direction == LayoutDirection.LR) LayoutDirection.TB else LayoutDirection.LR
            },
            zoom = zoom,
            showZoom = prepared.isSuccess && tab == DiagramTab.Diagram,
            onZoomIn = { zoom = (zoom + 0.15f).coerceAtMost(2.5f) },
            onZoomOut = { zoom = (zoom - 0.15f).coerceAtLeast(0.5f) },
            onZoomReset = { zoom = 1f },
            onReload = onReload,
            colors = colors,
            onCopyImage = onCopyImage,
        )
        Divider(colors)
        // 全 degrade (名前のみ) と partial (一部の参照が未解決) を区別して明示する。
        if (model.degraded) DegradedBanner(model.error, colors)
        else if (model.unresolved) UnresolvedBanner(colors)
        TabBar(tab = tab, onSelect = { tab = it }, colors = colors)
        Divider(colors)

        Box(Modifier.fillMaxSize()) {
            when (tab) {
                DiagramTab.Diagram -> prepared.fold(
                    onSuccess = { (graph, layout) ->
                        StoreDiagram(
                            graph = graph,
                            layout = layout,
                            colors = colors,
                            onNavigate = onNavigate,
                            zoom = zoom,
                            // ピンチ / Ctrl+ホイールの倍率を zoom に畳み込む (ボタンと同じ 0.5〜2.5 に制限)。
                            onZoomBy = { factor -> zoom = (zoom * factor).coerceIn(0.5f, 2.5f) },
                        )
                    },
                    onFailure = { RenderErrorGuidance(it, colors) },
                )
                DiagramTab.Transitions -> TransitionsTable(model = model, colors = colors, onNavigate = onNavigate)
            }
        }
    }
}

/** A 1dp horizontal rule between the header, tabs and content. */
@Composable
private fun Divider(colors: DiagramColors) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.compositeBorder))
}
