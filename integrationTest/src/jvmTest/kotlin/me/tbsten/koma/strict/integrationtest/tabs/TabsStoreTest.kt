package me.tbsten.koma.strict.integrationtest.tabs

import koma.test.dispatchAndAwait
import koma.test.startAndAwait
import me.tbsten.koma.strict.integrationtest.runStoreTest
import me.tbsten.koma.strict.integrationtest.tabs.TabsAction.SelectTab.Tab
import me.tbsten.koma.strict.integrationtest.useStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/**
 * Behavior tests for the tabs sample store (root shared action / zero-declaration states /
 * E = Nothing) running on the real koma-core rc02.
 */
class TabsStoreTest {
    @Test
    fun `E=Nothingのstoreが構築でき初期stateはHomeになる`() =
        runStoreTest {
            createTabsStore().useStore {
                startAndAwait()
                assertEquals(TabsState.Home, currentState)
            }
        }

    @Test
    fun `root共有アクションselectTabでどのstateからも別タブへ遷移できる`() =
        runStoreTest {
            createTabsStore().useStore {
                startAndAwait()
                dispatchAndAwait(TabsAction.SelectTab(Tab.Search))
                assertEquals(TabsState.Search(query = ""), currentState)

                // 宣言ゼロ + companion なしの Profile へも遷移できる (data object 宣言)
                dispatchAndAwait(TabsAction.SelectTab(Tab.Profile))
                assertEquals(TabsState.Profile, currentState)

                dispatchAndAwait(TabsAction.SelectTab(Tab.Home))
                assertEquals(TabsState.Home, currentState)
            }
        }

    @Test
    fun `現在のタブへのselectTabはstayで状態が同一インスタンスのまま`() =
        runStoreTest {
            createTabsStore().useStore {
                startAndAwait()
                dispatchAndAwait(TabsAction.SelectTab(Tab.Search))
                dispatchAndAwait(TabsAction.UpdateQuery(query = "kotlin"))
                val before = currentState
                dispatchAndAwait(TabsAction.SelectTab(Tab.Search)) // 現在タブ → stayState()
                // stay なので query("kotlin") を持った同一インスタンスのまま
                assertSame(before, currentState)
            }
        }

    @Test
    fun `updateQueryの自己遷移は状態を作り直しqueryを差し替える`() =
        runStoreTest {
            createTabsStore().useStore {
                startAndAwait()
                dispatchAndAwait(TabsAction.SelectTab(Tab.Search))
                dispatchAndAwait(TabsAction.UpdateQuery(query = "kotlin"))
                val before = currentState
                dispatchAndAwait(TabsAction.UpdateQuery(query = "ktor")) // 自己遷移 (値が変わる)
                // 自己遷移は状態を作り直す (stay とは別物)。
                // 注意 (koma 実測): state は StateFlow 背骨のため「同値」の自己遷移は
                // equality conflation されインスタンスが差し替わらない。identity で
                // 自己遷移を観測できるのは値が変わる場合のみ
                assertNotSame(before, currentState)
                assertEquals(TabsState.Search(query = "ktor"), currentState)
            }
        }
}
