package me.tbsten.koma.strict.ksp.testing.smoke

import com.tschuchort.compiletesting.KotlinCompilation
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import me.tbsten.koma.strict.ksp.options.KomaStrictOptions
import me.tbsten.koma.strict.ksp.testing.compile.KomaStrictCompilationResult
import me.tbsten.koma.strict.ksp.testing.compile.SnapshotSource
import me.tbsten.koma.strict.ksp.testing.compile.generatedSourceText
import me.tbsten.koma.strict.ksp.testing.compile.runCompileSnapshotTest

/**
 * [runCompileSnapshotTest] の smoke テスト: snapshot ラッパが実際に [SnapshotSource] 入力を
 * koma-strict の KSP processor でコンパイルし、[KomaStrictCompilationResult] を `assertions` に
 * 公開し、返り値としても返すことを証明する。低レベルの `compileWithKomaStrict` は
 * [KomaStrictCompilationSmokeTest] が担う。ここが書く golden (`Input` / `KSP options` /
 * `Output:*` facets) は他の snapshot 同様 `-Dkoma.strict.snapshot.update=true` で生成する
 * (facet 5 種の見た目の見本にもなる)。
 */
internal class RunCompileSnapshotTestSmokeTest :
    FreeSpec({
        "@StrictStore の source をコンパイルし、結果を assertions に公開し、返り値としても返す" {
            var observed: KomaStrictCompilationResult? = null

            val result =
                runCompileSnapshotTest(
                    input =
                        SnapshotSource(
                            fileName = "MyState.kt",
                            code =
                                """
                                package smoke.compile

                                import me.tbsten.koma.strict.StrictStore

                                @StrictStore
                                sealed interface MyState {
                                    data object Loading : MyState
                                }
                                """.trimIndent(),
                        ),
                    options = KomaStrictOptions.default,
                    assertions = { compiled ->
                        withClue(compiled.messages) {
                            compiled.exitCode shouldBe KotlinCompilation.ExitCode.OK
                        }
                        withClue("generated:\n${compiled.generatedSourceText()}") {
                            compiled.generatedSourceText() shouldContain "myStateStoreFactory"
                        }
                        observed = compiled
                    },
                )

            observed shouldBe result
            result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        }
    })
