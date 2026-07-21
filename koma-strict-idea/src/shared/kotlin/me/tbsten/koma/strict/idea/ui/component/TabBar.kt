package me.tbsten.koma.strict.idea.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.tbsten.koma.strict.idea.ui.DiagramColors
import me.tbsten.koma.strict.idea.ui.DiagramTab
import org.jetbrains.jewel.ui.component.Text

/** The Diagram / Transitions tab bar (`ide.md` "図 + 遷移表" pair). */
@Composable
internal fun TabBar(tab: DiagramTab, onSelect: (DiagramTab) -> Unit, colors: DiagramColors) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (candidate in DiagramTab.entries) {
            TabItem(
                label = candidate.label,
                selected = candidate == tab,
                onClick = { onSelect(candidate) },
                colors = colors,
            )
        }
    }
}

@Composable
private fun TabItem(label: String, selected: Boolean, onClick: () -> Unit, colors: DiagramColors) {
    // 幅は常にラベル幅 (IntrinsicSize.Max) に固定し、下線はその幅いっぱい (fillMaxWidth)。
    // 選択/非選択で確保幅が変わらず色だけ切り替わるので、タブ切替で列がリフローせず、
    // 下線が短い "Diagram" からはみ出す / 長い "Transitions" より短くなる不揃いも起きない。
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 10.dp).width(IntrinsicSize.Max),
    ) {
        Text(
            label,
            color = if (selected) colors.nodeText else colors.compositeLabel,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(if (selected) colors.accent else Color.Transparent),
        )
    }
}
