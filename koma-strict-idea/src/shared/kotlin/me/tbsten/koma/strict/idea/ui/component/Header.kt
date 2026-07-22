package me.tbsten.koma.strict.idea.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.tbsten.koma.strict.idea.layout.LayoutDirection
import me.tbsten.koma.strict.idea.model.StoreDiagramModel
import me.tbsten.koma.strict.idea.ui.diagram.DiagramColors
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.ui.component.Dropdown
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
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
@OptIn(ExperimentalJewelApi::class)
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
        Spacer(Modifier.weight(1f))
        // トグルなので「何のコントロールか (Layout)」と「押すと切り替わる (⇄)」を明示し、現在値だけの曖昧さを消す。
        Text("Layout", color = colors.compositeLabel)
        OutlinedButton(onClick = onToggleDirection) {
            Text((if (direction == LayoutDirection.LR) "LR" else "TB") + "  ⇄")
        }
        // 図の画像コピー。図が描けている時 (onCopyImage != null) だけ出す。成功時は短く "Copied" 表示。
        if (onCopyImage != null) {
            var copied by remember { mutableStateOf(false) }
            // 連打しても表示期間がリセットされるよう、成功のたびに tick を進めて LaunchedEffect を再起動する。
            var copiedTick by remember { mutableStateOf(0) }
            OutlinedButton(
                onClick = {
                    if (onCopyImage()) {
                        copied = true
                        copiedTick++
                    }
                },
            ) {
                Text(if (copied) "Copied" else "Copy image")
            }
            if (copied) {
                LaunchedEffect(copiedTick) {
                    delay(1500)
                    copied = false
                }
            }
        }
        // 手動再解析 (ライブ追従が拾い切れない時の保険)。
        OutlinedButton(onClick = onReload) { Text("Reload") }
    }
}
