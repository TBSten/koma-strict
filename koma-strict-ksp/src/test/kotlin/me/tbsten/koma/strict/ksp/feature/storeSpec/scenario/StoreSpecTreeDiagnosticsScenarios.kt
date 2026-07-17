package me.tbsten.koma.strict.ksp.feature.storeSpec.scenario

import me.tbsten.koma.strict.ksp.testing.compile.SnapshotSource
import me.tbsten.koma.strict.ksp.testing.generator.Generator
import me.tbsten.koma.strict.ksp.testing.scenario.SnapshotScenario
import me.tbsten.koma.strict.ksp.testing.scenario.snapshotScenarios

/**
 * state ツリーの形 (宣言の種類 / 可視性 / ネスト / companion / initial) に対する診断 scenario 群。
 * annotation の形の診断は [storeSpecDiagnosticsScenarios] / [storeSpecHandlerDiagnosticsScenarios]、
 * 共有宣言・推論の診断は [storeSpecSharedDiagnosticsScenarios]、
 * 生成型名の衝突診断は [generatedNameCollisionDiagnosticsCases] (別ファイル) をこの family に合流。
 */
internal fun storeSpecTreeDiagnosticsScenarios(): Generator<SnapshotScenario> =
    Generator.snapshotScenarios(
        *generatedNameCollisionDiagnosticsCases(),
        "private の state はエラー" to
            SnapshotScenario(
                SnapshotSource(
                    fileName = "PrivateState.kt",
                    code =
                        """
                        package example.diag

                        import koma.core.Action
                        import koma.core.State
                        import me.tbsten.koma.strict.OnAction
                        import me.tbsten.koma.strict.StoreSpec

                        @StoreSpec
                        sealed interface PvState : State {
                            companion object

                            @OnAction<PvAction.Go>(nextState = [Done::class])
                            interface Start : PvState { companion object }

                            // 生成物 (top-level) が参照できない可視性は v1 で明示拒否する
                            private interface Done : PvState { companion object }
                        }

                        sealed interface PvAction : Action {
                            data object Go : PvAction
                        }
                        """.trimIndent(),
                ),
            ),
        "enum class の state はエラー" to
            SnapshotScenario(
                SnapshotSource(
                    fileName = "EnumState.kt",
                    code =
                        """
                        package example.diag

                        import koma.core.Action
                        import koma.core.State
                        import me.tbsten.koma.strict.OnAction
                        import me.tbsten.koma.strict.StoreSpec

                        @StoreSpec
                        sealed interface EnumHolderState : State {
                            companion object

                            @OnAction<EhAction.Go>(nextState = [Done::class])
                            interface Start : EnumHolderState { companion object }

                            interface Done : EnumHolderState { companion object }

                            enum class Mode : EnumHolderState { A, B }   // enum は state として不可
                        }

                        sealed interface EhAction : Action {
                            data object Go : EhAction
                        }
                        """.trimIndent(),
                ),
            ),
        "ネスト外の sealed subtype はエラー" to
            SnapshotScenario(
                SnapshotSource(
                    fileName = "Outside.kt",
                    code =
                        """
                        package example.diag

                        import koma.core.Action
                        import koma.core.State
                        import me.tbsten.koma.strict.OnAction
                        import me.tbsten.koma.strict.StoreSpec

                        @StoreSpec
                        sealed interface OutsideState : State {
                            companion object

                            @OnAction<OsAction.Go>(nextState = [Done::class])
                            interface Start : OutsideState { companion object }

                            interface Done : OutsideState { companion object }
                        }

                        // body の外に置かれた subtype: 黙って無視せず tree 形を強制する
                        interface Stray : OutsideState { companion object }

                        sealed interface OsAction : Action {
                            data object Go : OsAction
                        }
                        """.trimIndent(),
                ),
            ),
        "宣言を持つ子を束ねる中間 sealed に companion が無いとエラー" to
            SnapshotScenario(
                SnapshotSource(
                    fileName = "GroupNoCompanion.kt",
                    code =
                        """
                        package example.diag

                        import koma.core.Action
                        import koma.core.State
                        import me.tbsten.koma.strict.OnAction
                        import me.tbsten.koma.strict.StoreSpec

                        @StoreSpec
                        sealed interface GncState : State {
                            companion object

                            @OnAction<GncAction.Go>(nextState = [Grouped.Inner::class])
                            interface Start : GncState { companion object }

                            // 自身は宣言ゼロでも、子が宣言を持つ = states() 束ね拡張の生やし先が必要
                            sealed interface Grouped : GncState {
                                @OnAction<GncAction.Back>(nextState = [Start::class])
                                interface Inner : Grouped { companion object }
                            }
                        }

                        sealed interface GncAction : Action {
                            data object Go : GncAction
                            data object Back : GncAction
                        }
                        """.trimIndent(),
                ),
            ),
        "companion 直後の data object が companion 名として食われると警告" to
            SnapshotScenario(
                SnapshotSource(
                    fileName = "SuspiciousCompanion.kt",
                    code =
                        """
                        package example.diag

                        import koma.core.Action
                        import koma.core.State
                        import me.tbsten.koma.strict.OnAction
                        import me.tbsten.koma.strict.StoreSpec

                        @StoreSpec
                        sealed interface SuspiciousState : State {
                            companion object
                            // kotlinc はこの `data` を companion の名前として消費する
                            // (companion 名 = `data` + 非 data の `object Home`)
                            data object Home : SuspiciousState

                            @OnAction<ScAction.Go>(nextState = [Home::class])
                            interface Away : SuspiciousState { companion object }
                        }

                        sealed interface ScAction : Action {
                            data object Go : ScAction
                        }
                        """.trimIndent(),
                ),
            ),
        "initial に中間 sealed を指定するとエラー" to
            SnapshotScenario(
                SnapshotSource(
                    fileName = "InitialGroup.kt",
                    code =
                        """
                        package example.diag

                        import koma.core.Action
                        import koma.core.State
                        import me.tbsten.koma.strict.OnAction
                        import me.tbsten.koma.strict.StoreSpec

                        @StoreSpec(initial = [IgState.Group::class])
                        sealed interface IgState : State {
                            companion object

                            @OnAction<IgAction.Go>(nextState = [Group.Leaf::class])
                            interface Start : IgState { companion object }

                            sealed interface Group : IgState {
                                companion object

                                interface Leaf : Group { companion object }
                            }
                        }

                        sealed interface IgAction : Action {
                            data object Go : IgAction
                        }
                        """.trimIndent(),
                ),
            ),
        "initial に階層外の型を指定するとエラー" to
            SnapshotScenario(
                SnapshotSource(
                    fileName = "InitialForeign.kt",
                    code =
                        """
                        package example.diag

                        import koma.core.Action
                        import koma.core.State
                        import me.tbsten.koma.strict.OnAction
                        import me.tbsten.koma.strict.StoreSpec

                        class NotAState

                        @StoreSpec(initial = [NotAState::class])
                        sealed interface IfState : State {
                            companion object

                            @OnAction<IfAction.Go>(nextState = [Done::class])
                            interface Start : IfState { companion object }

                            interface Done : IfState { companion object }
                        }

                        sealed interface IfAction : Action {
                            data object Go : IfAction
                        }
                        """.trimIndent(),
                ),
            ),
    )
