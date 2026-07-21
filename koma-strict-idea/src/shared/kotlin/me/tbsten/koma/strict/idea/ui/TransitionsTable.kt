package me.tbsten.koma.strict.idea.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import me.tbsten.koma.strict.idea.model.DiagramTrigger
import me.tbsten.koma.strict.idea.model.EnterTrigger
import me.tbsten.koma.strict.idea.model.GroupState
import me.tbsten.koma.strict.idea.model.LeafState
import me.tbsten.koma.strict.idea.model.RootState
import me.tbsten.koma.strict.idea.model.RecoverTrigger
import me.tbsten.koma.strict.idea.model.ActionTrigger
import me.tbsten.koma.strict.idea.model.SourceAnchor
import me.tbsten.koma.strict.idea.model.StoreDiagramModel
import me.tbsten.koma.strict.idea.model.walk
import org.jetbrains.jewel.ui.component.Text

/**
 * The Transitions tab — the diagram's ground truth (`ide.md` "図 + 遷移表 pair"). It is derived
 * straight from the slim [StoreDiagramModel], not from the lowered graph, so a rendering shortcut in
 * the figure can never silently hide a transition here. Columns: `Trigger | From | To | Emit`
 * (a stay folds into To as `<state> (stay)` rather than a separate column).
 * Rows whose source leaf is unreachable are shown in the warning color, matching the canvas.
 */
@Composable
fun TransitionsTable(
    model: StoreDiagramModel,
    colors: DiagramColors,
    modifier: Modifier = Modifier,
    onNavigate: (SourceAnchor) -> Unit = {},
) {
    val rows = remember(model) { model.transitionRows() }
    // 列幅は内容に合わせて実測で決める (下記 rememberColumnWidths)。
    // 横スクロールは header 行とデータ行で同一 state を共有し、列が縦にズレないようにする。
    // ヘッダは verticalScroll の外に出して常時表示 (sticky)、データ本体だけ縦横スクロールさせる。
    // これで狭い右ドックでも To / Emit 列がサイレントに切れず (表 = 正 の思想)、行数が多くても列見出しが流れない。
    val widths = rememberColumnWidths(rows)
    val hScroll = rememberScrollState()
    Column(modifier = modifier.fillMaxSize().padding(12.dp)) {
        Column(Modifier.horizontalScroll(hScroll)) {
            HeaderRow(colors, widths)
        }
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .horizontalScroll(hScroll),
        ) {
            if (rows.isEmpty()) {
                Text("No transitions.", color = colors.compositeLabel, modifier = Modifier.padding(top = 8.dp))
            }
            rows.forEachIndexed { index, row ->
                DataRow(row, colors, widths, striped = index % 2 == 1, onNavigate = onNavigate)
            }
        }
    }
}

// 各列の最小幅 (表示順 Trigger | From | To | Emit)。内容が短い列はこの幅を保ち、長い列だけ実測で広げる。
private val TRIGGER = 130.dp
private val FROM = 150.dp
private val TO = 170.dp
private val EMIT = 150.dp

// Cell の末尾パディング。実測テキスト幅にこの分を足して列幅を確保する。
private val CELL_PADDING = 8.dp

/** Per-column widths (each ≥ its min) and their total, shared by header, divider and every row. */
private data class ColumnWidths(val columns: List<Dp>, val total: Dp)

/**
 * Measures every cell (header + rows) per column with the actual Jewel text style and sizes each
 * column to the widest cell — so a long token like `on SessionExpiredException` fits on one line and
 * is never wrapped mid-word or clipped. Columns never shrink below their [FROM]/[TRIGGER]/… minimum,
 * keeping the relative balance; the wider total is absorbed by the surrounding horizontal scroll.
 */
@Composable
private fun rememberColumnWidths(rows: List<TransitionRow>): ColumnWidths {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val bodyStyle = JewelTheme.defaultTextStyle
    val headerStyle = bodyStyle.copy(fontWeight = FontWeight.SemiBold)
    return remember(rows, density, bodyStyle, headerStyle) {
        val mins = listOf(TRIGGER, FROM, TO, EMIT)
        val headers = listOf("Trigger", "From", "To", "Emit")
        fun measureDp(text: String, style: TextStyle): Dp {
            if (text.isEmpty()) return 0.dp
            val px = measurer.measure(text, style).size.width
            return with(density) { px.toDp() }
        }
        val columns = List(4) { col ->
            var widest = measureDp(headers[col], headerStyle)
            for (row in rows) {
                val cell = when (col) {
                    0 -> row.trigger
                    1 -> row.from
                    2 -> row.to
                    else -> row.emit
                }
                val w = measureDp(cell, bodyStyle)
                if (w > widest) widest = w
            }
            // 実測幅 + パディング + 端数吸収の安全マージンを、最小幅で下限を切って採用。
            maxOf(mins[col], widest + CELL_PADDING + 4.dp)
        }
        ColumnWidths(columns, columns.fold(0.dp) { acc, w -> acc + w })
    }
}

@Composable
private fun HeaderRow(colors: DiagramColors, widths: ColumnWidths) {
    Row(Modifier.width(widths.total).padding(vertical = 6.dp)) {
        Cell("Trigger", widths.columns[0], colors.nodeText, FontWeight.SemiBold)
        Cell("From", widths.columns[1], colors.nodeText, FontWeight.SemiBold)
        Cell("To", widths.columns[2], colors.nodeText, FontWeight.SemiBold)
        Cell("Emit", widths.columns[3], colors.nodeText, FontWeight.SemiBold)
    }
    Box(Modifier.width(widths.total).height(1.dp).background(colors.compositeBorder))
}

@Composable
private fun DataRow(row: TransitionRow, colors: DiagramColors, widths: ColumnWidths, striped: Boolean, onNavigate: (SourceAnchor) -> Unit) {
    val text = if (row.reachable) colors.nodeText else colors.warningText
    // Emit セルも行の到達可能性に連動させる (到達不能行は Emit も警告色。他セルだけ amber で Emit が灰の半端を防ぐ)。
    val emitColor = if (row.reachable) colors.compositeLabel else colors.warningText
    val bg = if (striped) colors.anyFill else colors.background
    // 行クリックで from 宣言へ遷移 (leaf は leaf 宣言・any / any <Group> はその scope の root / group 宣言)。
    val rowClick = row.source?.let { anchor -> Modifier.clickable { onNavigate(anchor) } } ?: Modifier
    Row(
        Modifier.width(widths.total).background(bg).then(rowClick).padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.Start,
    ) {
        Cell(row.trigger, widths.columns[0], text)
        Cell(row.from, widths.columns[1], text)
        Cell(row.to, widths.columns[2], text)
        Cell(row.emit, widths.columns[3], emitColor)
    }
}

@Composable
private fun Cell(text: String, width: Dp, color: Color, weight: FontWeight = FontWeight.Normal) {
    // softWrap=false + maxLines=1: 列幅は内容に合わせてあるので途中改行も切り詰めも起きない。
    Text(
        text,
        color = color,
        fontWeight = weight,
        softWrap = false,
        maxLines = 1,
        modifier = Modifier.width(width).padding(end = 8.dp),
    )
}

// ---- pure row extraction (kept next to the table; independent of GraphLowering) ----

/** A single transition-table row, ready to render. */
data class TransitionRow(
    val from: String,
    val trigger: String,
    val stay: Boolean,
    val to: String,
    val emit: String,
    val reachable: Boolean,
    /**
     * Declaration of the `from` node for row click-to-declaration. A leaf row points to the leaf; a
     * scope-shared `any` / `any <Group>` row points to the root / group declaration.
     */
    val source: SourceAnchor? = null,
)

/**
 * Flattens the model tree into table rows. Leaf triggers read as the leaf name; a scope-shared
 * trigger on the root / an intermediate sealed node reads as `any` / `any <Group>` (mirroring the
 * any-state pseudo node). Trigger tokens match the figure (`onEnter` / decapitalized action /
 * `on <Exception>`). A stay-only trigger yields a single `(self)` row.
 */
fun StoreDiagramModel.transitionRows(): List<TransitionRow> {
    val out = mutableListOf<TransitionRow>()
    for (node in root.walk()) {
        val from = when (node) {
            is RootState -> "any"
            is GroupState -> "any ${node.simpleName}"
            is LeafState -> node.simpleName
        }
        // scope-shared (root / group) 行も宣言へ飛べるよう、node 種別を問わず自身の source を運ぶ。
        val source = node.source
        val reachable = node !is LeafState || isReachable(node.id)
        val triggers = buildList {
            if (node is LeafState) node.enter?.let { add(it) }
            addAll(node.actions)
            addAll(node.recovers)
        }
        for (trigger in triggers) {
            val token = triggerToken(trigger)
            val emit = trigger.emits.joinToString(", ")
            for (target in trigger.targets) {
                out += TransitionRow(from, token, stay = false, to = target.simpleName ?: target.dotted, emit = emit, reachable = reachable, source = source)
            }
            // 未解決 target (foreign / error type) も表 (= 正) から隠さない。図は leaf でないので描かないが、
            // 「宣言はあるが解決できていない」ことを ?付きで残す (silent truncation を許さない)。
            for (unresolved in trigger.unresolvedTargets) {
                out += TransitionRow(from, token, stay = false, to = unresolved, emit = emit, reachable = reachable, source = source)
            }
            if (trigger.stay) {
                // Stay は独立列ではなく To に畳む: 同じ state に留まる = 「<from> (stay)」。
                out += TransitionRow(from, token, stay = true, to = "$from (stay)", emit = emit, reachable = reachable, source = source)
            }
            if (trigger.targets.isEmpty() && trigger.unresolvedTargets.isEmpty() && !trigger.stay) {
                out += TransitionRow(from, token, stay = false, to = "—", emit = emit, reachable = reachable, source = source)
            }
        }
        // @OnExit は遷移を持たない (To = —) が emit するので、表からは隠さない (図はバッジ)。
        node.exit?.let { exit ->
            out += TransitionRow(from, "exit", stay = false, to = "—", emit = exit.emits.joinToString(", "), reachable = reachable, source = source)
        }
    }
    return out
}

private fun triggerToken(trigger: DiagramTrigger): String = when (trigger) {
    is EnterTrigger -> "onEnter"
    is ActionTrigger -> trigger.actionName.replaceFirstChar { it.lowercase() }
    is RecoverTrigger -> "on ${trigger.exceptionName}"
}
