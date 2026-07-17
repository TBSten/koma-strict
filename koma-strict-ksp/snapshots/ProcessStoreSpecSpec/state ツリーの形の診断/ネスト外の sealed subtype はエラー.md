## Input:Outside.kt

```kt
package example.diag

import koma.core.Action
import koma.core.State
import me.tbsten.koma.strict.OnAction
import me.tbsten.koma.strict.StoreSpec

@StoreSpec
sealed interface OutsideState : State {
    companion object

    @OnAction<OsAction.Go>(nextState = [Done::class])
    interface Start : OutsideState { companion object }

    interface Done : OutsideState { companion object }
}

// body の外に置かれた subtype: 黙って無視せず tree 形を強制する
interface Stray : OutsideState { companion object }

sealed interface OsAction : Action {
    data object Go : OsAction
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
e: [ksp] <TMPDIR>/Kotlin-Compilation<N>/sources/Outside.kt:19: Invalid koma-strict usage: State 'example.diag.Stray' extends sealed state 'example.diag.OutsideState' but is not nested directly inside it.

Solution: 
  koma-strict derives the state tree from nesting. Move 'Stray' into the body of 'OutsideState'.
```

## Output:Generated sources

```kt

```
