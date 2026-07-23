package me.tbsten.koma.strict.idea.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.tbsten.koma.strict.idea.layout.LayoutDirection
import me.tbsten.koma.strict.idea.model.DiagramFlow
import me.tbsten.koma.strict.idea.model.StoreDiagramModel
import me.tbsten.koma.strict.idea.ui.diagram.DiagramColors
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.ui.component.Dropdown
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import kotlinx.coroutines.delay

/**
 * The tool-window header row: the store selector (a [Dropdown] only when more than one `@StoreSpec`
 * is in the file), the `LR`/`TB` layout toggle, a "Copy image" action (only when a figure is actually
 * rendered — [onCopyImage] non-null), and a manual Reload. The diagram zoom sits on the canvas itself
 * ([DiagramZoomControls]), not here. The product name is intentionally omitted — the `addComposeTab`
 * "Koma Strict" tab already carries it — so a narrow dock spends its width on the controls.
 *
 * [onCopyImage] copies the current figure to the system clipboard and reports success; on success the
 * button briefly reads "Copied" as feedback before reverting.
 */
@OptIn(ExperimentalJewelApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun Header(
    stores: List<StoreDiagramModel>,
    selected: Int,
    onSelect: (Int) -> Unit,
    direction: LayoutDirection,
    onToggleDirection: () -> Unit,
    onReload: () -> Unit,
    colors: DiagramColors,
    onCopyImage: (() -> Boolean)? = null,
    recording: Boolean = false,
    onToggleRecording: () -> Unit = {},
    /** `@FlowSpec` flows declared on the store (`flows-design.md`); a picker appears only when non-empty. */
    flows: List<DiagramFlow> = emptyList(),
    /** Currently playing flow (null = none). */
    selectedFlow: DiagramFlow? = null,
    /** Pick a flow to play (null clears playback). */
    onSelectFlow: (DiagramFlow?) -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 製品名は tool window タブ名 (addComposeTab "Koma Strict") と重複するのでヘッダには出さず、
        // 狭ドックの横幅を Store 切替 / Direction に回す。ヘッダは Store 名から始める。
        if (stores.size > 1) {
            val current = stores[selected.coerceIn(0, stores.lastIndex)]
            Dropdown(
                menuContent = {
                    stores.forEachIndexed { index, store ->
                        selectableItem(
                            selected = index == selected,
                            onClick = { onSelect(index) },
                        ) {
                            Text(store.root.simpleName)
                        }
                    }
                },
            ) {
                Text(current.root.simpleName)
            }
        } else {
            Text(stores.first().root.simpleName, fontWeight = FontWeight.SemiBold, color = colors.nodeText)
        }
        // Flow ピッカー (flows-design.md): @FlowSpec がある store でだけ出す。選ぶと図をステップ再生し、
        // (none) で解除。記録中は録画パネルに譲るので隠す。
        if (flows.isNotEmpty() && !recording) {
            Dropdown(
                menuContent = {
                    selectableItem(selected = selectedFlow == null, onClick = { onSelectFlow(null) }) {
                        Text("(none)")
                    }
                    flows.forEach { flow ->
                        selectableItem(selected = flow == selectedFlow, onClick = { onSelectFlow(flow) }) {
                            Text(flow.name)
                        }
                    }
                },
            ) {
                Text(selectedFlow?.name ?: "▶ Flow")
            }
        }
        Spacer(Modifier.weight(1f))
        // Flow Recorder のトグル (ide-test-code.md)。record ドットのアイコンボタン (記録中は赤塗り)。
        Tooltip(tooltip = { Text(if (recording) "Recording — click to stop" else "Record a flow") }) {
            IconButton(onClick = onToggleRecording) { RecordGlyph(recording) }
        }
        // トグルなので「何のコントロールか (Layout)」と「押すと切り替わる (⇄)」を明示し、現在値だけの曖昧さを消す。
        Text("Layout", color = colors.compositeLabel)
        OutlinedButton(onClick = onToggleDirection) {
            Text((if (direction == LayoutDirection.LR) "LR" else "TB") + "  ⇄")
        }
        // 図の画像コピー。図が描けている時 (onCopyImage != null) だけ出す。成功時は短くチェックに変わる。
        if (onCopyImage != null) {
            var copied by remember { mutableStateOf(false) }
            // 連打しても表示期間がリセットされるよう、成功のたびに tick を進めて LaunchedEffect を再起動する。
            var copiedTick by remember { mutableStateOf(0) }
            Tooltip(tooltip = { Text(if (copied) "Copied" else "Copy image") }) {
                IconButton(
                    onClick = {
                        if (onCopyImage()) {
                            copied = true
                            copiedTick++
                        }
                    },
                ) {
                    Icon(
                        key = if (copied) AllIconsKeys.Actions.Checked else AllIconsKeys.Actions.Copy,
                        contentDescription = "Copy image",
                    )
                }
            }
            if (copied) {
                LaunchedEffect(copiedTick) {
                    delay(1500)
                    copied = false
                }
            }
        }
        // 手動再解析 (ライブ追従が拾い切れない時の保険)。IDE バンドルの refresh アイコン。
        Tooltip(tooltip = { Text("Reload") }) {
            IconButton(onClick = onReload) { Icon(key = AllIconsKeys.Actions.Refresh, contentDescription = "Reload") }
        }
    }
}

private val RecordRed = Color(0xFFE5484D)

/**
 * Record dot: a filled red circle while recording, a red ring otherwise. Drawn (there is no fitting
 * IDE-bundled "record" icon; the red dot is the universal record affordance).
 */
@Composable
private fun RecordGlyph(recording: Boolean) {
    Canvas(Modifier.size(14.dp)) {
        val radius = size.minDimension / 2f * 0.85f
        if (recording) {
            drawCircle(RecordRed, radius = radius, center = center)
        } else {
            drawCircle(RecordRed, radius = radius, center = center, style = Stroke(width = 1.5.dp.toPx()))
        }
    }
}
