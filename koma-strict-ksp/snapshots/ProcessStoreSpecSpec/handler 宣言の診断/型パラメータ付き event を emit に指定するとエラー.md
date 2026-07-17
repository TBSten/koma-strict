## Input:GenericEvent.kt

```kt
package example.diag

import koma.core.Action
import koma.core.Event
import koma.core.State
import me.tbsten.koma.strict.OnAction
import me.tbsten.koma.strict.StoreSpec

@StoreSpec
sealed interface GeState : State {
    companion object

    @OnAction<GeAction.Go>(nextState = [Done::class], emit = [GeEvent.Loaded::class])
    interface Start : GeState { companion object }

    interface Done : GeState { companion object }
}

sealed interface GeAction : Action {
    data object Go : GeAction
}

sealed interface GeEvent : Event {
    data class Loaded<T>(val items: List<T>) : GeEvent
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
e: [ksp] <TMPDIR>/Kotlin-Compilation<N>/sources/GenericEvent.kt:13: Invalid koma-strict usage: Type-parameterized event 'example.diag.GeEvent.Loaded' is not supported in emit = [...] (v1 restriction).

Solution: 
  Generated code cannot re-render the type arguments. Use a non-generic event type (e.g. hold the value as a concrete-typed property).
```

## Output:Generated sources

```kt

```
