package me.tbsten.koma.strict.ksp.testing.generator.util

import me.tbsten.koma.strict.ksp.testing.generator.Generator
import me.tbsten.koma.strict.ksp.testing.generator.GeneratorBuilder
import me.tbsten.koma.strict.ksp.testing.generator.generator

/**
 * この generator の決定的な [representativeValues][Generator.representativeValues] を、[builder]
 * ([generator] と同じ `case` / `"label" case value` DSL) で手選びした集合に差し替える。
 * 元の [arb][Generator.arb] はそのまま保つ。
 *
 * 直積由来の代表値集合が snapshot / example テストには多すぎる・ノイジーな時に、代表だけを
 * 絞る用途 (cream では option matrix 48 → 4 点の絞り込みに使用)。property テストには
 * 元の全空間が残る。
 */
internal fun <T> Generator<T>.withRepresentativeValues(builder: GeneratorBuilder<T>.() -> Unit): Generator<T> {
    val base = this
    return generator<T> {
        builder()
        base.arb()
    }
}
