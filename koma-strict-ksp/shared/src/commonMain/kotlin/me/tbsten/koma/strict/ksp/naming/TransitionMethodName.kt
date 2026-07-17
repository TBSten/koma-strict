package me.tbsten.koma.strict.ksp.naming

import me.tbsten.koma.strict.InternalKomaStrictApi
import me.tbsten.koma.strict.ksp.model.StatePath

/**
 * Transition function name on Transitions (the Xxx part of `nextState.toXxx()`).
 *
 * Named "relative to the source" — the longest common prefix of the source node's parent
 * path and the target path is dropped, and the remainder is concatenated
 * (examples from samples.md):
 *
 * - `Loading` -> `Stable.Idle` = `toStableIdle` (no common prefix)
 * - `Stable.Idle` -> `Stable.Refreshing` = `toRefreshing` (`Stable` is dropped)
 * - `Stable.Refresh.Error` -> `Stable.Refresh.Loading` = `toLoading`
 * - `Search` -> `Search` (self-transition) = `toSearch`
 * - root shared declaration (source = root) -> `LoggedOut` = `toLoggedOut`
 *
 * TODO(doc/internal/generate-strict-store-factory-dsl.md open issue 4): collision resolution
 *   for same-simpleName leaves whose relative names coincide (qualified naming details) is
 *   undecided. Relative-path concatenation rarely collides today; qualify later if needed
 *   once decided.
 */
@InternalKomaStrictApi
public fun transitionMethodName(
    sourcePath: StatePath,
    targetPath: StatePath,
): String {
    val sourceParent = sourcePath.segments.dropLast(1)
    val common =
        sourceParent
            .zip(targetPath.segments)
            .takeWhile { (a, b) -> a == b }
            .size
    val relative = targetPath.segments.drop(common)
    // source の親が target を包含し切る形は tree 構造上起きないが、防御的に simpleName へ落とす
    val token = if (relative.isEmpty()) targetPath.simpleName.orEmpty() else relative.joinToString("")
    return "to$token"
}
