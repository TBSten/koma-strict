package me.tbsten.koma.strict.ksp.codegen

import me.tbsten.koma.strict.ksp.model.StateParent
import me.tbsten.koma.strict.ksp.model.StatePath
import me.tbsten.koma.strict.ksp.model.hasAnyHandlerDeclarations
import me.tbsten.koma.strict.ksp.naming.stateParamName

// states() の trailing `configure` block = per-state escape の生成
// (.local/design/facade-named-arguments.md「確定: states() の trailing block = per-state escape(2026-07-17)」)。
// escape scope の member = 子 state 名。leaf member はその leaf の state ブロック末尾へ、
// 中間 sealed member は subtree の全生成 state ブロックへ展開する (共有宣言の展開と同型)。
// member が 1 つも無い states() には escape param を付けずセンチネルを維持する
// (空 escape は無意味 + 自宣言のみ group の v4 登録 builder と単一 lambda の解決が曖昧になるため)。

/** One member of a states() escape scope (`configure` block): a child state with declarations. */
internal class StatesConfigureMember(
    val name: String,
    val targetPath: StatePath,
    /** True = intermediate sealed child: the block is a shared escape expanded into every generated leaf block of the subtree. */
    val isGroup: Boolean,
)

/**
 * The escape members of [parent]'s `states(...)` (child states with declarations, in source
 * declaration order — the same set as the child params, so every addressable member has at
 * least one generated `state<...> {}` block to append to). The default block gets no member
 * (it does not correspond to a single koma state block, like the leaf `configure` rule).
 */
internal fun statesConfigureMembers(
    env: CodegenEnv,
    path: StatePath,
    parent: StateParent,
): List<StatesConfigureMember> =
    parent.children
        .filter { it.hasAnyHandlerDeclarations() }
        .map { child ->
            StatesConfigureMember(
                name = stateParamName(child.simpleName),
                targetPath = path + child.simpleName,
                isGroup = child is StateParent,
            )
        }

/** The trailing escape param of a states() function (callers prepend their own indent). */
internal fun statesConfigureParam(scopeType: String): String = "configure: $scopeType.() -> Unit = {},"

/**
 * The escape scope class section. The KDoc carries the overlap contract **measured against
 * koma-core rc02** (integrationTest LceOverlappingHandlersTest): first-registered wins.
 */
internal fun statesConfigureScopeSection(
    env: CodegenEnv,
    scopeType: String,
    owner: String,
    members: List<StatesConfigureMember>,
): String =
    buildString {
        appendLine("/**")
        appendLine(" * Receiver of the trailing `configure` block of the matching `states(...)` call — the")
        appendLine(" * per-state escape hatch. Each member appends raw koma DSL to the generated `state<...> {}`")
        appendLine(" * block(s) of the same-named child state, after the generated registrations (and after the")
        appendLine(" * leaf's own `actions(configure = ...)` block). Calling the same member twice fails fast")
        appendLine(" * with [IllegalStateException].")
        appendLine(" *")
        appendLine(" * Overlapping registrations (measured against koma-core rc02): koma dispatches at most one")
        appendLine(" * handler per trigger — when multiple registered handlers match the same state and trigger,")
        appendLine(" * the **first registered** one runs and later ones are silently ignored. Generated")
        appendLine(" * registrations always precede this escape block, so a raw handler for a trigger already")
        appendLine(" * declared on the state never runs; use this escape for what the declarations do not cover")
        appendLine(" * (raw `enter` / `exit` / `action<A>` / `recover<T>` of undeclared triggers, `launch`,")
        appendLine(" * dispatcher overrides, ...).")
        appendLine(" */")
        appendLine("@KomaStrictDsl")
        appendLine("@$KOMA_CORE_PACKAGE.KomaStoreDsl")
        appendLine("${env.visibility} class $scopeType internal constructor() {")

        val sections =
            buildList {
                members.forEach { member ->
                    add("    internal var ${member.name}: (${blockType(env, member)})? = null")
                }
                members.forEach { member ->
                    add(
                        buildString {
                            if (member.isGroup) {
                                appendLine(
                                    "    /** Appends raw koma DSL to every generated `state<...> {}` block under [${env.stateRef(member.targetPath)}] (a shared escape, expanded like shared declarations). Fails fast if called twice. */",
                                )
                            } else {
                                appendLine(
                                    "    /** Appends raw koma DSL at the end of the generated `state<${env.stateRef(member.targetPath)}> {}` block. Fails fast if called twice. */",
                                )
                            }
                            appendLine("    public fun ${member.name}(block: ${blockType(env, member)}) {")
                            appendLine(
                                "        if (this.${member.name} != null) throwDuplicateBuilderEntry(\"$owner\", \"${member.name}\")",
                            )
                            appendLine("        this.${member.name} = block")
                            append("    }")
                        },
                    )
                }
            }
        sections.forEachIndexed { index, section ->
            if (index > 0) appendLine()
            appendLine(section)
        }
        appendLine("}")
    }

/**
 * Block type of one escape member: koma's per-state builder receiver, typed to the member's
 * state node. For group members the leaf blocks apply it through an unchecked cast on the
 * (invariant) `S2` — sound because `S2` only occurs in produced-scope positions, and koma
 * itself narrows the same way inside `action<A2>` (rc02 sources).
 */
private fun blockType(
    env: CodegenEnv,
    member: StatesConfigureMember,
): String = "${env.stateHandlerConfigRef(member.targetPath)}.() -> Unit"

/**
 * The escape applications appended at the end of one leaf's `state<...> {}` block,
 * innermost states() level first, the root escape last (registration order follows source
 * order: inner escapes are written before the root's trailing block). Levels whose escape
 * has no member for the next path segment contribute nothing.
 */
internal fun leafEscapeApplications(
    env: CodegenEnv,
    leafPath: StatePath,
): List<String> =
    buildList {
        val segments = leafPath.segments
        for (i in segments.size - 1 downTo 0) {
            val levelPath = StatePath(segments.take(i))
            val targetPath = StatePath(segments.take(i + 1))
            // member が生えているのは「宣言を持つ子」だけ (statesConfigureMembers と同じ条件)
            if (!env.nodeAt(targetPath).hasAnyHandlerDeclarations()) continue
            val member = stateParamName(segments[i])
            val ownerExpr =
                if (levelPath.isRoot) {
                    "configure"
                } else {
                    levelPath.segments.joinToString(".") { stateParamName(it) } + ".configure"
                }
            val needsCast = i + 1 < segments.size
            add(
                if (needsCast) {
                    "$ownerExpr.$member?.invoke(this as ${env.stateHandlerConfigRef(targetPath)})"
                } else {
                    "$ownerExpr.$member?.invoke(this)"
                },
            )
        }
    }
