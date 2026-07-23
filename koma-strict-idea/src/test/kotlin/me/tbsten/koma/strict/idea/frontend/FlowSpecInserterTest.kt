package me.tbsten.koma.strict.idea.frontend

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import me.tbsten.koma.strict.idea.flow.FlowTransition
import me.tbsten.koma.strict.idea.flow.RecordedFlow
import me.tbsten.koma.strict.idea.flow.generateFlowSpec
import me.tbsten.koma.strict.idea.ir.EdgeKind
import me.tbsten.koma.strict.idea.model.RootState
import me.tbsten.koma.strict.idea.model.StateId
import me.tbsten.koma.strict.idea.model.StoreDiagramModel
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile

/** PSI insertion of the generated `@FlowSpec` into the state file (`ide-test-code.md` F8). */
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

    fun testInsertsAnnotationClassAppliesToRootAndImports() {
        val file = myFixture.configureByText("FeedState.kt", src) as KtFile
        FlowSpecInserter.insert(project, rootOf(file), generated())
        val text = file.text
        assertTrue("annotation class 挿入", text.contains("annotation class RecordedFlow"))
        assertTrue("@FlowSpec 本体", text.contains("@FlowSpec("))
        assertTrue("root へ適用", text.contains("@RecordedFlow"))
        // @RecordedFlow は既存注釈 (@StoreSpec) の後 = 一番最後に付く。
        assertTrue("@RecordedFlow は @StoreSpec の後", text.indexOf("@RecordedFlow") > text.indexOf("@StoreSpec"))
        assertTrue("FlowSpec import", text.contains("import me.tbsten.koma.strict.FlowSpec"))
        assertTrue("FlowStep import", text.contains("import me.tbsten.koma.strict.FlowStep"))
        assertTrue("OnEnter import", text.contains("import me.tbsten.koma.strict.OnEnter"))
    }

    fun testSecondInsertDedupesName() {
        val file = myFixture.configureByText("FeedState.kt", src) as KtFile
        FlowSpecInserter.insert(project, rootOf(file), generated())
        FlowSpecInserter.insert(project, rootOf(file), generated())
        assertTrue("2 本目は連番", file.text.contains("annotation class RecordedFlow2"))
    }
}
