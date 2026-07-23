package me.tbsten.koma.strict.idea.frontend

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.psi.KtClassOrObject

/**
 * Writes a generated test file into the test source set matching the `@StoreSpec` root's location
 * (`ide-test-code.md`): the root's module name has its `main` source-set suffix swapped for `test`
 * (`commonMain`→`commonTest`, `jvmMain`→`jvmTest`, `main`→`test`); if that module isn't found it falls
 * back to a test source root in the same module, then to any `*Test` module. The file goes under the
 * root's package and is overwritten if it already exists (the whole write is one undoable action).
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
        val vfile = stateRoot.containingFile?.virtualFile ?: return null
        val module = ModuleUtilCore.findModuleForFile(vfile, project) ?: return null
        val modules = ModuleManager.getInstance(project).modules
        // 1. module 名の Main -> Test 変換 (commonMain -> commonTest 等) で対応 test module を探す。
        deriveTestModuleName(module.name)?.let { testName ->
            modules.find { it.name == testName }?.let { testModule ->
                kotlinSourceRoot(testModule)?.let { return it }
            }
        }
        // 2. 同じ Gradle プロジェクト (同 prefix) の test module。KMP で 1 の完全一致に失敗した時の保険。
        //    prefix を必ず一致させ、別ビルド (koma-strict-idea 等) の test module へ飛ばないようにする
        //    (これが今回のバグ原因: プロジェクト全体の *Test を拾って別ビルドへ書いていた)。commonTest を優先。
        val prefix = module.name.substringBeforeLast('.', "")
        if (prefix.isNotEmpty()) {
            modules
                .filter { it.name.startsWith("$prefix.") && isTestModuleName(it.name) }
                .sortedByDescending { it.name.substringAfterLast('.') == "commonTest" }
                .firstNotNullOfOrNull { kotlinSourceRoot(it) }
                ?.let { return it }
        }
        // 3. 同じ module 内の test source root (非 KMP レイアウト)。
        return testSourceRoot(module)
    }

    /** True when [name]'s last source-set segment is a test one (`test` / `commonTest` / `jvmTest` / …). */
    private fun isTestModuleName(name: String): Boolean {
        val segment = name.substringAfterLast('.')
        return segment == "test" || segment.endsWith("Test")
    }

    /**
     * Derives the test module name from a source-set module name: `…​.main`→`…​.test`,
     * `…​.commonMain`→`…​.commonTest`, `…​.jvmMain`→`…​.jvmTest`. Null when there is no `main`/`…Main` segment.
     */
    internal fun deriveTestModuleName(moduleName: String): String? {
        val dot = moduleName.lastIndexOf('.')
        val prefix = if (dot >= 0) moduleName.substring(0, dot + 1) else ""
        val sourceSet = if (dot >= 0) moduleName.substring(dot + 1) else moduleName
        val testSourceSet = when {
            sourceSet == "main" -> "test"
            sourceSet.endsWith("Main") -> sourceSet.removeSuffix("Main") + "Test"
            else -> return null
        }
        return prefix + testSourceSet
    }

    /** First `.../kotlin` source root of [module] (a `*Test` module's roots are all test roots). */
    private fun kotlinSourceRoot(module: Module): VirtualFile? {
        val roots = ModuleRootManager.getInstance(module).sourceRoots
        return roots.firstOrNull { it.path.endsWith("kotlin") } ?: roots.firstOrNull()
    }

    /** First TEST source root of [module] (same-module fallback for non-KMP layouts). */
    @Suppress("DEPRECATION")
    private fun testSourceRoot(module: Module): VirtualFile? {
        val testRoots = ModuleRootManager.getInstance(module).contentEntries
            .flatMap { it.sourceFolders.toList() }
            .filter { it.isTestSource }
            .mapNotNull { it.file }
        return testRoots.firstOrNull { it.path.endsWith("kotlin") } ?: testRoots.firstOrNull()
    }
}
