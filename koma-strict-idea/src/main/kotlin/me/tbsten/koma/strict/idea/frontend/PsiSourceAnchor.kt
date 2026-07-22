package me.tbsten.koma.strict.idea.frontend

import com.intellij.psi.SmartPsiElementPointer
import me.tbsten.koma.strict.idea.model.SourceAnchor
import org.jetbrains.kotlin.psi.KtClassOrObject

/**
 * PSI-backed [SourceAnchor]: a smart pointer from a diagram node back to the state's declaration.
 *
 * The tool window's click-to-declaration (`ide.md` v1) resolves [declaration] and hands it to
 * `NavigationUtil`. A smart pointer is used so the anchor survives edits between analysis and click
 * (it re-resolves lazily), which is why the click target is asserted through the pointer in tests.
 */
@JvmInline
value class PsiSourceAnchor(
    val pointer: SmartPsiElementPointer<KtClassOrObject>,
) : SourceAnchor {
    /** The declaration, or null if it was invalidated (edited / deleted). */
    val declaration: KtClassOrObject? get() = pointer.element
}
