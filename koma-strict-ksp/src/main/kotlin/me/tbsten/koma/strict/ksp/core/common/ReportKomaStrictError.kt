package me.tbsten.koma.strict.ksp.core.common

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSNode
import me.tbsten.koma.strict.ksp.InvalidKomaStrictUsageException
import me.tbsten.koma.strict.ksp.KomaStrictException

/**
 * Funnel that reports a user-misuse [KomaStrictException] as a positioned COMPILATION_ERROR.
 * Raw throws are forbidden because KSP treats them as INTERNAL_ERROR ("processor crashed" +
 * stack trace + partially generated files). Callers must abort processing immediately after
 * this call (return@forEach, etc.).
 * [ksNode] positions the diagnostic at the relevant declaration (null only when there is no
 * source location, e.g. option errors).
 */
internal fun KSPLogger.reportKomaStrictError(
    exception: KomaStrictException,
    ksNode: KSNode?,
) {
    error(exception.message.orEmpty(), ksNode)
}

/**
 * Safe cast to [KSClassDeclaration]. On failure, returns null with a clean error already reported.
 * Callers must stop processing the unit on null (`?: return@forEach`).
 */
context(logger: KSPLogger)
internal fun KSAnnotated.asClassDeclarationOrReport(annotationSimpleName: String): KSClassDeclaration? =
    this as? KSClassDeclaration
        ?: run {
            logger.reportKomaStrictError(
                InvalidKomaStrictUsageException(
                    message = "@$annotationSimpleName must be applied to a class.",
                    solution = "Please apply @$annotationSimpleName to a `class`, `object`, or `interface`",
                ),
                this,
            )
            null
        }

// TODO typealias 対象の注釈を追加したら cream の asDeclarationOrReport
//   (cream-ksp/.../core/common/ReportCreamError.kt) も移植する。
