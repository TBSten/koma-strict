package me.tbsten.koma.strict.integrationtest.lce

import koma.core.Store
import koma.test.dispatchAndAwait
import koma.test.record
import koma.test.startAndAwait
import me.tbsten.koma.strict.integrationtest.runStoreTest
import me.tbsten.koma.strict.integrationtest.useStore
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Measures how koma-core rc02 merges a generated handler and a raw same-trigger handler
 * registered on the same state.
 *
 * Measured rule (also stated in koma's own KDoc: "If multiple ... handlers can match,
 * the first registered handler is used"): **the first registered handler wins; later
 * same-trigger registrations are silently ignored** (no error, not both, not last-wins).
 * koma-strict guarantees the generated registrations precede every escape block, so a raw
 * handler for a trigger already declared on the state never runs — escapes are for
 * triggers the declarations do not cover. This measured contract is documented on the
 * generated escape KDoc.
 */
class LceOverlappingHandlersTest {
    /** 生成 enter (@OnEnter) と escape 内の素の enter を同一 state に重ねた store。 */
    private fun createOverlappingEnterStore(): Store<LceState, LceAction, LceEvent> =
        Store<LceState, LceAction, LceEvent>(initialState = LceState.Loading()) {
            states(
                loading = LceState.Loading.actions(
                    enter = {
                        emitLoadFailed("generated:enter")
                        nextState.toContent(data = "from-generated")
                    },
                ) {
                    // 素の koma DSL で同一 trigger (enter) を重ねる
                    enter {
                        event(LceEvent.LoadFailed(message = "escape:enter"))
                    }
                },
                content = LceState.Content.actions(reload = { nextState.toLoading() }),
                error = LceState.Error.actions(retry = { nextState.toLoading() }),
            )
        }

    /** 生成 action<Reload> と escape 内の素の action<Reload> を同一 state に重ねた store。 */
    private fun createOverlappingActionStore(): Store<LceState, LceAction, LceEvent> =
        Store<LceState, LceAction, LceEvent>(initialState = LceState.Content(data = "initial")) {
            states(
                loading = LceState.Loading.actions(
                    enter = {
                        emitLoadFailed("generated:loading-enter")
                        nextState.toContent(data = "fetched")
                    },
                ),
                content = LceState.Content.actions(
                    reload = { nextState.toLoading() },
                ) {
                    // 素の koma DSL で同一 action 型 (Reload) を重ねる
                    action<LceAction.Reload> {
                        event(LceEvent.LoadFailed(message = "escape:action"))
                    }
                },
                error = LceState.Error.actions(retry = { nextState.toLoading() }),
            )
        }

    /** 宣言外 action への素の handler を escape 内で 2 回重ねた store (raw 同士の先勝ち検証)。 */
    private fun createOverlappingRawActionStore(): Store<LceState, LceAction, LceEvent> =
        Store<LceState, LceAction, LceEvent>(initialState = LceState.Content(data = "initial")) {
            states(
                loading = LceState.Loading.actions(enter = { nextState.toContent(data = "fetched") }),
                content = LceState.Content.actions(
                    reload = { nextState.toLoading() },
                ) {
                    // Retry は Content に宣言されていない -> 素の handler が発火できる。同型を 2 つ登録
                    action<LceAction.Retry> {
                        event(LceEvent.LoadFailed(message = "raw:first"))
                    }
                    action<LceAction.Retry> {
                        event(LceEvent.LoadFailed(message = "raw:second"))
                    }
                },
                error = LceState.Error.actions(retry = { nextState.toLoading() }),
            )
        }

    @Test
    fun `同一stateの同一triggerは先勝ちで後から登録した素のenterは走らない`() =
        runStoreTest {
            createOverlappingEnterStore().useStore {
                record { recorder ->
                    startAndAwait()
                    // 実測: 生成 enter (先に登録) だけが走り、escape の素の enter は黙って無視される
                    assertEquals(
                        listOf<LceEvent>(LceEvent.LoadFailed(message = "generated:enter")),
                        recorder.events,
                    )
                    assertEquals(LceState.Content(data = "from-generated"), currentState)
                }
            }
        }

    @Test
    fun `同一stateの同一action型も先勝ちで後から登録した素のactionは走らない`() =
        runStoreTest {
            createOverlappingActionStore().useStore {
                record { recorder ->
                    startAndAwait()
                    dispatchAndAwait(LceAction.Reload)
                    // 実測: 生成 reload (先に登録) の遷移だけが起き、escape の素の action<Reload> は無視される
                    // (event は遷移先 Loading の生成 enter によるもののみ)
                    assertEquals(
                        listOf<LceEvent>(LceEvent.LoadFailed(message = "generated:loading-enter")),
                        recorder.events,
                    )
                    assertEquals(LceState.Content(data = "fetched"), currentState)
                }
            }
        }

    @Test
    fun `escape内の素のhandler同士でも先に登録した方が勝つ`() =
        runStoreTest {
            createOverlappingRawActionStore().useStore {
                record { recorder ->
                    startAndAwait()
                    dispatchAndAwait(LceAction.Retry)
                    // 実測: 素の handler 同士でも登録順の先勝ち (raw:first のみ)
                    assertEquals(
                        listOf<LceEvent>(LceEvent.LoadFailed(message = "raw:first")),
                        recorder.events,
                    )
                    assertEquals(LceState.Content(data = "initial"), currentState)
                }
            }
        }
}
