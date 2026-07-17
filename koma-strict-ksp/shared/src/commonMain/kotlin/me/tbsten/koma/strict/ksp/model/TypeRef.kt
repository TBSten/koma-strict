package me.tbsten.koma.strict.ksp.model

import me.tbsten.koma.strict.InternalKomaStrictApi

/**
 * KSP-independent type reference. The StoreSpec model never references KSP types;
 * it holds types as this pair of qualified-name strings
 * (the layering in .local/design/migration-analysis-api.md).
 *
 * [packageName] and [underPackageName] are held separately because a qualified-name
 * string alone cannot distinguish the package boundary from nested-class boundaries
 * (the `Outer.Inner` part of `foo.Outer.Inner`).
 */
@InternalKomaStrictApi
public data class TypeRef(
    /** Package name. The default package is the empty string. */
    val packageName: String,
    /** Qualified name without the package (`MyAction.Logout` / `FooScreen.State` form). */
    val underPackageName: String,
) {
    /** The last segment (`MyAction.Logout` -> `Logout`). */
    val simpleName: String
        get() = underPackageName.substringAfterLast(".")

    /** Fully qualified name (`example.MyAction.Logout`). In the default package it equals [underPackageName]. */
    val qualifiedName: String
        get() = if (packageName.isEmpty()) underPackageName else "$packageName.$underPackageName"
}
