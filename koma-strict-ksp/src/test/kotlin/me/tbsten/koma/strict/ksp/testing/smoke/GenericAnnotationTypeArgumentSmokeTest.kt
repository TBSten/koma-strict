@file:OptIn(ExperimentalCompilerApi::class)

package me.tbsten.koma.strict.ksp.testing.smoke

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.symbolProcessorProviders
import com.tschuchort.compiletesting.useKsp2
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import java.io.File

/**
 * KSP2 が SOURCE retention の generic annotation (`@TestOnAction<A>`) の型引数を
 * 解決できることの実測 smoke テスト。
 *
 * doc/internal/generate-strict-store-factory-dsl.md「未決事項 1 (最優先スパイク)」の恒久記録:
 * `@OnAction<A>` 宣言 API はこの解決に全面依存する。ここが赤くなったら
 * (KSP / Kotlin バージョン更新等)、`@OnAction(action = A::class)` へのフォールバック判断が必要。
 *
 * 検証している KSP API の読み方:
 * - 発見: `resolver.getSymbolsWithAnnotation("<annotation FQN>")`
 * - repeatable の列挙: symbol 側の `annotations` を annotation の FQN でフィルタ
 *   (実測: KSP2 2.3.10 は同一クラスに 2 つ付けても symbol を重複させない。processor 内の
 *   distinct() は防御的なもの。列挙順はソースの付与順)
 * - 型引数: `annotation.annotationType.resolve().arguments` の各 [KSTypeArgument] から
 *   `type?.resolve()?.declaration?.qualifiedName`
 *
 * 既存の compile 基盤 (`compileWithKomaStrict`) は koma-strict の provider 固定なので、
 * ここではテスト内 inline の [SymbolProcessor] instance を kctfork に直接渡す。
 */
internal class GenericAnnotationTypeArgumentSmokeTest :
    FreeSpec({
        "SOURCE retention の generic annotation の型引数を KSP2 が解決できる (単一 + repeatable)" {
            val capturedTypeArguments = mutableMapOf<String, List<String>>()
            val capturedSymbolNames = mutableListOf<String>()
            val processor = TypeArgumentCapturingProcessor(capturedTypeArguments, capturedSymbolNames)

            val result =
                KotlinCompilation()
                    .apply {
                        // 性能: classpath は stdlib のみに絞る (KomaStrictCompilation と同方針)
                        inheritClassPath = false
                        classpaths = listOf(kotlinStdlibClasspathRoot)
                        useKsp2()
                        symbolProcessorProviders += SymbolProcessorProvider { processor }
                        sources = listOf(SourceFile.kotlin("Spike.kt", spikeSource))
                        allWarningsAsErrors = true
                    }.compile()

            withClue(result.messages) {
                result.exitCode shouldBe KotlinCompilation.ExitCode.OK
            }

            withClue("getSymbolsWithAnnotation が両クラスを発見する (repeatable でも symbol は重複しない)") {
                capturedSymbolNames shouldContainExactlyInAnyOrder
                    listOf("SingleTarget", "RepeatedTarget")
            }
            withClue("単一付与: 型引数の qualifiedName が解決される") {
                capturedTypeArguments["SingleTarget"] shouldBe listOf("spike.SomeAction.Increment")
            }
            withClue("repeatable (同一クラスに 2 つ): 各 annotation の型引数が個別に解決される") {
                capturedTypeArguments["RepeatedTarget"] shouldBe
                    listOf("spike.SomeAction.Increment", "spike.SomeAction.Decrement")
            }
        }
    })

/** 実プロダクトの `@OnAction<A>(nextState = [...])` と同形の宣言・付与 (samples.md 準拠)。 */
private val spikeSource =
    """
    package spike

    import kotlin.reflect.KClass

    @Target(AnnotationTarget.CLASS)
    @Retention(AnnotationRetention.SOURCE)
    @Repeatable
    annotation class TestOnAction<A : Any>(
        val nextState: Array<KClass<*>> = [],
    )

    sealed interface SomeAction {
        data object Increment : SomeAction
        data object Decrement : SomeAction
    }

    // 型引数 + 値引数の併用 (実際の @OnAction の使われ方)
    @TestOnAction<SomeAction.Increment>(nextState = [SingleTarget::class])
    class SingleTarget

    // repeatable: 同一クラスに 2 つ
    @TestOnAction<SomeAction.Increment>
    @TestOnAction<SomeAction.Decrement>(nextState = [RepeatedTarget::class])
    class RepeatedTarget
    """.trimIndent()

private const val TEST_ON_ACTION_FQN = "spike.TestOnAction"

/**
 * `@TestOnAction` が付いたクラスごとに「付与順の annotation の型引数 qualifiedName」を捕捉する。
 * kctfork + useKsp2 は同一 JVM 内で processor を走らせるため、テスト側の可変コレクションに
 * 直接書き込める (ログ経由のパース不要)。
 */
private class TypeArgumentCapturingProcessor(
    private val capturedTypeArguments: MutableMap<String, List<String>>,
    private val capturedSymbolNames: MutableList<String>,
) : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        resolver
            .getSymbolsWithAnnotation(TEST_ON_ACTION_FQN)
            .filterIsInstance<KSClassDeclaration>()
            .onEach { capturedSymbolNames += it.simpleName.asString() }
            .distinct()
            .forEach { klass ->
                capturedTypeArguments[klass.simpleName.asString()] =
                    klass.annotations
                        .filter { annotation ->
                            annotation.annotationType
                                .resolve()
                                .declaration.qualifiedName
                                ?.asString() == TEST_ON_ACTION_FQN
                        }.map { annotation ->
                            annotation.annotationType
                                .resolve()
                                .arguments
                                .joinToString { typeArgument ->
                                    typeArgument.type
                                        ?.resolve()
                                        ?.declaration
                                        ?.qualifiedName
                                        ?.asString()
                                        ?: "<unresolved>"
                                }
                        }.toList()
            }
        return emptyList()
    }
}

private val kotlinStdlibClasspathRoot: File
    get() {
        val location =
            checkNotNull(Unit::class.java.protectionDomain?.codeSource?.location) {
                "Cannot locate the kotlin-stdlib classpath root (codeSource is null)."
            }
        return File(location.toURI())
    }
