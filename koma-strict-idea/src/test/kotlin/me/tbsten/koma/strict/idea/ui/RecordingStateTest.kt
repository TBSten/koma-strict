package me.tbsten.koma.strict.idea.ui

import me.tbsten.koma.strict.idea.ir.EdgeKind
import me.tbsten.koma.strict.idea.ir.GraphEdge
import me.tbsten.koma.strict.idea.ir.NodeId
import me.tbsten.koma.strict.idea.model.LeafState
import me.tbsten.koma.strict.idea.model.RootState
import me.tbsten.koma.strict.idea.model.StateId
import me.tbsten.koma.strict.idea.model.StoreDiagramModel
import me.tbsten.koma.strict.idea.ui.diagram.DiagramSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Click -> flow mapping (`ide-test-code.md`): only edges leaving the cursor are recorded. */
class RecordingStateTest {

    private val loading = StateId("Loading")
    private val error = StateId("Error")

    private val model = StoreDiagramModel(
        root = RootState(
            "FeedState",
            children = listOf(LeafState("Loading", loading), LeafState("Error", error)),
        ),
        initial = listOf(loading),
    )

    private fun edge(from: StateId, to: StateId, kind: EdgeKind, typeRef: String? = null) =
        DiagramSelection.Edge(GraphEdge(NodeId.State(from), NodeId.State(to), kind, trigger = "t", triggerTypeRef = typeRef))

    @Test
    fun `cursor から出るエッジは記録される`() {
        val s = RecordingState()
        s.record(edge(loading, error, EdgeKind.ENTER), model)
        assertEquals(1, s.flow.transitions.size)
        assertEquals(error, s.flow.transitions[0].target)
        assertEquals(error, s.flow.cursor) // カーソルが進む
    }

    @Test
    fun `cursor から出ないエッジは無視される`() {
        val s = RecordingState()
        s.record(edge(loading, error, EdgeKind.ENTER), model) // cursor -> Error
        s.record(edge(loading, error, EdgeKind.ACTION), model) // from=Loading != cursor(Error) -> 無視
        assertEquals(1, s.flow.transitions.size)
    }

    @Test
    fun `INITIAL エッジは記録されない`() {
        val s = RecordingState()
        s.record(DiagramSelection.Edge(GraphEdge(NodeId.Start, NodeId.State(loading), EdgeKind.INITIAL, trigger = "")), model)
        assertEquals(0, s.flow.transitions.size)
    }

    @Test
    fun `action の typeRef が transition に載る`() {
        val s = RecordingState()
        s.record(edge(loading, error, EdgeKind.ENTER), model) // -> Error
        s.record(edge(error, loading, EdgeKind.ACTION, typeRef = "FeedAction.Retry"), model)
        assertEquals("FeedAction.Retry", s.flow.transitions[1].typeRef)
    }

    @Test
    fun `Node タップはステップにならない`() {
        val s = RecordingState()
        s.record(DiagramSelection.Node(NodeId.State(loading)), model)
        assertEquals(0, s.flow.transitions.size)
    }

    @Test
    fun `initial 未設定なら model の initial が既定になる`() {
        val s = RecordingState()
        s.record(edge(loading, error, EdgeKind.ENTER), model)
        assertEquals(loading, s.flow.initial)
    }

    @Test
    fun `setInitial は flow をリセットする`() {
        val s = RecordingState()
        s.record(edge(loading, error, EdgeKind.ENTER), model)
        s.setInitial(error)
        assertEquals(error, s.flow.initial)
        assertEquals(0, s.flow.transitions.size)
        assertNull(s.flow.transitions.firstOrNull())
    }
}
