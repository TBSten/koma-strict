## Input:GroupTarget.kt

```kt
package example.diag

import koma.core.Action
import koma.core.State
import me.tbsten.koma.strict.OnAction
import me.tbsten.koma.strict.StoreSpec

@StoreSpec
sealed interface GtState : State {
    companion object

    @OnAction<GtAction.Go>(nextState = [Group::class])
    interface Start : GtState { companion object }

    sealed interface Group : GtState {
        companion object

        interface Leaf : Group { companion object }
    }
}

sealed interface GtAction : Action {
    data object Go : GtAction
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
e: [ksp] <TMPDIR>/Kotlin-Compilation<N>/sources/GroupTarget.kt:12: Invalid koma-strict usage: nextState element 'example.diag.GtState.Group' is an intermediate sealed state (transitions must target concrete leaves).

Solution: 
  Use concrete leaf states of the same sealed hierarchy (or Stay::class) as nextState elements.
```

## Output:Generated sources

```kt

```
