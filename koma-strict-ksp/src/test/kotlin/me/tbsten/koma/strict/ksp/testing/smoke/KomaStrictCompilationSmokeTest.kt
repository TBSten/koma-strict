package me.tbsten.koma.strict.ksp.testing.smoke

import com.tschuchort.compiletesting.KotlinCompilation
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import me.tbsten.koma.strict.ksp.testing.compile.compileWithKomaStrict
import me.tbsten.koma.strict.ksp.testing.compile.generatedSourceText
import java.io.File

/**
 * [me.tbsten.koma.strict.ksp.testing] の compile 基盤の smoke テスト:
 * `compileWithKomaStrict` が実際に koma-strict の KSP processor をインライン source に対して
 * 走らせ、生成ソースへのアクセサが結果を返すことを証明する。全 compile 系テストがこの配管に
 * 依存するので、ここが壊れたら他の全テストが無意味 — 安く・依存無しに保ち、最初に落ちて
 * 基盤そのものを指すようにする。
 */
internal class KomaStrictCompilationSmokeTest :
    FreeSpec({
        val minimalStoreSpecSource =
            """
            package smoke

            import koma.core.Action
            import koma.core.State
            import me.tbsten.koma.strict.OnAction
            import me.tbsten.koma.strict.StoreSpec

            @StoreSpec
            sealed interface MyState : State {
                companion object

                @OnAction<MyAction.Load>(nextState = [Loaded::class])
                interface Idle : MyState { companion object }

                interface Loaded : MyState { companion object }
            }

            sealed interface MyAction : Action {
                data object Load : MyAction
            }
            """.trimIndent()

        "@StoreSpec 付きの最小 source がコンパイルでき、states() 拡張が生成される" {
            val result = compileWithKomaStrict(minimalStoreSpecSource)

            withClue(result.messages) {
                result.exitCode shouldBe KotlinCompilation.ExitCode.OK
            }

            val generatedText = result.generatedSourceText()
            withClue("generated:\n$generatedText") {
                generatedText shouldContain "fun koma.core.StoreBuilder<MyState, MyAction, Nothing>.states("
                generatedText shouldContain "@kotlin.jvm.JvmName(\"myStateStates\")"
            }
        }

        "複数 source DSL で別ファイルに分けてもコンパイルできる" {
            val result =
                compileWithKomaStrict {
                    "State.kt" source minimalStoreSpecSource
                    "Other.kt" source
                        """
                        package smoke

                        class Other
                        """.trimIndent()
                }

            withClue(result.messages) {
                result.exitCode shouldBe KotlinCompilation.ExitCode.OK
            }
            withClue("generated:\n${result.generatedSourceText()}") {
                result.generatedSourceText() shouldContain "fun koma.core.StoreBuilder<MyState, MyAction, Nothing>.states("
            }
        }

        "annotation の無い source では何も生成されない" {
            val result =
                compileWithKomaStrict(
                    """
                    package smoke

                    class Plain(val a: String)
                    """.trimIndent(),
                )

            withClue(result.messages) {
                result.exitCode shouldBe KotlinCompilation.ExitCode.OK
            }
            result.generatedSources() shouldBe emptyList<File>()
        }
    })
