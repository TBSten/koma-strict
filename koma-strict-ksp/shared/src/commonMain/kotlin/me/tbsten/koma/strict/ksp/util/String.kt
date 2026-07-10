package me.tbsten.koma.strict.ksp.util

import me.tbsten.koma.strict.InternalKomaStrictApi

@InternalKomaStrictApi
public fun lines(vararg lines: String, indent: String = ""): String =
    lines.joinToString("\n") { "$indent$it" }

@InternalKomaStrictApi
public fun StringBuilder.appendLines(vararg lines: String, indent: String = ""): String {
    val lines = lines(*lines, indent = indent)
    append(lines)
    return lines
}
