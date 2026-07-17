## Input:FooScreenState.kt

```kt
package example.clash

import koma.core.Action
import koma.core.State
import me.tbsten.koma.strict.OnAction
import me.tbsten.koma.strict.StoreSpec

@StoreSpec
sealed interface FooScreenState : State {
    companion object

    @OnAction<FooScreenAction.Reload>(nextState = [Loading::class])
    interface Loading : FooScreenState { companion object }
}

sealed interface FooScreenAction : Action {
    data object Reload : FooScreenAction
}
```

## Input:BarScreenState.kt

```kt
package example.clash

import koma.core.Action
import koma.core.State
import me.tbsten.koma.strict.OnAction
import me.tbsten.koma.strict.StoreSpec

@StoreSpec
sealed interface BarScreenState : State {
    companion object

    // FooScreenState.Loading と同名 leaf + 同名アクション ->
    // LoadingImpl / LoadingReloadReaction / LoadingHandlers 等が衝突する
    @OnAction<BarScreenAction.Reload>(nextState = [Loading::class])
    interface Loading : BarScreenState { companion object }
}

sealed interface BarScreenAction : Action {
    data object Reload : BarScreenAction
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
e: [ksp] <TMPDIR>/Kotlin-Compilation<N>/sources/BarScreenState.kt:9: Invalid koma-strict usage: Generated declaration 'LoadingActionsBuilder' in package 'example.clash' is generated more than once (by 'example.clash.BarScreenState' and 'example.clash.FooScreenState'). Generated helper type names are derived from the state path without the root, and the store factory function name from the root name (with a trailing 'State' stripped), so nearby names collide within one package.

Solution: 
  Rename one of the conflicting states (or roots), or move one @StoreSpec hierarchy to another package so the generated names stay unique.

e: [ksp] <TMPDIR>/Kotlin-Compilation<N>/sources/BarScreenState.kt:9: Invalid koma-strict usage: Generated declaration 'LoadingHandlers' in package 'example.clash' is generated more than once (by 'example.clash.BarScreenState' and 'example.clash.FooScreenState'). Generated helper type names are derived from the state path without the root, and the store factory function name from the root name (with a trailing 'State' stripped), so nearby names collide within one package.

Solution: 
  Rename one of the conflicting states (or roots), or move one @StoreSpec hierarchy to another package so the generated names stay unique.

e: [ksp] <TMPDIR>/Kotlin-Compilation<N>/sources/BarScreenState.kt:9: Invalid koma-strict usage: Generated declaration 'LoadingHandlersScope' in package 'example.clash' is generated more than once (by 'example.clash.BarScreenState' and 'example.clash.FooScreenState'). Generated helper type names are derived from the state path without the root, and the store factory function name from the root name (with a trailing 'State' stripped), so nearby names collide within one package.

Solution: 
  Rename one of the conflicting states (or roots), or move one @StoreSpec hierarchy to another package so the generated names stay unique.

e: [ksp] <TMPDIR>/Kotlin-Compilation<N>/sources/BarScreenState.kt:9: Invalid koma-strict usage: Generated declaration 'LoadingImpl' in package 'example.clash' is generated more than once (by 'example.clash.BarScreenState' and 'example.clash.FooScreenState'). Generated helper type names are derived from the state path without the root, and the store factory function name from the root name (with a trailing 'State' stripped), so nearby names collide within one package.

Solution: 
  Rename one of the conflicting states (or roots), or move one @StoreSpec hierarchy to another package so the generated names stay unique.

e: [ksp] <TMPDIR>/Kotlin-Compilation<N>/sources/BarScreenState.kt:9: Invalid koma-strict usage: Generated declaration 'LoadingReloadReaction' in package 'example.clash' is generated more than once (by 'example.clash.BarScreenState' and 'example.clash.FooScreenState'). Generated helper type names are derived from the state path without the root, and the store factory function name from the root name (with a trailing 'State' stripped), so nearby names collide within one package.

Solution: 
  Rename one of the conflicting states (or roots), or move one @StoreSpec hierarchy to another package so the generated names stay unique.

e: [ksp] <TMPDIR>/Kotlin-Compilation<N>/sources/BarScreenState.kt:9: Invalid koma-strict usage: Generated declaration 'LoadingReloadScope' in package 'example.clash' is generated more than once (by 'example.clash.BarScreenState' and 'example.clash.FooScreenState'). Generated helper type names are derived from the state path without the root, and the store factory function name from the root name (with a trailing 'State' stripped), so nearby names collide within one package.

Solution: 
  Rename one of the conflicting states (or roots), or move one @StoreSpec hierarchy to another package so the generated names stay unique.

e: [ksp] <TMPDIR>/Kotlin-Compilation<N>/sources/BarScreenState.kt:9: Invalid koma-strict usage: Generated declaration 'LoadingReloadTransitions' in package 'example.clash' is generated more than once (by 'example.clash.BarScreenState' and 'example.clash.FooScreenState'). Generated helper type names are derived from the state path without the root, and the store factory function name from the root name (with a trailing 'State' stripped), so nearby names collide within one package.

Solution: 
  Rename one of the conflicting states (or roots), or move one @StoreSpec hierarchy to another package so the generated names stay unique.
```

## Output:Generated sources

```kt

```
