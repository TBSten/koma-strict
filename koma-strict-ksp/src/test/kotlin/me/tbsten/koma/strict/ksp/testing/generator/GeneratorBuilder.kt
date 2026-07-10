package me.tbsten.koma.strict.ksp.testing.generator

import io.kotest.property.Arb

/**
 * [Generator] を組み立てる DSL エントリポイント:
 * ```
 * val ids = generator {
 *     "Basic" case 123     // ラベル付き代表値
 *     "Zero" case 0
 *     case(456)            // ラベル無し代表値
 *     Arb.int()            // lambda の戻り値が Arb になる
 * }
 * ```
 * lambda 内で [GeneratorBuilder.case] (とそのラベル付き infix 版) で代表値を登録し、
 * property テスト用の [Arb] を返す。
 */
internal fun <Value> generator(builder: GeneratorBuilder<Value>.() -> Arb<Value>): Generator<Value> {
    val scope = GeneratorBuilderImpl<Value>()
    val arb = scope.builder()
    return object : Generator<Value> {
        override fun representativeValues(): Sequence<GeneratorValue<Value>> = scope.collected.toList().asSequence()

        override fun arb(): Arb<Value> = arb
    }
}

/** [generator] DSL lambda の receiver。代表値を (任意でラベル付きで) 登録する。 */
internal interface GeneratorBuilder<Value> {
    /** ラベル無し代表値の登録: `case(123)`。ラベルを付けるなら infix 版を使う。 */
    fun case(value: Value)

    /** ラベルが先に読める infix 版: `"Basic" case 123`。 */
    infix fun String.case(value: Value)
}

private class GeneratorBuilderImpl<Value> : GeneratorBuilder<Value> {
    val collected = mutableListOf<GeneratorValue<Value>>()

    override fun case(value: Value) {
        collected += GeneratorValue(label = null, value = value)
    }

    override infix fun String.case(value: Value) {
        collected += GeneratorValue(label = this, value = value)
    }
}
