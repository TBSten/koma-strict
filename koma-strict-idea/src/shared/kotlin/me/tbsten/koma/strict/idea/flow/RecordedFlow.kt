package me.tbsten.koma.strict.idea.flow

import me.tbsten.koma.strict.idea.ir.EdgeKind
import me.tbsten.koma.strict.idea.model.StateId

/**
 * One recorded transition of a [RecordedFlow] (`ide-test-code.md`): the edge the user clicked in the
 * diagram plus the state it lands on. It carries both the *display* facets (the [label] and [fromId]
 * the Steps list renders) and the *codegen* facets ([kind] / [typeRef] / [target] / [stay]) that
 * [generateFlowSpec] / [generateKomaTest] turn into `FlowStep`s and koma-test calls.
 */
data class FlowTransition(
    /** Trigger family of the clicked edge. */
    val kind: EdgeKind,
    /** Package-relative action / exception reference (`FeedAction.Retry`); null for an `onEnter` edge. */
    val typeRef: String?,
    /** Rendered edge label for the Steps list (`onEnter / LoadFailed`, `retry`). */
    val label: String,
    /** State the edge left, for the Steps "from" chip. */
    val fromId: StateId?,
    /** Resulting state; null when the transition stays in the current state. */
    val target: StateId?,
    /** True for a `Stay` (`nextState = [Stay::class]`) — the current state is kept. */
    val stay: Boolean = false,
)

/**
 * The scenario recorded by clicking transitions in the state diagram (`ide-test-code.md`): a start
 * state plus the ordered transitions taken from it. Pure and immutable — every edit returns a new value
 * so the whole thing is unit-testable without the UI. The UI state holder drives the edits; the two
 * generators read it.
 */
data class RecordedFlow(
    /** Start state — the `@StoreSpec(initial)` default, or any leaf the user picks. */
    val initial: StateId? = null,
    /** Transitions taken from [initial], in click order. */
    val transitions: List<FlowTransition> = emptyList(),
) {
    /** The state the next clicked transition must leave: the last non-stay target, else [initial]. */
    val cursor: StateId?
        get() = transitions.lastOrNull { !it.stay }?.target ?: initial

    val isEmpty: Boolean get() = transitions.isEmpty()

    /** Append a clicked transition. */
    fun append(transition: FlowTransition): RecordedFlow = copy(transitions = transitions + transition)

    /** Reset to just the (possibly new) start state, dropping every transition. */
    fun withInitial(id: StateId?): RecordedFlow = RecordedFlow(initial = id)

    /** Drop every transition, keeping the start state. */
    fun cleared(): RecordedFlow = copy(transitions = emptyList())

    /**
     * Remove the Steps row [row] and everything after it. Row 0 is the [initial] node, so deleting it
     * clears every transition; row `n` (>= 1) is `transitions[n - 1]`, so deleting it truncates to
     * `transitions.take(n - 1)` — keeping the recorded path connected (`ide-test-code.md` 行削除粒度).
     */
    fun truncateFromRow(row: Int): RecordedFlow = when {
        row <= 0 -> cleared()
        else -> copy(transitions = transitions.take(row - 1))
    }
}
