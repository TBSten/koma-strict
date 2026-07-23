package me.tbsten.koma.strict.idea

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.psi.KtFile

/**
 * Companion-file fallback (`flows-design.md` IDE section): opening a file with no `@StoreSpec` of its
 * own — e.g. `FeedState.flows.kt` — resolves to the sibling `FeedState.kt`'s roots, so the tool window
 * shows the same diagram there as on the state file itself instead of the empty setup screen.
 */
@OptIn(KaAllowAnalysisOnEdt::class)
internal class KomaStrictToolWindowControllerTest : BasePlatformTestCase() {

    // teardown 時の環境依存ノイズ (Vue LSP 未初期化 / stale file ids) を無視する
    // (StoreSpecModelBuilderTest と同じ recipe — テストの実際の失敗ではない)。
    @Throws(Exception::class)
    override fun tearDown() = ignoreUnrelatedLoggedErrors { super.tearDown() }

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

    fun testBaseStateFileNameDerivesTheSiblingOrNullForABaseFile() {
        assertEquals("FeedState.kt", KomaStrictToolWindowController.baseStateFileName("FeedState.flows.kt"))
        // 既に base file 自身 (追加ドットなし) は対象外。
        assertNull(KomaStrictToolWindowController.baseStateFileName("FeedState.kt"))
        // .kt でなければ対象外。
        assertNull(KomaStrictToolWindowController.baseStateFileName("FeedState.flows.txt"))
    }

    fun testResolveStoreSpecRootsFallsBackToTheSiblingStateFile() {
        myFixture.addFileToProject("me/tbsten/koma/strict/Annotations.kt", KOMA_STRICT_STUB)
        myFixture.addFileToProject(
            "demo/FeedState.kt",
            """
                package demo

                import me.tbsten.koma.strict.StoreSpec

                @StoreSpec(initial = [FeedState.A::class])
                sealed interface FeedState {
                    companion object
                    interface A : FeedState { companion object }
                }
            """.trimIndent(),
        )
        val flowsFile = myFixture.addFileToProject(
            "demo/FeedState.flows.kt",
            """
                package demo

                import me.tbsten.koma.strict.FlowSpec
                import me.tbsten.koma.strict.FlowStep

                @FlowSpec(steps = [FlowStep(FeedState.A::class)])
                internal annotation class SomeFlow
            """.trimIndent(),
        ) as KtFile

        val controller = KomaStrictToolWindowController(project, testRootDisposable)
        val roots = runReadActionBlocking {
            allowAnalysisOnEdt { controller.resolveStoreSpecRoots(flowsFile.virtualFile, flowsFile) }
        }
        assertEquals(
            "flows.kt 自身に @StoreSpec が無くても、隣接 FeedState.kt の root が返る",
            listOf("FeedState"),
            roots.map { it.name },
        )
    }

    fun testResolveStoreSpecRootsPrefersOwnStoreSpecWhenPresent() {
        myFixture.addFileToProject("me/tbsten/koma/strict/Annotations.kt", KOMA_STRICT_STUB)
        val file = myFixture.configureByText(
            "PlainState.kt",
            """
                import me.tbsten.koma.strict.StoreSpec

                @StoreSpec(initial = [PlainState.A::class])
                sealed interface PlainState {
                    companion object
                    interface A : PlainState { companion object }
                }
            """.trimIndent(),
        ) as KtFile

        val controller = KomaStrictToolWindowController(project, testRootDisposable)
        val roots = runReadActionBlocking {
            allowAnalysisOnEdt { controller.resolveStoreSpecRoots(file.virtualFile, file) }
        }
        // 自身が @StoreSpec を持つ通常ファイルはフォールバックせず、そのまま自分の root を返す。
        assertEquals(listOf("PlainState"), roots.map { it.name })
    }

    fun testResolveStoreSpecRootsReturnsEmptyWithoutAnyStoreSpecOrSibling() {
        myFixture.addFileToProject("me/tbsten/koma/strict/Annotations.kt", KOMA_STRICT_STUB)
        // "Orphan.kt" が存在しないので companion 風の命名でもフォールバック先が無い。
        val file = myFixture.configureByText("Orphan.notes.kt", "class Whatever") as KtFile

        val controller = KomaStrictToolWindowController(project, testRootDisposable)
        val roots = runReadActionBlocking {
            allowAnalysisOnEdt { controller.resolveStoreSpecRoots(file.virtualFile, file) }
        }
        assertTrue("sibling が無ければ空", roots.isEmpty())
    }
}

// koma-strict 注釈のスタブ (StoreSpecModelBuilderTest と同じ形。FlowSpec/FlowStep も含む)。
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
