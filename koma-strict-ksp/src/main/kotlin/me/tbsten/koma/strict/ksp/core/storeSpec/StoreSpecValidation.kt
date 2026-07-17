package me.tbsten.koma.strict.ksp.core.storeSpec

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Modifier
import me.tbsten.koma.strict.ksp.core.common.fullName
import me.tbsten.koma.strict.ksp.model.GroupNode
import me.tbsten.koma.strict.ksp.model.LeafNode
import me.tbsten.koma.strict.ksp.model.StateDeclarationKind
import me.tbsten.koma.strict.ksp.model.StateNode
import me.tbsten.koma.strict.ksp.model.StateParent
import me.tbsten.koma.strict.ksp.model.StatePath
import me.tbsten.koma.strict.ksp.model.StoreSpec
import me.tbsten.koma.strict.ksp.model.TransitionHandlerDecl
import me.tbsten.koma.strict.ksp.model.hasAnyHandlerDeclarations
import me.tbsten.koma.strict.ksp.model.hasOwnHandlerDeclarations
import me.tbsten.koma.strict.ksp.model.leavesWithPath
import me.tbsten.koma.strict.ksp.model.nodesWithPath
import me.tbsten.koma.strict.ksp.naming.stateParamName
import me.tbsten.koma.strict.ksp.options.DeadActionSeverity
import me.tbsten.koma.strict.ksp.options.KomaStrictOptions

// 構築済み StoreSpec model に対する構造検証 (doc/internal/generate-strict-store-factory-dsl.md
// §KSP 静的検証)。診断位置は KsStateTree 経由で元の KS 宣言に張る。

/** Structural validation of the whole model. Violations are reported to [StoreSpecDiagnostics] (the caller skips generation). */
context(diagnostics: StoreSpecDiagnostics, options: KomaStrictOptions)
internal fun validateStoreSpecModel(
    spec: StoreSpec,
    tree: KsStateTree,
    actionsDecl: KSClassDeclaration?,
    declaredActionDecls: List<KSClassDeclaration>,
) {
    validateInheritedDuplicates(spec, tree)
    validateCompanionRequired(spec, tree)
    validateDefaultParamNameClash(spec, tree)
    validateReachability(spec, tree)
    validateDeadActions(actionsDecl, declaredActionDecls)
}

/** Validation of ancestor/descendant duplicate declarations (same action / same exception type / exit). */
context(diagnostics: StoreSpecDiagnostics)
private fun validateInheritedDuplicates(
    spec: StoreSpec,
    tree: KsStateTree,
) {
    fun visit(
        path: StatePath,
        node: StateNode,
        inheritedActions: Map<String, StatePath>,
        inheritedRecovers: Map<String, StatePath>,
        inheritedExit: StatePath?,
    ) {
        val decl = tree.nodesByPath.getValue(path).decl
        node.actions.forEach { action ->
            inheritedActions[action.action.qualifiedName]?.let { ownerPath ->
                diagnostics.error(
                    message =
                        "@OnAction<${action.action.qualifiedName}> on '${decl.fullName}' duplicates the declaration on its ancestor " +
                            "'${ancestorLabel(spec, ownerPath)}' (shared action declarations expand to all leaves).",
                    solution = "Declare each action either on one ancestor (shared) or on individual leaves, not both.",
                    node = decl,
                )
            }
        }
        node.recovers.forEach { recover ->
            inheritedRecovers[recover.exception.qualifiedName]?.let { ownerPath ->
                diagnostics.error(
                    message =
                        "@OnRecover<${recover.exception.qualifiedName}> on '${decl.fullName}' duplicates the declaration on its ancestor " +
                            "'${ancestorLabel(spec, ownerPath)}'.",
                    solution = "Declare each exception type either on one ancestor (shared) or on individual leaves, not both.",
                    node = decl,
                )
            }
        }
        if (node.exit != null && inheritedExit != null) {
            diagnostics.error(
                message =
                    "@OnExit on '${decl.fullName}' duplicates the declaration on its ancestor " +
                        "'${ancestorLabel(spec, inheritedExit)}' (koma's behavior for multiple exit blocks is unverified).",
                solution = "Declare @OnExit either on one ancestor (shared) or on individual leaves, not both.",
                node = decl,
            )
        }
        if (node is StateParent) {
            val nextActions = inheritedActions + node.actions.associate { it.action.qualifiedName to path }
            val nextRecovers = inheritedRecovers + node.recovers.associate { it.exception.qualifiedName to path }
            val nextExit = inheritedExit ?: path.takeIf { node.exit != null }
            node.children.forEach { child ->
                visit(path + child.simpleName, child, nextActions, nextRecovers, nextExit)
            }
        }
    }
    visit(StatePath.root, spec.root, emptyMap(), emptyMap(), null)
}

private fun ancestorLabel(
    spec: StoreSpec,
    path: StatePath,
): String = if (path.isRoot) spec.root.type.qualifiedName else path.dotJoined()

/**
 * Validation that a companion is required.
 * Nodes with own declarations need it for the generated `actions()` extension; intermediate
 * sealed nodes whose subtree has declarations need it for the generated `states()` bundling
 * extension (with value-passing params, the companion extension is the only way to construct
 * the GroupHandlers value). data objects are excluded because Kotlin does not allow them to
 * have a companion; extensions are generated on the object itself instead.
 */
context(diagnostics: StoreSpecDiagnostics)
private fun validateCompanionRequired(
    spec: StoreSpec,
    tree: KsStateTree,
) {
    spec.root.nodesWithPath().forEach { (path, node) ->
        if (node.hasCompanion) return@forEach
        val isDataObject = node is LeafNode && node.declarationKind == StateDeclarationKind.DATA_OBJECT
        if (node.hasOwnHandlerDeclarations() && !isDataObject) {
            val decl = tree.nodesByPath.getValue(path).decl
            diagnostics.error(
                message = "State '${decl.fullName}' declares handlers but has no companion object.",
                solution =
                    "Generated extensions (actions() / states() / factory) attach to the companion object. " +
                        "Add `companion object` to '${decl.simpleName.asString()}'.",
                node = decl,
            )
        } else if (node is GroupNode && node.hasAnyHandlerDeclarations()) {
            val decl = tree.nodesByPath.getValue(path).decl
            diagnostics.error(
                message =
                    "Intermediate state '${decl.fullName}' has handler declarations in its subtree " +
                        "but no companion object.",
                solution =
                    "The generated states() bundling extension attaches to the companion object. " +
                        "Add `companion object` to '${decl.simpleName.asString()}'.",
                node = decl,
            )
        }
    }
}

/** Collision validation between state param names (decapitalized) and the default block name (avoidable via `@DefaultName`). */
context(diagnostics: StoreSpecDiagnostics)
private fun validateDefaultParamNameClash(
    spec: StoreSpec,
    tree: KsStateTree,
) {
    spec.root.nodesWithPath().forEach { (path, node) ->
        val parent = node as? StateParent ?: return@forEach
        if (!parent.hasOwnHandlerDeclarations()) return@forEach
        parent.children
            .filter { it.hasAnyHandlerDeclarations() }
            .forEach { child ->
                if (stateParamName(child.simpleName) == parent.defaultName) {
                    val childDecl = tree.nodesByPath.getValue(path + child.simpleName).decl
                    diagnostics.error(
                        message =
                            "State '${childDecl.fullName}' collides with the shared (default) block: both become the " +
                                "states() parameter '${parent.defaultName}'.",
                        solution =
                            "Rename the shared block with @DefaultName(\"...\") on the parent sealed state, " +
                                "or rename the state.",
                        node = childDecl,
                    )
                }
            }
    }
}

/** Unreachable-state warning. With no `initial` declared there is no starting point, so the analysis is skipped. */
context(diagnostics: StoreSpecDiagnostics)
private fun validateReachability(
    spec: StoreSpec,
    tree: KsStateTree,
) {
    if (spec.initial.isEmpty()) return
    val nodesByPath = spec.root.nodesWithPath().toMap()
    val leaves = spec.root.leavesWithPath()

    // leaf からの遷移先 = 自身の handler + 祖先の共有 handler (全 leaf へ展開される) の全 targets
    fun targetsFrom(leafPath: StatePath): Set<StatePath> =
        (0..leafPath.segments.size)
            .mapNotNull { i -> nodesByPath[StatePath(leafPath.segments.take(i))] }
            .flatMap { node ->
                buildList<TransitionHandlerDecl> {
                    (node as? LeafNode)?.enter?.let(::add)
                    addAll(node.actions)
                    addAll(node.recovers)
                }
            }.flatMap { it.transition.targets }
            .toSet()

    val reachable = spec.initial.toMutableSet()
    var frontier = spec.initial.toList()
    while (frontier.isNotEmpty()) {
        frontier = frontier.flatMap { targetsFrom(it) }.filter { reachable.add(it) }
    }

    leaves.forEach { (path, _) ->
        if (path !in reachable) {
            val decl = tree.nodesByPath.getValue(path).decl
            diagnostics.warn(
                message =
                    "koma-strict: state '${decl.fullName}' is unreachable from the declared initial state(s) " +
                        spec.initial.joinToString(", ") { "'${it.dotJoined()}'" } + ".",
                node = decl,
            )
        }
    }
}

/**
 * Dead-action diagnostic (an action handled by no state).
 * Warning by default; promoted to error via `koma.strict.deadActionSeverity=ERROR`.
 * Skipped when the actions root is not sealed (cannot be enumerated).
 */
context(diagnostics: StoreSpecDiagnostics, options: KomaStrictOptions)
private fun validateDeadActions(
    actionsDecl: KSClassDeclaration?,
    declaredActionDecls: List<KSClassDeclaration>,
) {
    if (actionsDecl == null || Modifier.SEALED !in actionsDecl.modifiers) return
    val declaredFqns = declaredActionDecls.mapNotNull { it.qualifiedName?.asString() }.toSet()

    fun concreteActionTypes(declaration: KSClassDeclaration): List<KSClassDeclaration> =
        if (Modifier.SEALED in declaration.modifiers) {
            declaration.getSealedSubclasses().flatMap { concreteActionTypes(it) }.toList()
        } else {
            listOf(declaration)
        }

    concreteActionTypes(actionsDecl).forEach { actionLeaf ->
        val handled =
            declaredFqns.any { declaredFqn -> actionLeaf.isSubtypeOfOrSelf(declaredFqn) }
        if (!handled) {
            val message =
                "koma-strict: action '${actionLeaf.fullName}' is never handled by any state (dead action)."
            when (options.deadActionSeverity) {
                DeadActionSeverity.WARNING -> diagnostics.warn(message, actionLeaf)
                DeadActionSeverity.ERROR ->
                    diagnostics.error(
                        message = message,
                        solution =
                            "Handle it with @OnAction<${actionLeaf.simpleName.asString()}>(...) on some state, " +
                                "remove the action, or set ksp arg koma.strict.deadActionSeverity=WARNING.",
                        node = actionLeaf,
                    )
            }
        }
    }
}
