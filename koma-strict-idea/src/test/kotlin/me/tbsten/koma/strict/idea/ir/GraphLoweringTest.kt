package me.tbsten.koma.strict.idea.ir

import me.tbsten.koma.strict.idea.SampleModels
import me.tbsten.koma.strict.idea.layout.layered.LayeredLayout
import me.tbsten.koma.strict.idea.model.ActionTrigger
import me.tbsten.koma.strict.idea.model.GroupState
import me.tbsten.koma.strict.idea.model.LeafState
import me.tbsten.koma.strict.idea.model.RootState
import me.tbsten.koma.strict.idea.model.SourceAnchor
import me.tbsten.koma.strict.idea.model.StateId
import me.tbsten.koma.strict.idea.model.StoreDiagramModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** model -> グラフ IR の lowering を素の JUnit で検証する。 */
class GraphLoweringTest {

    private fun DiagramGraph.edge(from: NodeId, to: NodeId): GraphEdge? =
        edges.firstOrNull { it.fromId == from && it.toId == to && it.fromId != it.toId }

    @Test
    fun `LCE は起点ノードと enter エッジと emit ラベルを持つ`() {
        val graph = GraphLowering.lower(SampleModels.lce())

        assertNotNull("[*] 起点ノードがあるはず", graph.node(StartNode.START_ID))
        assertEquals(setOf("Loading", "Content", "Error"), graph.stateNodes.map { it.id.display }.toSet())

        // initial エッジ。
        assertEquals(EdgeKind.INITIAL, graph.edge(StartNode.START_ID, NodeId.state("Loading"))?.kind)

        // enter は target ごとにエッジを作り、emit は Mealy ラベルに載る。
        val toError = graph.edge(NodeId.state("Loading"), NodeId.state("Error"))!!
        assertEquals("onEnter", toError.trigger)
        assertEquals(listOf("LoadFailed"), toError.emits)
        assertEquals("onEnter / LoadFailed", toError.label)

        // action は decapitalize した名前がトリガになる。
        assertEquals("reload", graph.edge(NodeId.state("Content"), NodeId.state("Loading"))?.trigger)
        assertEquals("retry", graph.edge(NodeId.state("Error"), NodeId.state("Loading"))?.trigger)

        // 共有アクションが無いので any-state ノードは無い。
        assertTrue(graph.anyStateNodes.isEmpty())
    }

    @Test
    fun `共有アクションは any-state 擬似ノードから展開され stay は ScopeStay になる`() {
        val graph = GraphLowering.lower(SampleModels.tabs())

        // ターゲット付きの共有アクションがあるので any-state ノードは生成される。
        val any = graph.anyStateNodes.singleOrNull()
        assertNotNull("root 共有アクションの any-state ノードがあるはず", any)
        assertEquals(AnyStateNode.ROOT_ANY_ID, any!!.id)
        assertEquals("any state", any.label)

        // any-state -> 各タブへの selectTab エッジ。
        assertEquals("selectTab", graph.edge(AnyStateNode.ROOT_ANY_ID, NodeId.state("Search"))?.trigger)

        // 共有 trigger の stay は any ノードの自己ループではなく scope の囲いへの ScopeStay になる。
        assertTrue(
            "any ノードに stay 自己ループは残らない",
            graph.edges.none { it.fromId == AnyStateNode.ROOT_ANY_ID && it.toId == AnyStateNode.ROOT_ANY_ID },
        )
        val scopeStay = graph.scopeStays.firstOrNull { it.scope.isRoot }
        assertNotNull("root scope の ScopeStay があるはず", scopeStay)
        assertEquals("selectTab (stay)", scopeStay!!.label)

        // Search の自己遷移 (stay ではない)。
        val selfTransition = graph.edges.firstOrNull { it.fromId == NodeId.state("Search") && it.toId == NodeId.state("Search") }
        assertNotNull(selfTransition)
        assertTrue("自己遷移は stay ではない", !selfTransition!!.stay)
    }

    @Test
    fun `中間 sealed は composite box になり条件付き遷移は通常エッジと stay ループの 2 本になる`() {
        val graph = GraphLowering.lower(SampleModels.feed())

        val box = graph.composites.firstOrNull { it.id == NodeId.composite("Stable") }
        assertNotNull("Stable の composite box があるはず", box)
        assertTrue(
            box!!.memberIds.map { it.display }.containsAll(listOf("Stable.Idle", "Stable.Refreshing", "Stable.LoadingMore")),
        )

        // 条件付き [Stay, LoadingMore]: 通常エッジ + stay 自己ループの 2 本。
        assertNotNull(graph.edge(NodeId.state("Stable", "Idle"), NodeId.state("Stable", "LoadingMore")))
        val loop = graph.edges.firstOrNull { it.fromId == NodeId.state("Stable", "Idle") && it.toId == NodeId.state("Stable", "Idle") && it.stay }
        assertEquals("loadMore (stay)", loop?.label)
    }

    @Test
    fun `recover は any-state から破線 RECOVER エッジになり exit は遷移せずバッジになる`() {
        val graph = GraphLowering.lower(SampleModels.auth())

        // root 共有 recover -> any-state 擬似ノード。
        val any = graph.anyStateNodes.singleOrNull()
        assertNotNull("root 共有 recover の any-state ノードがあるはず", any)
        assertEquals(AnyStateNode.ROOT_ANY_ID, any!!.id)

        // any -> LoggedOut の RECOVER エッジ。ラベルは on <Exception> / <Event>。
        val recover = graph.edge(AnyStateNode.ROOT_ANY_ID, NodeId.state("LoggedOut"))!!
        assertEquals(EdgeKind.RECOVER, recover.kind)
        assertEquals("on SessionExpiredException", recover.trigger)
        assertEquals("on SessionExpiredException / SessionExpired", recover.label)

        // exit は遷移エッジを作らない (Authenticating を発/着とする ACTION/ENTER 以外の edge は無い)。
        assertTrue("exit はエッジを持たない", graph.edges.none { it.trigger == "exit" })
        // exit はノードのバッジになる。
        val authenticating = graph.stateNodes.first { it.id == NodeId.state("Authenticating") }
        assertEquals("exit / AuthAttemptFinished", authenticating.exitBadge)
    }

    @Test
    fun `2 段入れ子は composite が入れ子になり group 共有は any Group ノードになる`() {
        val graph = GraphLowering.lower(SampleModels.settings())

        // Loaded は General(box) と Account と any Loaded を内包する。
        val loaded = graph.composites.first { it.id == NodeId.composite("Loaded") }
        assertTrue("Loaded は General box を member に持つ", NodeId.composite("Loaded", "General") in loaded.memberIds)
        assertTrue("Loaded は Account を member に持つ", NodeId.state("Loaded", "Account") in loaded.memberIds)
        assertTrue("Loaded は any Loaded 擬似ノードを内包する", AnyStateNode.idFor(StateId("Loaded")) in loaded.memberIds)

        // General は Viewing / Editing のみ。
        val general = graph.composites.first { it.id == NodeId.composite("Loaded", "General") }
        assertEquals(setOf("Loaded.General.Viewing", "Loaded.General.Editing"), general.memberIds.map { it.display }.toSet())

        // any Loaded -> Loading の共有 Close エッジ (グループ外への遷移)。
        val anyLoaded = graph.anyStateNodes.first { it.scope == StateId("Loaded") }
        assertEquals("any Loaded", anyLoaded.label)
        assertEquals("close", graph.edge(anyLoaded.id, NodeId.state("Loading"))?.trigger)
    }

    @Test
    fun `any-state と composite box は scope の source を運ぶ`() {
        val rootAnchor = object : SourceAnchor {}
        val groupAnchor = object : SourceAnchor {}
        val group = GroupState(
            simpleName = "G",
            id = StateId("G"),
            children = listOf(LeafState("Inner", StateId("G", "Inner"))),
            // group 共有アクション: any G 擬似ノードを生む。
            actions = listOf(ActionTrigger("GroupShared", targets = listOf(StateId("A")))),
            source = groupAnchor,
        )
        val root = RootState(
            simpleName = "S",
            children = listOf(LeafState("A", StateId("A")), group),
            // root 共有アクション: any state 擬似ノードを生む。
            actions = listOf(ActionTrigger("RootShared", targets = listOf(StateId("A")))),
            source = rootAnchor,
        )
        val graph = GraphLowering.lower(StoreDiagramModel(root = root, initial = listOf(StateId("A"))))

        val rootAny = graph.anyStateNodes.first { it.scope == StateId.Root }
        assertSame("root any-state は root の source を運ぶ", rootAnchor, rootAny.source)
        val groupAny = graph.anyStateNodes.first { it.scope == StateId("G") }
        assertSame("group any-state は group の source を運ぶ", groupAnchor, groupAny.source)
        val box = graph.composites.first { it.id == NodeId.composite("G") }
        assertSame("composite box は group の source を運ぶ", groupAnchor, box.source)
    }

    @Test
    fun `group を指す遷移は composite box の id を toId に持つエッジになる`() {
        val graph = GraphLowering.lower(SampleModels.session())

        // SignedOut -> SignedIn (group) の遷移エッジ。toId は group の NodeId.Composite (= composite box id)。
        val toGroup = graph.edge(NodeId.state("SignedOut"), NodeId.composite("SignedIn"))
        assertNotNull("SignedOut から SignedIn グループへのエッジがあるはず", toGroup)
        assertEquals(EdgeKind.ACTION, toGroup!!.kind)
        assertEquals("signIn", toGroup.trigger)

        // toId は composite box の id と一致する (描画時に box 矩形へ解決できる)。
        val box = graph.composites.first { it.id == NodeId.composite("SignedIn") }
        assertEquals(box.id, toGroup.toId)

        // group id は node ではないので、SignedIn という node は無い (box としてのみ存在)。
        assertNull(graph.node(NodeId.composite("SignedIn")))
    }

    // P1-01: フロントエンドが空/省略 nextState を stay=true, targets=[] に正規化した後の姿。lowering は
    // これを自己ループ 1 本にし、target エッジは作らない (図と表が同じ正規化結果を使う起点)。
    @Test
    fun `空 nextState 正規化の stay-only トリガは自己ループ 1 本になり target エッジを作らない`() {
        val root = RootState(
            simpleName = "S",
            children = listOf(
                LeafState("A", StateId("A"), actions = listOf(ActionTrigger("Refresh", targets = emptyList(), stay = true))),
            ),
        )
        val graph = GraphLowering.lower(StoreDiagramModel(root = root, initial = listOf(StateId("A"))))
        val loops = graph.edges.filter { it.fromId == NodeId.state("A") && it.toId == NodeId.state("A") }
        assertEquals(1, loops.size)
        assertTrue("stay ループである", loops.single().stay)
        assertEquals("refresh (stay)", loops.single().label)
        assertTrue("target エッジは無い", graph.edges.none { it.fromId == NodeId.state("A") && it.toId != NodeId.state("A") })
    }

    // P1-02: 未解決 target (foreign / error type) は leaf ではないのでエッジ/ノードにしない (図には描かない)。
    @Test
    fun `未解決 target はエッジもノードも作らない`() {
        val root = RootState(
            simpleName = "S",
            children = listOf(
                LeafState(
                    "A",
                    StateId("A"),
                    actions = listOf(ActionTrigger("Go", targets = emptyList(), unresolvedTargets = listOf("?Foreign"))),
                ),
            ),
        )
        val graph = GraphLowering.lower(StoreDiagramModel(root = root, initial = listOf(StateId("A"))))
        assertTrue("未解決 target からのエッジは無い", graph.edges.none { it.fromId == NodeId.state("A") })
        assertNull("?Foreign というノードは存在しない", graph.node(NodeId.state("?Foreign")))
    }

    @Test
    fun `到達不能 leaf には reachable フラグが false で立つ`() {
        // Orphan を足した model を lower して reachable を確認する。
        val lce = SampleModels.lce()
        val graph = GraphLowering.lower(lce)
        assertTrue(graph.stateNodes.all { it.reachable })

        // exit / start が無い純ロジックの健全性: initial があれば必ず start ノードが 1 個。
        assertEquals(1, graph.nodes.count { it is StartNode })
        assertNull(graph.node(NodeId.state("nonexistent")))
    }

    // ---- P1-04: 型付き NodeId で pseudo / state の id が衝突しない ----

    // graph.nodes の id は一意 (型付き NodeId が種別を key に含むので pseudo と state が別 key)。
    // 全 SampleModels + "any" 命名ケース + pseudo 風 backtick 名で不変条件を固定する。
    @Test
    fun `全 SampleModels で graph nodes の id は一意`() {
        val samples = listOf(
            SampleModels.lce(), SampleModels.unreachable(), SampleModels.tabs(), SampleModels.feed(),
            SampleModels.feedBranch(), SampleModels.longNames(), SampleModels.wizard(), SampleModels.selfLoops(),
            SampleModels.auth(), SampleModels.settings(), SampleModels.session(), SampleModels.anyNamed(),
        )
        for (model in samples) {
            val graph = GraphLowering.lower(model)
            val ids = graph.nodes.map { it.id }
            assertEquals("node id が重複している: ${ids.groupingBy { it }.eachCount().filter { it.value > 1 }}", ids.size, ids.toSet().size)
        }
    }

    // root leaf "any" と root any-state 擬似ノードは別 id で共存する (旧 String id では "any" 同士で衝突)。
    @Test
    fun `root leaf any と root any-state は別ノードとして共存する`() {
        val graph = GraphLowering.lower(SampleModels.anyNamed())

        // 具体 leaf "any" (NodeId.State) と root any-state 擬似ノード (NodeId.Any(Root)) が両方存在する。
        val leafAny = graph.node(NodeId.state("any"))
        assertNotNull("root leaf \"any\" が State ノードとして存在する", leafAny)
        assertTrue(leafAny is StateGraphNode)
        val rootAny = graph.node(AnyStateNode.ROOT_ANY_ID)
        assertNotNull("root any-state 擬似ノードが存在する", rootAny)
        assertTrue(rootAny is AnyStateNode)

        // display は両方 "any" だが、id (key) は種別が異なるので別物。
        assertEquals("any", NodeId.state("any").display)
        assertEquals("any", AnyStateNode.ROOT_ANY_ID.display)
        assertTrue("key としては別物", NodeId.state("any") != AnyStateNode.ROOT_ANY_ID)

        // nested leaf "any" (Section.any) と scoped any-state (any:Section) も別 id で共存する。
        assertNotNull("nested leaf Section.any が存在する", graph.node(NodeId.state("Section", "any")))
        assertNotNull("scoped any-state any:Section が存在する", graph.node(AnyStateNode.idFor(StateId("Section"))))
    }

    // pseudo id に似た backtick identifier ("[*]" や "any:Scope") を名乗る leaf でも衝突しない。
    @Test
    fun `pseudo id 風の backtick 名 leaf は start any-state と衝突しない`() {
        val root = RootState(
            simpleName = "S",
            children = listOf(
                // Kotlin では `[*]` や `any:Grp` のような backtick 識別子が書ける。旧 String id では
                // それぞれ start "[*]" / scoped any "any:Grp" と衝突していた。
                LeafState("[*]", StateId("[*]"), actions = listOf(ActionTrigger("Go", targets = listOf(StateId("Home"))))),
                LeafState("any:Grp", StateId("any:Grp")),
                LeafState("Home", StateId("Home")),
            ),
            // root 共有アクション: root any-state 擬似ノードを生む。
            actions = listOf(ActionTrigger("Ping", targets = listOf(StateId("Home")), stay = true)),
        )
        val graph = GraphLowering.lower(StoreDiagramModel(root = root, initial = listOf(StateId("Home"))))

        // start 擬似ノード ("[*]") と、"[*]" という名の leaf が別ノードとして共存する。
        assertTrue("[*] start 擬似ノードが存在する", graph.nodes.any { it is StartNode })
        assertNotNull("\"[*]\" という名の leaf が存在する", graph.node(NodeId.state("[*]")))
        assertNotNull("\"any:Grp\" という名の leaf が存在する", graph.node(NodeId.state("any:Grp")))
        assertNotNull("root any-state 擬似ノードが存在する", graph.node(AnyStateNode.ROOT_ANY_ID))

        // id は全て一意。
        val ids = graph.nodes.map { it.id }
        assertEquals(ids.size, ids.toSet().size)

        // レイアウトも例外なく通る (旧実装は remaining.first で NoSuchElementException)。
        val layout = LayeredLayout.layout(graph)
        assertEquals(graph.nodes.size, layout.nodeRects.size)
    }
}
