package me.tbsten.koma.strict.idea.flow.kotest

import me.tbsten.koma.strict.idea.AssertState
import me.tbsten.koma.strict.idea.GenerateDispatchActionAndAssertStateStatementsBlock
import me.tbsten.koma.strict.idea.GenerateDispatchActionAndAssertStateStatementsBlockContext
import me.tbsten.koma.strict.idea.flow.KomaTestApi

/** kotest state assertion: `store.currentState shouldBe FeedState.Error`. */
internal object GenerateKotestAssertsBlock : GenerateDispatchActionAndAssertStateStatementsBlock() {
    override fun assert(
        context: GenerateDispatchActionAndAssertStateStatementsBlockContext,
        state: AssertState,
    ): String = "${context.indent}${KomaTestApi.getCurrentState(context.storeName)} shouldBe ${stateInstance(state)}"
}
