@file:OptIn(ExperimentalCompilerApi::class)

package me.tbsten.koma.strict.ksp.feature.storeSpec

import com.tschuchort.compiletesting.KotlinCompilation
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.koma.strict.ksp.feature.storeSpec.scenario.samplesUseCaseScenarios
import me.tbsten.koma.strict.ksp.testing.compile.compileWithKomaStrict
import me.tbsten.koma.strict.ksp.testing.compile.runCompileSnapshotTest
import me.tbsten.koma.strict.ksp.testing.compile.toKspArgs
import me.tbsten.koma.strict.ksp.testing.generator.Generator
import me.tbsten.koma.strict.ksp.testing.generator.komaStrict.allKomaStrictOptions
import me.tbsten.koma.strict.ksp.testing.generator.komaStrict.validKomaStrictOptions
import me.tbsten.koma.strict.ksp.testing.generator.util.cartesian
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

/**
 * doc/internal/samples.md の 5 ユースケース (基本 LCE / LCE + pull-to-refresh + additional load /
 * タブ切替 / フォームウィザード / 認証 + セッション切れ) の「宣言」と「利用側」コードを
 * 忠実に写経した入力の golden snapshot spec。
 *
 * 目的は **samples.md の記載と実装の乖離検知** (ドキュメントが約束する DSL がそのまま
 * コンパイルでき、生成物が samples.md の「生成されるコード」と一致し続けること)。
 * 機能軸の網羅・診断系は [ProcessStoreSpecSpec] が担当し、入力の一部重複は意図的
 * (重複 golden 許容)。scenario は [samplesUseCaseScenarios] の独立ファミリ。
 *
 * 意図的に snapshot にしない case と理由:
 * - 診断・reject 系 — samples.md は正常系サンプル集のため対象外
 *   ([ProcessStoreSpecSpec] の診断ファミリが担当)
 * - samples.md の「生成コードの見どころ (抜粋)」単位の細分化 — 生成物全量は各 golden の
 *   `Output:Generated sources` facet が捕捉するため個別 case は不要
 * - bare 名 root annotation (kotlinc が Unresolved reference で reject する形) — samples.md
 *   自体が qualify + 注記コメント記載に改訂されたため、写経対象に存在しない
 */
internal class StoreSpecUseCasesTest :
    FreeSpec({
        "samples.md のユースケースとオプションの全組み合わせ" - {
            cartesian(
                samplesUseCaseScenarios(),
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

        "全ての UseCase はコンパイル可能" {
            // golden (表現の固定) とは独立な性質テスト: 全ユースケース × 全 option 組み合わせが
            // ExitCode.OK でコンパイルできること。将来 option が増えて snapshot 側の matrix が
            // withRepresentativeValues で代表点に間引かれても、こちらは間引かず全直積を回し続けること
            // (そのため validKomaStrictOptions ではなく全直積契約の allKomaStrictOptions を使う)。
            cartesian(
                samplesUseCaseScenarios(),
                Generator.allKomaStrictOptions(),
                label = { scenarioLabel, optionsLabel -> "scenario=$scenarioLabel, options=$optionsLabel" },
            ).representativeValues()
                .forEach { (comboLabel, value) ->
                    val (scenario, options) = value

                    withClue(comboLabel!!) {
                        val result =
                            compileWithKomaStrict(options = options.toKspArgs()) {
                                scenario.sources.forEach { it.fileName source it.code }
                            }
                        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
                    }
                }
        }
    })
