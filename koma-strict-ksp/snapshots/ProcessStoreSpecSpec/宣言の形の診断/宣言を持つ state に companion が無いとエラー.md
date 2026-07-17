## Input:NoCompanion.kt

```kt
package example.diag

import koma.core.Action
import koma.core.State
import me.tbsten.koma.strict.OnAction
import me.tbsten.koma.strict.StoreSpec

@StoreSpec
sealed interface NoCompanionState : State {
    companion object

    @OnAction<NcAction.Go>(nextState = [Done::class])
    interface Start : NoCompanionState

    interface Done : NoCompanionState { companion object }
}

sealed interface NcAction : Action {
    data object Go : NcAction
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
e: [ksp] <TMPDIR>/Kotlin-Compilation<N>/sources/NoCompanion.kt:13: Invalid koma-strict usage: State 'example.diag.NoCompanionState.Start' declares handlers but has no companion object.

Solution: 
  Generated extensions (actions() / states() / factory) attach to the companion object. Add `companion object` to 'Start'.
```

## Output:Generated sources

```kt

```
