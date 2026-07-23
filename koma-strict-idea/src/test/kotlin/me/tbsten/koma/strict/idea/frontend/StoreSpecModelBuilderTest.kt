package me.tbsten.koma.strict.idea.frontend

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import me.tbsten.koma.strict.idea.ir.AnyStateNode
import me.tbsten.koma.strict.idea.ir.GraphLowering
import me.tbsten.koma.strict.idea.ir.NodeId
import me.tbsten.koma.strict.idea.ir.StartNode
import me.tbsten.koma.strict.idea.layout.layered.LayeredLayout
import me.tbsten.koma.strict.idea.model.DiagramFlowStep
import me.tbsten.koma.strict.idea.model.GroupState
import me.tbsten.koma.strict.idea.model.SourceAnchor
import me.tbsten.koma.strict.idea.model.StateId
import me.tbsten.koma.strict.idea.model.StoreDiagramModel
import me.tbsten.koma.strict.idea.model.findById
import me.tbsten.koma.strict.idea.ui.diagram.hitSource
import me.tbsten.koma.strict.idea.model.transitionRows
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile

/**
 * Headless functional test of the Analysis-API frontend (`ide.dev.md` main loop): open a fixture
 * `.kt`, build the slim model, and assert the model / IR / layout / navigation target.
 */
@OptIn(KaAllowAnalysisOnEdt::class)
internal class StoreSpecModelBuilderTest : BasePlatformTestCase() {

    @Throws(Exception::class)
    override fun tearDown() = ignoreUnrelatedLoggedErrors { super.tearDown() }

    private fun buildFrom(
        source: String,
        addStub: Boolean = true,
        extraFiles: Map<String, String> = emptyMap(),
    ): Pair<StoreDiagramModel, KtFile> {
        if (addStub) myFixture.addFileToProject("me/tbsten/koma/strict/Annotations.kt", KOMA_STRICT_STUB)
        extraFiles.forEach { (path, text) -> myFixture.addFileToProject(path, text) }
        val ktFile = myFixture.configureByText("Store.kt", source) as KtFile
        val model = runReadActionBlocking {
            allowAnalysisOnEdt {
                val root = StoreSpecModelBuilder.findStoreSpecClasses(ktFile).first()
                StoreSpecModelBuilder.build(root)
            }
        }
        return model to ktFile
    }

    // @StoreSpec の状態木・遷移・emit・到達可能性が AA で構築できる。
    fun testLceModelIsFullyResolved() {
        val (model, _) = buildFrom(LCE_SRC)

        assertEquals("LceState", model.root.simpleName)
        assertEquals(setOf("Loading", "Content", "Error"), model.leaves.map { it.simpleName }.toSet())
        assertFalse("解析成功なので degraded ではない", model.degraded)

        assertTrue(StateId("Loading") in model.initial)

        val loading = model.leaf(StateId("Loading"))!!
        assertEquals(setOf(StateId("Content"), StateId("Error")), loading.enter!!.targets.toSet())
        assertEquals(listOf("LoadFailed"), loading.enter.emits)

        val content = model.leaf(StateId("Content"))!!
        assertEquals("Reload", content.actions.single().actionName)
        assertEquals(listOf(StateId("Loading")), content.actions.single().targets)

        // 全 leaf が到達可能。
        assertEquals(model.leaves.map { it.id }.toSet(), model.reachableLeafIds)
    }

    // @FlowSpec を付けた注釈クラスを root に適用した flow が model.flows に読み込まれ、
    // steps が Node / Enter / Trigger に分類される (flows-design.md IDE 読取)。name は @FlowSpec(name) 優先、
    // 省略時は注釈クラス名。宣言が別ファイル (XxxState.flows.kt) でも findClass で解決できることも兼ねる。
    fun testFlowsAreReadFromRootAnnotations() {
        val (model, _) = buildFrom(
            FLOW_DEMO_SRC,
            extraFiles = mapOf("FlowDemoState.flows.kt" to FLOW_DEMO_FLOWS_SRC),
        )
        assertFalse("解析成功", model.degraded)

        val byName = model.flows.associateBy { it.name }
        assertEquals(setOf("initialize happy path", "RetryFlow"), byName.keys)

        // name は @FlowSpec(name) を優先。steps は node / enter に分類。
        assertEquals(
            listOf(
                DiagramFlowStep.Node(StateId("Loading")),
                DiagramFlowStep.Enter,
                DiagramFlowStep.Node(StateId("Content")),
            ),
            byName.getValue("initialize happy path").steps,
        )
        // name 省略 → 注釈クラス名。action ステップは Trigger(relativeClassName)、EdgeKind は区別しない。
        assertEquals(
            listOf(
                DiagramFlowStep.Node(StateId("Error")),
                DiagramFlowStep.Trigger("FlowDemoAction.Retry"),
                DiagramFlowStep.Node(StateId("Loading")),
            ),
            byName.getValue("RetryFlow").steps,
        )
    }

    // @FlowSpec の無いストアは flows 空。
    fun testStoreWithoutFlowsHasEmptyFlows() {
        val (model, _) = buildFrom(LCE_SRC)
        assertTrue("flow 宣言が無ければ空", model.flows.isEmpty())
    }

    // lowering / layout も実 model から通ることをスモーク確認する。
    fun testLceLoweringAndLayout() {
        val (model, _) = buildFrom(LCE_SRC)
        val graph = GraphLowering.lower(model)
        assertNotNull(graph.node(StartNode.START_ID))
        assertEquals(3, graph.stateNodes.size)

        val layout = LayeredLayout.layout(graph)
        assertEquals(graph.nodes.size, layout.nodeRects.size)
        assertTrue(layout.canvasSize.width > 0.0)
    }

    // ノード -> ソースの SmartPsiElementPointer が正しい KtClass を指す(クリック遷移の実体)。
    fun testNavigationTargetPointsToDeclaration() {
        val (model, ktFile) = buildFrom(LCE_SRC)
        val loading = model.leaf(StateId("Loading"))!!
        val anchor = loading.source as PsiSourceAnchor

        val decl = runReadActionBlocking { anchor.pointer.element }
        assertNotNull(decl)
        assertEquals("Loading", decl!!.name)
        assertTrue("Loading は interface 宣言 = KtClass", decl is KtClass)

        val expected = runReadActionBlocking {
            val root = ktFile.declarations.filterIsInstance<KtClass>().first { it.name == "LceState" }
            root.declarations.filterIsInstance<KtClass>().first { it.name == "Loading" }
        }
        assertSame("pointer は fixture の Loading 宣言そのものを指す", expected, decl)
    }

    // Transition の source は遷移を定義する @On... 注釈 (State 宣言ではない) を指し、lowering 後の
    // GraphEdge へ伝播する = 矢印クリックで遷移定義へ飛べる (ide-4.md)。
    fun testTransitionSourcePointsToAnnotationAndPropagatesToEdge() {
        val (model, _) = buildFrom(LCE_SRC)

        // leaf Loading の @OnEnter トリガ。source は注釈エントリで、State 宣言 source とは別物。
        val loading = model.leaf(StateId("Loading"))!!
        val enter = loading.enter!!
        assertNotNull("@OnEnter トリガに source が付く", enter.source)
        assertNotSame("トリガ source は State 宣言 source とは別物", loading.source, enter.source)

        val enterHead = runReadActionBlocking {
            val el = (enter.source as PsiSourceAnchor).element
            assertTrue("トリガ source は @OnEnter 注釈エントリを指す", el is KtAnnotationEntry)
            (el as KtAnnotationEntry).text.substringBefore('(')
        }
        assertEquals("@OnEnter", enterHead)

        // @OnAction<LceAction.Reload> も同様に注釈サイトを指す。
        val content = model.leaf(StateId("Content"))!!
        val actionHead = runReadActionBlocking {
            (content.actions.single().source as PsiSourceAnchor).element?.let { (it as KtAnnotationEntry).text }
        }
        assertTrue("@OnAction サイトを指す", actionHead!!.startsWith("@OnAction<LceAction.Reload>"))

        // lowering: Loading -> Content / Error の enter エッジがトリガ source をそのまま運ぶ。
        val graph = GraphLowering.lower(model)
        val enterEdge = graph.edges.first { it.fromId == NodeId.state("Loading") && it.toId == NodeId.state("Content") }
        assertSame("enter エッジは @OnEnter トリガの source を運ぶ", enter.source, enterEdge.source)
    }

    // root 共有 recover と leaf の exit が AA で解決され、model / IR に落ちる (samples §5)。
    fun testAuthRecoverAndExitResolved() {
        val (model, _) = buildFrom(AUTH_SRC)
        assertFalse("解析成功", model.degraded)

        // root 共有 recover: 型引数 (例外名)・nextState・emit が解決される。
        val recover = model.root.recovers.single()
        assertEquals("SessionExpiredException", recover.exceptionName)
        assertEquals(listOf(StateId("LoggedOut")), recover.targets)
        assertEquals(listOf("SessionExpired"), recover.emits)

        // Authenticating の @OnExit(emit) が解決される (遷移能力なし)。
        val authenticating = model.leaf(StateId("Authenticating"))!!
        assertNotNull("exit があるはず", authenticating.exit)
        assertEquals(listOf("AuthAttemptFinished"), authenticating.exit!!.emits)

        // IR: any-state -> LoggedOut の RECOVER エッジ + Authenticating の exit バッジ。
        val graph = GraphLowering.lower(model)
        val any = graph.anyStateNodes.single()
        assertEquals(AnyStateNode.ROOT_ANY_ID, any.id)
        val recoverEdge = graph.edges.single { it.fromId == AnyStateNode.ROOT_ANY_ID && it.toId == NodeId.state("LoggedOut") }
        assertEquals("on SessionExpiredException / SessionExpired", recoverEdge.label)
        assertEquals("exit / AuthAttemptFinished", graph.stateNodes.first { it.id == NodeId.state("Authenticating") }.exitBadge)
    }

    // root 共有アクションが any-state 擬似ノードに落ち、event 宣言ゼロでも壊れない。
    fun testTabsSharedActionLowersToAnyState() {
        val (model, _) = buildFrom(TABS_SRC)

        val selectTab = model.root.actions.single()
        assertEquals("SelectTab", selectTab.actionName)
        assertTrue("stay 込みの共有アクション", selectTab.stay)
        assertEquals(setOf(StateId("Home"), StateId("Search"), StateId("Profile")), selectTab.targets.toSet())

        val graph = GraphLowering.lower(model)
        val any = graph.anyStateNodes.single()
        assertEquals(AnyStateNode.ROOT_ANY_ID, any.id)
        assertEquals("any state", any.label)
    }

    // 中間 sealed Stable(composite)+ 条件付き遷移 [Stay, X] を持つ feed (samples §2)。
    fun testFeedStableCompositeAndConditionalStay() {
        val (model, _) = buildFrom(FEED_SRC)
        assertFalse("解析成功", model.degraded)

        // 中間 sealed Stable が group、その子が leaf。
        val stable = model.root.findById(StateId("Stable")) as GroupState
        assertEquals(
            setOf(StateId("Stable", "Idle"), StateId("Stable", "Refreshing"), StateId("Stable", "LoadingMore")),
            stable.children.map { it.id }.toSet(),
        )
        assertEquals(
            setOf("Loading", "Idle", "Refreshing", "LoadingMore", "Error"),
            model.leaves.map { it.simpleName }.toSet(),
        )

        // Loading の enter は中間 sealed 内の Stable.Idle と Error を指す(跨階層の nextState 解決)。
        val loading = model.leaf(StateId("Loading"))!!
        assertEquals(setOf(StateId("Stable", "Idle"), StateId("Error")), loading.enter!!.targets.toSet())

        // Idle の LoadMore は条件付き = stay + LoadingMore、Refresh は stay なし。
        val idle = model.leaf(StateId("Stable", "Idle"))!!
        val loadMore = idle.actions.first { it.actionName == "LoadMore" }
        assertTrue("hasMore=false は stay", loadMore.stay)
        assertEquals(listOf(StateId("Stable", "LoadingMore")), loadMore.targets)
        assertFalse(idle.actions.first { it.actionName == "Refresh" }.stay)

        assertEquals("全 leaf 到達可能", model.leaves.map { it.id }.toSet(), model.reachableLeafIds)
        // lowering / layout: Stable の composite box が出る。
        val layout = LayeredLayout.layout(GraphLowering.lower(model))
        assertTrue("Stable の composite box", layout.compositeRects.containsKey(NodeId.composite("Stable")))
    }

    // フォームウィザード: 自己遷移(InputName Step1->Step1)・検証NG の stay+emit・終端 leaf Done (samples §4)。
    fun testWizardSelfTransitionStayEmitAndTerminalLeaf() {
        val (model, _) = buildFrom(WIZARD_SRC)
        assertFalse("解析成功", model.degraded)
        assertEquals(
            setOf("Step1", "Step2", "Step3", "Submitting", "Done"),
            model.leaves.map { it.simpleName }.toSet(),
        )
        assertTrue(StateId("Step1") in model.initial)

        val step1 = model.leaf(StateId("Step1"))!!
        // InputName は Step1 自身へ = 自己遷移(stay ではない)。
        val inputName = step1.actions.first { it.actionName == "InputName" }
        assertEquals(listOf(StateId("Step1")), inputName.targets)
        assertFalse("自己遷移は stay ではない", inputName.stay)
        // Next は検証NG で stay + Step2、ValidationFailed を emit。
        val next = step1.actions.first { it.actionName == "Next" }
        assertTrue("検証NG は stay", next.stay)
        assertEquals(listOf(StateId("Step2")), next.targets)
        assertEquals(listOf("ValidationFailed"), next.emits)

        // Submitting の enter は Done / Step3、SubmitFailed を emit。
        val submitting = model.leaf(StateId("Submitting"))!!
        assertEquals(setOf(StateId("Done"), StateId("Step3")), submitting.enter!!.targets.toSet())
        assertEquals(listOf("SubmitFailed"), submitting.enter.emits)

        // Done は宣言ゼロの終端 leaf(トリガ無し)。
        val done = model.leaf(StateId("Done"))!!
        assertNull(done.enter)
        assertTrue(done.actions.isEmpty() && done.recovers.isEmpty() && done.exit == null)

        assertEquals("全 leaf 到達可能", model.leaves.map { it.id }.toSet(), model.reachableLeafIds)
    }

    // 1 ファイルに複数 @StoreSpec があれば全て build できる(ツールバー Store ドロップダウンの元データ)。
    fun testMultipleStoreSpecsInOneFileYieldTwoModels() {
        myFixture.addFileToProject("me/tbsten/koma/strict/Annotations.kt", KOMA_STRICT_STUB)
        val ktFile = myFixture.configureByText("Multi.kt", MULTI_SRC) as KtFile
        val models = runReadActionBlocking {
            allowAnalysisOnEdt {
                StoreSpecModelBuilder.findStoreSpecClasses(ktFile).map { StoreSpecModelBuilder.build(it) }
            }
        }
        assertEquals(2, models.size)
        assertEquals(setOf("FirstState", "SecondState"), models.map { it.root.simpleName }.toSet())

        val first = models.first { it.root.simpleName == "FirstState" }
        assertFalse("解析成功", first.degraded)
        assertEquals(setOf("A", "B"), first.leaves.map { it.simpleName }.toSet())
        assertEquals("Go", first.leaf(StateId("A"))!!.actions.single().actionName)
    }

    // stub 無し(未解決注釈)でも落ちず、素 PSI の state 名は保持される。
    fun testDegradesGracefullyWithoutAnnotationStub() {
        val (model, _) = buildFrom(LCE_SRC, addStub = false)
        // 注釈が解決できなくても状態木の名前は取れる(degraded / robust fallback)。
        assertEquals("LceState", model.root.simpleName)
        assertEquals(setOf("Loading", "Content", "Error"), model.leaves.map { it.simpleName }.toSet())
    }

    // alias import した @OnAction (shortName が "OnAction" でない) でも型引数が正しく解決される。
    // index ではなく各注釈自身の PSI から型名を読むので、shortName 照合のズレが起きない。
    fun testAliasedOnActionResolvesTypeArgument() {
        val (model, _) = buildFrom(ALIAS_SRC)
        assertFalse("解析成功", model.degraded)
        val a = model.leaf(StateId("A"))!!
        assertEquals("Go", a.actions.single().actionName)
        assertEquals(listOf(StateId("B")), a.actions.single().targets)
    }

    // leaf が抱えるヘルパ (data class / enum) は state ではないので子に数えず、leaf のままにする。
    fun testNestedHelperDoesNotBecomePhantomState() {
        val (model, _) = buildFrom(HELPER_SRC)
        assertFalse("解析成功", model.degraded)
        assertEquals(setOf("A", "B"), model.leaves.map { it.simpleName }.toSet())
        // A は leaf のまま (ヘルパ Params / Mode で group 化していない)。
        val a = model.leaf(StateId("A"))
        assertNotNull("A は leaf のはず", a)
        assertEquals("Go", a!!.actions.single().actionName)
        // ヘルパは phantom leaf として現れない。
        assertFalse("Params は state ではない", model.leaves.any { it.simpleName == "Params" })
        assertFalse("Mode は state ではない", model.leaves.any { it.simpleName == "Mode" })
    }

    // store 外/typo の nextState (fqToId に無いが解決はできる foreign 参照) は通常 target と同じ成功扱いに
    // せず、targets から分離して ?Name の未解決として残す (図には描かず、表 = 正 には残す)。
    fun testOutOfStoreTargetSurfacesAsUnresolvedRow() {
        val (model, _) = buildFrom(FOREIGN_SRC)
        assertFalse("名前だけの全 degrade ではない (state 木 + 解決分は取れている)", model.degraded)
        assertTrue("foreign target があるので partial として印が付く", model.unresolved)

        val go = model.leaf(StateId("A"))!!.actions.single()
        // foreign は通常 target 扱いしない: targets には入らず unresolvedTargets に ?付きで残る。
        assertTrue("foreign は通常 target に化けない", go.targets.isEmpty())
        assertTrue("store 外 target は ?SomewhereElse として残る", go.unresolvedTargets.any { it == "?SomewhereElse" })
        // 要素はあるので空配列扱い (暗黙 Stay) にしない。
        assertFalse("foreign 要素は空ではないので Stay を捏造しない", go.stay)

        // 表 (= 正) にも行として出る (silent truncation を許さない)。
        val rows = model.transitionRows()
        assertTrue(rows.any { it.from == "A" && it.trigger == "go" && it.to == "?SomewhereElse" })
    }

    // root 共有 recover を持つ AuthState では any-state 擬似ノードが root 宣言 (AuthState) を指し、
    // ノード矩形のクリックで root へ飛べる (leaf のみだった従来 gap の解消)。
    fun testRootAnyStateCarriesRootSourceAndHitTests() {
        val (model, _) = buildFrom(AUTH_SRC)
        assertNotNull("root 宣言に source が付く", model.root.source)

        val graph = GraphLowering.lower(model)
        val any = graph.anyStateNodes.single()
        assertEquals(AnyStateNode.ROOT_ANY_ID, any.id)
        assertEquals("AuthState", declName(any.source))

        // hit-test: any-state ノードの矩形中心をクリックすると root 宣言 source が返る。
        val layout = LayeredLayout.layout(graph)
        val r = layout.nodeRects[AnyStateNode.ROOT_ANY_ID]!!
        assertSame(any.source, graph.hitSource(layout, r.center.x, r.center.y))
    }

    // 2 段入れ子 sealed: root / 中間 group (Loaded, General) が宣言 source を持ち、composite box と
    // any <Group> 擬似ノードへ伝播し、box のラベル帯クリックで group 宣言へ飛べる。
    fun testNestedGroupSourceAnchorsAndLabelStripHitTest() {
        val (model, _) = buildFrom(SETTINGS_SRC)
        assertFalse("解析成功", model.degraded)

        // root / 中間 group の宣言 source が正しい KtClass を指す。
        assertEquals("SettingsState", declName(model.root.source))
        val loaded = model.root.findById(StateId("Loaded")) as GroupState
        val general = model.root.findById(StateId("Loaded", "General")) as GroupState
        assertEquals("Loaded", declName(loaded.source))
        assertEquals("General", declName(general.source))

        // lowering: composite box / any Loaded 擬似ノードが group の source をそのまま運ぶ。
        val graph = GraphLowering.lower(model)
        assertSame("composite box は group source", loaded.source, graph.composites.first { it.id == NodeId.composite("Loaded") }.source)
        assertSame(
            "any Loaded は group source",
            loaded.source,
            graph.anyStateNodes.first { it.scope == StateId("Loaded") }.source,
        )

        // hit-test: Loaded box のラベル帯 (上端) クリックは Loaded 宣言 source を返す。
        val layout = LayeredLayout.layout(graph)
        val box = layout.compositeRects[NodeId.composite("Loaded")]!!
        assertSame("ラベル帯は group source", loaded.source, graph.hitSource(layout, box.x + 8.0, box.y + 5.0))
        // 帯より下の box 内部余白は帯ではないので拾わない (子ノードのクリックを奪わない)。
        assertNull("box 内部は帯ではない", graph.hitSource(layout, box.x + 2.0, box.center.y))
    }

    // 編集途中の壊れコード (未解決の型引数・存在しない nextState・未解決 emit) でも build は例外を投げず、
    // 少なくとも state 名は出す。ツールウィンドウが空/フリーズしない保証 (ide.md「半端コードで落とさない」)。
    fun testHalfTypedCodeDegradesGracefullyWithoutThrowing() {
        val (model, _) = buildFrom(BROKEN_SRC)

        assertEquals("BrokenState", model.root.simpleName)
        assertEquals(setOf("A", "B"), model.leaves.map { it.simpleName }.toSet())

        // 解析不完全 (未解決 nextState / emit / 型引数) は必ず UI へ surfacing される: 全 degrade か
        // partial (unresolved) のいずれか。無言で「宣言なし」に見せない (P1-02)。
        assertTrue("半端コードは degraded か unresolved で必ず印が付く", model.degraded || model.unresolved)

        // lowering / layout も壊れモデルで例外を投げず、有限・正のキャンバスを返す (描画パスの保護)。
        val graph = GraphLowering.lower(model)
        val layout = LayeredLayout.layout(graph)
        assertTrue(
            "canvas は有限・正",
            layout.canvasSize.width in 1.0..20_000.0 && layout.canvasSize.height in 1.0..20_000.0,
        )
    }

    // P1-01 の受入マトリクス: 空/省略/[X]/[Stay,X]/[Stay] の nextState 正規化が KSP 契約
    // (canStay = declaredStay || targets.isEmpty()) と一致し、図(自己ループ)と表(stay 行)が
    // 同じ正規化結果を使うことを end-to-end で固定する。
    fun testEmptyAndOmittedNextStateNormalizeToStayMatrix() {
        val (model, _) = buildFrom(NEXTSTATE_MATRIX_SRC)
        assertFalse("全て解決できるので unresolved ではない", model.unresolved)
        val home = model.leaf(StateId("Home"))!!
        fun action(name: String) = home.actions.first { it.actionName == name }

        // 省略 → Stay-only。
        action("Omitted").let { assertTrue("省略は stay", it.stay); assertTrue("省略は target なし", it.targets.isEmpty()) }
        // [] → Stay-only。
        action("EmptyList").let { assertTrue("[] は stay", it.stay); assertTrue("[] は target なし", it.targets.isEmpty()) }
        // [X] → target のみ、stay しない。
        action("ToTarget").let { assertFalse("[X] は stay しない", it.stay); assertEquals(listOf(StateId("Away")), it.targets) }
        // [Stay, X] → target + stay。
        action("StayAndTarget").let { assertTrue("[Stay,X] は stay", it.stay); assertEquals(listOf(StateId("Away")), it.targets) }
        // [Stay] → Stay-only。
        action("StayOnly").let { assertTrue("[Stay] は stay", it.stay); assertTrue("[Stay] は target なし", it.targets.isEmpty()) }

        // 図: 空/省略/[Stay] は Home の自己ループを生む (GraphLowering self-loop)。
        val graph = GraphLowering.lower(model)
        fun hasStayLoop(token: String) = graph.edges.any { it.fromId == NodeId.state("Home") && it.toId == NodeId.state("Home") && it.stay && it.trigger == token }
        assertTrue("Omitted は自己ループ", hasStayLoop("omitted"))
        assertTrue("EmptyList は自己ループ", hasStayLoop("emptyList"))
        assertTrue("StayOnly は自己ループ", hasStayLoop("stayOnly"))
        assertFalse("ToTarget は stay ループを作らない", hasStayLoop("toTarget"))
        // StayAndTarget は target エッジ + stay ループの両方。
        assertTrue("StayAndTarget の target エッジ", graph.edges.any { it.fromId == NodeId.state("Home") && it.toId == NodeId.state("Away") && it.trigger == "stayAndTarget" })
        assertTrue("StayAndTarget の stay ループ", hasStayLoop("stayAndTarget"))

        // 表: 空/省略は (stay) 行になり "—" 行にはならない (図と同じ正規化)。
        val rows = model.transitionRows()
        assertTrue(rows.any { it.from == "Home" && it.trigger == "omitted" && it.stay && it.to == "Home (stay)" })
        assertFalse("空 nextState を — 行にしない", rows.any { it.from == "Home" && it.trigger == "omitted" && it.to == "—" })
    }

    // P1-02: 未解決の nextState / emit / 型引数を空値として黙って捨てず、要素数を減らさず、partial として
    // 印を付ける。foreign と違い error type (未定義シンボル) でも同様に surfacing する。
    fun testUnresolvedValuesAreSurfacedNotDropped() {
        val (model, _) = buildFrom(UNRESOLVED_SRC)
        // state 木 + 解決分は取れるので全 degrade ではなく partial。
        assertFalse("名前だけの全 degrade ではない", model.degraded)
        assertTrue("未解決値があるので partial として印が付く", model.unresolved)

        // 未解決 nextState: 空配列扱い (暗黙 Stay) にせず、未解決として残す。
        val go = model.leaf(StateId("A"))!!.actions.first { it.actionName == "Go" }
        assertTrue("未解決 target が残る", go.unresolvedTargets.isNotEmpty())
        assertTrue("未解決 target は解決済み target に化けない", go.targets.isEmpty())
        assertFalse("要素はあるので暗黙 Stay にしない", go.stay)

        // 未解決 @OnAction 型引数 → actionName は ? (それでも nextState=[B] は解決される)。
        assertTrue(
            "未解決型引数の action が ? として残る",
            model.leaf(StateId("A"))!!.actions.any { it.actionName == "?" && it.targets == listOf(StateId("B")) },
        )

        // 一部だけ未解決の nextState / emit: 解決分は残り、未解決分も無言で消えない (要素数が減らない)。
        val enter = model.leaf(StateId("B"))!!.enter!!
        assertEquals("解決分 A は残る", listOf(StateId("A")), enter.targets)
        assertTrue("未解決 nextState 要素が ? として残る", enter.unresolvedTargets.any { it.startsWith("?") })
        assertEquals("emit は 2 要素 (Real + 未解決) のまま", 2, enter.emits.size)
        assertTrue("解決 emit は残る", enter.emits.contains("Real"))
        assertTrue("未解決 emit は ? として残る", enter.emits.contains("?"))

        // 表にも未解決 target 行が出る (silent truncation を許さない)。
        val rows = model.transitionRows()
        assertTrue("未解決 target が表に残る", rows.any { it.from == "A" && it.trigger == "go" && it.to.startsWith("?") })
    }

    // @StoreSpec が無いファイルでは findStoreSpecClasses が空 (= setup 画面へ / 例外なし)。
    fun testFileWithoutStoreSpecYieldsNoClasses() {
        myFixture.addFileToProject("me/tbsten/koma/strict/Annotations.kt", KOMA_STRICT_STUB)
        val ktFile = myFixture.configureByText("Plain.kt", NO_STORESPEC_SRC) as KtFile
        val found = runReadActionBlocking { StoreSpecModelBuilder.findStoreSpecClasses(ktFile) }
        assertTrue("@StoreSpec なしは空を返す", found.isEmpty())
    }

    // discovery は shortName ではなく classId で判定する: alias import した @Spec (shortName != "StoreSpec")
    // でも FQN 一致で検出され、解決済み contract で state 木が取れる。
    fun testAliasImportedStoreSpecIsDiscoveredAndBuilt() {
        val (model, ktFile) = buildFrom(ALIAS_SPEC_SRC)
        val found = runReadActionBlocking { allowAnalysisOnEdt { StoreSpecModelBuilder.findStoreSpecClasses(ktFile) } }
        assertEquals("alias import の @StoreSpec を検出する", listOf("AliasSpecState"), found.map { it.name })
        assertFalse("解析成功", model.degraded)
        assertEquals(setOf("A", "B"), model.leaves.map { it.simpleName }.toSet())
        assertEquals(listOf(StateId("B")), model.leaf(StateId("A"))!!.actions.single().targets)
    }

    // discovery: FQN の違う foreign な同名 @StoreSpec (other.StoreSpec) は誤検出しない。
    fun testForeignSameNameStoreSpecIsNotDiscovered() {
        myFixture.addFileToProject("me/tbsten/koma/strict/Annotations.kt", KOMA_STRICT_STUB)
        myFixture.addFileToProject("other/StoreSpec.kt", FOREIGN_STORESPEC_STUB)
        val ktFile = myFixture.configureByText("ForeignSpec.kt", FOREIGN_SPEC_SRC) as KtFile
        val found = runReadActionBlocking { allowAnalysisOnEdt { StoreSpecModelBuilder.findStoreSpecClasses(ktFile) } }
        assertTrue("foreign な同名 StoreSpec は検出しない", found.isEmpty())
    }

    // 解決済み contract (StateTreeParsing.kt): typealias 経由で親を継承する state も子として拾う
    // (shortName 照合だと alias 名が親名と違い消えてしまうケース)。
    fun testTypealiasParentedStateIsIncluded() {
        val (model, _) = buildFrom(TYPEALIAS_PARENT_SRC)
        assertFalse("解析成功", model.degraded)
        // KSP: Direct (直接) と ViaAlias (typealias 経由) の両方が leaf。
        assertEquals(setOf("Direct", "ViaAlias"), model.leaves.map { it.simpleName }.toSet())
    }

    // fully-qualified な親 supertype でも解決して子として拾う (KSP と同じ tree)。
    fun testFullyQualifiedParentIsIncluded() {
        val (model, _) = buildFrom(FQ_PARENT_SRC)
        assertFalse("解析成功", model.degraded)
        assertEquals(setOf("A", "B"), model.leaves.map { it.simpleName }.toSet())
    }

    // 同じ単純名の foreign 型を親と誤認しない: resolved FQN が親と違えば子に数えない
    // (shortName 照合だと foreign な other.CollideState を子扱いしてしまう)。
    fun testSameSimpleNameForeignParentIsExcluded() {
        val (model, _) = buildFrom(
            SAME_NAME_FOREIGN_SRC,
            extraFiles = mapOf("other/CollideState.kt" to FOREIGN_COLLIDE_STUB),
        )
        assertFalse("解析成功", model.degraded)
        // KSP: Decoy は foreign other.CollideState を継承 = この sealed root の子ではない。Real のみ leaf。
        assertEquals(setOf("Real"), model.leaves.map { it.simpleName }.toSet())
        assertNull("Decoy は state 木に現れない", model.root.findById(StateId("Decoy")))
    }

    // sealed だが子が (一時的に) ゼロの node は leaf 誤認せず group 契約に従う (KSP: isParent = sealed)。
    fun testSealedNodeWithZeroChildrenIsGroupNotLeaf() {
        val (model, _) = buildFrom(ZERO_CHILD_GROUP_SRC)
        assertFalse("解析成功", model.degraded)
        // A のみ leaf。EmptyGroup は sealed = group (leaf ではない)。
        assertEquals(setOf("A"), model.leaves.map { it.simpleName }.toSet())
        val emptyGroup = model.root.findById(StateId("EmptyGroup"))
        assertTrue("空 sealed は group", emptyGroup is GroupState)
        assertTrue("group の子はゼロ", (emptyGroup as GroupState).children.isEmpty())
    }

    // 非 sealed leaf が nested subtype (自身を継承する nested class) を持っても group 化しない
    // (KSP: 非 parent は子を持たない)。nested helper は HELPER_SRC 側で担保済み。
    fun testNonSealedNodeWithNestedSubtypeStaysLeaf() {
        val (model, _) = buildFrom(NESTED_SUBTYPE_SRC)
        assertFalse("解析成功", model.degraded)
        assertEquals(setOf("A", "B"), model.leaves.map { it.simpleName }.toSet())
        assertNotNull("A は leaf のまま", model.leaf(StateId("A")))
        assertFalse("nested subtype Impl は state ではない", model.leaves.any { it.simpleName == "Impl" })
        assertNull("Impl は state 木に現れない", model.root.findById(StateId("A", "Impl")))
    }

    // KSP invalid (root が非 sealed) は KSP が生成しない = 正常な完全 model として見せず degraded にする
    // (半端コード fallback と KSP invalid の分離。名前は空にせず保持する)。
    fun testNonSealedRootIsDegradedNotShownAsComplete() {
        val (model, _) = buildFrom(NON_SEALED_ROOT_SRC)
        assertTrue("非 sealed root は degraded", model.degraded)
        assertEquals("root 名は保持 (空にしない)", "NonSealedRootState", model.root.simpleName)
    }

    private fun declName(source: SourceAnchor?): String? =
        runReadActionBlocking { (source as? PsiSourceAnchor)?.pointer?.element?.name }

    private fun ignoreUnrelatedLoggedErrors(block: () -> Unit) {
        LoggedErrorProcessor.executeWith<Throwable>(object : LoggedErrorProcessor() {
            override fun processError(
                category: String,
                message: String,
                details: Array<out String>,
                t: Throwable?,
            ): Set<Action> {
                val text = "$category $message ${t?.stackTraceToString().orEmpty()}"
                val ignorable = listOf("Vue", "Lsp", "stale file ids").any { text.contains(it, ignoreCase = true) }
                return if (ignorable) emptySet() else super.processError(category, message, details, t)
            }
        }) { block() }
    }
}

// koma-strict 注釈のスタブ。FQN を本物と合わせて AA の classId 判定を通す。
private val KOMA_STRICT_STUB = """
    package me.tbsten.koma.strict
    import kotlin.reflect.KClass
    @Target(AnnotationTarget.CLASS)
    annotation class StoreSpec(
        val actions: KClass<*> = Unit::class,
        val events: KClass<*> = Unit::class,
        val initial: Array<KClass<*>> = [],
    )
    @Target(AnnotationTarget.CLASS)
    annotation class OnEnter(val nextState: Array<KClass<*>> = [], val emit: Array<KClass<*>> = [])
    @Target(AnnotationTarget.CLASS)
    annotation class OnExit(val emit: Array<KClass<*>> = [])
    @Target(AnnotationTarget.CLASS)
    annotation class OnAction<A>(val nextState: Array<KClass<*>> = [], val emit: Array<KClass<*>> = [])
    @Target(AnnotationTarget.CLASS)
    annotation class OnRecover<E>(val nextState: Array<KClass<*>> = [], val emit: Array<KClass<*>> = [])
    class Stay
    @Target()
    annotation class FlowStep(val ref: KClass<*>)
    @Target(AnnotationTarget.CLASS)
    annotation class FlowSpec(val name: String = "", val steps: Array<FlowStep> = [])
""".trimIndent()

// 編集途中を模す壊れコード: 未解決の型引数 (Nonexistent.Foo)・存在しない nextState (Ghost)・
// 未解決 emit (Missing.Evt)。AA は error type を返すだけで build は落ちず degrade する想定。
private val BROKEN_SRC = """
    import me.tbsten.koma.strict.StoreSpec
    import me.tbsten.koma.strict.OnAction
    import me.tbsten.koma.strict.OnEnter

    @StoreSpec(initial = [BrokenState.A::class])
    sealed interface BrokenState {
        companion object

        @OnAction<Nonexistent.Foo>(nextState = [Ghost::class])
        interface A : BrokenState { companion object }

        @OnEnter(nextState = [A::class], emit = [Missing.Evt::class])
        interface B : BrokenState { companion object }
    }
""".trimIndent()

private val NO_STORESPEC_SRC = """
    class JustAClass
    fun whatever() = 42
""".trimIndent()

// P1-03 discovery: alias import した @StoreSpec (@Spec)。shortName != "StoreSpec" だが classId 一致で検出。
private val ALIAS_SPEC_SRC = """
    import me.tbsten.koma.strict.StoreSpec as Spec
    import me.tbsten.koma.strict.OnAction

    interface State
    interface Action

    @Spec(initial = [AliasSpecState.A::class])
    sealed interface AliasSpecState : State {
        companion object

        @OnAction<AliasSpecAction.Go>(nextState = [B::class])
        interface A : AliasSpecState { companion object }

        interface B : AliasSpecState { companion object }
    }

    sealed interface AliasSpecAction : Action {
        data object Go : AliasSpecAction
    }
""".trimIndent()

// P1-03 discovery: koma-strict とは無関係な foreign な同名注釈 other.StoreSpec。誤検出してはいけない。
private val FOREIGN_STORESPEC_STUB = """
    package other
    import kotlin.reflect.KClass
    @Target(AnnotationTarget.CLASS)
    annotation class StoreSpec(val initial: Array<KClass<*>> = [])
""".trimIndent()

private val FOREIGN_SPEC_SRC = """
    import other.StoreSpec

    interface State

    @StoreSpec(initial = [ForeignAnnotatedState.A::class])
    sealed interface ForeignAnnotatedState : State {
        companion object
        interface A : ForeignAnnotatedState { companion object }
        interface B : ForeignAnnotatedState { companion object }
    }
""".trimIndent()

// P1-03 tree: typealias 経由で親を継承する ViaAlias も子。shortName 照合だと消えるが resolved なら残る。
private val TYPEALIAS_PARENT_SRC = """
    import me.tbsten.koma.strict.StoreSpec

    interface State

    @StoreSpec(initial = [TypeAliasParentState.Direct::class])
    sealed interface TypeAliasParentState : State {
        companion object
        interface Direct : TypeAliasParentState { companion object }
        interface ViaAlias : ParentAlias { companion object }
    }

    typealias ParentAlias = TypeAliasParentState
""".trimIndent()

// P1-03 tree: fully-qualified な親 supertype (app.FqParentState) も解決して子として拾う。
private val FQ_PARENT_SRC = """
    package app

    import me.tbsten.koma.strict.StoreSpec

    interface State

    @StoreSpec(initial = [FqParentState.A::class])
    sealed interface FqParentState : State {
        companion object
        interface A : app.FqParentState { companion object }
        interface B : FqParentState { companion object }
    }
""".trimIndent()

// P1-03 tree: Decoy は同じ単純名の foreign other.CollideState を継承 = この root の子ではない。
private val SAME_NAME_FOREIGN_SRC = """
    import me.tbsten.koma.strict.StoreSpec

    interface State

    @StoreSpec(initial = [CollideState.Real::class])
    sealed interface CollideState : State {
        companion object
        interface Real : CollideState { companion object }
        interface Decoy : other.CollideState { companion object }
    }
""".trimIndent()

private val FOREIGN_COLLIDE_STUB = """
    package other
    interface CollideState
""".trimIndent()

// P1-03 tree: EmptyGroup は sealed だが子ゼロ。KSP 契約では leaf ではなく group。
private val ZERO_CHILD_GROUP_SRC = """
    import me.tbsten.koma.strict.StoreSpec

    interface State

    @StoreSpec(initial = [ZeroChildState.A::class])
    sealed interface ZeroChildState : State {
        companion object
        interface A : ZeroChildState { companion object }
        sealed interface EmptyGroup : ZeroChildState { companion object }
    }
""".trimIndent()

// P1-03 tree: A は非 sealed leaf。自身を継承する nested subtype Impl があっても A は leaf のまま。
private val NESTED_SUBTYPE_SRC = """
    import me.tbsten.koma.strict.StoreSpec
    import me.tbsten.koma.strict.OnAction

    interface State
    interface Action

    @StoreSpec(initial = [NestedSubtypeState.A::class])
    sealed interface NestedSubtypeState : State {
        companion object

        @OnAction<NestedSubtypeAction.Go>(nextState = [B::class])
        interface A : NestedSubtypeState {
            companion object
            class Impl : A
        }

        interface B : NestedSubtypeState { companion object }
    }

    sealed interface NestedSubtypeAction : Action {
        data object Go : NestedSubtypeAction
    }
""".trimIndent()

// P1-03: root が非 sealed = KSP invalid。正常な完全 model として見せず degraded にする。
private val NON_SEALED_ROOT_SRC = """
    import me.tbsten.koma.strict.StoreSpec
    import me.tbsten.koma.strict.OnAction

    interface State
    interface Action

    @StoreSpec(initial = [NonSealedRootState.A::class])
    interface NonSealedRootState : State {
        @OnAction<NonSealedAction.Go>(nextState = [B::class])
        interface A : NonSealedRootState
        interface B : NonSealedRootState
    }

    sealed interface NonSealedAction : Action {
        data object Go : NonSealedAction
    }
""".trimIndent()

// P1-01 マトリクス: 1 leaf に 空/省略/[X]/[Stay,X]/[Stay] の nextState を並べる。全て解決可能。
private val NEXTSTATE_MATRIX_SRC = """
    import me.tbsten.koma.strict.StoreSpec
    import me.tbsten.koma.strict.OnAction
    import me.tbsten.koma.strict.Stay

    interface State
    interface Action

    @StoreSpec(initial = [MatrixState.Home::class])
    sealed interface MatrixState : State {
        companion object

        @OnAction<MatrixAction.Omitted>()
        @OnAction<MatrixAction.EmptyList>(nextState = [])
        @OnAction<MatrixAction.ToTarget>(nextState = [Away::class])
        @OnAction<MatrixAction.StayAndTarget>(nextState = [Stay::class, Away::class])
        @OnAction<MatrixAction.StayOnly>(nextState = [Stay::class])
        interface Home : MatrixState { companion object }

        interface Away : MatrixState { companion object }
    }

    sealed interface MatrixAction : Action {
        data object Omitted : MatrixAction
        data object EmptyList : MatrixAction
        data object ToTarget : MatrixAction
        data object StayAndTarget : MatrixAction
        data object StayOnly : MatrixAction
    }
""".trimIndent()

// P1-02: 未定義シンボル (error type) による未解決 nextState (Ghost)・未解決型引数 (Nope)・未解決 emit
// (MissingEvt)、および一部だけ未解決の array を含む。stub 注釈自体は解決するので全 degrade にはならない。
private val UNRESOLVED_SRC = """
    import me.tbsten.koma.strict.StoreSpec
    import me.tbsten.koma.strict.OnAction
    import me.tbsten.koma.strict.OnEnter

    interface State
    interface Action

    sealed interface UnresolvedAction : Action {
        data object Go : UnresolvedAction
    }

    sealed interface UnresolvedEvent {
        data class Real(val x: Int) : UnresolvedEvent
    }

    @StoreSpec(initial = [UnresolvedState.A::class])
    sealed interface UnresolvedState : State {
        companion object

        // 未解決 nextState (Ghost は未定義) + 未解決型引数 (Nope は未定義)。
        @OnAction<UnresolvedAction.Go>(nextState = [Ghost::class])
        @OnAction<Nope>(nextState = [B::class])
        interface A : UnresolvedState { companion object }

        // 一部だけ未解決の nextState (Ghost) + 一部だけ未解決の emit (MissingEvt)。
        @OnEnter(nextState = [A::class, Ghost::class], emit = [UnresolvedEvent.Real::class, MissingEvt::class])
        interface B : UnresolvedState { companion object }
    }
""".trimIndent()

// @FlowSpec 読取用: root に 2 つの flow 注釈を適用。宣言は別ファイル (FLOW_DEMO_FLOWS_SRC) に置き、
// 別ファイルでも findClass で解決できる = 実運用 (XxxState.flows.kt 分離) と同じ配置で検証する。
private val FLOW_DEMO_SRC = """
    import me.tbsten.koma.strict.StoreSpec
    import me.tbsten.koma.strict.OnEnter
    import me.tbsten.koma.strict.OnAction

    interface State
    interface Action

    @StoreSpec(initial = [FlowDemoState.Loading::class])
    @InitializeHappyPathFlow
    @RetryFlow
    sealed interface FlowDemoState : State {
        companion object

        @OnEnter(nextState = [Content::class, Error::class])
        interface Loading : FlowDemoState { companion object }

        @OnAction<FlowDemoAction.Retry>(nextState = [Loading::class])
        interface Error : FlowDemoState { companion object }

        interface Content : FlowDemoState { companion object }
    }

    sealed interface FlowDemoAction : Action {
        data object Retry : FlowDemoAction
    }
""".trimIndent()

private val FLOW_DEMO_FLOWS_SRC = """
    import me.tbsten.koma.strict.FlowSpec
    import me.tbsten.koma.strict.FlowStep
    import me.tbsten.koma.strict.OnEnter

    @FlowSpec(
        name = "initialize happy path",
        steps = [
            FlowStep(FlowDemoState.Loading::class),
            FlowStep(OnEnter::class),
            FlowStep(FlowDemoState.Content::class),
        ],
    )
    internal annotation class InitializeHappyPathFlow

    @FlowSpec(
        steps = [
            FlowStep(FlowDemoState.Error::class),
            FlowStep(FlowDemoAction.Retry::class),
            FlowStep(FlowDemoState.Loading::class),
        ],
    )
    internal annotation class RetryFlow
""".trimIndent()

private val LCE_SRC = """
    import me.tbsten.koma.strict.StoreSpec
    import me.tbsten.koma.strict.OnEnter
    import me.tbsten.koma.strict.OnAction

    interface State
    interface Action

    @StoreSpec(initial = [LceState.Loading::class])
    sealed interface LceState : State {
        companion object

        @OnEnter(nextState = [Content::class, Error::class], emit = [LceEvent.LoadFailed::class])
        interface Loading : LceState { companion object }

        @OnAction<LceAction.Reload>(nextState = [Loading::class])
        interface Content : LceState { companion object }

        @OnAction<LceAction.Retry>(nextState = [Loading::class])
        interface Error : LceState { companion object }
    }

    sealed interface LceAction : Action {
        data object Reload : LceAction
        data object Retry : LceAction
    }

    sealed interface LceEvent {
        data class LoadFailed(val message: String?) : LceEvent
    }
""".trimIndent()

private val MULTI_SRC = """
    import me.tbsten.koma.strict.StoreSpec
    import me.tbsten.koma.strict.OnAction

    interface State
    interface Action

    @StoreSpec(initial = [FirstState.A::class])
    sealed interface FirstState : State {
        companion object

        @OnAction<FirstAction.Go>(nextState = [B::class])
        interface A : FirstState { companion object }

        interface B : FirstState { companion object }
    }

    @StoreSpec(initial = [SecondState.X::class])
    sealed interface SecondState : State {
        companion object
        interface X : SecondState { companion object }
        interface Y : SecondState { companion object }
    }

    sealed interface FirstAction : Action {
        data object Go : FirstAction
    }
""".trimIndent()

private val TABS_SRC = """
    import me.tbsten.koma.strict.StoreSpec
    import me.tbsten.koma.strict.OnAction
    import me.tbsten.koma.strict.Stay

    interface State
    interface Action

    @StoreSpec(initial = [TabsState.Home::class])
    @OnAction<TabsAction.SelectTab>(
        nextState = [Stay::class, TabsState.Home::class, TabsState.Search::class, TabsState.Profile::class],
    )
    sealed interface TabsState : State {
        data object Home : TabsState

        @OnAction<TabsAction.UpdateQuery>(nextState = [Search::class])
        interface Search : TabsState { companion object }

        data object Profile : TabsState

        companion object
    }

    sealed interface TabsAction : Action {
        data class SelectTab(val tab: Int) : TabsAction
        data class UpdateQuery(val query: String) : TabsAction
    }
""".trimIndent()

private val ALIAS_SRC = """
    import me.tbsten.koma.strict.StoreSpec
    import me.tbsten.koma.strict.OnAction as Act

    interface State
    interface Action

    @StoreSpec(initial = [AliasState.A::class])
    sealed interface AliasState : State {
        companion object

        @Act<AliasAction.Go>(nextState = [B::class])
        interface A : AliasState { companion object }

        interface B : AliasState { companion object }
    }

    sealed interface AliasAction : Action {
        data object Go : AliasAction
    }
""".trimIndent()

private val HELPER_SRC = """
    import me.tbsten.koma.strict.StoreSpec
    import me.tbsten.koma.strict.OnAction

    interface State
    interface Action

    @StoreSpec(initial = [HelperState.A::class])
    sealed interface HelperState : State {
        companion object

        @OnAction<HelperAction.Go>(nextState = [B::class])
        interface A : HelperState {
            companion object
            // state ではないヘルパ (親 sealed を継承しない)。子状態に数えてはいけない。
            data class Params(val x: Int)
            enum class Mode { X, Y }
        }

        interface B : HelperState { companion object }
    }

    sealed interface HelperAction : Action {
        data object Go : HelperAction
    }
""".trimIndent()

private val FOREIGN_SRC = """
    import me.tbsten.koma.strict.StoreSpec
    import me.tbsten.koma.strict.OnAction

    interface State
    interface Action

    interface SomewhereElse

    @StoreSpec(initial = [ForeignState.A::class])
    sealed interface ForeignState : State {
        companion object

        @OnAction<ForeignAction.Go>(nextState = [SomewhereElse::class])
        interface A : ForeignState { companion object }

        interface B : ForeignState { companion object }
    }

    sealed interface ForeignAction : Action {
        data object Go : ForeignAction
    }
""".trimIndent()

private val SETTINGS_SRC = """
    import me.tbsten.koma.strict.StoreSpec
    import me.tbsten.koma.strict.OnEnter
    import me.tbsten.koma.strict.OnAction

    interface State
    interface Action

    @StoreSpec(initial = [SettingsState.Loading::class])
    sealed interface SettingsState : State {
        companion object

        @OnEnter(nextState = [Loaded.General.Viewing::class])
        interface Loading : SettingsState { companion object }

        @OnAction<SettingsAction.Close>(nextState = [Loading::class])
        sealed interface Loaded : SettingsState {
            // 直後に `sealed interface` が来ると kotlinc が `sealed` を companion 名として食う
            // parse quirk (StateTreeParsing.kt 参照)。明示的な空 body で回避する。
            companion object {}

            sealed interface General : Loaded {
                companion object

                @OnAction<SettingsAction.Edit>(nextState = [Editing::class])
                interface Viewing : General { companion object }

                interface Editing : General { companion object }
            }

            @OnAction<SettingsAction.Back>(nextState = [General.Viewing::class])
            interface Account : Loaded { companion object }
        }
    }

    sealed interface SettingsAction : Action {
        data object Close : SettingsAction
        data object Edit : SettingsAction
        data object Back : SettingsAction
    }
""".trimIndent()

private val AUTH_SRC = """
    import me.tbsten.koma.strict.StoreSpec
    import me.tbsten.koma.strict.OnEnter
    import me.tbsten.koma.strict.OnExit
    import me.tbsten.koma.strict.OnAction
    import me.tbsten.koma.strict.OnRecover

    interface State
    interface Action

    class SessionExpiredException : Exception()

    @StoreSpec(initial = [AuthState.CheckingSession::class])
    @OnRecover<SessionExpiredException>(
        nextState = [AuthState.LoggedOut::class],
        emit = [AuthEvent.SessionExpired::class],
    )
    sealed interface AuthState : State {
        companion object

        @OnEnter(nextState = [LoggedIn::class, LoggedOut::class])
        interface CheckingSession : AuthState { companion object }

        @OnAction<AuthAction.Login>(nextState = [Authenticating::class])
        interface LoggedOut : AuthState { companion object }

        @OnEnter(nextState = [LoggedIn::class, LoggedOut::class], emit = [AuthEvent.LoginFailed::class])
        @OnExit(emit = [AuthEvent.AuthAttemptFinished::class])
        interface Authenticating : AuthState { companion object }

        @OnAction<AuthAction.Logout>(nextState = [LoggedOut::class])
        interface LoggedIn : AuthState { companion object }
    }

    sealed interface AuthAction : Action {
        data class Login(val userName: String) : AuthAction
        data object Logout : AuthAction
    }

    sealed interface AuthEvent {
        data object SessionExpired : AuthEvent
        data class LoginFailed(val message: String?) : AuthEvent
        data object AuthAttemptFinished : AuthEvent
    }
""".trimIndent()

private val FEED_SRC = """
    import me.tbsten.koma.strict.StoreSpec
    import me.tbsten.koma.strict.OnEnter
    import me.tbsten.koma.strict.OnAction
    import me.tbsten.koma.strict.Stay

    interface State
    interface Action

    @StoreSpec(initial = [FeedState.Loading::class])
    sealed interface FeedState : State {
        companion object

        @OnEnter(nextState = [Stable.Idle::class, Error::class], emit = [FeedEvent.LoadFailed::class])
        interface Loading : FeedState { companion object }

        sealed interface Stable : FeedState {
            companion object

            @OnAction<FeedAction.Refresh>(nextState = [Refreshing::class])
            @OnAction<FeedAction.LoadMore>(nextState = [Stay::class, LoadingMore::class])
            interface Idle : Stable { companion object }

            @OnEnter(nextState = [Idle::class], emit = [FeedEvent.RefreshFailed::class])
            interface Refreshing : Stable { companion object }

            @OnEnter(nextState = [Idle::class], emit = [FeedEvent.LoadMoreFailed::class])
            interface LoadingMore : Stable { companion object }
        }

        @OnAction<FeedAction.Retry>(nextState = [Loading::class])
        interface Error : FeedState { companion object }
    }

    sealed interface FeedAction : Action {
        data object Refresh : FeedAction
        data object LoadMore : FeedAction
        data object Retry : FeedAction
    }

    sealed interface FeedEvent {
        data class LoadFailed(val message: String?) : FeedEvent
        data class RefreshFailed(val message: String?) : FeedEvent
        data class LoadMoreFailed(val message: String?) : FeedEvent
    }
""".trimIndent()

private val WIZARD_SRC = """
    import me.tbsten.koma.strict.StoreSpec
    import me.tbsten.koma.strict.OnEnter
    import me.tbsten.koma.strict.OnAction
    import me.tbsten.koma.strict.Stay

    interface State
    interface Action

    @StoreSpec(initial = [WizardState.Step1::class])
    sealed interface WizardState : State {
        companion object

        @OnAction<WizardAction.InputName>(nextState = [Step1::class])
        @OnAction<WizardAction.Next>(nextState = [Stay::class, Step2::class], emit = [WizardEvent.ValidationFailed::class])
        interface Step1 : WizardState { companion object }

        @OnAction<WizardAction.Next>(nextState = [Stay::class, Step3::class], emit = [WizardEvent.ValidationFailed::class])
        @OnAction<WizardAction.Back>(nextState = [Step1::class])
        interface Step2 : WizardState { companion object }

        @OnAction<WizardAction.Submit>(nextState = [Submitting::class])
        @OnAction<WizardAction.Back>(nextState = [Step2::class])
        interface Step3 : WizardState { companion object }

        @OnEnter(nextState = [Done::class, Step3::class], emit = [WizardEvent.SubmitFailed::class])
        interface Submitting : WizardState { companion object }

        interface Done : WizardState
    }

    sealed interface WizardAction : Action {
        data class InputName(val name: String) : WizardAction
        data object Next : WizardAction
        data object Back : WizardAction
        data object Submit : WizardAction
    }

    sealed interface WizardEvent {
        data class ValidationFailed(val reason: String) : WizardEvent
        data class SubmitFailed(val message: String?) : WizardEvent
    }
""".trimIndent()
