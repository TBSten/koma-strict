package me.tbsten.koma.strict.idea.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reachability rules behind the gutter "next state" markers (`ide-gutter.md`). */
class StateNavigationTest {

    // FeedState: root has a scope-shared @OnAction Reset -> Initial; Loading @OnEnter -> Error; Error @OnAction Retry -> Loading.
    private fun feedModel(): StoreDiagramModel {
        val initial = LeafState("Initial", StateId("Initial"))
        val loading = LeafState("Loading", StateId("Loading"), enter = EnterTrigger(targets = listOf(StateId("Error"))))
        val error = LeafState(
            "Error", StateId("Error"),
            actions = listOf(ActionTrigger(actionName = "Retry", targets = listOf(StateId("Loading")))),
        )
        val root = RootState(
            simpleName = "FeedState",
            children = listOf(initial, loading, error),
            actions = listOf(ActionTrigger(actionName = "Reset", targets = listOf(StateId("Initial")))),
        )
        return StoreDiagramModel(root = root)
    }

    @Test
    fun `own transitions come first, then inherited scope transitions`() {
        val out = feedModel().outgoingTransitionsByState()
        // Loading: 自前 enter -> Error、続いて root の scope action Reset -> Initial。
        assertEquals(
            listOf(
                NextStateTransition(StateId("Error"), "enter"),
                NextStateTransition(StateId("Initial"), "Reset Action"),
            ),
            out[StateId("Loading")],
        )
        // Error: 自前 Retry Action -> Loading、続いて継承の Reset Action -> Initial。
        assertEquals(
            listOf(
                NextStateTransition(StateId("Loading"), "Retry Action"),
                NextStateTransition(StateId("Initial"), "Reset Action"),
            ),
            out[StateId("Error")],
        )
    }

    @Test
    fun `root lists its own scope transitions, a plain leaf lists only inherited`() {
        val out = feedModel().outgoingTransitionsByState()
        assertEquals(listOf(NextStateTransition(StateId("Initial"), "Reset Action")), out[StateId.Root])
        // Initial は自前トリガ無し -> 継承分だけ。
        assertEquals(listOf(NextStateTransition(StateId("Initial"), "Reset Action")), out[StateId("Initial")])
    }

    @Test
    fun `a state with no reachable next state is absent (no gutter icon)`() {
        // トリガがどこにも無いモデル: どの state も outgoing 無し。
        val a = LeafState("A", StateId("A"))
        val b = LeafState("B", StateId("B"))
        val out = StoreDiagramModel(root = RootState("S", listOf(a, b))).outgoingTransitionsByState()
        assertFalse(out.containsKey(StateId("A")))
        assertTrue(out.isEmpty())
    }

    @Test
    fun `nested group scope transitions are inherited by its leaves`() {
        val idle = LeafState("Idle", StateId("Stable", "Idle"))
        val stable = GroupState(
            "Stable", StateId("Stable"), children = listOf(idle),
            actions = listOf(ActionTrigger(actionName = "Refresh", targets = listOf(StateId("Stable", "Idle")))),
        )
        val out = StoreDiagramModel(root = RootState("FeedState", listOf(stable))).outgoingTransitionsByState()
        // Idle は group Stable の scope action Refresh を継承する。
        assertEquals(listOf(NextStateTransition(StateId("Stable", "Idle"), "Refresh Action")), out[StateId("Stable", "Idle")])
        // group 自身も own として持つ。
        assertEquals(listOf(NextStateTransition(StateId("Stable", "Idle"), "Refresh Action")), out[StateId("Stable")])
    }
}
