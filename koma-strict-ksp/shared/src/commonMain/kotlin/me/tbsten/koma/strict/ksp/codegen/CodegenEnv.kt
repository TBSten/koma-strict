package me.tbsten.koma.strict.ksp.codegen

import me.tbsten.koma.strict.InternalKomaStrictApi
import me.tbsten.koma.strict.ksp.model.LeafNode
import me.tbsten.koma.strict.ksp.model.RootNode
import me.tbsten.koma.strict.ksp.model.StateDeclarationKind
import me.tbsten.koma.strict.ksp.model.StateNode
import me.tbsten.koma.strict.ksp.model.StatePath
import me.tbsten.koma.strict.ksp.model.StateProp
import me.tbsten.koma.strict.ksp.model.StoreSpec
import me.tbsten.koma.strict.ksp.model.TypeRef
import me.tbsten.koma.strict.ksp.model.nodeAt
import me.tbsten.koma.strict.ksp.naming.generatedTypePrefix
import me.tbsten.koma.strict.ksp.naming.implTypeName
import me.tbsten.koma.strict.ksp.naming.stateTypeReference

/**
 * Packages imported by default in Kotlin files.
 * Types in these packages can use shortened source references (`kotlin.String` -> `String`).
 * Shadowing by same-named types is not considered (accepted as a pathological case).
 */
@InternalKomaStrictApi
public val DEFAULT_IMPORT_PACKAGES: Set<String> =
    setOf(
        "kotlin",
        "kotlin.annotation",
        "kotlin.collections",
        "kotlin.comparisons",
        "kotlin.io",
        "kotlin.ranges",
        "kotlin.sequences",
        "kotlin.text",
    )

/**
 * Derived information from a [StoreSpec], shared throughout codegen.
 * All derivations are pure (KSP-independent) and passed as the context of the appendXxx family.
 */
internal class CodegenEnv(
    internal val spec: StoreSpec,
) {
    internal val root: RootNode = spec.root
    private val packageName: String = root.type.packageName

    /**
     * Visibility modifier prepended to generated top-level declarations (supporting types,
     * factories, the `states()` extension), inherited from the state declarations
     * (see [me.tbsten.koma.strict.ksp.model.StateVisibility]).
     * Members of generated classes stay `public` because the containing type restricts visibility.
     */
    internal val visibility: String = spec.visibility.keyword

    /** Source reference to the root state type (`LceState`). The `next` type of Reaction Transitions. */
    internal val rootRef: String = stateTypeReference(root, StatePath.root)

    /** Source reference to the actions hierarchy root. */
    internal val actionsRef: String = sourceTypeRef(spec.actionsType)

    /** Source reference to the events hierarchy root. Null = zero event declarations (`E = Nothing`). */
    internal val eventsRef: String? = spec.eventsType?.let(::sourceTypeRef)

    /** [eventsRef] as rendered in generated signatures (`Nothing` with zero event declarations). */
    internal val eventsOrNothingRef: String = eventsRef ?: "Nothing"

    /**
     * Fully qualified reference to koma's per-state builder type for the leaf at [leafPath]
     * (the receiver of the `state<X> { ... }` block, and of the per-state `configure`
     * escape hatch held by the leaf's Handlers).
     */
    internal fun stateHandlerConfigRef(leafPath: StatePath): String =
        "$KOMA_CORE_PACKAGE.StoreBuilder.StateHandlerConfig" +
            "<$rootRef, $actionsRef, $eventsOrNothingRef, ${stateRef(leafPath)}>"

    /**
     * Type reference as seen from sources in the declaring package.
     * Types in the same package / default-import packages are shortened; everything else is
     * fully qualified (the generated files' import boilerplate is `me.tbsten.koma.strict.*` only).
     */
    internal fun sourceTypeRef(type: TypeRef): String =
        if (type.packageName == packageName || type.packageName in DEFAULT_IMPORT_PACKAGES) {
            type.underPackageName
        } else {
            type.qualifiedName
        }

    internal fun stateRef(path: StatePath): String = stateTypeReference(root, path)

    internal fun prefix(path: StatePath): String = generatedTypePrefix(root, path)

    internal fun nodeAt(path: StatePath): StateNode =
        root.nodeAt(path)
            ?: error("BUG(koma-strict codegen): unknown state path '${path.dotJoined()}' in ${root.type.qualifiedName}")

    /** The (path, node) sequence from the root (inclusive) to the node at [path] (inclusive). */
    internal fun nodeChain(path: StatePath): List<Pair<StatePath, StateNode>> =
        (0..path.segments.size).map { i ->
            val p = StatePath(path.segments.take(i))
            p to nodeAt(p)
        }

    /**
     * Effective props of the node type at [path] (ancestors' shared props + its own props).
     * For same-named overrides, **the deepest (leaf-side) declaration decides the type** —
     * even with covariant overrides (narrowing `val data: Any` via `override val data: String`),
     * this is the type the generated Impl / factory must actually satisfy. Argument order
     * preserves the position of the first (ancestor-side) declaration.
     */
    internal fun effectiveProps(path: StatePath): List<StateProp> {
        val chainProps = nodeChain(path).flatMap { (_, node) -> node.props }
        return chainProps
            .distinctBy { it.name }
            .map { first -> chainProps.last { it.name == first.name } }
    }

    /** Construction arguments of a target leaf (factory / constructor parameters, in declaration order). */
    internal fun constructionParams(path: StatePath): List<StateProp> {
        val leaf = nodeAt(path) as LeafNode
        return when (leaf.declarationKind) {
            // interface 宣言は生成 factory / Impl の引数 = 実効 prop
            StateDeclarationKind.INTERFACE -> effectiveProps(path)
            // data class は自身の primary constructor パラメータが全て (継承 prop も override で含む)
            StateDeclarationKind.DATA_CLASS -> leaf.props
            StateDeclarationKind.DATA_OBJECT -> emptyList()
        }
    }

    /** Construction expression of a target leaf. Interfaces go through the public factory (direct Impl construction only when there is no companion). */
    internal fun constructionExpr(
        path: StatePath,
        args: String,
    ): String {
        val leaf = nodeAt(path) as LeafNode
        return when (leaf.declarationKind) {
            StateDeclarationKind.DATA_OBJECT -> stateRef(path)
            StateDeclarationKind.DATA_CLASS -> "${stateRef(path)}($args)"
            StateDeclarationKind.INTERFACE ->
                when {
                    leaf.hasCompanion -> "${stateRef(path)}($args)"
                    constructionParams(path).isEmpty() -> implTypeName(prefix(path))
                    else -> "${implTypeName(prefix(path))}($args)"
                }
        }
    }
}

/** The type part of a prop's declaration rendering (`name: Type?` form). */
internal fun StateProp.renderedType(): String = if (isNullable) "$type?" else type
