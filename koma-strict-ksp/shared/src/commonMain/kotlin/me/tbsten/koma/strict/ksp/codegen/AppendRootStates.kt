package me.tbsten.koma.strict.ksp.codegen

import me.tbsten.koma.strict.ksp.model.ActionHandler
import me.tbsten.koma.strict.ksp.model.EnterHandler
import me.tbsten.koma.strict.ksp.model.ExitHandler
import me.tbsten.koma.strict.ksp.model.LeafNode
import me.tbsten.koma.strict.ksp.model.RecoverHandler
import me.tbsten.koma.strict.ksp.model.StateNode
import me.tbsten.koma.strict.ksp.model.StateParent
import me.tbsten.koma.strict.ksp.model.StatePath
import me.tbsten.koma.strict.ksp.model.hasOwnHandlerDeclarations
import me.tbsten.koma.strict.ksp.model.leavesWithPath
import me.tbsten.koma.strict.ksp.naming.rootStatesJvmName
import me.tbsten.koma.strict.ksp.naming.stateParamName
import me.tbsten.koma.strict.ksp.naming.statesConfigureScopeTypeName
import me.tbsten.koma.strict.ksp.naming.storeFactoryFunctionName

/**
 * The koma package. Generated code has no import boilerplate, so it references koma
 * with fully qualified names. Confirmed against the koma-core 4.0.0-rc02 sources.
 */
internal const val KOMA_CORE_PACKAGE: String = "koma.core"

/**
 * The root `states()` extension (`<Root>.storeSpec.generated`, the only whole-spec-dependent file).
 *
 * An extension on `StoreBuilder<S, A, E>` — the receiver's type arguments select which
 * store's states() this is. Params take `Scope.() -> Handlers` scope lambdas; because the
 * bundle types implement `(Scope) -> Self`, already-built values (from the companion
 * `actions()` / `states()` extensions) can be passed as-is — both call styles are supported.
 * They stay required named params, so state exhaustiveness is unchanged. The body compiles
 * down to koma's `state<X> {}`. Shared declarations (default blocks on the root /
 * intermediate nodes) are expanded into every leaf's state block without relying on koma's
 * hierarchical dispatch, and each leaf's `configure` escape hatch is invoked at the end of
 * its block, followed by the states()-level escape blocks (innermost first, root last).
 *
 * The trailing `configure` param is the per-state escape block (see the generated
 * `...StatesConfigureScope`); with zero escape members the sentinel is kept instead.
 */
internal fun Appendable.appendRootStates(env: CodegenEnv) {
    val root = env.root
    val params = statesParams(env, StatePath.root, root)
    val escapeMembers = statesConfigureMembers(env, StatePath.root, root)
    val escapeScopeType = rootStatesConfigureScopeType(env).takeIf { escapeMembers.isNotEmpty() }
    val leafBlocks =
        root.leavesWithPath().mapNotNull { (path, leaf) ->
            buildString { appendLeafStateBlock(env, path, leaf) }.takeIf { it.isNotEmpty() }
        }

    // 同名 states() 同士の JVM signature clash 回避 (param ゼロの states() や erasure が同型に
    // なるケースの保険 + 安定した JVM 名)。commonMain でも使えるよう完全修飾で付ける
    // (生成ファイルは import を足せないため)。
    appendLine("@kotlin.jvm.JvmName(\"${rootStatesJvmName(root)}\")")
    val suppressions =
        buildList {
            // scope lambda / escape block を 1 回だけ評価し、意図的に param と同名の local へ束ねる
            // (shadowing) ための抑制
            if (params.isNotEmpty() || escapeScopeType != null) add("NAME_SHADOWING")
            // 中間 sealed member の共有 escape は S2 不変の StateHandlerConfig を leaf へ狭める
            // (S2 は産出位置のみ = 健全。koma 自身の action<A2> と同じ形)
            if (leafBlocks.any { it.contains("?.invoke(this as ") }) add("UNCHECKED_CAST")
        }
    if (suppressions.isNotEmpty()) {
        appendLine("@Suppress(${suppressions.joinToString(", ") { "\"$it\"" }})")
    }
    appendLine(
        "${env.visibility} fun $KOMA_CORE_PACKAGE.StoreBuilder" +
            "<${env.rootRef}, ${env.actionsRef}, ${env.eventsOrNothingRef}>.states(",
    )
    params.forEach { appendLine("    ${it.name}: ${it.paramType},") }
    if (escapeScopeType != null) {
        appendLine("    ${statesConfigureParam(escapeScopeType)}")
    } else {
        appendLine("    $SENTINEL_PARAM")
    }
    appendLine(") {")
    // 各 param の lambda はここで 1 回だけ評価する (値渡しでは自己返し invoke が呼ばれるだけ)。
    // 初期化子内の名前は Kotlin のスコープ規則で param 側に解決される
    params.forEach { appendLine("    val ${it.name} = ${it.evalExpr}") }
    escapeScopeType?.let { appendLine("    val configure = $it().apply(configure)") }
    if ((params.isNotEmpty() || escapeScopeType != null) && leafBlocks.isNotEmpty()) appendLine()
    leafBlocks.forEach(::append)
    appendLine("}")
}

/** Escape scope type of the root `states()` (generated into the same storeSpec file). */
internal fun rootStatesConfigureScopeType(env: CodegenEnv): String =
    statesConfigureScopeTypeName(env.prefix(StatePath.root))

/**
 * The per-store factory function (`lceStore(...)`), generated next to the root `states()`
 * extension. A type-argument-free sugar entry point over the canonical koma form —
 * `Store<S, A, E>(initialState) { states(...) }` builds the exact same store. The params
 * mirror `states(...)` (same order, both call styles); the trailing `configuration` param
 * appends raw koma DSL after the generated handlers (store-level escape hatch), so no
 * sentinel is needed. `context` follows koma rc02's `Store()` factory signature
 * (`CoroutineContext? = null`).
 */
internal fun Appendable.appendStoreFactory(env: CodegenEnv) {
    val root = env.root
    val params = statesParams(env, StatePath.root, root)
    val factoryName = storeFactoryFunctionName(root)
    val typeArgs = "<${env.rootRef}, ${env.actionsRef}, ${env.eventsOrNothingRef}>"

    appendLine("/**")
    appendLine(" * Builds a [$KOMA_CORE_PACKAGE.Store] for [${env.rootRef}] without spelling the store type arguments.")
    appendLine(" *")
    appendLine(" * Sugar over the canonical koma entry point — `Store$typeArgs(initialState) { states(...) }`")
    appendLine(" * builds the exact same store. [configuration] appends raw koma DSL after the generated")
    appendLine(" * handlers (store-level escape hatch).")
    appendLine(" */")
    appendLine("${env.visibility} fun $factoryName(")
    appendLine("    initialState: ${env.rootRef},")
    params.forEach { appendLine("    ${it.name}: ${it.paramType},") }
    appendLine("    context: kotlin.coroutines.CoroutineContext? = null,")
    appendLine("    configuration: $KOMA_CORE_PACKAGE.StoreBuilder$typeArgs.() -> Unit = {},")
    appendLine("): $KOMA_CORE_PACKAGE.Store$typeArgs =")
    appendLine("    $KOMA_CORE_PACKAGE.Store$typeArgs(initialState = initialState, context = context) {")
    if (params.isEmpty()) {
        appendLine("        states()")
    } else {
        appendLine("        states(")
        params.forEach { appendLine("            ${it.name} = ${it.name},") }
        appendLine("        )")
    }
    appendLine("        configuration()")
    appendLine("    }")
}

/** The `state<X> { ... }` block for one leaf. Leaves with no effective handlers are not emitted. */
private fun Appendable.appendLeafStateBlock(
    env: CodegenEnv,
    leafPath: StatePath,
    leaf: LeafNode,
) {
    // 共有宣言 (root -> 中間) が先、自身の宣言が後 — states() の default 先頭と同じ並び。
    val chain = env.nodeChain(leafPath)
    val enter = leaf.enter?.let { HandlerGen(env, leafPath, it) }
    val actions =
        chain.flatMap { (path, node) ->
            node.actions.map { HandlerGen(env, path, it) to handlersAccessExpr(env, path, node) }
        }
    val exits =
        chain.mapNotNull { (path, node) ->
            node.exit?.let { HandlerGen(env, path, it) to handlersAccessExpr(env, path, node) }
        }
    val recovers =
        chain.flatMap { (path, node) ->
            node.recovers.map { HandlerGen(env, path, it) to handlersAccessExpr(env, path, node) }
        }
    if (enter == null && actions.isEmpty() && exits.isEmpty() && recovers.isEmpty()) return

    appendLine("    state<${env.stateRef(leafPath)}> {")
    enter?.let { handler ->
        val access = handlersAccessExpr(env, leafPath, leaf)
        appendLine("        enter {")
        appendDispatch(env, handler, access, indent = "            ")
        appendLine("        }")
    }
    actions.forEach { (handler, access) ->
        val actionRef = env.sourceTypeRef((handler.decl as ActionHandler).action)
        appendLine("        action<$actionRef> {")
        appendDispatch(env, handler, access, indent = "            ")
        appendLine("        }")
    }
    exits.forEach { (handler, access) ->
        appendLine("        exit {")
        appendDispatch(env, handler, access, indent = "            ")
        appendLine("        }")
    }
    recovers.forEach { (handler, access) ->
        val exceptionRef = env.sourceTypeRef((handler.decl as RecoverHandler).exception)
        appendLine("        recover<$exceptionRef> {")
        appendDispatch(env, handler, access, indent = "            ")
        appendLine("        }")
    }
    if (leaf.hasOwnHandlerDeclarations()) {
        // per-state escape hatch: 生成 handler の登録後 (ブロック末尾) に素の koma DSL を差し込む
        appendLine("        ${handlersAccessExpr(env, leafPath, leaf)}.configure(this)")
    }
    // states() の trailing escape block (内側の states() 分が先・root 分が最後 = ソース記述順)
    leafEscapeApplications(env, leafPath).forEach { appendLine("        $it") }
    appendLine("    }")
}

/**
 * The handler call plus the Reaction when-branch.
 * stay = do not call koma's `nextState`. Zero-target (stay-only) handlers and exit are just called.
 */
private fun Appendable.appendDispatch(
    env: CodegenEnv,
    handler: HandlerGen,
    accessExpr: String,
    indent: String,
) {
    val scopeArgs =
        buildList {
            add("state")
            when (handler.decl) {
                is ActionHandler -> add("action")
                is RecoverHandler -> add("error")
                is EnterHandler, is ExitHandler -> Unit
            }
            if (handler.hasEventSink) add("::event")
            add("::clearPendingActions")
        }.joinToString(", ")
    val call = "$accessExpr.${handler.paramName}(${handler.scopeType}($scopeArgs))"

    if (handler.transitionsType == null) {
        appendLine("$indent$call")
        return
    }
    appendLine("${indent}when (val r = $call) {")
    appendLine("$indent    is ${handler.reactionType}.Transition -> nextState { r.next }")
    if (handler.canStay) {
        appendLine("$indent    is ${handler.reactionType}.Stay -> Unit")
    }
    appendLine("$indent}")
}

/**
 * Expression that reaches the declaring node's Handlers value from the states() body
 * (the params are the Handlers values themselves).
 * Examples: leaf `Stable.Idle` -> `stable.idle` / shared declaration on
 * intermediate `Stable.Refresh` -> `stable.refresh.refreshCommon` /
 * shared declaration on the root -> `default`.
 */
private fun handlersAccessExpr(
    env: CodegenEnv,
    ownerPath: StatePath,
    ownerNode: StateNode,
): String {
    if (ownerPath.isRoot) return env.root.defaultName
    val segments = ownerPath.segments
    val base = stateParamName(segments.first())
    val rest = segments.drop(1).joinToString("") { "." + stateParamName(it) }
    val defaultPart = if (ownerNode is StateParent) ".${ownerNode.defaultName}" else ""
    return base + rest + defaultPart
}
