package me.tbsten.koma.strict.idea.model

/**
 * Placeholder shown for an annotation value the frontend could not resolve to a concrete in-store
 * declaration — an error type (half-typed code) or a class from another store. A `nextState` /
 * `emit` element rendered as `"?"` (or `"?" + shortName` for a resolvable-but-foreign target) means
 * "declared, but not a normal in-store target". Its presence marks the model as partially unresolved
 * so the UI can flag incomplete analysis instead of silently dropping the value.
 */
const val UNRESOLVED_MARKER: String = "?"

/**
 * A trigger declared on a state node, in the slim shape the diagram needs (`ide.md` semantics table).
 *
 * Each variant carries the transition target leaves ([targets]), whether staying is allowed ([stay]),
 * and the emitted event simple names ([emits], Mealy `trigger / Event`). `@OnExit` is modelled
 * separately by [ExitInfo] because koma's ExitScope cannot transition (no targets / no stay).
 */
sealed interface DiagramTrigger {
    /** Target concrete leaves (source declaration order). Empty when the trigger only stays / emits. */
    val targets: List<StateId>

    /** `Stay::class` was declared (or the empty-`nextState` sugar) — drawn as a self-loop. */
    val stay: Boolean

    /** Emitted event simple names (`emit = [...]`), source order. Unresolved elements are kept as [UNRESOLVED_MARKER]. */
    val emits: List<String>

    /**
     * `nextState` elements that don't resolve to an in-store leaf / group — a foreign state (kept as
     * `"?"` + its short name) or an unresolvable error type (kept as `"?"`). They are surfaced in the
     * transition table but never drawn as diagram edges (no leaf to attach). Non-empty ⇒ the model is
     * partially unresolved (see [DiagramStateNode.hasUnresolvedDeclarations]).
     */
    val unresolvedTargets: List<String>
}

/** True when this trigger declares any value the frontend could not resolve (foreign / error type). */
val DiagramTrigger.hasUnresolvedValue: Boolean
    get() = unresolvedTargets.isNotEmpty() || emits.any { it == UNRESOLVED_MARKER }

/** An `@OnEnter(nextState, emit)` trigger. Leaves only. */
data class EnterTrigger(
    override val targets: List<StateId>,
    override val stay: Boolean = false,
    override val emits: List<String> = emptyList(),
    override val unresolvedTargets: List<String> = emptyList(),
) : DiagramTrigger

/**
 * An `@OnAction<A>(nextState, emit)` trigger. On a leaf it is a normal `(state, action)` handler;
 * on the root / an intermediate sealed node it is a scope-shared action (rendered from an any-state
 * pseudo node).
 */
data class ActionTrigger(
    /** Simple name of the action type (`@OnAction`'s type argument), e.g. `Reload`. [UNRESOLVED_MARKER] when unresolved. */
    val actionName: String,
    override val targets: List<StateId>,
    override val stay: Boolean = false,
    override val emits: List<String> = emptyList(),
    override val unresolvedTargets: List<String> = emptyList(),
) : DiagramTrigger

/**
 * An `@OnRecover<E>(nextState, emit)` trigger. On the root / an intermediate sealed node it is a
 * scope-shared recover.
 */
data class RecoverTrigger(
    /** Simple name of the caught exception type (`@OnRecover`'s type argument). [UNRESOLVED_MARKER] when unresolved. */
    val exceptionName: String,
    override val targets: List<StateId>,
    override val stay: Boolean = false,
    override val emits: List<String> = emptyList(),
    override val unresolvedTargets: List<String> = emptyList(),
) : DiagramTrigger

/**
 * An `@OnExit(emit)` declaration. It has no transition capability, so it is not a [DiagramTrigger];
 * the diagram draws it as a badge beside the node rather than an edge (see `ide.all.md` §5).
 */
data class ExitInfo(
    /** Emitted event simple names on exit. */
    val emits: List<String> = emptyList(),
)
