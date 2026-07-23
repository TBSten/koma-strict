package me.tbsten.koma.strict.idea.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import me.tbsten.koma.strict.idea.flow.FlowTransition
import me.tbsten.koma.strict.idea.flow.RecordedFlow
import me.tbsten.koma.strict.idea.flow.defaultTestFramework
import me.tbsten.koma.strict.idea.ir.DiagramGraph
import me.tbsten.koma.strict.idea.ir.EdgeKind
import me.tbsten.koma.strict.idea.ir.NodeId
import me.tbsten.koma.strict.idea.model.StateId
import me.tbsten.koma.strict.idea.model.StoreDiagramModel
import me.tbsten.koma.strict.idea.model.leaves
import me.tbsten.koma.strict.idea.ui.diagram.DiagramSelection

/** Remembers a standalone [RecordingState] (preview / default). The plugin passes a controller-owned one instead. */
@Composable
fun rememberRecordingState(): RecordingState = remember { RecordingState() }

/** The tab shown in the recorder's slide-in panel (`ide-test-code.md`). */
enum class FlowPanelTab { Steps, TestCode, FlowSpec }

/**
 * Interactive state of the Flow Recorder (`ide-test-code.md`): whether recording is on, the
 * [RecordedFlow] built by clicking transitions, the flow name, and the slide-in panel (open + tab).
 *
 * Clicking a transition while [recording] appends a step ([record]); only edges that *leave the current
 * [RecordedFlow.cursor]* are accepted, so the recorded path is always connected (and thus a valid
 * `@FlowSpec`). All figure geometry lives in the diagram; this holder is pure Compose state so the tool
 * window only wires it to the UI. The store's model is passed per call rather than stored, so the state
 * survives live re-analysis (a new model instance each edit) without resetting.
 */
@Stable
class RecordingState {
    /** True while click-to-record is armed. */
    var recording by mutableStateOf(false)
        private set

    /** The scenario recorded so far. */
    var flow by mutableStateOf(RecordedFlow())
        private set

    /** Annotation-class name for the generated `@FlowSpec` (edited in the panel; two-way bound). */
    var flowName by mutableStateOf("RecordedFlow")

    /** Test class name override for the generated test (blank = derive from the store; two-way bound). */
    var testClassName by mutableStateOf("")

    /** Test case name override for the generated test (blank = the humanized flow name; two-way bound). */
    var testCaseName by mutableStateOf("")

    /** Whether the slide-in Steps / code panel is open. */
    var panelOpen by mutableStateOf(false)
        private set

    /** The active panel tab. */
    var activeTab by mutableStateOf(FlowPanelTab.Steps)
        private set

    /** Whether the floating recording pill is collapsed to a single icon (two-way bound). */
    var pillMinimized by mutableStateOf(false)

    /** Selected test framework for the Test Code tab (a key of the codegen framework maps; two-way bound). */
    var testFramework by mutableStateOf(defaultTestFramework)

    /**
     * Toggle recording. Starting a recording resets the flow; its start state is [preferredInitial]
     * (e.g. the currently selected State) when given, otherwise the declared `@StoreSpec(initial)`.
     */
    fun toggleRecording(model: StoreDiagramModel, preferredInitial: StateId? = null) {
        recording = !recording
        if (recording) flow = RecordedFlow(initial = preferredInitial ?: defaultInitial(model))
    }

    /** Change the start state, resetting the recorded transitions (an old prefix may no longer connect). */
    fun setInitial(id: StateId?) {
        flow = flow.withInitial(id)
    }

    fun clear() {
        flow = flow.cleared()
    }

    /** Delete Steps row [row] and everything after it (row 0 = the initial node). */
    fun deleteRow(row: Int) {
        flow = flow.truncateFromRow(row)
    }

    fun setTab(tab: FlowPanelTab) {
        activeTab = tab
        panelOpen = true
    }

    fun openPanel(tab: FlowPanelTab = activeTab) {
        activeTab = tab
        panelOpen = true
    }

    fun closePanel() {
        panelOpen = false
    }

    /** Seeds the state for previews / tests (bypasses click-driven recording). */
    internal fun seed(
        flow: RecordedFlow,
        tab: FlowPanelTab = FlowPanelTab.FlowSpec,
        recording: Boolean = true,
        panelOpen: Boolean = true,
        name: String = "RecordedFlow",
    ) {
        this.flow = flow
        this.activeTab = tab
        this.recording = recording
        this.panelOpen = panelOpen
        this.flowName = name
    }

    /** Reset everything (used when the shown store changes — the flow references the old store's states). */
    fun reset() {
        recording = false
        flow = RecordedFlow()
        panelOpen = false
        activeTab = FlowPanelTab.Steps
        pillMinimized = false
        testFramework = defaultTestFramework
        testClassName = ""
        testCaseName = ""
    }

    /**
     * Fold a diagram tap into the flow. Returns true if a transition was recorded (so the caller can
     * select the resulting state). No-op — returns false — for a node tap or an edge that doesn't leave
     * the cursor.
     */
    fun record(selection: DiagramSelection, model: StoreDiagramModel): Boolean {
        if (flow.initial == null) flow = flow.withInitial(defaultInitial(model))
        val transition = toTransition(selection, flow.cursor) ?: return false
        flow = flow.append(transition)
        return true
    }

    /**
     * The transitions recordable from the current [RecordedFlow.cursor] — the valid next steps — as
     * diagram selections. While recording, only these are highlighted (and clickable); everything else
     * is dimmed / non-selectable (`ide-test-code.md`).
     */
    fun recordableSelections(graph: DiagramGraph): Set<DiagramSelection> {
        val cursor = flow.cursor ?: return emptySet()
        val result = mutableSetOf<DiagramSelection>()
        for (edge in graph.edges) {
            if (edge.kind == EdgeKind.INITIAL) continue
            if (canFollow(edge.fromId, cursor)) result += DiagramSelection.Edge(edge)
        }
        for (stay in graph.scopeStays) {
            if (scopeContains(stay.scope, cursor)) result += DiagramSelection.Stay(stay)
        }
        return result
    }

    private fun defaultInitial(model: StoreDiagramModel): StateId? =
        model.initial.firstOrNull() ?: model.leaves.firstOrNull()?.id

    private fun toTransition(selection: DiagramSelection, cursor: StateId?): FlowTransition? {
        when (selection) {
            is DiagramSelection.Edge -> {
                val edge = selection.edge
                // INITIAL (`[*]` -> X) は遷移ステップではない (initial は dropdown が担う)。
                if (edge.kind == EdgeKind.INITIAL) return null
                if (!canFollow(edge.fromId, cursor)) return null
                val target = (edge.toId as? NodeId.State)?.path ?: (edge.toId as? NodeId.Composite)?.path
                return FlowTransition(
                    kind = edge.kind,
                    typeRef = edge.triggerTypeRef,
                    label = edge.label,
                    fromId = cursor,
                    target = if (edge.stay) null else target,
                    stay = edge.stay,
                )
            }
            is DiagramSelection.Stay -> {
                val stay = selection.stay
                if (!scopeContains(stay.scope, cursor)) return null
                return FlowTransition(
                    kind = stay.kind,
                    typeRef = stay.triggerTypeRef,
                    label = stay.label,
                    fromId = cursor,
                    target = null,
                    stay = true,
                )
            }
            else -> return null // Node / Composite はステップではない
        }
    }

    /** An edge may be recorded when it leaves the cursor: a leaf edge from it, or a scope-shared edge covering it. */
    private fun canFollow(fromId: NodeId, cursor: StateId?): Boolean {
        cursor ?: return false
        return when (fromId) {
            is NodeId.State -> fromId.path == cursor
            is NodeId.Any -> scopeContains(fromId.scope, cursor)
            else -> false
        }
    }

    /** True when [cursor] is a leaf inside [scope] (root scope covers every leaf). */
    private fun scopeContains(scope: StateId, cursor: StateId?): Boolean {
        cursor ?: return false
        return scope.isRoot || cursor.segments.take(scope.segments.size) == scope.segments
    }
}
