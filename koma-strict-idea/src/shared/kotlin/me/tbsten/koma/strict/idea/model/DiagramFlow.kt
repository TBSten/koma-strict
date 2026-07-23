package me.tbsten.koma.strict.idea.model

/**
 * A named `@FlowSpec` path read off a `@StoreSpec` root (`flows-design.md`): the ordered steps the
 * diagram highlights when the user picks this flow. Renderer-independent — the steps carry only model
 * references ([StateId] / a package-relative trigger ref), and the `ui/diagram` layer resolves them to
 * concrete graph nodes / edges for playback (so this type stays free of the IR `GraphEdge`).
 *
 * A flow adds no capability to the store; it only names an existing path, so unresolved / invalid steps
 * are kept as [DiagramFlowStep.Unresolved] and simply skipped when playing (best-effort v1 — path
 * validation is out of scope).
 */
data class DiagramFlow(
    /** Display name: the `@FlowSpec(name = "...")` value, or the annotation-class simple name when blank. */
    val name: String,
    /** The path, interleaving node steps and edge steps, in declaration order (first / last are nodes). */
    val steps: List<DiagramFlowStep>,
    /** Click anchor of the `@Xxx` application on the root, for future navigation. Null in pure tests. */
    val source: SourceAnchor? = null,
)

/**
 * One step of a [DiagramFlow] path, classified from a `FlowStep(ref::class)` reference:
 * - [Node] — a concrete state the path passes through.
 * - [Stay] — a `Stay` node position (the handler stays in the current state).
 * - [Enter] — an `OnEnter` edge leaving the preceding node.
 * - [Trigger] — an action / exception edge, named by its package-relative type ([ref], e.g.
 *   `FeedAction.Retry`); matched against a `GraphEdge.triggerTypeRef` at playback time, so the action /
 *   recover kind never has to be told apart here.
 * - [Unresolved] — a step whose reference could not be resolved (half-typed / foreign); skipped on play.
 */
sealed interface DiagramFlowStep {
    data class Node(val id: StateId) : DiagramFlowStep
    data object Stay : DiagramFlowStep
    data object Enter : DiagramFlowStep
    data class Trigger(val ref: String) : DiagramFlowStep
    data object Unresolved : DiagramFlowStep
}
