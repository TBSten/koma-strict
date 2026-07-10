@file:OptIn(ExperimentalCompilerApi::class)

package me.tbsten.koma.strict.ksp.testing.compile

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.sourcesGeneratedBySymbolProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import java.io.ByteArrayOutputStream
import java.io.File

internal data class KomaStrictCompilationResult(
    private val raw: JvmCompilationResult,
    val compilation: KotlinCompilation,
    private val compilerOutputBuffer: ByteArrayOutputStream,
) {
    val exitCode: KotlinCompilation.ExitCode get() = raw.exitCode
    val messages: String get() = raw.messages

    /**
     * `messageOutputStream` に出た全出力 (Tee 捕捉)。
     * snapshot には [normalizedCompilerOutput] を使う (raw には絶対パスが混ざるため)。
     */
    val compilerOutput: String get() = compilerOutputBuffer.toString(Charsets.UTF_8)

    fun generatedSources(): List<File> = raw.sourcesGeneratedBySymbolProcessor.toList()

    fun loadGeneratedClass(fqName: String): Class<*> = raw.classLoader.loadClass(fqName)
}
