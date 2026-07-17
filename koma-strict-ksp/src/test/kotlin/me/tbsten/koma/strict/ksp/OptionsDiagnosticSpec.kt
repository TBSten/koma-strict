@file:OptIn(ExperimentalCompilerApi::class)

package me.tbsten.koma.strict.ksp

import com.tschuchort.compiletesting.KotlinCompilation
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import me.tbsten.koma.strict.ksp.testing.compile.compileWithKomaStrict
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

/**
 * 不正な KSP option 値の診断 e2e ([KomaStrictSymbolProcessor] の lazy パース経路)。
 *
 * options は constructor でパースすると KSP が INTERNAL_ERROR ("processor crashed") として
 * 報告してしまうため、process() 内で clean な COMPILATION_ERROR として報告する
 * (.claude/rules/ksp-architecture.md)。純パース面 (throw する例外の中身) は shared 側の
 * KomaStrictOptionsParsingTest が担い、ここではコンパイル結果の面だけを検証する。
 *
 * golden にしないのは、option エラーがソース位置を持たず入力ソースにも依存しない
 * (facet の大半が意味を持たない) ため。
 */
internal class OptionsDiagnosticSpec :
    FreeSpec({
        val minimalStoreSpecSource =
            """
            package example.options

            import koma.core.Action
            import koma.core.State
            import me.tbsten.koma.strict.OnAction
            import me.tbsten.koma.strict.StoreSpec

            @StoreSpec
            sealed interface OptState : State {
                companion object

                @OnAction<OptAction.Go>(nextState = [Done::class])
                interface Start : OptState { companion object }

                interface Done : OptState { companion object }
            }

            sealed interface OptAction : Action {
                data object Go : OptAction
            }
            """.trimIndent()

        "不正な option 値は INTERNAL_ERROR ではなく clean な COMPILATION_ERROR になり、生成ファイルも出ない" {
            val result =
                compileWithKomaStrict(
                    source = minimalStoreSpecSource,
                    sourceFileName = "OptState.kt",
                    options = mapOf("koma.strict.deadActionSeverity" to "fatal"),
                )

            result.exitCode shouldBe KotlinCompilation.ExitCode.COMPILATION_ERROR

            // キーと実際の値を含む clean なエラーメッセージ (ソース位置なし)
            result.messages shouldContain "koma.strict.deadActionSeverity"
            result.messages shouldContain "fatal"

            // constructor crash (= KSP が processor 例外として stack trace を出す経路) ではない
            result.messages shouldNotContain "InvalidKomaStrictOptionException"

            // 不正 option の round は丸ごとスキップ (部分生成ファイルを出さない)
            result.generatedSources().shouldBeEmpty()
        }

        "有効な option 値なら同じ入力がそのままコンパイルできる (対照)" {
            val result =
                compileWithKomaStrict(
                    source = minimalStoreSpecSource,
                    sourceFileName = "OptState.kt",
                    options = mapOf("koma.strict.deadActionSeverity" to "ERROR"),
                )
            result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        }
    })
