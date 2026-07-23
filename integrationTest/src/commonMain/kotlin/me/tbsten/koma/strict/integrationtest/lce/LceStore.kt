package me.tbsten.koma.strict.integrationtest.lce

import koma.core.Store

// doc/internal/samples.md ケース「基本 LCE」の利用側の 1:1 移植 + builder 形式 (第 3 の書き方)。
// fetchData はテストから成功 / 失敗を注入できるよう関数パラメータにしている。
// content は builder 形式 (`actions { reload { ... } }`) で登録し、実物 koma 上での挙動を
// LceStoreTest が named 形式と同一に検証する。Loading は enter 宣言つきのため builder overload が
// 生成されず named 形式のまま (仕様: enter / exit は builder member を持たない)。

/**
 * Builds the LCE sample store against the generated `states()` extension.
 *
 * @param fetchData Injected loader so tests can drive the success / failure paths.
 */
fun createLceStore(
    fetchData: suspend () -> String,
    initialState: LceState = LceState.Loading(),
): Store<LceState, LceAction, LceEvent> =
    restoreLceStore(
        initialState = initialState,
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
        // builder 形式: 宣言済み handler ごとの member 関数で登録する (網羅チェックは構築時 fail-fast)
        content = LceState.Content.actions {
            reload {
                nextState.toLoading()
            }
        },
        error = LceState.Error.actions(
            retry = {
                nextState.toLoading()
            },
        ),
    ) {
    }
