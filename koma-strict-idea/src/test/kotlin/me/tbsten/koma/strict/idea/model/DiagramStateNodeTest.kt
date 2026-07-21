package me.tbsten.koma.strict.idea.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 状態木の走査 (walk) と未解決判定 (hasUnresolvedDeclarations) の純ロジックを素の JUnit で検証する。 */
class DiagramStateNodeTest {

    @Test
    fun `walk は pre-order で親を先に 子を宣言順に辿る`() {
        val root = RootState(
            simpleName = "R",
            children = listOf(
                GroupState(
                    "G",
                    StateId("G"),
                    children = listOf(
                        LeafState("G1", StateId("G", "G1")),
                        LeafState("G2", StateId("G", "G2")),
                    ),
                ),
                LeafState("L", StateId("L")),
            ),
        )
        assertEquals(
            listOf("R", "G", "G1", "G2", "L"),
            root.walk().map { it.simpleName }.toList(),
        )
    }

    // P2-14: 再帰 walk なら StackOverflow する深さでも iterative walk は完走する。
    @Test
    fun `深い入れ子でも walk は StackOverflow せず全ノードを辿る`() {
        val depth = 20_000
        var node: DiagramStateNode = LeafState("leaf", StateId("leaf"))
        for (i in depth downTo 1) {
            node = GroupState("g$i", StateId("g$i"), children = listOf(node))
        }
        val root = RootState(simpleName = "R", children = listOf(node))
        // 例外なく完走し、node 総数 = root(1) + group(depth) + leaf(1) を数えられること。
        assertEquals(depth + 2, root.walk().count())
    }

    @Test
    fun `hasUnresolvedDeclarations は未解決 target emit 型引数を検出し 解決済みは false`() {
        val resolved = LeafState(
            "A",
            StateId("A"),
            actions = listOf(ActionTrigger("Go", targets = listOf(StateId("B")), emits = listOf("Evt"))),
        )
        assertFalse("解決済みは未解決なし", resolved.hasUnresolvedDeclarations())

        val unresolvedTarget = LeafState(
            "A",
            StateId("A"),
            actions = listOf(ActionTrigger("Go", targets = emptyList(), unresolvedTargets = listOf("?Foreign"))),
        )
        assertTrue("未解決 target を検出", unresolvedTarget.hasUnresolvedDeclarations())

        val unresolvedEmit = LeafState(
            "A",
            StateId("A"),
            actions = listOf(ActionTrigger("Go", targets = listOf(StateId("B")), emits = listOf(UNRESOLVED_MARKER))),
        )
        assertTrue("未解決 emit を検出", unresolvedEmit.hasUnresolvedDeclarations())

        val unresolvedTypeArg = LeafState(
            "A",
            StateId("A"),
            actions = listOf(ActionTrigger(UNRESOLVED_MARKER, targets = listOf(StateId("B")))),
        )
        assertTrue("未解決型引数 (actionName = ?) を検出", unresolvedTypeArg.hasUnresolvedDeclarations())
    }
}
