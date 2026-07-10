package me.tbsten.koma.strict.ksp.core.common

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.symbol.KSName

/**
 * 生成 Kotlin ファイルの唯一の書き出し口。
 *
 * [block] はまず in-memory buffer に対して実行され、**宣言のみ**を書くこと —
 * `package` 行と `import me.tbsten.koma.strict.*` boilerplate はここが SSoT として所有する。
 * [block] が何も書かなかった場合はファイルを開かない (空の package + import だけの
 * ファイルを出さない。cream issue #113 と同じ轍を踏まないため)。
 *
 * 呼び出し側の不変条件: [dependencies] には必ず
 * `Dependencies(aggregating = true, <source>.containingFile!!)` を渡す (incremental 対応)。
 */
internal fun CodeGenerator.createNewKotlinFile(
    dependencies: Dependencies,
    packageName: KSName,
    fileName: String,
    block: (Appendable) -> Unit,
) {
    val buffer = StringBuilder()
    block(buffer)
    val body = buffer.toString()

    if (body.isEmpty()) return

    createNewFile(
        dependencies = dependencies,
        packageName = packageName.asString(),
        fileName = fileName,
    ).bufferedWriter()
        .use {
            // default (root) package では packageName が "" になる。裸の `package ` 行は
            // Kotlin の構文エラーになるため、その場合は package 行自体を出さない。
            val pkg = packageName.asString()
            if (pkg.isNotEmpty()) {
                it.appendLine("package $pkg")
                it.appendLine()
            }
            it.appendLine("import me.tbsten.koma.strict.*")
            it.appendLine()
            it.append(body)
        }
}
