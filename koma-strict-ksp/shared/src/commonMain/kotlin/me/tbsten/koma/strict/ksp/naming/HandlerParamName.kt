package me.tbsten.koma.strict.ksp.naming

import me.tbsten.koma.strict.InternalKomaStrictApi
import me.tbsten.koma.strict.ksp.model.ActionHandler
import me.tbsten.koma.strict.ksp.model.EnterHandler
import me.tbsten.koma.strict.ksp.model.ExitHandler
import me.tbsten.koma.strict.ksp.model.HandlerDecl
import me.tbsten.koma.strict.ksp.model.RecoverHandler
import me.tbsten.koma.strict.ksp.model.StateParent

/**
 * Handler param name in `actions()`:
 * `enter` / `exit` / decapitalized action name / `recover{ExceptionSimpleName}` (tentatively fixed).
 */
@InternalKomaStrictApi
public fun handlerParamName(handler: HandlerDecl): String =
    when (handler) {
        is EnterHandler -> "enter"
        is ExitHandler -> "exit"
        is ActionHandler -> handler.action.simpleName.decapitalized()
        is RecoverHandler -> "recover" + handler.exception.simpleName
    }

/**
 * Member-function name of a handler in the generated actions builder (`actions { ... }`).
 * Currently identical to [handlerParamName]; kept as a separate naming point so a rename of
 * the builder members (e.g. an `onEnter` / `onExit` scheme, under discussion) stays local.
 * Note: enter / exit currently have no builder member at all — the eligibility policy lives
 * in `codegen/AppendHandlersBuilder.kt` ([me.tbsten.koma.strict.ksp.codegen]).
 */
@InternalKomaStrictApi
public fun builderHandlerMemberName(handler: HandlerDecl): String = handlerParamName(handler)

/** State param name in `states()` (the decapitalized state name). */
@InternalKomaStrictApi
public fun stateParamName(stateSimpleName: String): String = stateSimpleName.decapitalized()

/**
 * Whether a state param name collides with the reserved param name (the default block name).
 * A collision produces a KSP error plus rename guidance (avoidable via `@DefaultName`)
 * (doc/internal/generate-strict-store-factory-dsl.md, KSP static validation).
 */
@InternalKomaStrictApi
public fun stateParamNameConflictsWithDefaultBlock(
    stateSimpleName: String,
    defaultName: String = StateParent.DEFAULT_BLOCK_PARAM_NAME,
): Boolean = stateParamName(stateSimpleName) == defaultName
