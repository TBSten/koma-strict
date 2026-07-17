## Input:PrivateState.kt

```kt
package example.diag

import koma.core.Action
import koma.core.State
import me.tbsten.koma.strict.OnAction
import me.tbsten.koma.strict.StoreSpec

@StoreSpec
sealed interface PvState : State {
    companion object

    @OnAction<PvAction.Go>(nextState = [Done::class])
    interface Start : PvState { companion object }

    // 生成物 (top-level) が参照できない可視性は v1 で明示拒否する
    private interface Done : PvState { companion object }
}

sealed interface PvAction : Action {
    data object Go : PvAction
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
e: [ksp] <TMPDIR>/Kotlin-Compilation<N>/sources/PrivateState.kt:16: Invalid koma-strict usage: State 'example.diag.PvState.Done' has unsupported visibility 'private' (v1 restriction: only public / internal states are supported).

Solution: 
  Generated support types are top-level declarations that inherit the state's visibility. Declare 'Done' as public or internal.
```

## Output:Generated sources

```kt

```
