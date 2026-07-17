package me.tbsten.koma.strict.ksp.feature.storeSpec.scenario

import me.tbsten.koma.strict.ksp.testing.compile.SnapshotSource

// builder 形式 (第 3 の書き方) の e2e: leaf の actions { } / 中間 sealed の states { } と
// named・値渡し・scope lambda 形式との混在、および overload 解決の実証。
// 宣言は既存の lce (StoreSpecBasicScenarios) / flow (StoreSpecHierarchyScenarios) を共用する。

/**
 * builder 形式 (`actions { ... }`) と named / 値渡し / scope lambda 形式の混在のコンパイル証明。
 *
 * overload 解決の実証ポイント:
 * - Content は必須 handler が 1 個 (reload) だけ = named overload も単一 positional lambda で
 *   applicable になる、解決が最も競合しやすい形。default 引数 (configure) を使わない builder
 *   overload が優先されて解決される (Kotlin 2.4.0 スパイクで実測済みの規則の e2e 固定)
 * - named 形式 + trailing lambda (= configure) は builder overload 追加後も named に解決される
 *   (必須 named param が明示されるため builder 側は arity 不一致で除外される)
 */
internal fun builderFormMixedUsageSource(): SnapshotSource =
    SnapshotSource(
        fileName = "LceBuilderFormUsage.kt",
        code =
            """
            package example.lce

            import koma.core.Store

            fun buildBuilderFormLceStore(): Store<LceState, LceAction, LceEvent> =
                Store<LceState, LceAction, LceEvent>(initialState = LceState.Loading()) {
                    states(
                        // enter 宣言つき Loading には builder overload が生えない -> named 形式のまま
                        loading = LceState.Loading.actions(
                            enter = { nextState.toContent(data = "fetched") },
                        ),
                        // builder 形式 (単一 lambda)。member = 宣言済み action + configure
                        content = LceState.Content.actions {
                            reload { nextState.toLoading() }
                            configure { exit { } }   // builder 内でも per-state escape hatch を書ける
                        },
                        // scope lambda 形式との混在 (ミラーの builder overload も同居している)
                        error = { actions(retry = { nextState.toLoading() }) },
                    )
                }

            fun buildNamedTrailingConfigureLceStore(): Store<LceState, LceAction, LceEvent> =
                Store<LceState, LceAction, LceEvent>(initialState = LceState.Loading()) {
                    states(
                        loading = LceState.Loading.actions(enter = { nextState.toContent(data = "fetched") }),
                        // named 形式 + trailing lambda (= configure) は引き続き named に解決される
                        content = LceState.Content.actions(reload = { nextState.toLoading() }) { exit { } },
                        // scope ミラー側の builder overload の解決も実証
                        error = { actions { retry { nextState.toLoading() } } },
                    )
                }
            """.trimIndent(),
    )

/**
 * 中間 sealed の builder 形式 (`states { ... }`) のコンパイル証明。
 * 自宣言つき (FlowState.Refresh) は default 名 member (`refreshCommon`) を含む合成 builder。
 * 各 member は値渡し (`failed(...)`) / builder ネスト (`failed { ... }`) のどちらでも登録できる。
 * 単一子 + センチネルの named overload とも競合しない (builder 側に解決) ことの e2e 固定。
 */
internal fun statesBuilderUsageSource(): SnapshotSource =
    SnapshotSource(
        fileName = "FlowStatesBuilderUsage.kt",
        code =
            """
            package example.flow

            import koma.core.Store

            fun buildFlowStoreWithStatesBuilder(): Store<FlowState, FlowAction, Nothing> =
                Store<FlowState, FlowAction, Nothing>(initialState = FlowState.Idle()) {
                    states(
                        // leaf の builder 形式
                        idle = FlowState.Idle.actions {
                            start { nextState.toRefreshRunning() }
                        },
                        // 自宣言つき中間 sealed の builder 形式: default 名 member + 子 state 名 member
                        refresh = FlowState.Refresh.states {
                            refreshCommon {                     // default ブロックの builder ネスト
                                cancel { nextState.toIdle() }
                            }
                            failed(                             // 値渡しでも登録できる
                                FlowState.Refresh.Failed.actions(retry = { nextState.toRunning() }),
                            )
                        },
                    )
                }

            fun buildFlowStoreWithNestedBuilders(): Store<FlowState, FlowAction, Nothing> =
                Store<FlowState, FlowAction, Nothing>(initialState = FlowState.Idle()) {
                    states(
                        idle = FlowState.Idle.actions { start { nextState.toRefreshRunning() } },
                        refresh = FlowState.Refresh.states {
                            refreshCommon(FlowState.Refresh.actions(cancel = { nextState.toIdle() })) // 値渡し
                            failed { retry { nextState.toRunning() } }                                // builder ネスト
                        },
                    )
                }
            """.trimIndent(),
    )

/**
 * enter 宣言を持つ state (`LceState.Loading`) には builder overload (`actions { ... }`) 自体が
 * 生成されないことの error golden 用 usage。単一 lambda は named overload の `enter` param に
 * positional で束縛され、lambda 内の `enter` (builder member のつもりの呼び出し) が
 * unresolved になる = builder overload の非生成がコンパイルエラーとして観測できる。
 */
internal fun builderFormOnEnterStateUsageSource(): SnapshotSource =
    SnapshotSource(
        fileName = "LceLoadingBuilderFormUsage.kt",
        code =
            """
            package example.lce

            fun buildLoadingWithBuilderForm(): LoadingHandlers =
                LceState.Loading.actions {
                    enter { nextState.toContent(data = "fetched") }
                }
            """.trimIndent(),
    )
