package me.tbsten.koma.strict.ksp.testing.generator.komaStrict

import io.kotest.property.Arb
import io.kotest.property.arbitrary.of
import me.tbsten.koma.strict.ksp.options.DeadActionSeverity
import me.tbsten.koma.strict.ksp.options.KomaStrictOptions
import me.tbsten.koma.strict.ksp.testing.generator.Generator
import me.tbsten.koma.strict.ksp.testing.generator.generator

/**
 * [KomaStrictOptions] の option matrix 軸 generator。代表値は default + 非 default の数点。
 *
 * TODO: option が増えたら cream の CreamOptionsGenerator.kt の per-axis generator + combine +
 *   withRepresentativeValues 形式に育てる。
 */
internal fun Generator.Companion.validKomaStrictOptions(): Generator<KomaStrictOptions> =
    generator {
        val representatives =
            listOf(
                KomaStrictOptions.default,
                KomaStrictOptions.default.copy(deadActionSeverity = DeadActionSeverity.ERROR),
            )
        representatives.forEach { options -> komaStrictOptionsLabel(options) case options }
        Arb.of(representatives)
    }

/** default と異なる軸だけラベルに出す (cream の creamOptionsLabel と同じ規約)。 */
private fun komaStrictOptionsLabel(options: KomaStrictOptions): String {
    val default = KomaStrictOptions.default
    val parts =
        buildList {
            if (options.deadActionSeverity != default.deadActionSeverity) {
                add("deadActionSeverity=${options.deadActionSeverity.name}")
            }
        }
    return if (parts.isEmpty()) "Default" else parts.joinToString(", ", prefix = "(", postfix = ")")
}
