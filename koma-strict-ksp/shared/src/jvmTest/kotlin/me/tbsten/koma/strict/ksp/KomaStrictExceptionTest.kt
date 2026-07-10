package me.tbsten.koma.strict.ksp

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith

/** 例外階層と message 整形 (solution ブロック / prefix / issue リンク) のユニットテスト。 */
internal class KomaStrictExceptionTest :
    FreeSpec({
        "solution 付きの message は Solution ブロックをインデント付きで含む" {
            val e = object : KomaStrictException(message = "本文", solution = "行1\n行2") {}
            e.message.orEmpty() shouldContain "本文"
            e.message.orEmpty() shouldContain "Solution: "
            e.message.orEmpty() shouldContain "  行1"
            e.message.orEmpty() shouldContain "  行2"
        }

        "solution が無ければ Solution ブロックを含まない" {
            val e = object : KomaStrictException(message = "本文") {}
            e.message.orEmpty() shouldNotContain "Solution"
        }

        "InvalidKomaStrictUsageException は 'Invalid koma-strict usage:' を message の先頭に付ける" {
            val e = InvalidKomaStrictUsageException("誤用です", solution = null)
            e.message.orEmpty() shouldStartWith "Invalid koma-strict usage: 誤用です"
        }

        "InvalidKomaStrictOptionException は InvalidKomaStrictUsageException でも KomaStrictException でもある" {
            val e = InvalidKomaStrictOptionException("x", solution = null)
            (e is InvalidKomaStrictUsageException) shouldBe true
            (e is KomaStrictException) shouldBe true
        }

        "UnknownKomaStrictException は GitHub issue への報告先を solution に含む" {
            UnknownKomaStrictException(message = "boom").message.orEmpty() shouldContain
                "github.com/TBSten/koma-strict/issues"
        }

        "UnknownKomaStrictException は message を省略しても 'null' が混入しない" {
            val e = UnknownKomaStrictException(cause = IllegalStateException("root"))
            e.message.orEmpty() shouldNotContain "null"
            e.message.orEmpty().lineSequence().first() shouldBe "Unexpected error"
        }

        "reportToGithub に項目を渡すと見出しと各項目が別々の行になる" {
            val lines = reportToGithub("問題A", "問題B").lines()
            lines shouldContain "  and report problems with:"
            lines shouldContain "    - 問題A"
            lines shouldContain "    - 問題B"
        }

        "cause は保持される" {
            val cause = IllegalStateException("root")
            InvalidKomaStrictOptionException("x", solution = null, cause = cause).cause shouldBe cause
        }
    })
