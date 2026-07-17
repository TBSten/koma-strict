package me.tbsten.koma.strict.ksp.model

import me.tbsten.koma.strict.InternalKomaStrictApi
import me.tbsten.koma.strict.ksp.model.StateParent.Companion.DEFAULT_BLOCK_PARAM_NAME

/**
 * A node of the state declaration tree: root ([RootNode]) / intermediate sealed ([GroupNode]) /
 * concrete leaf ([LeafNode]).
 *
 * Every list (children / props / actions / recovers) preserves **source declaration order** —
 * this is the source of the param-order guarantee of the generated `states()` / `actions()`
 * (the "just fill them in the order you read the declarations" intuition).
 */
@InternalKomaStrictApi
public sealed interface StateNode {
    public val simpleName: String

    /**
     * Name of the hand-written `companion object` (null = no companion).
     * It is the attach point of generated extensions (`actions()` / `states()` / factory);
     * unnamed companions are `"Companion"`.
     *
     * The name is kept to work around a kotlinc parsing quirk: when an unnamed
     * `companion object` is immediately followed by a declaration starting with a soft
     * keyword modifier such as `data object ...`, the modifier is consumed as the
     * companion's **name** (`companion object data` + `object Home`). In that case
     * `X.Companion` becomes unresolved, so generated extensions build the receiver
     * from the actual name.
     */
    public val companionName: String?

    /** Whether a hand-written `companion object` exists. For interface leaves without a companion, factory generation is skipped and the Impl becomes internal. */
    public val hasCompanion: Boolean
        get() = companionName != null

    /** Props declared by this node itself (shared props of an intermediate sealed type belong to the declaring node). */
    public val props: List<StateProp>

    /** `@OnExit` declaration. */
    public val exit: ExitHandler?

    /** `@OnAction` declarations. On the root / an intermediate sealed type, these are scope-shared actions (= the default block). */
    public val actions: List<ActionHandler>

    /** `@OnRecover` declarations. On the root / an intermediate sealed type, these are scope-shared recovers. */
    public val recovers: List<RecoverHandler>
}

/** A node with child states (root / intermediate sealed). */
@InternalKomaStrictApi
public sealed interface StateParent : StateNode {
    /** Child nodes (source declaration order). The source of the generated `states()` param order. */
    public val children: List<StateNode>

    /** Param name of the default block, with `@DefaultName` already applied (defaults to [DEFAULT_BLOCK_PARAM_NAME]). */
    public val defaultName: String

    public companion object {
        /** Default-block param name when `@DefaultName` is not specified (= the reserved param name). */
        public const val DEFAULT_BLOCK_PARAM_NAME: String = "default"
    }
}

/** The sealed root annotated with `@StoreSpec`. */
@InternalKomaStrictApi
public data class RootNode(
    /** Reference to the root type. The source of the generated-type prefix, generated file names, and the `@JvmName` of `states()`. */
    val type: TypeRef,
    override val companionName: String?,
    override val children: List<StateNode>,
    override val props: List<StateProp> = emptyList(),
    override val defaultName: String = DEFAULT_BLOCK_PARAM_NAME,
    override val actions: List<ActionHandler> = emptyList(),
    override val recovers: List<RecoverHandler> = emptyList(),
    override val exit: ExitHandler? = null,
) : StateParent {
    override val simpleName: String
        get() = type.simpleName
}

/** An intermediate sealed node. */
@InternalKomaStrictApi
public data class GroupNode(
    override val simpleName: String,
    override val companionName: String?,
    override val children: List<StateNode>,
    override val props: List<StateProp> = emptyList(),
    override val defaultName: String = DEFAULT_BLOCK_PARAM_NAME,
    override val actions: List<ActionHandler> = emptyList(),
    override val recovers: List<RecoverHandler> = emptyList(),
    override val exit: ExitHandler? = null,
) : StateParent

/** A concrete leaf state. The only node kind that may declare `@OnEnter`. */
@InternalKomaStrictApi
public data class LeafNode(
    override val simpleName: String,
    val declarationKind: StateDeclarationKind,
    override val companionName: String?,
    override val props: List<StateProp> = emptyList(),
    /** `@OnEnter` declaration. */
    val enter: EnterHandler? = null,
    override val exit: ExitHandler? = null,
    override val actions: List<ActionHandler> = emptyList(),
    override val recovers: List<RecoverHandler> = emptyList(),
) : StateNode
