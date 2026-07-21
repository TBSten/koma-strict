package me.tbsten.koma.strict

/**
 * Declares a named flow: a path through the declared transition graph that generated
 * documentation should highlight.
 *
 * A flow adds no capability to the store — it is documentation metadata. Everything it names is
 * already declared by [OnEnter] / [OnAction] / [OnRecover], so a flow can only point at
 * transitions that exist; a path that does not match the declared graph is an error rather than
 * a diagram that quietly disagrees with the code.
 *
 * Applied to an annotation class, which is then applied to the [StoreSpec] root. This keeps each
 * flow a named declaration that can live next to (or apart from) the state hierarchy without
 * adding noise to it:
 *
 * ```kotlin
 * @FlowSpec(
 *     name = "initialize happy path",
 *     steps = [
 *         FlowStep(FeedState.Loading::class),
 *         FlowStep(OnEnter::class),
 *         FlowStep(FeedState.Stable.Idle::class),
 *     ],
 * )
 * annotation class InitializeHappyPathFlow
 *
 * @StoreSpec(initial = [FeedState.Loading::class])
 * @InitializeHappyPathFlow
 * @RefreshFlow
 * sealed interface FeedState : State { ... }
 * ```
 *
 * Flow annotations can also be grouped into one annotation class and applied together, which
 * keeps the root readable once there are many of them:
 *
 * ```kotlin
 * @InitializeHappyPathFlow
 * @RefreshFlow
 * annotation class FeedStateFlows
 * ```
 *
 * Because the retention is [AnnotationRetention.SOURCE], flow annotation classes must live in
 * the same compilation unit as the root they annotate.
 *
 * NOTE: this is the declaration surface only. Discovery, validation, and diagram highlighting
 * are not implemented yet — see `doc/internal/generate-state-diagrams.md`.
 *
 * @property name Display name of the flow. When omitted, the name of the annotated annotation
 *   class is used.
 * @property steps The path, interleaving node steps and edge steps: a node, the edge leaving it,
 *   the node that edge reaches, and so on. The first and last steps are nodes. See [FlowStep]
 *   for how each step's role is derived.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
public annotation class FlowSpec(
    val name: String = "",
    val steps: Array<FlowStep> = [],
)
