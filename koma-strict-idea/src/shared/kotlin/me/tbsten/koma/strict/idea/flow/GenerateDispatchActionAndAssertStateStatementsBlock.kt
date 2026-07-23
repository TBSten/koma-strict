package me.tbsten.koma.strict.idea

import me.tbsten.koma.strict.idea.flow.TemplateVariablesContainer
import me.tbsten.koma.strict.idea.flow.kotlintest.KomaTestApi
import org.intellij.lang.annotations.Language

internal data class GenerateDispatchActionAndAssertStateStatementsBlockContext(
    val indent: String,
    val storeName: String,
    val stateAndActions: List<StateOrAction>,
)

sealed interface StateOrAction

data class AssertState(val stateName: String) : StateOrAction

data class DispatchAction(val actionName: String, val actionConstructorProperties: List<Pair<String, String>>?) : StateOrAction

internal abstract class GenerateDispatchActionAndAssertStateStatementsBlock : TemplateVariablesContainer<GenerateDispatchActionAndAssertStateStatementsBlockContext>() {
    final override fun replaceByVariables(context: GenerateDispatchActionAndAssertStateStatementsBlockContext): String = with(context) {
        stateAndActions
            .joinToString("\n") { stateOrAction ->
                "${indent}${
                    when(stateOrAction) {
                        is AssertState ->
                            assert(context, stateOrAction)
                        is DispatchAction ->
                            KomaTestApi.DISPATCH_AND_AWAIT_METHOD_NAME +
                                    "(" +
                                    stateOrAction.actionName +
                                    (stateOrAction.actionConstructorProperties?.joinToString(", "){ (argName: String, argType: String) ->
                                        "$argName: $argType"
                                    } ?: "/* TODO */") +
                                    ")"
                    }
                }"
            }
        @Language("kotlin")
        """
            assertEquals
        """.trimIndent()
    }

    abstract fun assert(context: GenerateDispatchActionAndAssertStateStatementsBlockContext, state: AssertState): String
}
