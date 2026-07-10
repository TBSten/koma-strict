package me.tbsten.koma.strict.ksp.testing.generator.util

import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import me.tbsten.koma.strict.ksp.testing.generator.Generator
import me.tbsten.koma.strict.ksp.testing.generator.GeneratorValue

/**
 * [a] と [b] の全直積 (左 × 右) の `Generator<Pair>` を作る。scenario × option matrix の軸。
 * 2 つのラベルの結合は [label] でカスタムできる (デフォルトは ", " join)。
 */
internal fun <A, B> cartesian(
    a: Generator<A>,
    b: Generator<B>,
    label: (String?, String?) -> String? = { labelA, labelB -> listOfNotNull(labelA, labelB).joinToString(", ") },
): Generator<Pair<A, B>> =
    object : Generator<Pair<A, B>> {
        override fun representativeValues(): Sequence<GeneratorValue<Pair<A, B>>> =
            sequence {
                val rights = b.representativeValues().toList()
                a.representativeValues().forEach { leftValue ->
                    rights.forEach { rightValue -> yield(leftValue.pairedWith(rightValue, label)) }
                }
            }

        override fun arb(): Arb<Pair<A, B>> = arbitrary { rng -> a.arb().sample(rng).value to b.arb().sample(rng).value }
    }

private fun <A, B> GeneratorValue<A>.pairedWith(
    other: GeneratorValue<B>,
    label: (String?, String?) -> String?,
): GeneratorValue<Pair<A, B>> =
    GeneratorValue(
        label = label(this.label, other.label),
        value = value to other.value,
    )

// TODO: option 軸が増えたら cream の Combine.kt から combine (3〜6 arg) / combineToList /
//   varyingOnePair / 3-arg cartesian を移植する。
