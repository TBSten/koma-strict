package me.tbsten.koma.strict.ksp.feature.storeSpec.scenario

import me.tbsten.koma.strict.ksp.testing.compile.SnapshotSource
import me.tbsten.koma.strict.ksp.testing.generator.Generator
import me.tbsten.koma.strict.ksp.testing.scenario.SnapshotScenario
import me.tbsten.koma.strict.ksp.testing.scenario.snapshotScenarios

/**
 * 宣言の形に対する診断 scenario 群 (error-as-golden)。
 * handler 宣言まわりの診断は [storeSpecHandlerDiagnosticsScenarios]。
 */
internal fun storeSpecDiagnosticsScenarios(): Generator<SnapshotScenario> =
    Generator.snapshotScenarios(
        "sealed でない class への StoreSpec はエラー" to
            SnapshotScenario(
                SnapshotSource(
                    fileName = "NotSealed.kt",
                    code =
                        """
                        package example.diag

                        import koma.core.State
                        import me.tbsten.koma.strict.StoreSpec

                        @StoreSpec
                        interface NotSealed : State {
                            companion object
                        }
                        """.trimIndent(),
                ),
            ),
        "型パラメータ付き state はエラー" to
            SnapshotScenario(
                SnapshotSource(
                    fileName = "GenericState.kt",
                    code =
                        """
                        package example.diag

                        import koma.core.Action
                        import koma.core.State
                        import me.tbsten.koma.strict.OnAction
                        import me.tbsten.koma.strict.StoreSpec

                        @StoreSpec
                        sealed interface GenericState : State {
                            companion object

                            @OnAction<GenericAction.Go>(nextState = [Done::class])
                            interface Holder<T> : GenericState { companion object }

                            interface Done : GenericState { companion object }
                        }

                        sealed interface GenericAction : Action {
                            data object Go : GenericAction
                        }
                        """.trimIndent(),
                ),
            ),
        "宣言を持つ state に companion が無いとエラー" to
            SnapshotScenario(
                SnapshotSource(
                    fileName = "NoCompanion.kt",
                    code =
                        """
                        package example.diag

                        import koma.core.Action
                        import koma.core.State
                        import me.tbsten.koma.strict.OnAction
                        import me.tbsten.koma.strict.StoreSpec

                        @StoreSpec
                        sealed interface NoCompanionState : State {
                            companion object

                            @OnAction<NcAction.Go>(nextState = [Done::class])
                            interface Start : NoCompanionState

                            interface Done : NoCompanionState { companion object }
                        }

                        sealed interface NcAction : Action {
                            data object Go : NcAction
                        }
                        """.trimIndent(),
                ),
            ),
        "state 名が default ブロック名と衝突するとエラー" to
            SnapshotScenario(
                SnapshotSource(
                    fileName = "DefaultClash.kt",
                    code =
                        """
                        package example.diag

                        import koma.core.Action
                        import koma.core.State
                        import me.tbsten.koma.strict.OnAction
                        import me.tbsten.koma.strict.StoreSpec

                        @StoreSpec
                        @OnAction<ClashAction.Go>(nextState = [ClashState.Ok::class])
                        sealed interface ClashState : State {
                            companion object

                            @OnAction<ClashAction.Other>(nextState = [Ok::class])
                            interface Default : ClashState { companion object }

                            interface Ok : ClashState { companion object }
                        }

                        sealed interface ClashAction : Action {
                            data object Go : ClashAction
                            data object Other : ClashAction
                        }
                        """.trimIndent(),
                ),
            ),
        "アクション宣言ゼロで actions を推論できないとエラー" to
            SnapshotScenario(
                SnapshotSource(
                    fileName = "NoActions.kt",
                    code =
                        """
                        package example.diag

                        import koma.core.State
                        import me.tbsten.koma.strict.OnEnter
                        import me.tbsten.koma.strict.StoreSpec

                        @StoreSpec
                        sealed interface NoActionsState : State {
                            companion object

                            @OnEnter(nextState = [Done::class])
                            interface Loading : NoActionsState { companion object }

                            interface Done : NoActionsState { companion object }
                        }
                        """.trimIndent(),
                ),
            ),
        "actions 明示と OnAction 型引数の矛盾はエラー" to
            SnapshotScenario(
                SnapshotSource(
                    fileName = "Contradiction.kt",
                    code =
                        """
                        package example.diag

                        import koma.core.Action
                        import koma.core.State
                        import me.tbsten.koma.strict.OnAction
                        import me.tbsten.koma.strict.StoreSpec

                        @StoreSpec(actions = MainAction::class)
                        sealed interface ContradictionState : State {
                            companion object

                            @OnAction<OtherAction.Foo>(nextState = [Done::class])
                            interface Start : ContradictionState { companion object }

                            interface Done : ContradictionState { companion object }
                        }

                        sealed interface MainAction : Action {
                            data object X : MainAction
                        }

                        sealed interface OtherAction : Action {
                            data object Foo : OtherAction
                        }
                        """.trimIndent(),
                ),
            ),
        "到達不能 state は警告" to
            SnapshotScenario(
                SnapshotSource(
                    fileName = "Unreachable.kt",
                    code =
                        """
                        package example.diag

                        import koma.core.Action
                        import koma.core.State
                        import me.tbsten.koma.strict.OnAction
                        import me.tbsten.koma.strict.StoreSpec

                        @StoreSpec(initial = [ReachState.Start::class])
                        sealed interface ReachState : State {
                            companion object

                            @OnAction<ReachAction.Go>(nextState = [Done::class])
                            interface Start : ReachState { companion object }

                            interface Done : ReachState { companion object }

                            @OnAction<ReachAction.Go>(nextState = [Done::class])
                            interface Orphan : ReachState { companion object }
                        }

                        sealed interface ReachAction : Action {
                            data object Go : ReachAction
                        }
                        """.trimIndent(),
                ),
            ),
    )
