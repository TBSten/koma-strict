## Input:DefaultClash.kt

```kt
package example.diag

import koma.core.Action
import koma.core.State
import me.tbsten.koma.strict.OnAction
import me.tbsten.koma.strict.StoreSpec

@StoreSpec
@OnAction<ClashAction.Go>(nextState = [ClashState.Ok::class])
sealed interface ClashState : State {
    companion object

    @OnAction<ClashAction.Other>(nextState = [Ok::class])
    interface Default : ClashState { companion object }

    interface Ok : ClashState { companion object }
}

sealed interface ClashAction : Action {
    data object Go : ClashAction
    data object Other : ClashAction
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
e: [ksp] <TMPDIR>/Kotlin-Compilation<N>/sources/DefaultClash.kt:14: Invalid koma-strict usage: State 'example.diag.ClashState.Default' collides with the shared (default) block: both become the states() parameter 'default'.

Solution: 
  Rename the shared block with @DefaultName("...") on the parent sealed state, or rename the state.
```

## Output:Generated sources

```kt

```
