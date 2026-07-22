package me.tbsten.koma.strict.idea.ui

import me.tbsten.koma.strict.idea.ir.DiagramGraph
import me.tbsten.koma.strict.idea.ir.EdgeKind
import me.tbsten.koma.strict.idea.ir.GraphEdge
import me.tbsten.koma.strict.idea.ir.GraphLowering
import me.tbsten.koma.strict.idea.ir.NodeId
import me.tbsten.koma.strict.idea.layout.EdgeRouting
import me.tbsten.koma.strict.idea.layout.GraphLayout
import me.tbsten.koma.strict.idea.layout.LayeredLayout
import me.tbsten.koma.strict.idea.layout.LayoutDirection
import me.tbsten.koma.strict.idea.layout.Point
import me.tbsten.koma.strict.idea.layout.Size
import me.tbsten.koma.strict.idea.model.ActionTrigger
import me.tbsten.koma.strict.idea.model.GroupState
import me.tbsten.koma.strict.idea.model.LeafState
import me.tbsten.koma.strict.idea.model.RootState
import me.tbsten.koma.strict.idea.model.StateId
import me.tbsten.koma.strict.idea.model.StoreDiagramModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 図のフォーカス選択の純ロジック ([focusFrom] / [hitElement]) を素の JUnit で検証する。フォーカスは
 * interactive なので静的 PNG に写らない — フォーカス集合の計算とエッジ当たり判定 (折れ線距離) を純関数
 * として固める (`ide-2.md`)。
 */
class DiagramFocusTest {

    // root R { A(Go->B), B(Back->A, Loop stay), C(ToB->B) } + root 共有 Reset(stay)。initial=A。
    // A から見ると C は入出力エッジの相手ではないので focus から外れる関係を作る。
    private fun abcModel(): StoreDiagramModel {
        val root = RootState(
            simpleName = "R",
            children = listOf(
                LeafState("A", StateId("A"), actions = listOf(ActionTrigger("Go", targets = listOf(StateId("B"))))),
                LeafState(
                    "B",
                    StateId("B"),
                    actions = listOf(
                        ActionTrigger("Back", targets = listOf(StateId("A"))),
                        ActionTrigger("Loop", targets = listOf(StateId("B")), stay = true),
                    ),
                ),
                LeafState("C", StateId("C"), actions = listOf(ActionTrigger("ToB", targets = listOf(StateId("B"))))),
            ),
            actions = listOf(ActionTrigger("Reset", targets = emptyList(), stay = true)),
        )
        return StoreDiagramModel(root = root, initial = listOf(StateId("A")))
    }

    private fun edge(graph: DiagramGraph, from: NodeId, to: NodeId): GraphEdge =
        graph.edges.first { it.fromId == from && it.toId == to }

    @Test
    fun `nextSelection のタップ意味論 (ide-3)`() {
        val a = DiagramSelection.Node(NodeId.state("A"))
        val b = DiagramSelection.Node(NodeId.state("B"))
        // 通常クリックは単一選択に置換。
        assertEquals(setOf(a), nextSelection(setOf(b), a, shift = false, clickedEmpty = false))
        // 空クリック (何も navigable でない) で全解除。
        assertEquals(emptySet<DiagramSelection>(), nextSelection(setOf(a, b), null, shift = false, clickedEmpty = true))
        // navigable だが focus 対象でない (hit=null かつ clickedEmpty=false) は選択維持。
        assertEquals(setOf(a), nextSelection(setOf(a), null, shift = false, clickedEmpty = false))
        // Shift はトグル: 未選択なら追加、既選択なら除去。
        assertEquals(setOf(a, b), nextSelection(setOf(a), b, shift = true, clickedEmpty = false))
        assertEquals(setOf(b), nextSelection(setOf(a, b), a, shift = true, clickedEmpty = false))
        // Shift + 空クリックは維持 (誤解除しない)。
        assertEquals(setOf(a), nextSelection(setOf(a), null, shift = true, clickedEmpty = true))
    }

    @Test
    fun `node 選択で入出力エッジとその相手ノードだけが focus に入る`() {
        val graph = GraphLowering.lower(abcModel())
        val a = NodeId.state("A")
        val b = NodeId.state("B")
        val c = NodeId.state("C")

        val focus = graph.focusFrom(DiagramSelection.Node(a))

        // A 自身・入出力エッジの相手 (B / Start) は focus。無関係な C は外れる。
        assertTrue(focus.isNodeFocused(a))
        assertTrue(focus.isNodeFocused(b))
        assertTrue(focus.isNodeFocused(NodeId.Start))
        assertFalse(focus.isNodeFocused(c))

        // A に接続するエッジ (Go A->B / Back B->A) は focus、C->B や B の self-loop は外れる。
        assertTrue(focus.isEdgeFocused(edge(graph, a, b)))
        assertTrue(focus.isEdgeFocused(edge(graph, b, a)))
        assertFalse(focus.isEdgeFocused(edge(graph, c, b)))
        assertFalse(focus.isEdgeFocused(edge(graph, b, b)))
    }

    @Test
    fun `node 選択は自ノードの self-loop と所属 scope の scope-stay を含む`() {
        val graph = GraphLowering.lower(abcModel())
        val b = NodeId.state("B")

        val focus = graph.focusFrom(DiagramSelection.Node(b))

        // B の self-loop (Loop) は B に接続するので focus。相手 C (C->B) も focus に入る。
        assertTrue(focus.isEdgeFocused(edge(graph, b, b)))
        assertTrue(focus.isNodeFocused(NodeId.state("C")))
        // B は initial ではないので Start は入らない。
        assertFalse(focus.isNodeFocused(NodeId.Start))
        // root 共有 stay は全 state で発火するので B 選択でも focus。
        assertTrue(focus.isScopeStayFocused(graph.scopeStays.first()))
    }

    @Test
    fun `edge 選択でそのエッジと前後の2ノードだけが focus に入る`() {
        val graph = GraphLowering.lower(abcModel())
        val a = NodeId.state("A")
        val b = NodeId.state("B")
        val goAB = edge(graph, a, b)

        val focus = graph.focusFrom(DiagramSelection.Edge(goAB))

        assertTrue(focus.isEdgeFocused(goAB))
        assertTrue(focus.isNodeFocused(a))
        assertTrue(focus.isNodeFocused(b))
        assertFalse(focus.isNodeFocused(NodeId.state("C")))
        assertFalse(focus.isNodeFocused(NodeId.Start))
        // edge 選択では相手側の逆エッジや scope-stay は含めない。
        assertFalse(focus.isEdgeFocused(edge(graph, b, a)))
        assertFalse(focus.isScopeStayFocused(graph.scopeStays.first()))
    }

    // root R { G(group){ X } + G 共有 GKeep(stay), Y } + root 共有 RKeep(stay)。
    private fun groupScopeModel(): StoreDiagramModel {
        val group = GroupState(
            simpleName = "G",
            id = StateId("G"),
            children = listOf(LeafState("X", StateId("G", "X"))),
            actions = listOf(ActionTrigger("GKeep", targets = emptyList(), stay = true)),
        )
        val root = RootState(
            simpleName = "R",
            children = listOf(group, LeafState("Y", StateId("Y"))),
            actions = listOf(ActionTrigger("RKeep", targets = emptyList(), stay = true)),
        )
        return StoreDiagramModel(root = root, initial = listOf(StateId("G", "X")))
    }

    @Test
    fun `scope-stay は選択ノードを含む scope のものだけが focus に入る`() {
        val graph = GraphLowering.lower(groupScopeModel())
        val gKeep = graph.scopeStays.first { it.scope == StateId("G") }
        val rKeep = graph.scopeStays.first { it.scope.isRoot }

        // group 内の X を選ぶと root 共有・group 共有の両 stay が focus。
        val focusX = graph.focusFrom(DiagramSelection.Node(NodeId.state("G", "X")))
        assertTrue(focusX.isScopeStayFocused(gKeep))
        assertTrue(focusX.isScopeStayFocused(rKeep))

        // group 外の Y を選ぶと root 共有だけ focus、group 共有は外れる。
        val focusY = graph.focusFrom(DiagramSelection.Node(NodeId.state("Y")))
        assertFalse(focusY.isScopeStayFocused(gKeep))
        assertTrue(focusY.isScopeStayFocused(rKeep))
    }

    // root R { G(group){ In1(Go->Out) }, Out }。In1 -> Out はグループ外へ出るエッジ。
    private fun groupEdgeModel(): StoreDiagramModel {
        val group = GroupState(
            simpleName = "G",
            id = StateId("G"),
            children = listOf(
                LeafState("In1", StateId("G", "In1"), actions = listOf(ActionTrigger("Go", targets = listOf(StateId("Out"))))),
            ),
        )
        val root = RootState(
            simpleName = "R",
            children = listOf(group, LeafState("Out", StateId("Out"))),
        )
        return StoreDiagramModel(root = root, initial = listOf(StateId("G", "In1")))
    }

    @Test
    fun `選択そのものは selected に記録され 隣接は selected には入らない (ide-3)`() {
        val graph = GraphLowering.lower(abcModel())
        val a = NodeId.state("A")
        val b = NodeId.state("B")

        val nodeFocus = graph.focusFrom(DiagramSelection.Node(a))
        // A は selected (tier1)、隣接 B は focused だが selected ではない。
        assertTrue(nodeFocus.isNodeSelected(a))
        assertFalse(nodeFocus.isNodeSelected(b))
        assertTrue(nodeFocus.isNodeFocused(b))

        val goAB = edge(graph, a, b)
        val edgeFocus = graph.focusFrom(DiagramSelection.Edge(goAB))
        assertTrue(edgeFocus.isEdgeSelected(goAB))
        assertFalse(edgeFocus.isNodeSelected(a))
    }

    @Test
    fun `nest state 選択で配下 State と直結エッジが focus・外の相手ノードは外れる (ide-3)`() {
        val graph = GraphLowering.lower(groupEdgeModel())
        val g = NodeId.Composite(StateId("G"))
        val in1 = NodeId.state("G", "In1")
        val out = NodeId.state("Out")

        val focus = graph.focusFrom(DiagramSelection.Composite(g))

        // composite 自身は selected + focused、配下 In1 は focus、直結エッジ In1->Out も focus。
        assertTrue(focus.isCompositeSelected(g))
        assertTrue(focus.isCompositeFocused(g))
        assertTrue(focus.isNodeFocused(in1))
        assertTrue(focus.isEdgeFocused(edge(graph, in1, out)))
        // グループ外へ出る相手ノード Out は focus に入らない (薄いまま) = 素直な仕様解釈。
        assertFalse(focus.isNodeFocused(out))
    }

    @Test
    fun `scope-stay 選択でそのスコープの State と composite が focus に入る (ide-3)`() {
        val graph = GraphLowering.lower(groupScopeModel())
        val gKeep = graph.scopeStays.first { it.scope == StateId("G") }

        val focus = graph.focusFrom(DiagramSelection.Stay(gKeep))

        assertTrue(focus.isStaySelected(gKeep))
        assertTrue(focus.isScopeStayFocused(gKeep))
        // G スコープの member (X) と composite G が focus。
        assertTrue(focus.isNodeFocused(NodeId.state("G", "X")))
        assertTrue(focus.isCompositeFocused(NodeId.Composite(StateId("G"))))
    }

    @Test
    fun `複数選択は各 focus 集合の和になる (ide-3)`() {
        val graph = GraphLowering.lower(abcModel())
        val a = NodeId.state("A")
        val c = NodeId.state("C")
        val b = NodeId.state("B")

        val focus = graph.focusFrom(setOf(DiagramSelection.Node(a), DiagramSelection.Node(c)))

        // A・C の両方が selected、両者の隣接 B は focus (和集合)。
        assertTrue(focus.isNodeSelected(a))
        assertTrue(focus.isNodeSelected(c))
        assertTrue(focus.isNodeFocused(b))
        assertTrue(focus.isEdgeFocused(edge(graph, a, b)))
        assertTrue(focus.isEdgeFocused(edge(graph, c, b)))
    }

    @Test
    fun `hitElement は composite ラベル帯で Composite を返す (ide-3)`() {
        val graph = GraphLowering.lower(groupEdgeModel())
        val layout = LayeredLayout.layout(graph)
        val routes = EdgeRouting.routeAll(graph, layout)
        val g = NodeId.Composite(StateId("G"))
        val box = layout.compositeRects[g]!!

        // 箱の上端ラベル帯 (strip) 内をクリック -> Composite 選択。
        val hit = graph.hitElement(layout, routes, box.x + 5.0, box.y + 3.0)
        assertEquals(DiagramSelection.Composite(g), hit)
    }

    @Test
    fun `hitElement はノード矩形中心でそのノードを返し 図の外では null`() {
        val graph = GraphLowering.lower(abcModel())
        val layout = LayeredLayout.layout(graph)
        val routes = EdgeRouting.routeAll(graph, layout)

        val a = layout.nodeRects[NodeId.state("A")]!!
        assertEquals(DiagramSelection.Node(NodeId.state("A")), graph.hitElement(layout, routes, a.center.x, a.center.y))

        assertNull(
            graph.hitElement(layout, routes, layout.canvasSize.width + 200.0, layout.canvasSize.height + 200.0),
        )
    }

    @Test
    fun `hitElement は斜め線 go-around 折れ線の線上だけをヒットとする`() {
        // node 矩形を持たない layout + 手組みの折れ線ルートで、当たり判定を幾何的に固定する。
        val e = GraphEdge(NodeId.state("A"), NodeId.state("B"), EdgeKind.ACTION, "go")
        val graph = DiagramGraph(nodes = emptyList(), edges = listOf(e))
        val layout = GraphLayout(
            direction = LayoutDirection.LR,
            layers = emptyMap(),
            nodeRects = emptyMap(),
            compositeRects = emptyMap(),
            canvasSize = Size(0.0, 0.0),
        )
        // seg1: 斜め (0,0)->(80,80)、seg2: 水平 (80,80)->(160,80) の go-around 風折れ線。
        val routes = mapOf(e to listOf(Point(0.0, 0.0), Point(80.0, 80.0), Point(160.0, 80.0)))
        val sel = DiagramSelection.Edge(e)

        // 各区間の線上はヒット。折れ (bend) 点もヒット。
        assertEquals(sel, graph.hitElement(layout, routes, 40.0, 40.0))
        assertEquals(sel, graph.hitElement(layout, routes, 120.0, 80.0))
        assertEquals(sel, graph.hitElement(layout, routes, 80.0, 80.0))

        // 斜め線から数 px (< 6) 以内はヒット、大きく離れると非ヒット。
        assertEquals(sel, graph.hitElement(layout, routes, 40.0 - 2.8, 40.0 + 2.8))
        assertNull(graph.hitElement(layout, routes, 40.0 - 10.6, 40.0 + 10.6))
        assertNull(graph.hitElement(layout, routes, 300.0, 300.0))
    }
}
