package me.tbsten.koma.strict.ksp.testing.generator

import io.kotest.property.Arb

/**
 * テスト入力のソース。2 つの面を持つ:
 *
 * - [representativeValues] — 決定的で小さい代表値集合 (snapshot / example 用)。
 *   実行間で安定であること (乱数禁止・順序安定)。
 * - [arb] — PBT 用の kotest [Arb]。現状は未配線 (どのテストも checkAll に接続していない) だが、
 *   将来 `checkAll(gen.arb())` に接続できる設計にしておく。
 *
 * DSL エントリポイント [generator] は `GeneratorBuilder.kt`、コンビネータは
 * `testing/generator/util/` にある。
 */
internal interface Generator<Value> {
    fun representativeValues(): Sequence<GeneratorValue<Value>>

    fun arb(): Arb<Value>

    companion object
}

/** 代表値 1 件。人間可読な [label] を任意で持つ。 */
internal data class GeneratorValue<Value>(
    val label: String? = null,
    val value: Value,
)
