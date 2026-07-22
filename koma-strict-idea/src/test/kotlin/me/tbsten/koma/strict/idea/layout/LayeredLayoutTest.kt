package me.tbsten.koma.strict.idea.layout

import me.tbsten.koma.strict.idea.SampleModels
import me.tbsten.koma.strict.idea.ir.CompositeBox
import me.tbsten.koma.strict.idea.ir.DiagramGraph
import me.tbsten.koma.strict.idea.ir.GraphLowering
import me.tbsten.koma.strict.idea.ir.NodeId
import me.tbsten.koma.strict.idea.ir.StartNode
import me.tbsten.koma.strict.idea.layout.layered.LayeredLayout
import me.tbsten.koma.strict.idea.model.ActionTrigger
import me.tbsten.koma.strict.idea.model.EnterTrigger
import me.tbsten.koma.strict.idea.model.GroupState
import me.tbsten.koma.strict.idea.model.LeafState
import me.tbsten.koma.strict.idea.model.Reachability
import me.tbsten.koma.strict.idea.model.RootState
import me.tbsten.koma.strict.idea.model.StateId
import me.tbsten.koma.strict.idea.model.StoreDiagramModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** BFS レイヤレイアウトの座標算出を素の JUnit で検証する。 */
class LayeredLayoutTest {

    @Test
    fun `全 SampleModels が lowering + layout を例外なく通り有限キャンバスになる`() {
        // preview 用の全サンプルを test からも実際に使う (unreachable / longNames / wizard の未使用化を防ぐ)
        // + lowering / layout が全サンプルで例外を投げず有限・正のキャンバスを返すことの広域スモーク。
        val samples = listOf(
            SampleModels.lce(), SampleModels.unreachable(), SampleModels.tabs(), SampleModels.feed(),
            SampleModels.feedBranch(), SampleModels.longNames(), SampleModels.wizard(), SampleModels.selfLoops(),
            SampleModels.auth(), SampleModels.settings(), SampleModels.session(), SampleModels.anyNamed(),
        )
        for (model in samples) {
            val graph = GraphLowering.lower(model)
            val layout = LayeredLayout.layout(graph)
            assertEquals(graph.nodes.size, layout.nodeRects.size)
            assertTrue(layout.canvasSize.width > 0.0 && layout.canvasSize.height > 0.0)
        }
    }

    @Test
    fun `LR では層が進むごとに x が増え同一層の兄弟は y で積まれる`() {
        val graph = GraphLowering.lower(SampleModels.lce())
        val layout = LayeredLayout.layout(graph, direction = LayoutDirection.LR)

        // 層: [*]=0, Loading=1, Content/Error=2。
        assertEquals(0, layout.layers[StartNode.START_ID])
        assertEquals(1, layout.layers[NodeId.state("Loading")])
        assertEquals(2, layout.layers[NodeId.state("Content")])
        assertEquals(2, layout.layers[NodeId.state("Error")])

        val start = layout.rect(StartNode.START_ID)!!
        val loading = layout.rect(NodeId.state("Loading"))!!
        val content = layout.rect(NodeId.state("Content"))!!
        val error = layout.rect(NodeId.state("Error"))!!

        assertTrue("start は Loading より左", start.x < loading.x)
        assertTrue("Loading は Content より左", loading.x < content.x)
        assertEquals("同一層は同じ x", content.x, error.x, 0.0001)
        assertTrue("同一層の兄弟は y が異なる", content.y != error.y)

        // start ノードは startSize の正方形。
        val cfg = LayoutConfig()
        assertEquals(cfg.startSize, start.width, 0.0001)
        assertEquals(cfg.startSize, start.height, 0.0001)

        assertTrue(layout.canvasSize.width > 0 && layout.canvasSize.height > 0)
    }

    @Test
    fun `TB では層が進むごとに y が増える`() {
        val graph = GraphLowering.lower(SampleModels.lce())
        val layout = LayeredLayout.layout(graph, direction = LayoutDirection.TB)

        val start = layout.rect(StartNode.START_ID)!!
        val loading = layout.rect(NodeId.state("Loading"))!!
        val content = layout.rect(NodeId.state("Content"))!!
        assertTrue("start は Loading より上", start.y < loading.y)
        assertTrue("Loading は Content より上", loading.y < content.y)
    }

    @Test
    fun `composite box は自分の member を内包する矩形になる`() {
        val graph = GraphLowering.lower(SampleModels.feed())
        val layout = LayeredLayout.layout(graph)

        val box = layout.compositeRects[NodeId.composite("Stable")]
        assertNotNull("Stable box の矩形があるはず", box)

        val members = listOf(NodeId.state("Stable", "Idle"), NodeId.state("Stable", "Refreshing"), NodeId.state("Stable", "LoadingMore"))
            .mapNotNull { layout.rect(it) }
        assertEquals(3, members.size)
        for (m in members) {
            assertTrue("box は member を左上で内包", box!!.x <= m.x && box.y <= m.y)
            assertTrue("box は member を右下で内包", box.right >= m.right && box.bottom >= m.bottom)
        }
    }

    @Test
    fun `composite box は member でないノード (Error) を内包しない`() {
        val graph = GraphLowering.lower(SampleModels.feed())
        val layout = LayeredLayout.layout(graph)

        val box = layout.compositeRects[NodeId.composite("Stable")]!!
        val error = layout.rect(NodeId.state("Error"))!!
        // Error は Stable の member ではないので Stable box と重ならない (押し出し済み)。
        val overlaps = error.x < box.right && error.right > box.x && error.y < box.bottom && error.bottom > box.y
        assertFalse("Error は Stable box の外にあるべき", overlaps)
    }

    @Test
    fun `入れ子 composite の上端左端は margin 以上に正規化され見切れない`() {
        // composite box は member を compositePadding 外へ膨らませるので、正規化しないと上端が負座標で
        // クリップされる (Stable ラベルが見切れる回帰)。全 rect が margin 以上から始まることを保証する。
        val graph = GraphLowering.lower(SampleModels.feed())
        val layout = LayeredLayout.layout(graph)
        val cfg = LayoutConfig()

        val box = layout.compositeRects[NodeId.composite("Stable")]!!
        assertTrue("Stable box の上端は margin 以上 (負座標で見切れない)", box.y >= cfg.margin - 0.0001)
        assertTrue("Stable box の左端は margin 以上", box.x >= cfg.margin - 0.0001)

        val all = layout.nodeRects.values + layout.compositeRects.values
        assertTrue(
            "node/composite の全 rect が margin 以上から始まる",
            all.all { it.x >= cfg.margin - 0.0001 && it.y >= cfg.margin - 0.0001 },
        )
    }

    @Test
    fun `Loading が複数 root leaf へ分岐しても押し出された Test と Error は重ならない`() {
        // Idle(group entry)・Error・Test が同一 layer/列に並び、押し出しで Error が box 下 (Test の位置)
        // へ落ちる。separateOverlaps がほどかないと 2 箱が隙間ゼロで重なる (feed-branch 回帰)。
        val graph = GraphLowering.lower(SampleModels.feedBranch())
        val layout = LayeredLayout.layout(graph)

        val test = layout.rect(NodeId.state("Test"))!!
        val error = layout.rect(NodeId.state("Error"))!!
        assertFalse("Test と Error は重ならない", test.intersects(error))

        // 一般化: どの 2 ノード矩形も重ならない (押し出し後の衝突が解消済み)。
        val rects = layout.nodeRects.values.toList()
        for (i in rects.indices) {
            for (j in i + 1 until rects.size) {
                assertFalse("node rect 同士は重ならない", rects[i].intersects(rects[j]))
            }
        }
    }

    @Test
    fun `2 段入れ子で各 composite は自分の member だけを内包し group any は箱の中に入る`() {
        val graph = GraphLowering.lower(SampleModels.settings())
        val layout = LayeredLayout.layout(graph)

        val loaded = layout.compositeRects[NodeId.composite("Loaded")]!!
        val general = layout.compositeRects[NodeId.composite("Loaded", "General")]!!

        // General box は Loaded box の中。
        assertTrue("General は Loaded の中", loaded.x <= general.x && loaded.y <= general.y && loaded.right >= general.right && loaded.bottom >= general.bottom)

        // any Loaded (group 共有擬似ノード) は Loaded box の中に配置される。
        val anyLoaded = layout.rect(NodeId.Any(StateId("Loaded")))!!
        assertTrue("any Loaded は Loaded box の中", anyLoaded.x >= loaded.x && anyLoaded.right <= loaded.right && anyLoaded.y >= loaded.y && anyLoaded.bottom <= loaded.bottom)

        // Loading (member でない) は Loaded box の外。
        val loading = layout.rect(NodeId.state("Loading"))!!
        val outside = !(loading.x < loaded.right && loading.right > loaded.x && loading.y < loaded.bottom && loading.bottom > loaded.y)
        assertTrue("Loading は Loaded box の外", outside)

        // Account は General の member ではないので General box の外。
        val account = layout.rect(NodeId.state("Loaded", "Account"))!!
        val accountOutGeneral = !(account.x < general.right && account.right > general.x && account.y < general.bottom && account.bottom > general.y)
        assertTrue("Account は General box の外", accountOutGeneral)
    }

    @Test
    fun `group を指す遷移があっても層マップは node id だけを持ち box は member を内包する`() {
        val graph = GraphLowering.lower(SampleModels.session())
        val layout = LayeredLayout.layout(graph)

        // group を指すエッジ (toId = "SignedIn") は非ノードなので、層マップに混ざらない。
        // 全ノードにちょうど層が割り当てられ、余分な id は無い。
        val nodeIds = graph.nodes.map { it.id }.toSet()
        assertEquals(nodeIds, layout.layers.keys)
        assertEquals(graph.nodes.size, layout.nodeRects.size)

        // group target のエッジ端点となる composite box 矩形が存在し、自分の member を内包する。
        val box = layout.compositeRects[NodeId.composite("SignedIn")]!!
        val home = layout.rect(NodeId.state("SignedIn", "Home"))!!
        val settings = layout.rect(NodeId.state("SignedIn", "Settings"))!!
        for (m in listOf(home, settings)) {
            assertTrue("box は member を内包", box.x <= m.x && box.y <= m.y && box.right >= m.right && box.bottom >= m.bottom)
        }

        // group edge の発生源 (SignedOut) は box の member ではないので box の外に置かれる。
        val signedOut = layout.rect(NodeId.state("SignedOut"))!!
        val overlaps = signedOut.x < box.right && signedOut.right > box.x && signedOut.y < box.bottom && signedOut.bottom > box.y
        assertFalse("SignedOut は SignedIn box の外にあるべき", overlaps)
    }

    @Test
    fun `自己ループのみのグラフでも層割り当てが停止する`() {
        // Search は自己遷移を持つ。any-state も stay 自己ループを持つ。無限ループしないこと。
        val graph = GraphLowering.lower(SampleModels.tabs())
        val layout = LayeredLayout.layout(graph)
        // すべてのノードに層が割り当てられている。
        assertEquals(graph.nodes.size, layout.layers.size)
        assertEquals(graph.nodes.size, layout.nodeRects.size)
    }

    // ---- final geometry invariant (P1-06 / P2-12) ----

    @Test
    fun `全 SampleModels で確定 composite rect は非 member ノードと交差しない`() {
        // P1-06: resolver が見た band と最終 box の食い違いで、非 member (settings の Loading 等) が
        // 確定 outer box と再交差していた。全サンプルを LR/TB 両方で回し、どの composite box も自分の
        // transitive member 以外のノードと交差しないことを確認する。start も全 box の非 member なので、
        // この不変条件が P2-12 (start が composite border を跨がない) も同時に押さえる。
        val samples = listOf(
            SampleModels.lce(), SampleModels.unreachable(), SampleModels.tabs(), SampleModels.feed(),
            SampleModels.feedBranch(), SampleModels.longNames(), SampleModels.wizard(), SampleModels.selfLoops(),
            SampleModels.auth(), SampleModels.settings(), SampleModels.session(), SampleModels.anyNamed(),
        )
        for (model in samples) {
            val graph = GraphLowering.lower(model)
            for (direction in LayoutDirection.entries) {
                val layout = LayeredLayout.layout(graph, direction)
                assertCompositesExcludeNonMembers(graph, layout, direction)
            }
        }
    }

    @Test
    fun `Outer-Inner-Leaf の外に root leaf があっても両 box は非 member を内包しない`() {
        // Outer -> Inner -> Leaf の 2 段入れ子 + box の外の root leaf (Outside)。Outside は box へ入る
        // 遷移で box 付近の層に来るが、確定した Outer/Inner box いずれとも交差してはならない (LR/TB)。
        val model = outerInnerOutside()
        val graph = GraphLowering.lower(model)
        for (direction in LayoutDirection.entries) {
            val layout = LayeredLayout.layout(graph, direction)
            val outer = layout.compositeRects[NodeId.composite("Outer")]!!
            val inner = layout.compositeRects[NodeId.composite("Outer", "Inner")]!!
            val outside = layout.rect(NodeId.state("Outside"))!!
            val deep = layout.rect(NodeId.state("Outer", "Inner", "Deep"))!!

            assertFalse("[$direction] Outside は Outer box の外", outside.intersects(outer))
            assertFalse("[$direction] Outside は Inner box の外", outside.intersects(inner))
            assertTrue(
                "[$direction] Inner box は Outer box の中",
                outer.x <= inner.x && outer.y <= inner.y && outer.right >= inner.right && outer.bottom >= inner.bottom,
            )
            assertTrue(
                "[$direction] Deep は Inner box の中",
                inner.x <= deep.x && inner.y <= deep.y && inner.right >= deep.right && inner.bottom >= deep.bottom,
            )
            assertCompositesExcludeNonMembers(graph, layout, direction)
        }
    }

    @Test
    fun `3 段入れ子でも各 box は親 box に包含され非 member を内包しない`() {
        val model = nestedSingleton3()
        val graph = GraphLowering.lower(model)
        for (direction in LayoutDirection.entries) {
            val layout = LayeredLayout.layout(graph, direction)
            val l1 = layout.compositeRects[NodeId.composite("L1")]!!
            val l2 = layout.compositeRects[NodeId.composite("L1", "L2")]!!
            val l3 = layout.compositeRects[NodeId.composite("L1", "L2", "L3")]!!
            // L1 ⊇ L2 ⊇ L3 (深い box ほど内側)。
            assertTrue("[$direction] L2 は L1 の中", l1.x <= l2.x && l1.y <= l2.y && l1.right >= l2.right && l1.bottom >= l2.bottom)
            assertTrue("[$direction] L3 は L2 の中", l2.x <= l3.x && l2.y <= l3.y && l2.right >= l3.right && l2.bottom >= l3.bottom)
            assertTrue("[$direction] 有限キャンバス", layout.canvasSize.width > 0.0 && layout.canvasSize.height > 0.0)
            assertCompositesExcludeNonMembers(graph, layout, direction)
        }
    }

    @Test
    fun `入れ子 box の周囲に root leaf が複数あっても box は非 member を内包しない`() {
        val model = boxSurroundedByLeaves()
        val graph = GraphLowering.lower(model)
        for (direction in LayoutDirection.entries) {
            val layout = LayeredLayout.layout(graph, direction)
            assertCompositesExcludeNonMembers(graph, layout, direction)
        }
    }

    @Test
    fun `start marker は初期状態が図の内側にある場合でも root frame の外に出る`() {
        // auth は initial (CheckingSession) が最左でなく図の内側にあるが、start は必ず root frame
        // (content bbox ± ROOT_FRAME_PAD) の外に置かれる。左右上下いずれかの辺の外側で可。
        for (model in listOf(SampleModels.auth(), SampleModels.lce(), SampleModels.session())) {
            val graph = GraphLowering.lower(model)
            for (direction in LayoutDirection.entries) {
                val layout = LayeredLayout.layout(graph, direction)
                val start = layout.rect(StartNode.START_ID) ?: continue
                val content = (layout.nodeRects - StartNode.START_ID).values + layout.compositeRects.values
                val pad = LayeredLayout.ROOT_FRAME_PAD
                val fl = content.minOf { it.x } - pad
                val ft = content.minOf { it.y } - pad
                val fr = content.maxOf { it.right } + pad
                val fb = content.maxOf { it.bottom } + pad
                // start の中心が frame の外側 (どれか 1 辺の外) にあること。
                val c = start.center
                val outside = c.x <= fl || c.x >= fr || c.y <= ft || c.y >= fb
                assertTrue("[$direction] ${model.root.simpleName} の start が root frame の外に出ていない", outside)
            }
        }
    }

    @Test
    fun `start marker は composite border を跨がない`() {
        // P2-12: session は initial が composite (SignedIn) 内の Home を指すため、start を寄せると
        // SignedIn box へ食い込んでいた。start と box が交差しないこと (LR/TB)。
        val graph = GraphLowering.lower(SampleModels.session())
        for (direction in LayoutDirection.entries) {
            val layout = LayeredLayout.layout(graph, direction)
            val start = layout.rect(StartNode.START_ID)!!
            val box = layout.compositeRects[NodeId.composite("SignedIn")]!!
            assertFalse("[$direction] start は SignedIn box の border を跨がない", start.intersects(box))
        }
    }

    @Test
    fun `loop 持ちノードの下には merged loop ラベルぶんのクリアランスが確保される`() {
        // 同一列に積まれた loop 持ちノード同士は、弧 (lift 32) + 複数行ラベル + 相手側の弧・ラベルが
        // 収まる縦の隙間が要る。従来は一律 siblingGap しか無く、ラベルのピル同士が覆い合っていた。
        val root = RootState(
            simpleName = "S",
            children = listOf(
                LeafState(
                    "A",
                    idOf("A"),
                    actions = listOf(
                        ActionTrigger("PingSomething", targets = emptyList(), stay = true, emits = listOf("NavigateSomewhereLongEnough")),
                        ActionTrigger("PongSomething", targets = emptyList(), stay = true, emits = listOf("NavigateOtherPlaceAlsoLong")),
                    ),
                ),
                LeafState(
                    "B",
                    idOf("B"),
                    actions = listOf(
                        ActionTrigger("Poke", targets = emptyList(), stay = true, emits = listOf("NavigateB")),
                    ),
                ),
            ),
        )
        val graph = GraphLowering.lower(modelOf(root, initial = listOf(idOf("A"), idOf("B"))))
        val layout = LayeredLayout.layout(graph)
        val a = layout.rect(NodeId.state("A"))!!
        val b = layout.rect(NodeId.state("B"))!!
        assertTrue("A と B は同一列 (x 重なり) の前提", a.x < b.right && a.right > b.x)
        val top = if (a.y < b.y) a else b
        val bottom = if (a.y < b.y) b else a
        val gap = bottom.y - top.bottom
        assertTrue(
            "loop 持ちノード同士の縦隙間 $gap は弧+複数行ラベルの所要 (>=120dp) を確保する",
            gap >= 120.0,
        )
    }

    // ---- helpers ----

    private fun idOf(vararg s: String) = StateId(*s)

    private fun modelOf(root: RootState, initial: List<StateId>): StoreDiagramModel =
        StoreDiagramModel(root = root, initial = initial, reachableLeafIds = Reachability.compute(root, initial))

    /** Node ids drawn inside [boxId], expanding nested box members (mirrors the layout's own logic). */
    private fun transitiveMembers(boxId: NodeId, byId: Map<NodeId, CompositeBox>): Set<NodeId> {
        val out = LinkedHashSet<NodeId>()
        fun visit(cur: NodeId) {
            val box = byId[cur]
            if (box == null) { out += cur; return }
            box.memberIds.forEach(::visit)
        }
        byId.getValue(boxId).memberIds.forEach(::visit)
        return out
    }

    /** Asserts every composite box rect is disjoint from every node that is not one of its members. */
    private fun assertCompositesExcludeNonMembers(graph: DiagramGraph, layout: GraphLayout, direction: LayoutDirection) {
        val byId = graph.composites.associateBy { it.id }
        for (box in graph.composites) {
            val boxRect = layout.compositeRects[box.id] ?: continue
            val members = transitiveMembers(box.id, byId)
            for (node in graph.nodes) {
                if (node.id in members) continue
                val nr = layout.rect(node.id) ?: continue
                assertFalse(
                    "[$direction] composite ${box.id} は非 member ${node.id} と交差してはならない",
                    boxRect.intersects(nr),
                )
            }
        }
    }

    /** Outer -> Inner -> Leaf (Deep) 2 段入れ子 + box 外の Outside root leaf。 */
    private fun outerInnerOutside(): StoreDiagramModel {
        val inner = GroupState(
            simpleName = "Inner",
            id = idOf("Outer", "Inner"),
            children = listOf(
                LeafState(
                    "Deep",
                    idOf("Outer", "Inner", "Deep"),
                    actions = listOf(ActionTrigger("Leave", targets = listOf(idOf("Outside")))),
                ),
            ),
        )
        val outer = GroupState(simpleName = "Outer", id = idOf("Outer"), children = listOf(inner))
        val root = RootState(
            simpleName = "S",
            children = listOf(
                LeafState(
                    "Outside",
                    idOf("Outside"),
                    enter = EnterTrigger(targets = listOf(idOf("Outer", "Inner", "Deep"))),
                ),
                outer,
            ),
        )
        return modelOf(root, initial = listOf(idOf("Outside")))
    }

    /** L1 { L2 { L3 { Leaf } } } の 3 段 singleton 入れ子 + 外部の Entry leaf。 */
    private fun nestedSingleton3(): StoreDiagramModel {
        val l3 = GroupState(
            simpleName = "L3",
            id = idOf("L1", "L2", "L3"),
            children = listOf(
                LeafState(
                    "Leaf",
                    idOf("L1", "L2", "L3", "Leaf"),
                    actions = listOf(ActionTrigger("Out", targets = listOf(idOf("Entry")))),
                ),
            ),
        )
        val l2 = GroupState(simpleName = "L2", id = idOf("L1", "L2"), children = listOf(l3))
        val l1 = GroupState(simpleName = "L1", id = idOf("L1"), children = listOf(l2))
        val root = RootState(
            simpleName = "S",
            children = listOf(
                LeafState(
                    "Entry",
                    idOf("Entry"),
                    enter = EnterTrigger(targets = listOf(idOf("L1", "L2", "L3", "Leaf"))),
                ),
                l1,
            ),
        )
        return modelOf(root, initial = listOf(idOf("Entry")))
    }

    /** 1 つの入れ子 box (A/B) の周囲に複数の root leaf を置き、押し出しの網羅性を見る。 */
    private fun boxSurroundedByLeaves(): StoreDiagramModel {
        val box = GroupState(
            simpleName = "Box",
            id = idOf("Box"),
            children = listOf(
                LeafState(
                    "A",
                    idOf("Box", "A"),
                    actions = listOf(ActionTrigger("Go", targets = listOf(idOf("Box", "B")))),
                ),
                LeafState(
                    "B",
                    idOf("Box", "B"),
                    actions = listOf(ActionTrigger("Out", targets = listOf(idOf("East")))),
                ),
            ),
        )
        val root = RootState(
            simpleName = "S",
            children = listOf(
                LeafState("North", idOf("North"), actions = listOf(ActionTrigger("Enter", targets = listOf(idOf("Box", "A"))))),
                LeafState("West", idOf("West"), enter = EnterTrigger(targets = listOf(idOf("Box", "A"), idOf("North")))),
                box,
                LeafState("East", idOf("East"), actions = listOf(ActionTrigger("Back", targets = listOf(idOf("Box", "A"))))),
                LeafState("South", idOf("South"), actions = listOf(ActionTrigger("Up", targets = listOf(idOf("Box", "B"))))),
            ),
        )
        return modelOf(root, initial = listOf(idOf("West")))
    }
}
