package me.tbsten.koma.strict.ksp.codegen

import me.tbsten.koma.strict.ksp.model.ActionHandler
import me.tbsten.koma.strict.ksp.model.EventRef
import me.tbsten.koma.strict.ksp.model.RecoverHandler
import me.tbsten.koma.strict.ksp.model.TransitionHandlerDecl
import me.tbsten.koma.strict.ksp.naming.transitionMethodName

// per-handler の支援型 (Reaction / Transitions / Scope) の生成。
// 期待形は doc/internal/samples.md (LCE ケースに全量)。

/** Per-handler Reaction type — a return type from which only the declared reactions (Transition / Stay) can be constructed. */
internal fun Appendable.appendReaction(
    env: CodegenEnv,
    handler: HandlerGen,
) {
    val reaction = handler.reactionType ?: return
    appendLine("${env.visibility} sealed interface $reaction {")
    val hasTransition = handler.transitionsType != null
    if (hasTransition) {
        appendLine("    public class Transition internal constructor(")
        appendLine("        internal val next: ${env.rootRef},")
        appendLine("    ) : $reaction")
    }
    if (handler.canStay) {
        if (hasTransition) appendLine()
        appendLine("    public data object Stay : $reaction")
    }
    appendLine("}")
}

/** Per-handler Transitions type — only the declared transitions (`toXxx`) are generated. */
internal fun Appendable.appendTransitions(
    env: CodegenEnv,
    handler: HandlerGen,
) {
    val transitions = handler.transitionsType ?: return
    val reaction = handler.reactionType ?: return
    val targets = (handler.decl as TransitionHandlerDecl).transition.targets
    val sourceProps = env.effectiveProps(handler.ownerPath)

    // 同名 prop 持ち越し (property matching): 名前 + 型 + nullability が一致した prop にだけ
    // `= state.<prop>` デフォルト値を付ける。1 つも無ければ state は未使用になる。
    fun matchesSourceProp(name: String, type: String, isNullable: Boolean): Boolean =
        sourceProps.any { it.name == name && it.type == type && it.isNullable == isNullable }

    val usesState =
        targets.any { target ->
            env.constructionParams(target).any { matchesSourceProp(it.name, it.type, it.isNullable) }
        }

    appendLine("${env.visibility} class $transitions internal constructor(")
    val suppress = if (usesState) "" else "@Suppress(\"unused\") "
    appendLine("    ${suppress}private val state: ${handler.stateRef},")
    appendLine(") {")
    targets.forEachIndexed { index, target ->
        if (index > 0) appendLine()
        val methodName = transitionMethodName(handler.ownerPath, target)
        val params = env.constructionParams(target)
        val construction = env.constructionExpr(target, params.joinToString(", ") { it.name })
        if (params.isEmpty()) {
            appendLine("    public fun $methodName(): $reaction = $reaction.Transition($construction)")
        } else {
            appendLine("    public fun $methodName(")
            params.forEach { param ->
                val default =
                    if (matchesSourceProp(param.name, param.type, param.isNullable)) " = state.${param.name}" else ""
                appendLine("        ${param.name}: ${param.renderedType()}$default,")
            }
            appendLine("    ): $reaction = $reaction.Transition($construction)")
        }
    }
    appendLine("}")
}

/**
 * Per-handler scope type — the whitelisted surface (state / action or error / nextState /
 * stayState / emitXxx). With zero emit declarations, the scope has no eventSink at all.
 *
 * The `clearPendingActions()` passthrough (shared by every scope) and the `onClearPendingActions`
 * wiring live on the runtime base [me.tbsten.koma.strict.dsl.HandlerScope]; only the whitelisted
 * members are generated here. Constructing the base requires opting in to
 * [me.tbsten.koma.strict.InternalKomaStrictApi], so the generated class carries `@OptIn`.
 *
 * Annotated with both @KomaStrictDsl and koma's @KomaStoreDsl (@DslMarker): without the koma
 * marker, handler lambdas written inline inside `Store {}` would leak the outer builder API
 * through the implicit receiver (verified against rc02). The markers stay on this generated
 * subclass (the actual handler-lambda receiver), not on the base.
 */
internal fun Appendable.appendScope(
    env: CodegenEnv,
    handler: HandlerGen,
) {
    appendLine("@KomaStrictDsl")
    appendLine("@$KOMA_CORE_PACKAGE.KomaStoreDsl")
    appendLine("@OptIn(InternalKomaStrictApi::class)")
    appendLine("${env.visibility} class ${handler.scopeType} internal constructor(")
    appendLine("    public val state: ${handler.stateRef},")
    when (val decl = handler.decl) {
        is ActionHandler -> appendLine("    public val action: ${env.sourceTypeRef(decl.action)},")
        is RecoverHandler -> appendLine("    public val error: ${env.sourceTypeRef(decl.exception)},")
        else -> Unit
    }
    if (handler.hasEventSink) {
        appendLine("    private val eventSink: suspend (${env.eventsRef}) -> Unit,")
    }
    appendLine("    onClearPendingActions: () -> Unit,")

    val bodySections =
        buildList {
            handler.transitionsType?.let { transitions ->
                add("    public val nextState: $transitions = $transitions(state)")
            }
            if (handler.canStay) {
                add(
                    buildString {
                        appendLine(
                            "    /** Chooses to stay in the current state. This simply does not call koma's nextState: no instance is created and pending actions are not discarded. */",
                        )
                        append("    public fun stayState(): ${handler.reactionType} = ${handler.reactionType}.Stay")
                    },
                )
            }
            handler.decl.emits.forEach { event -> add(buildString { appendEmitFunction(env, event) }) }
        }

    // clearPendingActions と onClearPendingActions 配線は runtime の HandlerScope 基底が持つ。
    // whitelist メンバー (nextState / stayState / emitXxx) が空なら本体ごと省略する。
    val supertype = "$HANDLER_SCOPE_BASE(onClearPendingActions)"
    if (bodySections.isEmpty()) {
        appendLine(") : $supertype")
    } else {
        appendLine(") : $supertype {")
        bodySections.forEachIndexed { index, section ->
            if (index > 0) appendLine()
            appendLine(section)
        }
        appendLine("}")
    }
}

/** Runtime base of every generated handler scope (owns `clearPendingActions()`). Resolved via the file's `import me.tbsten.koma.strict.dsl.*`. */
private const val HANDLER_SCOPE_BASE: String = "HandlerScope"

/** `emit{Event}(...)` for one declared event. Takes the event's construction arguments as its own parameters. */
private fun Appendable.appendEmitFunction(
    env: CodegenEnv,
    event: EventRef,
) {
    val eventRef = env.sourceTypeRef(event.type)
    val params = event.params.joinToString(", ") { "${it.name}: ${it.renderedType()}" }
    val construction = if (event.isObject) eventRef else "$eventRef(${event.params.joinToString(", ") { it.name }})"
    appendLine("    public suspend fun emit${event.type.simpleName}($params) {")
    appendLine("        eventSink($construction)")
    append("    }")
}
