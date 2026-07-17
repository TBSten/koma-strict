package me.tbsten.koma.strict.ksp.feature.storeSpec

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.validate
import me.tbsten.koma.strict.ksp.ProcessContext
import me.tbsten.koma.strict.ksp.codegen.generateStoreSpecFiles
import me.tbsten.koma.strict.ksp.core.common.asClassDeclarationOrReport
import me.tbsten.koma.strict.ksp.core.common.createNewKotlinFile
import me.tbsten.koma.strict.ksp.core.common.fullName
import me.tbsten.koma.strict.ksp.core.storeSpec.buildValidatedStoreSpec
import me.tbsten.koma.strict.ksp.core.storeSpec.reportGeneratedTypeNameCollisions
import me.tbsten.koma.strict.ksp.util.with
import me.tbsten.koma.strict.StoreSpec as StoreSpecAnnotation

private val annotationName = StoreSpecAnnotation::class.simpleName!!

/**
 * Entry point of the `@StoreSpec` feature: discovery -> StoreSpec model construction +
 * validation (core/storeSpec) -> codegen (pure functions in shared) -> file writing.
 *
 * Roots with validation errors skip generation entirely (no partially generated files).
 * The expected shape of the output is doc/internal/samples.md; the list of generated
 * artifacts is in doc/internal/generate-strict-store-factory-dsl.md, "generated artifacts".
 */
context(processContext: ProcessContext)
internal fun processStoreSpec(): List<KSAnnotated> =
    with(processContext.logger, processContext.options) {
        val (validTargets, invalidTargets) =
            processContext.resolver
                .getSymbolsWithAnnotation(annotationName = StoreSpecAnnotation::class.fullName)
                .partition { it.validate() }

        val validatedSpecs =
            validTargets.mapNotNull { target ->
                // 失敗時は null が返り error は報告済み。部分生成を防ぐため spec を作らない。
                val rootDecl = target.asClassDeclarationOrReport(annotationName) ?: return@mapNotNull null
                buildValidatedStoreSpec(rootDecl)?.let { spec -> rootDecl to spec }
            }

        // 生成 top-level 型名の衝突 (同一 package の @StoreSpec 横断) は spec 単体では
        // 検出できないためここで検証し、関与した spec の生成を丸ごとスキップする。
        val collided = reportGeneratedTypeNameCollisions(validatedSpecs)

        validatedSpecs.forEach { (rootDecl, spec) ->
            if (rootDecl in collided) return@forEach

            generateStoreSpecFiles(spec).forEach { file ->
                processContext.codeGenerator.createNewKotlinFile(
                    dependencies = Dependencies(aggregating = true, rootDecl.containingFile!!),
                    packageName = rootDecl.packageName,
                    fileName = file.fileName,
                ) { it.append(file.content) }
            }
        }
        // 未検証 (validate() 失敗 = 他 round で解決されうる) symbol を deferred として KSP に返す
        return invalidTargets
    }
