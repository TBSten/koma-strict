package me.tbsten.koma.strict.ksp.model

import me.tbsten.koma.strict.InternalKomaStrictApi

/**
 * Common shape of a handler declared on a node (one annotation's worth).
 * Naming (trigger tokens / param names) is derived by pure functions in the naming package.
 */
@InternalKomaStrictApi
public sealed interface HandlerDecl {
    /** Emit whitelist (`emit = [...]` declaration, in source declaration order). References to event types + construction info. */
    public val emits: List<EventRef>
}

/**
 * A handler with transition capability (enter / action / recover).
 * Only exit has no transition capability at all, because koma's ExitScope cannot transition ([ExitHandler]).
 */
@InternalKomaStrictApi
public sealed interface TransitionHandlerDecl : HandlerDecl {
    public val transition: TransitionSpec
}

/** An `@OnEnter(nextState, emit)` declaration. Attached to leaves only. */
@InternalKomaStrictApi
public data class EnterHandler(
    override val transition: TransitionSpec,
    override val emits: List<EventRef> = emptyList(),
) : TransitionHandlerDecl

/** An `@OnExit(emit)` declaration (leaf / intermediate sealed / root). Its only capability is emit. */
@InternalKomaStrictApi
public data class ExitHandler(
    override val emits: List<EventRef> = emptyList(),
) : HandlerDecl

/**
 * An `@OnAction<A>(nextState, emit)` declaration (leaf / intermediate sealed / root).
 * Attached to an intermediate node or the root, it becomes a scope-shared action (= the default block).
 */
@InternalKomaStrictApi
public data class ActionHandler(
    /** The action type (`@OnAction`'s type argument). */
    val action: TypeRef,
    override val transition: TransitionSpec,
    override val emits: List<EventRef> = emptyList(),
) : TransitionHandlerDecl

/** An `@OnRecover<E>(nextState, emit)` declaration (leaf / intermediate sealed / root). */
@InternalKomaStrictApi
public data class RecoverHandler(
    /** The exception type to catch (`@OnRecover`'s type argument). */
    val exception: TypeRef,
    override val transition: TransitionSpec,
    override val emits: List<EventRef> = emptyList(),
) : TransitionHandlerDecl
