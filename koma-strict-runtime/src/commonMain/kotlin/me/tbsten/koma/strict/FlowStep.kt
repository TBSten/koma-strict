package me.tbsten.koma.strict

import kotlin.reflect.KClass

/**
 * One step of a [FlowSpec] path: either a node (a state) or an edge (a transition).
 *
 * A path has to interleave two different kinds of thing, but a Kotlin annotation array is
 * homogeneous — so every step is a [FlowStep] and only the wrapped class varies. The role of a
 * step is derived from what [ref] is:
 *
 * | [ref] | role |
 * |---|---|
 * | a [koma.core.State] subtype | node |
 * | [Stay] (node position) | node — the handler stays, see below |
 * | a [koma.core.Action] subtype | edge declared by [OnAction] with that action |
 * | an [Exception] subtype | edge declared by [OnRecover] with that exception |
 * | [OnEnter] | edge declared by [OnEnter] on the preceding node |
 *
 * Edges are named by what identifies them in the declaration: [OnAction] and [OnRecover] are
 * identified by their type argument (the action / exception class), so only the non-generic
 * [OnEnter] is referenced as the annotation class itself. Nothing here needs a class literal of
 * a generic annotation, which Kotlin cannot express. [OnExit] never appears in a path because
 * exit handlers cannot transition.
 *
 * [Stay] in a node position means the handler stays in the current state, and is distinct from
 * repeating the preceding state's own class, which denotes a self-transition (the state is
 * recreated). This mirrors the distinction the declaration API already makes — see [Stay].
 *
 * An intermediate sealed type is allowed only as the source node of a shared declaration
 * ([OnAction] / [OnRecover] applied to that intermediate type itself). Transition targets are
 * always concrete leaves, so an intermediate type in a target position is an error.
 *
 * @property ref The state, action, exception, or [OnEnter] marker this step refers to.
 */
@Target()
@Retention(AnnotationRetention.SOURCE)
public annotation class FlowStep(
    val ref: KClass<*>,
)
