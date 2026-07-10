package me.tbsten.koma.strict.ksp.core.common

import me.tbsten.koma.strict.InternalKomaStrictApi

// TODO(doc/internal/generate-strict-store-factory-dsl.md): placeholder naming。
//   本設計では facade 関数は `myStateStore(...)`、生成ファイルは `<qualified state>.generated.kt`
//   (1 node = 1 file)。DSL 実装着手時にこのファイルの命名ロジックごと置き換える。
//   その際 facade 関数名の決定では「同一パッケージ内に同 simpleName のネストクラス
//   (FooScreen.State / BarScreen.State) が並ぶ」衝突軸を必ず考慮すること (下記関数名と同じ理由)。

/**
 * `MyState` -> `myStateStoreFactory`。
 * ネストクラスは underPackageName 由来で top-level 関数名の衝突を回避する
 * (`FooScreen.State` -> `fooScreenStateStoreFactory`。simpleName ベースだと同一パッケージの
 * `BarScreen.State` と同名になり consumer が Conflicting overloads でコンパイル不能になる)。
 */
@InternalKomaStrictApi
public fun storeFactoryFunctionName(source: ClassDeclarationInfo): String =
    source.underPackageName
        .replace(".", "")
        .replaceFirstChar { it.lowercaseChar() } + "StoreFactory"

/**
 * `MyState` -> `MyStateStoreFactory` (.kt は KSP の createNewFile が付ける)。
 * ネストクラスは underPackageName でファイル名衝突を回避する (`Outer.Inner` -> `Outer.InnerStoreFactory`)。
 */
@InternalKomaStrictApi
public fun storeFactoryFileName(source: ClassDeclarationInfo): String =
    source.underPackageName + "StoreFactory"
