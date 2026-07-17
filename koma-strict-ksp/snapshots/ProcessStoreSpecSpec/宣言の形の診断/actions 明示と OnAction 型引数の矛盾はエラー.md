## Input:Contradiction.kt

```kt
package example.diag

import koma.core.Action
import koma.core.State
import me.tbsten.koma.strict.OnAction
import me.tbsten.koma.strict.StoreSpec

@StoreSpec(actions = MainAction::class)
sealed interface ContradictionState : State {
    companion object

    @OnAction<OtherAction.Foo>(nextState = [Done::class])
    interface Start : ContradictionState { companion object }

    interface Done : ContradictionState { companion object }
}

sealed interface MainAction : Action {
    data object X : MainAction
}

sealed interface OtherAction : Action {
    data object Foo : OtherAction
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
e: [ksp] <TMPDIR>/Kotlin-Compilation<N>/sources/Contradiction.kt:12: Invalid koma-strict usage: The action type 'example.diag.OtherAction.Foo' (declared via @OnAction) is not a subtype of the explicit action root 'example.diag.MainAction'.

Solution: 
  Make 'Foo' a subtype of 'MainAction', or fix @StoreSpec(actions = ...).

w: [ksp] <TMPDIR>/Kotlin-Compilation<N>/sources/Contradiction.kt:19: koma-strict: action 'example.diag.MainAction.X' is never handled by any state (dead action).
```

## Output:Generated sources

```kt

```
