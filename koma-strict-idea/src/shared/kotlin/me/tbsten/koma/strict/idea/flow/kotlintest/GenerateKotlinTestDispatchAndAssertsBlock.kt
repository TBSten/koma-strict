package me.tbsten.koma.strict.idea.flow.kotlintest

import me.tbsten.koma.strict.idea.AssertState
import me.tbsten.koma.strict.idea.GenerateDispatchActionAndAssertStateStatementsBlock
import me.tbsten.koma.strict.idea.GenerateDispatchActionAndAssertStateStatementsBlockContext
import me.tbsten.koma.strict.idea.flow.KomaTestApi

internal object GenerateKotlinTestDispatchAndAssertsBlock : GenerateDispatchActionAndAssertStateStatementsBlock() {
    override fun assert(
        context: GenerateDispatchActionAndAssertStateStatementsBlockContext,
        state: AssertState
    ): String = "assertEquals(${state.stateName}, ${KomaTestApi.getCurrentState(storeName = context.storeName)})"
}
