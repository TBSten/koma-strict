package me.tbsten.koma.strict.ksp.feature.storeSpec.scenario

import me.tbsten.koma.strict.ksp.testing.compile.SnapshotSource
import me.tbsten.koma.strict.ksp.testing.generator.Generator
import me.tbsten.koma.strict.ksp.testing.scenario.SnapshotScenario
import me.tbsten.koma.strict.ksp.testing.scenario.snapshotScenarios

/**
 * handler 宣言 (@OnAction / @OnEnter / @OnRecover / nextState) まわりの診断 scenario 群。
 * 型パラメータ付き action / event / exception の診断は
 * [genericTypeDiagnosticsCases] (別ファイル) をこの family に合流させる。
 */
internal fun storeSpecHandlerDiagnosticsScenarios(): Generator<SnapshotScenario> =
    Generator.snapshotScenarios(
        *genericTypeDiagnosticsCases(),
        "nextState に中間 sealed を指定するとエラー" to
            SnapshotScenario(
                SnapshotSource(
                    fileName = "GroupTarget.kt",
                    code =
                        """
                        package example.diag

                        import koma.core.Action
                        import koma.core.State
                        import me.tbsten.koma.strict.OnAction
                        import me.tbsten.koma.strict.StoreSpec

                        @StoreSpec
                        sealed interface GtState : State {
                            companion object

                            @OnAction<GtAction.Go>(nextState = [Group::class])
                            interface Start : GtState { companion object }

                            sealed interface Group : GtState {
                                companion object

                                interface Leaf : Group { companion object }
                            }
                        }

                        sealed interface GtAction : Action {
                            data object Go : GtAction
                        }
                        """.trimIndent(),
                ),
            ),
        "nextState に階層外の型を指定するとエラー" to
            SnapshotScenario(
                SnapshotSource(
                    fileName = "ForeignTarget.kt",
                    code =
                        """
                        package example.diag

                        import koma.core.Action
                        import koma.core.State
                        import me.tbsten.koma.strict.OnAction
                        import me.tbsten.koma.strict.StoreSpec

                        // State は実装している (nextState の KClass<out State> 境界は満たす) が
                        // FtState 階層の外 → KSP 診断で検出されるケース
                        class Foreign : State

                        @StoreSpec
                        sealed interface FtState : State {
                            companion object

                            @OnAction<FtAction.Go>(nextState = [Foreign::class])
                            interface Start : FtState { companion object }
                        }

                        sealed interface FtAction : Action {
                            data object Go : FtAction
                        }
                        """.trimIndent(),
                ),
            ),
        "同一 state への同一アクションの重複宣言はエラー" to
            SnapshotScenario(
                SnapshotSource(
                    fileName = "DupSameNode.kt",
                    code =
                        """
                        package example.diag

                        import koma.core.Action
                        import koma.core.State
                        import me.tbsten.koma.strict.OnAction
                        import me.tbsten.koma.strict.StoreSpec

                        @StoreSpec
                        sealed interface DupState : State {
                            companion object

                            @OnAction<DupAction.Go>(nextState = [Done::class])
                            @OnAction<DupAction.Go>
                            interface Start : DupState { companion object }

                            interface Done : DupState { companion object }
                        }

                        sealed interface DupAction : Action {
                            data object Go : DupAction
                        }
                        """.trimIndent(),
                ),
            ),
        "祖先と子孫での同一アクションの重複宣言はエラー" to
            SnapshotScenario(
                SnapshotSource(
                    fileName = "DupAncestor.kt",
                    code =
                        """
                        package example.diag

                        import koma.core.Action
                        import koma.core.State
                        import me.tbsten.koma.strict.OnAction
                        import me.tbsten.koma.strict.StoreSpec

                        @StoreSpec
                        @OnAction<DaAction.Go>(nextState = [DaState.Done::class])
                        sealed interface DaState : State {
                            companion object

                            @OnAction<DaAction.Go>(nextState = [Done::class])
                            interface Start : DaState { companion object }

                            interface Done : DaState { companion object }
                        }

                        sealed interface DaAction : Action {
                            data object Go : DaAction
                        }
                        """.trimIndent(),
                ),
            ),
        "同一 state への同一例外型の重複 OnRecover はエラー" to
            SnapshotScenario(
                SnapshotSource(
                    fileName = "DupRecover.kt",
                    code =
                        """
                        package example.diag

                        import koma.core.Action
                        import koma.core.State
                        import me.tbsten.koma.strict.OnAction
                        import me.tbsten.koma.strict.OnRecover
                        import me.tbsten.koma.strict.StoreSpec

                        class DrException : Exception()

                        @StoreSpec
                        sealed interface DrState : State {
                            companion object

                            @OnAction<DrAction.Go>(nextState = [Done::class])
                            @OnRecover<DrException>(nextState = [Done::class])
                            @OnRecover<DrException>
                            interface Start : DrState { companion object }

                            interface Done : DrState { companion object }
                        }

                        sealed interface DrAction : Action {
                            data object Go : DrAction
                        }
                        """.trimIndent(),
                ),
            ),
        "OnEnter を中間 sealed に付与するとエラー" to
            SnapshotScenario(
                SnapshotSource(
                    fileName = "EnterOnGroup.kt",
                    code =
                        """
                        package example.diag

                        import koma.core.Action
                        import koma.core.State
                        import me.tbsten.koma.strict.OnAction
                        import me.tbsten.koma.strict.OnEnter
                        import me.tbsten.koma.strict.StoreSpec

                        @StoreSpec
                        sealed interface EgState : State {
                            companion object

                            @OnEnter(nextState = [Group.Leaf::class])
                            sealed interface Group : EgState {
                                companion object

                                @OnAction<EgAction.Go>(nextState = [Leaf::class])
                                interface Leaf : Group { companion object }
                            }
                        }

                        sealed interface EgAction : Action {
                            data object Go : EgAction
                        }
                        """.trimIndent(),
                ),
            ),
    )
