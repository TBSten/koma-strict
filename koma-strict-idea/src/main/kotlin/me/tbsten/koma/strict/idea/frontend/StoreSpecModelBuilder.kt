package me.tbsten.koma.strict.idea.frontend

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.psi.SmartPointerManager
import me.tbsten.koma.strict.idea.model.DiagramStateNode
import me.tbsten.koma.strict.idea.model.GroupState
import me.tbsten.koma.strict.idea.model.LeafState
import me.tbsten.koma.strict.idea.model.Reachability
import me.tbsten.koma.strict.idea.model.RootState
import me.tbsten.koma.strict.idea.model.StateId
import me.tbsten.koma.strict.idea.model.StoreDiagramModel
import me.tbsten.koma.strict.idea.model.hasUnresolvedDeclarations
import me.tbsten.koma.strict.idea.model.walk
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import kotlin.coroutines.cancellation.CancellationException

/**
 * Analysis-API frontend: turns an open `@StoreSpec` [KtClassOrObject] into the slim
 * [StoreDiagramModel] (`ide.md` "parse: Analysis API").
 *
 * The **resolved** contract mirrors the KSP state-tree parser
 * (`koma-strict-ksp .../core/storeSpec/StateTreeParsing.kt`): discovery matches the annotation by
 * fully-qualified [KomaStrictFq.STORE_SPEC] (not a source shortName), group vs leaf is decided by the
 * `sealed` modifier (not by whether children happen to exist), and children are the nested
 * declarations whose **resolved** direct supertype is the parent — so a typealias-parented state is
 * kept, a same-simple-name foreign supertype is rejected, an empty sealed node stays a group, and a
 * non-sealed node never adopts a nested helper or subtype.
 *
 * Any resolution failure (half-typed code) degrades to a names-only model — a separate, PSI-only
 * skeleton with click anchors and no triggers — instead of throwing, so the tool window never goes
 * blank. That degraded fallback is intentionally kept apart from the resolved contract above.
 */
object StoreSpecModelBuilder {

    /**
     * Every `@StoreSpec`-annotated declaration in the file (top-level or nested), confirmed by the
     * fully-qualified annotation [KomaStrictFq.STORE_SPEC] via the Analysis API. Enumerating the
     * candidate declarations is unavoidably PSI (the Analysis API resolves symbols but does not offer
     * a symbol-only "all nested classes in a file" traversal), so a cheap PSI pre-filter — declarations
     * that carry any annotation — keeps a file with no annotations from ever opening an analysis
     * session; the session then confirms each via [isStoreSpec] (per-entry `classId` resolution, so an
     * alias-imported `@Spec` is accepted and a foreign same-name `StoreSpec` is rejected).
     */
    fun findStoreSpecClasses(file: KtFile): List<KtClassOrObject> {
        val candidates = file.collectDescendantsOfType<KtClassOrObject> { it.annotationEntries.isNotEmpty() }
        if (candidates.isEmpty()) return emptyList()
        return analyze(file) {
            candidates.filter { candidate -> isStoreSpec(candidate) }
        }
    }

    /**
     * True when [candidate] carries the koma-strict `@StoreSpec`. Each annotation is resolved through
     * the [KaSession] (`entry.typeReference?.type`): one that resolves to a class type is matched
     * strictly by [KomaStrictFq.STORE_SPEC] — so an alias-imported `@Spec` is accepted and a foreign
     * same-name `StoreSpec` is rejected. Only an annotation that does **not** resolve to a class type
     * (an error type — koma-strict not yet on the classpath / half-typed code) falls back to the source
     * shortName, so the tool window still degrades gracefully; a resolvable foreign annotation never
     * reaches that fallback. (Per-entry resolution is deliberate: `symbol.annotations` cannot tell an
     * unresolvable annotation apart from a resolved one, since an error annotation keeps a bogus classId.)
     */
    private fun KaSession.isStoreSpec(candidate: KtClassOrObject): Boolean =
        candidate.annotationEntries.any { entry ->
            val fqName = (entry.typeReference?.type as? KaClassType)?.classId?.asFqNameString()
            if (fqName != null) fqName == KomaStrictFq.STORE_SPEC
            else entry.shortName?.asString() == KomaStrictFq.STORE_SPEC.substringAfterLast('.')
        }

    /** Builds the diagram model for one store root. Must be called inside a read action. */
    fun build(root: KtClassOrObject): StoreDiagramModel {
        // 半端コード用の degraded fallback は resolution 無しの PSI 近似 (analyze が丸ごと失敗した時のみ使う)。
        val psiSkeleton = buildSkeleton(root, StateId.Root, isRoot = true, ::psiChildState)
        // KSP 契約: root が sealed でなければ state tree を成さない (KSP は null を返し何も生成しない)。
        // KSP invalid な非 sealed root を「正常な完全 model」として見せず degraded にする。
        if (!root.hasModifier(KtTokens.SEALED_KEYWORD)) {
            return degradedModel(psiSkeleton, "@StoreSpec must be applied to a sealed interface or sealed class.")
        }
        return try {
            analyze(root) {
                // 解決済み contract: sealed modifier + 解決済み direct supertype で tree を組む (KSP と同一の形)。
                val skeleton = buildSkeleton(root, StateId.Root, isRoot = true) { child, parent ->
                    isResolvedChildState(child, parent)
                }
                val fqToId = HashMap<String, StateId>().also { collectFqToId(skeleton, it) }
                val initial = readInitial(root, fqToId)
                val rootNode = assembleNode(skeleton, fqToId) as RootState
                val reachable = Reachability.compute(rootNode, initial.targets)
                // 解析は成功したが、一部の nextState / emit / 型引数が解決できなかった (foreign / error type) 場合は
                // partial として印を付け、UI で「解析不完全」を明示する (未解決値を空値として黙って消さない)。
                val unresolved = initial.unresolved || rootNode.walk().any { it.hasUnresolvedDeclarations() }
                StoreDiagramModel(
                    root = rootNode,
                    initial = initial.targets,
                    reachableLeafIds = reachable,
                    unresolved = unresolved,
                )
            }
        } catch (e: Exception) {
            // 制御フロー系 (キャンセル / dumb mode) は degraded に化けさせず必ず伝播する。
            // これを握りつぶすと正当なキャンセルが degraded=true として cache/表示に焼き付く (IntelliJ 指針)。
            if (e is ProcessCanceledException ||
                e is CancellationException ||
                e is IndexNotReadyException
            ) {
                throw e
            }
            degradedModel(psiSkeleton, e.message ?: e::class.simpleName)
        }
    }

    // ---- structural skeleton ----

    private enum class NodeKind { ROOT, GROUP, LEAF }

    private class NodeSkeleton(
        val psi: KtClassOrObject,
        val simpleName: String,
        val id: StateId,
        val fqName: String?,
        val children: List<NodeSkeleton>,
        val kind: NodeKind,
    ) {
        val isRoot: Boolean get() = kind == NodeKind.ROOT
        val isLeaf: Boolean get() = kind == NodeKind.LEAF
    }

    /**
     * Builds the structural skeleton. Group vs leaf follows the `sealed` modifier (KSP `isParent`):
     * the root and every `sealed` node are parents (even with zero children), every non-sealed node is
     * a leaf that never adopts children. Which nested declarations count as a parent's children is
     * decided by [isChildState] — resolved-supertype matching for the real contract, shortName
     * matching for the degraded PSI fallback.
     */
    private fun buildSkeleton(
        psi: KtClassOrObject,
        id: StateId,
        isRoot: Boolean,
        isChildState: (child: KtClassOrObject, parent: KtClassOrObject) -> Boolean,
    ): NodeSkeleton {
        val kind = when {
            isRoot -> NodeKind.ROOT
            psi.hasModifier(KtTokens.SEALED_KEYWORD) -> NodeKind.GROUP
            else -> NodeKind.LEAF
        }
        val children = if (kind == NodeKind.LEAF) {
            // KSP: 非 sealed (= 非 parent) は子を持たない。leaf が抱える nested helper / nested subtype を
            // state に数えない (これを怠ると leaf が phantom child を持つ group に化ける)。
            emptyList()
        } else {
            psi.declarations
                .filterIsInstance<KtClassOrObject>()
                .filterNot { it is KtObjectDeclaration && it.isCompanion() }
                .filter { isChildState(it, psi) }
                .map { child -> buildSkeleton(child, id + (child.name ?: "?"), isRoot = false, isChildState) }
        }
        return NodeSkeleton(psi, psi.name ?: "?", id, psi.fqName?.asString(), children, kind)
    }

    /**
     * Resolved child matcher (KSP `directChildStates`): [child] is a child of [parent] when one of its
     * direct supertypes resolves — following typealiases — to the exact declaration [parent]. Matching
     * on the resolved fully-qualified name keeps a typealias-parented state, and rejects a foreign
     * supertype that merely shares the parent's simple name.
     */
    private fun KaSession.isResolvedChildState(child: KtClassOrObject, parent: KtClassOrObject): Boolean {
        val parentFq = parent.fqName?.asString() ?: return false
        return child.superTypeListEntries.any { entry ->
            val superType = entry.typeReference?.type as? KaClassType
            superType?.classId?.asFqNameString() == parentFq
        }
    }

    /**
     * PSI-only child matcher for the degraded fallback: [child] lists [parent]'s simple name as a
     * supertype (no resolution). Approximate by design — the resolved contract is reserved for
     * successfully analyzed code; this only has to keep half-typed code non-blank.
     */
    private fun psiChildState(child: KtClassOrObject, parent: KtClassOrObject): Boolean =
        child.extendsByName(parent.name)

    /** True when this declaration lists [parentName] as a supertype (purely from PSI, no resolution). */
    private fun KtClassOrObject.extendsByName(parentName: String?): Boolean {
        if (parentName == null) return true
        return superTypeListEntries.any { entry ->
            (entry.typeReference?.typeElement as? KtUserType)?.referencedName == parentName
        }
    }

    private fun collectFqToId(node: NodeSkeleton, into: MutableMap<String, StateId>) {
        node.fqName?.let { into[it] = node.id }
        node.children.forEach { collectFqToId(it, into) }
    }

    // ---- resolved assembly (inside analyze) ----

    private fun KaSession.assembleNode(node: NodeSkeleton, fqToId: Map<String, StateId>): DiagramStateNode {
        val triggers = readTriggers(node.psi, fqToId, includeEnter = node.isLeaf)
        return when (node.kind) {
            NodeKind.ROOT -> RootState(
                simpleName = node.simpleName,
                children = node.children.map { assembleNode(it, fqToId) },
                actions = triggers.actions,
                recovers = triggers.recovers,
                exit = triggers.exit,
                source = anchorFor(node.psi),
            )
            NodeKind.LEAF -> LeafState(
                simpleName = node.simpleName,
                id = node.id,
                enter = triggers.enter,
                actions = triggers.actions,
                recovers = triggers.recovers,
                exit = triggers.exit,
                source = anchorFor(node.psi),
            )
            NodeKind.GROUP -> GroupState(
                simpleName = node.simpleName,
                id = node.id,
                children = node.children.map { assembleNode(it, fqToId) },
                actions = triggers.actions,
                recovers = triggers.recovers,
                exit = triggers.exit,
                source = anchorFor(node.psi),
            )
        }
    }

    // ---- degraded fallback (names + anchors only) ----

    private fun degradedModel(skeleton: NodeSkeleton, cause: String?): StoreDiagramModel =
        StoreDiagramModel(
            root = degradedNode(skeleton) as RootState,
            degraded = true,
            error = cause,
        )

    private fun degradedNode(node: NodeSkeleton): DiagramStateNode = when (node.kind) {
        NodeKind.ROOT -> RootState(node.simpleName, node.children.map { degradedNode(it) }, source = anchorFor(node.psi))
        NodeKind.LEAF -> LeafState(node.simpleName, node.id, source = anchorFor(node.psi))
        NodeKind.GROUP -> GroupState(node.simpleName, node.id, node.children.map { degradedNode(it) }, source = anchorFor(node.psi))
    }

    private fun anchorFor(psi: KtClassOrObject): PsiSourceAnchor =
        PsiSourceAnchor(SmartPointerManager.getInstance(psi.project).createSmartPsiElementPointer(psi))
}
