package me.tbsten.koma.strict.ksp.naming

import me.tbsten.koma.strict.InternalKomaStrictApi
import me.tbsten.koma.strict.ksp.model.RootNode
import me.tbsten.koma.strict.ksp.model.StatePath

// 生成ファイル名 (.kt は KSP の createNewFile が付ける)。
// 既存の storeFactoryFileName と同様 dot 入りで良い。
// ファイル分割は KSP incremental の依存単位に一致 (1 node = 1 file)。

/**
 * Generated file name of a node: `<qualified state>.generated`.
 * Example: `Loading` of `LceState` -> `LceState.Loading.generated`.
 * Nested roots include the underPackageName and therefore do not collide
 * (`FooScreen.State.Loading.generated`).
 */
@InternalKomaStrictApi
public fun generatedFileName(
    root: RootNode,
    path: StatePath,
): String = stateTypeReference(root, path) + ".generated"

/** File name of the root `states()` extension (the only whole-spec-dependent file): `<Root>.storeSpec.generated`. */
@InternalKomaStrictApi
public fun storeSpecFileName(root: RootNode): String = root.type.underPackageName + ".storeSpec.generated"
