package me.tbsten.koma.strict.ksp.testing.smoke

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.string
import io.kotest.property.arbitrary.take
import me.tbsten.koma.strict.ksp.testing.generator.generator
import me.tbsten.koma.strict.ksp.testing.generator.util.cartesian
import me.tbsten.koma.strict.ksp.testing.generator.util.mapLabel
import me.tbsten.koma.strict.ksp.testing.generator.util.union
import me.tbsten.koma.strict.ksp.testing.generator.util.withRepresentativeValues

/**
 * [generator] DSL と `testing/generator/util/` のコンビネータの smoke テスト。
 * 決定的な representativeValues 側と、Arb が使えることを確認する
 * (snapshot 配線そのものは各 feature の Spec が担う)。
 */
internal class GeneratorSmokeTest :
    FreeSpec(
        {
            "generator DSL はラベル付き・無しの代表値を宣言順に集め、Arb も使える" {
                val names =
                    generator {
                        case("")
                        case("a")
                        "spaced" case "long name"
                        Arb.string()
                    }

                names.representativeValues().map { it.value }.toList() shouldBe listOf("", "a", "long name")
                names.representativeValues().last().label shouldBe "spaced"
                names.arb().take(5).count() shouldBe 5
            }

            "cartesian は左右のラベルを ', ' で結合し、カスタム結合もできる" {
                val left =
                    generator {
                        "L1" case "l1"
                        "L2" case "l2"
                        Arb.string()
                    }
                val right =
                    generator {
                        "R1" case "r1"
                        "R2" case "r2"
                        Arb.string()
                    }

                cartesian(left, right).representativeValues().first().label shouldBe "L1, R1"
                cartesian(left, right) { l, r -> "$l * $r" }
                    .representativeValues()
                    .map { it.label }
                    .toList() shouldBe listOf("L1 * R1", "L1 * R2", "L2 * R1", "L2 * R2")
            }

            "union builder は 'prefix' case でサブラベルを名前空間化し、無ラベルは index にフォールバックする" {
                val labelled =
                    generator {
                        "X" case "x"
                        "Y" case "y"
                        Arb.string()
                    }
                val unlabelled =
                    generator {
                        case("u1")
                        case("u2")
                        Arb.string()
                    }

                val u =
                    union {
                        "body" case labelled // -> "body/X", "body/Y"（潰れない）
                        "raw" case unlabelled // -> "raw[0]", "raw[1]"（index で一意）
                        case(labelled) // ラベルそのまま -> "X", "Y"
                        case(labelled) { it?.lowercase() } // フル変換 -> "x", "y"
                    }

                u.representativeValues().map { it.label }.toList() shouldBe
                    listOf("body/X", "body/Y", "raw[0]", "raw[1]", "X", "Y", "x", "y")
                u.representativeValues().map { it.value }.toList() shouldBe
                    listOf("x", "y", "u1", "u2", "x", "y", "x", "y")
                u.arb().take(5).count() shouldBe 5
            }

            "mapLabel はラベルだけ変換し、値と Arb は変えない" {
                val base =
                    generator {
                        "A" case "a"
                        Arb.string()
                    }
                val relabelled = base.mapLabel { label -> "prefix/$label" }

                relabelled.representativeValues().map { it.label }.toList() shouldBe listOf("prefix/A")
                relabelled.representativeValues().map { it.value }.toList() shouldBe listOf("a")
                relabelled.arb().take(3).count() shouldBe 3
            }

            "withRepresentativeValues は代表値だけ差し替え、元の Arb を保つ" {
                val base =
                    generator {
                        "derived1" case "d1"
                        "derived2" case "d2"
                        Arb.string()
                    }
                val curated =
                    base.withRepresentativeValues {
                        "handPicked" case "h1"
                    }

                curated.representativeValues().map { it.label to it.value }.toList() shouldBe
                    listOf("handPicked" to "h1")
                curated.arb().take(3).count() shouldBe 3
            }
        },
    )
