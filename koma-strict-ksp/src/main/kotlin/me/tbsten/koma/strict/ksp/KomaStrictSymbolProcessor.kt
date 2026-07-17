package me.tbsten.koma.strict.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import me.tbsten.koma.strict.ksp.feature.storeSpec.processStoreSpec
import me.tbsten.koma.strict.ksp.options.KomaStrictOptions
import me.tbsten.koma.strict.ksp.options.toKomaStrictOptions

internal class KomaStrictSymbolProcessor(
    private val rawOptions: Map<String, String>,
    internal val codeGenerator: CodeGenerator,
    internal val logger: KSPLogger,
) : SymbolProcessor {
    // lazy パースの理由: 無効な option を constructor crash (KSP が INTERNAL_ERROR として報告) ではなく
    // process() 内の clean な COMPILATION_ERROR として報告するため。初回成功時に backing field へ固定。
    private var parsedOptions: KomaStrictOptions? = null
    internal val options: KomaStrictOptions
        get() = parsedOptions ?: rawOptions.toKomaStrictOptions().also { parsedOptions = it }

    override fun process(resolver: Resolver): List<KSAnnotated> {
        // option は build script 由来でソース位置が無いので KSNode なしの logger.error。
        // この round の処理は丸ごとスキップ (部分生成ファイルを出さない)。
        val parsed =
            try {
                options
            } catch (e: InvalidKomaStrictOptionException) {
                logger.error(e.message.orEmpty())
                return emptyList()
            }

        val processContext = ProcessContext(resolver, parsed, codeGenerator, logger)

        return with(processContext) {
            buildList {
                // feature を追加するたびここに addAll を足す
                addAll(processStoreSpec())
            }
        }
    }
}
