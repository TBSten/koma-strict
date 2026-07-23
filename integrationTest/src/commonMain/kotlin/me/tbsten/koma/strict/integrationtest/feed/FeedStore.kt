package me.tbsten.koma.strict.integrationtest.feed

import koma.core.Store

// doc/internal/samples.md ケース「LCE + pull-to-refresh + additional load」の利用側の 1:1 移植。
// fetchPage はテストから成功 / 失敗・ページ内容を注入できるよう関数パラメータにしている。

/** One page of feed items returned by the injected loader. */
class FeedPage(val items: List<String>, val hasMore: Boolean)

/**
 * Builds the feed sample store against the generated `states()` extension.
 *
 * @param fetchPage Injected pager so tests can drive success / failure and page contents.
 */
fun createFeedStore(fetchPage: suspend (offset: Int) -> FeedPage, initialState: FeedState.Loading = FeedState.Loading()): Store<FeedState, FeedAction, FeedEvent> =
    createFeedStore(
        initialState = initialState,
        loading = FeedState.Loading.actions(
            enter = {
                runCatching { fetchPage(0) }.fold(
                    onSuccess = { nextState.toStableIdle(items = it.items, hasMore = it.hasMore) },
                    onFailure = {
                        emitLoadFailed(it.message)
                        nextState.toError(message = it.message)
                    },
                )
            },
        ),
        stable = FeedState.Stable.states(
            idle = FeedState.Stable.Idle.actions(
                refresh = { nextState.toRefreshing() }, // items は同名 prop から持ち越し
                loadMore = { if (state.hasMore) nextState.toLoadingMore() else stayState() },
            ),
            refreshing = FeedState.Stable.Refreshing.actions(
                enter = {
                    runCatching { fetchPage(0) }.fold(
                        onSuccess = { nextState.toIdle(items = it.items, hasMore = it.hasMore) },
                        onFailure = {
                            emitRefreshFailed(it.message)
                            nextState.toIdle(hasMore = true) // items は持ち越し = 旧一覧を温存
                        },
                    )
                },
            ),
            loadingMore = FeedState.Stable.LoadingMore.actions(
                enter = {
                    runCatching { fetchPage(state.items.size) }.fold(
                        onSuccess = { nextState.toIdle(items = state.items + it.items, hasMore = it.hasMore) },
                        onFailure = {
                            emitLoadMoreFailed(it.message)
                            nextState.toIdle(hasMore = true)
                        },
                    )
                },
            ),
        ),
        error = FeedState.Error.actions(retry = { nextState.toLoading() }),
    )
