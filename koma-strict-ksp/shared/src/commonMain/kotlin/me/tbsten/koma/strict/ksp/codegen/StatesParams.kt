package me.tbsten.koma.strict.ksp.codegen

import me.tbsten.koma.strict.ksp.model.StateParent
import me.tbsten.koma.strict.ksp.model.StatePath
import me.tbsten.koma.strict.ksp.model.hasAnyHandlerDeclarations
import me.tbsten.koma.strict.ksp.model.hasOwnHandlerDeclarations
import me.tbsten.koma.strict.ksp.naming.bundleScopeTypeName
import me.tbsten.koma.strict.ksp.naming.groupBuilderTypeName
import me.tbsten.koma.strict.ksp.naming.groupHandlersTypeName
import me.tbsten.koma.strict.ksp.naming.handlersTypeName
import me.tbsten.koma.strict.ksp.naming.stateParamName

// `states(...)` の param モデル (default 先頭 -> 子は宣言順)。root の states() 拡張 / per-store
// factory (AppendRootStates.kt)・中間 sealed の束ね (AppendGroupStates.kt)・group builder
// (AppendGroupBuilder.kt) が共有する。

/**
 * One param of `states(...)` (the default block or a child state).
 * The default (if declared) comes first, then child states in source declaration order —
 * even with named params this guarantees the "just fill them in the order you read the
 * declarations" intuition. Subtrees with zero declarations get no param.
 */
internal class StatesParam(
    val name: String,
    val handlersType: String,
    /**
     * Builder type of the nested-builder registration overload in the parent's group builder
     * (`idle { ... }`). Null = value-only registration: leaf children (and default blocks)
     * declaring enter / exit have no actions builder (see AppendHandlersBuilder.kt).
     */
    val nestedBuilderType: String? = null,
    /** True for the default block param (shared declarations); affects builder-member KDoc wording. */
    val isDefaultBlock: Boolean = false,
) {
    /** Receiver of the scope-lambda form of this param. */
    val scopeType: String = bundleScopeTypeName(handlersType)

    /** Declared param type — a receiver-typed function type, accepting both call styles. */
    val paramType: String = "$scopeType.() -> $handlersType"

    /** Evaluation of this param inside a `states(...)` body (invokes the lambda exactly once). */
    val evalExpr: String = "$scopeType().$name()"
}

/** The `states(...)` param list of [parent] (default first, then children in source declaration order). */
internal fun statesParams(
    env: CodegenEnv,
    path: StatePath,
    parent: StateParent,
): List<StatesParam> =
    buildList {
        if (parent.hasOwnHandlerDeclarations()) {
            add(
                StatesParam(
                    name = parent.defaultName,
                    handlersType = nodeHandlersTypeName(env, path, parent),
                    nestedBuilderType =
                        nodeActionsBuilderTypeName(env, path, parent)
                            .takeIf { actionsBuilderSupported(env.handlersOf(path, parent)) },
                    isDefaultBlock = true,
                ),
            )
        }
        parent.children
            .filter { it.hasAnyHandlerDeclarations() }
            .forEach { child ->
                val childPath = path + child.simpleName
                add(
                    StatesParam(
                        name = stateParamName(child.simpleName),
                        handlersType =
                            when {
                                // 自宣言 + 子を持つ中間 sealed の親側 param 型は合成型 {Prefix}Handlers
                                child is StateParent && child.hasOwnHandlerDeclarations() ->
                                    handlersTypeName(env.prefix(childPath))
                                child is StateParent -> groupHandlersTypeName(env.prefix(childPath))
                                else -> nodeHandlersTypeName(env, childPath, child)
                            },
                        nestedBuilderType =
                            when {
                                // 子中間 sealed の states builder は常に生える (member は子 state 名なので enter/exit と被らない)
                                child is StateParent -> groupBuilderTypeName(env.prefix(childPath))
                                else ->
                                    nodeActionsBuilderTypeName(env, childPath, child)
                                        .takeIf { actionsBuilderSupported(env.handlersOf(childPath, child)) }
                            },
                    ),
                )
            }
    }
