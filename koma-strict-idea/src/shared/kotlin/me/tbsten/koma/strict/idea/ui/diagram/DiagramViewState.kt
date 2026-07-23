package me.tbsten.koma.strict.idea.ui.diagram

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import me.tbsten.koma.strict.idea.model.DiagramFlow

// 要求 zoom の下限/上限と、+/- ボタン 1 押しの加算幅 (`ide.md`)。
private const val MIN_ZOOM = 0.05f
private const val MAX_ZOOM = 4.0f
private const val ZOOM_STEP = 0.15f

/**
 * The diagram's *requested* zoom (buttons + pinch / wheel), clamped to [MIN_ZOOM]..[MAX_ZOOM]. A stable
 * holder so gesture modifiers can mutate it directly and [DiagramZoomControls] can drive it, instead of
 * threading a `value` + `onChange` pair. [StoreDiagram] turns this into the actual render zoom (auto-fit).
 */
@Stable
class ZoomState(initial: Float = 1f) {
    var zoom by mutableStateOf(initial.coerceIn(MIN_ZOOM, MAX_ZOOM))
        private set

    /** Multiplicative nudge from a pinch / `Ctrl`+wheel delta. */
    fun nudge(factor: Float) {
        zoom = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
    }

    fun zoomIn() {
        zoom = (zoom + ZOOM_STEP).coerceAtMost(MAX_ZOOM)
    }

    fun zoomOut() {
        zoom = (zoom - ZOOM_STEP).coerceAtLeast(MIN_ZOOM)
    }

    fun reset() {
        zoom = 1f
    }
}

@Composable
fun rememberZoomState(initial: Float = 1f): ZoomState = remember { ZoomState(initial) }

/**
 * The set of focused [DiagramSelection]s (`ide-3.md`). A stable holder so the tap gesture can mutate it
 * directly and the tool window can read it (to bake focus into "Copy image"). Tap semantics live in the
 * pure [nextSelection]; [onTap] applies them.
 */
@Stable
class SelectionState(initial: Set<DiagramSelection> = emptySet()) {
    var selection by mutableStateOf(initial)
        private set

    /** Fold a tap into the selection: Shift toggles, a plain click replaces or clears (`ide-3.md`). */
    fun onTap(hit: DiagramSelection?, shift: Boolean, clickedEmpty: Boolean) {
        selection = nextSelection(selection, hit, shift, clickedEmpty)
    }

    /** Replace the selection outright (e.g. select the target state right after recording a transition). */
    fun select(selection: Set<DiagramSelection>) {
        this.selection = selection
    }

    fun clear() {
        selection = emptySet()
    }
}

@Composable
fun rememberSelectionState(initial: Set<DiagramSelection> = emptySet()): SelectionState =
    remember { SelectionState(initial) }

/**
 * Flow playback state (`flows-design.md` IDE section): the flow picked in the header dropdown
 * ([selected]; null = none) and how many of its reveal steps have played so far ([revealedCount]). A
 * stable holder so the dropdown mutates it and the diagram reads it; the 0.15s step timer lives in the
 * composable. The diagram highlights `flowReveal(selected).take(revealedCount)` while [selected] is set.
 */
@Stable
class FlowPlaybackState {
    var selected: DiagramFlow? by mutableStateOf(null)
        private set
    var revealedCount: Int by mutableStateOf(0)
        private set

    /** Pick a flow (or clear with null) and restart its playback from the first step. */
    fun select(flow: DiagramFlow?) {
        selected = flow
        revealedCount = 0
    }

    /** Reveal one more step. */
    fun revealNext() {
        revealedCount += 1
    }

    /** Stop playback and clear the selection. */
    fun clear() {
        selected = null
        revealedCount = 0
    }
}

/**
 * The selection after tapping [hit] over [current] (`ide-3.md`). Shift toggles the target into a
 * multi-selection; a plain tap replaces with just that target, or clears when the tap hit truly empty
 * space ([clickedEmpty]); a navigable-but-not-focusable spot keeps the selection. Pure, so the tap
 * semantics can be unit tested independently of a live canvas.
 */
internal fun nextSelection(
    current: Set<DiagramSelection>,
    hit: DiagramSelection?,
    shift: Boolean,
    clickedEmpty: Boolean,
): Set<DiagramSelection> = when {
    shift && hit != null -> if (hit in current) current - hit else current + hit
    shift -> current
    hit != null -> setOf(hit)
    clickedEmpty -> emptySet()
    else -> current
}
