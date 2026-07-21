package me.tbsten.koma.strict.idea.preview

import java.io.File

/**
 * Builds a self-contained `index.html` gallery for the headless preview PNGs.
 *
 * [PreviewMain] renders many `koma-<name>-<theme>.png` files under `build/preview`. This walks that
 * directory, parses each file name into a **scenario** (lce / feed / auth / …) and a **variant**
 * (full tool window / canvas / transitions table / …), then writes a single HTML page that groups the
 * shots scenario-by-scenario. The page is deterministic (no timestamps), uses inline CSS / JS (no
 * CDN), references the PNGs by relative path, adapts to the viewer's light/dark theme, lets each
 * image be opened full size in a new tab, and offers a "Copy image" button per shot ([COPY_BTN_JS],
 * with a right-click fallback when the clipboard is unavailable, e.g. on `file://`).
 */
internal fun writePreviewGallery(outDir: File) {
    val shots = (outDir.listFiles() ?: emptyArray())
        .filter { it.isFile && it.name.startsWith("koma-") && it.name.endsWith(".png") }
        .map { it.name }
        .sorted()
        .map { parseShot(it) }
    // 手動採点 (snapshots/preview-scores.json) があればカードに「みやすさ N/10」バッジを載せる。
    val scores = loadPreviewScores(File("snapshots/preview-scores.json"))

    File(outDir, "index.html").writeText(buildGalleryHtml(shots, scores))
}

/** A single rendered PNG, parsed from its file name. */
private data class PreviewShot(
    val fileName: String,
    val scenarioKey: String,
    val theme: String, // "light" | "dark"
    val variant: Variant,
    val variantLabel: String,
    /** Key into `snapshots/preview-scores.json`: the stem without `koma-` prefix / theme suffix. */
    val scoreKey: String,
)

/** Coarse variant bucket, used only to order shots within a scenario section. */
private enum class Variant {
    FullWindow, CanvasLr, CanvasTb, CanvasZoom, Transitions, TransitionsNarrow
}

// ファイル名のうち scenario ではなく variant を表すトークン (scenario 抽出時に取り除く)。
private val VARIANT_TOKENS = setOf("full", "transitions", "canvas", "tb", "narrow", "zoom")

private fun parseShot(fileName: String): PreviewShot {
    val stem = fileName.removePrefix("koma-").removeSuffix(".png")
    val theme = when {
        stem.endsWith("-dark") -> "dark"
        stem.endsWith("-light") -> "light"
        else -> "light"
    }
    val mid = stem.removeSuffix("-$theme")
    val tokens = mid.split("-").filter { it.isNotEmpty() }

    val tokenSet = tokens.toSet()

    val variant = when {
        "transitions" in tokenSet && "narrow" in tokenSet -> Variant.TransitionsNarrow
        "transitions" in tokenSet -> Variant.Transitions
        "zoom" in tokenSet -> Variant.CanvasZoom
        "tb" in tokenSet -> Variant.CanvasTb
        "canvas" in tokenSet -> Variant.CanvasLr
        else -> Variant.FullWindow
    }
    val variantLabel = when (variant) {
        Variant.FullWindow -> "Full tool window"
        Variant.CanvasLr -> "Canvas · left → right"
        Variant.CanvasTb -> "Canvas · top → bottom"
        Variant.CanvasZoom -> "Canvas · zoomed 1.6×"
        Variant.Transitions -> if ("full" in tokenSet) "Transitions · in tool window" else "Transitions · table only"
        Variant.TransitionsNarrow -> "Transitions · narrow dock"
    }

    // variant トークンを除いた残りが scenario。全部除かれる (zoom / transitions-narrow 等) 場合は
    // mid 全体を scenario キーにフォールバックし、独立セクションとして扱う。
    val scenarioKey = tokens.filterNot { it in VARIANT_TOKENS }
        .joinToString("-")
        .ifEmpty { mid }

    return PreviewShot(fileName, scenarioKey, theme, variant, variantLabel, scoreKey = mid)
}

/** Title + one-line blurb for a scenario section. */
private data class ScenarioMeta(val title: String, val blurb: String)

private val SCENARIO_META = mapOf(
    "lce" to ScenarioMeta("LCE", "Loading / Content / Error — the baseline store."),
    "feed" to ScenarioMeta("Feed", "Feed store with a richer state graph."),
    "feed-branch" to ScenarioMeta("Feed (branch)", "Loading branches into Idle / Error / Test — regression for pushed-apart leaves."),
    "tabs" to ScenarioMeta("Tabs", "Tab-switch store."),
    "longname" to ScenarioMeta("Long names", "Very long state names — wrapping, then font autosize."),
    "wizard" to ScenarioMeta("Wizard", "Form wizard — self-transition, stay+emit on invalid input, terminal Done leaf."),
    "selfloops" to ScenarioMeta("Self-loops", "One node with mixed ENTER / ACTION / RECOVER self-loops fanned onto separate faces."),
    "auth" to ScenarioMeta("Auth", "Auth flow — dashed colored recover edges and @OnExit badges."),
    "settings" to ScenarioMeta("Settings", "Two-level nested composite sharing an `any Loaded` group."),
    "session" to ScenarioMeta("Session", "Edge pointing at a group (SignedOut → SignedIn) landing on the composite box."),
    "multi" to ScenarioMeta("Multi-store", "Multiple @StoreSpec — top store dropdown switching."),
    "unreachable" to ScenarioMeta("Unreachable", "Unreachable state (Broken) shown with the warning color."),
    "setup" to ScenarioMeta("Setup / empty", "No @StoreSpec — setup guidance / empty state."),
    "indexing" to ScenarioMeta("Indexing", "Index-in-progress fallback screen."),
    "zoom" to ScenarioMeta("Zoom", "Canvas scaled 1.6× to check zoom rendering."),
    "oversize" to ScenarioMeta("Oversize", "Graph larger than the canvas cap — auto-fit to keep every node/edge on-canvas, banner explains the scale-down (P1-08: nothing clipped)."),
    "transitions-narrow" to ScenarioMeta("Transitions (narrow)", "Narrow dock — horizontal scroll with sticky header."),
)

// scenario セクションの並び順。ここに無いキーは末尾へ (名前順)。
private val SCENARIO_ORDER = listOf(
    "lce", "feed", "feed-branch", "tabs", "longname", "wizard", "selfloops", "auth",
    "settings", "session", "multi", "unreachable", "setup", "indexing",
    "zoom", "oversize", "transitions-narrow",
)

private fun scenarioMeta(key: String): ScenarioMeta =
    SCENARIO_META[key] ?: ScenarioMeta(
        title = key.split("-").joinToString(" ") { it.replaceFirstChar(Char::uppercase) },
        blurb = "",
    )

private fun buildGalleryHtml(shots: List<PreviewShot>, scores: Map<String, PreviewScore>): String {
    val sections = shots.groupBy { it.scenarioKey }
        .toList()
        .sortedWith(compareBy({ orderIndex(it.first) }, { it.first }))

    val sb = StringBuilder()
    sb.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n")
    sb.append("<meta charset=\"utf-8\">\n")
    sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
    sb.append("<title>Koma Strict — preview gallery</title>\n")
    sb.append("<style>\n").append(GALLERY_CSS).append("\n").append(COPY_BTN_CSS).append("\n</style>\n")
    sb.append("</head>\n<body>\n")

    sb.append("<header class=\"page-head\">\n")
    sb.append("  <h1>Koma Strict — preview gallery</h1>\n")
    sb.append("  <p class=\"summary\">")
        .append(shots.size).append(" images")
        .append(" · ").append(sections.size).append(" scenarios")
        .append(" · click any image to open it full size</p>\n")
    sb.append("</header>\n")

    sb.append("<main>\n")
    for ((key, sectionShots) in sections) {
        val meta = scenarioMeta(key)
        val ordered = sectionShots.sortedWith(
            compareBy({ it.variant.ordinal }, { if (it.theme == "light") 0 else 1 }, { it.fileName }),
        )
        sb.append("<section class=\"scenario\">\n")
        sb.append("  <div class=\"scenario-head\">\n")
        sb.append("    <h2>").append(esc(meta.title)).append("</h2>\n")
        sb.append("    <span class=\"count\">").append(sectionShots.size).append("</span>\n")
        sb.append("  </div>\n")
        if (meta.blurb.isNotEmpty()) {
            sb.append("  <p class=\"blurb\">").append(esc(meta.blurb)).append("</p>\n")
        }
        sb.append("  <div class=\"grid\">\n")
        for (shot in ordered) {
            sb.append(cardHtml(shot, scores[shot.scoreKey]))
        }
        sb.append("  </div>\n")
        sb.append("</section>\n")
    }
    sb.append("</main>\n")

    sb.append("<footer class=\"page-foot\">Generated by <code>updatePreview</code> · ")
        .append(shots.size).append(" PNGs under <code>build/preview</code></footer>\n")

    sb.append("<script>\n").append(COPY_BTN_JS).append("\n</script>\n")
    sb.append("</body>\n</html>\n")
    return sb.toString()
}

private fun cardHtml(shot: PreviewShot, score: PreviewScore?): String {
    val themeClass = if (shot.theme == "dark") "badge badge-dark" else "badge badge-light"
    return buildString {
        append("    <figure class=\"card\">\n")
        // shot の背景はビューアのテーマではなく shot 自身のテーマに合わせる (light 版は明るい下地、
        // dark 版は暗い下地)。これで dark viewer でも light shot の余白が黒くならない (P2-15)。
        append("      <a class=\"shot shot-").append(shot.theme).append("\" href=\"")
            .append(shot.fileName).append("\" target=\"_blank\" rel=\"noopener\">\n")
        append("        <img loading=\"lazy\" src=\"").append(shot.fileName)
            .append("\" alt=\"").append(esc(shot.fileName)).append("\">\n")
        append("      </a>\n")
        append("      <figcaption>\n")
        append("        <span class=\"variant\">").append(esc(shot.variantLabel)).append("</span>\n")
        append("        <span class=\"").append(themeClass).append("\">").append(shot.theme).append("</span>\n")
        if (score != null) {
            // 手動採点バッジ。0-3 赤 / 4-6 黄 / 7-10 緑 (控えめな色)。理由は title と小さい文字の両方に出す。
            append("        <span class=\"score ").append(scoreClass(score.score))
                .append("\" title=\"").append(esc(score.reason)).append("\">みやすさ ")
                .append(score.score).append("/10</span>\n")
        }
        append("        <button class=\"copy-btn\" type=\"button\" data-src=\"")
            .append(esc(shot.fileName)).append("\">Copy image</button>\n")
        if (score != null) {
            append("        <span class=\"score-reason\">").append(esc(score.reason)).append("</span>\n")
        }
        append("        <code class=\"fname\">").append(esc(shot.fileName)).append("</code>\n")
        append("      </figcaption>\n")
        append("    </figure>\n")
    }
}

private fun scoreClass(score: Int): String = when {
    score <= 3 -> "score-low"
    score <= 6 -> "score-mid"
    else -> "score-high"
}

private fun orderIndex(key: String): Int =
    SCENARIO_ORDER.indexOf(key).let { if (it >= 0) it else SCENARIO_ORDER.size }

private fun esc(s: String): String = s
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")

private val GALLERY_CSS = """
:root {
  --bg: #f5f6f8;
  --panel: #ffffff;
  --border: #e3e6ea;
  --text: #1b1e23;
  --muted: #6b7280;
  --accent: #3b74d6;
  --shot-bg: #f0f1f4;
  --badge-light-bg: #eaf1ff;
  --badge-light-fg: #2b5bb0;
  --badge-dark-bg: #2b2f36;
  --badge-dark-fg: #d7dce4;
  --score-low-bg: #fdecea;
  --score-low-fg: #a13c31;
  --score-mid-bg: #faf3d9;
  --score-mid-fg: #8a6d1a;
  --score-high-bg: #e8f5e9;
  --score-high-fg: #2e7d43;
}
@media (prefers-color-scheme: dark) {
  :root {
    --bg: #17191d;
    --panel: #23262b;
    --border: #363b42;
    --text: #e7e9ec;
    --muted: #9aa1ab;
    --accent: #6ea8ff;
    --shot-bg: #1c1f24;
    --badge-light-bg: #33465f;
    --badge-light-fg: #cfe0ff;
    --badge-dark-bg: #101216;
    --badge-dark-fg: #c3c9d2;
    --score-low-bg: #462a27;
    --score-low-fg: #f0b3a9;
    --score-mid-bg: #453d22;
    --score-mid-fg: #e6cf85;
    --score-high-bg: #253d2b;
    --score-high-fg: #a2d6a9;
  }
}
* { box-sizing: border-box; }
body {
  margin: 0;
  padding: 0 24px 64px;
  background: var(--bg);
  color: var(--text);
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
  line-height: 1.5;
}
.page-head {
  max-width: 1280px;
  margin: 0 auto;
  padding: 28px 4px 8px;
}
.page-head h1 { margin: 0 0 4px; font-size: 22px; font-weight: 650; }
.summary { margin: 0; color: var(--muted); font-size: 14px; }
main { max-width: 1280px; margin: 0 auto; }
.scenario {
  margin-top: 34px;
  padding-top: 12px;
  border-top: 1px solid var(--border);
}
.scenario-head { display: flex; align-items: baseline; gap: 10px; }
.scenario-head h2 { margin: 0; font-size: 17px; font-weight: 620; }
.count {
  font-size: 12px;
  color: var(--muted);
  border: 1px solid var(--border);
  border-radius: 999px;
  padding: 1px 8px;
}
.blurb { margin: 4px 0 0; color: var(--muted); font-size: 13.5px; }
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
  margin-top: 14px;
}
.card {
  margin: 0;
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 10px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.shot {
  display: block;
  background: var(--shot-bg);
  border-bottom: 1px solid var(--border);
}
/* shot の下地は shot 自身のテーマで固定 (ビューアのテーマに追従させない) = 撮影時の背景と一致。 */
.shot-light { background: #ffffff; }
.shot-dark { background: #2b2d30; }
.shot img {
  display: block;
  width: 100%;
  height: auto;
  max-width: 100%;
}
figcaption {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
}
.variant { font-size: 13px; font-weight: 560; }
.badge {
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.03em;
  border-radius: 5px;
  padding: 1px 7px;
}
.badge-light { background: var(--badge-light-bg); color: var(--badge-light-fg); }
.badge-dark { background: var(--badge-dark-bg); color: var(--badge-dark-fg); }
/* 手動採点バッジ (snapshots/preview-scores.json)。0-3 赤 / 4-6 黄 / 7-10 緑。 */
.score {
  font-size: 11px;
  font-weight: 600;
  border-radius: 5px;
  padding: 1px 7px;
  white-space: nowrap;
}
.score-low { background: var(--score-low-bg); color: var(--score-low-fg); }
.score-mid { background: var(--score-mid-bg); color: var(--score-mid-fg); }
.score-high { background: var(--score-high-bg); color: var(--score-high-fg); }
.score-reason {
  flex-basis: 100%;
  font-size: 11.5px;
  color: var(--muted);
}
.fname {
  flex-basis: 100%;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 11.5px;
  color: var(--muted);
}
.page-foot {
  max-width: 1280px;
  margin: 48px auto 0;
  color: var(--muted);
  font-size: 12.5px;
  text-align: center;
}
code { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; }
""".trim()
