package me.tbsten.koma.strict.idea.flow

/**
 * The koma-test API surface the generated snippets target. Swap the tokens here to retarget the
 * generated code at a different test API without touching the per-framework snippet generators.
 */
internal object KomaTestApi {
    const val DISPATCH_AND_AWAIT_METHOD_NAME = "dispatchAndAwait"
    const val START_AND_AWAIT_METHOD_NAME = "startAndAwait"

    fun getCurrentState(storeName: String) = "$storeName.currentState"
    fun dispatchAndAwait(storeName: String, actionExpr: String) = "$storeName.$DISPATCH_AND_AWAIT_METHOD_NAME($actionExpr)"
    fun startAndAwait(storeName: String) = "$storeName.$START_AND_AWAIT_METHOD_NAME()"
}
