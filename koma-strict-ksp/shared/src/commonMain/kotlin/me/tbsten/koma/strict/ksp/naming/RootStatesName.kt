package me.tbsten.koma.strict.ksp.naming

import me.tbsten.koma.strict.InternalKomaStrictApi
import me.tbsten.koma.strict.ksp.model.RootNode

/**
 * `@JvmName` of the root `states()` extension (`{decapitalized root}States`, e.g. `myStateStates`).
 * Avoids JVM signature clashes between same-named `states()` functions (the receiver
 * `StoreBuilder<S, A, E>` erases to `StoreBuilder`, so e.g. two zero-param `states()`
 * in one package would clash) and keeps the JVM name stable.
 *
 * Nested same-simpleName collisions within a package (`FooScreen.State` / `BarScreen.State`)
 * are avoided by concatenating the underPackageName (`fooScreenStateStates`)
 * (same approach as StoreFactoryName.kt).
 */
@InternalKomaStrictApi
public fun rootStatesJvmName(root: RootNode): String = rootTypePrefix(root).decapitalized() + "States"
