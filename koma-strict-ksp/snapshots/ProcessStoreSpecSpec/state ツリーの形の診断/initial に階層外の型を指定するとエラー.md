## Input:InitialForeign.kt

```kt
package example.diag

import koma.core.Action
import koma.core.State
import me.tbsten.koma.strict.OnAction
import me.tbsten.koma.strict.StoreSpec

class NotAState

@StoreSpec(initial = [NotAState::class])
sealed interface IfState : State {
    companion object

    @OnAction<IfAction.Go>(nextState = [Done::class])
    interface Start : IfState { companion object }

    interface Done : IfState { companion object }
}

sealed interface IfAction : Action {
    data object Go : IfAction
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
e: [ksp] <TMPDIR>/Kotlin-Compilation<N>/sources/InitialForeign.kt:10: Invalid koma-strict usage: initial element 'example.diag.NotAState' is not a concrete leaf state of 'example.diag.IfState'.

Solution: 
  Use concrete leaf states of this sealed hierarchy as @StoreSpec(initial = [...]) elements.
```

## Output:Generated sources

```kt

```
