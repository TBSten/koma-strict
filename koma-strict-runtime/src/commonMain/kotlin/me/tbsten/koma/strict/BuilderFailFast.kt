package me.tbsten.koma.strict

// 生成される builder 形式 (`actions { ... }` / `states { ... }`) の構築時 fail-fast の SSoT。
// メッセージ本文をここに一元化し、生成コードは owner (state 参照) と entry 名だけを渡す。

/**
 * Throws the build-time fail-fast error for a duplicate registration inside a generated
 * builder block (`actions { ... }` / `states { ... }`).
 *
 * The builder form cannot enforce "register exactly once" at compile time, so the generated
 * builder members call this as soon as the same handler / child state is registered twice.
 *
 * Called by generated code only; not intended to be called by users.
 *
 * @param owner Source reference of the declaring state (e.g. `LceState.Content`), plus the
 *   default-block name for shared declarations (e.g. `FlowState.Refresh.refreshCommon`).
 * @param entry The builder member registered twice (a handler name such as `reload`,
 *   a child state name such as `idle`, or `configure`).
 */
public fun throwDuplicateBuilderEntry(
    owner: String,
    entry: String,
): Nothing =
    throw IllegalStateException(
        "koma-strict builder for $owner: '$entry' is already registered. " +
            "Register each declared handler / child state exactly once in the builder block.",
    )

/**
 * Throws the build-time fail-fast error for declared handlers / child states that are still
 * unregistered when a generated builder block (`actions { ... }` / `states { ... }`) returns.
 *
 * The builder form checks exhaustiveness only at build time (= store construction time),
 * which is why the message points back to the named-argument form where the same mistake
 * is a compile-time error.
 *
 * Called by generated code only; not intended to be called by users.
 *
 * @param owner Source reference of the declaring state (see [throwDuplicateBuilderEntry]).
 * @param missing The unregistered entries, in declaration order.
 */
public fun throwMissingBuilderEntries(
    owner: String,
    missing: List<String>,
): Nothing =
    throw IllegalStateException(
        "koma-strict builder for $owner: missing ${missing.joinToString(", ") { "'$it'" }}. " +
            "The builder form (actions { ... } / states { ... }) checks exhaustiveness only at build time; " +
            "the named-argument form turns missing handlers into a compile-time error.",
    )
