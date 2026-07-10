package me.tbsten.koma.strict.ksp.options

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import me.tbsten.koma.strict.ksp.InvalidKomaStrictOptionException

/**
 * `Map<String, String>` → [KomaStrictOptions] の純パーステスト。KSP コンパイルは走らせない。
 * 不正値のビルド失敗 (診断) 面は koma-strict-ksp 側の OptionsDiagnosticTest (TODO) が担う。
 */
internal class KomaStrictOptionsParsingTest :
    FreeSpec({
        "オプション未指定なら全プロパティが default にフォールバックする" {
            emptyMap<String, String>().toKomaStrictOptions() shouldBe KomaStrictOptions.default
        }

        "deadActionSeverity は各列挙名をパースできる" {
            mapOf("koma.strict.deadActionSeverity" to "WARNING")
                .toKomaStrictOptions()
                .deadActionSeverity shouldBe DeadActionSeverity.WARNING
            mapOf("koma.strict.deadActionSeverity" to "ERROR")
                .toKomaStrictOptions()
                .deadActionSeverity shouldBe DeadActionSeverity.ERROR
        }

        "deadActionSeverity のパースは大文字小文字を区別しない" {
            mapOf("koma.strict.deadActionSeverity" to "error")
                .toKomaStrictOptions()
                .deadActionSeverity shouldBe DeadActionSeverity.ERROR
        }

        "不正な deadActionSeverity はキーと実際の値を message に含む InvalidKomaStrictOptionException を投げる" {
            val error =
                shouldThrow<InvalidKomaStrictOptionException> {
                    mapOf("koma.strict.deadActionSeverity" to "fatal").toKomaStrictOptions()
                }
            error.message.orEmpty() shouldContain "koma.strict.deadActionSeverity"
            error.message.orEmpty() shouldContain "fatal"
        }

        "未知のキーは無視され、指定した option だけが反映される" {
            // option が 2 つ以上になったら cream 同様の「他 option と独立」テストへ育てる
            val options =
                mapOf(
                    "koma.strict.deadActionSeverity" to "ERROR",
                    "unrelated.key" to "whatever",
                ).toKomaStrictOptions()
            options.deadActionSeverity shouldBe DeadActionSeverity.ERROR
        }
    })
