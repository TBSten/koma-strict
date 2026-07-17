## Input:GroupNoCompanion.kt

```kt
package example.diag

import koma.core.Action
import koma.core.State
import me.tbsten.koma.strict.OnAction
import me.tbsten.koma.strict.StoreSpec

@StoreSpec
sealed interface GncState : State {
    companion object

    @OnAction<GncAction.Go>(nextState = [Grouped.Inner::class])
    interface Start : GncState { companion object }

    // 自身は宣言ゼロでも、子が宣言を持つ = states() 束ね拡張の生やし先が必要
    sealed interface Grouped : GncState {
        @OnAction<GncAction.Back>(nextState = [Start::class])
        interface Inner : Grouped { companion object }
    }
}

sealed interface GncAction : Action {
    data object Go : GncAction
    data object Back : GncAction
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
e: [ksp] <TMPDIR>/Kotlin-Compilation<N>/sources/GroupNoCompanion.kt:16: Invalid koma-strict usage: Intermediate state 'example.diag.GncState.Grouped' has handler declarations in its subtree but no companion object.

Solution: 
  The generated states() bundling extension attaches to the companion object. Add `companion object` to 'Grouped'.
```

## Output:Generated sources

```kt

```
