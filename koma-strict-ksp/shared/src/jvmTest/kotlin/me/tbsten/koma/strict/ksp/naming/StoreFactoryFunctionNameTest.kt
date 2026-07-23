package me.tbsten.koma.strict.ksp.naming

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.koma.strict.ksp.model.RootNode
import me.tbsten.koma.strict.ksp.model.TypeRef

/** per-store factory 関数名の単体テスト (末尾 State strip + create/restore 接頭辞 + Store)。 */
internal class StoreFactoryFunctionNameTest :
    FreeSpec({
        fun root(underPackageName: String): RootNode =
            RootNode(
                type = TypeRef("example", underPackageName),
                companionName = "Companion",
                children = emptyList(),
            )

        "末尾 State を strip して create/restore + Store を付ける" {
            createStoreFactoryFunctionName(root("LceState")) shouldBe "createLceStore"
            restoreStoreFactoryFunctionName(root("LceState")) shouldBe "restoreLceStore"
            createStoreFactoryFunctionName(root("FeedState")) shouldBe "createFeedStore"
            restoreStoreFactoryFunctionName(root("FeedState")) shouldBe "restoreFeedStore"
        }

        "State で終わらない root 名はそのまま create/restore + Store" {
            createStoreFactoryFunctionName(root("Wizard")) shouldBe "createWizardStore"
            restoreStoreFactoryFunctionName(root("Wizard")) shouldBe "restoreWizardStore"
        }

        "ネストした root は underPackageName 連結ベース (同 simpleName 衝突を回避)" {
            createStoreFactoryFunctionName(root("FooScreen.State")) shouldBe "createFooScreenStore"
            createStoreFactoryFunctionName(root("BarScreen.State")) shouldBe "createBarScreenStore"
        }

        "root 名が State そのものの縮退ケースは strip せず安全側に倒す" {
            createStoreFactoryFunctionName(root("State")) shouldBe "createStateStore"
            restoreStoreFactoryFunctionName(root("State")) shouldBe "restoreStateStore"
        }
    })
