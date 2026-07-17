package me.tbsten.koma.strict.ksp.naming

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import me.tbsten.koma.strict.ksp.model.RootNode
import me.tbsten.koma.strict.ksp.model.StatePath
import me.tbsten.koma.strict.ksp.model.TypeRef

/** 生成ファイル名と root `states()` の `@JvmName` の単体テスト。 */
internal class GeneratedFileNameTest :
    FreeSpec({
        fun root(underPackageName: String): RootNode =
            RootNode(
                type = TypeRef("example", underPackageName),
                companionName = "Companion",
                children = emptyList(),
            )

        "node の生成ファイル名は <qualified state>.generated になる" {
            generatedFileName(root("LceState"), StatePath("Loading")) shouldBe "LceState.Loading.generated"
            generatedFileName(root("FeedState"), StatePath("Stable", "Idle")) shouldBe
                "FeedState.Stable.Idle.generated"
        }

        "ネストした root の生成ファイル名は underPackageName を含み衝突しない" {
            generatedFileName(root("FooScreen.State"), StatePath("Loading")) shouldBe
                "FooScreen.State.Loading.generated"
        }

        "storeSpec ファイル名は <Root>.storeSpec.generated になる" {
            storeSpecFileName(root("LceState")) shouldBe "LceState.storeSpec.generated"
            storeSpecFileName(root("FooScreen.State")) shouldBe "FooScreen.State.storeSpec.generated"
        }

        "states() の @JvmName は {root decapitalize}States になる" {
            rootStatesJvmName(root("MyState")) shouldBe "myStateStates"
            rootStatesJvmName(root("LceState")) shouldBe "lceStateStates"
        }

        "同一パッケージ内の同 simpleName ネスト root でも @JvmName が衝突しない" {
            val foo = rootStatesJvmName(root("FooScreen.State"))
            val bar = rootStatesJvmName(root("BarScreen.State"))
            foo shouldBe "fooScreenStateStates"
            bar shouldBe "barScreenStateStates"
            foo shouldNotBe bar
        }
    })
