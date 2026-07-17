package me.tbsten.koma.strict.integrationtest.feed

import koma.core.State
import me.tbsten.koma.strict.OnAction
import me.tbsten.koma.strict.OnEnter
import me.tbsten.koma.strict.Stay
import me.tbsten.koma.strict.StoreSpec

// doc/internal/samples.md ケース「LCE + pull-to-refresh + additional load」の宣言の 1:1 移植
// (Action / Event は FeedAction.kt / FeedEvent.kt に分割)

@StoreSpec(initial = [FeedState.Loading::class])
sealed interface FeedState : State {
    companion object

    @OnEnter(nextState = [Stable.Idle::class, Error::class], emit = [FeedEvent.LoadFailed::class])
    interface Loading : FeedState { companion object }

    sealed interface Stable : FeedState { // 共有 prop は 1 回だけ宣言
        val items: List<String>
        companion object

        @OnAction<FeedAction.Refresh>(nextState = [Refreshing::class])
        @OnAction<FeedAction.LoadMore>(nextState = [Stay::class, LoadingMore::class]) // 条件付き遷移
        interface Idle : Stable { val hasMore: Boolean; companion object }

        @OnEnter(nextState = [Idle::class], emit = [FeedEvent.RefreshFailed::class])
        interface Refreshing : Stable { companion object }

        @OnEnter(nextState = [Idle::class], emit = [FeedEvent.LoadMoreFailed::class])
        interface LoadingMore : Stable { companion object }
    }

    @OnAction<FeedAction.Retry>(nextState = [Loading::class])
    interface Error : FeedState { val message: String?; companion object }
}
