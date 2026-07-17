## Input:GenericState.kt

```kt
package example.diag

import koma.core.Action
import koma.core.State
import me.tbsten.koma.strict.OnAction
import me.tbsten.koma.strict.StoreSpec

@StoreSpec
sealed interface GenericState : State {
    companion object

    @OnAction<GenericAction.Go>(nextState = [Done::class])
    interface Holder<T> : GenericState { companion object }

    interface Done : GenericState { companion object }
}

sealed interface GenericAction : Action {
    data object Go : GenericAction
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
e: [ksp] <TMPDIR>/Kotlin-Compilation<N>/sources/GenericState.kt:13: Invalid koma-strict usage: Type-parameterized state 'example.diag.GenericState.Holder' is not supported (v1 restriction).

Solution: 
  Remove the type parameters from 'Holder' (e.g. hold the value as a concrete-typed property).
```

## Output:Generated sources

```kt

```
