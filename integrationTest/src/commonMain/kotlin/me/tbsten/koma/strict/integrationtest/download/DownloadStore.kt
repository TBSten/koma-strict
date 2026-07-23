package me.tbsten.koma.strict.integrationtest.download

import koma.core.Store

// 自宣言 + 子を持つ中間 sealed の plus 合成 (`actions(...) + states(...)`) と
// per-store factory (`createDownloadStore`) の実利用 (実物 koma rc02 での behavior 検証対象)。

/**
 * Builds the download sample store through the generated per-store factory
 * (no store type arguments at the call site).
 *
 * - `active` composes the intermediate node's own shared handlers and its child states
 *   with the generated `plus` operator (`actions(...) + states(...)`) — the shared
 *   `cancel` handler fires from both `Running` and `Paused`.
 * - The trailing `configuration` lambda exercises the store-level escape hatch: a raw koma
 *   `state<Idle> {}` block that emits [DownloadEvent.ReturnedToIdle] on entering `Idle`
 *   (`Idle` declares no `@OnEnter`, so the raw handler observably fires).
 */
fun createDownloadStore(): Store<DownloadState, DownloadAction, DownloadEvent> =
    createDownloadStore(
        initialState = DownloadState.Idle,
        idle = DownloadState.Idle.actions(
            start = { nextState.toActiveRunning(url = action.url) }, // 入力の取り込みは明示(原則 2)
        ),
        active = DownloadState.Active.actions(
            cancel = {
                emitCanceled()
                nextState.toIdle()
            },
        ) + DownloadState.Active.states(
            running = DownloadState.Active.Running.actions(pause = { nextState.toPaused() }), // url は持ち越し
            paused = DownloadState.Active.Paused.actions(resume = { nextState.toRunning() }),
        ),
    ) {
        // 末尾 configuration = store-level escape hatch (生成 handler 登録の後に素の koma DSL を追記)
        state<DownloadState.Idle> {
            enter { event(DownloadEvent.ReturnedToIdle) }
        }
    }
