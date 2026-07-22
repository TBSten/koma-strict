package me.tbsten.koma.strict.idea.ui

import me.tbsten.koma.strict.idea.SampleModels
import me.tbsten.koma.strict.idea.ir.GraphLowering
import me.tbsten.koma.strict.idea.layout.GraphLayout
import me.tbsten.koma.strict.idea.layout.layered.LayeredLayout
import me.tbsten.koma.strict.idea.layout.LayoutConfig
import me.tbsten.koma.strict.idea.layout.LayoutDirection
import me.tbsten.koma.strict.idea.model.ActionTrigger
import me.tbsten.koma.strict.idea.model.LeafState
import me.tbsten.koma.strict.idea.model.Reachability
import me.tbsten.koma.strict.idea.model.RootState
import me.tbsten.koma.strict.idea.model.StateId
import me.tbsten.koma.strict.idea.model.StoreDiagramModel
import me.tbsten.koma.strict.idea.ui.diagram.DiagramFit
import me.tbsten.koma.strict.idea.ui.diagram.MAX_CANVAS_EXTENT
import me.tbsten.koma.strict.idea.ui.diagram.fitDiagram
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P1-08 の回帰テスト: canvas extent cap ([MAX_CANVAS_EXTENT]) を超える図が無言で clip されず、
 * [fitDiagram] が全体を canvas 内に収める auto-fit 倍率へ縮小 + 縮小した事実を [DiagramFit.capped] で
 * 明示することを純ロジックで検証する。描画は静的 PNG に写らない領域を含むため、寸法・倍率で assert する。
 *
 * 契約の核: どの要求 zoom (50% / 100% / 250%) でも `layout寸法 × renderZoom <= cap` が両軸で成り立ち、
 * scroll 領域外へ node/transition が押し出されない (= 無言欠落しない)。
 */
class DiagramFitTest {

    // 図が実際に描かれる密度非依存の許容誤差 (割り算の FP 丸めで 1dp 程度はみ出しても clip 扱いしない)。
    private val tolerance = 1.0

    // 図の全体が canvas cap 内に収まっている (どのノードも scroll 外へ押し出されていない) こと。
    private fun assertFitsCanvas(width: Double, height: Double, zoom: Float) {
        val fit = fitDiagram(width, height, zoom)
        assertTrue(
            "width $width x zoom ${fit.renderZoom} は cap を超えてはならない",
            width * fit.renderZoom <= MAX_CANVAS_EXTENT + tolerance,
        )
        assertTrue(
            "height $height x zoom ${fit.renderZoom} は cap を超えてはならない",
            height * fit.renderZoom <= MAX_CANVAS_EXTENT + tolerance,
        )
    }

    @Test
    fun `cap 以内の通常サイズは要求 zoom をそのまま使い 縮小しない`() {
        for (zoom in floatArrayOf(0.5f, 1f, 2.5f)) {
            val fit = fitDiagram(layoutWidth = 1000.0, layoutHeight = 500.0, requestedZoom = zoom)
            assertEquals("通常図では renderZoom == 要求 zoom", zoom, fit.renderZoom, 1e-4f)
            assertFalse("通常図では capped でない", fit.capped)
        }
    }

    @Test
    fun `幅が cap を超える図は auto-fit で縮小し capped を立てつつ両軸を cap 内へ収める`() {
        // 30000dp 幅は 100% では cap 超過 → ceiling = 20000/30000 ≈ 0.667 まで縮小。
        val fit = fitDiagram(layoutWidth = 30_000.0, layoutHeight = 500.0, requestedZoom = 1f)
        assertTrue("cap 超過で capped", fit.capped)
        assertEquals("両軸を収める最大倍率 (幅律速)", 20_000.0 / 30_000.0, fit.renderZoom.toDouble(), 1e-3)
        assertFitsCanvas(30_000.0, 500.0, 1f)
    }

    @Test
    fun `高さが cap を超える図も max zoom で縮小し高さ軸を cap 内へ収める`() {
        // 縦長 (500 x 30000) を 250% 要求 → 高さ律速で ceiling ≈ 0.667。
        val fit = fitDiagram(layoutWidth = 500.0, layoutHeight = 30_000.0, requestedZoom = 2.5f)
        assertTrue("cap 超過で capped", fit.capped)
        assertEquals("両軸を収める最大倍率 (高さ律速)", 20_000.0 / 30_000.0, fit.renderZoom.toDouble(), 1e-3)
        assertFitsCanvas(500.0, 30_000.0, 2.5f)
    }

    @Test
    fun `同一の巨大図でも 50 100 250 の各 zoom で clip されない同じ契約が成立する`() {
        // 50% では収まる (capped でない) が、100% / 250% では縮小 (capped)。いずれも canvas 内。
        val width = 30_000.0
        val height = 800.0
        assertFalse("50% では 15000dp で収まるので縮小不要", fitDiagram(width, height, 0.5f).capped)
        assertTrue("100% では cap 超過で縮小", fitDiagram(width, height, 1f).capped)
        assertTrue("250% では cap 超過で縮小", fitDiagram(width, height, 2.5f).capped)
        for (zoom in floatArrayOf(0.5f, 1f, 2.5f)) assertFitsCanvas(width, height, zoom)
    }

    @Test
    fun `max zoom で cap を超える中程度の図は ceiling まで縮小する`() {
        // 9000dp を 250% 要求すると 22500dp で cap 超過 → ceiling = 20000/9000 ≈ 2.22。
        val fit = fitDiagram(layoutWidth = 9_000.0, layoutHeight = 300.0, requestedZoom = 2.5f)
        assertTrue("max zoom でも超過なら capped", fit.capped)
        assertEquals(20_000.0 / 9_000.0, fit.renderZoom.toDouble(), 1e-3)
        assertFitsCanvas(9_000.0, 300.0, 2.5f)
    }

    @Test
    fun `異常な寸法や zoom でも例外を投げず有限で正の renderZoom を返す`() {
        for (bad in listOf(
            Triple(Double.NaN, 500.0, 1f),
            Triple(1000.0, Double.NEGATIVE_INFINITY, 1f),
            Triple(0.0, 0.0, 0f),
            Triple(-10.0, -10.0, -3f),
            Triple(1000.0, 500.0, Float.NaN),
        )) {
            val (w, h, z) = bad
            val fit = fitDiagram(w, h, z)
            assertTrue("renderZoom は有限", fit.renderZoom.isFinite())
            assertTrue("renderZoom は正", fit.renderZoom > 0f)
        }
    }

    // ---- layout との結合: 実レイアウトが cap 超えの寸法を返し、fit がそれを検知して縮小へ分岐する ----

    // 図ツールウィンドウが LR/TB 双方で使う config (KomaStrictToolWindow の UiLayoutConfig と同一)。
    private val uiConfig = LayoutConfig(layerGap = 208.0, siblingGap = 60.0)

    private fun layoutOf(model: StoreDiagramModel, direction: LayoutDirection): GraphLayout =
        LayeredLayout.layout(GraphLowering.lower(model), direction, uiConfig)

    @Test
    fun `58 state 以上の LR chain は cap を超える幅になり auto-fit へ分岐する`() {
        val layout = layoutOf(SampleModels.longChain(80), LayoutDirection.LR)
        assertTrue(
            "80 連の LR chain は cap を超える幅を返すはず (現状 ${layout.canvasSize.width})",
            layout.canvasSize.width > MAX_CANVAS_EXTENT,
        )
        // 100% / 250% は縮小、全 zoom で canvas 内。
        assertTrue("100% では縮小", fitDiagram(layout.canvasSize.width, layout.canvasSize.height, 1f).capped)
        assertTrue("250% では縮小", fitDiagram(layout.canvasSize.width, layout.canvasSize.height, 2.5f).capped)
        for (zoom in floatArrayOf(0.5f, 1f, 2.5f)) {
            assertFitsCanvas(layout.canvasSize.width, layout.canvasSize.height, zoom)
        }
    }

    @Test
    fun `長い TB chain は cap を超える高さになり auto-fit へ分岐する`() {
        val layout = layoutOf(SampleModels.longChain(80), LayoutDirection.TB)
        assertTrue(
            "80 連の TB chain は cap を超える高さを返すはず (現状 ${layout.canvasSize.height})",
            layout.canvasSize.height > MAX_CANVAS_EXTENT,
        )
        assertTrue("100% では縮小", fitDiagram(layout.canvasSize.width, layout.canvasSize.height, 1f).capped)
        for (zoom in floatArrayOf(0.5f, 1f, 2.5f)) {
            assertFitsCanvas(layout.canvasSize.width, layout.canvasSize.height, zoom)
        }
    }

    @Test
    fun `大量 sibling の TB は cap を超える幅になり auto-fit へ分岐する`() {
        // 同一 layer に並ぶ大量の兄弟 leaf: TB では横に積まれ幅が cap を超える。
        val model = siblingFan(160)
        val layout = layoutOf(model, LayoutDirection.TB)
        assertTrue(
            "160 兄弟の TB は cap を超える幅を返すはず (現状 ${layout.canvasSize.width})",
            layout.canvasSize.width > MAX_CANVAS_EXTENT,
        )
        assertTrue("100% では縮小", fitDiagram(layout.canvasSize.width, layout.canvasSize.height, 1f).capped)
        for (zoom in floatArrayOf(0.5f, 1f, 2.5f)) {
            assertFitsCanvas(layout.canvasSize.width, layout.canvasSize.height, zoom)
        }
    }

    // Home から [count] 個の leaf 兄弟へ分岐する扇形モデル (同一 layer に大量の兄弟を作る)。
    private fun siblingFan(count: Int): StoreDiagramModel {
        val leaves = (0 until count).map { LeafState("L$it", StateId("L$it")) }
        val home = LeafState(
            "Home",
            StateId("Home"),
            actions = leaves.mapIndexed { i, l -> ActionTrigger("Go$i", targets = listOf(l.id)) },
        )
        val root = RootState(simpleName = "FanState", children = listOf(home) + leaves)
        return StoreDiagramModel(
            root = root,
            initial = listOf(StateId("Home")),
            reachableLeafIds = Reachability.compute(root, listOf(StateId("Home"))),
        )
    }
}
