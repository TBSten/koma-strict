package me.tbsten.koma.strict.ksp.testing.compile

import com.squareup.kotlinpoet.FileSpec
import io.kotest.core.test.TestScope
import me.tbsten.koma.strict.ksp.options.KomaStrictOptions
import me.tbsten.koma.strict.ksp.testing.snapshot.SnapshotFacetBuilder
import me.tbsten.koma.strict.ksp.testing.snapshot.assertMatchesSnapshot
import org.intellij.lang.annotations.Language

/** snapshot 1 件に入力する source ファイル (ファイル名 + 手書き Kotlin コード)。 */
internal data class SnapshotSource(
    val fileName: String,
    @Language("kotlin") val code: String,
)

/** 将来 KotlinPoet generator を配線する時の継ぎ目。 */
internal fun FileSpec.asSnapshotSource(): SnapshotSource = SnapshotSource("$name.kt", toString())

/**
 * koma-strict の KSP プロセッサで [inputs] を実コンパイルし、その結果を 1 つの golden に snapshot する。
 * facet セットは固定 (追加は [additionalFacets] で):
 * - `Input:<file>`             : 入力ソース
 * - `KSP options`              : [options] を `ksp { arg(...) }` 形式で
 * - `Output:ExitCode`          : コンパイル終了コード
 * - `Output:Console`           : 正規化したコンパイラ / KSP 出力 ([normalizedCompilerOutput])
 * - `Output:Generated sources` : 生成された全ソースを連結 ([generatedSourceText])
 *
 * [assertions] でコンパイル結果に追加検証ができる。[KomaStrictCompilationResult] を返すので
 * 呼び出し側でさらに調べられる。
 */
internal inline fun TestScope.runCompileSnapshotTest(
    inputs: List<SnapshotSource>,
    options: KomaStrictOptions = KomaStrictOptions.default,
    crossinline assertions: (KomaStrictCompilationResult) -> Unit = { },
    crossinline additionalFacets: SnapshotFacetBuilder.(KomaStrictCompilationResult) -> Unit = { },
): KomaStrictCompilationResult {
    val result =
        compileWithKomaStrict(options = options.toKspArgs()) {
            inputs.forEach { it.fileName source it.code }
        }

    assertMatchesSnapshot {
        inputs.forEach { input -> "Input:${input.fileName}" facetOf input.code }
        "KSP options" facetOf options.toKspConfigString()
        facet("Output:ExitCode", result.exitCode.name, lang = "text")
        facet("Output:Console", result.normalizedCompilerOutput(), lang = "text")
        "Output:Generated sources" facetOf result.generatedSourceText()
        additionalFacets(result)
    }

    assertions(result)

    return result
}

/** 単一入力版の [runCompileSnapshotTest]。 */
internal inline fun TestScope.runCompileSnapshotTest(
    input: SnapshotSource,
    options: KomaStrictOptions = KomaStrictOptions.default,
    crossinline assertions: (KomaStrictCompilationResult) -> Unit = { },
    crossinline additionalFacets: SnapshotFacetBuilder.(KomaStrictCompilationResult) -> Unit = { },
): KomaStrictCompilationResult =
    runCompileSnapshotTest(
        inputs = listOf(input),
        options = options,
        assertions = assertions,
        additionalFacets = additionalFacets,
    )
