package me.tbsten.koma.strict.ksp.feature.storeSpec.scenario

import me.tbsten.koma.strict.ksp.testing.compile.SnapshotSource

// data class 宣言形 (StateDeclarationKind.DATA_CLASS) のケース。
// Impl / factory を生成せず遷移が `StateRef(args)` を直接構築すること、
// primary constructor パラメータの prop 持ち越し、nextState 省略 (= stay のみ) が見どころ。

/** data class 宣言の leaf state (prop 持ち越し / stay のみアクション / E = Nothing)。 */
internal fun counterScenarioSource(): SnapshotSource =
    SnapshotSource(
        fileName = "CounterState.kt",
        code =
            """
            package example.counter

            import koma.core.Action
            import koma.core.State
            import me.tbsten.koma.strict.OnAction
            import me.tbsten.koma.strict.StoreSpec

            @StoreSpec(initial = [CounterState.Idle::class])
            sealed interface CounterState : State {
                companion object

                @OnAction<CounterAction.Increment>(nextState = [Idle::class])       // 自己遷移
                @OnAction<CounterAction.Reset>(nextState = [Confirming::class])
                data class Idle(val count: Int) : CounterState { companion object } // data class 宣言 (従来通り可)

                @OnAction<CounterAction.Confirm>(nextState = [Idle::class])
                @OnAction<CounterAction.Cancel>                                     // nextState 省略 = stay のみ
                data class Confirming(val count: Int, val message: String? = null) : CounterState {
                    companion object
                }
            }

            sealed interface CounterAction : Action {
                data object Increment : CounterAction
                data object Reset : CounterAction
                data object Confirm : CounterAction
                data object Cancel : CounterAction
            }
            """.trimIndent(),
    )

/**
 * [counterScenarioSource] の利用側コード。data class は factory を経由せず
 * `CounterState.Idle(...)` 直接構築で遷移できること (prop 持ち越しの default 込み) の
 * コンパイル証明。
 */
internal fun counterUsageSource(): SnapshotSource =
    SnapshotSource(
        fileName = "CounterStoreUsage.kt",
        code =
            """
            package example.counter

            import koma.core.Store

            fun buildCounterStore(): Store<CounterState, CounterAction, Nothing> =
                Store<CounterState, CounterAction, Nothing>(initialState = CounterState.Idle(count = 0)) {
                    states(
                        idle = CounterState.Idle.actions(
                            increment = { nextState.toIdle(count = state.count + 1) },
                            // count は持ち越し / message は新規 = 必須 (data class 宣言の
                            // default 値は遷移関数へは伝播しない — デフォルト値の源は state のみ)
                            reset = { nextState.toConfirming(message = null) },
                        ),
                        confirming = CounterState.Confirming.actions(
                            confirm = { nextState.toIdle() },          // count は持ち越し
                            cancel = { stayState() },                  // stay のみ宣言のアクション
                        ),
                    )
                }
            """.trimIndent(),
    )
