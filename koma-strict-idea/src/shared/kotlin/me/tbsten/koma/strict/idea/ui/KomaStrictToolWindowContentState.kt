package me.tbsten.koma.strict.idea.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import me.tbsten.koma.strict.idea.layout.LayoutDirection
import me.tbsten.koma.strict.idea.model.StoreDiagramModel
import me.tbsten.koma.strict.idea.ui.diagram.DiagramSelection
import me.tbsten.koma.strict.idea.ui.diagram.FlowPlaybackState
import me.tbsten.koma.strict.idea.ui.diagram.SelectionState
import me.tbsten.koma.strict.idea.ui.diagram.ZoomState

/**
 * All interactive state of [KomaStrictToolWindowContent], split into semantic sub-holders
 * ([StoreSelectionState] / [DirectionState] / [ZoomState] / [SelectionState]) so the composable only
 * wires state to UI. Created via [rememberKomaStrictToolWindowContentState]; re-created (= reset) when
 * the set of stores in the file (by name) changes.
 */
@Stable
class KomaStrictToolWindowContentState(stores: List<StoreDiagramModel>) {
    /** Which of the file's `@StoreSpec`s is shown. */
    val store = StoreSelectionState(stores)

    /** Diagram layout direction (LR / TB). */
    val direction = DirectionState()

    /** Requested zoom (buttons + pinch / wheel). */
    val zoom = ZoomState()

    /** Focus selection (`ide-3.md`). */
    val focus = SelectionState()

    /** Flow playback selection (`flows-design.md`): the flow dropdown + step reveal. */
    val flowPlayback = FlowPlaybackState()

    /** The currently shown store model. */
    val model: StoreDiagramModel get() = store.model

    /**
     * Switch the shown store and drop the focus selection: its [DiagramSelection]s reference the old
     * store's nodes. The recorded flow is **not** dropped — it is controller-owned so it survives store /
     * file switches (press Record to start a fresh one).
     */
    fun selectStore(index: Int) {
        store.select(index)
        focus.clear()
        // 選択中フローは旧 store のノードを参照するので再生も止める。
        flowPlayback.clear()
    }
}

@Composable
fun rememberKomaStrictToolWindowContentState(stores: List<StoreDiagramModel>): KomaStrictToolWindowContentState {
    // 中身 (store 集合) が同じなら状態を保持し、編集中の再解析で表示 store が先頭へ戻らないようにする。
    val state = remember(stores.map { it.root.simpleName }) { KomaStrictToolWindowContentState(stores) }
    // ただし state 名が同じでも中身が変わった再解析 (initial / nextState / emit 等の編集 + Reload) は
    // 反映する。選択 index / direction / zoom / focus は保持したまま model だけ最新へ差し替える
    // (構造的に等しければ no-op なので無限再コンポーズにはならない)。
    state.store.updateStores(stores)
    return state
}

/** Which store in the file is shown (a dropdown when there is more than one). */
@Stable
class StoreSelectionState(initialStores: List<StoreDiagramModel>) {
    /** Latest analyzed stores. Replaced on each re-analysis so live edits reach the figure. */
    var stores by mutableStateOf(initialStores)
        private set

    var selectedIndex by mutableStateOf(0)
        private set

    val model: StoreDiagramModel get() = stores[selectedIndex.coerceIn(0, stores.lastIndex)]

    fun select(index: Int) {
        selectedIndex = index
    }

    /** Replace the stores with the latest analysis result, keeping [selectedIndex] (`ide.md` live update). */
    fun updateStores(latest: List<StoreDiagramModel>) {
        stores = latest
    }
}

/** The diagram layout-direction toggle. Defaults to [LayoutDirection.TB]. */
@Stable
class DirectionState(initial: LayoutDirection = LayoutDirection.TB) {
    var direction by mutableStateOf(initial)
        private set

    fun toggle() {
        direction = if (direction == LayoutDirection.LR) LayoutDirection.TB else LayoutDirection.LR
    }
}
