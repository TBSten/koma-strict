package me.tbsten.koma.strict.idea

import me.tbsten.koma.strict.idea.flow.KomaTestApi
import me.tbsten.koma.strict.idea.flow.SnippetGenerator
import me.tbsten.koma.strict.idea.flow.kotest.GenerateKotestAssertsBlock
import me.tbsten.koma.strict.idea.flow.kotest.kotestSnippets
import me.tbsten.koma.strict.idea.flow.kotlintest.GenerateKotlinTestDispatchAndAssertsBlock

internal data class GenerateDispatchActionAndAssertStateStatementsBlockContext(
    val indent: String,
    val storeName: String,
    val stateAndActions: List<StateOrAction>,
)

/** One statement of the recorded scenario: assert a state, dispatch an action, or raise an exception. */
sealed interface StateOrAction

/** Assert the store is in [stateName] (e.g. `FeedState.Error`). */
data class AssertState(val stateName: String) : StateOrAction

/** Dispatch [actionName]; [actionConstructorProperties] = (name, type) pairs when it has a constructor, else null. */
data class DispatchAction(val actionName: String, val actionConstructorProperties: List<Pair<String, String>>?) : StateOrAction

/** Raise [exceptionName] to drive an `@OnRecover` (emitted as a TODO — the plugin can't synthesize the throw). */
data class RaiseException(val exceptionName: String) : StateOrAction

/**
 * Renders the body statements (dispatch / assert / raise) of a generated test. The state assertion is
 * left [abstract] so each test framework fills in its own form (`assertEquals(...)` / `... shouldBe ...`);
 * dispatch and raise are framework-agnostic.
 */
internal abstract class GenerateDispatchActionAndAssertStateStatementsBlock :
    SnippetGenerator<GenerateDispatchActionAndAssertStateStatementsBlockContext>() {

    final override fun generate(context: GenerateDispatchActionAndAssertStateStatementsBlockContext): String = with(context) {
        // 各文が自分で indent を付ける (assert 実装は context.indent を前置きする)。ここで二重に付けない。
        stateAndActions.joinToString("\n") { stateOrAction ->
            when (stateOrAction) {
                is AssertState -> assert(context, stateOrAction)
                is DispatchAction -> indent + KomaTestApi.dispatchAndAwait(storeName, actionCall(stateOrAction))
                is RaiseException -> indent + "// TODO: raise ${stateOrAction.exceptionName} to drive @OnRecover"
            }
        }
    }

    /** Framework-specific state assertion, e.g. `assertEquals(X, store.currentState)` / `store.currentState shouldBe X`. */
    abstract fun assert(context: GenerateDispatchActionAndAssertStateStatementsBlockContext, state: AssertState): String

    /**
     * A *state instance* expression to compare `currentState` against — `FeedState.Error(/* TODO */)`.
     * A bare `FeedState.Error` is only the type; koma states are constructed via `invoke`, and the plugin
     * doesn't know the expected props, so the constructor args are left as a TODO for the developer.
     */
    protected fun stateInstance(state: AssertState): String = "${state.stateName}(/* TODO: expected state props */)"

    private fun actionCall(action: DispatchAction): String {
        val props = action.actionConstructorProperties
        return if (props.isNullOrEmpty()) {
            action.actionName
        } else {
            action.actionName + "(" + props.joinToString(", ") { (name, type) -> "$name = /* $type */ TODO()" } + ")"
        }
    }

    companion object {
        // 全 kotest spec は asserts (shouldBe) が共通なので、kotest の全キーへ同じブロックを割り当てる。
        val All: Map<String, GenerateDispatchActionAndAssertStateStatementsBlock> =
            mapOf("kotlin.test" to GenerateKotlinTestDispatchAndAssertsBlock) +
                kotestSnippets.keys.associateWith { GenerateKotestAssertsBlock }
    }
}
