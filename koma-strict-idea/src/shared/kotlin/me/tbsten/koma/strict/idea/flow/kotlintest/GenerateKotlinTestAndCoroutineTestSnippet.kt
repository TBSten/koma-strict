package me.tbsten.koma.strict.idea.flow.kotlintest

import me.tbsten.koma.strict.idea.flow.GenerateTestSnippet
import me.tbsten.koma.strict.idea.flow.GenerateTestSnippetContext
import me.tbsten.koma.strict.idea.flow.KomaTestApi

/** `kotlin.test` + `kotlinx.coroutines.test.runTest` outer snippet. */
internal object GenerateKotlinTestAndCoroutineTestSnippet : GenerateTestSnippet() {
    override fun generate(context: GenerateTestSnippetContext): String = with(context) {
        buildString {
            append("package ").append(packageName).append("\n")
            append("\n")
            append("import koma.core.Store\n")
            append("import koma.test.dispatchAndAwait\n")
            append("import koma.test.startAndAwait\n")
            append("import kotlin.test.Test\n")
            append("import kotlin.test.assertEquals\n")
            append("import kotlinx.coroutines.test.runTest\n")
            append("\n")
            append("internal class $testClassName {\n")
            append("    @Test\n")
            append("    fun `").append(testCaseName).append("`() = runTest {\n")
            append("        val initialState: ").append(initialStateName).append(" = TODO()\n")
            append("        val store: ").append(storeType)
                .append($$" = TODO(\"construct the store with $initialState = ").append(initialStateName).append("\")\n")
            append("        ").append(KomaTestApi.startAndAwait("store")).append("\n")
            append(dispatchActionAndAssertStateStatementsBlock("        ") + "\n")
            append("    }\n")
            append("}")
        }
    }
}
