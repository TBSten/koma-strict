## Input:DupRecover.kt

```kt
package example.diag

import koma.core.Action
import koma.core.State
import me.tbsten.koma.strict.OnAction
import me.tbsten.koma.strict.OnRecover
import me.tbsten.koma.strict.StoreSpec

class DrException : Exception()

@StoreSpec
sealed interface DrState : State {
    companion object

    @OnAction<DrAction.Go>(nextState = [Done::class])
    @OnRecover<DrException>(nextState = [Done::class])
    @OnRecover<DrException>
    interface Start : DrState { companion object }

    interface Done : DrState { companion object }
}

sealed interface DrAction : Action {
    data object Go : DrAction
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
e: [ksp] <TMPDIR>/Kotlin-Compilation<N>/sources/DupRecover.kt:18: Invalid koma-strict usage: Duplicate @OnRecover<example.diag.DrException> on 'example.diag.DrState.Start': the same exception type is declared more than once.

Solution: 
  Merge the declarations into one @OnRecover<example.diag.DrException>(nextState = [...]).
```

## Output:Generated sources

```kt

```
