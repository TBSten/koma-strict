## Input:GenericAction.kt

```kt
package example.diag

import koma.core.Action
import koma.core.State
import me.tbsten.koma.strict.OnAction
import me.tbsten.koma.strict.StoreSpec

@StoreSpec
sealed interface GaState : State {
    companion object

    @OnAction<GaAction.Paged<String>>(nextState = [Done::class])
    interface Start : GaState { companion object }

    interface Done : GaState { companion object }
}

sealed interface GaAction : Action {
    data class Paged<T>(val items: List<T>) : GaAction
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
e: [ksp] <TMPDIR>/Kotlin-Compilation<N>/sources/GenericAction.kt:12: Invalid koma-strict usage: Type-parameterized action 'example.diag.GaAction.Paged' is not supported in @OnAction (v1 restriction).

Solution: 
  Generated code cannot re-render the type arguments. Use a non-generic action type (e.g. hold the value as a concrete-typed property).
```

## Output:Generated sources

```kt

```
