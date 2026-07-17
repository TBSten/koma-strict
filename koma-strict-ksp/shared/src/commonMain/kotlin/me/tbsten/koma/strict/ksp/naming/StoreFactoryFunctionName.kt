package me.tbsten.koma.strict.ksp.naming

import me.tbsten.koma.strict.InternalKomaStrictApi
import me.tbsten.koma.strict.ksp.model.RootNode

// per-store factory 関数 (型引数なしの糖衣入口) の命名規約。
// root 名の末尾 `State` を strip して decapitalize + `Store`
// (.local/design/facade-named-arguments.md「追記: per-store factory 関数の生成(2026-07-16)」)。

/**
 * Name of the generated per-store factory function (`LceState` -> `lceStore`).
 *
 * The trailing `State` of the root type name is stripped, the remainder is decapitalized
 * and suffixed with `Store`. Nested roots use the underPackageName concatenation
 * (`FooScreen.State` -> `FooScreenState` -> `fooScreenStore`), the same approach as the
 * generated-type prefix ([rootTypePrefix]).
 *
 * Degenerate case: when stripping would leave nothing (a root named exactly `State`),
 * the name is kept unstripped (`State` -> `stateStore`) — safe fallback instead of an error.
 *
 * Same-named factory functions from different roots in one package (`Lce` and `LceState`
 * both yield `lceStore`) are rejected by the generated-name collision diagnostic
 * ([me.tbsten.koma.strict.ksp.codegen.generatedTopLevelFunctionNames]).
 */
@InternalKomaStrictApi
public fun storeFactoryFunctionName(root: RootNode): String {
    val prefix = rootTypePrefix(root)
    val base = prefix.removeSuffix("State").ifEmpty { prefix }
    return base.decapitalized() + "Store"
}
