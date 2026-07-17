## Input:NestState.kt

```kt
package example.clash

import koma.core.Action
import koma.core.State
import me.tbsten.koma.strict.OnAction
import me.tbsten.koma.strict.StoreSpec

@StoreSpec
sealed interface NestState : State {
    companion object

    // path 連結 (Stable + Idle) と単一 leaf 名 StableIdle が同じ prefix になる
    @OnAction<NsAction.Go>(nextState = [StableIdle::class])
    interface StableIdle : NestState { companion object }

    sealed interface Stable : NestState {
        companion object

        @OnAction<NsAction.Go>(nextState = [Idle::class])
        interface Idle : Stable { companion object }
    }
}

sealed interface NsAction : Action {
    data object Go : NsAction
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
e: [ksp] <TMPDIR>/Kotlin-Compilation<N>/sources/NestState.kt:9: Invalid koma-strict usage: Generated declaration 'StableIdleActionsBuilder' in package 'example.clash' is generated more than once (by 'example.clash.NestState'). Generated helper type names are derived from the state path without the root, and the store factory function name from the root name (with a trailing 'State' stripped), so nearby names collide within one package.

Solution: 
  Rename one of the conflicting states (or roots), or move one @StoreSpec hierarchy to another package so the generated names stay unique.

e: [ksp] <TMPDIR>/Kotlin-Compilation<N>/sources/NestState.kt:9: Invalid koma-strict usage: Generated declaration 'StableIdleGoReaction' in package 'example.clash' is generated more than once (by 'example.clash.NestState'). Generated helper type names are derived from the state path without the root, and the store factory function name from the root name (with a trailing 'State' stripped), so nearby names collide within one package.

Solution: 
  Rename one of the conflicting states (or roots), or move one @StoreSpec hierarchy to another package so the generated names stay unique.

e: [ksp] <TMPDIR>/Kotlin-Compilation<N>/sources/NestState.kt:9: Invalid koma-strict usage: Generated declaration 'StableIdleGoScope' in package 'example.clash' is generated more than once (by 'example.clash.NestState'). Generated helper type names are derived from the state path without the root, and the store factory function name from the root name (with a trailing 'State' stripped), so nearby names collide within one package.

Solution: 
  Rename one of the conflicting states (or roots), or move one @StoreSpec hierarchy to another package so the generated names stay unique.

e: [ksp] <TMPDIR>/Kotlin-Compilation<N>/sources/NestState.kt:9: Invalid koma-strict usage: Generated declaration 'StableIdleGoTransitions' in package 'example.clash' is generated more than once (by 'example.clash.NestState'). Generated helper type names are derived from the state path without the root, and the store factory function name from the root name (with a trailing 'State' stripped), so nearby names collide within one package.

Solution: 
  Rename one of the conflicting states (or roots), or move one @StoreSpec hierarchy to another package so the generated names stay unique.

e: [ksp] <TMPDIR>/Kotlin-Compilation<N>/sources/NestState.kt:9: Invalid koma-strict usage: Generated declaration 'StableIdleHandlers' in package 'example.clash' is generated more than once (by 'example.clash.NestState'). Generated helper type names are derived from the state path without the root, and the store factory function name from the root name (with a trailing 'State' stripped), so nearby names collide within one package.

Solution: 
  Rename one of the conflicting states (or roots), or move one @StoreSpec hierarchy to another package so the generated names stay unique.

e: [ksp] <TMPDIR>/Kotlin-Compilation<N>/sources/NestState.kt:9: Invalid koma-strict usage: Generated declaration 'StableIdleHandlersScope' in package 'example.clash' is generated more than once (by 'example.clash.NestState'). Generated helper type names are derived from the state path without the root, and the store factory function name from the root name (with a trailing 'State' stripped), so nearby names collide within one package.

Solution: 
  Rename one of the conflicting states (or roots), or move one @StoreSpec hierarchy to another package so the generated names stay unique.

e: [ksp] <TMPDIR>/Kotlin-Compilation<N>/sources/NestState.kt:9: Invalid koma-strict usage: Generated declaration 'StableIdleImpl' in package 'example.clash' is generated more than once (by 'example.clash.NestState'). Generated helper type names are derived from the state path without the root, and the store factory function name from the root name (with a trailing 'State' stripped), so nearby names collide within one package.

Solution: 
  Rename one of the conflicting states (or roots), or move one @StoreSpec hierarchy to another package so the generated names stay unique.
```

## Output:Generated sources

```kt

```
