package me.tbsten.koma.strict.ksp

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

public class KomaStrictSymbolProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        KomaStrictSymbolProcessor(
            rawOptions = environment.options,
            codeGenerator = environment.codeGenerator,
            logger = environment.logger,
        )
}
