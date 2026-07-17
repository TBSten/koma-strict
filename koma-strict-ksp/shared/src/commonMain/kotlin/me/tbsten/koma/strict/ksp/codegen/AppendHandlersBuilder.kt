package me.tbsten.koma.strict.ksp.codegen

import me.tbsten.koma.strict.ksp.model.ActionHandler
import me.tbsten.koma.strict.ksp.model.HandlerDecl
import me.tbsten.koma.strict.ksp.model.RecoverHandler
import me.tbsten.koma.strict.ksp.model.StateNode
import me.tbsten.koma.strict.ksp.model.StateParent
import me.tbsten.koma.strict.ksp.model.StatePath
import me.tbsten.koma.strict.ksp.naming.actionsBuilderTypeName
import me.tbsten.koma.strict.ksp.naming.builderHandlerMemberName
import me.tbsten.koma.strict.ksp.naming.defaultActionsBuilderTypeName

// builder 形式 (第 3 の書き方) のうち leaf / default ブロックの `actions { ... }` の生成。
// 網羅チェックは構築時 fail-fast (runtime の throwMissingBuilderEntries / throwDuplicateBuilderEntry)。
// 中間 sealed の `states { ... }` は AppendGroupBuilder.kt。

/**
 * Builder-eligibility policy — **the single place deciding which handlers appear in the
 * builder form**. enter / exit have no builder member: inside the per-state `configure`
 * escape hatch (and raw koma DSL in general) `enter { }` / `exit { }` already exist with
 * different (unchecked) semantics, and reusing the same words for a checked registration
 * would be misleading. A rename scheme (e.g. `onEnter` / `onExit`) is under discussion; if
 * adopted, flip this policy and map the member names in naming/HandlerParamName.kt.
 */
internal fun handlerHasBuilderMember(decl: HandlerDecl): Boolean = decl is ActionHandler || decl is RecoverHandler

/**
 * Whether a node's handler bundle gets the builder form (`actions { ... }`) at all.
 * Nodes declaring enter / exit do not — the builder could never satisfy their
 * exhaustiveness because those handlers have no builder member ([handlerHasBuilderMember]).
 * Such nodes keep the named-argument form only.
 */
internal fun actionsBuilderSupported(handlers: List<HandlerGen>): Boolean =
    handlers.all { handlerHasBuilderMember(it.decl) }

/** Actions-builder type name of a node (default blocks mirror [nodeHandlersTypeName]'s default-name concatenation). */
internal fun nodeActionsBuilderTypeName(
    env: CodegenEnv,
    path: StatePath,
    node: StateNode,
): String {
    val prefix = env.prefix(path)
    return if (node is StateParent) {
        defaultActionsBuilderTypeName(prefix, node.defaultName)
    } else {
        actionsBuilderTypeName(prefix)
    }
}

/**
 * Owner reference used in the fail-fast messages: the state's source reference, plus the
 * default-block name for shared declarations (`FlowState.Refresh.refreshCommon`).
 */
internal fun builderOwnerRef(
    env: CodegenEnv,
    path: StatePath,
    node: StateNode,
): String {
    val stateRef = env.stateRef(path)
    return if (node is StateParent) "$stateRef.${node.defaultName}" else stateRef
}

/**
 * The `actions { ... }` builder overload. With [receiver] it is the companion extension;
 * without it, the same-signature mirror generated as a member of the bundle's scope type.
 * The KDoc must state that exhaustiveness is checked at build time (spec requirement).
 */
internal fun Appendable.appendActionsBuilderFunction(
    builderType: String,
    bundleType: String,
    receiver: String?,
    visibility: String,
    indent: String = "",
) {
    appendLine("$indent/**")
    appendLine("$indent * Builder-form overload of `actions(...)` (see [$builderType]).")
    appendLine("$indent *")
    appendLine("$indent * Note: unlike the named-argument overload, exhaustiveness of the declared handlers is")
    appendLine("$indent * checked **at build time** (fail-fast when the block finishes), not at compile time.")
    appendLine("$indent * This overload exists only for states without enter / exit declarations (those handlers")
    appendLine("$indent * have no builder member).")
    appendLine("$indent */")
    val receiverPart = receiver?.let { "$it." } ?: ""
    appendLine(
        "$indent$visibility fun ${receiverPart}actions(build: $builderType.() -> Unit): $bundleType = " +
            "$builderType().apply(build).build()",
    )
}

/**
 * The actions-builder class section of a node: one registration member per declared handler
 * (same name as the `actions(...)` parameter), plus `configure { }` on leaves. Duplicate
 * registration fails immediately; `build()` throws when declared handlers are missing
 * (both via the runtime helpers, keeping the message text in one place).
 */
internal fun actionsBuilderClassSection(
    env: CodegenEnv,
    builderType: String,
    bundleType: String,
    owner: String,
    handlers: List<HandlerGen>,
    configureType: String?,
): String =
    buildString {
        appendLine("/**")
        appendLine(" * Builder receiver of the `actions { ... }` overload building [$bundleType].")
        appendLine(" *")
        appendLine(" * Each member function registers the handler of the same-named `actions(...)` parameter.")
        appendLine(" * Exhaustiveness is checked when the block finishes (build-time fail-fast), not at compile")
        appendLine(" * time: handlers left unregistered — and any duplicate registration — throw")
        appendLine(" * [IllegalStateException]. Prefer the named-argument `actions(...)` overload to catch")
        appendLine(" * missing handlers at compile time.")
        appendLine(" */")
        appendLine("@KomaStrictDsl")
        appendLine("@$KOMA_CORE_PACKAGE.KomaStoreDsl")
        appendLine("@OptIn(InternalKomaStrictApi::class)")
        appendLine("${env.visibility} class $builderType internal constructor() {")

        // 登録先スロット。重複エラー込みの set-once 機構は runtime の SetOnceSlot が持つ
        handlers.forEach { handler ->
            appendLine("    ${setOnceSlotDeclaration(memberName(handler), handler.handlerFunctionType, owner)}")
            appendLine()
        }
        if (configureType != null) {
            appendLine("    ${setOnceSlotDeclaration("configure", configureType, owner)}")
            appendLine()
        }

        // 各 handler の登録 member (重複は SetOnceSlot.set が即 fail-fast)
        handlers.forEach { handler ->
            val name = memberName(handler)
            appendLine(
                "    /** Registers the handler of the same-named `actions(...)` parameter. Duplicate registration fails fast with [IllegalStateException]. */",
            )
            appendLine("    public fun $name(handler: ${handler.handlerFunctionType}) { $name.set(handler) }")
            appendLine()
        }
        if (configureType != null) {
            appendLine(
                "    /** Appends raw koma DSL at the end of the generated `state<...> {}` block (the trailing `configure` parameter of `actions(...)`). Duplicate registration fails fast with [IllegalStateException]. */",
            )
            appendLine("    public fun configure(block: $configureType) { configure.set(block) }")
            appendLine()
        }

        appendBuilderBuildFunction(
            bundleType = bundleType,
            owner = owner,
            requiredMembers = handlers.map { memberName(it) },
            trailingArgs = if (configureType != null) listOf("configure.getOrNull() ?: {}") else emptyList(),
        )
        appendLine("}")
    }

private fun memberName(handler: HandlerGen): String = builderHandlerMemberName(handler.decl)

/**
 * One `SetOnceSlot` field declaration backing a builder member. The write-once + duplicate
 * fail-fast machinery lives in the runtime [me.tbsten.koma.strict.dsl.SetOnceSlot]; the generated
 * builder only names the member and its owner (resolved via `import me.tbsten.koma.strict.dsl.*`).
 */
internal fun setOnceSlotDeclaration(
    name: String,
    valueType: String,
    owner: String,
): String = "private val $name = SetOnceSlot<$valueType>(\"$owner\", \"$name\")"

/**
 * The `build()` of a generated builder: collects still-missing required entries in
 * declaration order (via each slot's `isSet`), fail-fasts via the runtime helper, then
 * constructs the bundle from the slots. Shared by the actions builders and the group builders
 * (AppendGroupBuilder.kt). Each required member is a [me.tbsten.koma.strict.dsl.SetOnceSlot];
 * [trailingArgs] are passed through verbatim (e.g. an empty escape scope).
 */
internal fun Appendable.appendBuilderBuildFunction(
    bundleType: String,
    owner: String,
    requiredMembers: List<String>,
    trailingArgs: List<String> = emptyList(),
) {
    appendLine("    internal fun build(): $bundleType {")
    if (requiredMembers.isNotEmpty()) {
        appendLine("        val missing = listOfNotNull(")
        requiredMembers.forEach { name ->
            appendLine("            \"$name\".takeIf { !$name.isSet },")
        }
        appendLine("        )")
        appendLine("        if (missing.isNotEmpty()) throwMissingBuilderEntries(\"$owner\", missing)")
    }
    val args = requiredMembers.map { "$it.getOrNull()!!" } + trailingArgs
    if (args.isEmpty()) {
        appendLine("        return $bundleType()")
    } else {
        appendLine("        return $bundleType(${args.joinToString(", ")})")
    }
    appendLine("    }")
}
