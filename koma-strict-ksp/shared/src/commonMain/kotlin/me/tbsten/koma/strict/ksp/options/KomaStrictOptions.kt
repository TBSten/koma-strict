package me.tbsten.koma.strict.ksp.options

import me.tbsten.koma.strict.InternalKomaStrictApi
import me.tbsten.koma.strict.ksp.InvalidKomaStrictOptionException
import me.tbsten.koma.strict.ksp.util.lines
import kotlin.reflect.KProperty1

/**
 * KSP の arg (`koma.strict.<プロパティ名>` キー) からパースされる koma-strict のオプション。
 *
 * オプション追加手順 (cream 踏襲):
 * 1. data class に property を追加
 * 2. [KomaStrictOptions.Companion.default] と [KomaStrictOptions.Companion.properties] に追加
 * 3. [toKomaStrictOptions] にパース分岐を追加
 * 4. 消費側 core の context で受ける
 * 5. 診断テストを追加
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

        /** オプション網羅テスト・ドキュメント生成が列挙に使う (cream CreamOptions.properties 対応)。 */
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
