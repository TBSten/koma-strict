package me.tbsten.koma.strict.idea.frontend

import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import me.tbsten.koma.strict.idea.model.SourceAnchor
import org.jetbrains.kotlin.psi.KtElement

/**
 * PSI-backed [SourceAnchor]: a smart pointer from a diagram element back to the declaration it should
 * navigate to. The target is a state declaration (`KtClassOrObject`) for a node / composite box, or a
 * trigger's annotation application (`KtAnnotationEntry`) for a transition arrow (`ide-4.md`) — both are
 * [KtElement]s, and `KtElement : NavigatablePsiElement`, so a single anchor type covers both.
 *
 * The tool window's click-to-declaration (`ide.md` v1) resolves [element] and drives its `Navigatable`.
 * A smart pointer is used so the anchor survives edits between analysis and click (it re-resolves
 * lazily), which is why the click target is asserted through the pointer in tests.
 */
@JvmInline
value class PsiSourceAnchor(
    val pointer: SmartPsiElementPointer<out KtElement>,
) : SourceAnchor {
    /** The navigable target (state declaration or trigger annotation), or null if invalidated (edited / deleted). */
    val element: KtElement? get() = pointer.element
}

/** Creates a [PsiSourceAnchor] pointing at [element] (a state declaration or a trigger annotation entry). */
internal fun anchorTo(element: KtElement): PsiSourceAnchor =
    PsiSourceAnchor(SmartPointerManager.getInstance(element.project).createSmartPsiElementPointer(element))
