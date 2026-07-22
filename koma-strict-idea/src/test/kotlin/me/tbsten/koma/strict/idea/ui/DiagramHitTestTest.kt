package me.tbsten.koma.strict.idea.ui

import me.tbsten.koma.strict.idea.ir.AnyStateNode
import me.tbsten.koma.strict.idea.ir.GraphLowering
import me.tbsten.koma.strict.idea.ir.NodeId
import me.tbsten.koma.strict.idea.layout.layered.LayeredLayout
import me.tbsten.koma.strict.idea.model.ActionTrigger
import me.tbsten.koma.strict.idea.model.GroupState
import me.tbsten.koma.strict.idea.model.LeafState
import me.tbsten.koma.strict.idea.model.RootState
import me.tbsten.koma.strict.idea.model.SourceAnchor
import me.tbsten.koma.strict.idea.model.StateId
import me.tbsten.koma.strict.idea.model.StoreDiagramModel
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * 図のクリックヒットテスト ([hitSource]) を素の JUnit で検証する (クリックは静的 PNG に写らないので
 * 純ロジックで担保する)。leaf / any-state ノード / composite ラベル帯の 3 経路と帯限定を確認する。
 */
class DiagramHitTestTest {

    private val leafAnchor = object : SourceAnchor {}
    private val rootAnchor = object : SourceAnchor {}
    private val groupAnchor = object : SourceAnchor {}

    // root S { A(leaf), G(group){ Inner(leaf) } } + root 共有 RootShared + group 共有 GroupShared。
    private fun model(): StoreDiagramModel {
        val group = GroupState(
            simpleName = "G",
            id = StateId("G"),
            children = listOf(LeafState("Inner", StateId("G", "Inner"))),
            actions = listOf(ActionTrigger("GroupShared", targets = listOf(StateId("A")))),
            source = groupAnchor,
        )
        val root = RootState(
            simpleName = "S",
            children = listOf(
                LeafState(
                    "A",
                    StateId("A"),
                    actions = listOf(ActionTrigger("Go", targets = listOf(StateId("G", "Inner")))),
                    source = leafAnchor,
                ),
                group,
            ),
            actions = listOf(ActionTrigger("RootShared", targets = listOf(StateId("A")))),
            source = rootAnchor,
        )
        return StoreDiagramModel(root = root, initial = listOf(StateId("A")))
    }

    @Test
    fun `leaf と any-state ノードの矩形中心クリックはそのノードの source を返す`() {
        val graph = GraphLowering.lower(model())
        val layout = LayeredLayout.layout(graph)

        val a = layout.nodeRects[NodeId.state("A")]!!
        assertSame(leafAnchor, graph.hitSource(layout, a.center.x, a.center.y))

        val rootAny = layout.nodeRects[AnyStateNode.ROOT_ANY_ID]!!
        assertSame(rootAnchor, graph.hitSource(layout, rootAny.center.x, rootAny.center.y))

        val groupAny = layout.nodeRects[AnyStateNode.idFor(StateId("G"))]!!
        assertSame(groupAnchor, graph.hitSource(layout, groupAny.center.x, groupAny.center.y))
    }

    @Test
    fun `composite box はラベル帯だけが group source を返し内部は返さない`() {
        val graph = GraphLowering.lower(model())
        val layout = LayeredLayout.layout(graph)
        val box = layout.compositeRects[NodeId.composite("G")]!!

        // 上端のラベル帯 (box.y .. box.y + strip) は group 宣言へ。
        assertSame(groupAnchor, graph.hitSource(layout, box.x + 6.0, box.y + 4.0))

        // 帯より下・member でない左の余白は帯ではないので拾わない (帯限定 = 子ノードのクリックを奪わない)。
        assertNull(graph.hitSource(layout, box.x + 2.0, box.center.y))
    }

    @Test
    fun `どのノードにも当たらない座標は null`() {
        val graph = GraphLowering.lower(model())
        val layout = LayeredLayout.layout(graph)
        assertNull(graph.hitSource(layout, layout.canvasSize.width + 100.0, layout.canvasSize.height + 100.0))
    }
}
