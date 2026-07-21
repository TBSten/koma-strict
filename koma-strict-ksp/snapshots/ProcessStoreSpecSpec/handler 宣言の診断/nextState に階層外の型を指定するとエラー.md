## Input:ForeignTarget.kt

```kt
package example.diag

import koma.core.Action
import koma.core.State
import me.tbsten.koma.strict.OnAction
import me.tbsten.koma.strict.StoreSpec

// State は実装している (nextState の KClass<out State> 境界は満たす) が
// FtState 階層の外 → KSP 診断で検出されるケース
class Foreign : State

@StoreSpec
sealed interface FtState : State {
    companion object

    @OnAction<FtAction.Go>(nextState = [Foreign::class])
    interface Start : FtState { companion object }
}

sealed interface FtAction : Action {
    data object Go : FtAction
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
e: [ksp] <TMPDIR>/Kotlin-Compilation<N>/sources/ForeignTarget.kt:16: Invalid koma-strict usage: nextState element 'example.diag.Foreign' is not a state of 'example.diag.FtState'.

Solution: 
  Use concrete leaf states of the same sealed hierarchy (or Stay::class) as nextState elements.
```

## Output:Generated sources

```kt

```
