package me.tbsten.koma.strict.ksp.core.storeSpec

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.google.devtools.ksp.symbol.KSTypeArgument
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.Variance
import me.tbsten.koma.strict.ksp.codegen.DEFAULT_IMPORT_PACKAGES
import me.tbsten.koma.strict.ksp.core.common.fullName
import me.tbsten.koma.strict.ksp.core.common.underPackageName
import me.tbsten.koma.strict.ksp.model.StateProp
import me.tbsten.koma.strict.ksp.model.TypeRef

// KSP 型 -> StoreSpec model (KSP 非依存) への橋渡し。
// レンダリング済み文字列は「宣言 package 内のソースから参照可能」な形にする
// (生成コードは宣言と同 package に生え、import boilerplate を足せないため)。

/** Converts a KSP class declaration into a KSP-independent [TypeRef]. */
internal fun KSDeclaration.toTypeRef(): TypeRef =
    TypeRef(
        packageName = packageName.asString(),
        underPackageName = underPackageName,
    )

/** Resolves to a class declaration, following typealiases. Null when it does not end at a class (type parameters, etc.). */
internal fun KSType.resolveToClassDeclaration(): KSClassDeclaration? =
    when (val declaration = declaration) {
        is KSClassDeclaration -> declaration
        is KSTypeAlias -> declaration.type.resolve().resolveToClassDeclaration()
        else -> null
    }

/**
 * Rendering of a type as seen from sources in the declaring package ([contextPackage]).
 * Types in the same package / default-import packages are shortened; everything else is
 * fully qualified. Recursively includes generic arguments, nullability, and variance.
 */
internal fun renderSourceType(
    type: KSType,
    contextPackage: String,
    includeNullability: Boolean = true,
): String {
    val base =
        when (val declaration = type.declaration) {
            is KSTypeParameter -> declaration.name.asString()
            else -> renderDeclarationRef(declaration, contextPackage)
        }
    val arguments =
        if (type.arguments.isEmpty()) {
            ""
        } else {
            type.arguments.joinToString(separator = ", ", prefix = "<", postfix = ">") {
                renderTypeArgument(it, contextPackage)
            }
        }
    val nullability = if (includeNullability && type.isMarkedNullable) "?" else ""
    return "$base$arguments$nullability"
}

private fun renderDeclarationRef(
    declaration: KSDeclaration,
    contextPackage: String,
): String {
    val packageName = declaration.packageName.asString()
    return if (packageName == contextPackage || packageName in DEFAULT_IMPORT_PACKAGES) {
        declaration.underPackageName
    } else {
        declaration.fullName
    }
}

private fun renderTypeArgument(
    argument: KSTypeArgument,
    contextPackage: String,
): String {
    if (argument.variance == Variance.STAR) return "*"
    val prefix =
        when (argument.variance) {
            Variance.COVARIANT -> "out "
            Variance.CONTRAVARIANT -> "in "
            else -> ""
        }
    val rendered = argument.type?.resolve()?.let { renderSourceType(it, contextPackage) } ?: "*"
    return "$prefix$rendered"
}

/**
 * The model value for one prop. Per the [StateProp] contract, the type string does not
 * include the outer nullability; it is separated into [StateProp.isNullable].
 */
internal fun buildStateProp(
    name: String,
    type: KSType,
    contextPackage: String,
): StateProp =
    StateProp(
        name = name,
        type = renderSourceType(type, contextPackage, includeNullability = false),
        isNullable = type.isMarkedNullable,
    )
