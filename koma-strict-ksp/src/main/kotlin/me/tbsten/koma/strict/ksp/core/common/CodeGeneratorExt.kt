package me.tbsten.koma.strict.ksp.core.common

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.symbol.KSName

/**
 * The single writer for generated Kotlin files.
 *
 * [block] is first executed against an in-memory buffer and must write **declarations only** —
 * the `package` line and the `import me.tbsten.koma.strict.*` / `import me.tbsten.koma.strict.dsl.*`
 * boilerplate are owned here as the SSoT (the `dsl` package holds the shared runtime plumbing
 * the generated code builds on). If [block] writes nothing, no file is opened (never emit a file containing only
 * a package line + imports; avoiding the same pitfall as cream issue #113).
 *
 * Caller invariant: always pass `Dependencies(aggregating = true, <source>.containingFile!!)`
 * as [dependencies] (for incremental processing).
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
            it.appendLine("import me.tbsten.koma.strict.dsl.*")
            it.appendLine()
            it.append(body)
        }
}
