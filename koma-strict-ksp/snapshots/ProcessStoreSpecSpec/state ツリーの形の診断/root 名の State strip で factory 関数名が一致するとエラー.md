## Input:PlayerStates.kt

```kt
package example.clash

import koma.core.Action
import koma.core.State
import me.tbsten.koma.strict.OnAction
import me.tbsten.koma.strict.StoreSpec

// leaf 名は互いに異なる (型は衝突しない) が、root 名の末尾 State を strip した
// factory 関数名がどちらも playerStore になる
@StoreSpec
sealed interface PlayerState : State {
    companion object

    @OnAction<PlayerAction.Play>(nextState = [Playing::class])
    interface Playing : PlayerState { companion object }
}

@StoreSpec
sealed interface Player : State {
    companion object

    @OnAction<PlayerAction.Play>(nextState = [Paused::class])
    interface Paused : Player { companion object }
}

sealed interface PlayerAction : Action {
    data object Play : PlayerAction
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
e: [ksp] <TMPDIR>/Kotlin-Compilation<N>/sources/PlayerStates.kt:19: Invalid koma-strict usage: Generated declaration 'restorePlayerStore' in package 'example.clash' is generated more than once (by 'example.clash.Player' and 'example.clash.PlayerState'). Generated helper type names are derived from the state path without the root, and the store factory function name from the root name (with a trailing 'State' stripped), so nearby names collide within one package.

Solution: 
  Rename one of the conflicting states (or roots), or move one @StoreSpec hierarchy to another package so the generated names stay unique.
```

## Output:Generated sources

```kt

```
