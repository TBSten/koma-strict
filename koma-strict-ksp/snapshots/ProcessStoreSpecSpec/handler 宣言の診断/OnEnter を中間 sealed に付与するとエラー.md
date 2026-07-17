## Input:EnterOnGroup.kt

```kt
package example.diag

import koma.core.Action
import koma.core.State
import me.tbsten.koma.strict.OnAction
import me.tbsten.koma.strict.OnEnter
import me.tbsten.koma.strict.StoreSpec

@StoreSpec
sealed interface EgState : State {
    companion object

    @OnEnter(nextState = [Group.Leaf::class])
    sealed interface Group : EgState {
        companion object

        @OnAction<EgAction.Go>(nextState = [Leaf::class])
        interface Leaf : Group { companion object }
    }
}

sealed interface EgAction : Action {
    data object Go : EgAction
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
e: [ksp] <TMPDIR>/Kotlin-Compilation<N>/sources/EnterOnGroup.kt:13: Invalid koma-strict usage: @OnEnter on 'example.diag.EgState.Group' is invalid: it can only be declared on a concrete leaf state.

Solution: 
  Declare @OnEnter on each concrete leaf state that should react to being entered.
```

## Output:Generated sources

```kt

```
