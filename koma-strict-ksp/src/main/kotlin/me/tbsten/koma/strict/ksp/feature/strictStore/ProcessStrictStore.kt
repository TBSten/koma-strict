package me.tbsten.koma.strict.ksp.feature.strictStore

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.validate
import me.tbsten.koma.strict.StrictStore
import me.tbsten.koma.strict.ksp.ProcessContext
import me.tbsten.koma.strict.ksp.core.common.asClassDeclarationOrReport
import me.tbsten.koma.strict.ksp.core.common.createNewKotlinFile
import me.tbsten.koma.strict.ksp.core.common.fullName
import me.tbsten.koma.strict.ksp.core.common.storeFactoryFileName
import me.tbsten.koma.strict.ksp.core.strictStore.appendStoreFactoryStub
import me.tbsten.koma.strict.ksp.util.with

private val annotationName = StrictStore::class.simpleName!!

/**
 * TODO(doc/internal/generate-strict-store-factory-dsl.md): pipeline e2e 証明用の placeholder feature。
 *   DSL 設計の実装着手時に @StoreSpec 系の feature へ置き換える。
 */
context(processContext: ProcessContext)
internal fun processStrictStore(): List<KSAnnotated> =
    with(processContext.logger, processContext.options) {
        val (validTargets, invalidTargets) =
            processContext.resolver
                .getSymbolsWithAnnotation(annotationName = StrictStore::class.fullName)
                .partition { it.validate() }

        validTargets.forEach { target ->
            // 失敗時は null が返り error は報告済み。部分生成を防ぐため即 return@forEach。
            val sourceClass = target.asClassDeclarationOrReport(annotationName) ?: return@forEach

            processContext.codeGenerator.createNewKotlinFile(
                dependencies = Dependencies(aggregating = true, sourceClass.containingFile!!),
                packageName = sourceClass.packageName,
                fileName = storeFactoryFileName(sourceClass),
            ) {
                it.appendStoreFactoryStub(source = sourceClass)
            }
        }
        // 未検証 (validate() 失敗 = 他 round で解決されうる) symbol を deferred として KSP に返す
        return invalidTargets
    }
