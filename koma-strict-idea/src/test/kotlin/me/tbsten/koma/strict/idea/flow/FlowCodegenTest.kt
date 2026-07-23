package me.tbsten.koma.strict.idea.flow

import me.tbsten.koma.strict.idea.ir.EdgeKind
import me.tbsten.koma.strict.idea.model.RootState
import me.tbsten.koma.strict.idea.model.StateId
import me.tbsten.koma.strict.idea.model.StoreDiagramModel
import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure codegen tests (`ide-test-code.md`): a recorded flow -> `@FlowSpec` / koma-test text. */
class FlowCodegenTest {

    // codegen が読むのは root.simpleName と packageName だけなので、children 無しの最小 model で十分。
    private fun feedModel() =
        StoreDiagramModel(root = RootState(simpleName = "FeedState", children = emptyList()), packageName = "com.example.feed")

    /** The mockup scenario (`ide-test-code-flow-spec.png`): reproduced verbatim. */
    private fun feedFlow() = RecordedFlow(
        initial = StateId("Loading"),
        transitions = listOf(
            FlowTransition(EdgeKind.ENTER, typeRef = null, label = "onEnter / LoadFailed", fromId = StateId("Loading"), target = StateId("Error")),
            FlowTransition(EdgeKind.ACTION, typeRef = "FeedAction.Retry", label = "retry", fromId = StateId("Error"), target = StateId("Loading")),
            FlowTransition(EdgeKind.ENTER, typeRef = null, label = "onEnter", fromId = StateId("Loading"), target = StateId("Stable", "Idle")),
        ),
    )

    @Test
    fun `generateFlowSpec がモック画像どおりの注釈を出す`() {
        val spec = generateFlowSpec(feedModel(), feedFlow(), "RecordedFlow")
        val expected = """
            @FlowSpec(
                name = "recorded flow",
                steps = [
                    FlowStep(FeedState.Loading::class),
                    FlowStep(OnEnter::class),
                    FlowStep(FeedState.Error::class),
                    FlowStep(FeedAction.Retry::class),
                    FlowStep(FeedState.Loading::class),
                    FlowStep(OnEnter::class),
                    FlowStep(FeedState.Stable.Idle::class),
                ],
            )
            internal annotation class RecordedFlow
        """.trimIndent()
        assertEquals(expected, spec.declaration)
        assertEquals("RecordedFlow", spec.annotationClassName)
    }

    @Test
    fun `generateFlowSpec の import は使った注釈だけを含む`() {
        val spec = generateFlowSpec(feedModel(), feedFlow(), "RecordedFlow")
        // enter を使うので OnEnter は入る / stay は無いので Stay は入らない。
        assertEquals(
            listOf(
                "me.tbsten.koma.strict.FlowSpec",
                "me.tbsten.koma.strict.FlowStep",
                "me.tbsten.koma.strict.OnEnter",
            ),
            spec.requiredImports,
        )
    }

    @Test
    fun `stay は FlowStep(Stay) と Stay import になる`() {
        val flow = RecordedFlow(
            initial = StateId("Stable", "Idle"),
            transitions = listOf(
                FlowTransition(EdgeKind.ACTION, typeRef = "FeedAction.LoadMore", label = "loadMore (stay)", fromId = StateId("Stable", "Idle"), target = null, stay = true),
            ),
        )
        val spec = generateFlowSpec(feedModel(), flow, "LoadMoreExhaustedFlow")
        assertTrue(spec.declaration.contains("FlowStep(FeedState.Stable.Idle::class),"))
        assertTrue(spec.declaration.contains("FlowStep(FeedAction.LoadMore::class),"))
        assertTrue(spec.declaration.contains("FlowStep(Stay::class),"))
        assertTrue(spec.declaration.contains("internal annotation class LoadMoreExhaustedFlow"))
        assertTrue(spec.declaration.contains("name = \"load more exhausted flow\""))
        assertContainsAll(spec.requiredImports, "me.tbsten.koma.strict.Stay")
    }

    @Test
    fun `generateKomaTest は kotlin_test の scaffold を出す`() {
        val test = generateKomaTest(feedModel(), feedFlow(), "RecordedFlow")
        // 外側の書式 (class ラッパ / import 行 / TODO メッセージ等) は変わりうるので、核となる文の
        // 構造だけを検証する (State 参照は `Type(` = コンストラクタ呼び出し形かどうかで見る)。
        assertTrue(test.contains("import koma.core.Store"))
        assertTrue(test.contains("fun `recorded flow`() = runTest {"))
        // store 型は koma の総称 Store<State, Action, Event>。
        assertTrue(test.contains("val store: Store<FeedState, FeedAction, FeedEvent> = TODO("))
        assertTrue(test.contains("store.startAndAwait()"))
        assertTrue(test.contains("assertEquals(FeedState.Error(") && test.contains(", store.currentState)"))
        assertTrue(test.contains("store.dispatchAndAwait(FeedAction.Retry)"))
        assertTrue(test.contains("assertEquals(FeedState.Loading("))
        assertTrue(test.contains("assertEquals(FeedState.Stable.Idle("))
    }

    @Test
    fun `generateKomaTest は kotest FreeSpec の scaffold を出す`() {
        val test = generateKomaTest(feedModel(), feedFlow(), "RecordedFlow", frameworkName = "kotest FreeSpec")
        @Language("kotlin")
        val expected = """
            package com.example.feed

            import io.kotest.core.spec.style.FreeSpec
            import io.kotest.matchers.shouldBe
            import koma.core.Store
            import koma.test.dispatchAndAwait
            import koma.test.startAndAwait

            internal class FeedStoreTest : FreeSpec({
                "recorded flow" {
                    val initialState: FeedState.Loading = TODO()
                    val store: Store<FeedState, FeedAction, FeedEvent> = TODO("construct the store with ${'$'}initialState = FeedState.Loading")
                    store.startAndAwait()
                    store.currentState shouldBe FeedState.Error(/* TODO: expected state props */)
                    store.dispatchAndAwait(FeedAction.Retry)
                    store.currentState shouldBe FeedState.Loading(/* TODO: expected state props */)
                    store.currentState shouldBe FeedState.Stable.Idle(/* TODO: expected state props */)
                }
            })
        """.trimIndent()
        assertEquals(expected, test)
    }

    @Test
    fun `stay は dispatch と 同一 state の assert になる`() {
        val flow = RecordedFlow(
            initial = StateId("Stable", "Idle"),
            transitions = listOf(
                FlowTransition(EdgeKind.ACTION, typeRef = "FeedAction.LoadMore", label = "loadMore (stay)", fromId = StateId("Stable", "Idle"), target = null, stay = true),
            ),
        )
        val test = generateKomaTest(feedModel(), flow, "StayFlow")
        assertTrue(test.contains("store.dispatchAndAwait(FeedAction.LoadMore)"))
        // stay は現状維持なので from (Stable.Idle) を assert する。
        assertTrue(test.contains("assertEquals(FeedState.Stable.Idle(/* TODO: expected state props */), store.currentState)"))
    }

    @Test
    fun `recover は TODO 発火コメントになる`() {
        val flow = RecordedFlow(
            initial = StateId("Authenticated"),
            transitions = listOf(
                FlowTransition(EdgeKind.RECOVER, typeRef = "SessionExpiredException", label = "on SessionExpiredException", fromId = StateId("Authenticated"), target = StateId("LoggedOut")),
            ),
        )
        val test = generateKomaTest(StoreDiagramModel(root = RootState("AuthState", emptyList())), flow, "RecoverFlow")
        assertTrue(test.contains("// TODO: raise SessionExpiredException to drive @OnRecover"))
        assertTrue(test.contains("assertEquals(AuthState.LoggedOut(/* TODO: expected state props */), store.currentState)"))
    }

    @Test
    fun `defaultTestClassName と komaTestFileName`() {
        assertEquals("FeedStoreTest", defaultTestClassName(feedModel()))
        assertEquals("FeedStoreTest.kt", komaTestFileName(defaultTestClassName(feedModel())))
        // カスタムのクラス名はそのまま .kt になる。
        assertEquals("MyFlowTest.kt", komaTestFileName("MyFlowTest"))
    }

    @Test
    fun `カスタム testClassName testCaseName が生成に反映される`() {
        val test = generateKomaTest(
            feedModel(), feedFlow(), "RecordedFlow",
            testClassName = "MyFeedTest", testCaseName = "happy path",
        )
        assertTrue(test.contains("class MyFeedTest {"))
        assertTrue(test.contains("fun `happy path`() = runTest {"))
    }

    @Test
    fun `humanizeFlowName が camelCase を空白区切りにする`() {
        assertEquals("recorded flow", humanizeFlowName("RecordedFlow"))
        assertEquals("initialize happy path flow", humanizeFlowName("InitializeHappyPathFlow"))
        assertEquals("", humanizeFlowName(""))
    }

    private fun assertContainsAll(actual: List<String>, vararg expected: String) {
        for (e in expected) assertTrue("expected $actual to contain $e", actual.contains(e))
    }
}
