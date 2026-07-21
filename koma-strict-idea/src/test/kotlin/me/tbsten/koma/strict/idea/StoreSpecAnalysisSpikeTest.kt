package me.tbsten.koma.strict.idea

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationValue
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtUserType

/**
 * Spike: confirm the Analysis API can resolve the koma-strict annotations inside a plugin test
 * (the foundation of the "correctness" self-drive loop). The biggest risk is reading the type
 * argument of a generic annotation `@OnAction<A>`.
 */
@OptIn(KaAllowAnalysisOnEdt::class)
internal class StoreSpecAnalysisSpikeTest : BasePlatformTestCase() {

    // Vue の VFS listener は tearDown の fixture 削除イベントで初期化に失敗し logged error を出す。
    // tearDown を包んで、その無関係エラーをテスト失敗へ昇格させない。
    @Throws(Exception::class)
    override fun tearDown() = ignoreUnrelatedLoggedErrors { super.tearDown() }

    // @StoreSpec の initial と @OnAction<A> の型引数を AA で取れることを確かめる。
    fun testStoreSpecInitialAndOnActionTypeArgument() {
        myFixture.addFileToProject("me/tbsten/koma/strict/Annotations.kt", KOMA_STRICT_STUB)
        val ktFile = myFixture.configureByText("LceState.kt", LCE_SRC) as KtFile

        val lceState = ktFile.declarations.filterIsInstance<KtClass>().first { it.name == "LceState" }
        val content = lceState.declarations.filterIsInstance<KtClass>().first { it.name == "Content" }

        runReadActionBlocking {
            allowAnalysisOnEdt {
                // (1) @StoreSpec と initial の解決
                analyze(lceState) {
                    val symbol = lceState.symbol
                    val storeSpec = symbol.annotations.firstOrNull {
                        it.classId?.asFqNameString() == "me.tbsten.koma.strict.StoreSpec"
                    } ?: error("@StoreSpec が見つからない。ann=${symbol.annotations.map { it.classId?.asFqNameString() }}")

                    val initialArg = storeSpec.arguments.firstOrNull { it.name.asString() == "initial" }?.expression
                    val initialNames = (initialArg as? KaAnnotationValue.ArrayValue)?.values?.mapNotNull { v ->
                        (v as? KaAnnotationValue.ClassLiteralValue)?.let { (it.type as? KaClassType)?.classId?.asFqNameString() }
                    }.orEmpty()
                    println("[spike] @StoreSpec initial = $initialNames")
                    assertTrue("initial に Loading が含まれるはず: $initialNames", initialNames.any { it.endsWith("Loading") })
                }

                // (2) @OnAction<A> の型引数 A を、注釈の PSI 型引数から解決する (最大リスク)
                val onAction = content.annotationEntries.first { it.shortName?.asString() == "OnAction" }
                val typeArgRef = (onAction.typeReference?.typeElement as? KtUserType)
                    ?.typeArgumentList?.arguments?.firstOrNull()?.typeReference
                    ?: error("@OnAction の型引数が PSI から取れない")
                analyze(content) {
                    val kaType = typeArgRef.type
                    val classId = (kaType as? KaClassType)?.classId?.asFqNameString()
                    println("[spike] @OnAction<A> type arg = ${classId ?: kaType}")
                    assertTrue(
                        "@OnAction<A> が Reload に解決されるはず: ${classId ?: kaType}",
                        (classId ?: kaType.toString()).contains("Reload"),
                    )
                }
            }
        }
    }

    // 統合 IDEA (IU) が抱える無関係な bundled plugin (Vue LSP 等) の初期化エラーや shutdown 時の
    // stale index を、テスト失敗へ昇格させない (spike の本題 = AA 解析 以外のノイズを黙らせる)。
    private fun ignoreUnrelatedLoggedErrors(block: () -> Unit) {
        LoggedErrorProcessor.executeWith<Throwable>(object : LoggedErrorProcessor() {
            override fun processError(
                category: String,
                message: String,
                details: Array<out String>,
                t: Throwable?,
            ): Set<LoggedErrorProcessor.Action> {
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
    annotation class OnAction<A>(val nextState: Array<KClass<*>> = [], val emit: Array<KClass<*>> = [])
    class Stay
""".trimIndent()

// LCE の最小宣言 (samples の LCE を縮約)。koma-core marker は fixture 追加ファイルを増やさないよう同一ファイルにインライン。
private val LCE_SRC = """
    import me.tbsten.koma.strict.StoreSpec
    import me.tbsten.koma.strict.OnEnter
    import me.tbsten.koma.strict.OnAction

    interface State
    interface Action

    @StoreSpec(initial = [LceState.Loading::class])
    sealed interface LceState : State {
        @OnEnter(nextState = [Content::class, Error::class])
        interface Loading : LceState
        @OnAction<LceAction.Reload>(nextState = [Loading::class])
        interface Content : LceState
        @OnAction<LceAction.Retry>(nextState = [Loading::class])
        interface Error : LceState
    }

    sealed interface LceAction : Action {
        data object Reload : LceAction
        data object Retry : LceAction
    }
""".trimIndent()
