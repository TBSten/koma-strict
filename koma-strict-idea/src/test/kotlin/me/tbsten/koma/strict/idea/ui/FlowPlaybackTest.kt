package me.tbsten.koma.strict.idea.ui

import me.tbsten.koma.strict.idea.ir.EdgeKind
import me.tbsten.koma.strict.idea.ir.GraphLowering
import me.tbsten.koma.strict.idea.ir.NodeId
import me.tbsten.koma.strict.idea.model.ActionTrigger
import me.tbsten.koma.strict.idea.model.DiagramFlow
import me.tbsten.koma.strict.idea.model.DiagramFlowStep
import me.tbsten.koma.strict.idea.model.EnterTrigger
import me.tbsten.koma.strict.idea.model.LeafState
import me.tbsten.koma.strict.idea.model.RootState
import me.tbsten.koma.strict.idea.model.StateId
import me.tbsten.koma.strict.idea.model.StoreDiagramModel
import me.tbsten.koma.strict.idea.ui.diagram.DiagramSelection
import me.tbsten.koma.strict.idea.ui.diagram.flowFocusFrom
import me.tbsten.koma.strict.idea.ui.diagram.flowReveal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure flow-playback logic (`flows-design.md` IDE section): resolving a [DiagramFlow] to its ordered
 * reveal list ([flowReveal]) and building the exact-highlight focus set ([flowFocusFrom]). Playback is
 * interactive so it never lands in a static PNG — these fix the model→selection mapping as pure functions.
 */
class FlowPlaybackTest {

    // root R { A(enter -> B), B(Go -> A) }。A から enter で B、B から action Go で A に戻る往復。
    private fun flowModel(): StoreDiagramModel {
        val root = RootState(
            simpleName = "R",
            children = listOf(
                LeafState("A", StateId("A"), enter = EnterTrigger(targets = listOf(StateId("B")))),
                LeafState(
                    "B",
                    StateId("B"),
                    actions = listOf(ActionTrigger("Go", targets = listOf(StateId("A")), actionRef = "RAction.Go")),
                ),
            ),
        )
        return StoreDiagramModel(root = root, initial = listOf(StateId("A")))
    }

    @Test
    fun `flowReveal は node edge node の順で選択列を作る`() {
        val graph = GraphLowering.lower(flowModel())
        val a = NodeId.state("A")
        val b = NodeId.state("B")
        val enterAB = graph.edges.first { it.fromId == a && it.toId == b && it.kind == EdgeKind.ENTER }
        val goBA = graph.edges.first { it.fromId == b && it.toId == a && it.kind == EdgeKind.ACTION }

        val flow = DiagramFlow(
            name = "round trip",
            steps = listOf(
                DiagramFlowStep.Node(StateId("A")),
                DiagramFlowStep.Enter,
                DiagramFlowStep.Node(StateId("B")),
                DiagramFlowStep.Trigger("RAction.Go"),
                DiagramFlowStep.Node(StateId("A")),
            ),
        )

        assertEquals(
            listOf(
                DiagramSelection.Node(a),
                DiagramSelection.Edge(enterAB),
                DiagramSelection.Node(b),
                DiagramSelection.Edge(goBA),
                DiagramSelection.Node(a),
            ),
            graph.flowReveal(flow),
        )
    }

    @Test
    fun `flowReveal は解決できない edge ステップと Unresolved をスキップする`() {
        val graph = GraphLowering.lower(flowModel())
        // A から出る "Nonexistent.X" の edge は無い + Unresolved は飛ばす。node だけ残る。
        val flow = DiagramFlow(
            name = "broken",
            steps = listOf(
                DiagramFlowStep.Node(StateId("A")),
                DiagramFlowStep.Trigger("Nonexistent.X"),
                DiagramFlowStep.Node(StateId("B")),
                DiagramFlowStep.Unresolved,
            ),
        )
        assertEquals(
            listOf(DiagramSelection.Node(NodeId.state("A")), DiagramSelection.Node(NodeId.state("B"))),
            graph.flowReveal(flow),
        )
    }

    @Test
    fun `flowFocusFrom は revealed した要素だけを強調し edge の相手ノードは明るくしない`() {
        val graph = GraphLowering.lower(flowModel())
        val a = NodeId.state("A")
        val b = NodeId.state("B")
        val enterAB = graph.edges.first { it.fromId == a && it.toId == b && it.kind == EdgeKind.ENTER }

        // Node(A) と enter 矢印だけ revealed。相手ノード B はまだ revealed していない。
        val focus = flowFocusFrom(setOf(DiagramSelection.Node(a), DiagramSelection.Edge(enterAB)))

        // revealed 要素は tier1 (selected) かつ tier2 (focused) で強調。
        assertTrue(focus.isNodeSelected(a))
        assertTrue(focus.isNodeFocused(a))
        assertTrue(focus.isEdgeSelected(enterAB))
        assertTrue(focus.isEdgeFocused(enterAB))
        // 重要: focusFrom と違い、edge の端点 B は revealed していないので明るくしない (減光のまま)。
        assertFalse(focus.isNodeFocused(b))
    }

    @Test
    fun `flowFocusFrom(空) は全要素を非強調にする`() {
        val focus = flowFocusFrom(emptySet())
        assertFalse(focus.isNodeFocused(NodeId.state("A")))
        assertFalse(focus.isNodeSelected(NodeId.state("A")))
    }
}
