package me.tbsten.koma.strict.ksp.core.storeSpec

import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSNode
import me.tbsten.koma.strict.DefaultName
import me.tbsten.koma.strict.OnAction
import me.tbsten.koma.strict.OnEnter
import me.tbsten.koma.strict.OnExit
import me.tbsten.koma.strict.OnRecover
import me.tbsten.koma.strict.Stay
import me.tbsten.koma.strict.ksp.core.common.fullName
import me.tbsten.koma.strict.ksp.model.ActionHandler
import me.tbsten.koma.strict.ksp.model.EnterHandler
import me.tbsten.koma.strict.ksp.model.EventRef
import me.tbsten.koma.strict.ksp.model.ExitHandler
import me.tbsten.koma.strict.ksp.model.RecoverHandler
import me.tbsten.koma.strict.ksp.model.StatePath
import me.tbsten.koma.strict.ksp.model.TransitionSpec

private val onEnterFqn = OnEnter::class.fullName
private val onExitFqn = OnExit::class.fullName
private val onActionFqn = OnAction::class.fullName
private val onRecoverFqn = OnRecover::class.fullName
private val defaultNameFqn = DefaultName::class.fullName
private val stayFqn = Stay::class.fullName

/** Handler-annotation parsing result for one node (model values + KS references for validation). */
internal class ParsedNodeHandlers(
    val defaultName: String?,
    val enter: EnterHandler?,
    val exit: ExitHandler?,
    val actions: List<ActionHandler>,
    val recovers: List<RecoverHandler>,
    /** For actions / events inference and subtype validation: the declared types and diagnostic positions. */
    val actionDecls: List<Pair<KSClassDeclaration, KSNode>>,
    val eventDecls: List<Pair<KSClassDeclaration, KSNode>>,
)

/** Parses a node's handler annotations (pass B). Enumeration order is the source annotation order. */
context(diagnostics: StoreSpecDiagnostics)
internal fun parseNodeHandlers(
    tree: KsStateTree,
    node: KsStateNode,
    contextPackage: String,
): ParsedNodeHandlers {
    var defaultName: String? = null
    var enter: EnterHandler? = null
    var exit: ExitHandler? = null
    val actions = mutableListOf<ActionHandler>()
    val recovers = mutableListOf<RecoverHandler>()
    val actionDecls = mutableListOf<Pair<KSClassDeclaration, KSNode>>()
    val eventDecls = mutableListOf<Pair<KSClassDeclaration, KSNode>>()

    node.decl.annotations.forEach { annotation ->
        when (annotation.annotationType.resolve().declaration.qualifiedName?.asString()) {
            onEnterFqn -> {
                if (node.isParent) {
                    diagnostics.error(
                        message = "@OnEnter on '${node.decl.fullName}' is invalid: it can only be declared on a concrete leaf state.",
                        solution = "Declare @OnEnter on each concrete leaf state that should react to being entered.",
                        node = annotation,
                    )
                    return@forEach
                }
                val transition = parseTransition(tree, annotation) ?: return@forEach
                enter = EnterHandler(transition = transition, emits = parseEmits(annotation, contextPackage, eventDecls))
            }

            onExitFqn -> {
                // @OnExit は nextState パラメータ自体を持たない (koma の ExitScope が遷移不可)
                exit = ExitHandler(emits = parseEmits(annotation, contextPackage, eventDecls))
            }

            onActionFqn -> {
                val actionDecl = annotation.typeArgumentClassDeclaration()
                if (actionDecl == null) {
                    diagnostics.error(
                        message = "Cannot resolve the action type argument of @OnAction on '${node.decl.fullName}'.",
                        solution = "Specify a concrete action type: `@OnAction<MyAction.Xxx>(...)`.",
                        node = annotation,
                    )
                    return@forEach
                }
                // transition の解析に失敗しても action 型自体は actions 推論の入力に使える
                actionDecls += actionDecl to annotation
                if (reportTypeParameterizedDecl(actionDecl, role = "action", annotationLabel = "@OnAction", node = annotation)) {
                    return@forEach
                }
                val transition = parseTransition(tree, annotation) ?: return@forEach
                actions +=
                    ActionHandler(
                        action = actionDecl.toTypeRef(),
                        transition = transition,
                        emits = parseEmits(annotation, contextPackage, eventDecls),
                    )
            }

            onRecoverFqn -> {
                val exceptionDecl = annotation.typeArgumentClassDeclaration()
                if (exceptionDecl == null) {
                    diagnostics.error(
                        message = "Cannot resolve the exception type argument of @OnRecover on '${node.decl.fullName}'.",
                        solution = "Specify a concrete exception type: `@OnRecover<MyException>(...)`.",
                        node = annotation,
                    )
                    return@forEach
                }
                if (reportTypeParameterizedDecl(exceptionDecl, role = "exception", annotationLabel = "@OnRecover", node = annotation)) {
                    return@forEach
                }
                if (!exceptionDecl.isExceptionSubtype()) {
                    diagnostics.error(
                        message =
                            "@OnRecover type argument '${exceptionDecl.fullName}' is not a subtype of kotlin.Exception.",
                        solution =
                            "koma's recover only catches Exception (CancellationException / Error are excluded). " +
                                "Use an Exception subtype as the type argument.",
                        node = annotation,
                    )
                    return@forEach
                }
                val transition = parseTransition(tree, annotation) ?: return@forEach
                recovers +=
                    RecoverHandler(
                        exception = exceptionDecl.toTypeRef(),
                        transition = transition,
                        emits = parseEmits(annotation, contextPackage, eventDecls),
                    )
            }

            defaultNameFqn -> {
                if (!node.isParent) {
                    diagnostics.error(
                        message = "@DefaultName on '${node.decl.fullName}' is invalid: it can only be applied to the sealed root or an intermediate sealed state.",
                        solution = "Move @DefaultName to the sealed state that owns the shared (default) block.",
                        node = annotation,
                    )
                } else {
                    defaultName = annotation.argumentValue("name") as? String
                }
            }
        }
    }

    reportSameNodeDuplicates(node, actions, recovers)

    return ParsedNodeHandlers(
        defaultName = defaultName,
        enter = enter,
        exit = exit,
        actions = actions,
        recovers = recovers,
        actionDecls = actionDecls,
        eventDecls = eventDecls,
    )
}

/** Validation of duplicate declarations within one node (same (state, action) pair / same exception type). */
context(diagnostics: StoreSpecDiagnostics)
private fun reportSameNodeDuplicates(
    node: KsStateNode,
    actions: List<ActionHandler>,
    recovers: List<RecoverHandler>,
) {
    actions
        .groupBy { it.action.qualifiedName }
        .filterValues { it.size > 1 }
        .keys
        .forEach { actionFqn ->
            diagnostics.error(
                message = "Duplicate @OnAction<$actionFqn> on '${node.decl.fullName}': the same (state, action) pair is declared more than once.",
                solution = "Merge the declarations into one @OnAction<$actionFqn>(nextState = [...]) with all transition targets.",
                node = node.decl,
            )
        }
    recovers
        .groupBy { it.exception.qualifiedName }
        .filterValues { it.size > 1 }
        .keys
        .forEach { exceptionFqn ->
            diagnostics.error(
                message = "Duplicate @OnRecover<$exceptionFqn> on '${node.decl.fullName}': the same exception type is declared more than once.",
                solution = "Merge the declarations into one @OnRecover<$exceptionFqn>(nextState = [...]).",
                node = node.decl,
            )
        }
}

/**
 * v1 rejection diagnostic for type-parameterized action / event / exception types
 * (symmetric with the v1 restriction on states).
 * Converting to TypeRef (toTypeRef) drops the type arguments, so letting a generic type
 * through would emit generated code with raw references (`action<Paged>` /
 * `val action: Paged`, etc.) — uncompilable partial output.
 * true = reported (the caller must skip the corresponding handler / emit element).
 */
context(diagnostics: StoreSpecDiagnostics)
private fun reportTypeParameterizedDecl(
    decl: KSClassDeclaration,
    role: String,
    annotationLabel: String,
    node: KSNode,
): Boolean {
    if (decl.typeParameters.isEmpty()) return false
    diagnostics.error(
        message = "Type-parameterized $role '${decl.fullName}' is not supported in $annotationLabel (v1 restriction).",
        solution =
            "Generated code cannot re-render the type arguments. " +
                "Use a non-generic $role type (e.g. hold the value as a concrete-typed property).",
        node = node,
    )
    return true
}

/**
 * Parses `nextState = [...]`. Elements must be concrete leaves of the same sealed hierarchy
 * or `Stay::class` only. An empty list (or omission) is sugar for `[Stay::class]`
 * (desugared by [TransitionSpec.of]).
 */
context(diagnostics: StoreSpecDiagnostics)
private fun parseTransition(
    tree: KsStateTree,
    annotation: KSAnnotation,
): TransitionSpec? {
    var declaredStay = false
    var failed = false
    val targets = mutableListOf<StatePath>()
    annotation.kClassArrayArgument("nextState").forEach { type ->
        val declaration = type.resolveToClassDeclaration()
        val fqn = declaration?.qualifiedName?.asString()
        if (fqn == stayFqn) {
            declaredStay = true
            return@forEach
        }
        val target = fqn?.let { tree.nodesByFqn[it] }
        if (target == null || target.isParent) {
            val label = fqn ?: type.toString()
            val detail =
                if (target?.isParent == true) {
                    "is an intermediate sealed state (transitions must target concrete leaves)"
                } else {
                    "is not a state of '${tree.rootDecl.fullName}'"
                }
            diagnostics.error(
                message = "nextState element '$label' $detail.",
                solution = "Use concrete leaf states of the same sealed hierarchy (or Stay::class) as nextState elements.",
                node = annotation,
            )
            failed = true
        } else {
            targets += target.path
        }
    }
    if (failed) return null
    return TransitionSpec.of(targets = targets, declaredStay = declaredStay)
}

/** Parses `emit = [...]`. Also resolves the events' construction info (object / primary constructor parameters). */
context(diagnostics: StoreSpecDiagnostics)
private fun parseEmits(
    annotation: KSAnnotation,
    contextPackage: String,
    collectEventDecls: MutableList<Pair<KSClassDeclaration, KSNode>>,
): List<EventRef> =
    annotation.kClassArrayArgument("emit").mapNotNull { type ->
        val declaration = type.resolveToClassDeclaration()
        if (declaration == null) {
            diagnostics.error(
                message = "Cannot resolve emit element '$type'.",
                solution = "Use concrete event classes (or objects) as emit elements.",
                node = annotation,
            )
            return@mapNotNull null
        }
        collectEventDecls += declaration to annotation
        if (reportTypeParameterizedDecl(declaration, role = "event", annotationLabel = "emit = [...]", node = annotation)) {
            return@mapNotNull null
        }
        val isObject = declaration.classKind == ClassKind.OBJECT
        EventRef(
            type = declaration.toTypeRef(),
            isObject = isObject,
            params =
                if (isObject) {
                    emptyList()
                } else {
                    declaration.primaryConstructor?.parameters.orEmpty().mapNotNull { parameter ->
                        parameter.name?.asString()?.let { name ->
                            buildStateProp(name, parameter.type.resolve(), contextPackage)
                        }
                    }
                },
        )
    }
