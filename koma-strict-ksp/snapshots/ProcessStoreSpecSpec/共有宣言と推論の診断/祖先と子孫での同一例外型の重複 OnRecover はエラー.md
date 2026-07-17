## Input:DupRecoverAncestor.kt

```kt
package example.diag

import koma.core.Action
import koma.core.State
import me.tbsten.koma.strict.OnAction
import me.tbsten.koma.strict.OnRecover
import me.tbsten.koma.strict.StoreSpec

class DraException : Exception()

@StoreSpec
@OnRecover<DraException>(nextState = [DraState.Done::class])
sealed interface DraState : State {
    companion object

    @OnAction<DraAction.Go>(nextState = [Done::class])
    @OnRecover<DraException>(nextState = [Done::class])
    interface Start : DraState { companion object }

    interface Done : DraState { companion object }
}

sealed interface DraAction : Action {
    data object Go : DraAction
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
e: [ksp] <TMPDIR>/Kotlin-Compilation<N>/sources/DupRecoverAncestor.kt:18: Invalid koma-strict usage: @OnRecover<example.diag.DraException> on 'example.diag.DraState.Start' duplicates the declaration on its ancestor 'example.diag.DraState'.

Solution: 
  Declare each exception type either on one ancestor (shared) or on individual leaves, not both.
```

## Output:Generated sources

```kt

```
