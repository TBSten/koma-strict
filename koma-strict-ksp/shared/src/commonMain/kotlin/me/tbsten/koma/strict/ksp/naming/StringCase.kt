package me.tbsten.koma.strict.ksp.naming

// 命名規約の大文字小文字変換。先頭 1 文字のみ変換し acronym は考慮しない
// (既存 StoreFactoryName.kt の replaceFirstChar と同じ方針)。

internal fun String.decapitalized(): String = replaceFirstChar { it.lowercaseChar() }

internal fun String.capitalized(): String = replaceFirstChar { it.uppercaseChar() }
