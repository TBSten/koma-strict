package me.tbsten.koma.strict.ksp.testing.snapshot

import io.kotest.assertions.fail
import io.kotest.assertions.withClue
import io.kotest.core.test.TestScope
import io.kotest.core.test.parents
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * Run with `-Dkoma.strict.snapshot.update=true` to (re-)generate snapshot files.
 */
private val updateSnapshots: Boolean
    get() = System.getProperty("koma.strict.snapshot.update")?.equals("true", ignoreCase = true) == true

private val snapshotRoot: File by lazy {
    // build.gradle.kts が渡す絶対パスを優先 (IDE 実行の workingDir 差異対策)。fallback はモジュール直下 snapshots/
    val dir = System.getProperty("koma.strict.snapshot.dir")?.let(::File) ?: File("snapshots")
    if (!dir.exists()) dir.mkdirs()
    dir
}

/**
 * Markdown golden snapshot と [block] で宣言した facets を比較する共通実装。
 *
 * 各 facet は宣言順に `## <facet name>` セクション + fenced code block になる。fence 長は
 * 本文内の最長 backtick run + 1 (最小 3) で計算するので、生成 KDoc 内の ` ```kt ` 例とも
 * 衝突しない。比較はファイル全体 (見出し + fence 込み) なので wrapping 自体のずれも検出する。
 * facet は最低 1 つ必須。
 */
private fun assertMatchesSnapshotAt(
    relativePath: String,
    block: SnapshotFacetBuilder.() -> Unit,
) {
    val builder = SnapshotFacetBuilderImpl()
    builder.block()
    val facets = builder.build()
    require(facets.isNotEmpty()) {
        "assertMatchesSnapshot(\"$relativePath\") requires at least one facet inside its block."
    }

    val file = File(snapshotRoot, relativePath)
    val normalizedActual = renderFacets(facets)

    if (!file.exists()) {
        if (updateSnapshots) {
            file.parentFile.mkdirs()
            file.writeText(normalizedActual, Charsets.UTF_8)
            return
        }
        fail(
            "Snapshot file not found: ${file.path}\n" +
                "Run with -Dkoma.strict.snapshot.update=true to create it.\n" +
                "Actual content:\n" +
                normalizedActual,
        )
    }

    val expected = file.readText(Charsets.UTF_8)
    if (expected != normalizedActual) {
        if (updateSnapshots) {
            file.writeText(normalizedActual, Charsets.UTF_8)
            return
        }
        withClue(
            "Snapshot mismatch for ${file.path}\n" +
                "Run with -Dkoma.strict.snapshot.update=true to update.",
        ) {
            normalizedActual shouldBe expected
        }
    }
}

/**
 * 明示名版。dotted な [name] の最初の `.` だけを `/` に書き換えて
 * `<TestName>/<testCase>.md` レイアウトにマップする (診断テスト用の
 * `"Spec.case.output"` → `Spec/case.output.md` 規約)。
 */
internal fun assertMatchesSnapshot(
    name: String,
    block: SnapshotFacetBuilder.() -> Unit,
) {
    assertMatchesSnapshotAt("${name.replaceFirst(".", "/")}.md", block)
}

/**
 * [TestScope] 版: 実行中のテストから golden パスを導出する。パスは
 * `<Spec 名>/<ネストしたテスト名>/....md` — セグメントを直接連結するので、テスト名に `.` が
 * 含まれても壊れない (cream の「先頭 `.` を `/` に書き換え」経由を意図的に避けた改善)。
 *
 * Note: `TestName` オブジェクトの `toString()` は `TestName(name=..., focus=...)` で golden パスに
 * 漏れるため、必ず `.name.name` を使う。
 */
internal fun TestScope.assertMatchesSnapshot(
    nameSuffix: String? = null,
    block: SnapshotFacetBuilder.() -> Unit,
) {
    val segments =
        buildList {
            add(testCase.spec::class.simpleName ?: "UnknownSpec")
            testCase.parents().forEach { add(it.name.name) }
            add(testCase.name.name)
            nameSuffix?.let { add(it) }
        }
    assertMatchesSnapshotAt(segments.joinToString("/") + ".md", block)
}

/**
 * [assertMatchesSnapshot] の facets block の builder。各 facet は宣言順に golden の
 * `## <name>` セクションになる。
 */
internal interface SnapshotFacetBuilder {
    /** fence 言語 `kt` の facet を追加する。`facet(this, content)` と等価。 */
    infix fun String.facetOf(content: String)

    /** fence 言語を明示して facet を追加する (e.g. `"text"`, `"properties"`)。 */
    fun facet(
        name: String,
        content: String,
        lang: String = "kt",
    )
}

private class SnapshotFacetBuilderImpl : SnapshotFacetBuilder {
    private val facets = mutableListOf<Facet>()

    override infix fun String.facetOf(content: String) {
        facets += Facet(this, content, "kt")
    }

    override fun facet(
        name: String,
        content: String,
        lang: String,
    ) {
        facets += Facet(name, content, lang)
    }

    fun build(): List<Facet> = facets.toList()
}

private data class Facet(
    val name: String,
    val content: String,
    val lang: String,
)

private fun renderFacets(facets: List<Facet>): String =
    buildString {
        for ((index, facet) in facets.withIndex()) {
            if (index > 0) append('\n')
            append("## ").append(facet.name).append("\n\n")
            append(renderFencedBlock(facet.content, facet.lang)).append('\n')
        }
    }

/**
 * [content] を fenced Markdown code block で包む。本文自体が backtick run を含む場合
 * (生成 KDoc の ` ```kt ` 例など) にも壊れないよう、外側の fence は本文内の最長 backtick run
 * より 1 長くする (最小 3)。
 */
private fun renderFencedBlock(
    content: String,
    lang: String,
): String {
    val trimmed = content.trimEnd()
    val longestInternalRun =
        Regex("`+").findAll(trimmed).maxOfOrNull { it.value.length } ?: 0
    val fence = "`".repeat(maxOf(3, longestInternalRun + 1))
    return buildString {
        append(fence)
        append(lang)
        append('\n')
        append(trimmed)
        append('\n')
        append(fence)
    }
}
