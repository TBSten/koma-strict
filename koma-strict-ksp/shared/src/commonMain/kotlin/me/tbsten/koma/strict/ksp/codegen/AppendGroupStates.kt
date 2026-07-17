package me.tbsten.koma.strict.ksp.codegen

import me.tbsten.koma.strict.ksp.model.GroupNode
import me.tbsten.koma.strict.ksp.model.StatePath
import me.tbsten.koma.strict.ksp.model.hasOwnHandlerDeclarations
import me.tbsten.koma.strict.ksp.naming.bundleScopeTypeName
import me.tbsten.koma.strict.ksp.naming.groupBuilderTypeName
import me.tbsten.koma.strict.ksp.naming.groupHandlersTypeName
import me.tbsten.koma.strict.ksp.naming.handlersTypeName
import me.tbsten.koma.strict.ksp.naming.statesConfigureScopeTypeName

// 中間 sealed node の束ね (GroupHandlers / 合成 Handlers / `Companion.states()` / plus / builder)。
// root の束ね (StoreBuilder への states() 拡張) は AppendRootStates.kt — 語彙 (states) は対称。
// param モデル (StatesParam / statesParams) は StatesParams.kt。
// param は scope lambda と値渡しの両対応 (`Scope.() -> Handlers` に Function1 自己返しの値も渡せる)。
// 自宣言 + 子を持つ中間 sealed は `actions(...) + states(...)` の plus 合成でも書ける
// (.local/design/facade-named-arguments.md「追記: 中間 sealed の actions + states 合成(2026-07-16)」)。

/**
 * The bundle sections of an intermediate sealed node: GroupHandlers (children only) /
 * companion `states()` / GroupHandlersScope. Self-declaring groups (own shared declarations
 * plus children) additionally get the composite sections ([compositeSections]).
 */
internal fun groupStatesBundleSections(
    env: CodegenEnv,
    path: StatePath,
    group: GroupNode,
): List<String> =
    buildList {
        val hasOwnDeclarations = group.hasOwnHandlerDeclarations()
        val allParams = statesParams(env, path, group)
        val childParams = if (hasOwnDeclarations) allParams.drop(1) else allParams
        val groupType = groupHandlersTypeName(env.prefix(path))
        // receiver は companion の実名 (無名でも soft keyword を食って別名になりうる — StateNode.companionName 参照)。
        // companion が無い中間 sealed は frontend の検証で reject 済み (防御的 skip)
        val companionReceiver = group.companionName?.let { "${env.stateRef(path)}.$it" }
        val childrenOnlyKdoc =
            if (hasOwnDeclarations) {
                "/** Bundles the child states only. Compose with the shared `actions(...)` via `+` to obtain the [${handlersTypeName(env.prefix(path))}] the parent parameter requires. */"
            } else {
                null
            }

        // builder 形式: 自宣言つきは合成型を組む builder (default 名 member 込み)、無しは GroupHandlers を組む
        val builderType = groupBuilderTypeName(env.prefix(path))
        val builderBundleType = if (hasOwnDeclarations) handlersTypeName(env.prefix(path)) else groupType

        // states() の trailing escape (per-state の素の koma DSL)。member ゼロならセンチネル維持で非生成
        val escapeMembers = statesConfigureMembers(env, path, group)
        val escapeScopeType =
            statesConfigureScopeTypeName(env.prefix(path)).takeIf { escapeMembers.isNotEmpty() }
        // 中間 companion states() で集めた escape は bundle が運搬し、root states() が leaf ブロックへ適用する
        val escapeProperty = escapeScopeType?.let { "internal val configure: $it" }

        add(
            bundleClassSection(
                env,
                groupType,
                childParams.map { "internal val ${it.name}: ${it.handlersType}" } + listOfNotNull(escapeProperty),
            ),
        )
        if (companionReceiver != null) {
            add(
                buildString {
                    appendStatesFunction(
                        params = childParams,
                        bundleType = groupType,
                        receiver = companionReceiver,
                        visibility = env.visibility,
                        configureScopeType = escapeScopeType,
                        kdoc = childrenOnlyKdoc,
                    )
                },
            )
            if (!hasOwnDeclarations) {
                // 自宣言つきの states(build) は合成型を返す (compositeSections 側)。overload は 1 つだけ
                // (children-only / combined の 2 builder overload は単一 lambda の解決が曖昧になる)
                add(
                    buildString {
                        appendStatesBuilderFunction(
                            builderType = builderType,
                            bundleType = builderBundleType,
                            receiver = companionReceiver,
                            visibility = env.visibility,
                        )
                    },
                )
            }
        }
        add(
            scopeClassSection(
                env,
                scopeType = bundleScopeTypeName(groupType),
                kdoc =
                    "/** Receiver of the scope-lambda form (`{ states(...) }`) of the matching `states(...)` parameter. [states] mirrors the companion `states(...)` extension. */",
                members =
                    buildList {
                        add(
                            buildString {
                                appendStatesFunction(
                                    params = childParams,
                                    bundleType = groupType,
                                    receiver = null,
                                    visibility = "public",
                                    configureScopeType = escapeScopeType,
                                    indent = "    ",
                                    kdoc = childrenOnlyKdoc,
                                )
                            },
                        )
                        if (!hasOwnDeclarations) {
                            add(
                                buildString {
                                    appendStatesBuilderFunction(
                                        builderType = builderType,
                                        bundleType = builderBundleType,
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

        if (hasOwnDeclarations) {
            addAll(
                compositeSections(env, path, group, allParams, childParams, companionReceiver, escapeScopeType),
            )
        }

        escapeScopeType?.let {
            add(
                statesConfigureScopeSection(
                    env,
                    scopeType = it,
                    owner = env.stateRef(path),
                    members = escapeMembers,
                ),
            )
        }

        add(
            groupBuilderClassSection(
                env,
                builderType = builderType,
                bundleType = builderBundleType,
                owner = env.stateRef(path),
                params = if (hasOwnDeclarations) allParams else childParams,
                // v4 登録 builder は escape を集めない (空 scope で埋める)
                buildTrailingArgs = listOfNotNull(escapeScopeType?.let { "$it()" }),
            ),
        )
    }

/**
 * The composite sections of a self-declaring group: the composite Handlers (the parent-facing
 * param type) / the `plus` operator composing `actions(...) + states(...)` / the combined
 * companion `states(default, children...)` overload / the composite scope with the mirrors.
 * Exhaustiveness by type: neither the shared `actions(...)` bundle nor the children-only
 * `states(...)` bundle alone matches the parent param — only `plus` (or the combined
 * `states()`) produces the composite.
 */
private fun compositeSections(
    env: CodegenEnv,
    path: StatePath,
    group: GroupNode,
    allParams: List<StatesParam>,
    childParams: List<StatesParam>,
    companionReceiver: String?,
    escapeScopeType: String?,
): List<String> =
    buildList {
        val compositeType = handlersTypeName(env.prefix(path))
        val defaultType = nodeHandlersTypeName(env, path, group)
        val groupType = groupHandlersTypeName(env.prefix(path))
        val handlers = env.handlersOf(path, group)
        val builderType = groupBuilderTypeName(env.prefix(path))
        // default ブロック (自宣言) の actions builder — exit 宣言つきなら生えない (AppendHandlersBuilder.kt の一元 policy)
        val defaultBuilderType =
            nodeActionsBuilderTypeName(env, path, group).takeIf { actionsBuilderSupported(handlers) }
        val combinedKdoc =
            "/** Bundles the shared default block (`${group.defaultName}`) and the child states in one call — the same [$compositeType] as `actions(...) + states(...)`. */"
        val escapeProperty = escapeScopeType?.let { "internal val configure: $it" }

        add(
            bundleClassSection(
                env,
                compositeType,
                allParams.map { "internal val ${it.name}: ${it.handlersType}" } + listOfNotNull(escapeProperty),
            ),
        )

        add(
            buildString {
                appendLine("/**")
                appendLine(" * Composes this node's own shared handlers (built with `actions(...)`) with its child")
                appendLine(" * states' bundle (built with `states(...)`) into the [$compositeType] the parent `states(...)`")
                appendLine(" * parameter requires. Both sides are required by type: forgetting either the shared")
                appendLine(" * handlers or the child bundle is a compile error.")
                appendLine(" */")
                appendLine("${env.visibility} operator fun $defaultType.plus(children: $groupType): $compositeType =")
                val escapeArg = if (escapeScopeType != null) ", children.configure" else ""
                appendLine(
                    "    $compositeType(this${childParams.joinToString("") { ", children.${it.name}" }}$escapeArg)",
                )
            },
        )

        if (companionReceiver != null) {
            add(
                buildString {
                    appendStatesFunction(
                        params = allParams,
                        bundleType = compositeType,
                        receiver = companionReceiver,
                        visibility = env.visibility,
                        configureScopeType = escapeScopeType,
                        kdoc = combinedKdoc,
                    )
                },
            )
            add(
                buildString {
                    appendStatesBuilderFunction(
                        builderType = builderType,
                        bundleType = compositeType,
                        receiver = companionReceiver,
                        visibility = env.visibility,
                    )
                },
            )
        }

        add(
            scopeClassSection(
                env,
                scopeType = bundleScopeTypeName(compositeType),
                kdoc =
                    "/** Receiver of the scope-lambda form (`{ actions(...) + states(...) }`) of the matching `states(...)` parameter. [actions] / [states] mirror the companion extensions. */",
                members =
                    buildList {
                        add(
                            buildString {
                                appendActionsFunction(
                                    handlers = handlers,
                                    bundleType = defaultType,
                                    configureType = null,
                                    receiver = null,
                                    visibility = "public",
                                    indent = "    ",
                                )
                            },
                        )
                        defaultBuilderType?.let { defaultBuilder ->
                            add(
                                buildString {
                                    appendActionsBuilderFunction(
                                        builderType = defaultBuilder,
                                        bundleType = defaultType,
                                        receiver = null,
                                        visibility = "public",
                                        indent = "    ",
                                    )
                                },
                            )
                        }
                        add(
                            buildString {
                                appendStatesFunction(
                                    params = childParams,
                                    bundleType = groupType,
                                    receiver = null,
                                    visibility = "public",
                                    configureScopeType = escapeScopeType,
                                    indent = "    ",
                                    kdoc =
                                        "/** Bundles the child states only. Compose with the shared `actions(...)` via `+` to obtain the [$compositeType] the parent parameter requires. */",
                                )
                            },
                        )
                        add(
                            buildString {
                                appendStatesFunction(
                                    params = allParams,
                                    bundleType = compositeType,
                                    receiver = null,
                                    visibility = "public",
                                    configureScopeType = escapeScopeType,
                                    indent = "    ",
                                    kdoc = combinedKdoc,
                                )
                            },
                        )
                        add(
                            buildString {
                                appendStatesBuilderFunction(
                                    builderType = builderType,
                                    bundleType = compositeType,
                                    receiver = null,
                                    visibility = "public",
                                    indent = "    ",
                                )
                            },
                        )
                    },
            ),
        )
    }
