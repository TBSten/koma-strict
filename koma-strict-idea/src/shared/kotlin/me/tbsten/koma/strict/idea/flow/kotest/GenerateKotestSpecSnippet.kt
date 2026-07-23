package me.tbsten.koma.strict.idea.flow.kotest

import me.tbsten.koma.strict.idea.flow.GenerateTestSnippet
import me.tbsten.koma.strict.idea.flow.GenerateTestSnippetContext
import me.tbsten.koma.strict.idea.flow.KomaTestApi

/**
 * A kotest outer snippet parameterized by spec style. Every kotest spec shares the same koma-test
 * body (construct → startAndAwait → the dispatch/assert statements) and the same asserts
 * ([GenerateKotestAssertsBlock]); only the class supertype and the test-declaration nesting differ,
 * captured by [specType] / [openTest] / [closeTest] / [bodyIndent].
 */
internal class GenerateKotestSpecSnippet(
    private val specType: String,
    private val bodyIndent: String,
    private val openTest: (GenerateTestSnippetContext) -> String,
    private val closeTest: String,
) : GenerateTestSnippet() {
    override fun generate(context: GenerateTestSnippetContext): String = with(context) {
        buildString {
            append("package ").append(packageName).append("\n\n")
            append("import io.kotest.core.spec.style.").append(specType).appendLine()
            append("import io.kotest.matchers.shouldBe\n")
            append("import koma.core.Store\n")
            append("import koma.test.dispatchAndAwait\n")
            append("import koma.test.startAndAwait\n")
            append("\n")
            append("internal class ").append(testClassName).append(" : ").append(specType).append("({\n")
            append(openTest(context)).append("\n")
            append(bodyIndent).append("val initialState: ").append(initialStateName).append(" = TODO()\n")
            append(bodyIndent).append("val store: ").append(storeType)
                .append($$" = TODO(\"construct the store with $initialState = ").append(initialStateName).append("\")\n")
            append(bodyIndent).append(KomaTestApi.startAndAwait("store")).append("\n")
            append(dispatchActionAndAssertStateStatementsBlock(bodyIndent))
            append("\n").append(closeTest).append("\n")
            append("})")
        }
    }

    companion object {
        // 1 段ネスト (test lambda 直下 = 8 スペース)。
        val FreeSpec = GenerateKotestSpecSnippet("FreeSpec", "        ", { "    \"${it.testCaseName}\" {" }, "    }")
        val StringSpec = GenerateKotestSpecSnippet("StringSpec", "        ", { "    \"${it.testCaseName}\" {" }, "    }")
        val FunSpec = GenerateKotestSpecSnippet("FunSpec", "        ", { "    test(\"${it.testCaseName}\") {" }, "    }")
        val ShouldSpec = GenerateKotestSpecSnippet("ShouldSpec", "        ", { "    should(\"${it.testCaseName}\") {" }, "    }")
        val ExpectSpec = GenerateKotestSpecSnippet("ExpectSpec", "        ", { "    expect(\"${it.testCaseName}\") {" }, "    }")

        // 2 段ネスト (describe { it { } } = 12 スペース)。
        val DescribeSpec = GenerateKotestSpecSnippet(
            specType = "DescribeSpec",
            bodyIndent = "            ",
            openTest = { "    describe(\"${it.testCaseName}\") {\n        it(\"reaches the recorded states\") {" },
            closeTest = "        }\n    }",
        )

        // 3 段ネスト (given { `when` { then { } } } = 16 スペース)。
        val BehaviorSpec = GenerateKotestSpecSnippet(
            specType = "BehaviorSpec",
            bodyIndent = "                ",
            openTest = {
                "    given(\"${it.testCaseName}\") {\n" +
                    "        `when`(\"${it.testCaseName}\") {\n" +
                    "            then(\"reaches the recorded states\") {"
            },
            closeTest = "            }\n        }\n    }",
        )
    }
}

/** All kotest spec snippets, keyed by framework name (shared with the asserts-block registry). */
internal val kotestSnippets: Map<String, GenerateTestSnippet> = mapOf(
    "kotest FreeSpec" to GenerateKotestSpecSnippet.FreeSpec,
    "kotest StringSpec" to GenerateKotestSpecSnippet.StringSpec,
    "kotest FunSpec" to GenerateKotestSpecSnippet.FunSpec,
    "kotest ShouldSpec" to GenerateKotestSpecSnippet.ShouldSpec,
    "kotest ExpectSpec" to GenerateKotestSpecSnippet.ExpectSpec,
    "kotest DescribeSpec" to GenerateKotestSpecSnippet.DescribeSpec,
    "kotest BehaviorSpec" to GenerateKotestSpecSnippet.BehaviorSpec,
)
