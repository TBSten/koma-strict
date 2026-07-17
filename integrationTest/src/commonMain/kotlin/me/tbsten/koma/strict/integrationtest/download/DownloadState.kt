package me.tbsten.koma.strict.integrationtest.download

import koma.core.State
import me.tbsten.koma.strict.OnAction
import me.tbsten.koma.strict.StoreSpec

// 自宣言 (@OnAction Cancel) と子 state の両方を持つ中間 sealed (`Active`) の実機ケース。
// samples.md には無い追加ケースで、plus 合成 (`actions(...) + states(...)`) と
// per-store factory (`downloadStore`) の behavior 検証を担う (Action / Event は別ファイルに分割)。

@StoreSpec(initial = [DownloadState.Idle::class])
sealed interface DownloadState : State {
    @OnAction<DownloadAction.Start>(nextState = [Active.Running::class])
    data object Idle : DownloadState

    // 中間 sealed 自身の共有宣言 = default ブロック。親側 param 型は合成型 (ActiveHandlers) になり、
    // `actions(...) + states(...)` の plus 合成か default 込み states() だけがそれを作れる
    @OnAction<DownloadAction.Cancel>(nextState = [Idle::class], emit = [DownloadEvent.Canceled::class])
    sealed interface Active : DownloadState {
        val url: String

        companion object

        @OnAction<DownloadAction.Pause>(nextState = [Paused::class])
        interface Running : Active { companion object }

        @OnAction<DownloadAction.Resume>(nextState = [Running::class])
        interface Paused : Active { companion object }
    }

    // companion object の直後に修飾子付き宣言(data object 等)を置くと kotlinc の
    // parser quirk で companion が壊れる(koma-strict が警告診断を出す)ため末尾に置く
    companion object
}
