package me.tbsten.koma.strict.ksp.core.common

import com.google.devtools.ksp.symbol.KSClassDeclaration
import me.tbsten.koma.strict.ksp.core.common.storeFactoryFileName as storeFactoryFileNameShared
import me.tbsten.koma.strict.ksp.core.common.storeFactoryFunctionName as storeFactoryFunctionNameShared

/** shared の命名ロジック (KSP 非依存) へ KSP 型を橋渡しする無名 object アダプタ。 */
internal fun KSClassDeclaration.toClassDeclarationInfo(): ClassDeclarationInfo {
    val kspClass = this
    return object : ClassDeclarationInfo {
        override val packageName: String = kspClass.packageName.asString()
        override val underPackageName: String = kspClass.underPackageName
        override val simpleName: String = kspClass.simpleName.asString()
        override val fullName: String = kspClass.fullName
    }
}

internal fun storeFactoryFunctionName(source: KSClassDeclaration): String =
    storeFactoryFunctionNameShared(source.toClassDeclarationInfo())

internal fun storeFactoryFileName(source: KSClassDeclaration): String =
    storeFactoryFileNameShared(source.toClassDeclarationInfo())
