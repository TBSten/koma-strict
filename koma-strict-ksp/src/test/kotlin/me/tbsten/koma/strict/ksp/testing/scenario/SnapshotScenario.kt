package me.tbsten.koma.strict.ksp.testing.scenario

import io.kotest.property.Arb
import io.kotest.property.arbitrary.of
import me.tbsten.koma.strict.ksp.testing.compile.SnapshotSource
import me.tbsten.koma.strict.ksp.testing.generator.Generator
import me.tbsten.koma.strict.ksp.testing.generator.generator

/** snapshot 1 件の入力 = コンパイルする source の集合。`runCompileSnapshotTest(inputs = ...)` にそのまま渡せる。 */
internal data class SnapshotScenario(
    val sources: List<SnapshotSource>,
)

internal fun SnapshotScenario(vararg sources: SnapshotSource): SnapshotScenario = SnapshotScenario(sources.toList())

/** curated な (label -> scenario) 群を [Generator] にする。arb は代表からの一様抽選。 */
internal fun Generator.Companion.snapshotScenarios(vararg cases: Pair<String, SnapshotScenario>): Generator<SnapshotScenario> =
    generator {
        cases.forEach { (label, scenario) -> label case scenario }
        Arb.of(cases.map { it.second })
    }

// TODO: DSL 設計確定後、cream の testing/poet/ClassBuilders.kt (Prop / clazz / dataClass /
//   sealedInterface / asInner / containing) 相当の KotlinPoet ビルダを移植して scenario を
//   programmatic に組む。
