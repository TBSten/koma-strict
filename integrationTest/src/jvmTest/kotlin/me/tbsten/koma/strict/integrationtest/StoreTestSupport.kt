package me.tbsten.koma.strict.integrationtest

import koma.core.Action
import koma.core.Event
import koma.core.State
import koma.core.Store
import kotlinx.coroutines.runBlocking

// 待機は koma-test の startAndAwait / dispatchAndAwait を使う(ポーリング・delay 禁止)。
// event の検証は StoreRecorder で行う (SharedFlow(replay = 0) の購読レースを構造的に回避)。
// store.state × Flow.first{} は rc02 でハングするため使わない。

/**
 * Runs a suspending store test body on a real dispatcher.
 *
 * Returns [Unit] explicitly so it can be used as a JUnit test method expression body.
 */
internal fun runStoreTest(body: suspend () -> Unit) {
    runBlocking { body() }
}

/**
 * Runs [block] against this store and always closes the store afterwards.
 */
internal suspend fun <S : State, A : Action, E : Event> Store<S, A, E>.useStore(
    block: suspend Store<S, A, E>.() -> Unit,
) {
    try {
        block()
    } finally {
        close()
    }
}
