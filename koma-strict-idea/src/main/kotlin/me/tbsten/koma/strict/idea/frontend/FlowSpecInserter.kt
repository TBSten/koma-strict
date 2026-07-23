package me.tbsten.koma.strict.idea.frontend

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleManager
import me.tbsten.koma.strict.idea.flow.GeneratedFlowSpec
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.resolve.ImportPath

/**
 * Inserts a generated `@FlowSpec` into a store's source file (`ide-test-code.md` F8): it adds the
 * `annotation class <Name>` declaration just before the `@StoreSpec` [root], applies `@<Name>` to the
 * root, and adds the koma-strict imports. All in one undoable [WriteCommandAction] so a single undo
 * reverts the whole edit; the annotation-class name is deduped against the file.
 */
object FlowSpecInserter {

    /**
     * Performs the insertion and returns the inserted `annotation class` element (for navigation), or
     * null if nothing was written. Must be called on the EDT.
     */
    fun insert(project: Project, root: KtClassOrObject, generated: GeneratedFlowSpec): PsiElement? {
        var inserted: PsiElement? = null
        // PSI の read/write は全て WriteCommandAction 内で行う: EDT でも素の PSI read は read-action を
        // 要求される (2026.1 の threading model) ため、uniqueName 等の読み取りもこの中に入れる。
        WriteCommandAction.runWriteCommandAction(project, "Add @FlowSpec", null, {
            val file = root.containingKtFile
            val factory = KtPsiFactory(project)
            // 同名 annotation class があれば連番で回避 (RecordedFlow -> RecordedFlow2 ...)。
            val name = uniqueName(root, generated.annotationClassName)
            val declarationText = generated.declaration.replaceFirst(
                "annotation class ${generated.annotationClassName}",
                "annotation class $name",
            )
            // 1. import (未 import の koma-strict 注釈)。state / action 参照は @StoreSpec と同一ファイルなので不要。
            val importList = file.importList
            if (importList != null) {
                val existing = importList.imports.mapNotNull { it.importedFqName?.asString() }.toHashSet()
                for (fq in generated.requiredImports) {
                    if (fq !in existing) {
                        importList.add(factory.createImportDirective(ImportPath(FqName(fq), false)))
                    }
                }
            }
            // 2. annotation class 宣言を root の直前に挿入。
            val declaration = factory.createDeclaration<KtClassOrObject>(declarationText)
            val added = file.addBefore(declaration, root)
            file.addAfter(factory.createNewLine(2), added)
            // 3. root へ @<Name> を付与。addAnnotationEntry は modifier list の先頭に足す (= @StoreSpec より
            //    上) ので、既存注釈の「一番最後」(宣言の直前) に来るよう、最後の注釈の後ろへ手動挿入する。
            val entry = factory.createAnnotationEntry("@$name")
            val modifierList = root.modifierList
            val lastAnnotation = modifierList?.annotationEntries?.lastOrNull()
            if (modifierList != null && lastAnnotation != null) {
                modifierList.addAfter(entry, lastAnnotation)
                modifierList.addAfter(factory.createNewLine(), lastAnnotation)
            } else {
                root.addAnnotationEntry(entry)
            }
            // 4. 整形 (import の改行 / 挿入宣言のインデント / 追加注釈の位置)。
            val reformatter = CodeStyleManager.getInstance(project)
            root.modifierList?.let { reformatter.reformat(it) }
            inserted = reformatter.reformat(added)
        })
        return inserted
    }

    /** First of `base`, `base2`, `base3` … not already used by a class/object in the file. */
    private fun uniqueName(root: KtClassOrObject, base: String): String {
        val used = root.containingKtFile.collectDescendantsOfType<KtClassOrObject>().mapNotNull { it.name }.toHashSet()
        if (base !in used) return base
        var n = 2
        while ("$base$n" in used) n++
        return "$base$n"
    }
}
