package me.tbsten.koma.strict.integrationtest.feed

import me.tbsten.koma.strict.FlowSpec
import me.tbsten.koma.strict.FlowStep
import me.tbsten.koma.strict.OnEnter
import me.tbsten.koma.strict.Stay

@FlowSpec(
    name = "initialize happy path",
    steps = [
        FlowStep(FeedState.Loading::class),
        FlowStep(OnEnter::class),
        FlowStep(FeedState.Stable.Idle::class),
    ],
)
annotation class InitializeHappyPathFlow

// Refresh は Stable ではなく Idle に宣言されているので、起点も Idle でなければならない
@FlowSpec(
    steps = [
        FlowStep(FeedState.Stable.Idle::class),
        FlowStep(FeedAction.Refresh::class),
        FlowStep(FeedState.Stable.Refreshing::class),
        FlowStep(OnEnter::class),
        FlowStep(FeedState.Stable.Idle::class),
    ],
)
annotation class RefreshFlow

// 末尾の Stay = 「現状維持」。Idle::class を再掲すると自己遷移になり別物
@FlowSpec(
    name = "load more (no further page)",
    steps = [
        FlowStep(FeedState.Stable.Idle::class),
        FlowStep(FeedAction.LoadMore::class),
        FlowStep(Stay::class),
    ],
)
annotation class LoadMoreExhaustedFlow
