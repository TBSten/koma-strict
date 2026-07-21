package me.tbsten.koma.strict.idea.ui

import me.tbsten.koma.strict.idea.SampleModels
import me.tbsten.koma.strict.idea.ir.DiagramGraph
import me.tbsten.koma.strict.idea.ir.EdgeKind
import me.tbsten.koma.strict.idea.ir.GraphEdge
import me.tbsten.koma.strict.idea.ir.GraphLowering
import me.tbsten.koma.strict.idea.ir.NodeId
import me.tbsten.koma.strict.idea.layout.LayeredLayout
import me.tbsten.koma.strict.idea.layout.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/**
 * P1-05 の回帰テスト: 同一ノードに複数の self-loop がある場合でも、各ループが別々の面 (上/下/右/左) に
 * 振り分けられ、弧もラベル位置も完全重複しないことを純粋な幾何で検証する ([selfLoopArc])。描画関数は
 * 静的 PNG に写らない部分もあるため、座標レベルで assert する。
 *
 * 併せて wizard (Step1 = inputName + next stay の 2 self-loop) と、3 種混在 (ENTER/ACTION/RECOVER) の
 * [SampleModels.selfLoops] を lowering + layout してループ配置が重ならないことを確認する。
 */
class DiagramSelfLoopTest {

    // drawDiagram と同じ規則: self-loop は (fromId, kind) で 1 グループ = 1 弧に集約され、
    // グループに fromId ごとの描画順 ordinal が振られる。
    private fun selfLoopGroupOrdinals(graph: DiagramGraph): Map<Pair<NodeId, EdgeKind>, Int> {
        val count = HashMap<NodeId, Int>()
        val result = LinkedHashMap<Pair<NodeId, EdgeKind>, Int>()
        for (edge in graph.edges) {
            if (edge.fromId != edge.toId) continue
            val key = edge.fromId to edge.kind
            if (key in result) continue
            val n = count.getOrDefault(edge.fromId, 0)
            result[key] = n
            count[edge.fromId] = n + 1
        }
        return result
    }

    private fun arcOf(rect: Rect, ordinal: Int): SelfLoopArc =
        // drawSelfLoop と同じ dp 定数 (density 1 の preview / layout 座標に一致)。
        selfLoopArc(
            left = rect.x.toFloat(),
            top = rect.y.toFloat(),
            width = rect.width.toFloat(),
            height = rect.height.toFloat(),
            ordinal = ordinal,
            opening = 18f,
            spread = 48f,
            lift = 32f,
            labelGap = 4f,
        )

    private fun dist(ax: Float, ay: Float, bx: Float, by: Float): Float = hypot(ax - bx, ay - by)

    @Test
    fun `self-loop の face は ordinal で上下右左を順に巡り 4 本目で先頭へ戻る`() {
        assertEquals(SelfLoopFace.TOP, selfLoopFace(0))
        assertEquals(SelfLoopFace.BOTTOM, selfLoopFace(1))
        assertEquals(SelfLoopFace.RIGHT, selfLoopFace(2))
        assertEquals(SelfLoopFace.LEFT, selfLoopFace(3))
        assertEquals(SelfLoopFace.TOP, selfLoopFace(4))
    }

    @Test
    fun `異なる ordinal の self-loop は弧の制御点もラベル位置も一致しない`() {
        val rect = Rect(x = 100.0, y = 100.0, width = 140.0, height = 48.0)
        val arcs = (0..3).map { arcOf(rect, it) }

        // どの 2 本を取っても弧 (制御点) が完全一致しない = 「label だけずらして curve は重複」ではない。
        for (i in arcs.indices) {
            for (j in i + 1 until arcs.size) {
                val ctrlSame = arcs[i].ctrl1X == arcs[j].ctrl1X && arcs[i].ctrl1Y == arcs[j].ctrl1Y &&
                    arcs[i].ctrl2X == arcs[j].ctrl2X && arcs[i].ctrl2Y == arcs[j].ctrl2Y
                assertTrue("ordinal $i と $j の弧が完全重複している", !ctrlSame)
                // ラベル中心も別点 (団子状に重なって背景が覆い合わない)。
                assertTrue(
                    "ordinal $i と $j のラベル中心が同一点",
                    dist(arcs[i].labelX, arcs[i].labelY, arcs[j].labelX, arcs[j].labelY) > 1f,
                )
            }
        }

        // 各面のラベルはノードの外側 (上/下/右/左) に出る。
        assertTrue(arcs[0].labelY < rect.y)
        assertTrue(arcs[1].labelY > rect.y + rect.height)
        assertTrue(arcs[2].labelX > rect.x + rect.width)
        assertTrue(arcs[3].labelX < rect.x)
    }

    @Test
    fun `同一面に複数の self-loop が並ぶ場合は面に沿って横へずれた耳になり弧が重ならない`() {
        val rect = Rect(x = 0.0, y = 0.0, width = 140.0, height = 48.0)
        val top0 = arcOf(rect, 0) // TOP 1 本目 (中央)
        val top4 = arcOf(rect, 4) // TOP 2 本目 (横へずれた耳)
        // 同じ TOP 面でも 2 本目は面に沿って横へずれ、弧が一致しない (重ねリングだと矢印・ラベルが密集する)。
        assertNotEquals(top0.ctrl1X, top4.ctrl1X)
        assertTrue("耳のラベルも横へずれる", dist(top0.labelX, top0.labelY, top4.labelX, top4.labelY) > 1f)
        // 耳の足 (start/end) はノードの上面の範囲内に留まる。
        assertTrue(top4.startX >= rect.x.toFloat() && top4.endX <= (rect.x + rect.width).toFloat())
    }

    @Test
    fun `selfLoops モデルは Relay に ENTER ACTION RECOVER の 3 self-loop を持つ`() {
        val graph = GraphLowering.lower(SampleModels.selfLoops())
        val loops = graph.edges.filter { it.fromId == NodeId.state("Relay") && it.toId == NodeId.state("Relay") }
        assertEquals(3, loops.size)
        // 描画順 = enter → action → recover。混在した種類がそのまま並ぶ。
        assertEquals(listOf(EdgeKind.ENTER, EdgeKind.ACTION, EdgeKind.RECOVER), loops.map { it.kind })
        // 通常の Relay -> Done エッジ (self ではない) は別に存在する。
        assertTrue(graph.edges.any { it.fromId == NodeId.state("Relay") && it.toId == NodeId.state("Done") && it.kind == EdgeKind.ACTION })
    }

    @Test
    fun `selfLoops の 3 種の self-loop は種別ごとに別グループになり配置が重ならない`() {
        val graph = GraphLowering.lower(SampleModels.selfLoops())
        val layout = LayeredLayout.layout(graph)
        val rect = layout.nodeRects[NodeId.state("Relay")]!!
        val ordinals = selfLoopGroupOrdinals(graph)
        // ENTER / ACTION / RECOVER の 3 種 = 3 グループ (弧 3 本)。
        val relayGroups = ordinals.filterKeys { it.first == NodeId.state("Relay") }
        assertEquals(setOf(0, 1, 2), relayGroups.values.toSet())

        val arcs = relayGroups.values.map { arcOf(rect, it) }
        // 3 本のラベル中心は互いに十分離れている (最小でもノード高さ程度)。
        for (i in arcs.indices) {
            for (j in i + 1 until arcs.size) {
                assertTrue(
                    "self-loop ラベルが接近しすぎ",
                    dist(arcs[i].labelX, arcs[i].labelY, arcs[j].labelX, arcs[j].labelY) > rect.height.toFloat(),
                )
            }
        }
    }

    @Test
    fun `wizard Step1 の同種 self-loop は 1 グループ = 1 本の弧に集約される`() {
        val graph = GraphLowering.lower(SampleModels.wizard())
        val ordinals = selfLoopGroupOrdinals(graph)
        val loops = graph.edges.filter { it.fromId == NodeId.state("Step1") && it.toId == NodeId.state("Step1") }
        // inputName (自己遷移) と next (stay) の 2 本 — どちらも ACTION なので弧は 1 本に集約され、
        // ラベルは複数行 1 枚になる (弧・矢印・ラベルの密集を避ける集約仕様)。
        assertEquals(2, loops.size)
        assertTrue(loops.all { it.kind == EdgeKind.ACTION })
        val step1Groups = ordinals.filterKeys { it.first == NodeId.state("Step1") }
        assertEquals(1, step1Groups.size)
        assertEquals(SelfLoopFace.TOP, selfLoopFace(step1Groups.values.single()))
    }
}
