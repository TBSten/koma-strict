package me.tbsten.koma.strict.idea.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.tbsten.koma.strict.idea.ui.DiagramColors
import org.jetbrains.jewel.ui.component.Text
import kotlin.math.roundToInt

/**
 * The message surfaces the tool window shows instead of (or above) the diagram: a degraded-analysis
 * banner, and the full-window "indexing", "render error", and "no @StoreSpec" guidance states.
 */

/** A thin inline banner above the tabs when the model resolved only partially (names, no triggers). */
@Composable
internal fun DegradedBanner(error: String?, colors: DiagramColors) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(colors.warningFill)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            "Analysis incomplete — showing names only." + (error?.let { " ($it)" } ?: ""),
            color = colors.warningText,
        )
    }
}

/**
 * A thin inline banner when analysis succeeded but some `nextState` / `emit` / type-argument values
 * could not be resolved (foreign or half-typed code). The diagram and table still show what resolved,
 * with the unresolved values marked `?`, so the user knows the picture is incomplete rather than
 * believing a declaration was empty.
 */
@Composable
internal fun UnresolvedBanner(colors: DiagramColors) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(colors.warningFill)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            "Some references couldn't be resolved (shown as \"?\") — the diagram may be incomplete.",
            color = colors.warningText,
        )
    }
}

/**
 * A thin inline banner shown above the diagram when the figure is larger than the canvas extent cap
 * and has been auto-fit down to a smaller zoom so nothing is clipped (`ide-review.md` P1-08). Without
 * it the automatic shrink from [requestedZoom] to [renderZoom] would look like a rendering bug; the
 * banner makes the trade-off explicit and points at the Transitions tab, which always lists every
 * transition in full regardless of figure size.
 */
@Composable
internal fun OversizeBanner(requestedZoom: Float, renderZoom: Float, colors: DiagramColors) {
    val requestedPct = (requestedZoom * 100).roundToInt()
    val fitPct = (renderZoom * 100).roundToInt()
    Box(
        Modifier
            .fillMaxWidth()
            .background(colors.warningFill)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            "Diagram too large to show at $requestedPct% — scaled to $fitPct% to fit. " +
                "No states or transitions are hidden; the Transitions tab lists them all.",
            color = colors.warningText,
        )
    }
}

@Composable
internal fun IndexingGuidance(colors: DiagramColors) {
    // index 中は注釈が解決できず正しい図を出せないので、半端に描かず「indexing 中」だけ案内する。
    // Controller が DumbService.exitDumbMode で自動再解析するので、完了後に図へ切り替わる。
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Indexing…", fontWeight = FontWeight.SemiBold, color = colors.nodeText)
        Spacer(Modifier.height(6.dp))
        Text(
            "The state diagram is unavailable while the project is being indexed. It appears automatically once indexing finishes.",
            color = colors.compositeLabel,
        )
    }
}

@Composable
internal fun RenderErrorGuidance(cause: Throwable, colors: DiagramColors) {
    // lowering/layout が想定外の入力で落ちても、ツールウィンドウ全体を巻き込まずここで止める。
    // Reload か、コードを直せば次の再解析で復帰する。
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Couldn't render this diagram", fontWeight = FontWeight.SemiBold, color = colors.warningText)
        Spacer(Modifier.height(6.dp))
        Text(
            "The file may be mid-edit or in a shape the diagram can't lay out yet. " +
                "Fix the code or press Reload to retry." + (cause.message?.let { " ($it)" } ?: ""),
            color = colors.compositeLabel,
        )
    }
}

@Composable
internal fun SetupGuidance(colors: DiagramColors) {
    // 空状態は中央寄せ。製品名見出しは tool window タブ名と重複するので出さず、次の一手だけを案内する。
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No @StoreSpec in this file", fontWeight = FontWeight.SemiBold, color = colors.nodeText)
        Spacer(Modifier.height(6.dp))
        Text(
            "Open a Kotlin file that declares a @StoreSpec state root to see its live state diagram and transition table.",
            color = colors.compositeLabel,
        )
    }
}
