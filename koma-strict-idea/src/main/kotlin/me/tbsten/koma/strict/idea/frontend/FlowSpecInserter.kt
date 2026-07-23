package me.tbsten.koma.strict.idea.frontend

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleManager
import me.tbsten.koma.strict.idea.flow.GeneratedFlowSpec
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtModifierList
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.resolve.ImportPath

/**
 * Inserts a generated `@FlowSpec` for a store, splitting it across two files (`flows-design.md` IDE
 * section): the `internal annotation class <Name>` **declaration** goes into a sibling
 * `<StateFile>.flows.kt` (created with a package header + koma-strict imports on first use, appended to
 * afterwards), while the one-line `@<Name>` application stays on the `@StoreSpec` [root] in the state
 * file (a Kotlin annotation must live at its declaration site). Keeping the verbose `@FlowSpec` bodies
 * out of the state file is the whole point of the split. All edits run in one undoable
 * [WriteCommandAction]; the annotation-class name is deduped against the flows file.
 */
object FlowSpecInserter {

    /**
     * Performs the insertion and returns the inserted `@<Name>` **application** on [root] (for
     * navigation — the edit the user actually cares to see is the state file, not the verbose flows-file
     * declaration), or null if nothing was written. Must be called on the EDT.
     */
    fun insert(project: Project, root: KtClassOrObject, generated: GeneratedFlowSpec): PsiElement? {
        var inserted: PsiElement? = null
        // PSI の read/write は全て WriteCommandAction 内で行う: EDT でも素の PSI read は read-action を
        // 要求される (2026.1 の threading model) ため、uniqueName 等の読み取りもこの中に入れる。
        WriteCommandAction.runWriteCommandAction(project, "Add @FlowSpec", null, {
            val stateFile = root.containingKtFile
            val dir = stateFile.containingDirectory ?: return@runWriteCommandAction
            val factory = KtPsiFactory(project)
            val flowsFile = dir.findFile(flowsFileName(stateFile.name)) as? KtFile
            // 宣言は flows ファイルに置くので、連番回避 (RecordedFlow -> RecordedFlow2 ...) は flows ファイルの
            // 既存クラス名に対して行う。初回はまだファイルが無いので空集合 = base 名がそのまま採用される。
            val used = flowsFile?.collectDescendantsOfType<KtClassOrObject>()?.mapNotNull { it.name }?.toHashSet() ?: hashSetOf()
            val name = uniqueName(used, generated.annotationClassName)
            val declarationText = generated.declaration.replaceFirst(
                "annotation class ${generated.annotationClassName}",
                "annotation class $name",
            )
            // 1. まず root へ @<Name> を付与する (state ファイルに残す。注釈はクラス宣言箇所にしか付けられない)。
            //    createAnnotationEntry は throwaway ファイルの parse に依存するので、**新規ファイル作成
            //    (下の dir.add) より前に**済ませる。順序を逆にすると live IDE では新規ファイル作成後の
            //    throwaway parse が壊れ、createProperty(...).modifierList == null で NPE になる
            //    (BasePlatformTestCase の軽量 VFS では再現しないため要注意)。
            val appliedEntry = applyAnnotationToRoot(factory, root, name)
            // 2. 宣言を flows ファイルへ (無ければ package + import 付きで新規作成、あれば末尾に追記)。
            val declaration = if (flowsFile == null) {
                createFlowsFile(factory, dir, flowsFileName(stateFile.name), stateFile.packageFqName.asString(), generated.requiredImports, declarationText)
            } else {
                appendToFlowsFile(factory, flowsFile, generated.requiredImports, declarationText)
            }
            // 3. 整形。modifierList の reformat は挿入した entry の PSI を再生成しうるので、reformat 後の
            // modifierList から @<Name> を引き直す (ジャンプ先は state ファイルの適用箇所 declaration ではない:
            // ユーザーが確認したいのは自分が触っているファイルの追加行であって、隣の flows ファイルではない)。
            val reformatter = CodeStyleManager.getInstance(project)
            declaration?.let { reformatter.reformat(it) }
            val reformattedModifiers = root.modifierList?.let { reformatter.reformat(it) } as? KtModifierList
            inserted = reformattedModifiers?.annotationEntries?.firstOrNull { it.text == "@$name" } ?: appliedEntry
        })
        return inserted
    }

    /** Sibling flows-file name for a state file: `FeedState.kt` -> `FeedState.flows.kt`. */
    private fun flowsFileName(stateFileName: String): String = stateFileName.removeSuffix(".kt") + ".flows.kt"

    /** Creates `<StateFile>.flows.kt` with a package header + koma-strict imports + the declaration; returns the declaration. */
    private fun createFlowsFile(
        factory: KtPsiFactory,
        dir: PsiDirectory,
        fileName: String,
        packageName: String,
        imports: List<String>,
        declarationText: String,
    ): PsiElement? {
        val content = buildString {
            if (packageName.isNotEmpty()) append("package ").append(packageName).append("\n\n")
            for (fq in imports) append("import ").append(fq).append('\n')
            if (imports.isNotEmpty()) append('\n')
            append("// TODO 追加の Flow があればこのファイルに続けて宣言していく\n")
            append(declarationText).append('\n')
        }
        val added = dir.add(factory.createFile(fileName, content)) as? KtFile ?: return null
        return added.declarations.firstOrNull { it is KtClassOrObject }
    }

    /** Appends the declaration to an existing flows file, adding any missing koma-strict imports; returns the declaration. */
    private fun appendToFlowsFile(
        factory: KtPsiFactory,
        flowsFile: KtFile,
        imports: List<String>,
        declarationText: String,
    ): PsiElement {
        flowsFile.importList?.let { importList ->
            val existing = importList.imports.mapNotNull { it.importedFqName?.asString() }.toHashSet()
            for (fq in imports) {
                if (fq !in existing) importList.add(factory.createImportDirective(ImportPath(FqName(fq), false)))
            }
        }
        flowsFile.add(factory.createNewLine(2))
        return flowsFile.add(factory.createDeclaration<KtClassOrObject>(declarationText))
    }

    /**
     * Applies `@<name>` to [root] and returns the inserted entry. [KtClassOrObject.addAnnotationEntry]
     * prepends to the modifier list (above `@StoreSpec`), so to keep the flow annotation last (just
     * before the declaration) it is inserted after the current last annotation instead.
     */
    private fun applyAnnotationToRoot(factory: KtPsiFactory, root: KtClassOrObject, name: String): KtAnnotationEntry {
        val entry = factory.createAnnotationEntry("@$name")
        val modifierList = root.modifierList
        val lastAnnotation = modifierList?.annotationEntries?.lastOrNull()
        return if (modifierList != null && lastAnnotation != null) {
            val added = modifierList.addAfter(entry, lastAnnotation) as KtAnnotationEntry
            modifierList.addAfter(factory.createNewLine(), lastAnnotation)
            added
        } else {
            root.addAnnotationEntry(entry)
        }
    }

    /** First of `base`, `base2`, `base3` … not already declared in the flows file. */
    private fun uniqueName(used: Set<String>, base: String): String {
        if (base !in used) return base
        var n = 2
        while ("$base$n" in used) n++
        return "$base$n"
    }
}
