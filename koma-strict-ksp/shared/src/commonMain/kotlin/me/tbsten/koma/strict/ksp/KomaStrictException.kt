package me.tbsten.koma.strict.ksp

import me.tbsten.koma.strict.InternalKomaStrictApi
import me.tbsten.koma.strict.ksp.util.appendLines

@InternalKomaStrictApi
public abstract class KomaStrictException(
    message: String,
    solution: String? = null,
    cause: Throwable? = null,
) : Exception(
        buildString {
            appendLine(message)
            if (solution != null) {
                appendLine()
                appendLine("Solution: ")
                solution.lineSequence().forEach { appendLine("  $it") }
            }
        },
        cause,
    )

@InternalKomaStrictApi
public open class InvalidKomaStrictUsageException(
    message: String,
    solution: String?,
    cause: Throwable? = null,
) : KomaStrictException(
        message = "Invalid koma-strict usage: $message",
        solution = solution,
        cause = cause,
    )

@InternalKomaStrictApi
public class InvalidKomaStrictOptionException(
    message: String,
    solution: String?,
    cause: Throwable? = null,
) : InvalidKomaStrictUsageException(
        message = "Invalid option: $message",
        solution = solution,
        cause = cause,
    )

@InternalKomaStrictApi
public class UnknownKomaStrictException(
    message: String? = null,
    solution: String? = null,
    cause: Throwable? = null,
) : KomaStrictException(
        // .orEmpty() が無いと message == null 時に String.plus(Any?) が "null" を連結してしまう
        message = ("Unexpected error" + message?.let { ": $it" }.orEmpty()),
        solution = solution ?: reportToGithub(),
        cause = cause,
    )

@InternalKomaStrictApi
public fun reportToGithub(vararg with: String): String =
    buildString {
        appendLines(
            "Please report this issue at:",
            "",
            "    https://github.com/TBSten/koma-strict/issues",
            "",
        )
        if (with.isNotEmpty()) {
            // appendLines は改行終端しない (joinToString ベース) ため、繰り返し呼ぶと 1 行に潰れる。
            // 見出し・各項目は 1 行ずつ appendLine で書く。
            appendLine("  and report problems with:")
            with.forEach { appendLine("    - $it") }
            appendLine()
        }
    }
