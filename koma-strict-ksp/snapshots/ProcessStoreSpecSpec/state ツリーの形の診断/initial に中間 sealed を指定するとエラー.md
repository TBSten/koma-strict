## Input:InitialGroup.kt

```kt
package example.diag

import koma.core.Action
import koma.core.State
import me.tbsten.koma.strict.OnAction
import me.tbsten.koma.strict.StoreSpec

@StoreSpec(initial = [IgState.Group::class])
sealed interface IgState : State {
    companion object

    @OnAction<IgAction.Go>(nextState = [Group.Leaf::class])
    interface Start : IgState { companion object }

    sealed interface Group : IgState {
        companion object

        interface Leaf : Group { companion object }
    }
}

sealed interface IgAction : Action {
    data object Go : IgAction
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
e: [ksp] <TMPDIR>/Kotlin-Compilation<N>/sources/InitialGroup.kt:8: Invalid koma-strict usage: initial element 'example.diag.IgState.Group' is not a concrete leaf state of 'example.diag.IgState'.

Solution: 
  Use concrete leaf states of this sealed hierarchy as @StoreSpec(initial = [...]) elements.
```

## Output:Generated sources

```kt

```
