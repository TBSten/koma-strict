package me.tbsten.koma.strict.ksp.core.common

import com.google.devtools.ksp.symbol.KSDeclaration
import me.tbsten.koma.strict.ksp.UnknownKomaStrictException
import kotlin.reflect.KClass

// koma-strict 固有型 (UnknownKomaStrictException) に依存するため util ではなく core/common に置く。
// ここの throw は「内部想定外」なので診断ポリシー (throw 禁止はユーザー誤用のみ) に反しない。

internal val KSDeclaration.fullName: String
    get() =
        qualifiedName?.asString()
            ?: throw UnknownKomaStrictException("qualifiedName is null")

internal val KSDeclaration.underPackageName: String
    // removePrefix であることが重要: replace (全出現置換) だと
    // (1) default package (packageName == "") で全ドットが消え `Outer.Inner` -> `OuterInner` になる
    // (2) パッケージ名と同名セグメントを挟む修飾名 (`foo.Outer.foo.Inner`) の途中まで消える
    get() {
        val pkg = packageName.asString()
        return if (pkg.isEmpty()) fullName else fullName.removePrefix("$pkg.")
    }

internal val KClass<*>.fullName: String
    get() =
        qualifiedName
            ?: throw UnknownKomaStrictException("qualifiedName is null")
