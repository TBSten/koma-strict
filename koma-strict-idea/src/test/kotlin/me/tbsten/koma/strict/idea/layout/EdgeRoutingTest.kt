package me.tbsten.koma.strict.idea.layout

import me.tbsten.koma.strict.idea.SampleModels
import me.tbsten.koma.strict.idea.ir.DiagramGraph
import me.tbsten.koma.strict.idea.ir.GraphLowering
import me.tbsten.koma.strict.idea.ir.NodeId
import me.tbsten.koma.strict.idea.layout.edge.EdgeRouting
import me.tbsten.koma.strict.idea.model.StoreDiagramModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P1-07 の回帰テスト: エッジの経路 ([EdgeRouting]) が「その端点ノード以外の node interior」を貫かない
 * ことを座標で検証する。描画は private なので経路計算を純ヘルパへ切り出してテストする。
 *
 * - `route` 単体: 直線で足りるなら 2 点のまま (従来の直線挙動を維持)、障害物があれば折れ線で迂回する。
 * - lowering + layout した実モデル (feed-branch / auth TB / auth LR / settings / session) で、全エッジの
 *   全区間が無関係な node rect の内部を通らないことを assert する。特に feed-branch の retry
 *   (Error -> Loading) が Test を貫通しないこと (受入条件)。
 */
class EdgeRoutingTest {

    // 線分がこの長さを超えて矩形内部を通れば「貫通」とみなす (router の CROSS_EPS と揃える)。
    private val crossTolerance = 1.0

    private fun p(x: Double, y: Double) = Point(x, y)

    // ---- straight-line contract (曲げない) ----

    @Test
    fun `どのエッジの経路も非端点ノードを貫通しない`() {
        // 契約: エッジは基本 [sourcePort, targetPort] の 2 点直線 (面ペア付け替えで障害物を避ける)。
        // それでも非端点ノードを貫く時だけ go-around 迂回 (>2 点) に差し替える。最終不変条件として、
        // 直線でも迂回でも「経路の各区間が非端点ノードの内部を貫通しない」ことを固定する
        // (start マーカーは装飾なので除外)。
        val samples = listOf(
            SampleModels.lce(), SampleModels.feed(), SampleModels.feedBranch(), SampleModels.tabs(),
            SampleModels.wizard(), SampleModels.auth(), SampleModels.settings(), SampleModels.session(),
            SampleModels.anyNamed(), SampleModels.selfLoops(), SampleModels.unreachable(),
        )
        for (model in samples) {
            for (direction in LayoutDirection.entries) {
                val (graph, layout) = lowerAndLayout(model, direction)
                val routes = EdgeRouting.routeAll(graph, layout)
                val startId = graph.nodes.filterIsInstance<me.tbsten.koma.strict.idea.ir.StartNode>().firstOrNull()?.id
                for ((edge, pts) in routes) {
                    val obstacles = layout.nodeRects.filterKeys {
                        it != edge.fromId && it != edge.toId && it != startId
                    }.values
                    for (i in 0 until pts.size - 1) {
                        for (o in obstacles) {
                            assertEquals(
                                "${model.root.simpleName} $direction: ${edge.fromId.display}->${edge.toId.display} " +
                                    "の区間 $i が非端点ノードを貫通",
                                0.0,
                                EdgeRouting.overlapLength(pts[i], pts[i + 1], o),
                                0.5,
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `overlapLength は矩形を貫く線分で正・かする線分で 0`() {
        val rect = Rect(x = 0.0, y = 0.0, width = 100.0, height = 100.0)
        // 中央を横断する線分は矩形幅ぶん内部を通る。
        assertTrue(EdgeRouting.overlapLength(p(-10.0, 50.0), p(110.0, 50.0), rect) > 90.0)
        // 上辺に沿ってかするだけの線分は内部長 0。
        assertEquals(0.0, EdgeRouting.overlapLength(p(-10.0, 0.0), p(110.0, 0.0), rect), 1e-6)
        // 完全に外れた線分も 0。
        assertEquals(0.0, EdgeRouting.overlapLength(p(-10.0, 200.0), p(110.0, 200.0), rect), 1e-6)
    }

    // ---- integration invariants over real models ----

    /** 全ての非 self エッジについて、その端点ノード以外の node rect 内部を全区間が通らないことを検証する。 */
    private fun assertNoUnrelatedNodeCrossing(graph: DiagramGraph, layout: GraphLayout) {
        val routes = EdgeRouting.routeAll(graph, layout)
        for (edge in graph.edges) {
            if (edge.fromId == edge.toId) continue
            val pts = routes[edge] ?: continue
            for ((id, rect) in layout.nodeRects) {
                if (id == edge.fromId || id == edge.toId) continue
                for (i in 0 until pts.size - 1) {
                    val overlap = EdgeRouting.overlapLength(pts[i], pts[i + 1], rect)
                    assertTrue(
                        "edge ${edge.fromId.display} -> ${edge.toId.display} の区間 $i が無関係な node " +
                            "${id.display} を貫通している (overlap=$overlap)",
                        overlap <= crossTolerance,
                    )
                }
            }
        }
    }

    private fun lowerAndLayout(model: StoreDiagramModel, direction: LayoutDirection): Pair<DiagramGraph, GraphLayout> {
        val graph = GraphLowering.lower(model)
        // 実 tool window / canvas preview と同じ寸法 (layerGap=208 LR / 128 TB, siblingGap=60)。
        val config = when (direction) {
            LayoutDirection.LR -> LayoutConfig(layerGap = 208.0, siblingGap = 60.0)
            LayoutDirection.TB -> LayoutConfig(layerGap = 128.0, siblingGap = 60.0)
        }
        return graph to LayeredLayout.layout(graph, direction, config)
    }

    @Test
    fun `feed-branch の retry (Error to Loading) は Test ノードを貫通しない`() {
        val (graph, layout) = lowerAndLayout(SampleModels.feedBranch(), LayoutDirection.LR)
        val retry = graph.edges.single {
            it.fromId == NodeId.state("Error") && it.toId == NodeId.state("Loading")
        }
        val pts = EdgeRouting.routeAll(graph, layout).getValue(retry)
        val testRect = layout.nodeRects.getValue(NodeId.state("Test"))
        for (i in 0 until pts.size - 1) {
            assertTrue(
                "retry の区間 $i が Test を貫通している",
                EdgeRouting.overlapLength(pts[i], pts[i + 1], testRect) <= crossTolerance,
            )
        }
    }

    @Test
    fun `feed-branch の全 edge は無関係な node interior を通らない`() {
        val (graph, layout) = lowerAndLayout(SampleModels.feedBranch(), LayoutDirection.LR)
        assertNoUnrelatedNodeCrossing(graph, layout)
    }

    @Test
    fun `auth TB の全 edge は無関係な node interior を通らない`() {
        val (graph, layout) = lowerAndLayout(SampleModels.auth(), LayoutDirection.TB)
        assertNoUnrelatedNodeCrossing(graph, layout)
    }

    @Test
    fun `auth LR の全 edge は無関係な node interior を通らない`() {
        val (graph, layout) = lowerAndLayout(SampleModels.auth(), LayoutDirection.LR)
        assertNoUnrelatedNodeCrossing(graph, layout)
    }

    @Test
    fun `settings の全 edge は無関係な node interior を通らない`() {
        val (graph, layout) = lowerAndLayout(SampleModels.settings(), LayoutDirection.LR)
        assertNoUnrelatedNodeCrossing(graph, layout)
    }

    @Test
    fun `session の全 edge は無関係な node interior を通らない`() {
        val (graph, layout) = lowerAndLayout(SampleModels.session(), LayoutDirection.LR)
        assertNoUnrelatedNodeCrossing(graph, layout)
    }

    // ---- median (兄弟並べ替え) の交差削減リグレッション ----

    /** 2 線分が互いの内部で交差する (端点同士の接触は除く) かどうか。 */
    private fun properlyIntersects(a1: Point, a2: Point, b1: Point, b2: Point): Boolean {
        fun cross(o: Point, p: Point, q: Point): Double =
            (p.x - o.x) * (q.y - o.y) - (p.y - o.y) * (q.x - o.x)
        val eps = 1e-9
        val d1 = cross(b1, b2, a1)
        val d2 = cross(b1, b2, a2)
        val d3 = cross(a1, a2, b1)
        val d4 = cross(a1, a2, b2)
        // 厳密に両側へ跨る場合のみ「交差」とする (共線・端点接触は数えない)。
        return ((d1 > eps && d2 < -eps) || (d1 < -eps && d2 > eps)) &&
            ((d3 > eps && d4 < -eps) || (d3 < -eps && d4 > eps))
    }

    /** 異なるエッジ同士の全区間ペアの交差数。 */
    private fun countEdgeCrossings(graph: DiagramGraph, layout: GraphLayout): Int {
        val routes = EdgeRouting.routeAll(graph, layout)
        val edges = graph.edges.filter { it.fromId != it.toId }.mapNotNull { routes[it] }
        var count = 0
        for (i in edges.indices) {
            for (j in i + 1 until edges.size) {
                val a = edges[i]
                val b = edges[j]
                for (s in 0 until a.size - 1) {
                    for (t in 0 until b.size - 1) {
                        if (properlyIntersects(a[s], a[s + 1], b[t], b[t + 1])) count++
                    }
                }
            }
        }
        return count
    }

    @Test
    fun `auth LR は median の並べ替えでエッジ同士が交差しない`() {
        val (graph, layout) = lowerAndLayout(SampleModels.auth(), LayoutDirection.LR)
        assertEquals(0, countEdgeCrossings(graph, layout))
    }

    @Test
    fun `auth TB は median の並べ替えでエッジ同士が交差しない`() {
        val (graph, layout) = lowerAndLayout(SampleModels.auth(), LayoutDirection.TB)
        assertEquals(0, countEdgeCrossings(graph, layout))
    }
}
