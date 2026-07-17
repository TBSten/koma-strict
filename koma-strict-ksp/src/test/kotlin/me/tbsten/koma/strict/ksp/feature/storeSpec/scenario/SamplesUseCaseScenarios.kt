package me.tbsten.koma.strict.ksp.feature.storeSpec.scenario

import me.tbsten.koma.strict.ksp.testing.generator.Generator
import me.tbsten.koma.strict.ksp.testing.scenario.SnapshotScenario
import me.tbsten.koma.strict.ksp.testing.scenario.snapshotScenarios

/**
 * doc/internal/samples.md の 5 ユースケースを「宣言 (user が書く) + 利用側 (user が書く)」
 * まるごと忠実に写経した scenario 群
 * ([me.tbsten.koma.strict.ksp.feature.storeSpec.StoreSpecUseCasesTest] 専用ファミリ)。
 *
 * 機能軸の [storeSpecScenarios] と入力が一部重複するが目的が異なる:
 * こちらは **samples.md の記載と実装の乖離検知** が目的のため、samples.md の記述を
 * (コンパイル不能な箇所の最小調整を除き) そのまま入力にする。重複 golden は許容する。
 * samples.md を改訂したらこのファミリも追随させること。
 *
 * ケース名は samples.md の見出しと 1:1 対応。samples.md からの調整点は
 * 各 `Samples*UseCaseScenario.kt` の冒頭コメントと入力内コメントに明記している。
 */
internal fun samplesUseCaseScenarios(): Generator<SnapshotScenario> =
    Generator.snapshotScenarios(
        "基本 LCE" to samplesLceUseCase(),
        "LCE + pull-to-refresh + additional load" to samplesFeedUseCase(),
        "タブ切替" to samplesTabsUseCase(),
        "フォームウィザード" to samplesWizardUseCase(),
        "認証 + セッション切れ" to samplesAuthUseCase(),
    )
