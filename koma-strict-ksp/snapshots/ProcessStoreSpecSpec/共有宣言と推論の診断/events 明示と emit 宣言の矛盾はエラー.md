## Input:EventContradiction.kt

```kt
package example.diag

import koma.core.Action
import koma.core.Event
import koma.core.State
import me.tbsten.koma.strict.OnAction
import me.tbsten.koma.strict.StoreSpec

@StoreSpec(events = MainEvent::class)
sealed interface EcState : State {
    companion object

    @OnAction<EcAction.Go>(nextState = [Done::class], emit = [OtherEvent.Boom::class])
    interface Start : EcState { companion object }

    interface Done : EcState { companion object }
}

sealed interface EcAction : Action {
    data object Go : EcAction
}

sealed interface MainEvent : Event {
    data object X : MainEvent
}

sealed interface OtherEvent : Event {
    data object Boom : OtherEvent
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
e: [ksp] <TMPDIR>/Kotlin-Compilation<N>/sources/EventContradiction.kt:13: Invalid koma-strict usage: The event type 'example.diag.OtherEvent.Boom' (declared via emit) is not a subtype of the explicit event root 'example.diag.MainEvent'.

Solution: 
  Make 'Boom' a subtype of 'MainEvent', or fix @StoreSpec(events = ...).
```

## Output:Generated sources

```kt

```
