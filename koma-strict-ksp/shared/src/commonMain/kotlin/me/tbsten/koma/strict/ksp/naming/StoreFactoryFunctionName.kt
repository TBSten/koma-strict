package me.tbsten.koma.strict.ksp.naming

import me.tbsten.koma.strict.InternalKomaStrictApi
import me.tbsten.koma.strict.ksp.model.RootNode

// per-store factory 関数 (型引数なしの糖衣入口) の命名規約。
// root 名の末尾 `State` を strip し、先頭に `create` / `restore` を付ける
// (.local/design/facade-named-arguments.md「追記: per-store factory 関数の生成(2026-07-16)」
// 「追記: create/restore への分離(2026-07-23)」)。

/**
 * Shared base of the generated per-store factory function names (`LceState` -> `Lce`).
 *
 * The trailing `State` of the root type name is stripped. Nested roots use the
 * underPackageName concatenation (`FooScreen.State` -> `FooScreenState` -> base `FooScreen`),
 * the same approach as the generated-type prefix ([rootTypePrefix]).
 *
 * Degenerate case: when stripping would leave nothing (a root named exactly `State`),
 * the name is kept unstripped (base `State`) — safe fallback instead of an error.
 */
private fun storeFactoryBaseName(root: RootNode): String {
    val prefix = rootTypePrefix(root)
    return prefix.removeSuffix("State").ifEmpty { prefix }
}

/**
 * Name of the generated per-store factory function that starts a store from a declared
 * `@StoreSpec(initial = ...)` candidate (`LceState` -> `createLceStore`).
 *
 * One overload is generated per candidate (`initialState` narrowed to that candidate's own
 * type — compile-time enforced); not generated at all when `initial` is undeclared, since there
 * is then no candidate to narrow to (see [restoreStoreFactoryFunctionName]).
 *
 * Same-named factory functions from different roots in one package (`Lce` and `LceState`
 * both yield `createLceStore`) are rejected by the generated-name collision diagnostic
 * ([me.tbsten.koma.strict.ksp.codegen.generatedTopLevelFunctionNames]).
 */
@InternalKomaStrictApi
public fun createStoreFactoryFunctionName(root: RootNode): String = "create${storeFactoryBaseName(root)}Store"

/**
 * Name of the generated per-store factory function that starts a store from an arbitrary state
 * of the root type (`LceState` -> `restoreLceStore`).
 *
 * Always generated, regardless of whether `@StoreSpec(initial = ...)` is declared — for
 * restoring a persisted state or starting mid-flow in tests, where
 * [createStoreFactoryFunctionName]'s compile-time-narrowed initial state does not apply.
 *
 * Same-named factory functions from different roots in one package are rejected by the
 * generated-name collision diagnostic
 * ([me.tbsten.koma.strict.ksp.codegen.generatedTopLevelFunctionNames]).
 */
@InternalKomaStrictApi
public fun restoreStoreFactoryFunctionName(root: RootNode): String = "restore${storeFactoryBaseName(root)}Store"
