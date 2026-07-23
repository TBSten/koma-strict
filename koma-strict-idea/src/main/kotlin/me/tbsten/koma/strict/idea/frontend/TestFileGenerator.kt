package me.tbsten.koma.strict.idea.frontend

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.SourceFolder
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.jps.model.java.JavaSourceRootProperties
import org.jetbrains.kotlin.psi.KtClassOrObject

/**
 * Writes a generated test file into the **host unit-test** source set matching the `@StoreSpec` root's
 * location (`ide-test-code.md`). The store's source set maps to its unit-test set — `commonMain`→
 * `commonTest`, `jvmMain`→`jvmTest`, `main`→`test`/`unitTest`, and crucially `androidMain`→`androidUnitTest`
 * (the *host* set), **never** the instrumented `androidTest`. Only test modules of the **same Gradle
 * project** (matching module-name prefix) are considered, so the write never lands in another build; among
 * them an exact target match wins, then shared `commonTest`. Instrumented sets, per-variant aggregations
 * (`debugUnitTest`, `releaseUnitTest`), and **generated roots under `build/`** are never targeted. The file
 * goes under the root's package and is overwritten if it already exists (the whole write is one undoable action).
 */
object TestFileGenerator {

    /**
     * The would-be target file **if it already exists**, so the caller can confirm an overwrite before
     * [generate] clobbers it; null when the destination is free (or no test source root resolves). Reads
     * VFS / module roots, so call inside a read action.
     */
    fun existingTestFile(project: Project, stateRoot: KtClassOrObject, packageName: String, fileName: String): VirtualFile? {
        val testRoot = findTestSourceRoot(project, stateRoot) ?: return null
        val relative = if (packageName.isBlank()) fileName else packageName.replace('.', '/') + "/" + fileName
        return testRoot.findFileByRelativePath(relative)
    }

    /** Creates / overwrites the test file and returns it (to open), or null if no test source root was found. */
    fun generate(project: Project, stateRoot: KtClassOrObject, packageName: String, fileName: String, content: String): VirtualFile? {
        var result: VirtualFile? = null
        WriteCommandAction.runWriteCommandAction(project, "Generate Test File", null, {
            val testRoot = findTestSourceRoot(project, stateRoot) ?: return@runWriteCommandAction
            val packagePath = packageName.replace('.', '/')
            val dir = if (packagePath.isBlank()) testRoot else VfsUtil.createDirectoryIfMissing(testRoot, packagePath)
            val file = dir.findChild(fileName) ?: dir.createChildData(this, fileName)
            VfsUtil.saveText(file, content)
            result = file
        })
        return result
    }

    private fun findTestSourceRoot(project: Project, stateRoot: KtClassOrObject): VirtualFile? {
        val vfile = stateRoot.containingFile.virtualFile ?: return null
        val module = ModuleUtilCore.findModuleForFile(vfile, project) ?: return null
        val modules = ModuleManager.getInstance(project).modules

        // store が属する source set (module 名末尾) から、対応する host unit test の source set 名を優先順で導く。
        // androidMain は instrumented の androidTest ではなく host の androidUnitTest 系を狙う (バグ原因②の片方)。
        val preferred = preferredTestSegments(module.name.substringAfterLast('.'))

        // 同じ Gradle プロジェクト (source set を除いた module 名 prefix が完全一致) の test module だけを候補にし、
        // 別ビルド (koma-strict-idea 等) へ飛ばないようにする。instrumented / variant 固有 (debugUnitTest 等) は
        // isUnitTestSegment で除外し、preferred 完全一致 > commonTest > その他 の順で最良の1つを選ぶ。
        val prefix = module.name.substringBeforeLast('.', "")
        if (prefix.isNotEmpty()) {
            modules
                .map { it to it.name.substringAfterLast('.') }
                .filter { (m, segment) -> m.name.startsWith("$prefix.") && isUnitTestSegment(segment) }
                .sortedByDescending { (_, segment) -> testSegmentRank(segment, preferred) }
                .firstNotNullOfOrNull { (m, _) -> testKotlinSourceRoot(m) }
                ?.let { return it }
        }
        // 非 KMP レイアウトのフォールバック: 同 module 内の test source root (生成物除外)。
        return sameModuleTestSourceRoot(module)
    }

    /**
     * Host **unit** test source-set segments to target for a store living in [mainSegment], most-preferred
     * first: `main`→`test`/`unitTest`, `commonMain`→`commonTest`, `jvmMain`→`jvmTest`, and — crucially —
     * `androidMain`→`androidUnitTest`/`androidHostTest` (the host set), **never** `androidTest` (instrumented).
     * Empty when [mainSegment] is not a `main` source set (the caller then just ranks by `commonTest`).
     */
    internal fun preferredTestSegments(mainSegment: String): List<String> = when {
        mainSegment == "main" -> listOf("test", "unitTest")
        mainSegment == "androidMain" -> listOf("androidUnitTest", "androidHostTest", "unitTest")
        mainSegment.endsWith("Main") -> listOf(mainSegment.removeSuffix("Main") + "Test")
        else -> emptyList()
    }

    /**
     * True when [segment] is a host **unit** test source set we may write into. Excludes non-test sets,
     * instrumented / device sets (`androidTest`, `androidInstrumentedTest`, `androidDeviceTest`, …), and
     * per-variant aggregations carrying `debug`/`release` (`debugUnitTest`, `testReleaseUnitTest`, …) — those
     * overlay/duplicate real sources and are exactly what dragged writes into a wrong or generated dir.
     */
    internal fun isUnitTestSegment(segment: String): Boolean {
        if (segment != "test" && !segment.endsWith("Test")) return false
        if (isInstrumentedTestSegment(segment)) return false
        val lower = segment.lowercase()
        if ("debug" in lower || "release" in lower) return false
        return true
    }

    private fun isInstrumentedTestSegment(segment: String): Boolean =
        segment == "androidTest" ||
            segment == "androidInstrumentedTest" ||
            segment == "androidDeviceTest" ||
            segment.endsWith("AndroidTest") ||
            segment.endsWith("InstrumentedTest") ||
            segment.endsWith("DeviceTest")

    /** Rank for [segment]; higher wins. [preferred] exact match first (in its own order), then shared `commonTest`. */
    internal fun testSegmentRank(segment: String, preferred: List<String>): Int {
        val idx = preferred.indexOf(segment)
        return when {
            idx >= 0 -> 100 - idx
            segment == "commonTest" -> 50
            else -> 10
        }
    }

    /** First non-generated Kotlin source root of a resolved `*Test` [module] (generated / `build` roots dropped). */
    private fun testKotlinSourceRoot(module: Module): VirtualFile? = usableSourceRoot(module, requireTestSource = false)

    /** Same-module fallback (non-KMP layout): first non-generated TEST source root. */
    private fun sameModuleTestSourceRoot(module: Module): VirtualFile? = usableSourceRoot(module, requireTestSource = true)

    // 生成物 (KSP 等の build/generated/**/kotlin) は絶対に宛先にしない。JPS の generated フラグと path の
    // /build/ セグメントの二重で弾き、残った src ソースから kotlin ディレクトリを優先して1つ選ぶ。
    @Suppress("DEPRECATION")
    private fun usableSourceRoot(module: Module, requireTestSource: Boolean): VirtualFile? {
        val roots = ModuleRootManager.getInstance(module).contentEntries
            .flatMap { it.sourceFolders.toList() }
            .filter { !requireTestSource || it.isTestSource }
            .filterNot { it.isForGeneratedSources() }
            .mapNotNull { it.file }
            .filterNot { "/build/" in it.path }
        return roots.firstOrNull { it.path.endsWith("kotlin") } ?: roots.firstOrNull()
    }

    /** JPS generated-source flag for [this] source folder (false when the root type carries no such property). */
    private fun SourceFolder.isForGeneratedSources(): Boolean =
        (jpsElement.properties as? JavaSourceRootProperties)?.isForGeneratedSources == true
}
