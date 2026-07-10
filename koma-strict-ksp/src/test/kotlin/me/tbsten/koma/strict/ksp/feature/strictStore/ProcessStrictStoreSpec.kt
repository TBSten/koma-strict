package me.tbsten.koma.strict.ksp.feature.strictStore

import io.kotest.core.spec.style.FreeSpec
import me.tbsten.koma.strict.ksp.feature.strictStore.scenario.strictStoreScenarios
import me.tbsten.koma.strict.ksp.testing.compile.runCompileSnapshotTest
import me.tbsten.koma.strict.ksp.testing.generator.Generator
import me.tbsten.koma.strict.ksp.testing.generator.komaStrict.validKomaStrictOptions
import me.tbsten.koma.strict.ksp.testing.generator.util.cartesian

/**
 * `@StrictStore` (placeholder) の golden snapshot spec。
 *
 * 意図的に snapshot にしない case と理由:
 * - DSL 未確定のため placeholder の入出力固定のみ。@StoreSpec DSL 確定後に scenario ファミリを
 *   拡張する (TODO)。
 * - 非 sealed class への `@StrictStore` — placeholder processor は sealed 検証を持たないため
 *   診断が出ない。sealed 検証の実装とセットで診断テスト (下のスタブ) を有効化する。
 */
internal class ProcessStrictStoreSpec :
    FreeSpec({
        "オプションと入力の全組み合わせ" - {
            cartesian(
                strictStoreScenarios(),
                Generator.validKomaStrictOptions(),
                label = { scenarioLabel, optionsLabel -> "option=$optionsLabel/$scenarioLabel" },
            ).representativeValues()
                .forEach { (testCaseName, value) ->
                    val (scenario, options) = value

                    testCaseName!! {
                        runCompileSnapshotTest(inputs = scenario.sources, options = options)
                    }
                }
        }

        // TODO(processor): sealed 検証実装後に有効化。cream の MultipleDiagnosticsTest と同形の
        //   facet 構成 (Compiler output / Input) で normalizedCompilerOutput を snapshot する。
        "sealed でない class への @StrictStore はエラー診断を出す".config(enabled = false) { }
    })
