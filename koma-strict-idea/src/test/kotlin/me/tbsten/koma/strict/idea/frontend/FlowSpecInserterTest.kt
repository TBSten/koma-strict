package me.tbsten.koma.strict.idea.frontend

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import me.tbsten.koma.strict.idea.flow.FlowTransition
import me.tbsten.koma.strict.idea.flow.RecordedFlow
import me.tbsten.koma.strict.idea.flow.generateFlowSpec
import me.tbsten.koma.strict.idea.ir.EdgeKind
import me.tbsten.koma.strict.idea.model.RootState
import me.tbsten.koma.strict.idea.model.StateId
import me.tbsten.koma.strict.idea.model.StoreDiagramModel
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile

/**
 * PSI insertion of the generated `@FlowSpec`, split across the state file and a sibling
 * `FeedState.flows.kt` (`flows-design.md` IDE section): the `internal annotation class` declaration goes
 * to the flows file, only the `@Name` application stays on the root.
 */
internal class FlowSpecInserterTest : BasePlatformTestCase() {

    private val src = """
        package demo

        import me.tbsten.koma.strict.StoreSpec

        @StoreSpec(initial = [FeedState.Loading::class])
        sealed interface FeedState {
            interface Loading : FeedState
            interface Error : FeedState
        }
    """.trimIndent()

    private fun generated() = generateFlowSpec(
        StoreDiagramModel(root = RootState("FeedState", emptyList())),
        RecordedFlow(
            initial = StateId("Loading"),
            transitions = listOf(
                FlowTransition(EdgeKind.ENTER, null, "onEnter", StateId("Loading"), StateId("Error")),
            ),
        ),
        "RecordedFlow",
    )

    private fun rootOf(file: KtFile): KtClassOrObject =
        file.declarations.filterIsInstance<KtClassOrObject>().first { it.name == "FeedState" }

    private fun flowsFileText(stateFile: KtFile): String {
        val dir = requireNotNull(stateFile.containingDirectory) { "state file has no directory" }
        val flows = requireNotNull(dir.findFile("FeedState.flows.kt")) { "FeedState.flows.kt was not created" }
        return flows.text
    }

    fun testInsertReturnsTheApplicationOnRootNotTheFlowsFileDeclaration() {
        val file = myFixture.configureByText("FeedState.kt", src) as KtFile
        val inserted = FlowSpecInserter.insert(project, rootOf(file), generated())

        // ジャンプ先 (呼び出し側が navigate する対象) は state ファイルの @RecordedFlow 適用箇所。
        // 隣の flows ファイルの verbose な宣言ではなく、ユーザーが触っているファイルの追加行を見せる。
        assertTrue("root の @Name 適用を返す", inserted is KtAnnotationEntry)
        assertEquals("@RecordedFlow", inserted!!.text)
        assertSame("同じファイル (flows ファイルではない)", file, inserted.containingFile)
    }

    fun testDeclarationGoesToFlowsFileAndOnlyApplicationStaysOnRoot() {
        val file = myFixture.configureByText("FeedState.kt", src) as KtFile
        FlowSpecInserter.insert(project, rootOf(file), generated())

        // state ファイルには @Name の適用行だけが残り、宣言 (annotation class / @FlowSpec / koma import) は移動している。
        val stateText = file.text
        assertTrue("root へ適用", stateText.contains("@RecordedFlow"))
        assertTrue("@RecordedFlow は @StoreSpec の後", stateText.indexOf("@RecordedFlow") > stateText.indexOf("@StoreSpec"))
        assertFalse("宣言は state ファイルに残らない", stateText.contains("annotation class RecordedFlow"))
        assertFalse("@FlowSpec 本体は state ファイルに残らない", stateText.contains("@FlowSpec("))

        // 宣言・@FlowSpec・koma import は flows ファイル側。
        val flowsText = flowsFileText(file)
        assertTrue("package", flowsText.contains("package demo"))
        assertTrue("internal annotation class 宣言", flowsText.contains("internal annotation class RecordedFlow"))
        assertTrue("@FlowSpec 本体", flowsText.contains("@FlowSpec("))
        assertTrue("FlowSpec import", flowsText.contains("import me.tbsten.koma.strict.FlowSpec"))
        assertTrue("FlowStep import", flowsText.contains("import me.tbsten.koma.strict.FlowStep"))
        assertTrue("OnEnter import", flowsText.contains("import me.tbsten.koma.strict.OnEnter"))
    }

    fun testSecondInsertDedupesNameInFlowsFile() {
        val file = myFixture.configureByText("FeedState.kt", src) as KtFile
        FlowSpecInserter.insert(project, rootOf(file), generated())
        FlowSpecInserter.insert(project, rootOf(file), generated())
        // 2 本目は同じ flows ファイルへ連番で追記される。
        val flowsText = flowsFileText(file)
        assertTrue("1 本目", flowsText.contains("internal annotation class RecordedFlow"))
        assertTrue("2 本目は連番", flowsText.contains("internal annotation class RecordedFlow2"))
        // root には両方の適用が付く。
        assertTrue(file.text.contains("@RecordedFlow"))
        assertTrue(file.text.contains("@RecordedFlow2"))
    }
}
