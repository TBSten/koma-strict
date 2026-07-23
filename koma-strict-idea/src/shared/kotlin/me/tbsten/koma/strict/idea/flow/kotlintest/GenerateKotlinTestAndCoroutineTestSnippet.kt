package me.tbsten.koma.strict.idea.flow.kotlintest

import me.tbsten.koma.strict.idea.flow.GenerateTestSnippet
import me.tbsten.koma.strict.idea.flow.GenerateTestSnippetContext
import org.intellij.lang.annotations.Language

internal object GenerateKotlinTestAndCoroutineTestSnippet : GenerateTestSnippet() {
    override fun replaceByVariables(context: GenerateTestSnippetContext): String = with(context) {
        @Language("kotlin")
        """
            @Test
            fun `$flowName`() = runTest {
                val store: $storeName = TODO("construct $storeName with initialState = $initialStateName")

            ${dispatchActionAndAssertStateStatementsBlock("    ")}
            }
        """.trimIndent()
    }
}