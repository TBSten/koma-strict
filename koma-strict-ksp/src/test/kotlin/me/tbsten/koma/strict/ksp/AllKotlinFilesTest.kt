package me.tbsten.koma.strict.ksp

import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import io.kotest.core.spec.style.FreeSpec
import me.tbsten.koma.strict.ksp.testing.konsist.FILE_LINE_LIMIT_OVERRIDES
import me.tbsten.koma.strict.ksp.testing.konsist.KSP_ROOT
import me.tbsten.koma.strict.ksp.testing.konsist.MAX_FILE_LINES
import me.tbsten.koma.strict.ksp.testing.konsist.ROOT_ALLOWED_FILES
import me.tbsten.koma.strict.ksp.testing.konsist.komaStrictKspMain

/**
 * koma-strict-ksp の全 production ファイルにレイヤを問わず適用するモジュール共通の
 * Konsist ガードレール:
 *
 * - composition root (`me.tbsten.koma.strict.ksp` 直下) には承認済み infra ファイルのみを置く
 * - 全ファイルが行数上限内に収まる (デフォルト 300、個別 override 可)
 *
 * レイヤ別ルールは各レイヤの隣に置く ([me.tbsten.koma.strict.ksp.feature.ArchTest] /
 * [me.tbsten.koma.strict.ksp.core.ArchTest])。検査は import ベース
 * (wildcard や inline FQ 参照ではなく import する、というプロジェクト規約に一致)。
 */
internal class AllKotlinFilesTest :
    FreeSpec(
        {
            "root レイヤ（composition root）" - {
                "直下には承認済みファイル（KomaStrictSymbolProcessor / Provider / ProcessContext）以外を置かない" {
                    // root パッケージは composition root のみ。生成ロジック・helper・例外
                    // (これらは :koma-strict-ksp:shared にある) をここへ追加してはならない。
                    komaStrictKspMain
                        .filter { it.packagee?.name == KSP_ROOT }
                        .assertTrue { file -> file.nameWithExtension in ROOT_ALLOWED_FILES }
                }
            }

            "ファイル全般（モジュール共通）" - {
                "1 ファイル原則 $MAX_FILE_LINES 行以内（FILE_LINE_LIMIT_OVERRIDES のファイルは個別上限）" {
                    komaStrictKspMain.assertFalse { file ->
                        val limit = FILE_LINE_LIMIT_OVERRIDES[file.nameWithExtension] ?: MAX_FILE_LINES
                        file.text.lines().size > limit
                    }
                }
            }
        },
    )
