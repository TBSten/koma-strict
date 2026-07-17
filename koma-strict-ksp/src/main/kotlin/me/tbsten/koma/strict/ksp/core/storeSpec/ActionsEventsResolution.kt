package me.tbsten.koma.strict.ksp.core.storeSpec

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSNode
import me.tbsten.koma.strict.ksp.core.common.fullName
import me.tbsten.koma.strict.ksp.model.TypeRef

/** Resolved actions / events root (a [TypeRef] for the model + the KS declaration for analysis). */
internal class KsResolvedType(
    val decl: KSClassDeclaration,
    val ref: TypeRef,
)

/**
 * Resolution of the actions hierarchy root.
 * When explicit (`@StoreSpec(actions = ...)`), consistency (subtyping) against every
 * declaration is validated; otherwise the common sealed supertype of all `@OnAction`
 * type arguments is inferred.
 * Zero action declarations + no explicit value makes inference impossible -> an error
 * demands an explicit value.
 */
context(diagnostics: StoreSpecDiagnostics)
internal fun resolveActionsType(
    explicit: KSClassDeclaration?,
    declared: List<Pair<KSClassDeclaration, KSNode>>,
    storeSpecAnnotation: KSNode,
): KsResolvedType? =
    resolveHierarchyRoot(
        kind = "action",
        annotationLabel = "@OnAction",
        explicitParamLabel = "@StoreSpec(actions = ...)",
        explicit = explicit,
        declared = declared,
        storeSpecAnnotation = storeSpecAnnotation,
        onNoDeclarations = {
            diagnostics.error(
                message = "Cannot infer the actions type: no @OnAction declarations found in this @StoreSpec hierarchy.",
                solution = "Declare at least one @OnAction<...> handler, or specify the actions root explicitly with @StoreSpec(actions = MyAction::class).",
                node = storeSpecAnnotation,
            )
            null
        },
    )

/**
 * Resolution of the events hierarchy root. Same rules as actions, except that zero emit
 * declarations + no explicit value yields null (generated with `E = Nothing`) instead of
 * an error.
 */
context(diagnostics: StoreSpecDiagnostics)
internal fun resolveEventsType(
    explicit: KSClassDeclaration?,
    declared: List<Pair<KSClassDeclaration, KSNode>>,
    storeSpecAnnotation: KSNode,
): KsResolvedType? =
    resolveHierarchyRoot(
        kind = "event",
        annotationLabel = "emit",
        explicitParamLabel = "@StoreSpec(events = ...)",
        explicit = explicit,
        declared = declared,
        storeSpecAnnotation = storeSpecAnnotation,
        onNoDeclarations = { null },
    )

context(diagnostics: StoreSpecDiagnostics)
private fun resolveHierarchyRoot(
    kind: String,
    annotationLabel: String,
    explicitParamLabel: String,
    explicit: KSClassDeclaration?,
    declared: List<Pair<KSClassDeclaration, KSNode>>,
    storeSpecAnnotation: KSNode,
    onNoDeclarations: () -> KsResolvedType?,
): KsResolvedType? {
    if (explicit != null) {
        // 明示値と宣言の矛盾検査: 宣言された全型が明示 root の subtype であること
        declared.forEach { (declaration, node) ->
            if (!declaration.isSubtypeOfOrSelf(explicit)) {
                diagnostics.error(
                    message =
                        "The $kind type '${declaration.fullName}' (declared via $annotationLabel) is not a subtype of " +
                            "the explicit $kind root '${explicit.fullName}'.",
                    solution = "Make '${declaration.simpleName.asString()}' a subtype of '${explicit.simpleName.asString()}', or fix $explicitParamLabel.",
                    node = node,
                )
            }
        }
        return KsResolvedType(decl = explicit, ref = explicit.toTypeRef())
    }

    val distinctDeclared = declared.map { it.first }.distinctBy { it.qualifiedName?.asString() }
    if (distinctDeclared.isEmpty()) return onNoDeclarations()

    val common =
        distinctDeclared
            .first()
            .sealedCandidates()
            .firstOrNull { candidate -> distinctDeclared.all { it.isSubtypeOfOrSelf(candidate) } }
    if (common == null) {
        diagnostics.error(
            message =
                "Cannot infer a common sealed supertype for the declared $kind types: " +
                    distinctDeclared.joinToString(", ") { "'${it.fullName}'" } + ".",
            solution = "Make all $kind types share one sealed root, or specify it explicitly with $explicitParamLabel.",
            node = storeSpecAnnotation,
        )
        return null
    }
    return KsResolvedType(decl = common, ref = common.toTypeRef())
}
