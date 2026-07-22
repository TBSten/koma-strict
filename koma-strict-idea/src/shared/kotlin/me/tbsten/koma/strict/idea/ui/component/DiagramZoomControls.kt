package me.tbsten.koma.strict.idea.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.tbsten.koma.strict.idea.ui.diagram.DiagramColors
import me.tbsten.koma.strict.idea.ui.diagram.ZoomState
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import kotlin.math.roundToInt

/**
 * Floating zoom controls pinned to the bottom-right corner of the Diagram tab (map / draw.io style).
 * Zoom out / in are Jewel [IconButton]s carrying the platform ExpUI `remove` / `add` glyphs (matching
 * the IDE chrome); the `%` readout between them resets to 100% on click. All zoom logic lives in
 * [ZoomState], so this only reads [ZoomState.zoom] and calls its operations — no value + callback pairs.
 *
 * The row draws on a subtle raised card ([DiagramColors.nodeFill] + border) so it stays readable over
 * whatever diagram content is behind it. The caller positions it (typically
 * `Modifier.align(Alignment.BottomEnd).padding(...)` inside the diagram [androidx.compose.foundation.layout.Box]).
 *
 * The headless preview renders against standalone Jewel, which resolves icons from classpath resources,
 * so `src/preview/resources/expui/general/{add,remove}.svg` ship alongside for the icons to appear
 * (the bundled IDE plugin gets them from the platform).
 */
@Composable
internal fun DiagramZoomControls(
    zoomState: ZoomState,
    colors: DiagramColors,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(colors.nodeFill, RoundedCornerShape(8.dp))
            .border(1.dp, colors.nodeBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 2.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        IconButton(onClick = zoomState::zoomOut) {
            Icon(key = AllIconsKeys.General.Remove, contentDescription = "Zoom out")
        }
        Text(
            "${(zoomState.zoom * 100).roundToInt()}%",
            color = colors.compositeLabel,
            modifier = Modifier.clickable(onClick = zoomState::reset).padding(horizontal = 4.dp),
        )
        IconButton(onClick = zoomState::zoomIn) {
            Icon(key = AllIconsKeys.General.Add, contentDescription = "Zoom in")
        }
    }
}
