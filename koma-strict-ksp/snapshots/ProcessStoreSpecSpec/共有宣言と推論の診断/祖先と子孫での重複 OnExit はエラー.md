## Input:DupExitAncestor.kt

```kt
package example.diag

import koma.core.Action
import koma.core.Event
import koma.core.State
import me.tbsten.koma.strict.OnAction
import me.tbsten.koma.strict.OnExit
import me.tbsten.koma.strict.StoreSpec

@StoreSpec
@OnExit(emit = [DeaEvent.Left::class])
sealed interface DeaState : State {
    companion object

    @OnAction<DeaAction.Go>(nextState = [Done::class])
    @OnExit(emit = [DeaEvent.Left::class])
    interface Start : DeaState { companion object }

    interface Done : DeaState { companion object }
}

sealed interface DeaAction : Action {
    data object Go : DeaAction
}

sealed interface DeaEvent : Event {
    data object Left : DeaEvent
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
e: [ksp] <TMPDIR>/Kotlin-Compilation<N>/sources/DupExitAncestor.kt:17: Invalid koma-strict usage: @OnExit on 'example.diag.DeaState.Start' duplicates the declaration on its ancestor 'example.diag.DeaState' (koma's behavior for multiple exit blocks is unverified).

Solution: 
  Declare @OnExit either on one ancestor (shared) or on individual leaves, not both.
```

## Output:Generated sources

```kt

```
