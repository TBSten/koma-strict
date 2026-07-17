package me.tbsten.koma.strict.ksp.feature.storeSpec.scenario

import me.tbsten.koma.strict.ksp.testing.generator.Generator
import me.tbsten.koma.strict.ksp.testing.scenario.SnapshotScenario
import me.tbsten.koma.strict.ksp.testing.scenario.snapshotScenarios

/**
 * storeSpec feature の正常系 scenario 群 (doc/internal/samples.md のケース 1〜5 準拠)。
 * 診断系は [storeSpecDiagnosticsScenarios] / [storeSpecHandlerDiagnosticsScenarios]。
 */
internal fun storeSpecScenarios(): Generator<SnapshotScenario> =
    Generator.snapshotScenarios(
        "基本 LCE" to SnapshotScenario(lceScenarioSource()),
        "基本 LCE と生成 store factory 経由の利用側コード" to SnapshotScenario(lceScenarioSource(), lceUsageSource()),
        "利用側 configure の trailing lambda と clearPendingActions" to
            SnapshotScenario(lceScenarioSource(), lceConfigureUsageSource()),
        "scope lambda 形式と値渡しの混在" to SnapshotScenario(lceScenarioSource(), mixedDslFormsUsageSource()),
        "builder 形式と他形式の混在" to SnapshotScenario(lceScenarioSource(), builderFormMixedUsageSource()),
        "中間 sealed の states builder と default 名 member" to
            SnapshotScenario(defaultNameScenarioSource(), statesBuilderUsageSource()),
        "中間 sealed の actions と states の plus 合成" to
            SnapshotScenario(defaultNameScenarioSource(), plusCompositionUsageSource()),
        "states() の trailing escape block" to
            SnapshotScenario(feedScenarioSource(), statesEscapeUsageSource()),
        "中間 sealed と条件付き遷移と prop 持ち越し" to SnapshotScenario(feedScenarioSource()),
        "root 共有アクションと data object 宣言と event ゼロ" to
            SnapshotScenario(tabsScenarioSource(), tabsUsageSource()),
        "stay と emit と自己遷移と必須デフォルト混在の factory" to
            SnapshotScenario(wizardScenarioSource(), wizardUsageSource()),
        "exit と recover の宣言" to SnapshotScenario(authScenarioSource(), authUsageSource()),
        "DefaultName 付き中間 sealed の共有アクション" to SnapshotScenario(defaultNameScenarioSource()),
        "死にアクションを含む宣言" to SnapshotScenario(deadActionScenarioSource()),
        "data class 宣言の leaf state と prop 持ち越し" to
            SnapshotScenario(counterScenarioSource(), counterUsageSource()),
        "共有 prop の covariant override" to
            SnapshotScenario(covariantOverrideScenarioSource(), covariantOverrideUsageSource()),
        "internal 宣言の可視性継承" to
            SnapshotScenario(internalVisibilityScenarioSource(), internalVisibilityUsageSource()),
    )
