package me.tbsten.koma.strict.integrationtest.lce

import koma.core.State
import me.tbsten.koma.strict.OnAction
import me.tbsten.koma.strict.OnEnter
import me.tbsten.koma.strict.StoreSpec

// doc/internal/samples.md ケース「基本 LCE」の宣言の 1:1 移植
// (Action / Event は LceAction.kt / LceEvent.kt に分割)

@StoreSpec(initial = [LceState.Loading::class]) // actions / events は宣言から推論
sealed interface LceState : State {
    companion object

    @OnEnter(nextState = [Content::class, Error::class], emit = [LceEvent.LoadFailed::class])
    interface Loading : LceState { companion object }

    @OnAction<LceAction.Reload>(nextState = [Loading::class])
    interface Content : LceState { val data: String; companion object }

    @OnAction<LceAction.Retry>(nextState = [Loading::class])
    interface Error : LceState { val message: String?; companion object }
}
