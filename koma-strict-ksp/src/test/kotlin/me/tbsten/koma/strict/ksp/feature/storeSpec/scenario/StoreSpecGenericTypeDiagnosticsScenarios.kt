package me.tbsten.koma.strict.ksp.feature.storeSpec.scenario

import me.tbsten.koma.strict.ksp.testing.compile.SnapshotSource
import me.tbsten.koma.strict.ksp.testing.scenario.SnapshotScenario

/**
 * 型パラメータ付きの action / event / exception 型の v1 拒否診断 scenario 群
 * ([storeSpecHandlerDiagnosticsScenarios] に合流)。
 * state 側の同制約は [storeSpecDiagnosticsScenarios] が担う。
 * TypeRef 化で型引数が落ちるため、通すと raw 参照の壊れた生成物が出る — 明示拒否が正。
 */
internal fun genericTypeDiagnosticsCases(): Array<Pair<String, SnapshotScenario>> =
    arrayOf(
        "型パラメータ付き action を OnAction に指定するとエラー" to
            SnapshotScenario(
                SnapshotSource(
                    fileName = "GenericAction.kt",
                    code =
                        """
                        package example.diag

                        import koma.core.Action
                        import koma.core.State
                        import me.tbsten.koma.strict.OnAction
                        import me.tbsten.koma.strict.StoreSpec

                        @StoreSpec
                        sealed interface GaState : State {
                            companion object

                            @OnAction<GaAction.Paged<String>>(nextState = [Done::class])
                            interface Start : GaState { companion object }

                            interface Done : GaState { companion object }
                        }

                        sealed interface GaAction : Action {
                            data class Paged<T>(val items: List<T>) : GaAction
                        }
                        """.trimIndent(),
                ),
            ),
        "型パラメータ付き event を emit に指定するとエラー" to
            SnapshotScenario(
                SnapshotSource(
                    fileName = "GenericEvent.kt",
                    code =
                        """
                        package example.diag

                        import koma.core.Action
                        import koma.core.Event
                        import koma.core.State
                        import me.tbsten.koma.strict.OnAction
                        import me.tbsten.koma.strict.StoreSpec

                        @StoreSpec
                        sealed interface GeState : State {
                            companion object

                            @OnAction<GeAction.Go>(nextState = [Done::class], emit = [GeEvent.Loaded::class])
                            interface Start : GeState { companion object }

                            interface Done : GeState { companion object }
                        }

                        sealed interface GeAction : Action {
                            data object Go : GeAction
                        }

                        sealed interface GeEvent : Event {
                            data class Loaded<T>(val items: List<T>) : GeEvent
                        }
                        """.trimIndent(),
                ),
            ),
        "型パラメータ付き exception を OnRecover に指定するとエラー" to
            SnapshotScenario(
                SnapshotSource(
                    fileName = "GenericException.kt",
                    code =
                        """
                        package example.diag

                        import koma.core.Action
                        import koma.core.State
                        import me.tbsten.koma.strict.OnAction
                        import me.tbsten.koma.strict.OnRecover
                        import me.tbsten.koma.strict.StoreSpec

                        class GxException<T>(val payload: T) : Exception()

                        @StoreSpec
                        sealed interface GxState : State {
                            companion object

                            @OnAction<GxAction.Go>(nextState = [Done::class])
                            @OnRecover<GxException<String>>(nextState = [Done::class])
                            interface Start : GxState { companion object }

                            interface Done : GxState { companion object }
                        }

                        sealed interface GxAction : Action {
                            data object Go : GxAction
                        }
                        """.trimIndent(),
                ),
            ),
    )
