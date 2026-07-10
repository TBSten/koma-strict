package me.tbsten.koma.strict.ksp.feature.strictStore.scenario

import me.tbsten.koma.strict.ksp.testing.compile.SnapshotSource
import me.tbsten.koma.strict.ksp.testing.generator.Generator
import me.tbsten.koma.strict.ksp.testing.scenario.SnapshotScenario
import me.tbsten.koma.strict.ksp.testing.scenario.snapshotScenarios

/** strictStore feature の snapshot scenario 群 (手書き source)。 */
internal fun strictStoreScenarios(): Generator<SnapshotScenario> =
    Generator.snapshotScenarios(
        "sealed interface の基本形" to
            SnapshotScenario(
                SnapshotSource(
                    fileName = "MyState.kt",
                    code =
                        """
                        package example

                        import me.tbsten.koma.strict.StrictStore

                        @StrictStore
                        sealed interface MyState {
                            data object Loading : MyState
                            data class Success(val data: String) : MyState
                        }
                        """.trimIndent(),
                ),
            ),
        "入れ子の sealed 階層" to
            SnapshotScenario(
                SnapshotSource(
                    fileName = "NestedState.kt",
                    code =
                        """
                        package example

                        import me.tbsten.koma.strict.StrictStore

                        @StrictStore
                        sealed interface NestedState {
                            data object Loading : NestedState

                            sealed interface Stable : NestedState {
                                data object Idle : Stable
                                data class Refresh(val progress: Int) : Stable
                            }
                        }
                        """.trimIndent(),
                ),
            ),
        "同一パッケージの同 simpleName ネストクラス" to
            SnapshotScenario(
                SnapshotSource(
                    fileName = "NestedSameSimpleName.kt",
                    code =
                        """
                        package example

                        import me.tbsten.koma.strict.StrictStore

                        class FooScreen {
                            @StrictStore
                            sealed interface State {
                                data object Loading : State
                            }
                        }

                        class BarScreen {
                            @StrictStore
                            sealed interface State {
                                data object Loading : State
                            }
                        }
                        """.trimIndent(),
                ),
            ),
        "パッケージ宣言なし (default package)" to
            SnapshotScenario(
                SnapshotSource(
                    fileName = "RootPackageState.kt",
                    code =
                        """
                        import me.tbsten.koma.strict.StrictStore

                        @StrictStore
                        sealed interface RootPackageState {
                            data object Loading : RootPackageState
                        }
                        """.trimIndent(),
                ),
            ),
        // TODO(matrix): DSL 確定後に cream 形式の scenario ファミリ (hierarchyShape / onAction /
        //   onEnter / ...) へ拡張。フル matrix は golden 爆発に注意 (cream は feature あたり
        //   136〜244 golden)。withRepresentativeValues で絞る。
    )
