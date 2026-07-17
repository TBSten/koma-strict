## Input:DupSameNode.kt

```kt
package example.diag

import koma.core.Action
import koma.core.State
import me.tbsten.koma.strict.OnAction
import me.tbsten.koma.strict.StoreSpec

@StoreSpec
sealed interface DupState : State {
    companion object

    @OnAction<DupAction.Go>(nextState = [Done::class])
    @OnAction<DupAction.Go>
    interface Start : DupState { companion object }

    interface Done : DupState { companion object }
}

sealed interface DupAction : Action {
    data object Go : DupAction
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
e: [ksp] <TMPDIR>/Kotlin-Compilation<N>/sources/DupSameNode.kt:14: Invalid koma-strict usage: Duplicate @OnAction<example.diag.DupAction.Go> on 'example.diag.DupState.Start': the same (state, action) pair is declared more than once.

Solution: 
  Merge the declarations into one @OnAction<example.diag.DupAction.Go>(nextState = [...]) with all transition targets.
```

## Output:Generated sources

```kt

```
