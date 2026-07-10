package me.tbsten.koma.strict.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import me.tbsten.koma.strict.ksp.options.KomaStrictOptions

/**
 * 全 feature 入口が共有する per-round の処理基盤。
 *
 * [logger] は non-null: KSP environment から常に供給されるため、生成系深部へ nullable を伝搬させない。
 * これは leaf infrastructure であり、feature / core に依存してはならない
 * (依存方向 root -> feature -> core -> util の唯一の上向き例外が feature -> ProcessContext)。
 * core 層は ProcessContext 全体ではなく、必要な capability (options / logger など) だけを
 * context parameters で受ける。
 */
internal class ProcessContext(
    val resolver: Resolver,
    val options: KomaStrictOptions,
    val codeGenerator: CodeGenerator,
    val logger: KSPLogger,
)
