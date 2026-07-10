@file:OptIn(ExperimentalCompilerApi::class)

package me.tbsten.koma.strict.ksp.testing.compile

import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

/** 生成された全ソースを名前ソートで決定的に連結する (snapshot 用)。 */
internal fun KomaStrictCompilationResult.generatedSourceText(): String =
    generatedSources()
        .sortedBy { it.name }
        .joinToString(separator = "\n\n// ----- next file -----\n\n") { file ->
            "// file: ${file.name}\n" + file.readText().trimEnd()
        }

/**
 * 捕捉したコンパイラ / KSP 出力から機械依存の部分を安定な placeholder に置換したもの
 * (snapshot golden にコミットできる形)。正規化 3 点:
 *
 * - JVM temp directory (`java.io.tmpdir`) → `<TMPDIR>`
 * - kctfork の per-run 作業ディレクトリ名 (`Kotlin-CompilationNNN`) → `Kotlin-Compilation<N>`
 * - stack trace の frame 列 (`\tat ...` / `\t... NN more`) → `\t<stack trace omitted>` 1 行。
 *   frame の内容や深さは JVM / Gradle / JUnit / KSP のバージョンで揺れるため。特定 frame を
 *   検証したい時は snapshot ではなく shouldContain を併用する。
 */
internal fun KomaStrictCompilationResult.normalizedCompilerOutput(): String {
    val tmpDir = System.getProperty("java.io.tmpdir").trimEnd('/', '\\')
    return compilerOutput
        .replace(tmpDir, "<TMPDIR>")
        .replace(Regex("Kotlin-Compilation\\d+"), "Kotlin-Compilation<N>")
        .replace(Regex("(?:\\n\\tat [^\\n]+|\\n\\t\\.\\.\\. \\d+ more)+"), "\n\t<stack trace omitted>")
        .trimEnd() + if (compilerOutput.isEmpty()) "" else "\n"
}
