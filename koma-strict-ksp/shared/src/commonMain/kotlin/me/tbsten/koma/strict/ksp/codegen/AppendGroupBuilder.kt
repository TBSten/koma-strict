package me.tbsten.koma.strict.ksp.codegen

// builder 形式 (第 3 の書き方) のうち中間 sealed の `states { ... }` の生成。
// member 名 = states(...) の param 名 (default 名 / 子 state 名)。値渡しと builder ネストの両 overload。
// 網羅チェックは構築時 fail-fast (AppendHandlersBuilder.kt の appendBuilderBuildFunction を共有)。

/**
 * The `states { ... }` builder overload. With [receiver] it is the companion extension;
 * without it, the same-signature mirror generated as a member of the bundle's scope type.
 * For self-declaring groups the built bundle is the composite Handlers (the builder carries
 * the default-name member), otherwise the children-only GroupHandlers.
 * The KDoc must state that exhaustiveness is checked at build time (spec requirement).
 */
internal fun Appendable.appendStatesBuilderFunction(
    builderType: String,
    bundleType: String,
    receiver: String?,
    visibility: String,
    indent: String = "",
) {
    appendLine("$indent/**")
    appendLine("$indent * Builder-form overload of `states(...)` (see [$builderType]).")
    appendLine("$indent *")
    appendLine("$indent * Note: unlike the named-argument overload, exhaustiveness of the child states (and the")
    appendLine("$indent * default block) is checked **at build time** (fail-fast when the block finishes), not at")
    appendLine("$indent * compile time.")
    appendLine("$indent */")
    val receiverPart = receiver?.let { "$it." } ?: ""
    appendLine(
        "$indent$visibility fun ${receiverPart}states(build: $builderType.() -> Unit): $bundleType = " +
            "$builderType().apply(build).build()",
    )
}

/**
 * The group-builder class section of an intermediate node: one registration member per
 * `states(...)` parameter (the default block first for self-declaring groups, then the child
 * states in declaration order). Every member has the value-passing overload; entries whose
 * own builder exists additionally get the nested-builder overload ([StatesParam.nestedBuilderType]
 * — child states declaring enter / exit can only be registered by value). Duplicate
 * registration fails immediately; `build()` throws when entries are missing.
 */
internal fun groupBuilderClassSection(
    env: CodegenEnv,
    builderType: String,
    bundleType: String,
    owner: String,
    params: List<StatesParam>,
    /** Extra trailing constructor args of the built bundle (e.g. an empty escape scope — the builder form collects no escape). */
    buildTrailingArgs: List<String> = emptyList(),
): String =
    buildString {
        appendLine("/**")
        appendLine(" * Builder receiver of the `states { ... }` overload building [$bundleType].")
        appendLine(" *")
        appendLine(" * Each member function registers the same-named `states(...)` parameter, either by an")
        appendLine(" * already-built value or with a nested builder block. Exhaustiveness is checked when the")
        appendLine(" * block finishes (build-time fail-fast), not at compile time: entries left unregistered —")
        appendLine(" * and any duplicate registration — throw [IllegalStateException]. Prefer the named-argument")
        appendLine(" * `states(...)` overload to catch missing entries at compile time.")
        appendLine(" */")
        appendLine("@KomaStrictDsl")
        appendLine("@$KOMA_CORE_PACKAGE.KomaStoreDsl")
        appendLine("@OptIn(InternalKomaStrictApi::class)")
        appendLine("${env.visibility} class $builderType internal constructor() {")

        params.forEach { param ->
            appendLine("    ${setOnceSlotDeclaration(param.name, param.handlersType, owner)}")
            appendLine()
        }

        params.forEach { param ->
            val what = if (param.isDefaultBlock) "the shared default block" else "this child state's handlers"
            appendLine(
                "    /** Registers $what by an already-built value. Duplicate registration fails fast with [IllegalStateException]. */",
            )
            appendLine("    public fun ${param.name}(handlers: ${param.handlersType}) { ${param.name}.set(handlers) }")
            appendLine()
            param.nestedBuilderType?.let { nestedBuilder ->
                appendLine(
                    "    /** Registers $what with a nested builder block (fails fast if already registered). */",
                )
                appendLine("    public fun ${param.name}(build: $nestedBuilder.() -> Unit) {")
                appendLine("        ${param.name}($nestedBuilder().apply(build).build())")
                appendLine("    }")
                appendLine()
            }
        }

        appendBuilderBuildFunction(
            bundleType = bundleType,
            owner = owner,
            requiredMembers = params.map { it.name },
            trailingArgs = buildTrailingArgs,
        )
        appendLine("}")
    }
