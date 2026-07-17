package me.tbsten.koma.strict.ksp.naming

import me.tbsten.koma.strict.InternalKomaStrictApi
import me.tbsten.koma.strict.ksp.model.ActionHandler
import me.tbsten.koma.strict.ksp.model.EnterHandler
import me.tbsten.koma.strict.ksp.model.ExitHandler
import me.tbsten.koma.strict.ksp.model.HandlerDecl
import me.tbsten.koma.strict.ksp.model.RecoverHandler
import me.tbsten.koma.strict.ksp.model.RootNode
import me.tbsten.koma.strict.ksp.model.StatePath
import me.tbsten.koma.strict.ksp.model.TransitionHandlerDecl

// 生成型名 = 「path から root を除いた連結」+ 種別 (doc/internal/generate-strict-store-factory-dsl.md §生成物)。
// 例: Loading -> LoadingEnterScope / LoadingHandlers、
//     Stable.Idle -> StableIdleRefreshReaction。
//     root 直付き共有は {Root}{Trigger} 形 (MyStateLogoutScope)。

/**
 * Generated-type prefix of a node.
 * For the root (empty path), the underPackageName is concatenated
 * (`FooScreen.State` -> `FooScreenState`) to avoid nested same-simpleName collisions
 * within a package (same approach as StoreFactoryName.kt).
 *
 * Leaf prefixes do not include the root (the short type names of samples.md, such as
 * `LoadingHandlers`, are canonical), so top-level type names can collide, e.g. between
 * same-named leaves of different @StoreSpec hierarchies in the same package —
 * such collisions are turned into KSP errors by the frontend validation
 * (the generated-type-name-collision diagnostic in core/storeSpec, enumerated by
 * [me.tbsten.koma.strict.ksp.codegen.generatedTopLevelTypeNames]).
 */
@InternalKomaStrictApi
public fun generatedTypePrefix(
    root: RootNode,
    path: StatePath,
): String = if (path.isRoot) rootTypePrefix(root) else path.segments.joinToString("")

internal fun rootTypePrefix(root: RootNode): String = root.type.underPackageName.replace(".", "")

/**
 * A handler's trigger token: the {Trigger} part of generated type names.
 * `Enter` / `Exit` / the action's simpleName / `Recover{ExceptionSimpleName}`.
 */
@InternalKomaStrictApi
public fun handlerTriggerToken(handler: HandlerDecl): String =
    when (handler) {
        is EnterHandler -> "Enter"
        is ExitHandler -> "Exit"
        is ActionHandler -> handler.action.simpleName
        is RecoverHandler -> "Recover" + handler.exception.simpleName
    }

/** Per-handler scope type name (e.g. `LoadingEnterScope` / `AuthenticatingExitScope`). */
@InternalKomaStrictApi
public fun handlerScopeTypeName(
    prefix: String,
    handler: HandlerDecl,
): String = prefix + handlerTriggerToken(handler) + "Scope"

/** Per-handler Reaction type name (e.g. `StableIdleLoadMoreReaction`). Not generated for exit. */
@InternalKomaStrictApi
public fun handlerReactionTypeName(
    prefix: String,
    handler: TransitionHandlerDecl,
): String = prefix + handlerTriggerToken(handler) + "Reaction"

/** Per-handler Transitions type name (e.g. `LoadingEnterTransitions`). Not generated for exit. */
@InternalKomaStrictApi
public fun handlerTransitionsTypeName(
    prefix: String,
    handler: TransitionHandlerDecl,
): String = prefix + handlerTriggerToken(handler) + "Transitions"

/** Concrete implementation type name for interface-declared states (e.g. `LoadingImpl`). */
@InternalKomaStrictApi
public fun implTypeName(prefix: String): String = prefix + "Impl"

/** Per-node Handlers type name (e.g. `LoadingHandlers`). */
@InternalKomaStrictApi
public fun handlersTypeName(prefix: String): String = prefix + "Handlers"

/** GroupHandlers type name for intermediate nodes (e.g. `StableGroupHandlers`). */
@InternalKomaStrictApi
public fun groupHandlersTypeName(prefix: String): String = prefix + "GroupHandlers"

/**
 * Scope type name of a handlers bundle (`LoadingHandlers` -> `LoadingHandlersScope`).
 * The scope is the receiver of the scope-lambda form of the generated `states(...)`
 * parameters; it mirrors the bundle's builder extensions (`actions(...)` / `states(...)`).
 */
@InternalKomaStrictApi
public fun bundleScopeTypeName(bundleTypeName: String): String = bundleTypeName + "Scope"

/**
 * Handlers type name of a default block (shared declarations on the root / an intermediate node).
 * The default name is capitalized and concatenated
 * (e.g. `TabsState` + `default` -> `TabsStateDefaultHandlers`).
 */
@InternalKomaStrictApi
public fun defaultHandlersTypeName(
    prefix: String,
    defaultName: String,
): String = prefix + defaultName.capitalized() + "Handlers"

/**
 * Scope type name of the trailing per-state escape block of a `states(...)` call
 * (e.g. root `FeedState` -> `FeedStateStatesConfigureScope`, group `Stable` ->
 * `StableStatesConfigureScope`). Named after the `configure` parameter it is the
 * receiver of, mirroring the leaf `actions(configure = ...)` vocabulary.
 */
@InternalKomaStrictApi
public fun statesConfigureScopeTypeName(prefix: String): String = prefix + "StatesConfigureScope"

/**
 * Source reference to a state type as seen from the declaring package (e.g. `LceState.Loading`).
 * The root (empty path) is the root type itself. Generated code lives in the same package
 * as the declarations, so no package qualification is needed.
 */
@InternalKomaStrictApi
public fun stateTypeReference(
    root: RootNode,
    path: StatePath,
): String = (listOf(root.type.underPackageName) + path.segments).joinToString(".")
