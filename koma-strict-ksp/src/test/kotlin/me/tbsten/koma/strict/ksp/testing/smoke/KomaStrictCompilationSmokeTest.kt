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
        "@StrictStore 付きの最小 source がコンパイルでき、StoreFactory stub が生成される" {
            val result =
                compileWithKomaStrict(
                    """
                    package smoke

                    import me.tbsten.koma.strict.StrictStore

                    @StrictStore
                    sealed interface MyState {
                        data object Loading : MyState
                    }
                    """.trimIndent(),
                )

            withClue(result.messages) {
                result.exitCode shouldBe KotlinCompilation.ExitCode.OK
            }

            val generatedText = result.generatedSourceText()
            withClue("generated:\n$generatedText") {
                generatedText shouldContain "myStateStoreFactory"
            }
        }

        "複数 source DSL で別ファイルに分けてもコンパイルできる" {
            val result =
                compileWithKomaStrict {
                    "State.kt" source
                        """
                        package smoke.multi

                        import me.tbsten.koma.strict.StrictStore

                        @StrictStore
                        sealed interface MultiState {
                            data object Loading : MultiState
                        }
                        """.trimIndent()
                    "Other.kt" source
                        """
                        package smoke.multi

                        class Other
                        """.trimIndent()
                }

            withClue(result.messages) {
                result.exitCode shouldBe KotlinCompilation.ExitCode.OK
            }
            withClue("generated:\n${result.generatedSourceText()}") {
                result.generatedSourceText() shouldContain "multiStateStoreFactory"
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
