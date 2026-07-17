## Input:EnumState.kt

```kt
package example.diag

import koma.core.Action
import koma.core.State
import me.tbsten.koma.strict.OnAction
import me.tbsten.koma.strict.StoreSpec

@StoreSpec
sealed interface EnumHolderState : State {
    companion object

    @OnAction<EhAction.Go>(nextState = [Done::class])
    interface Start : EnumHolderState { companion object }

    interface Done : EnumHolderState { companion object }

    enum class Mode : EnumHolderState { A, B }   // enum は state として不可
}

sealed interface EhAction : Action {
    data object Go : EhAction
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
e: [ksp] <TMPDIR>/Kotlin-Compilation<N>/sources/EnumState.kt:17: Invalid koma-strict usage: State 'example.diag.EnumHolderState.Mode' must be declared as an interface, a (data) class, or a (data) object (found: ENUM_CLASS).

Solution: 
  Use one of the two supported state declaration forms: `interface` (recommended) or `data class` / `data object`.
```

## Output:Generated sources

```kt

```
