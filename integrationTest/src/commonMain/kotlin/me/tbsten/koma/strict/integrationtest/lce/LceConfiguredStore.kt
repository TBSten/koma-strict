package me.tbsten.koma.strict.integrationtest.lce

import koma.core.Store

// samples.md の写経ではなく、per-state configure (escape hatch) と
// clearPendingActions passthrough の実挙動検証用バリアント (宣言は LceState を共用)。

/**
 * LCE store variant that exercises the per-state `configure` escape hatch and the
 * generated `clearPendingActions()` passthrough against the real koma-core.
 *
 * - `content`'s trailing `configure` lambda registers a raw koma `enter {}` for
 *   `LceState.Content` (which declares no `@OnEnter`, so the raw handler actually fires)
 *   and emits [LceEvent.LoadFailed] with a `configured:` prefix as an observable marker.
 *   Raw koma `event()` bypasses the per-handler emit whitelist by design (escape hatch).
 * - `reload` calls `clearPendingActions()` before transitioning, proving the passthrough
 *   delegates to the real koma scope at runtime.
 *
 * @param fetchData Injected loader so tests can drive the success / failure paths.
 */
fun createConfiguredLceStore(fetchData: suspend () -> String): Store<LceState, LceAction, LceEvent> =
    Store<LceState, LceAction, LceEvent>(initialState = LceState.Loading()) {
        states(
            loading = LceState.Loading.actions(
                enter = {
                    runCatching { fetchData() }.fold(
                        onSuccess = { nextState.toContent(data = it) },
                        onFailure = {
                            emitLoadFailed(it.message)
                            nextState.toError(message = it.message)
                        },
                    )
                },
            ),
            content = LceState.Content.actions(
                reload = {
                    clearPendingActions() // 生成 Scope の passthrough (実物 koma へ delegate)
                    nextState.toLoading()
                },
            ) {
                // per-state escape hatch: 生成される state<LceState.Content> {} ブロック末尾に
                // 差し込まれる素の koma DSL。Content は @OnEnter 未宣言なのでこの enter が発火する
                enter {
                    event(LceEvent.LoadFailed(message = "configured:${state.data}"))
                }
            },
            error = LceState.Error.actions(retry = { nextState.toLoading() }),
        )
    }
