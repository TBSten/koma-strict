package me.tbsten.koma.strict.ksp.model

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

/** StoreSpec model の型参照 (TypeRef) と path (StatePath) の単体テスト。 */
internal class TypeRefAndStatePathTest :
    FreeSpec({
        "TypeRef" - {
            "qualifiedName は package と underPackageName を連結する" {
                TypeRef("example", "MyAction.Logout").qualifiedName shouldBe "example.MyAction.Logout"
            }

            "default package では qualifiedName が underPackageName と一致する" {
                TypeRef("", "MyAction.Logout").qualifiedName shouldBe "MyAction.Logout"
            }

            "simpleName は最後のセグメントになる" {
                TypeRef("example", "MyAction.Logout").simpleName shouldBe "Logout"
                TypeRef("example", "MyState").simpleName shouldBe "MyState"
            }
        }

        "StatePath" - {
            "空 path は root を指す" {
                StatePath.root.isRoot shouldBe true
                StatePath.root.simpleName shouldBe null
            }

            "plus はセグメントを追加した新しい path を返す" {
                val stable = StatePath("Stable")
                val idle = stable + "Idle"
                idle shouldBe StatePath("Stable", "Idle")
                // 元の path は変化しない (immutable)
                stable shouldBe StatePath("Stable")
            }

            "simpleName は最後のセグメントになる" {
                StatePath("Stable", "Idle").simpleName shouldBe "Idle"
            }

            "dotJoined は dot 連結を返し root は空文字になる" {
                StatePath("Stable", "Idle").dotJoined() shouldBe "Stable.Idle"
                StatePath.root.dotJoined() shouldBe ""
            }
        }
    })
