package me.tbsten.koma.strict.ksp.codegen

import me.tbsten.koma.strict.ksp.naming.bundleScopeTypeName

// Handlers 束 (leaf Handlers / default Handlers / GroupHandlers / 合成 Handlers) の共通レンダリング。
// 「値渡し」と「scope lambda」の両対応は、束クラスの Function1 自己返し実装 + scope ミラーで実現する
// (.local/design/facade-named-arguments.md「訂正: 両対応が真意(2026-07-16)」。
// extension function type は supertype にできないため素の `(Scope) -> Self` を実装し、
// `states(...)` の param 側だけ receiver 付き関数型にする — 相互代入可・実測済み)。

/**
 * One handlers-bundle class section: the constructor properties, the `(Scope) -> Self`
 * supertype and the self-returning `invoke` override that makes an already-built value
 * assignable to the scope-lambda-typed parameter.
 *
 * The `(Scope) -> Self` function-type supertype stays in the generated code (not a runtime base):
 * Kotlin/JS forbids a class implementing a function interface, and koma-strict-runtime is
 * published for js / wasmJs, so hoisting it into the runtime would break those targets. The
 * generated bundles are only ever compiled for the store's own targets (jvm / native), where it
 * is allowed. The KDoc is condensed to a single line to keep the boilerplate small.
 */
internal fun bundleClassSection(
    env: CodegenEnv,
    bundleType: String,
    constructorProperties: List<String>,
): String =
    buildString {
        val scopeType = bundleScopeTypeName(bundleType)
        val supertype = "($scopeType) -> $bundleType"
        if (constructorProperties.isEmpty()) {
            appendLine("${env.visibility} class $bundleType internal constructor() : $supertype {")
        } else {
            appendLine("${env.visibility} class $bundleType internal constructor(")
            constructorProperties.forEach { appendLine("    $it,") }
            appendLine(") : $supertype {")
        }
        appendLine(
            "    /** Returns this bundle itself, so an already-built value is assignable to the scope-lambda-typed `states(...)` parameter (value-passing / scope-lambda interchange). */",
        )
        appendLine("    override fun invoke(p1: $scopeType): $bundleType = this")
        appendLine("}")
    }

/**
 * One `actions(...)` builder function. With [receiver] it is the top-level companion
 * extension; without it, the same-signature mirror generated as a member of the bundle's
 * scope type (the scope-lambda form). Leaf bundles take the trailing `configure` escape
 * hatch ([configureType] non-null); shared-declaration bundles (default blocks) keep the
 * trailing sentinel instead.
 */
internal fun Appendable.appendActionsFunction(
    handlers: List<HandlerGen>,
    bundleType: String,
    configureType: String?,
    receiver: String?,
    visibility: String,
    indent: String = "",
) {
    val passthrough = handlers.joinToString(", ") { it.paramName }
    if (configureType != null) {
        appendLine(
            "$indent/** Bundles this state's handlers. [configure] appends raw koma DSL at the end of the generated `state<...> {}` block (per-state escape hatch). */",
        )
    }
    val receiverPart = receiver?.let { "$it." } ?: ""
    appendLine("$indent$visibility fun ${receiverPart}actions(")
    handlers.forEach { appendLine("$indent    ${it.paramName}: ${it.handlerFunctionType},") }
    if (configureType != null) {
        appendLine("$indent    configure: $configureType = {},")
        appendLine("$indent): $bundleType = $bundleType($passthrough, configure)")
    } else {
        appendLine("$indent    $SENTINEL_PARAM")
        appendLine("$indent): $bundleType = $bundleType($passthrough)")
    }
}

/**
 * One `states(...)` builder function. With [receiver] it is the top-level companion
 * extension; without it, the same-signature mirror generated as a member of the bundle's
 * scope type. Each scope-lambda parameter is evaluated exactly once in the body
 * (`Scope().param()`); passing a value only triggers the bundle's self-returning `invoke`.
 *
 * With [configureScopeType] the trailing param is the optional per-state escape block
 * (replacing the sentinel — the trailing lambda is an intended input there); the collected
 * scope is stored in the bundle so the root `states()` can append the blocks inside the
 * generated `state<...> {}` blocks. Without it (no escape members) the sentinel stays.
 */
internal fun Appendable.appendStatesFunction(
    params: List<StatesParam>,
    bundleType: String,
    receiver: String?,
    visibility: String,
    configureScopeType: String? = null,
    indent: String = "",
    kdoc: String? = null,
) {
    kdoc?.let { appendLine("$indent$it") }
    val receiverPart = receiver?.let { "$it." } ?: ""
    appendLine("$indent$visibility fun ${receiverPart}states(")
    params.forEach { appendLine("$indent    ${it.name}: ${it.paramType},") }
    if (configureScopeType != null) {
        appendLine("$indent    ${statesConfigureParam(configureScopeType)}")
    } else {
        appendLine("$indent    $SENTINEL_PARAM")
    }
    val args =
        buildList {
            params.forEach { add(it.evalExpr) }
            configureScopeType?.let { add("$it().apply(configure)") }
        }
    if (args.isEmpty()) {
        appendLine("$indent): $bundleType = $bundleType()")
    } else {
        appendLine("$indent): $bundleType =")
        appendLine("$indent    $bundleType(")
        args.forEach { appendLine("$indent        $it,") }
        appendLine("$indent    )")
    }
}

/**
 * One bundle-scope class section: the receiver type of the scope-lambda form, carrying the
 * same-signature mirrors of the bundle's builder extensions as members. Annotated with both
 * @KomaStrictDsl and koma's @KomaStoreDsl so nested scope lambdas cannot reach an outer
 * scope's builders (and the koma builder API does not leak in) through implicit receivers.
 */
internal fun scopeClassSection(
    env: CodegenEnv,
    scopeType: String,
    kdoc: String,
    members: List<String>,
): String =
    buildString {
        appendLine(kdoc)
        appendLine("@KomaStrictDsl")
        appendLine("@$KOMA_CORE_PACKAGE.KomaStoreDsl")
        appendLine("${env.visibility} class $scopeType internal constructor() {")
        members.forEachIndexed { index, member ->
            if (index > 0) appendLine()
            append(member)
        }
        appendLine("}")
    }
