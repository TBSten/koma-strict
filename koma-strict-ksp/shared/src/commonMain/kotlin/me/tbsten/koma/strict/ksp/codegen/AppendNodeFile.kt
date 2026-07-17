package me.tbsten.koma.strict.ksp.codegen

import me.tbsten.koma.strict.ksp.model.GroupNode
import me.tbsten.koma.strict.ksp.model.LeafNode
import me.tbsten.koma.strict.ksp.model.StateDeclarationKind
import me.tbsten.koma.strict.ksp.model.StateNode
import me.tbsten.koma.strict.ksp.model.StateParent
import me.tbsten.koma.strict.ksp.model.StatePath
import me.tbsten.koma.strict.ksp.naming.bundleScopeTypeName
import me.tbsten.koma.strict.ksp.naming.defaultHandlersTypeName
import me.tbsten.koma.strict.ksp.naming.handlersTypeName
import me.tbsten.koma.strict.ksp.naming.implTypeName

// per-node の生成ファイル本文 (1 node = 1 file)。
// 期待形は doc/internal/samples.md。セクションを空行区切りで連結する。

/** Writes all sections of a node file. Writes nothing for nodes that generate nothing (no file is created). */
internal fun Appendable.appendNodeFile(
    env: CodegenEnv,
    path: StatePath,
    node: StateNode,
) {
    val sections =
        buildList {
            if (node is LeafNode && node.declarationKind == StateDeclarationKind.INTERFACE) {
                add(buildString { appendImpl(env, path, node) })
                node.companionName?.let { companionName ->
                    add(buildString { appendFactory(env, path, companionName) })
                }
            }
            val handlers = env.handlersOf(path, node)
            handlers.forEach { handler ->
                if (handler.reactionType != null) add(buildString { appendReaction(env, handler) })
                if (handler.transitionsType != null) add(buildString { appendTransitions(env, handler) })
                add(buildString { appendScope(env, handler) })
            }
            if (handlers.isNotEmpty()) {
                addAll(nodeHandlersBundleSections(env, path, node, handlers))
            }
            if (node is GroupNode) {
                addAll(groupStatesBundleSections(env, path, node))
            }
        }
    append(sections.joinToString("\n"))
}

/**
 * The concrete implementation of an interface-declared state. Kept private because both
 * users and other generated files construct it through the public factory. Only states
 * without a companion (no factory) are internal, since transitions in other files
 * construct them directly.
 */
private fun Appendable.appendImpl(
    env: CodegenEnv,
    path: StatePath,
    node: LeafNode,
) {
    val visibility = if (node.hasCompanion) "private" else "internal"
    val implName = implTypeName(env.prefix(path))
    val props = env.constructionParams(path)
    if (props.isEmpty()) {
        appendLine("$visibility data object $implName : ${env.stateRef(path)}")
    } else if (props.size == 1) {
        val prop = props.single()
        appendLine(
            "$visibility data class $implName(override val ${prop.name}: ${prop.renderedType()}) : ${env.stateRef(path)}",
        )
    } else {
        appendLine("$visibility data class $implName(")
        props.forEach { prop ->
            appendLine("    override val ${prop.name}: ${prop.renderedType()},")
        }
        appendLine(") : ${env.stateRef(path)}")
    }
}

/**
 * Factory of an interface-declared state (`operator fun Companion.invoke`, syntactically
 * compatible with a constructor). The receiver uses the companion's actual name
 * (see [me.tbsten.koma.strict.ksp.model.StateNode.companionName]).
 */
private fun Appendable.appendFactory(
    env: CodegenEnv,
    path: StatePath,
    companionName: String,
) {
    val stateRef = env.stateRef(path)
    val implName = implTypeName(env.prefix(path))
    val props = env.constructionParams(path)
    val construction = if (props.isEmpty()) implName else "$implName(${props.joinToString(", ") { it.name }})"
    if (props.size <= 1) {
        val params = props.joinToString(", ") { "${it.name}: ${it.renderedType()}" }
        appendLine("${env.visibility} operator fun $stateRef.$companionName.invoke($params): $stateRef = $construction")
    } else {
        appendLine("${env.visibility} operator fun $stateRef.$companionName.invoke(")
        props.forEach { prop ->
            appendLine("    ${prop.name}: ${prop.renderedType()},")
        }
        appendLine("): $stateRef = $construction")
    }
}

/** Handlers type name of a node: `{Prefix}Handlers` for leaves, default-name concatenation for the root / intermediate nodes (shared declarations). */
internal fun nodeHandlersTypeName(
    env: CodegenEnv,
    path: StatePath,
    node: StateNode,
): String {
    val prefix = env.prefix(path)
    return if (node is StateParent) defaultHandlersTypeName(prefix, node.defaultName) else handlersTypeName(prefix)
}

/**
 * The bundle sections: Handlers / companion `actions()` (named + builder overloads) /
 * HandlersScope (the mirrors) / the actions builder.
 *
 * Leaf Handlers additionally hold the per-state `configure` escape hatch (a raw koma
 * `StateHandlerConfig` block invoked at the end of the generated `state<X> {}` block).
 * Shared declarations on the root / intermediate nodes (default blocks) do not get
 * `configure` because they do not correspond to a single koma state block; their
 * `actions()` keeps the trailing sentinel instead.
 *
 * The builder overload (`actions { ... }`) and its builder class are generated only for
 * nodes without enter / exit declarations ([actionsBuilderSupported] — the one-place policy
 * in AppendHandlersBuilder.kt); other nodes keep the named-argument form only.
 */
private fun nodeHandlersBundleSections(
    env: CodegenEnv,
    path: StatePath,
    node: StateNode,
    handlers: List<HandlerGen>,
): List<String> =
    buildList {
        val handlersType = nodeHandlersTypeName(env, path, node)
        val configureType = if (node is LeafNode) "${env.stateHandlerConfigRef(path)}.() -> Unit" else null
        val builderType = nodeActionsBuilderTypeName(env, path, node).takeIf { actionsBuilderSupported(handlers) }

        add(
            bundleClassSection(
                env,
                bundleType = handlersType,
                constructorProperties =
                    buildList {
                        handlers.forEach { add("internal val ${it.paramName}: ${it.handlerFunctionType}") }
                        configureType?.let { add("internal val configure: $it") }
                    },
            ),
        )

        // Handlers の構築経路 = companion 拡張の actions() (値渡し形式)。
        // data object 宣言は companion を持てないため object 自身への拡張として生やす。
        val actionsReceiver =
            when {
                node is LeafNode && node.declarationKind == StateDeclarationKind.DATA_OBJECT -> env.stateRef(path)
                // companion の実名で receiver を組む (無名でも soft keyword を食って別名になりうる — StateNode.companionName 参照)
                node.companionName != null -> "${env.stateRef(path)}.${node.companionName}"
                else -> null // companion 必須検証で reject 済み (防御的 skip)
            }
        if (actionsReceiver != null) {
            add(
                buildString {
                    appendActionsFunction(
                        handlers = handlers,
                        bundleType = handlersType,
                        configureType = configureType,
                        receiver = actionsReceiver,
                        visibility = env.visibility,
                    )
                },
            )
            builderType?.let {
                add(
                    buildString {
                        appendActionsBuilderFunction(
                            builderType = it,
                            bundleType = handlersType,
                            receiver = actionsReceiver,
                            visibility = env.visibility,
                        )
                    },
                )
            }
        }

        // scope lambda 形式 (`loading = { actions(...) }`) の receiver。actions() ミラーは
        // member なので companion 非依存 — Handlers の supertype が scope を参照するため常に生成する
        add(
            scopeClassSection(
                env,
                scopeType = bundleScopeTypeName(handlersType),
                kdoc =
                    "/** Receiver of the scope-lambda form (`{ actions(...) }`) of the matching `states(...)` parameter. [actions] mirrors the companion `actions(...)` extension. */",
                members =
                    buildList {
                        add(
                            buildString {
                                appendActionsFunction(
                                    handlers = handlers,
                                    bundleType = handlersType,
                                    configureType = configureType,
                                    receiver = null,
                                    visibility = "public",
                                    indent = "    ",
                                )
                            },
                        )
                        builderType?.let {
                            add(
                                buildString {
                                    appendActionsBuilderFunction(
                                        builderType = it,
                                        bundleType = handlersType,
                                        receiver = null,
                                        visibility = "public",
                                        indent = "    ",
                                    )
                                },
                            )
                        }
                    },
            ),
        )

        builderType?.let {
            add(
                actionsBuilderClassSection(
                    env,
                    builderType = it,
                    bundleType = handlersType,
                    owner = builderOwnerRef(env, path, node),
                    handlers = handlers,
                    configureType = configureType,
                ),
            )
        }
    }
