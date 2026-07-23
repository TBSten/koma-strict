package me.tbsten.koma.strict.idea.frontend

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.awt.RelativePoint
import me.tbsten.koma.strict.idea.model.StateId
import me.tbsten.koma.strict.idea.model.StoreDiagramModel
import me.tbsten.koma.strict.idea.model.findById
import me.tbsten.koma.strict.idea.model.outgoingTransitionsByState
import me.tbsten.koma.strict.idea.model.ownTriggers
import me.tbsten.koma.strict.idea.model.walk
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import java.awt.event.MouseEvent
import javax.swing.Icon

/**
 * Editor gutter icons for a `@StoreSpec` state file (`ide-gutter.md`): a state declaration jumps to the
 * next states reachable from it (own + inherited scope transitions, each labelled by its trigger), and
 * an `@OnEnter` / `@OnAction` / `@OnRecover` line jumps to that transition's target state. A single
 * candidate navigates directly; several show a chooser popup; none render no icon at all.
 *
 * The reachability itself is the pure [outgoingTransitionsByState]; this provider only maps model nodes /
 * triggers back to their PSI leaves (via [PsiSourceAnchor]) and drives navigation. The whole per-file
 * mapping is cached on the `KtFile` so it is rebuilt only when the file changes.
 */
class StoreSpecGutterLineMarkerProvider : LineMarkerProvider {

    // 重い解析はまとめて行うため、単一要素の速いパスでは何もしない。
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

    override fun collectSlowLineMarkers(elements: List<PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
        val ktFile = elements.firstOrNull()?.containingFile as? KtFile ?: return
        val markers = markersFor(ktFile) ?: return
        for (element in elements) {
            val marker = markers[element] ?: continue
            result.add(marker.toLineMarkerInfo(element))
        }
    }

    // KtFile が変わるまで再計算しないようキャッシュする (daemon の再走のたびに再解析しない)。
    private fun markersFor(ktFile: KtFile): Map<PsiElement, Marker>? =
        CachedValuesManager.getCachedValue(ktFile) {
            CachedValueProvider.Result.create(computeMarkers(ktFile), ktFile)
        }

    private fun computeMarkers(ktFile: KtFile): Map<PsiElement, Marker>? {
        val roots = StoreSpecModelBuilder.findStoreSpecClasses(ktFile)
        if (roots.isEmpty()) return null
        val markers = HashMap<PsiElement, Marker>()
        for (root in roots) {
            val model = try {
                StoreSpecModelBuilder.build(root)
            } catch (e: IndexNotReadyException) {
                return null // index 未完。次の解析パスで作られる。
            }
            if (model.degraded) continue
            val outgoing = model.outgoingTransitionsByState()
            for (node in model.root.walk()) {
                // State 宣言行: その state から行ける次の state (自前 + 継承)。
                val nameLeaf = (node.source as? PsiSourceAnchor)?.element.let { it as? KtClassOrObject }?.nameIdentifier
                if (nameLeaf != null) {
                    val targets = outgoing[node.id].orEmpty().mapNotNull { t ->
                        model.anchorFor(t.target)?.let { GutterTarget("${t.target.dotted} (by ${t.via})", it) }
                    }
                    if (targets.isNotEmpty()) markers[nameLeaf] = Marker.NextState(targets)
                }
                // @OnEnter / @OnAction / @OnRecover 行: そのトランジションの遷移先。
                for (trigger in node.ownTriggers()) {
                    val annLeaf = ((trigger.source as? PsiSourceAnchor)?.element as? KtAnnotationEntry)?.nameLeaf() ?: continue
                    val targets = trigger.targets.mapNotNull { id ->
                        model.anchorFor(id)?.let { GutterTarget(id.dotted, it) }
                    }
                    if (targets.isNotEmpty()) markers[annLeaf] = Marker.Transition(targets)
                }
            }
        }
        return markers
    }

    private fun StoreDiagramModel.anchorFor(id: StateId): PsiSourceAnchor? =
        root.findById(id)?.source as? PsiSourceAnchor

    /** The `@OnAction` identifier leaf of an annotation entry, so the marker lands on the annotation. */
    private fun KtAnnotationEntry.nameLeaf(): PsiElement? =
        calleeExpression?.constructorReferenceExpression?.getReferencedNameElement()

    /** A navigable candidate: [label] shown in the chooser, [anchor] re-resolved lazily on click. */
    private class GutterTarget(val label: String, val anchor: PsiSourceAnchor)

    private sealed class Marker(val targets: List<GutterTarget>) {
        abstract val icon: Icon
        abstract val tooltip: String
        abstract val popupTitle: String

        class NextState(targets: List<GutterTarget>) : Marker(targets) {
            override val icon get() = AllIcons.Actions.Forward
            override val tooltip get() = "Go to next state"
            override val popupTitle get() = "Next State"
        }

        class Transition(targets: List<GutterTarget>) : Marker(targets) {
            override val icon get() = AllIcons.Actions.Forward
            override val tooltip get() = "Go to transition target"
            override val popupTitle get() = "Transition Target"
        }

        fun toLineMarkerInfo(element: PsiElement): LineMarkerInfo<PsiElement> = LineMarkerInfo(
            element,
            element.textRange,
            icon,
            { tooltip },
            GutterIconNavigationHandler<PsiElement> { event, _ -> navigate(event) },
            GutterIconRenderer.Alignment.LEFT,
            { tooltip },
        )

        // 候補 1 つなら即ジャンプ、複数ならポップアップで選択。
        private fun navigate(event: MouseEvent) {
            if (targets.size == 1) {
                open(targets.first())
                return
            }
            JBPopupFactory.getInstance()
                .createPopupChooserBuilder(targets)
                .setTitle(popupTitle)
                .setRenderer(SimpleListCellRenderer.create("") { it.label })
                .setItemChosenCallback { open(it) }
                .createPopup()
                .show(RelativePoint(event))
        }

        private fun open(target: GutterTarget) {
            // smart pointer の解決 (PSI read) は read action 内で確定し、navigate(true) だけ EDT で呼ぶ。
            ReadAction
                .computeBlocking<Navigatable?, RuntimeException> {
                    (target.anchor.element as? Navigatable)?.takeIf { it.canNavigate() }
                }?.navigate(true)
        }
    }
}
