package me.tbsten.koma.strict.ksp.core.storeSpec

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSNode
import me.tbsten.koma.strict.ksp.InvalidKomaStrictUsageException
import me.tbsten.koma.strict.ksp.core.common.reportKomaStrictError

/**
 * Aggregation of diagnostics while parsing one @StoreSpec.
 *
 * User misuse is reported immediately via `logger.error(message, ksNode)` (a clean
 * COMPILATION_ERROR) while counting errors; with even one error, generation for that
 * store spec is skipped entirely (no partially generated files). Parsing continues
 * after errors so that as many violations as possible are reported in a single build.
 */
internal class StoreSpecDiagnostics(
    private val logger: KSPLogger,
) {
    internal var errorCount: Int = 0
        private set

    internal val hasErrors: Boolean
        get() = errorCount > 0

    internal fun error(
        message: String,
        solution: String?,
        node: KSNode?,
    ) {
        errorCount++
        logger.reportKomaStrictError(
            InvalidKomaStrictUsageException(message = message, solution = solution),
            node,
        )
    }

    internal fun warn(
        message: String,
        node: KSNode?,
    ) {
        logger.warn(message, node)
    }
}
