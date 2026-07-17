package me.tbsten.koma.strict.ksp.feature.storeSpec.scenario

import me.tbsten.koma.strict.ksp.testing.compile.SnapshotSource
import me.tbsten.koma.strict.ksp.testing.generator.Generator
import me.tbsten.koma.strict.ksp.testing.scenario.SnapshotScenario
import me.tbsten.koma.strict.ksp.testing.scenario.snapshotScenarios

// 利用側 DSL の「書き方の形」の e2e: scope lambda / 値渡しの両対応・混在、
// 自宣言 + 子を持つ中間 sealed の `actions(...) + states(...)` plus 合成、
// および plus の片方忘れが型エラーになること (error-as-golden)。
// 宣言は既存の lce (StoreSpecBasicScenarios) / flow (StoreSpecHierarchyScenarios) を共用する。

/**
 * 旧 scope lambda 形式 (`loading = { actions(...) }`) と値渡し
 * (`content = LceState.Content.actions(...)`) が同一 states() 呼び出しで混在できることの
 * コンパイル証明。scope ミラーの configure (per-state escape hatch) も scope lambda 側で使う。
 */
internal fun mixedDslFormsUsageSource(): SnapshotSource =
    SnapshotSource(
        fileName = "LceMixedFormsUsage.kt",
        code =
            """
            package example.lce

            import koma.core.Store

            fun buildMixedFormsLceStore(): Store<LceState, LceAction, LceEvent> =
                Store<LceState, LceAction, LceEvent>(initialState = LceState.Loading()) {
                    states(
                        loading = {
                            // 旧 scope lambda 形式 (HandlersScope の actions() ミラー)
                            actions(
                                enter = { nextState.toContent(data = "fetched") },
                            )
                        },
                        // 値渡し (Handlers の Function1 自己返し実装でそのまま渡る)
                        content = LceState.Content.actions(reload = { nextState.toLoading() }),
                        error = {
                            actions(
                                retry = { nextState.toLoading() },
                                configure = { exit { } }, // ミラーにも per-state escape hatch がある
                            )
                        },
                    )
                }
            """.trimIndent(),
    )

/**
 * 自宣言 + 子を持つ中間 sealed (`FlowState.Refresh`) の合成 3 形のコンパイル証明:
 * 値渡し plus / scope lambda 内 plus / 従来形 (default 込み states() が合成型を直接返す)。
 */
internal fun plusCompositionUsageSource(): SnapshotSource =
    SnapshotSource(
        fileName = "FlowPlusUsage.kt",
        code =
            """
            package example.flow

            import koma.core.Store

            fun buildFlowStoreWithPlus(): Store<FlowState, FlowAction, Nothing> =
                Store<FlowState, FlowAction, Nothing>(initialState = FlowState.Idle()) {
                    states(
                        idle = FlowState.Idle.actions(start = { nextState.toRefreshRunning() }),
                        // 自宣言の束 (actions) + 子のみの束 (states) の plus 合成だけが親 param 型を満たす
                        refresh = FlowState.Refresh.actions(
                            cancel = { nextState.toIdle() },
                        ) + FlowState.Refresh.states(
                            failed = FlowState.Refresh.Failed.actions(retry = { nextState.toRunning() }),
                        ),
                    )
                }

            fun buildFlowStoreWithScopeLambdaPlus(): Store<FlowState, FlowAction, Nothing> =
                Store<FlowState, FlowAction, Nothing>(initialState = FlowState.Idle()) {
                    states(
                        idle = { actions(start = { nextState.toRefreshRunning() }) },
                        refresh = {
                            // 合成 scope のミラー同士でも plus 合成が書ける
                            actions(
                                cancel = { nextState.toIdle() },
                            ) + states(
                                failed = { actions(retry = { nextState.toRunning() }) },
                            )
                        },
                    )
                }

            fun buildFlowStoreWithCombinedStates(): Store<FlowState, FlowAction, Nothing> =
                Store<FlowState, FlowAction, Nothing>(initialState = FlowState.Idle()) {
                    states(
                        idle = FlowState.Idle.actions(start = { nextState.toRefreshRunning() }),
                        // 従来形: default (refreshCommon) 込みの states() は合成型を直接返す
                        refresh = FlowState.Refresh.states(
                            refreshCommon = FlowState.Refresh.actions(cancel = { nextState.toIdle() }),
                            failed = FlowState.Refresh.Failed.actions(retry = { nextState.toRunning() }),
                        ),
                    )
                }
            """.trimIndent(),
    )

/**
 * plus 合成の片方忘れが型エラーになることの error golden 用 usage。
 * 親 param 型は合成型なので、DefaultHandlers 単独 (states 忘れ) も GroupHandlers 単独
 * (actions 忘れ) もコンパイルエラーになる = 「共有 handler の足し忘れ」「子の束ね忘れ」の両方を型で防ぐ。
 */
internal fun plusCompositionMissingHalfUsageSource(): SnapshotSource =
    SnapshotSource(
        fileName = "FlowPlusMissingHalfUsage.kt",
        code =
            """
            package example.flow

            import koma.core.Store

            fun buildFlowStoreMissingStates(): Store<FlowState, FlowAction, Nothing> =
                Store<FlowState, FlowAction, Nothing>(initialState = FlowState.Idle()) {
                    states(
                        idle = FlowState.Idle.actions(start = { nextState.toRefreshRunning() }),
                        // states(...) の + し忘れ: DefaultHandlers 単独は合成型 param に渡せない
                        refresh = FlowState.Refresh.actions(cancel = { nextState.toIdle() }),
                    )
                }

            fun buildFlowStoreMissingActions(): Store<FlowState, FlowAction, Nothing> =
                Store<FlowState, FlowAction, Nothing>(initialState = FlowState.Idle()) {
                    states(
                        idle = FlowState.Idle.actions(start = { nextState.toRefreshRunning() }),
                        // actions(...) の + し忘れ: GroupHandlers 単独も合成型 param に渡せない
                        refresh = FlowState.Refresh.states(
                            failed = FlowState.Refresh.Failed.actions(retry = { nextState.toRunning() }),
                        ),
                    )
                }
            """.trimIndent(),
    )

/** 利用側 DSL の形の診断 (kotlinc の型エラーを error golden として固定する) scenario 群。 */
internal fun storeSpecDslFormDiagnosticsScenarios(): Generator<SnapshotScenario> =
    Generator.snapshotScenarios(
        "plus 合成の片方忘れはコンパイルエラー" to
            SnapshotScenario(defaultNameScenarioSource(), plusCompositionMissingHalfUsageSource()),
        "enter 宣言を持つ state への builder 形式はコンパイルエラー" to
            SnapshotScenario(lceScenarioSource(), builderFormOnEnterStateUsageSource()),
    )
