package me.tbsten.koma.strict.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import me.tbsten.koma.strict.ksp.options.KomaStrictOptions

/**
 * Per-round processing infrastructure shared by all feature entry points.
 *
 * [logger] is non-null: it is always supplied by the KSP environment, so nullability is not
 * propagated into the depths of the generation pipeline.
 * This is leaf infrastructure and must not depend on feature / core (feature -> ProcessContext
 * is the only upward exception to the dependency direction root -> feature -> core -> util).
 * The core layer receives only the capabilities it needs (options / logger, etc.) via
 * context parameters, not the whole ProcessContext.
 */
internal class ProcessContext(
    val resolver: Resolver,
    val options: KomaStrictOptions,
    val codeGenerator: CodeGenerator,
    val logger: KSPLogger,
)
