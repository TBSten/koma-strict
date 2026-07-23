package me.tbsten.koma.strict.idea.flow

import me.tbsten.koma.strict.idea.flow.kotest.kotestSnippets
import me.tbsten.koma.strict.idea.flow.kotlintest.GenerateKotlinTestAndCoroutineTestSnippet

/**
 * Inputs for the outer test-file snippet (the `@Test fun … = runTest { }` / kotest `FreeSpec({ })`
 * wrapper). The inner dispatch/assert statements are produced by
 * [dispatchActionAndAssertStateStatementsBlock]; the snippet indents and drops them into its body.
 */
internal data class GenerateTestSnippetContext(
    /** Package of the generated test file, e.g. `com.example.feed` (same as the State definition). */
    val packageName: String,
    /** Test class name (panel-customizable), e.g. `FeedStoreTest`. */
    val testClassName: String,
    /** Test case name (panel-customizable), e.g. `recorded flow` (a backticked fun / kotest test name). */
    val testCaseName: String,
    /** The generic koma store type of `val store`, e.g. `Store<FeedState, FeedAction, FeedEvent>`. */
    val storeType: String,
    /** Initial state reference, e.g. `FeedState.Loading`. */
    val initialStateName: String,
    /** Renders the dispatch/assert statements, each line prefixed with the given indent. */
    val dispatchActionAndAssertStateStatementsBlock: (indent: String) -> String,
) {
    companion object {
        /** Available outer snippets by framework name. Keys match [GenerateDispatchActionAndAssertStateStatementsBlock.All]. */
        val All: Map<String, GenerateTestSnippet> =
            mapOf("kotlin.test" to GenerateKotlinTestAndCoroutineTestSnippet) + kotestSnippets
    }
}

/** Generates the outer test snippet from a [GenerateTestSnippetContext]. */
internal typealias GenerateTestSnippet = SnippetGenerator<GenerateTestSnippetContext>
