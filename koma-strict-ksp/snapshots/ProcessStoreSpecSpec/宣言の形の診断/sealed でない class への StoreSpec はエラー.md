## Input:NotSealed.kt

```kt
package example.diag

import koma.core.State
import me.tbsten.koma.strict.StoreSpec

@StoreSpec
interface NotSealed : State {
    companion object
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
e: [ksp] <TMPDIR>/Kotlin-Compilation<N>/sources/NotSealed.kt:7: Invalid koma-strict usage: @StoreSpec must be applied to a sealed interface (or sealed class), but 'example.diag.NotSealed' is not sealed.

Solution: 
  Declare the state root as `sealed interface NotSealed` and nest all states inside it.
```

## Output:Generated sources

```kt

```
