package me.tbsten.koma.strict.ksp.naming

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.koma.strict.ksp.model.RootNode
import me.tbsten.koma.strict.ksp.model.TypeRef

/** per-store factory 関数名の単体テスト (末尾 State strip + decapitalize + Store)。 */
internal class StoreFactoryFunctionNameTest :
    FreeSpec({
        fun root(underPackageName: String): RootNode =
            RootNode(
                type = TypeRef("example", underPackageName),
                companionName = "Companion",
                children = emptyList(),
            )

        "末尾 State を strip して decapitalize + Store を付ける" {
            storeFactoryFunctionName(root("LceState")) shouldBe "lceStore"
            storeFactoryFunctionName(root("FeedState")) shouldBe "feedStore"
        }

        "State で終わらない root 名はそのまま decapitalize + Store" {
            storeFactoryFunctionName(root("Wizard")) shouldBe "wizardStore"
        }

        "ネストした root は underPackageName 連結ベース (同 simpleName 衝突を回避)" {
            storeFactoryFunctionName(root("FooScreen.State")) shouldBe "fooScreenStore"
            storeFactoryFunctionName(root("BarScreen.State")) shouldBe "barScreenStore"
        }

        "root 名が State そのものの縮退ケースは strip せず安全側に倒す" {
            storeFactoryFunctionName(root("State")) shouldBe "stateStore"
        }
    })
