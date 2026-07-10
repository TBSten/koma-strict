package me.tbsten.koma.strict.ksp.core

import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import io.kotest.core.spec.style.FreeSpec
import me.tbsten.koma.strict.ksp.testing.konsist.COMPOSITION_ROOT_TYPES
import me.tbsten.koma.strict.ksp.testing.konsist.CORE_PACKAGE
import me.tbsten.koma.strict.ksp.testing.konsist.FEATURE_PACKAGE
import me.tbsten.koma.strict.ksp.testing.konsist.KOMA_STRICT_ROOT
import me.tbsten.koma.strict.ksp.testing.konsist.KSP_API_PACKAGE
import me.tbsten.koma.strict.ksp.testing.konsist.PROCESS_CONTEXT_TYPE
import me.tbsten.koma.strict.ksp.testing.konsist.UTIL_PACKAGE
import me.tbsten.koma.strict.ksp.testing.konsist.importsFrom
import me.tbsten.koma.strict.ksp.testing.konsist.inLayer
import me.tbsten.koma.strict.ksp.testing.konsist.komaStrictKspMain

/**
 * core / util レイヤの architecture テスト — `feature → core → util` 方向の下半分:
 *
 * - `core` — koma-strict 固有の生成ロジック。`util` に依存してよいが、`feature` と root infra
 *   (`ProcessContext` / `KomaStrictSymbolProcessor`) には依存してはならない — 必要な capability
 *   だけを context parameters で受ける。
 * - `util` — 汎用 helper。`util` 直下は Kotlin-only (KSP API 禁止)、KSP 依存 helper は
 *   `util.ksp` に置く。いずれも `core` / `feature` と koma-strict 固有型を参照しない。
 *
 * feature レイヤ・モジュール共通ルールは [me.tbsten.koma.strict.ksp.feature.ArchTest] と
 * [me.tbsten.koma.strict.ksp.AllKotlinFilesTest] にある。
 */
internal class ArchTest :
    FreeSpec({
        "core レイヤ" - {
            "feature レイヤに依存しない" {
                komaStrictKspMain
                    .filter { it.inLayer(CORE_PACKAGE) }
                    .assertFalse { file -> file.importsFrom("$FEATURE_PACKAGE.") }
            }

            "root infra（ProcessContext / KomaStrictSymbolProcessor / Provider）に依存しない" {
                // core は ProcessContext 全体ではなく絞った context (options, logger) を受け、
                // composition root へ手を戻さない。
                komaStrictKspMain
                    .filter { it.inLayer(CORE_PACKAGE) }
                    .assertFalse { file ->
                        file.importsFrom(PROCESS_CONTEXT_TYPE, *COMPOSITION_ROOT_TYPES)
                    }
            }

            "core/ 直下に .kt を置かない（必ず core.<sub> サブパッケージに置く）" {
                // TODO: core のサブパッケージ構成が固まったら cream 同様の固定許可 set
                //   (CORE_SUBPACKAGES) に強化する
                komaStrictKspMain
                    .filter { it.inLayer(CORE_PACKAGE) }
                    .assertTrue { file -> file.packagee?.name.orEmpty().startsWith("$CORE_PACKAGE.") }
            }
        }

        "util レイヤ" - {
            "core / feature レイヤに依存しない" {
                komaStrictKspMain
                    .filter { it.inLayer(UTIL_PACKAGE) }
                    .assertFalse { file -> file.importsFrom("$CORE_PACKAGE.", "$FEATURE_PACKAGE.") }
            }

            "koma-strict 固有の型を参照しない（自分自身の util パッケージのみ許可）" {
                // util のファイルは util 自身の下にある koma-strict パッケージだけ参照できる。それ以外の
                // `me.tbsten.koma.strict.*` import (core / feature / ProcessContext / options / runtime
                // annotation, ...) があると koma-strict 固有になり、汎用 helper ではなくなる。
                komaStrictKspMain
                    .filter { it.inLayer(UTIL_PACKAGE) }
                    .assertFalse { file ->
                        file.imports.any { import ->
                            import.name.startsWith("$KOMA_STRICT_ROOT.") && !import.name.startsWith("$UTIL_PACKAGE.")
                        }
                    }
            }

            "直下（util.ksp を除く）は KSP 型に依存しない（KSP util は util/ksp に置く）" {
                // KSP API に触る helper は `util.ksp` に置く。トップレベルの `util` は Kotlin-only に
                // 保ち、非 KSP 文脈でも再利用できるようにする。
                komaStrictKspMain
                    .filter { it.packagee?.name == UTIL_PACKAGE }
                    .assertFalse { file -> file.importsFrom("$KSP_API_PACKAGE.") }
            }
        }
    })
