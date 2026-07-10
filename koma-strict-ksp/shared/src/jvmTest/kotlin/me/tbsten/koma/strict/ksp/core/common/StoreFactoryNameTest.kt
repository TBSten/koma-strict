package me.tbsten.koma.strict.ksp.core.common

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/** placeholder naming (関数名 / ファイル名) の衝突回避のユニットテスト。 */
internal class StoreFactoryNameTest :
    FreeSpec({
        fun info(
            packageName: String,
            underPackageName: String,
        ): ClassDeclarationInfo =
            object : ClassDeclarationInfo {
                override val packageName: String = packageName
                override val underPackageName: String = underPackageName
                override val simpleName: String = underPackageName.substringAfterLast(".")
                override val fullName: String =
                    if (packageName.isEmpty()) underPackageName else "$packageName.$underPackageName"
            }

        "トップレベルクラスの関数名は lowerCamel + StoreFactory になる" {
            val actual = storeFactoryFunctionName(info("example", "MyState"))
            actual shouldBe "myStateStoreFactory"
        }

        "同一パッケージ内の同 simpleName ネストクラスでも関数名が衝突しない" {
            val foo = storeFactoryFunctionName(info("example", "FooScreen.State"))
            val bar = storeFactoryFunctionName(info("example", "BarScreen.State"))
            foo shouldBe "fooScreenStateStoreFactory"
            bar shouldBe "barScreenStateStoreFactory"
            foo shouldNotBe bar
        }

        "ファイル名はネストクラスの underPackageName を含み衝突しない" {
            val actual = storeFactoryFileName(info("example", "FooScreen.State"))
            actual shouldBe "FooScreen.StateStoreFactory"
        }
    })
