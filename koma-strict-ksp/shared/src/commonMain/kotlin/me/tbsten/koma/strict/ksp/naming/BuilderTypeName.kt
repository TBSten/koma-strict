package me.tbsten.koma.strict.ksp.naming

import me.tbsten.koma.strict.InternalKomaStrictApi

// builder 形式 (第 3 の書き方) の生成型名。命名は将来の rename 案 (onEnter / onExit member 等)
// を差し替えやすいよう naming/ に集約する (生成条件の一元点は codegen/AppendHandlersBuilder.kt)。

/** Actions-builder type name of a leaf node (e.g. `Content` -> `ContentActionsBuilder`). */
@InternalKomaStrictApi
public fun actionsBuilderTypeName(prefix: String): String = prefix + "ActionsBuilder"

/**
 * Actions-builder type name of a default block (shared declarations on the root / an
 * intermediate node). The default name is capitalized and concatenated, mirroring
 * [defaultHandlersTypeName] (e.g. `TabsState` + `default` -> `TabsStateDefaultActionsBuilder`).
 */
@InternalKomaStrictApi
public fun defaultActionsBuilderTypeName(
    prefix: String,
    defaultName: String,
): String = prefix + defaultName.capitalized() + "ActionsBuilder"

/**
 * Group-builder type name of an intermediate node (e.g. `Stable` -> `StableGroupBuilder`).
 * For self-declaring groups the builder additionally carries the default-name member and
 * builds the composite Handlers, but the name stays the same.
 */
@InternalKomaStrictApi
public fun groupBuilderTypeName(prefix: String): String = prefix + "GroupBuilder"
