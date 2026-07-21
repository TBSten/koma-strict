@file:OptIn(ExperimentalCompilerApi::class)

package me.tbsten.koma.strict.ksp.testing.compile

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.kspProcessorOptions
import com.tschuchort.compiletesting.symbolProcessorProviders
import com.tschuchort.compiletesting.useKsp2
import me.tbsten.koma.strict.StoreSpec
import me.tbsten.koma.strict.ksp.KomaStrictSymbolProcessorProvider
import me.tbsten.koma.strict.ksp.testing.fixtures.KOMA_API_STUB_FILE_NAME
import me.tbsten.koma.strict.ksp.testing.fixtures.komaApiStubCode
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream

/**
 * DSL receiver for collecting source files. Use [source] from a [String] receiver
 * (file name) to add a Kotlin source. See the multi-source overload of
 * [compileWithKomaStrict].
 */
internal class KomaStrictSourcesBuilder {
    private val sources = mutableListOf<SourceFile>()

    infix fun String.source(
        @Language("kotlin") code: String,
    ) {
        sources += SourceFile.kotlin(this, code)
    }

    internal fun build(): List<SourceFile> = sources.toList()
}

/**
 * Multi-source overload. Use when a test needs more than one input file:
 *
 * ```kt
 * compileWithKomaStrict(options = mapOf(...)) {
 *     "State.kt" source """
 *         @StrictStore
 *         sealed interface MyState { ... }
 *     """.trimIndent()
 *     "Other.kt" source """
 *         class Other(...)
 *     """.trimIndent()
 * }
 * ```
 */
internal fun compileWithKomaStrict(
    options: Map<String, String> = emptyMap(),
    allWarningsAsErrors: Boolean = true,
    block: KomaStrictSourcesBuilder.() -> Unit,
): KomaStrictCompilationResult {
    val sources = KomaStrictSourcesBuilder().apply(block).build()
    return runCompilation(sources, options, allWarningsAsErrors)
}

/** Single-source convenience overload. */
internal fun compileWithKomaStrict(
    @Language("kotlin") source: String,
    options: Map<String, String> = emptyMap(),
    sourceFileName: String = "Test.kt",
    allWarningsAsErrors: Boolean = true,
): KomaStrictCompilationResult =
    compileWithKomaStrict(options = options, allWarningsAsErrors = allWarningsAsErrors) {
        sourceFileName source source
    }

private fun runCompilation(
    sources: List<SourceFile>,
    options: Map<String, String>,
    allWarningsAsErrors: Boolean,
): KomaStrictCompilationResult {
    val captured = ByteArrayOutputStream()
    val tee = TeeOutputStream(System.out, captured)
    val compilation =
        KotlinCompilation().apply {
            // 性能: classpath を runtime + stdlib に絞る (cream issue #155 に倣う)
            inheritClassPath = false
            classpaths = komaStrictCompilationClasspath
            useKsp2()
            symbolProcessorProviders += KomaStrictSymbolProcessorProvider()
            if (options.isNotEmpty()) {
                kspProcessorOptions = options.toMutableMap()
            }
            // 入力が参照する koma API (koma.core.*) は hermetic なスタブとして常に一緒にコンパイルする
            // (rc02 実物 sources と突き合わせ済み。詳細は testing/fixtures/KomaApiStub.kt)
            this.sources = sources + SourceFile.kotlin(KOMA_API_STUB_FILE_NAME, komaApiStubCode)
            messageOutputStream = tee
            this.allWarningsAsErrors = allWarningsAsErrors
        }
    return KomaStrictCompilationResult(
        raw = compilation.compile(),
        compilation = compilation,
        compilerOutputBuffer = captured,
    )
}

private val komaStrictCompilationClasspath: List<File> =
    listOf(
        // classpath root の取得は StoreSpec で行う (annotation class は class load しても安全)。
        // Stay::class は使わない: Stay : koma.core.State (JVM 21 bytecode) のため、
        // JDK 17 の test JVM で class load すると UnsupportedClassVersionError になる
        StoreSpec::class.java, // koma-strict-runtime (宣言 API annotation + Stay マーカー)
        Unit::class.java, // kotlin-stdlib
        // koma-core の実物 jar は意図的に足さない (hermetic 維持)。runtime jar 内の
        // `Stay : koma.core.State` の supertype は同時コンパイルされるスタブ source
        // (fixtures/KomaApiStub.kt の koma.core.State) が FQN 一致で解決する
    ).map { it.classpathRoot() }.distinct()

private fun Class<*>.classpathRoot(): File {
    val location =
        checkNotNull(protectionDomain?.codeSource?.location) {
            "Cannot locate the classpath root for $name (codeSource is null)."
        }
    return File(location.toURI())
}

private class TeeOutputStream(
    private val a: OutputStream,
    private val b: OutputStream,
) : OutputStream() {
    override fun write(byte: Int) {
        a.write(byte)
        b.write(byte)
    }

    override fun write(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ) {
        a.write(buffer, offset, length)
        b.write(buffer, offset, length)
    }

    override fun flush() {
        a.flush()
        b.flush()
    }
}
