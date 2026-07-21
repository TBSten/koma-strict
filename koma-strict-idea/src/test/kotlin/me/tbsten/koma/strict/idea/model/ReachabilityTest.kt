package me.tbsten.koma.strict.idea.model

import me.tbsten.koma.strict.idea.SampleModels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 素の JUnit で到達不能分析(reachability)の純ロジックを検証する。 */
class ReachabilityTest {

    private fun id(vararg s: String) = StateId(*s)

    @Test
    fun `LCE は全 leaf が initial から到達可能`() {
        val model = SampleModels.lce()
        assertEquals(
            setOf(id("Loading"), id("Content"), id("Error")),
            model.reachableLeafIds,
        )
    }

    @Test
    fun `共有アクションの target は起点 leaf が到達可能なら到達可能になる`() {
        val model = SampleModels.tabs()
        // initial は Home のみだが、root 共有 SelectTab が Home/Search/Profile を到達可能にする。
        assertEquals(setOf(id("Home"), id("Search"), id("Profile")), model.reachableLeafIds)
    }

    @Test
    fun `中間 sealed をまたいで initial から全 leaf に到達できる`() {
        val model = SampleModels.feed()
        assertEquals(
            setOf(
                id("Loading"),
                id("Stable", "Idle"),
                id("Stable", "Refreshing"),
                id("Stable", "LoadingMore"),
                id("Error"),
            ),
            model.reachableLeafIds,
        )
    }

    @Test
    fun `どこからも遷移されない leaf は到達不能`() {
        val root = RootState(
            simpleName = "S",
            children = listOf(
                LeafState("Start", id("Start"), enter = EnterTrigger(targets = listOf(id("Middle")))),
                LeafState("Middle", id("Middle")),
                LeafState("Orphan", id("Orphan")),
            ),
        )
        val reachable = Reachability.compute(root, initial = listOf(id("Start")))
        assertTrue(id("Start") in reachable)
        assertTrue(id("Middle") in reachable)
        assertFalse("Orphan は到達不能のはず", id("Orphan") in reachable)
    }

    @Test
    fun `initial 未宣言なら到達不能分析はスキップされ空集合を返す`() {
        val root = SampleModels.lce().root
        assertTrue(Reachability.compute(root, initial = emptyList()).isEmpty())
    }
}
