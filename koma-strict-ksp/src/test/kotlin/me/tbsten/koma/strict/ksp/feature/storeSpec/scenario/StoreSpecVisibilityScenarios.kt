package me.tbsten.koma.strict.ksp.feature.storeSpec.scenario

import me.tbsten.koma.strict.ksp.testing.compile.SnapshotSource

// 可視性継承ケース: internal な state 宣言 -> 生成物 (支援型 / factory / states() 拡張) も
// internal になる (doc §生成物の可視性ポリシー「public (state 宣言の可視性を継承)」)。

/** internal sealed root の宣言。生成物の可視性が internal に落ちることが見どころ。 */
internal fun internalVisibilityScenarioSource(): SnapshotSource =
    SnapshotSource(
        fileName = "HiddenState.kt",
        code =
            """
            package example.hidden

            import koma.core.Action
            import koma.core.State
            import me.tbsten.koma.strict.OnAction
            import me.tbsten.koma.strict.StoreSpec

            @StoreSpec(initial = [HiddenState.Idle::class])
            internal sealed interface HiddenState : State {   // internal 宣言 -> 生成物も internal
                companion object

                @OnAction<HiddenAction.Toggle>(nextState = [Active::class])
                interface Idle : HiddenState { companion object }

                @OnAction<HiddenAction.Toggle>(nextState = [Idle::class])
                interface Active : HiddenState { val startedAt: Long; companion object }
            }

            internal sealed interface HiddenAction : Action {
                data object Toggle : HiddenAction
            }
            """.trimIndent(),
    )

/** [internalVisibilityScenarioSource] の利用側コード (同一モジュール内から internal 生成物が使える)。 */
internal fun internalVisibilityUsageSource(): SnapshotSource =
    SnapshotSource(
        fileName = "HiddenStoreUsage.kt",
        code =
            """
            package example.hidden

            import koma.core.Store

            internal fun buildHiddenStore(): Store<HiddenState, HiddenAction, Nothing> =
                Store<HiddenState, HiddenAction, Nothing>(initialState = HiddenState.Idle()) {
                    states(
                        idle = HiddenState.Idle.actions(toggle = { nextState.toActive(startedAt = 0L) }),
                        active = HiddenState.Active.actions(toggle = { nextState.toIdle() }),
                    )
                }
            """.trimIndent(),
    )
