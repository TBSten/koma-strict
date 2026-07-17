package me.tbsten.koma.strict.ksp.feature.storeSpec.scenario

import me.tbsten.koma.strict.ksp.testing.compile.SnapshotSource
import me.tbsten.koma.strict.ksp.testing.generator.Generator
import me.tbsten.koma.strict.ksp.testing.scenario.SnapshotScenario
import me.tbsten.koma.strict.ksp.testing.scenario.snapshotScenarios

/**
 * 共有宣言 (祖先展開) と actions / events 推論に対する診断 scenario 群。
 * state ツリーの形の診断は [storeSpecTreeDiagnosticsScenarios]。
 */
internal fun storeSpecSharedDiagnosticsScenarios(): Generator<SnapshotScenario> =
    Generator.snapshotScenarios(
        "祖先と子孫での同一例外型の重複 OnRecover はエラー" to
            SnapshotScenario(
                SnapshotSource(
                    fileName = "DupRecoverAncestor.kt",
                    code =
                        """
                        package example.diag

                        import koma.core.Action
                        import koma.core.State
                        import me.tbsten.koma.strict.OnAction
                        import me.tbsten.koma.strict.OnRecover
                        import me.tbsten.koma.strict.StoreSpec

                        class DraException : Exception()

                        @StoreSpec
                        @OnRecover<DraException>(nextState = [DraState.Done::class])
                        sealed interface DraState : State {
                            companion object

                            @OnAction<DraAction.Go>(nextState = [Done::class])
                            @OnRecover<DraException>(nextState = [Done::class])
                            interface Start : DraState { companion object }

                            interface Done : DraState { companion object }
                        }

                        sealed interface DraAction : Action {
                            data object Go : DraAction
                        }
                        """.trimIndent(),
                ),
            ),
        "祖先と子孫での重複 OnExit はエラー" to
            SnapshotScenario(
                SnapshotSource(
                    fileName = "DupExitAncestor.kt",
                    code =
                        """
                        package example.diag

                        import koma.core.Action
                        import koma.core.Event
                        import koma.core.State
                        import me.tbsten.koma.strict.OnAction
                        import me.tbsten.koma.strict.OnExit
                        import me.tbsten.koma.strict.StoreSpec

                        @StoreSpec
                        @OnExit(emit = [DeaEvent.Left::class])
                        sealed interface DeaState : State {
                            companion object

                            @OnAction<DeaAction.Go>(nextState = [Done::class])
                            @OnExit(emit = [DeaEvent.Left::class])
                            interface Start : DeaState { companion object }

                            interface Done : DeaState { companion object }
                        }

                        sealed interface DeaAction : Action {
                            data object Go : DeaAction
                        }

                        sealed interface DeaEvent : Event {
                            data object Left : DeaEvent
                        }
                        """.trimIndent(),
                ),
            ),
        "leaf への DefaultName はエラー" to
            SnapshotScenario(
                SnapshotSource(
                    fileName = "DefaultNameOnLeaf.kt",
                    code =
                        """
                        package example.diag

                        import koma.core.Action
                        import koma.core.State
                        import me.tbsten.koma.strict.DefaultName
                        import me.tbsten.koma.strict.OnAction
                        import me.tbsten.koma.strict.StoreSpec

                        @StoreSpec
                        sealed interface DnlState : State {
                            companion object

                            @DefaultName("common")   // leaf には付けられない
                            @OnAction<DnlAction.Go>(nextState = [Done::class])
                            interface Start : DnlState { companion object }

                            interface Done : DnlState { companion object }
                        }

                        sealed interface DnlAction : Action {
                            data object Go : DnlAction
                        }
                        """.trimIndent(),
                ),
            ),
        "共通 sealed supertype を推論できない actions はエラー" to
            SnapshotScenario(
                SnapshotSource(
                    fileName = "SplitActions.kt",
                    code =
                        """
                        package example.diag

                        import koma.core.Action
                        import koma.core.State
                        import me.tbsten.koma.strict.OnAction
                        import me.tbsten.koma.strict.StoreSpec

                        @StoreSpec
                        sealed interface SplitState : State {
                            companion object

                            @OnAction<FirstAction.Go>(nextState = [Other::class])
                            interface Start : SplitState { companion object }

                            @OnAction<SecondAction.Back>(nextState = [Start::class])
                            interface Other : SplitState { companion object }
                        }

                        sealed interface FirstAction : Action {
                            data object Go : FirstAction
                        }

                        sealed interface SecondAction : Action {
                            data object Back : SecondAction
                        }
                        """.trimIndent(),
                ),
            ),
        "events 明示と emit 宣言の矛盾はエラー" to
            SnapshotScenario(
                SnapshotSource(
                    fileName = "EventContradiction.kt",
                    code =
                        """
                        package example.diag

                        import koma.core.Action
                        import koma.core.Event
                        import koma.core.State
                        import me.tbsten.koma.strict.OnAction
                        import me.tbsten.koma.strict.StoreSpec

                        @StoreSpec(events = MainEvent::class)
                        sealed interface EcState : State {
                            companion object

                            @OnAction<EcAction.Go>(nextState = [Done::class], emit = [OtherEvent.Boom::class])
                            interface Start : EcState { companion object }

                            interface Done : EcState { companion object }
                        }

                        sealed interface EcAction : Action {
                            data object Go : EcAction
                        }

                        sealed interface MainEvent : Event {
                            data object X : MainEvent
                        }

                        sealed interface OtherEvent : Event {
                            data object Boom : OtherEvent
                        }
                        """.trimIndent(),
                ),
            ),
    )
