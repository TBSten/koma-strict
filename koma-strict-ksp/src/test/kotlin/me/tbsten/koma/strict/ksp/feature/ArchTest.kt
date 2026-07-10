package me.tbsten.koma.strict.ksp.feature

import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.koma.strict.ksp.testing.konsist.COMPOSITION_ROOT_TYPES
import me.tbsten.koma.strict.ksp.testing.konsist.FEATURE_PACKAGE
import me.tbsten.koma.strict.ksp.testing.konsist.importsFrom
import me.tbsten.koma.strict.ksp.testing.konsist.inLayer
import me.tbsten.koma.strict.ksp.testing.konsist.komaStrictKspMain

/**
 * feature レイヤの architecture テスト。各 `feature.<name>` パッケージは annotation ごとの
 * 入口 ("発見 → 検証 → core 呼び出し") であり、`core` / `util` / `ProcessContext` には依存して
 * よいが、他の `feature.<name>` と composition root には依存してはならない。各 feature は
 * `context(ProcessContext) internal fun processXxx(): List<KSAnnotated>` を 1 本公開する。
 *
 * モジュール共通・core/util のルールは [me.tbsten.koma.strict.ksp.AllKotlinFilesTest] と
 * [me.tbsten.koma.strict.ksp.core.ArchTest] にある。entry point の署名検査は Konsist が
 * context parameters をモデル化していないため `KoFunctionDeclaration.text` の文字列検査で行う。
 */
internal class ArchTest :
    FreeSpec({
        // `feature.<name>` パッケージごとに sub-context を作り、違反が該当 feature を直接指すように
        // する。パッケージ一覧は spec 登録時に scope から発見する。
        val featurePackages =
            komaStrictKspMain
                .filter { it.inLayer(FEATURE_PACKAGE) }
                .groupBy { it.packagee?.name.orEmpty() }
                .toSortedMap()

        "scope に feature パッケージが存在する" {
            withClue("feature パッケージが 1 つも検出されていない（scope 設定の誤り）") {
                featurePackages.isNotEmpty() shouldBe true
            }
        }

        "各ファイルは feature.<name> サブパッケージに置く（feature/ 直下・深いネストを禁止）" {
            komaStrictKspMain
                .filter { it.inLayer(FEATURE_PACKAGE) }
                .assertTrue { file ->
                    val packageName = file.packagee?.name.orEmpty()
                    // feature の 1 つ下のみ: me.tbsten.koma.strict.ksp.feature.<name>
                    packageName.startsWith("$FEATURE_PACKAGE.") &&
                        !packageName.removePrefix("$FEATURE_PACKAGE.").contains('.')
                }
        }

        featurePackages.forEach { (packageName, files) ->
            packageName.substringAfterLast('.') - {
                "他の feature を参照しない（feature 間で関数などを使い合わない）" {
                    files.assertFalse { file ->
                        file.imports.any { import ->
                            import.name.startsWith("$FEATURE_PACKAGE.") &&
                                !import.name.startsWith("$packageName.")
                        }
                    }
                }

                "composition root（KomaStrictSymbolProcessor / Provider）に依存しない（ProcessContext のみ可）" {
                    // feature は ProcessContext だけを上向きに依存してよく、合成ルート本体には触れない。
                    files.assertFalse { file -> file.importsFrom(*COMPOSITION_ROOT_TYPES) }
                }

                "context(ProcessContext) な internal fun processXxx(): List<KSAnnotated> を公開する" {
                    val entryPoints =
                        files
                            .flatMap { it.functions(includeNested = false, includeLocal = false) }
                            .filter { it.name.startsWith("process") }

                    withClue("process* なトップレベル関数を公開していない") {
                        entryPoints.isNotEmpty() shouldBe true
                    }

                    entryPoints.forEach { entryPoint ->
                        withClue("entry point '${entryPoint.name}' は internal であるべき") {
                            entryPoint.hasInternalModifier shouldBe true
                        }
                        withClue(
                            "entry point '${entryPoint.name}' は List<KSAnnotated> を返すべき " +
                                "(actual: ${entryPoint.returnType?.sourceType})",
                        ) {
                            entryPoint.returnType?.sourceType shouldBe "List<KSAnnotated>"
                        }
                        // 署名 (本文の `{` より前) だけを検査する。本文中の文字列リテラルやコメントに
                        // `context(` / `ProcessContext` が現れても誤って pass しないため。
                        val signature = entryPoint.text.substringBefore('{').trim()
                        withClue(
                            "entry point '${entryPoint.name}' は context(ProcessContext) を宣言すべき " +
                                "(declaration: $signature)",
                        ) {
                            signature.contains("context(") shouldBe true
                            signature.contains("ProcessContext") shouldBe true
                        }
                    }
                }
            }
        }
    })
