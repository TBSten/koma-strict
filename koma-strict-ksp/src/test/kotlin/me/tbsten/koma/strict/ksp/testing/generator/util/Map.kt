package me.tbsten.koma.strict.ksp.testing.generator.util

import io.kotest.property.Arb
import io.kotest.property.arbitrary.map
import me.tbsten.koma.strict.ksp.testing.generator.Generator
import me.tbsten.koma.strict.ksp.testing.generator.GeneratorValue

/** 代表値と [Arb] の両方を map し、label は保持する。 */
internal fun <T, R> Generator<T>.map(transform: (T) -> R): Generator<R> {
    val source = this

    return object : Generator<R> {
        override fun representativeValues(): Sequence<GeneratorValue<R>> =
            sequence {
                source.representativeValues().forEach {
                    val mapped = transform(it.value)
                    val label = it.label
                    yield(GeneratorValue(label, mapped))
                }
            }

        override fun arb(): Arb<R> = source.arb().map(transform)
    }
}

/**
 * 各代表値の [label][GeneratorValue.label] だけを [transform] で変換し、値と [Arb] は変えない
 * ([Arb] はラベルを持たない)。値を変換しラベルを保つ [map] の双対。
 */
internal fun <T> Generator<T>.mapLabel(transform: (String?) -> String?): Generator<T> {
    val source = this

    return object : Generator<T> {
        override fun representativeValues(): Sequence<GeneratorValue<T>> =
            source.representativeValues().map { GeneratorValue(label = transform(it.label), value = it.value) }

        override fun arb(): Arb<T> = source.arb()
    }
}
