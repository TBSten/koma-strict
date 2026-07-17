## Input:DefaultNameOnLeaf.kt

```kt
package example.diag

import koma.core.Action
import koma.core.State
import me.tbsten.koma.strict.DefaultName
import me.tbsten.koma.strict.OnAction
import me.tbsten.koma.strict.StoreSpec

@StoreSpec
sealed interface DnlState : State {
    companion object

    @DefaultName("common")   // leaf には付けられない
    @OnAction<DnlAction.Go>(nextState = [Done::class])
    interface Start : DnlState { companion object }

    interface Done : DnlState { companion object }
}

sealed interface DnlAction : Action {
    data object Go : DnlAction
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
e: [ksp] <TMPDIR>/Kotlin-Compilation<N>/sources/DefaultNameOnLeaf.kt:13: Invalid koma-strict usage: @DefaultName on 'example.diag.DnlState.Start' is invalid: it can only be applied to the sealed root or an intermediate sealed state.

Solution: 
  Move @DefaultName to the sealed state that owns the shared (default) block.
```

## Output:Generated sources

```kt

```
