package me.tbsten.koma.strict.ksp.testing.konsist

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration

/**
 * architecture テスト群が共有する Konsist scope とレイヤ helper。
 * `feature → core → util` の依存方向ルールは 3 つの spec に分割してあり、違反がどのレイヤで
 * 起きたかへ直接ポイントする:
 *
 * - [me.tbsten.koma.strict.ksp.AllKotlinFilesTest] — モジュール共通ルール (root パッケージの
 *   許可リスト、ファイル行数上限)。
 * - [me.tbsten.koma.strict.ksp.feature.ArchTest] — feature レイヤのルール。
 * - [me.tbsten.koma.strict.ksp.core.ArchTest] — core / util レイヤのルール。
 */

internal const val KOMA_STRICT_ROOT = "me.tbsten.koma.strict"
internal const val KSP_ROOT = "$KOMA_STRICT_ROOT.ksp"
internal const val UTIL_PACKAGE = "$KSP_ROOT.util"
internal const val CORE_PACKAGE = "$KSP_ROOT.core"
internal const val FEATURE_PACKAGE = "$KSP_ROOT.feature"
internal const val KSP_API_PACKAGE = "com.google.devtools.ksp"
internal const val PROCESS_CONTEXT_TYPE = "$KSP_ROOT.ProcessContext"
internal const val MAX_FILE_LINES = 300

/**
 * root の `me.tbsten.koma.strict.ksp` パッケージ直下に定義される composition-root infra 型。
 * `core` / `feature` はこれらを import してはならない (`feature` は [PROCESS_CONTEXT_TYPE] のみ可)。
 */
internal val COMPOSITION_ROOT_TYPES =
    arrayOf(
        "$KSP_ROOT.KomaStrictSymbolProcessor",
        "$KSP_ROOT.KomaStrictSymbolProcessorProvider",
    )

/**
 * [MAX_FILE_LINES] の per-file override (キーはファイル名)。ここに載ったファイルはデフォルト 300
 * ではなく個別上限まで許容される。escape hatch なので、正当な理由がある時だけ追加し小さく保つ
 * (cream は FindMatchedProperty.kt を 500 に緩和していた)。
 */
internal val FILE_LINE_LIMIT_OVERRIDES = emptyMap<String, Int>()

/** root の `me.tbsten.koma.strict.ksp` パッケージ直下 (composition root) に置いてよい唯一のファイル群。 */
internal val ROOT_ALLOWED_FILES =
    setOf(
        "KomaStrictSymbolProcessor.kt",
        "KomaStrictSymbolProcessorProvider.kt",
        "ProcessContext.kt",
    )

/**
 * koma-strict-ksp の production (`main`) source set のみ — test source set と nested
 * `:koma-strict-ksp:shared` モジュールは含まれない。lazy 単回パースで全 spec が共有する。
 */
internal val komaStrictKspMain: List<KoFileDeclaration> by lazy {
    Konsist.scopeFromProduction(moduleName = "koma-strict-ksp", sourceSetName = "main").files
}

/** このファイルの package が [layerPackage] またはそのサブパッケージなら true。 */
internal fun KoFileDeclaration.inLayer(layerPackage: String): Boolean {
    val packageName = packagee?.name ?: return false
    return packageName == layerPackage || packageName.startsWith("$layerPackage.")
}

/** このファイルが [importPrefixes] のいずれかで始まる FQName を import していれば true。 */
internal fun KoFileDeclaration.importsFrom(vararg importPrefixes: String): Boolean =
    imports.any { import ->
        importPrefixes.any { prefix -> import.name.startsWith(prefix) }
    }
