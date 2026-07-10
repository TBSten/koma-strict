package me.tbsten.koma.strict.ksp.core.common

import me.tbsten.koma.strict.InternalKomaStrictApi

/**
 * KSP 非依存のクラス宣言情報。
 *
 * 命名ロジック (shared) の入力を抽象化して shared を KSP から完全に隔離する境界。
 * processor 側は `KSClassDeclaration.toClassDeclarationInfo()`
 * (:koma-strict-ksp の core/common/ClassDeclarationInfoExt.kt) で橋渡しする。
 */
@InternalKomaStrictApi
public interface ClassDeclarationInfo {
    public val packageName: String
    public val underPackageName: String
    public val simpleName: String
    public val fullName: String
}
