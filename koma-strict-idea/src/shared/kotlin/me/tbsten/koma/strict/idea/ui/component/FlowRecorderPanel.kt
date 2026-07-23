package me.tbsten.koma.strict.idea.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.tbsten.koma.strict.idea.flow.GeneratedFlowSpec
import me.tbsten.koma.strict.idea.flow.defaultTestClassName
import me.tbsten.koma.strict.idea.flow.generateFlowSpec
import me.tbsten.koma.strict.idea.flow.generateKomaTest
import me.tbsten.koma.strict.idea.flow.humanizeFlowName
import me.tbsten.koma.strict.idea.flow.komaTestFileName
import me.tbsten.koma.strict.idea.flow.testFrameworkNames
import me.tbsten.koma.strict.idea.model.StateId
import me.tbsten.koma.strict.idea.model.StoreDiagramModel
import me.tbsten.koma.strict.idea.ui.FlowPanelTab
import me.tbsten.koma.strict.idea.ui.RecordingState
import me.tbsten.koma.strict.idea.model.leaves
import me.tbsten.koma.strict.idea.ui.diagram.DiagramColors
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Dropdown
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text

/**
 * The Flow Recorder's slide-in panel (`ide-test-code.md`): a `Steps` / `Test Code` / `@FlowSpec` tab
 * bar over the recorded [RecordingState.flow]. The `@FlowSpec` tab (`ide-test-code-flow-spec.png`) shows
 * the generated annotation with a Flow-name field, `Copy`, and an "Add to <Root>State" button that
 * inserts it into the state file ([onInsertFlowSpec]).
 */
@Composable
internal fun FlowRecorderPanel(
    model: StoreDiagramModel,
    recording: RecordingState,
    colors: DiagramColors,
    onCopyText: (String) -> Boolean,
    onInsertFlowSpec: (GeneratedFlowSpec) -> Unit,
    canInsert: Boolean,
    onGenerateTestFile: (fileName: String, content: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val code = rememberCodeColors()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(JewelTheme.globalColors.panelBackground)
            .border(1.dp, colors.compositeBorder),
    ) {
        // タブ帯 + close。狭幅ではタブ側だけ横スクロールし、× は右端に固定する。
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                PanelTab("Steps", recording.activeTab == FlowPanelTab.Steps, colors) { recording.setTab(FlowPanelTab.Steps) }
                PanelTab("Test Code", recording.activeTab == FlowPanelTab.TestCode, colors) { recording.setTab(FlowPanelTab.TestCode) }
                PanelTab("@FlowSpec", recording.activeTab == FlowPanelTab.FlowSpec, colors) { recording.setTab(FlowPanelTab.FlowSpec) }
            }
            Text("✕", color = colors.compositeLabel, modifier = Modifier.padding(start = 8.dp).clickable { recording.closePanel() })
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.compositeBorder))

        when (recording.activeTab) {
            FlowPanelTab.Steps -> StepsTab(model, recording, colors)
            FlowPanelTab.TestCode -> TestCodeTab(model, recording, colors, code, onCopyText, onGenerateTestFile, canInsert)
            FlowPanelTab.FlowSpec -> FlowSpecTab(model, recording, colors, code, onCopyText, onInsertFlowSpec, canInsert)
        }
    }
}

@Composable
private fun PanelTab(label: String, active: Boolean, colors: DiagramColors, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            color = if (active) colors.nodeText else colors.compositeLabel,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.clickable(onClick = onClick).padding(bottom = 6.dp),
        )
        Box(
            Modifier.height(2.dp).width(if (active) 64.dp else 0.dp)
                .background(if (active) colors.accent else Color.Transparent),
        )
    }
}

/** The `@FlowSpec` tab (`ide-test-code-flow-spec.png`): name field, hint, code, Copy + Add-to-state. */
@Composable
private fun FlowSpecTab(
    model: StoreDiagramModel,
    recording: RecordingState,
    colors: DiagramColors,
    code: CodeColors,
    onCopyText: (String) -> Boolean,
    onInsertFlowSpec: (GeneratedFlowSpec) -> Unit,
    canInsert: Boolean,
) {
    val name = recording.flowName
    val generated = generateFlowSpec(model, recording.flow, name.ifBlank { "RecordedFlow" })
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Flow name", color = colors.compositeLabel)
        NameField(name, colors) { recording.flowName = it }
        // 入力が下でどう使われるかを明示する導線 (画像のヒント行)。
        BasicText(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = colors.compositeLabel)) { append("→ used below as ") }
                withStyle(SpanStyle(color = colors.nodeText, fontWeight = FontWeight.SemiBold)) { append("annotation class ${name.ifBlank { "RecordedFlow" }}") }
                withStyle(SpanStyle(color = colors.compositeLabel)) { append(" and ") }
                withStyle(SpanStyle(color = colors.nodeText, fontWeight = FontWeight.SemiBold)) { append("name = \"${humanizeFlowName(name.ifBlank { "RecordedFlow" })}\"") }
            },
            style = TextStyle(fontSize = 12.sp),
        )
        CodeBox(highlightKotlin(generated.declaration, code, name.ifBlank { "RecordedFlow" }), code, Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CopyButton(generated.declaration, onCopyText)
            Spacer(Modifier.weight(1f))
            DefaultButton(onClick = { onInsertFlowSpec(generated) }, enabled = canInsert) {
                Text("Add to ${model.root.simpleName}")
            }
        }
    }
}

/** The Test Code tab: a framework selector (kotlin.test / kotest) over the generated koma-test scaffold. */
@OptIn(ExperimentalJewelApi::class)
@Composable
private fun TestCodeTab(
    model: StoreDiagramModel,
    recording: RecordingState,
    colors: DiagramColors,
    code: CodeColors,
    onCopyText: (String) -> Boolean,
    onGenerateTestFile: (fileName: String, content: String) -> Unit,
    canGenerate: Boolean,
) {
    // 空欄なら派生デフォルトを表示 (フィールドを空にするとデフォルトへ戻る挙動)。生成にもこの実効値を使う。
    val className = recording.testClassName.ifBlank { defaultTestClassName(model) }
    val caseName = recording.testCaseName.ifBlank { humanizeFlowName(recording.flowName.ifBlank { "RecordedFlow" }) }
    val generated = generateKomaTest(
        model, recording.flow, recording.flowName.ifBlank { "RecordedFlow" }, recording.testFramework,
        testClassName = className, testCaseName = caseName,
    )
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Test class name", color = colors.compositeLabel)
                NameField(className, colors) { recording.testClassName = it }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Test case name", color = colors.compositeLabel)
                NameField(caseName, colors) { recording.testCaseName = it }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Framework:", color = colors.compositeLabel)
            Dropdown(
                menuContent = {
                    testFrameworkNames.forEach { framework ->
                        selectableItem(selected = framework == recording.testFramework, onClick = { recording.testFramework = framework }) {
                            Text(framework)
                        }
                    }
                },
            ) { Text(recording.testFramework) }
        }
        CodeBox(highlightKotlin(generated, code, null), code, Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CopyButton(generated, onCopyText)
            Spacer(Modifier.weight(1f))
            DefaultButton(onClick = { onGenerateTestFile(komaTestFileName(className), generated) }, enabled = canGenerate) {
                Text("Generate test file")
            }
        }
    }
}

@Composable
private fun StepsTab(model: StoreDiagramModel, recording: RecordingState, colors: DiagramColors) {
    val flow = recording.flow
    fun nameOf(id: StateId?): String = id?.let { model.leaf(it)?.simpleName ?: it.dotted } ?: "?"
    Column(Modifier.fillMaxSize()) {
        // Initial 選択 (dropdown で任意 leaf に切替) + Clear。
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Initial:", color = colors.compositeLabel)
            InitialDropdown(model, recording)
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = { recording.clear() }) { Text("Clear") }
        }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            StepRow(0, StateChip(nameOf(flow.initial), colors), "initial", null, colors) { recording.deleteRow(0) }
            flow.transitions.forEachIndexed { i, t ->
                StepRow(i + 1, StateChip(nameOf(t.fromId), colors), t.label, if (t.stay) "(stay)" else nameOf(t.target), colors) {
                    recording.deleteRow(i + 1)
                }
            }
        }
    }
}

@Composable
private fun StepRow(index: Int, from: @Composable () -> Unit, label: String, to: String?, colors: DiagramColors, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 遷移内容は横スクロール可能な領域に入れ、狭幅でも潰れず × は右端に残す。
        Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("${index + 1}", color = colors.compositeLabel, modifier = Modifier.width(20.dp))
            from()
            Text(label, color = colors.accent)
            if (to != null) {
                Text("→", color = colors.compositeLabel)
                ChipText(to, colors)
            }
        }
        Text("✕", color = colors.compositeLabel, modifier = Modifier.padding(start = 8.dp).clickable(onClick = onDelete))
    }
}

private fun StateChip(name: String, colors: DiagramColors): @Composable () -> Unit = { ChipText(name, colors) }

@Composable
private fun ChipText(name: String, colors: DiagramColors) {
    Box(
        Modifier.background(colors.nodeFill, RoundedCornerShape(6.dp)).border(1.dp, colors.nodeBorder, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) { Text(name, color = colors.nodeText) }
}

@Composable
private fun NameField(value: String, colors: DiagramColors, onChange: (String) -> Unit) {
    Box(
        Modifier.fillMaxWidth().background(JewelTheme.globalColors.panelBackground, RoundedCornerShape(6.dp))
            .border(1.dp, colors.nodeBorder, RoundedCornerShape(6.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = colors.nodeText, fontSize = 14.sp),
            cursorBrush = SolidColor(colors.nodeText),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CopyButton(text: String, onCopyText: (String) -> Boolean) {
    var copied by remember { mutableStateOf(false) }
    var tick by remember { mutableStateOf(0) }
    OutlinedButton(onClick = { if (onCopyText(text)) { copied = true; tick++ } }) {
        Text(if (copied) "Copied" else "Copy")
    }
    if (copied) LaunchedEffect(tick) { kotlinx.coroutines.delay(1500); copied = false }
}

@Composable
private fun CodeBox(text: AnnotatedString, code: CodeColors, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().background(code.background, RoundedCornerShape(8.dp)).border(1.dp, code.border, RoundedCornerShape(8.dp)),
    ) {
        // 折り返さず (softWrap=false) 横スクロール、SelectionContainer で選択・コピー可能。
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) {
            SelectionContainer {
                BasicText(
                    text = text,
                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = code.base),
                    softWrap = false,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

/**
 * The recording status pill overlaid on the diagram (`ide-test-code.png`): step count, an Initial
 * selector, Clear, and "View Test Code" to open the panel. Its content is a [FlowRow] so a narrow tool
 * window wraps the controls onto extra lines instead of clipping "View Test Code" off the right edge.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RecordingPill(model: StoreDiagramModel, recording: RecordingState, colors: DiagramColors, modifier: Modifier = Modifier) {
    // 最小化: record ドット 1 個のボタンに畳む。クリックで元に戻す。
    if (recording.pillMinimized) {
        Box(
            modifier
                .background(JewelTheme.globalColors.panelBackground, CircleShape)
                .border(1.dp, colors.compositeBorder, CircleShape)
                .clickable { recording.pillMinimized = false }
                .padding(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(10.dp).background(RecordRed, CircleShape))
        }
        return
    }
    val flow = recording.flow
    // 図の幅 (loose 制約) の中で折り返す。狭いと View Test Code などが次の行に回り、潰れない。
    FlowRow(
        modifier
            .background(JewelTheme.globalColors.panelBackground, RoundedCornerShape(24.dp))
            .border(1.dp, colors.compositeBorder, RoundedCornerShape(24.dp))
            .padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(RecordRed, CircleShape))
        Text("${flow.transitions.size} steps recorded", color = colors.nodeText)
        Text("Initial:", color = colors.compositeLabel)
        InitialDropdown(model, recording)
        Text("Clear", color = colors.compositeLabel, modifier = Modifier.clickable { recording.clear() })
        DefaultButton(onClick = { recording.openPanel(FlowPanelTab.TestCode) }) { Text("View Test Code →") }
        // 最小化ボタン (1 アイコンに畳む)。
        Text("–", color = colors.compositeLabel, modifier = Modifier.clickable { recording.pillMinimized = true }.padding(horizontal = 4.dp))
    }
}

/** Dropdown to pick any leaf as the flow's initial state (`ide-test-code.md`: 任意 leaf 上書き). */
@OptIn(ExperimentalJewelApi::class)
@Composable
private fun InitialDropdown(model: StoreDiagramModel, recording: RecordingState) {
    val leaves = model.leaves
    val current = recording.flow.initial
    Dropdown(
        menuContent = {
            leaves.forEach { leaf ->
                selectableItem(selected = leaf.id == current, onClick = { recording.setInitial(leaf.id) }) {
                    Text(leaf.id.dotted)
                }
            }
        },
    ) {
        Text(current?.dotted ?: "?")
    }
}

private val RecordRed = Color(0xFFE5484D)

/** Token colors for the generated-code preview, tuned per theme (no Jewel token for editor syntax). */
private class CodeColors(
    val background: Color,
    val border: Color,
    val base: Color,
    val annotation: Color,
    val string: Color,
    val type: Color,
    val nameHighlight: Color,
)

@Composable
private fun rememberCodeColors(): CodeColors {
    val dark = JewelTheme.isDark
    val globals = JewelTheme.globalColors
    return CodeColors(
        background = if (dark) Color(0xFF1E1F22) else Color(0xFFF7F8FA),
        border = globals.borders.normal,
        base = globals.text.normal,
        annotation = if (dark) Color(0xFFC586C0) else Color(0xFF9B2393),
        string = if (dark) Color(0xFFCE9178) else Color(0xFFA31515),
        type = if (dark) Color(0xFF4EC9B0) else Color(0xFF267F73),
        nameHighlight = if (dark) Color(0x33548AF7) else Color(0x223574F0),
    )
}

// 文字列 / @Annotation / ドット付き型参照 (FeedState.Loading) / flow 名 を色分けする軽量ハイライタ。
private fun highlightKotlin(codeText: String, c: CodeColors, flowName: String?): AnnotatedString {
    val base = """"[^"]*"|@\w+|[A-Z][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+"""
    val pattern = if (!flowName.isNullOrBlank()) "$base|\\b${Regex.escape(flowName)}\\b" else base
    val regex = Regex(pattern)
    return buildAnnotatedString {
        var last = 0
        for (m in regex.findAll(codeText)) {
            if (m.range.first > last) append(codeText.substring(last, m.range.first))
            val t = m.value
            val style = when {
                t.startsWith("\"") -> SpanStyle(color = c.string, background = c.nameHighlight)
                t.startsWith("@") -> SpanStyle(color = c.annotation)
                flowName != null && t == flowName -> SpanStyle(color = c.base, background = c.nameHighlight)
                else -> SpanStyle(color = c.type)
            }
            withStyle(style) { append(t) }
            last = m.range.last + 1
        }
        if (last < codeText.length) append(codeText.substring(last))
    }
}
