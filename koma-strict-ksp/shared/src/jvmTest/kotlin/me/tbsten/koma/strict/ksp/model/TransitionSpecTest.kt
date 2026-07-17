package me.tbsten.koma.strict.ksp.model

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

/** `nextState` 宣言の正規化 (アクション能力ルール) の単体テスト。 */
internal class TransitionSpecTest :
    FreeSpec({
        "空の nextState 宣言は stay のみ可の糖衣として正規化される" {
            val actual = TransitionSpec.of(targets = emptyList(), declaredStay = false)
            actual shouldBe TransitionSpec.stayOnly
            actual.canStay shouldBe true
            actual.targets shouldBe emptyList()
        }

        "遷移先のみの宣言は stay 不可になる" {
            val actual = TransitionSpec.of(targets = listOf(StatePath("Loading")), declaredStay = false)
            actual.canStay shouldBe false
            actual.targets shouldBe listOf(StatePath("Loading"))
        }

        "Stay と遷移先の併記は条件付き遷移 (stay 可 + 遷移先あり) になる" {
            val actual =
                TransitionSpec.of(
                    targets = listOf(StatePath("Stable", "LoadingMore")),
                    declaredStay = true,
                )
            actual.canStay shouldBe true
            actual.targets shouldBe listOf(StatePath("Stable", "LoadingMore"))
        }
    })
