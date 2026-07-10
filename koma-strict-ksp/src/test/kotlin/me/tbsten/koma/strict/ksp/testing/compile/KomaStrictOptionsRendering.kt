package me.tbsten.koma.strict.ksp.testing.compile

import me.tbsten.koma.strict.ksp.options.KomaStrictOptions

/**
 * [KomaStrictOptions] を KSP arg (`koma.strict.<プロパティ名>` キー) Map に変換する。
 * [KomaStrictOptions.Companion.properties] を回すので option 追加に自動追従する。
 * キー prefix の SSoT は shared 側の `toKomaStrictOptions()`。
 */
internal fun KomaStrictOptions.toKspArgs(): Map<String, String> =
    KomaStrictOptions.properties.associate { property ->
        val value = property.get(this)
        "koma.strict.${property.name}" to if (value is Enum<*>) value.name else value.toString()
    }

/** golden の `KSP options` facet 用。default と同値の option には ` /* default */` を付ける (cream 同様)。 */
internal fun KomaStrictOptions.toKspConfigString(): String =
    buildString {
        appendLine("ksp {")
        KomaStrictOptions.properties.forEach { property ->
            val value = property.get(this@toKspConfigString)
            val rendered = if (value is Enum<*>) value.name else value.toString()
            val defaultMark = if (value == property.get(KomaStrictOptions.default)) " /* default */" else ""
            appendLine("    arg(\"koma.strict.${property.name}\", \"$rendered\"$defaultMark)")
        }
        append("}")
    }
