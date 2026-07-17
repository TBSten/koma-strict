package me.tbsten.koma.strict.ksp.options

import me.tbsten.koma.strict.InternalKomaStrictApi
import me.tbsten.koma.strict.ksp.InvalidKomaStrictOptionException
import me.tbsten.koma.strict.ksp.util.lines
import kotlin.reflect.KProperty1

/**
 * koma-strict options parsed from KSP args (`koma.strict.<property name>` keys).
 *
 * Steps to add an option (following the cream idiom):
 * 1. Add a property to the data class
 * 2. Add it to [KomaStrictOptions.Companion.default] and [KomaStrictOptions.Companion.properties]
 * 3. Add a parsing branch to [toKomaStrictOptions]
 * 4. Receive it in the consuming core's context
 * 5. Add a diagnostic test
 */
// TODO cream の CreamOptions は optionBuilder (GUI) 用に @Serializable だった。
//   koma-strict に optionBuilder 相当を作るときに kotlinx-serialization を導入して付け直す。
@InternalKomaStrictApi
public data class KomaStrictOptions(
    val deadActionSeverity: DeadActionSeverity,
) {
    public companion object {
        public val default: KomaStrictOptions =
            KomaStrictOptions(
                deadActionSeverity = DeadActionSeverity.default,
            )

        /** Used for enumeration by option-coverage tests and doc generation (counterpart of cream's CreamOptions.properties). */
        public val properties: List<KProperty1<KomaStrictOptions, *>> =
            listOf(
                KomaStrictOptions::deadActionSeverity,
            )
    }
}

@InternalKomaStrictApi
public fun Map<String, String>.toKomaStrictOptions(): KomaStrictOptions =
    KomaStrictOptions(
        deadActionSeverity =
            this["koma.strict.deadActionSeverity"]?.let { rawValue ->
                try {
                    // enum 名は大文字小文字を許容する (cream の defaultVisibility と同じ方針)
                    DeadActionSeverity.valueOf(rawValue.uppercase())
                } catch (e: IllegalArgumentException) {
                    invalidDeadActionSeverityError(actualValue = rawValue, cause = e)
                }
            } ?: KomaStrictOptions.default.deadActionSeverity,
    )

@Suppress("NOTHING_TO_INLINE")
private inline fun invalidDeadActionSeverityError(
    actualValue: String?,
    cause: IllegalArgumentException,
): Nothing =
    throw InvalidKomaStrictOptionException(
        message =
            lines(
                "Invalid ksp.arg[\"koma.strict.deadActionSeverity\"] = $actualValue.",
                "It must be one of ${DeadActionSeverity.entries.joinToString(", ") { it.name }}",
            ),
        solution =
            lines(
                "Set one of the following for ksp.arg:",
                "",
                *DeadActionSeverity.entries
                    .map { "  - \"${it.name}\"" }
                    .toTypedArray(),
                "",
            ),
        cause = cause,
    )
