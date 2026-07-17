package me.tbsten.koma.strict.ksp.core.storeSpec

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Visibility
import me.tbsten.koma.strict.ksp.core.common.fullName
import me.tbsten.koma.strict.ksp.model.StateDeclarationKind
import me.tbsten.koma.strict.ksp.model.StatePath
import me.tbsten.koma.strict.ksp.model.StateProp
import me.tbsten.koma.strict.ksp.model.StateVisibility
import me.tbsten.koma.strict.ksp.model.TypeRef

/** KSP-side node of the state declaration tree (keeps the KS declaration for model construction and diagnostic positioning). */
internal class KsStateNode(
    val decl: KSClassDeclaration,
    val path: StatePath,
    /** Whether this is sealed (root / intermediate sealed). */
    val isParent: Boolean,
    /** Non-null for leaves only. */
    val declarationKind: StateDeclarationKind?,
    /** Actual name of the hand-written companion object (null = none). Even unnamed companions can end up with a different name due to parsing quirks (see the warning below). */
    val companionName: String?,
    /** Visibility of the declaration (public / internal only; anything else is a fallback with an error already reported as a v1 restriction). */
    val visibility: StateVisibility,
    val props: List<StateProp>,
    val children: List<KsStateNode>,
)

/** KSP-side tree and lookups for one @StoreSpec root. */
internal class KsStateTree(
    val rootDecl: KSClassDeclaration,
    val rootType: TypeRef,
    val root: KsStateNode,
) {
    /** Pre-order (source declaration order). */
    val allNodes: List<KsStateNode> =
        buildList {
            fun visit(node: KsStateNode) {
                add(node)
                node.children.forEach(::visit)
            }
            visit(root)
        }

    val nodesByFqn: Map<String, KsStateNode> =
        allNodes.mapNotNull { node -> node.decl.qualifiedName?.asString()?.let { it to node } }.toMap()

    val nodesByPath: Map<StatePath, KsStateNode> = allNodes.associateBy { it.path }
}

/**
 * Builds the state declaration tree from a sealed root annotated with @StoreSpec
 * (pass A: structure only). Handler annotation parsing is in HandlerParsing.kt (pass B).
 */
context(diagnostics: StoreSpecDiagnostics)
internal fun parseStateTree(rootDecl: KSClassDeclaration): KsStateTree? {
    if (Modifier.SEALED !in rootDecl.modifiers) {
        diagnostics.error(
            message = "@StoreSpec must be applied to a sealed interface (or sealed class), but '${rootDecl.fullName}' is not sealed.",
            solution = "Declare the state root as `sealed interface ${rootDecl.simpleName.asString()}` and nest all states inside it.",
            node = rootDecl,
        )
        return null
    }
    val contextPackage = rootDecl.packageName.asString()
    val root = buildNode(rootDecl, StatePath.root, contextPackage)
    return KsStateTree(rootDecl = rootDecl, rootType = rootDecl.toTypeRef(), root = root)
}

context(diagnostics: StoreSpecDiagnostics)
private fun buildNode(
    decl: KSClassDeclaration,
    path: StatePath,
    contextPackage: String,
): KsStateNode {
    if (decl.typeParameters.isNotEmpty()) {
        diagnostics.error(
            message = "Type-parameterized state '${decl.fullName}' is not supported (v1 restriction).",
            solution = "Remove the type parameters from '${decl.simpleName.asString()}' (e.g. hold the value as a concrete-typed property).",
            node = decl,
        )
    }

    val isParent = Modifier.SEALED in decl.modifiers
    val declarationKind =
        if (isParent) {
            null
        } else {
            when (decl.classKind) {
                ClassKind.INTERFACE -> StateDeclarationKind.INTERFACE
                ClassKind.OBJECT -> StateDeclarationKind.DATA_OBJECT
                ClassKind.CLASS -> StateDeclarationKind.DATA_CLASS
                else -> {
                    diagnostics.error(
                        message = "State '${decl.fullName}' must be declared as an interface, a (data) class, or a (data) object (found: ${decl.classKind}).",
                        solution = "Use one of the two supported state declaration forms: `interface` (recommended) or `data class` / `data object`.",
                        node = decl,
                    )
                    StateDeclarationKind.DATA_CLASS
                }
            }
        }

    val children =
        if (isParent) {
            checkSealedSubclassesAreNested(decl)
            directChildStates(decl).map { child ->
                buildNode(child, path + child.simpleName.asString(), contextPackage)
            }
        } else {
            emptyList()
        }

    return KsStateNode(
        decl = decl,
        path = path,
        isParent = isParent,
        declarationKind = declarationKind,
        companionName = companionNameOf(decl),
        visibility = stateVisibilityOf(decl),
        props = declaredProps(decl, isParent, declarationKind, contextPackage),
        children = children,
    )
}

/**
 * Visibility of a state declaration, inherited by the generated artifacts
 * (doc, visibility policy of generated artifacts).
 * v1 supports public / internal only — private / protected / local are explicitly rejected
 * because the top-level generated types could not reference the state.
 */
context(diagnostics: StoreSpecDiagnostics)
private fun stateVisibilityOf(decl: KSClassDeclaration): StateVisibility =
    when (val visibility = decl.getVisibility()) {
        Visibility.PUBLIC -> StateVisibility.PUBLIC
        Visibility.INTERNAL -> StateVisibility.INTERNAL
        else -> {
            diagnostics.error(
                message =
                    "State '${decl.fullName}' has unsupported visibility '${visibility.name.lowercase()}' " +
                        "(v1 restriction: only public / internal states are supported).",
                solution =
                    "Generated support types are top-level declarations that inherit the state's visibility. " +
                        "Declare '${decl.simpleName.asString()}' as public or internal.",
                node = decl,
            )
            StateVisibility.INTERNAL // 解析続行用の fallback (hasErrors により生成はされない)
        }
    }

/**
 * When a declaration starting with a soft keyword modifier (`data` / `sealed`, etc.) is placed
 * immediately after a `companion object`, kotlinc consumes that modifier as the companion's
 * **name** (`companion object` + `data object Home` -> a companion named `data` + a non-data
 * `object Home`). This parse is almost certainly unintended, so we warn
 * (empirically verified on Kotlin 2.4.0).
 */
private val suspiciousCompanionNames =
    setOf(
        "data", "value", "sealed", "open", "abstract", "inner", "enum", "annotation",
        "inline", "lateinit", "operator", "infix", "external", "suspend", "const",
        "private", "public", "internal", "protected",
    )

context(diagnostics: StoreSpecDiagnostics)
private fun companionNameOf(decl: KSClassDeclaration): String? {
    val companion =
        decl.declarations.filterIsInstance<KSClassDeclaration>().firstOrNull { it.isCompanionObject }
            ?: return null
    val name = companion.simpleName.asString()
    if (name in suspiciousCompanionNames) {
        diagnostics.warn(
            message =
                "koma-strict: the companion object of '${decl.fullName}' is named '$name'. " +
                    "An unnamed `companion object` immediately followed by a `$name ...` declaration is parsed " +
                    "by kotlinc as a companion NAMED '$name' (the modifier is consumed by the parser). " +
                    "If this is unintended, declare the companion last or give it an explicit body: `companion object {}`.",
            node = companion,
        )
    }
    return name
}

/** Class declarations declared directly in the body of [parent] that have [parent] as a direct supertype (source declaration order). */
private fun directChildStates(parent: KSClassDeclaration): List<KSClassDeclaration> {
    val parentFqn = parent.qualifiedName?.asString()
    return parent.declarations
        .filterIsInstance<KSClassDeclaration>()
        .filter { child ->
            !child.isCompanionObject &&
                child.superTypes.any { superType ->
                    superType.resolve().resolveToClassDeclaration()?.qualifiedName?.asString() == parentFqn
                }
        }.toList()
}

/**
 * Validates that sealed subtypes are nested directly in the body of [parent].
 * Silently ignoring subtypes outside the nesting would create a silent gap
 * ("declared but never appears in states()"), so the tree shape (path = nesting structure)
 * is enforced with an error.
 */
context(diagnostics: StoreSpecDiagnostics)
private fun checkSealedSubclassesAreNested(parent: KSClassDeclaration) {
    val parentFqn = parent.qualifiedName?.asString() ?: return
    parent.getSealedSubclasses().forEach { subclass ->
        if (subclass.parentDeclaration?.qualifiedName?.asString() != parentFqn) {
            diagnostics.error(
                message = "State '${subclass.fullName}' extends sealed state '${parent.fullName}' but is not nested directly inside it.",
                solution = "koma-strict derives the state tree from nesting. Move '${subclass.simpleName.asString()}' into the body of '${parent.simpleName.asString()}'.",
                node = subclass,
            )
        }
    }
}

/** Props declared by the node itself: abstract props for interface / sealed, primary constructor parameters for data classes. */
private fun declaredProps(
    decl: KSClassDeclaration,
    isParent: Boolean,
    declarationKind: StateDeclarationKind?,
    contextPackage: String,
): List<StateProp> =
    when {
        isParent || declarationKind == StateDeclarationKind.INTERFACE ->
            decl
                .getDeclaredProperties()
                .filter { it.isAbstract() }
                .map { property ->
                    buildStateProp(property.simpleName.asString(), property.type.resolve(), contextPackage)
                }.toList()

        declarationKind == StateDeclarationKind.DATA_CLASS ->
            decl.primaryConstructor
                ?.parameters
                .orEmpty()
                .mapNotNull { parameter ->
                    parameter.name?.asString()?.let { name ->
                        buildStateProp(name, parameter.type.resolve(), contextPackage)
                    }
                }

        else -> emptyList()
    }
