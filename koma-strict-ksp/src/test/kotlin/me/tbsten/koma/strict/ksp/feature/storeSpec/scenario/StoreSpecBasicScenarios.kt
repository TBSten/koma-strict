package me.tbsten.koma.strict.ksp.feature.storeSpec.scenario

import me.tbsten.koma.strict.ksp.testing.compile.SnapshotSource
import org.intellij.lang.annotations.Language

// 基本 LCE ケース (doc/internal/samples.md ケース 1) の宣言と利用側コード。

@Language("kotlin")
private val lceDeclaration =
    """
    package example.lce

    import koma.core.Action
    import koma.core.Event
    import koma.core.State
    import me.tbsten.koma.strict.OnAction
    import me.tbsten.koma.strict.OnEnter
    import me.tbsten.koma.strict.StoreSpec

    @StoreSpec(initial = [LceState.Loading::class])   // actions / events は宣言から推論
    sealed interface LceState : State {
        companion object

        @OnEnter(nextState = [Content::class, Error::class], emit = [LceEvent.LoadFailed::class])
        interface Loading : LceState { companion object }

        @OnAction<LceAction.Reload>(nextState = [Loading::class])
        interface Content : LceState { val data: String; companion object }

        @OnAction<LceAction.Retry>(nextState = [Loading::class])
        interface Error : LceState { val message: String?; companion object }
    }

    sealed interface LceAction : Action {
        data object Reload : LceAction
        data object Retry : LceAction
    }

    sealed interface LceEvent : Event {
        data class LoadFailed(val message: String?) : LceEvent
    }
    """.trimIndent()

/** samples.md ケース 1 の宣言 (生成コードの全量の正)。 */
internal fun lceScenarioSource(): SnapshotSource = SnapshotSource(fileName = "LceState.kt", code = lceDeclaration)

/**
 * samples.md ケース 1 の利用側コード。生成 per-store factory (`lceStore`。型引数なしの糖衣入口)・
 * companion actions()・state factory・Scope API が実際に「利用側から書ける」ことのコンパイル証明
 * (strict の約束の e2e)。koma 直入口 (`Store {}` + states()) 側は他 scenario と integrationTest が担う。
 */
internal fun lceUsageSource(): SnapshotSource =
    SnapshotSource(
        fileName = "LceStoreUsage.kt",
        code =
            """
            package example.lce

            import koma.core.Store

            fun buildLceStore(): Store<LceState, LceAction, LceEvent> =
                lceStore(
                    initialState = LceState.Loading(),
                    loading = LceState.Loading.actions(
                        enter = {
                            runCatching { "fetched" }.fold(
                                onSuccess = { nextState.toContent(data = it) },
                                onFailure = {
                                    emitLoadFailed(it.message)
                                    nextState.toError(message = it.message)
                                },
                            )
                        },
                    ),
                    content = LceState.Content.actions(reload = { nextState.toLoading() }),
                    error = LceState.Error.actions(retry = { nextState.toLoading() }),
                ) {
                    // 末尾 configuration = store-level escape hatch (素の koma DSL を併記できる)
                    state<LceState.Error> { exit { } }
                }
            """.trimIndent(),
    )

/**
 * per-state configure (escape hatch) と clearPendingActions passthrough の利用側コード。
 * leaf actions() の末尾 configure を trailing lambda で書き、生成される `state<X> {}` ブロック末尾に
 * 素の koma DSL (StateHandlerConfig) が差し込めること + 生成 Scope の clearPendingActions() が
 * koma へ delegate されることのコンパイル証明。
 */
internal fun lceConfigureUsageSource(): SnapshotSource =
    SnapshotSource(
        fileName = "LceConfigureUsage.kt",
        code =
            """
            package example.lce

            import koma.core.Store

            fun buildConfiguredLceStore(): Store<LceState, LceAction, LceEvent> =
                Store<LceState, LceAction, LceEvent>(initialState = LceState.Loading()) {
                    states(
                        loading = LceState.Loading.actions(
                            enter = {
                                clearPendingActions()   // 生成 Scope の koma passthrough
                                nextState.toContent(data = "fetched")
                            },
                        ),
                        content = LceState.Content.actions(
                            reload = { nextState.toLoading() },
                        ) {
                            // per-state escape hatch: 素の koma DSL (StateHandlerConfig) を trailing lambda で書ける
                            exit { }
                        },
                        error = LceState.Error.actions(retry = { nextState.toLoading() }),
                    )
                }
            """.trimIndent(),
    )
