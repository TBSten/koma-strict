package me.tbsten.koma.strict.idea.flow

internal object KomaTestApi {
    fun getCurrentState(storeName: String) = "$storeName.currentState"
    const val DISPATCH_AND_AWAIT_METHOD_NAME = "dispatchAndAwait"
}