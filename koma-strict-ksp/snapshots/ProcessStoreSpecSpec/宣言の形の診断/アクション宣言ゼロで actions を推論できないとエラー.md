## Input:NoActions.kt

```kt
package example.diag

import koma.core.State
import me.tbsten.koma.strict.OnEnter
import me.tbsten.koma.strict.StoreSpec

@StoreSpec
sealed interface NoActionsState : State {
    companion object

    @OnEnter(nextState = [Done::class])
    interface Loading : NoActionsState { companion object }

    interface Done : NoActionsState { companion object }
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
e: [ksp] <TMPDIR>/Kotlin-Compilation<N>/sources/NoActions.kt:7: Invalid koma-strict usage: Cannot infer the actions type: no @OnAction declarations found in this @StoreSpec hierarchy.

Solution: 
  Declare at least one @OnAction<...> handler, or specify the actions root explicitly with @StoreSpec(actions = MyAction::class).
```

## Output:Generated sources

```kt

```
