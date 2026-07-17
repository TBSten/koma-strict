## Input:SplitActions.kt

```kt
package example.diag

import koma.core.Action
import koma.core.State
import me.tbsten.koma.strict.OnAction
import me.tbsten.koma.strict.StoreSpec

@StoreSpec
sealed interface SplitState : State {
    companion object

    @OnAction<FirstAction.Go>(nextState = [Other::class])
    interface Start : SplitState { companion object }

    @OnAction<SecondAction.Back>(nextState = [Start::class])
    interface Other : SplitState { companion object }
}

sealed interface FirstAction : Action {
    data object Go : FirstAction
}

sealed interface SecondAction : Action {
    data object Back : SecondAction
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
e: [ksp] <TMPDIR>/Kotlin-Compilation<N>/sources/SplitActions.kt:8: Invalid koma-strict usage: Cannot infer a common sealed supertype for the declared action types: 'example.diag.FirstAction.Go', 'example.diag.SecondAction.Back'.

Solution: 
  Make all action types share one sealed root, or specify it explicitly with @StoreSpec(actions = ...).
```

## Output:Generated sources

```kt

```
