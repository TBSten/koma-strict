package me.tbsten.koma.strict.ksp.feature.storeSpec

import io.kotest.core.spec.style.FreeSpec
import me.tbsten.koma.strict.ksp.feature.storeSpec.scenario.storeSpecDiagnosticsScenarios
import me.tbsten.koma.strict.ksp.feature.storeSpec.scenario.storeSpecDslFormDiagnosticsScenarios
import me.tbsten.koma.strict.ksp.feature.storeSpec.scenario.storeSpecHandlerDiagnosticsScenarios
import me.tbsten.koma.strict.ksp.feature.storeSpec.scenario.storeSpecScenarios
import me.tbsten.koma.strict.ksp.feature.storeSpec.scenario.storeSpecSharedDiagnosticsScenarios
import me.tbsten.koma.strict.ksp.feature.storeSpec.scenario.storeSpecTreeDiagnosticsScenarios
import me.tbsten.koma.strict.ksp.testing.compile.runCompileSnapshotTest
import me.tbsten.koma.strict.ksp.testing.generator.Generator
import me.tbsten.koma.strict.ksp.testing.generator.komaStrict.validKomaStrictOptions
import me.tbsten.koma.strict.ksp.testing.generator.util.cartesian

/**
 * `@StoreSpec` feature の golden snapshot spec。
 * 期待する生成物の形の正は doc/internal/samples.md (LCE ケースに全量)。
 *
 * 意図的に snapshot にしない case と理由:
 * - `@OnRecover<E>` の E が Exception subtype でない入力 — 型引数の上限 (`E : Exception`) を
 *   kotlinc が先に reject するため KSP 診断まで到達しない (processor 側の検証は防御的に保持)
 * - 例外型同士が継承関係にある複数 `@OnRecover` — koma 側のディスパッチ順が未確認
 *   (スパイク (d) 待ち。doc 未決事項 3)
 * - `states()` の二重呼び出し・呼び忘れ — 設計上コンパイル時に検出しない (doc §利用側 DSL)
 * - 巨大階層の直積 matrix — golden 爆発防止。軸は samples.md の 5 ケース + 診断に絞る
 * - `@OnAction` / `@OnRecover` の型引数や emit 要素が解決不能な入力 — 不正 Kotlin として
 *   kotlinc が先に reject するため KSP 診断まで到達しない (processor 側の検証は防御的に保持)
 * - `@StoreSpec` をクラス以外 (関数等) に付与 — annotation の target 制約で kotlinc が先に reject
 */
internal class ProcessStoreSpecSpec :
    FreeSpec({
        "オプションと入力の全組み合わせ" - {
            cartesian(
                storeSpecScenarios(),
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

        "宣言の形の診断" - {
            storeSpecDiagnosticsScenarios()
                .representativeValues()
                .forEach { (testCaseName, scenario) ->
                    testCaseName!! {
                        runCompileSnapshotTest(inputs = scenario.sources)
                    }
                }
        }

        "handler 宣言の診断" - {
            storeSpecHandlerDiagnosticsScenarios()
                .representativeValues()
                .forEach { (testCaseName, scenario) ->
                    testCaseName!! {
                        runCompileSnapshotTest(inputs = scenario.sources)
                    }
                }
        }

        "state ツリーの形の診断" - {
            storeSpecTreeDiagnosticsScenarios()
                .representativeValues()
                .forEach { (testCaseName, scenario) ->
                    testCaseName!! {
                        runCompileSnapshotTest(inputs = scenario.sources)
                    }
                }
        }

        "共有宣言と推論の診断" - {
            storeSpecSharedDiagnosticsScenarios()
                .representativeValues()
                .forEach { (testCaseName, scenario) ->
                    testCaseName!! {
                        runCompileSnapshotTest(inputs = scenario.sources)
                    }
                }
        }

        "利用側 DSL の形の診断" - {
            storeSpecDslFormDiagnosticsScenarios()
                .representativeValues()
                .forEach { (testCaseName, scenario) ->
                    testCaseName!! {
                        runCompileSnapshotTest(inputs = scenario.sources)
                    }
                }
        }
    })
