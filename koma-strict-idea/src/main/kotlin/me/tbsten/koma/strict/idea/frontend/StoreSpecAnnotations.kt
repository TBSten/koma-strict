package me.tbsten.koma.strict.idea.frontend

import me.tbsten.koma.strict.idea.model.ActionTrigger
import me.tbsten.koma.strict.idea.model.EnterTrigger
import me.tbsten.koma.strict.idea.model.ExitInfo
import me.tbsten.koma.strict.idea.model.RecoverTrigger
import me.tbsten.koma.strict.idea.model.SourceAnchor
import me.tbsten.koma.strict.idea.model.StateId
import me.tbsten.koma.strict.idea.model.UNRESOLVED_MARKER
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotation
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationValue
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtUserType

/** Fully-qualified names of the koma-strict annotations (frontend resolves against these, not source shortNames). */
internal object KomaStrictFq {
    const val PKG: String = "me.tbsten.koma.strict"
    const val STORE_SPEC: String = "$PKG.StoreSpec"
    const val ON_ENTER: String = "$PKG.OnEnter"
    const val ON_EXIT: String = "$PKG.OnExit"
    const val ON_ACTION: String = "$PKG.OnAction"
    const val ON_RECOVER: String = "$PKG.OnRecover"
    const val STAY: String = "$PKG.Stay"
}

/** The triggers resolved for one state node. */
internal data class NodeTriggers(
    val enter: EnterTrigger?,
    val actions: List<ActionTrigger>,
    val recovers: List<RecoverTrigger>,
    val exit: ExitInfo?,
)

/**
 * Resolves every koma-strict trigger declared on [psi] via the Analysis API (the confirmed recipe in
 * `ide.dev.md`). [fqToId] maps a fully-qualified state name to its root-relative id so that
 * `nextState = [X::class]` / `initial = [...]` references become [StateId]s.
 *
 * This runs inside a [KaSession] (`analyze { }`); it never touches KSP types.
 */
internal fun KaSession.readTriggers(
    psi: KtClassOrObject,
    fqToId: Map<String, StateId>,
    // @OnEnter は leaf のみが宣言できる。非 leaf では計算自体を省く (無駄な resolve を避ける)。
    includeEnter: Boolean = true,
): NodeTriggers {
    val annotations = psi.symbol.annotations
    val enter = if (!includeEnter) null else annotations.firstOrNull { it.fqName() == KomaStrictFq.ON_ENTER }?.let { ann ->
        val ns = readNextState(ann, fqToId)
        EnterTrigger(
            targets = ns.targets,
            stay = ns.stay,
            emits = readEmits(ann),
            unresolvedTargets = ns.unresolvedTargets,
            source = ann.entryAnchor(),
        )
    }
    val exit = annotations.firstOrNull { it.fqName() == KomaStrictFq.ON_EXIT }
        ?.let { ExitInfo(emits = readEmits(it)) }
    val actions = readActions(annotations.filter { it.fqName() == KomaStrictFq.ON_ACTION }, fqToId)
    val recovers = readRecovers(annotations.filter { it.fqName() == KomaStrictFq.ON_RECOVER }, fqToId)
    return NodeTriggers(enter, actions, recovers, exit)
}

/** Resolved `@StoreSpec(initial = [...])`: the in-store seed leaves and whether any element was unresolved. */
internal class ResolvedInitial(val targets: List<StateId>, val unresolved: Boolean)

/** Resolves `@StoreSpec(initial = [...])` on the root. */
internal fun KaSession.readInitial(root: KtClassOrObject, fqToId: Map<String, StateId>): ResolvedInitial {
    val storeSpec = root.symbol.annotations.firstOrNull { it.fqName() == KomaStrictFq.STORE_SPEC }
        ?: return ResolvedInitial(emptyList(), unresolved = false)
    val ns = readNextStateNamed(storeSpec, "initial", fqToId)
    return ResolvedInitial(ns.targets, unresolved = ns.unresolvedTargets.isNotEmpty())
}

private fun KaSession.readActions(
    actionAnnotations: List<KaAnnotation>,
    fqToId: Map<String, StateId>,
): List<ActionTrigger> = actionAnnotations.map { ann ->
    // 型引数 @OnAction<A> は各 KaAnnotation 自身の PSI から読む (shortName 照合で index がずれる誤対応を避ける:
    // alias import `@Act<Reload>` や FQN 違いの同名注釈が混ざっても、注釈と型名が必ず一致する)。
    val typeName = typeArgumentSimpleName(ann) ?: UNRESOLVED_MARKER
    val ns = readNextState(ann, fqToId)
    ActionTrigger(
        actionName = typeName,
        targets = ns.targets,
        stay = ns.stay,
        emits = readEmits(ann),
        unresolvedTargets = ns.unresolvedTargets,
        source = ann.entryAnchor(),
        actionRef = typeArgumentRef(ann),
    )
}

private fun KaSession.readRecovers(
    recoverAnnotations: List<KaAnnotation>,
    fqToId: Map<String, StateId>,
): List<RecoverTrigger> = recoverAnnotations.map { ann ->
    val typeName = typeArgumentSimpleName(ann) ?: UNRESOLVED_MARKER
    val ns = readNextState(ann, fqToId)
    RecoverTrigger(
        exceptionName = typeName,
        targets = ns.targets,
        stay = ns.stay,
        emits = readEmits(ann),
        unresolvedTargets = ns.unresolvedTargets,
        source = ann.entryAnchor(),
        exceptionRef = typeArgumentRef(ann),
    )
}

/**
 * Normalized `nextState` / `initial`: the in-store target leaves, the [stay] capability, and the
 * elements that could not be resolved to an in-store leaf ([unresolvedTargets], each already marked
 * with [UNRESOLVED_MARKER]). Kept as a distinct result so the caller can tell "declared nothing"
 * (missing / empty ⇒ Stay-only) apart from "declared something we couldn't resolve".
 */
private class NextStateResult(
    val targets: List<StateId>,
    val stay: Boolean,
    val unresolvedTargets: List<String>,
)

/** Reads a `nextState = [...]` argument. */
private fun KaSession.readNextState(ann: KaAnnotation, fqToId: Map<String, StateId>): NextStateResult =
    readNextStateNamed(ann, "nextState", fqToId)

private fun KaSession.readNextStateNamed(
    ann: KaAnnotation,
    argName: String,
    fqToId: Map<String, StateId>,
): NextStateResult {
    val elements = classArrayElements(ann, argName)
    var stay = false
    val targets = mutableListOf<StateId>()
    val unresolved = mutableListOf<String>()
    for (element in elements) {
        if (element == null) {
            // 編集途中の error type / 解決不能な class literal。空値として黙って消さず未解決として残す。
            unresolved += UNRESOLVED_MARKER
            continue
        }
        val classId = element.classId
        val fq = classId.asFqNameString()
        when {
            fq == KomaStrictFq.STAY -> stay = true
            else -> {
                val mapped = fqToId[fq]
                // このストア内の state に解決できれば通常 target。解決できても fqToId 外 (別ストアの state /
                // typo で別クラスを指す等) の foreign 参照は通常 target と同じ成功扱いにせず、`?Name` の未解決
                // として表に残す (図は leaf でないので描かないが、表 = 正 は嘘をつかない)。
                if (mapped != null) targets += mapped
                else unresolved += UNRESOLVED_MARKER + classId.shortClassName.asString()
            }
        }
    }
    // 空配列/省略の nextState は Stay-only の糖衣 (KSP: canStay = declaredStay || targets.isEmpty())。
    // ただし要素はあるが未解決だった場合 (unresolved) は空とみなして Stay を捏造してはいけない
    // (実コードには本物の target がある可能性があり、暗黙 Stay は意味を変えてしまう)。
    val stayResult = stay || elements.isEmpty()
    return NextStateResult(targets = targets, stay = stayResult, unresolvedTargets = unresolved)
}

/**
 * Reads an `emit = [...]` argument into event simple names, keeping unresolved elements as
 * [UNRESOLVED_MARKER] so the declared element count is never silently reduced by half-typed code.
 */
private fun KaSession.readEmits(ann: KaAnnotation): List<String> =
    classArrayElements(ann, "emit").map { element ->
        element?.let { it.classId.shortClassName.asString() } ?: UNRESOLVED_MARKER
    }

/**
 * Extracts the elements of a `KClass`-array annotation argument, preserving order and arity. A
 * resolvable element becomes its [KaClassType]; an unresolvable element (an error type from
 * half-typed code, which is not a [KaClassType]) becomes `null` so the caller can surface it rather
 * than drop it. A missing / empty / non-array argument yields an empty list (the Stay-only sugar).
 */
private fun KaSession.classArrayElements(ann: KaAnnotation, argName: String): List<KaClassType?> {
    val array = ann.arguments.firstOrNull { it.name.asString() == argName }?.expression
        as? KaAnnotationValue.ArrayValue ?: return emptyList()
    return array.values.map { value ->
        (value as? KaAnnotationValue.ClassLiteralValue)?.type as? KaClassType
    }
}

/**
 * Resolves the simple name of the single type argument of this annotation (`@OnAction<A>` -> `A`) from
 * the annotation's own PSI, so the type name always belongs to this exact annotation application.
 */
private fun KaSession.typeArgumentSimpleName(ann: KaAnnotation): String? =
    typeArgumentClassType(ann)?.classId?.shortClassName?.asString()

/**
 * Package-relative reference of the single type argument (`@OnAction<FeedAction.Retry>` -> `FeedAction.Retry`)
 * from the annotation's own PSI, so flow codegen can emit `FlowStep(FeedAction.Retry::class)` with the
 * enclosing path rather than the bare leaf name.
 */
private fun KaSession.typeArgumentRef(ann: KaAnnotation): String? =
    typeArgumentClassType(ann)?.classId?.relativeClassName?.asString()

private fun KaSession.typeArgumentClassType(ann: KaAnnotation): KaClassType? {
    val entry = ann.psi as? KtAnnotationEntry ?: return null
    val typeRef = (entry.typeReference?.typeElement as? KtUserType)
        ?.typeArgumentList?.arguments?.firstOrNull()?.typeReference ?: return null
    return typeRef.type as? KaClassType
}

private fun KaAnnotation.fqName(): String? = classId?.asFqNameString()

/**
 * Click-to-declaration anchor for the trigger this annotation declares (`ide-4.md`): a smart pointer to
 * the `@On…` annotation application. Null when the annotation has no source PSI (e.g. an inherited /
 * synthetic annotation), in which case the transition arrow simply stays non-navigable.
 */
private fun KaAnnotation.entryAnchor(): SourceAnchor? = (psi as? KtAnnotationEntry)?.let { anchorTo(it) }
