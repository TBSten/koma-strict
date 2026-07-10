package me.tbsten.koma.strict.ksp.testing.generator.util

import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import me.tbsten.koma.strict.ksp.testing.generator.Generator
import me.tbsten.koma.strict.ksp.testing.generator.GeneratorValue

/**
 * 同じ型の generator 群を 1 つに union する: 代表値は各 generator の代表値を登録順に連結、
 * [Arb] は generator を一様に 1 つ選んで sample する。値空間の積を取る [cartesian] の双対
 * (こちらは値空間の和)。
 */
internal fun <T> List<Generator<T>>.union(): Generator<T> {
    require(isNotEmpty()) { "union requires at least one generator" }
    val generators = this

    return object : Generator<T> {
        override fun representativeValues(): Sequence<GeneratorValue<T>> = generators.asSequence().flatMap { it.representativeValues() }

        override fun arb(): Arb<T> = arbitrary { rng -> generators[rng.random.nextInt(generators.size)].arb().sample(rng).value }
    }
}

internal fun <T> union(vararg generators: Generator<T>): Generator<T> = generators.toList().union()

/**
 * 追加時に各メンバを (再) ラベルできる builder 形の [union]。サブ generator の union が
 * 1 つのフラットなラベル空間を共有せず、名前空間化できる:
 * ```
 * union {
 *     "body"       case bodySweep          // -> "body/<member label>" (無ラベルは "body[i]")
 *     "targetKind" case targetKindCases
 *     case(siblings)                       // メンバのラベルそのまま
 *     case(matching) { "matching/$it" }    // フル変換 — mapLabel と同じ形
 * }
 * ```
 * メンバは登録順に union される (上の union オーバーロードと同一)。
 * golden パスの名前空間化はこの `/` prefix が担う (連番プレフィックスはプロジェクト規約で禁止)。
 */
internal fun <T> union(build: UnionBuilder<T>.() -> Unit): Generator<T> = UnionBuilderImpl<T>().apply(build).members.union()

/** [union] builder lambda の receiver。メンバ generator を (任意で再ラベルして) 登録する。 */
internal interface UnionBuilder<T> {
    /** メンバをそのまま追加 — 代表値は自身のラベルを保つ。 */
    fun case(generator: Generator<T>)

    /** 各代表値のラベルを変換して追加。[mapLabel] と同じ形。 */
    fun case(
        generator: Generator<T>,
        label: (String?) -> String?,
    )

    /**
     * `"prefix" case generator` — 各代表ラベルに `"prefix/"` を付け、無ラベルの代表値は
     * `"prefix[i]"` にフォールバックする (メンバが 1 つのラベルに潰れない)。
     */
    infix fun String.case(generator: Generator<T>)
}

private class UnionBuilderImpl<T> : UnionBuilder<T> {
    val members = mutableListOf<Generator<T>>()

    override fun case(generator: Generator<T>) {
        members += generator
    }

    override fun case(
        generator: Generator<T>,
        label: (String?) -> String?,
    ) {
        members += generator.mapLabel(label)
    }

    override infix fun String.case(generator: Generator<T>) {
        addPrefixed(this, generator)
    }

    private fun addPrefixed(
        prefix: String,
        generator: Generator<T>,
    ) {
        members +=
            object : Generator<T> {
                override fun representativeValues(): Sequence<GeneratorValue<T>> =
                    generator.representativeValues().mapIndexed { index, value ->
                        GeneratorValue(
                            label = value.label?.let { "$prefix/$it" } ?: "$prefix[$index]",
                            value = value.value,
                        )
                    }

                override fun arb(): Arb<T> = generator.arb()
            }
    }
}
