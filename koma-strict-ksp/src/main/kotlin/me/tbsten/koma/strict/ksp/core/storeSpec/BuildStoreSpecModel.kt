package me.tbsten.koma.strict.ksp.core.storeSpec

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import me.tbsten.koma.strict.ksp.core.common.fullName
import me.tbsten.koma.strict.ksp.model.GroupNode
import me.tbsten.koma.strict.ksp.model.LeafNode
import me.tbsten.koma.strict.ksp.model.RootNode
import me.tbsten.koma.strict.ksp.model.StateNode
import me.tbsten.koma.strict.ksp.model.StateParent
import me.tbsten.koma.strict.ksp.model.StatePath
import me.tbsten.koma.strict.ksp.model.StateVisibility
import me.tbsten.koma.strict.ksp.model.StoreSpec
import me.tbsten.koma.strict.ksp.model.TypeRef
import me.tbsten.koma.strict.ksp.options.KomaStrictOptions
import me.tbsten.koma.strict.ksp.util.with
import me.tbsten.koma.strict.StoreSpec as StoreSpecAnnotation

private val storeSpecFqn = StoreSpecAnnotation::class.fullName

/**
 * Builds a validated [StoreSpec] model from a sealed root annotated with @StoreSpec.
 *
 * Pipeline: structural parsing (pass A) -> handler parsing (pass B) -> actions/events
 * resolution -> model assembly -> structural validation. Returns null if there is even
 * one error, and the caller skips generation entirely (errors are already reported to
 * the logger).
 */
context(logger: KSPLogger, options: KomaStrictOptions)
internal fun buildValidatedStoreSpec(rootDecl: KSClassDeclaration): StoreSpec? {
    val diagnostics = StoreSpecDiagnostics(logger)
    val spec = with(diagnostics, options) { buildStoreSpec(rootDecl) }
    return if (diagnostics.hasErrors) null else spec
}

context(diagnostics: StoreSpecDiagnostics, options: KomaStrictOptions)
private fun buildStoreSpec(rootDecl: KSClassDeclaration): StoreSpec? {
    val contextPackage = rootDecl.packageName.asString()
    val tree = parseStateTree(rootDecl) ?: return null
    val parsed = tree.allNodes.associateWith { node -> parseNodeHandlers(tree, node, contextPackage) }

    val storeSpecAnnotation =
        rootDecl.annotations.firstOrNull {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == storeSpecFqn
        } ?: return null // getSymbolsWithAnnotation 経由なので実質起きない

    val allActionDecls = parsed.values.flatMap { it.actionDecls }
    val allEventDecls = parsed.values.flatMap { it.eventDecls }
    val actionsResolved =
        resolveActionsType(
            explicit = storeSpecAnnotation.kClassArgumentDeclaration("actions"),
            declared = allActionDecls,
            storeSpecAnnotation = storeSpecAnnotation,
        )
    val eventsResolved =
        resolveEventsType(
            explicit = storeSpecAnnotation.kClassArgumentDeclaration("events"),
            declared = allEventDecls,
            storeSpecAnnotation = storeSpecAnnotation,
        )

    val root = assembleNode(tree.root, tree.rootType, parsed) as RootNode
    val spec =
        StoreSpec(
            root = root,
            // 解決失敗時も残りの検証を流すため placeholder を入れる (hasErrors により生成はされない)
            actionsType = actionsResolved?.ref ?: TypeRef("kotlin", "Nothing"),
            eventsType = eventsResolved?.ref,
            initial = parseInitial(tree, storeSpecAnnotation),
            // 生成型は root 型・他 leaf 型を相互参照するため、階層内に internal が 1 つでも
            // あれば spec 全体を internal に落とす (StateVisibility の KDoc 参照)
            visibility =
                if (tree.allNodes.any { it.visibility == StateVisibility.INTERNAL }) {
                    StateVisibility.INTERNAL
                } else {
                    StateVisibility.PUBLIC
                },
        )

    validateStoreSpecModel(
        spec = spec,
        tree = tree,
        actionsDecl = actionsResolved?.decl,
        declaredActionDecls = allActionDecls.map { it.first },
    )
    return spec
}

/** Assembles a KSP-independent model node from the KSP-side tree + parsed handlers. */
private fun assembleNode(
    ksNode: KsStateNode,
    rootType: TypeRef,
    parsed: Map<KsStateNode, ParsedNodeHandlers>,
): StateNode {
    val handlers = parsed.getValue(ksNode)
    val children = ksNode.children.map { assembleNode(it, rootType, parsed) }
    return when {
        ksNode.path.isRoot ->
            RootNode(
                type = rootType,
                companionName = ksNode.companionName,
                children = children,
                props = ksNode.props,
                defaultName = handlers.defaultName ?: StateParent.DEFAULT_BLOCK_PARAM_NAME,
                actions = handlers.actions,
                recovers = handlers.recovers,
                exit = handlers.exit,
            )

        ksNode.isParent ->
            GroupNode(
                simpleName = ksNode.decl.simpleName.asString(),
                companionName = ksNode.companionName,
                children = children,
                props = ksNode.props,
                defaultName = handlers.defaultName ?: StateParent.DEFAULT_BLOCK_PARAM_NAME,
                actions = handlers.actions,
                recovers = handlers.recovers,
                exit = handlers.exit,
            )

        else ->
            LeafNode(
                simpleName = ksNode.decl.simpleName.asString(),
                declarationKind = requireNotNull(ksNode.declarationKind),
                companionName = ksNode.companionName,
                props = ksNode.props,
                enter = handlers.enter,
                exit = handlers.exit,
                actions = handlers.actions,
                recovers = handlers.recovers,
            )
    }
}

/** Parses `@StoreSpec(initial = [...])`. Elements must be concrete leaves only. */
context(diagnostics: StoreSpecDiagnostics)
private fun parseInitial(
    tree: KsStateTree,
    storeSpecAnnotation: KSAnnotation,
): List<StatePath> =
    storeSpecAnnotation.kClassArrayArgument("initial").mapNotNull { type ->
        val declaration = type.resolveToClassDeclaration()
        val target = declaration?.qualifiedName?.asString()?.let { tree.nodesByFqn[it] }
        if (target == null || target.isParent) {
            diagnostics.error(
                message =
                    "initial element '${declaration?.fullName ?: type.toString()}' is not a concrete leaf state of " +
                        "'${tree.rootDecl.fullName}'.",
                solution = "Use concrete leaf states of this sealed hierarchy as @StoreSpec(initial = [...]) elements.",
                node = storeSpecAnnotation,
            )
            null
        } else {
            target.path
        }
    }
