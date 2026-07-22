@file:OptIn(InternalComposeUiApi::class)

package me.tbsten.koma.strict.idea.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.renderComposeScene
import me.tbsten.koma.strict.idea.SampleModels
import me.tbsten.koma.strict.idea.ir.DiagramGraph
import me.tbsten.koma.strict.idea.ir.GraphLowering
import me.tbsten.koma.strict.idea.ir.NodeId
import me.tbsten.koma.strict.idea.layout.LayeredLayout
import me.tbsten.koma.strict.idea.layout.LayoutConfig
import me.tbsten.koma.strict.idea.layout.LayoutDirection
import me.tbsten.koma.strict.idea.model.StoreDiagramModel
import me.tbsten.koma.strict.idea.ui.DiagramSelection
import me.tbsten.koma.strict.idea.ui.KomaStrictToolWindowContent
import me.tbsten.koma.strict.idea.ui.StoreDiagram
import me.tbsten.koma.strict.idea.ui.rememberDiagramColors
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.system.exitProcess

/**
 * Headless preview entry point for the Koma Strict tool window (`ide.dev.md` visual loop).
 *
 * Renders the shared [KomaStrictToolWindowContent] against the standalone Jewel Int UI theme via
 * `renderComposeScene` (Skiko SOFTWARE, no IDE) and writes one PNG per scenario × theme. The plugin
 * hosts the very same composable through `addComposeTab`, so these PNGs stay faithful to what ships
 * (only the theme wrapper differs).
 *
 * Two modes, selected by the first program argument (wired to Gradle tasks of the same name):
 * - `update` (`./gradlew updatePreview`): render, write the gallery `index.html`, then force-sync
 *   the golden snapshots under `snapshots/preview` ([PreviewVrt.updateGoldens]).
 * - `verify` (`./gradlew verifyPreview`): render, compare against the goldens
 *   ([PreviewVrt.verify]) and exit non-zero on any difference; a self-contained report is written
 *   under `build/preview/report`.
 */
fun main(args: Array<String>) {
    val mode = args.firstOrNull() ?: "update"
    if (mode != "update" && mode != "verify") {
        System.err.println("PREVIEW_USAGE unknown mode '$mode' (expected: update | verify)")
        exitProcess(2)
    }

    System.setProperty("java.awt.headless", "true")
    System.setProperty("skiko.renderApi", "SOFTWARE")

    val outDir = File("build/preview").apply { mkdirs() }
    // 生成前に管理下の生成物 (koma-*.png / index.html) を消す。rename/remove した旧 scenario の PNG が
    // gallery に残り続けないようにする (P3-07)。手書きファイルには触らない。
    PreviewChecks.cleanManagedOutput(outDir)

    renderAll(outDir)

    if (mode == "update") {
        // 全 PNG を走査して閲覧用の index.html (scenario 別グルーピング) を書き出す。
        writePreviewGallery(outDir)
    }

    // 全 PNG の四隅 alpha を検査する。通常 preview は theme surface で全面塗装されるので不透明のはず
    // (P2-15)。透明な角が残っていたら暗い viewer で黒 marker / table header / 薄線が消えるので、
    // 警告して非ゼロ終了し、update/verify を fail させる (silent な劣化を許さない)。
    val transparent = PreviewChecks.transparentCornerPngs(outDir)
    if (transparent.isNotEmpty()) {
        System.err.println(
            "PREVIEW_ALPHA_FAIL " + transparent.size + " PNG(s) have a transparent corner (alpha<255): " +
                transparent.joinToString(", "),
        )
        exitProcess(1)
    }
    println("PREVIEW_ALPHA_OK all preview PNGs have opaque corners (alpha=255)")

    val goldenDir = File("snapshots/preview")
    when (mode) {
        "update" -> {
            val sync = PreviewVrt.updateGoldens(outDir, goldenDir)
            println("PREVIEW_GOLDEN_UPDATED copied=" + sync.copied + " removed=" + sync.removed + " -> " + goldenDir.absolutePath)
            println("PREVIEW_OK wrote PNGs to " + outDir.absolutePath)
        }
        "verify" -> {
            val reportDir = File(outDir, "report")
            val results = PreviewVrt.verify(outDir, goldenDir, reportDir)
            val changedCount = results.count { it.status == VrtStatus.Changed }
            val newCount = results.count { it.status == VrtStatus.New }
            val missingCount = results.count { it.status == VrtStatus.Missing }
            if (changedCount + newCount + missingCount == 0) {
                println("PREVIEW_VERIFY_OK " + results.size + " PNG(s) match golden " + goldenDir.absolutePath)
            } else {
                println("PREVIEW_VERIFY_FAILED (changed=$changedCount new=$newCount missing=$missingCount)")
                for (result in results.filter { it.status != VrtStatus.Unchanged }) {
                    println("PREVIEW_VERIFY_DIFF " + result.status.toString().lowercase() + " " + result.name)
                }
                println("PREVIEW_VERIFY_REPORT " + File(reportDir, "index.html").absolutePath)
                exitProcess(1)
            }
        }
    }
}

/** Renders every sample scenario × theme into [outDir] (`koma-<name>-<theme>.png`). */
private fun renderAll(outDir: File) {
    // 全体 UI (ツールバー + タブ + 図) を light / dark で。
    render(outDir, "lce", 900, 360, dark = false) { KomaStrictToolWindowContent(listOf(SampleModels.lce())) }
    render(outDir, "lce", 900, 360, dark = true) { KomaStrictToolWindowContent(listOf(SampleModels.lce())) }
    render(outDir, "feed", 1040, 540, dark = false) { KomaStrictToolWindowContent(listOf(SampleModels.feed())) }
    render(outDir, "feed", 1040, 540, dark = true) { KomaStrictToolWindowContent(listOf(SampleModels.feed())) }
    // Loading が Idle/Error/Test の 3 root leaf へ分岐: 押し出された Error と Test が重ならず、
    // かつ Stable 箱の上端 (ラベル) が見切れないことを確認する回帰ケース。
    render(outDir, "feed-branch", 1040, 610, dark = false) { KomaStrictToolWindowContent(listOf(SampleModels.feedBranch())) }
    render(outDir, "feed-branch", 1040, 610, dark = true) { KomaStrictToolWindowContent(listOf(SampleModels.feedBranch())) }
    render(outDir, "tabs", 900, 490, dark = false) { KomaStrictToolWindowContent(listOf(SampleModels.tabs())) }
    render(outDir, "tabs", 900, 490, dark = true) { KomaStrictToolWindowContent(listOf(SampleModels.tabs())) }
    // 長い state 名: 枠内で折り返し、それでも入らなければフォント縮小 (autosize) されるのを確認する。
    render(outDir, "longname", 1010, 360, dark = false) { KomaStrictToolWindowContent(listOf(SampleModels.longNames())) }
    render(outDir, "longname", 1010, 360, dark = true) { KomaStrictToolWindowContent(listOf(SampleModels.longNames())) }
    // フォームウィザード (samples.md §4): 自己遷移 (InputName)・検証NG の stay+emit・終端 leaf Done。
    render(outDir, "wizard", 1700, 360, dark = false) { KomaStrictToolWindowContent(listOf(SampleModels.wizard())) }
    render(outDir, "wizard", 1700, 360, dark = true) { KomaStrictToolWindowContent(listOf(SampleModels.wizard())) }
    render(outDir, "wizard-canvas", 1700, 360, dark = false) { DiagramLrPreview(SampleModels.wizard()) }
    // 同一ノードに ENTER/ACTION/RECOVER の 3 self-loop (Relay): それぞれ別の面 (上/下/右) に振り分けられ、
    // 弧もラベルも重ならず全 trigger 名が読めることを確認する (P1-05)。
    render(outDir, "selfloops-canvas", 900, 360, dark = false) { DiagramLrPreview(SampleModels.selfLoops()) }
    render(outDir, "selfloops", 900, 360, dark = false) { KomaStrictToolWindowContent(listOf(SampleModels.selfLoops())) }
    render(outDir, "selfloops", 900, 360, dark = true) { KomaStrictToolWindowContent(listOf(SampleModels.selfLoops())) }
    // P1-04: root leaf "any" と root any-state 擬似ノードが同名で衝突しクラッシュしていた回帰。
    // 型付き NodeId で両者が別ノードとして共存し、layout/draw が崩れないことを確認する。
    render(outDir, "any-named", 1460, 500, dark = false) { KomaStrictToolWindowContent(listOf(SampleModels.anyNamed())) }
    render(outDir, "any-named", 1460, 500, dark = true) { KomaStrictToolWindowContent(listOf(SampleModels.anyNamed())) }
    // recover (破線・色つき) + exit バッジ = 認証サンプル (samples.md §5)。
    render(outDir, "auth", 1130, 390, dark = false) { KomaStrictToolWindowContent(listOf(SampleModels.auth())) }
    render(outDir, "auth", 1130, 390, dark = true) { KomaStrictToolWindowContent(listOf(SampleModels.auth())) }
    // 2 段入れ子 composite + group 共有 any Loaded。
    render(outDir, "settings", 1070, 440, dark = false) { KomaStrictToolWindowContent(listOf(SampleModels.settings())) }
    render(outDir, "settings", 1070, 440, dark = true) { KomaStrictToolWindowContent(listOf(SampleModels.settings())) }
    // 複数 @StoreSpec: 上段の Store ドロップダウン切替。
    render(outDir, "multi", 900, 360, dark = false) {
        KomaStrictToolWindowContent(listOf(SampleModels.lce(), SampleModels.feed(), SampleModels.tabs()))
    }
    render(outDir, "multi", 900, 360, dark = true) {
        KomaStrictToolWindowContent(listOf(SampleModels.lce(), SampleModels.feed(), SampleModels.tabs()))
    }
    // 到達不能 state (Broken) の警告色を確認。
    render(outDir, "unreachable", 1010, 360, dark = false) {
        KomaStrictToolWindowContent(listOf(SampleModels.unreachable()))
    }
    render(outDir, "unreachable", 1010, 360, dark = true) {
        KomaStrictToolWindowContent(listOf(SampleModels.unreachable()))
    }
    // @StoreSpec 無しファイルのセットアップ案内 (空状態)。
    render(outDir, "setup", 600, 240, dark = false) { KomaStrictToolWindowContent(emptyList()) }
    // index 中フォールバック画面。
    render(outDir, "indexing", 600, 240, dark = false) { KomaStrictToolWindowContent(indexing = true) }
    // 拡大 (zoom) 表示の確認 (canvas を 1.6 倍)。
    render(outDir, "zoom", 980, 460, dark = false) {
        val g = GraphLowering.lower(SampleModels.lce())
        val l = LayeredLayout.layout(g, LayoutDirection.LR, LayoutConfig(layerGap = 208.0, siblingGap = 60.0))
        StoreDiagram(graph = g, layout = l, colors = rememberDiagramColors(), zoom = 1.6f)
    }
    // cap を超える巨大な図 (長い LR chain): auto-fit で全体が canvas 内に収まって tail が無言 clip されず、
    // oversize バナーが縮小理由と「情報は欠落していない」ことを明示することを確認する (P1-08)。
    render(outDir, "oversize", 1120, 420, dark = false) {
        KomaStrictToolWindowContent(listOf(SampleModels.longChain(120)))
    }
    render(outDir, "oversize", 1120, 420, dark = true) {
        KomaStrictToolWindowContent(listOf(SampleModels.longChain(120)))
    }
    render(outDir, "lce-tb", 900, 390, dark = false) { DiagramTbPreview(SampleModels.lce()) }
    render(outDir, "auth-canvas", 1130, 360, dark = false) { DiagramLrPreview(SampleModels.auth()) }
    // フォーカス検証 (ide-3.md tier1/2/3): node 選択で自ノードが accent border 強調、入出力 Transition +
    // 相手 State は通常、それ以外は減光する。feed の Stable.Idle を選択。
    render(outDir, "focus-feed-node", 1200, 520, dark = false) {
        DiagramLrFocusPreview(SampleModels.feed()) { setOf(DiagramSelection.Node(NodeId.state("Stable", "Idle"))) }
    }
    // edge 選択: 矢印が太く・ラベルが accent 枠で囲まれ、前後 2 State 以外が減光する。lce の Loading -> Content。
    render(outDir, "focus-lce-edge", 980, 420, dark = false) {
        DiagramLrFocusPreview(SampleModels.lce()) { graph ->
            setOfNotNull(
                graph.edges.firstOrNull { it.fromId == NodeId.state("Loading") && it.toId == NodeId.state("Content") }
                    ?.let { DiagramSelection.Edge(it) },
            )
        }
    }
    // nest state (composite) 選択: 箱が accent border 強調、配下の子 State + 直結矢印だけ残り他は減光する。
    render(outDir, "focus-settings-composite", 1070, 460, dark = false) {
        DiagramLrFocusPreview(SampleModels.settings()) { graph ->
            setOfNotNull(graph.composites.firstOrNull()?.let { DiagramSelection.Composite(it.id) })
        }
    }
    // node self-loop (stay) 選択: 自己ループの弧が太く強調される。selfLoops の 1 本目の self-loop を選択。
    render(outDir, "focus-selfloops-stay", 900, 380, dark = false) {
        DiagramLrFocusPreview(SampleModels.selfLoops()) { graph ->
            setOfNotNull(graph.edges.firstOrNull { it.fromId == it.toId }?.let { DiagramSelection.Edge(it) })
        }
    }
    // 複数選択 (Shift): 2 つの State を同時選択すると両方が強調され、両者の focus 集合の和が通常表示になる。
    render(outDir, "focus-feed-multi", 1200, 520, dark = false) {
        DiagramLrFocusPreview(SampleModels.feed()) {
            setOf(
                DiagramSelection.Node(NodeId.state("Stable", "Idle")),
                DiagramSelection.Node(NodeId.state("Error")),
            )
        }
    }
    // TB では @OnExit バッジがノード下に出る (兄弟と重ならない) ことを確認する。
    render(outDir, "auth-tb-canvas", 900, 580, dark = false) { DiagramTbPreview(SampleModels.auth()) }
    render(outDir, "settings-canvas", 1070, 360, dark = false) { DiagramLrPreview(SampleModels.settings()) }
    // group を指す遷移エッジ (SignedOut -> SignedIn グループ): 矢印が composite box 境界に刺さり、
    // 箱を突き抜け/潰れが無いことを確認する。
    render(outDir, "session", 1040, 360, dark = false) { KomaStrictToolWindowContent(listOf(SampleModels.session())) }
    render(outDir, "session", 1040, 360, dark = true) { KomaStrictToolWindowContent(listOf(SampleModels.session())) }
    render(outDir, "session-canvas", 1040, 360, dark = false) { DiagramLrPreview(SampleModels.session()) }
}

/** Renders just the canvas in top-to-bottom direction to eyeball the TB toggle. */
@Composable
private fun DiagramTbPreview(model: StoreDiagramModel) {
    val graph = GraphLowering.lower(model)
    val layout = LayeredLayout.layout(graph, LayoutDirection.TB, LayoutConfig(layerGap = 128.0, siblingGap = 60.0))
    StoreDiagram(graph = graph, layout = layout, colors = rememberDiagramColors())
}

/** Renders just the canvas in left-to-right direction (no window chrome) to eyeball edges / labels. */
@Composable
private fun DiagramLrPreview(model: StoreDiagramModel) {
    val graph = GraphLowering.lower(model)
    val layout = LayeredLayout.layout(graph, LayoutDirection.LR, LayoutConfig(layerGap = 208.0, siblingGap = 60.0))
    StoreDiagram(graph = graph, layout = layout, colors = rememberDiagramColors())
}

/**
 * Renders the LR canvas with a fixed focus selection (`ide-3.md`) so the tier 1/2/3 emphasis + dimming
 * shows up in a golden PNG (focus is interactive, so it never appears otherwise). [selection] builds the
 * selected set from the lowered graph.
 */
@Composable
private fun DiagramLrFocusPreview(model: StoreDiagramModel, selection: (DiagramGraph) -> Set<DiagramSelection>) {
    val graph = GraphLowering.lower(model)
    val layout = LayeredLayout.layout(graph, LayoutDirection.LR, LayoutConfig(layerGap = 208.0, siblingGap = 60.0))
    StoreDiagram(graph = graph, layout = layout, colors = rememberDiagramColors(), selection = selection(graph))
}

private fun render(outDir: File, name: String, width: Int, height: Int, dark: Boolean, content: @Composable () -> Unit) {
    val image = renderComposeScene(width = width, height = height) {
        IntUiTheme(isDark = dark) {
            // render root を theme の panel background で全面塗装する。単体 canvas / table preview は
            // 自前で背景を塗らないので、これが無いと角が透明になり暗い viewer で消える (P2-15)。
            // 実 plugin の KomaStrictToolWindowContent も同じ panelBackground を敷くので忠実。
            Box(Modifier.fillMaxSize().background(JewelTheme.globalColors.panelBackground)) { content() }
        }
    }
    val png = image.encodeToData(EncodedImageFormat.PNG)!!.bytes
    val theme = if (dark) "dark" else "light"
    val outFile = File(outDir, "koma-$name-$theme.png")
    outFile.writeBytes(png)
    println("PREVIEW_PNG " + outFile.absolutePath + " (" + png.size + " bytes)")
}
