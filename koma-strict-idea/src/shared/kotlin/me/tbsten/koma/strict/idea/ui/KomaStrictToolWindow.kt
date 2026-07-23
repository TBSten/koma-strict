package me.tbsten.koma.strict.idea.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import me.tbsten.koma.strict.idea.flow.GeneratedFlowSpec
import me.tbsten.koma.strict.idea.ir.GraphLowering
import me.tbsten.koma.strict.idea.ir.NodeId
import me.tbsten.koma.strict.idea.layout.layered.LayeredLayout
import me.tbsten.koma.strict.idea.layout.LayoutConfig
import me.tbsten.koma.strict.idea.model.SourceAnchor
import me.tbsten.koma.strict.idea.model.StoreDiagramModel
import me.tbsten.koma.strict.idea.ui.component.DegradedBanner
import me.tbsten.koma.strict.idea.ui.component.DiagramZoomControls
import me.tbsten.koma.strict.idea.ui.component.FlowRecorderPanel
import me.tbsten.koma.strict.idea.ui.component.Header
import me.tbsten.koma.strict.idea.ui.component.RecordingPill
import me.tbsten.koma.strict.idea.ui.component.IndexingGuidance
import me.tbsten.koma.strict.idea.ui.component.RenderErrorGuidance
import me.tbsten.koma.strict.idea.ui.component.SetupGuidance
import me.tbsten.koma.strict.idea.ui.component.UnresolvedBanner
import me.tbsten.koma.strict.idea.ui.diagram.DiagramColors
import me.tbsten.koma.strict.idea.ui.diagram.DiagramSelection
import me.tbsten.koma.strict.idea.ui.diagram.StoreDiagram
import me.tbsten.koma.strict.idea.ui.diagram.copyDiagramImageToClipboard
import me.tbsten.koma.strict.idea.ui.diagram.focusFrom
import me.tbsten.koma.strict.idea.ui.diagram.rememberDiagramColors
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.HorizontalSplitLayout
import org.jetbrains.jewel.ui.component.rememberSplitLayoutState
import kotlinx.coroutines.delay
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
    /** Insert the generated `@FlowSpec` into the store's source file (`ide-test-code.md` F8); no-op in preview. */
    onInsertFlowSpec: (SourceAnchor, GeneratedFlowSpec) -> Unit = { _, _ -> },
    /** Write the generated test into the store's test source set (`ide-test-code.md`); no-op in preview. */
    onGenerateTestFile: (SourceAnchor, fileName: String, content: String) -> Unit = { _, _, _ -> },
    /**
     * Flow recorder state. The plugin passes a **controller-owned** instance so a recording survives file
     * switches and tool-window hide/show; the default is a local one (preview / tests).
     */
    recording: RecordingState = rememberRecordingState(),
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
        // 記録パネルの分割位置 (図が既定 62%)。境界ドラッグで変えられる。
        val splitState = rememberSplitLayoutState(0.62f)
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

        // 記録中の選択表示 (`ide-test-code.md`): cursor から記録可能な transition だけを focus し、他は減光
        // (= 選択不可の見た目)。遷移を記録した直後は 0.2 秒だけクリックした矢印を見せてから遷移後 cursor の
        // recordable へ移す (initial 変更 / 記録開始で遷移が無い時は即時)。連打は key 変化で追従する。
        LaunchedEffect(recording.recording, recording.flow.initial, recording.flow.transitions.size) {
            if (!recording.recording) return@LaunchedEffect
            if (recording.flow.transitions.isNotEmpty()) delay(200)
            val graph = prepared.getOrNull()?.first
            val recordable = graph?.let { recording.recordableSelections(it) }.orEmpty()
            val focus = recordable.ifEmpty {
                recording.flow.cursor?.let { setOf(DiagramSelection.Node(NodeId.State(it))) }.orEmpty()
            }
            state.focus.select(focus)
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
            recording = recording.recording,
            onToggleRecording = {
                // 開始時に State を1つ選択中なら、それを initial にする (無ければ宣言 initial)。
                val selectedInitial = state.focus.selection
                    .filterIsInstance<DiagramSelection.Node>()
                    .firstNotNullOfOrNull { (it.id as? NodeId.State)?.path }
                    ?.takeIf { model.leaf(it) != null }
                recording.toggleRecording(model, selectedInitial)
            },
        )
        Divider(colors)
        // 全 degrade (名前のみ) と partial (一部の参照が未解決) を区別して明示する。
        if (model.degraded) DegradedBanner(model.error, colors)
        else if (model.unresolved) UnresolvedBanner(colors)

        // テキスト (生成コード / @FlowSpec) を system clipboard へ。描画中は呼ばれないので headless でも安全。
        val onCopyText: (String) -> Boolean = { text ->
            try {
                java.awt.Toolkit.getDefaultToolkit().systemClipboard
                    .setContents(java.awt.datatransfer.StringSelection(text), null)
                true
            } catch (e: Exception) {
                false
            }
        }
        // 図ペイン (拡大ボタン + 記録ピルを内包)。分割の左 / パネル閉時はフル幅。
        val diagramArea: @Composable () -> Unit = {
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
                            recording = recording.recording,
                            // 記録できた矢印は即 focus (0.3 秒見せる)。記録できないクリックは無視 = 非選択。
                            // 0.3 秒後は上の LaunchedEffect が遷移後 cursor の recordable へ focus を移す。
                            onRecordTap = { sel -> if (recording.record(sel, model)) state.focus.select(setOf(sel)) },
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
                // 記録中 & パネル未展開はフローティングピルを下中央に。
                if (recording.recording && !recording.panelOpen) {
                    RecordingPill(
                        model = model,
                        recording = recording,
                        colors = colors,
                        // 最小化時は下・左端の 1 アイコンに畳む。展開時は下中央。
                        modifier = Modifier
                            .align(if (recording.pillMinimized) Alignment.BottomStart else Alignment.BottomCenter)
                            .padding(16.dp),
                    )
                }
            }
        }
        // 記録パネルは Jewel の HorizontalSplitLayout で図の右に並べ、境界ドラッグで幅を変えられる。
        if (recording.panelOpen) {
            HorizontalSplitLayout(
                first = diagramArea,
                second = {
                    FlowRecorderPanel(
                        model = model,
                        recording = recording,
                        colors = colors,
                        onCopyText = onCopyText,
                        onInsertFlowSpec = { generated -> model.root.source?.let { onInsertFlowSpec(it, generated) } },
                        canInsert = model.root.source != null && !model.degraded,
                        onGenerateTestFile = { fileName, content -> model.root.source?.let { onGenerateTestFile(it, fileName, content) } },
                        modifier = Modifier.fillMaxSize(),
                    )
                },
                modifier = Modifier.fillMaxSize(),
                firstPaneMinWidth = 220.dp,
                secondPaneMinWidth = 300.dp,
                state = splitState,
            )
        } else {
            diagramArea()
        }
    }
}

/** A 1dp horizontal rule between the header and the content. */
@Composable
private fun Divider(colors: DiagramColors) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.compositeBorder))
}
