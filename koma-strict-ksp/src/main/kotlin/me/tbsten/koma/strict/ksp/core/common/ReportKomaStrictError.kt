package me.tbsten.koma.strict.ksp.core.common

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSNode
import me.tbsten.koma.strict.ksp.InvalidKomaStrictUsageException
import me.tbsten.koma.strict.ksp.KomaStrictException

/**
 * ユーザー誤用 [KomaStrictException] を positioned な COMPILATION_ERROR として報告する funnel。
 * raw throw は KSP に INTERNAL_ERROR ("processor crashed" + stack trace + 半端な生成ファイル)
 * として扱われるため禁止。呼び出し側はこの直後に必ず処理を打ち切ること (return@forEach 等)。
 * [ksNode] は診断を該当宣言に position する (option エラー等ソース位置が無い場合のみ null)。
 */
internal fun KSPLogger.reportKomaStrictError(
    exception: KomaStrictException,
    ksNode: KSNode?,
) {
    error(exception.message.orEmpty(), ksNode)
}

/**
 * [KSClassDeclaration] への safe cast。失敗時は clean error を報告済みで null を返す。
 * 呼び出し側は null で unit の処理を止めること (`?: return@forEach`)。
 */
context(logger: KSPLogger)
internal fun KSAnnotated.asClassDeclarationOrReport(annotationSimpleName: String): KSClassDeclaration? =
    this as? KSClassDeclaration
        ?: run {
            logger.reportKomaStrictError(
                InvalidKomaStrictUsageException(
                    message = "@$annotationSimpleName must be applied to a class.",
                    solution = "Please apply @$annotationSimpleName to a `class`, `object`, or `interface`",
                ),
                this,
            )
            null
        }

// TODO typealias 対象の注釈を追加したら cream の asDeclarationOrReport
//   (cream-ksp/.../core/common/ReportCreamError.kt) も移植する。
