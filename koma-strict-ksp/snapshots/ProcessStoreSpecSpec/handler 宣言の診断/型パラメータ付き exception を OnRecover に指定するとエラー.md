## Input:GenericException.kt

```kt
package example.diag

import koma.core.Action
import koma.core.State
import me.tbsten.koma.strict.OnAction
import me.tbsten.koma.strict.OnRecover
import me.tbsten.koma.strict.StoreSpec

class GxException<T>(val payload: T) : Exception()

@StoreSpec
sealed interface GxState : State {
    companion object

    @OnAction<GxAction.Go>(nextState = [Done::class])
    @OnRecover<GxException<String>>(nextState = [Done::class])
    interface Start : GxState { companion object }

    interface Done : GxState { companion object }
}

sealed interface GxAction : Action {
    data object Go : GxAction
}
```

## KSP options

```kt
ksp {
    arg("koma.strict.deadActionSeverity", "WARNING" /* default */)
}
```

## Output:ExitCode

```text
COMPILATION_ERROR
```

## Output:Console

```text
e: Error occurred in KSP, check log for detail
e: [ksp] <TMPDIR>/Kotlin-Compilation<N>/sources/GenericException.kt:16: Invalid koma-strict usage: Type-parameterized exception 'example.diag.GxException' is not supported in @OnRecover (v1 restriction).

Solution: 
  Generated code cannot re-render the type arguments. Use a non-generic exception type (e.g. hold the value as a concrete-typed property).
```

## Output:Generated sources

```kt

```
