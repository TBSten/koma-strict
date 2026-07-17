package me.tbsten.koma.strict.ksp.feature.storeSpec.scenario

import me.tbsten.koma.strict.ksp.testing.compile.SnapshotSource
import me.tbsten.koma.strict.ksp.testing.scenario.SnapshotScenario

/**
 * 生成 top-level 名 (型 + per-store factory 関数) の衝突診断 scenario 群
 * ([storeSpecTreeDiagnosticsScenarios] に合流)。
 * leaf の生成型 prefix は root を含まない (samples.md の短い型名が正) ため、
 * 同一 package 内の同名 leaf は redeclaration になる — KSP エラーで明示拒否する。
 * factory 関数名 (root 名の末尾 State strip 由来) の一致も同じ診断で拒否する。
 */
internal fun generatedNameCollisionDiagnosticsCases(): Array<Pair<String, SnapshotScenario>> =
    arrayOf(
        "同一 package の別 StoreSpec 階層に同名 leaf があるとエラー" to
            SnapshotScenario(
                SnapshotSource(
                    fileName = "FooScreenState.kt",
                    code =
                        """
                        package example.clash

                        import koma.core.Action
                        import koma.core.State
                        import me.tbsten.koma.strict.OnAction
                        import me.tbsten.koma.strict.StoreSpec

                        @StoreSpec
                        sealed interface FooScreenState : State {
                            companion object

                            @OnAction<FooScreenAction.Reload>(nextState = [Loading::class])
                            interface Loading : FooScreenState { companion object }
                        }

                        sealed interface FooScreenAction : Action {
                            data object Reload : FooScreenAction
                        }
                        """.trimIndent(),
                ),
                SnapshotSource(
                    fileName = "BarScreenState.kt",
                    code =
                        """
                        package example.clash

                        import koma.core.Action
                        import koma.core.State
                        import me.tbsten.koma.strict.OnAction
                        import me.tbsten.koma.strict.StoreSpec

                        @StoreSpec
                        sealed interface BarScreenState : State {
                            companion object

                            // FooScreenState.Loading と同名 leaf + 同名アクション ->
                            // LoadingImpl / LoadingReloadReaction / LoadingHandlers 等が衝突する
                            @OnAction<BarScreenAction.Reload>(nextState = [Loading::class])
                            interface Loading : BarScreenState { companion object }
                        }

                        sealed interface BarScreenAction : Action {
                            data object Reload : BarScreenAction
                        }
                        """.trimIndent(),
                ),
            ),
        "root 名の State strip で factory 関数名が一致するとエラー" to
            SnapshotScenario(
                SnapshotSource(
                    fileName = "PlayerStates.kt",
                    code =
                        """
                        package example.clash

                        import koma.core.Action
                        import koma.core.State
                        import me.tbsten.koma.strict.OnAction
                        import me.tbsten.koma.strict.StoreSpec

                        // leaf 名は互いに異なる (型は衝突しない) が、root 名の末尾 State を strip した
                        // factory 関数名がどちらも playerStore になる
                        @StoreSpec
                        sealed interface PlayerState : State {
                            companion object

                            @OnAction<PlayerAction.Play>(nextState = [Playing::class])
                            interface Playing : PlayerState { companion object }
                        }

                        @StoreSpec
                        sealed interface Player : State {
                            companion object

                            @OnAction<PlayerAction.Play>(nextState = [Paused::class])
                            interface Paused : Player { companion object }
                        }

                        sealed interface PlayerAction : Action {
                            data object Play : PlayerAction
                        }
                        """.trimIndent(),
                ),
            ),
        "同一階層内で path 連結が一致する leaf があるとエラー" to
            SnapshotScenario(
                SnapshotSource(
                    fileName = "NestState.kt",
                    code =
                        """
                        package example.clash

                        import koma.core.Action
                        import koma.core.State
                        import me.tbsten.koma.strict.OnAction
                        import me.tbsten.koma.strict.StoreSpec

                        @StoreSpec
                        sealed interface NestState : State {
                            companion object

                            // path 連結 (Stable + Idle) と単一 leaf 名 StableIdle が同じ prefix になる
                            @OnAction<NsAction.Go>(nextState = [StableIdle::class])
                            interface StableIdle : NestState { companion object }

                            sealed interface Stable : NestState {
                                companion object

                                @OnAction<NsAction.Go>(nextState = [Idle::class])
                                interface Idle : Stable { companion object }
                            }
                        }

                        sealed interface NsAction : Action {
                            data object Go : NsAction
                        }
                        """.trimIndent(),
                ),
            ),
    )
