package me.tbsten.koma.strict.ksp.codegen

import me.tbsten.koma.strict.ksp.model.ExitHandler
import me.tbsten.koma.strict.ksp.model.HandlerDecl
import me.tbsten.koma.strict.ksp.model.LeafNode
import me.tbsten.koma.strict.ksp.model.StateNode
import me.tbsten.koma.strict.ksp.model.StatePath
import me.tbsten.koma.strict.ksp.model.TransitionHandlerDecl
import me.tbsten.koma.strict.ksp.naming.handlerParamName
import me.tbsten.koma.strict.ksp.naming.handlerReactionTypeName
import me.tbsten.koma.strict.ksp.naming.handlerScopeTypeName
import me.tbsten.koma.strict.ksp.naming.handlerTransitionsTypeName

/**
 * Codegen-derived information for one handler declaration (a bundle of [HandlerDecl] + naming).
 * Shared by all appendXxx of Reaction / Transitions / Scope / Handlers params / dispatch.
 */
internal class HandlerGen(
    env: CodegenEnv,
    /** Path to the node that declared this handler (a shared declaration on the root / an intermediate node). */
    internal val ownerPath: StatePath,
    internal val decl: HandlerDecl,
) {
    private val prefix = env.prefix(ownerPath)

    /** Type of the scope's `state` = the declaring node's state type (the root / intermediate type for shared declarations). */
    internal val stateRef: String = env.stateRef(ownerPath)

    internal val scopeType: String = handlerScopeTypeName(prefix, decl)

    /** Param name in `actions(...)` (`enter` / `reload` / `exit` / `recoverXxx`). */
    internal val paramName: String = handlerParamName(decl)

    internal val isExit: Boolean = decl is ExitHandler

    /** No Reaction is generated for exit (no transition capability). */
    internal val reactionType: String? =
        (decl as? TransitionHandlerDecl)?.let { handlerReactionTypeName(prefix, it) }

    /** No Transitions is generated for handlers with zero targets (stay only). */
    internal val transitionsType: String? =
        (decl as? TransitionHandlerDecl)
            ?.takeIf { it.transition.targets.isNotEmpty() }
            ?.let { handlerTransitionsTypeName(prefix, it) }

    internal val canStay: Boolean = (decl as? TransitionHandlerDecl)?.transition?.canStay == true

    internal val hasEventSink: Boolean = decl.emits.isNotEmpty()

    /** Function type of the Handlers' handler property / the `actions(...)` param. */
    internal val handlerFunctionType: String =
        if (isExit) "suspend $scopeType.() -> Unit" else "suspend $scopeType.() -> $reactionType"
}

/** Enumerates a node's own handler declarations in the facade's order (enter -> actions -> exit -> recovers). */
internal fun CodegenEnv.handlersOf(
    path: StatePath,
    node: StateNode,
): List<HandlerGen> =
    buildList {
        (node as? LeafNode)?.enter?.let { add(HandlerGen(this@handlersOf, path, it)) }
        node.actions.forEach { add(HandlerGen(this@handlersOf, path, it)) }
        node.exit?.let { add(HandlerGen(this@handlersOf, path, it)) }
        node.recovers.forEach { add(HandlerGen(this@handlersOf, path, it)) }
    }
