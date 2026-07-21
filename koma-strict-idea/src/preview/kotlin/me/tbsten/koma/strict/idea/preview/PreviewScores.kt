package me.tbsten.koma.strict.idea.preview

import java.io.File

/**
 * Manually curated readability score for one preview shot.
 *
 * @property score 0..10 — how easy the rendered state diagram is to read (10 = best).
 * @property reason Short human explanation for the score.
 */
internal data class PreviewScore(val score: Int, val reason: String)

/**
 * Loads `snapshots/preview-scores.json` — a flat object mapping a shot key (the PNG stem without
 * the `koma-` prefix and `-light`/`-dark` suffix, e.g. `feed-branch`) to `{ "score": N, "reason": S }`.
 *
 * Returns an empty map when the file does not exist. The file is parsed with the tiny hand-written
 * reader below (no JSON dependency in the preview source set); a malformed file logs a warning and
 * degrades to "no scores" instead of failing the whole preview run.
 */
internal fun loadPreviewScores(file: File): Map<String, PreviewScore> {
    if (!file.isFile) return emptyMap()
    return try {
        parsePreviewScores(file.readText())
    } catch (e: IllegalStateException) {
        System.err.println("PREVIEW_SCORES_WARN failed to parse ${file.path}: ${e.message}")
        emptyMap()
    }
}

// { "<name>": { "score": <int>, "reason": "<string>" }, ... } だけを読む最小パーサ。
private fun parsePreviewScores(text: String): Map<String, PreviewScore> {
    val cur = JsonCursor(text)
    val result = LinkedHashMap<String, PreviewScore>()
    cur.expect('{')
    if (!cur.tryConsume('}')) {
        do {
            val name = cur.readString()
            cur.expect(':')
            result[name] = cur.readScoreObject()
        } while (cur.tryConsume(','))
        cur.expect('}')
    }
    return result
}

/** Minimal cursor over a JSON text; only supports the exact shape used by the score file. */
private class JsonCursor(private val text: String) {
    private var i = 0

    private fun skipWs() {
        while (i < text.length && text[i].isWhitespace()) i++
    }

    fun expect(c: Char) {
        skipWs()
        check(i < text.length && text[i] == c) { "expected '$c' at offset $i" }
        i++
    }

    fun tryConsume(c: Char): Boolean {
        skipWs()
        if (i < text.length && text[i] == c) {
            i++
            return true
        }
        return false
    }

    fun readString(): String {
        expect('"')
        val sb = StringBuilder()
        while (true) {
            check(i < text.length) { "unterminated string at offset $i" }
            when (val ch = text[i++]) {
                '"' -> return sb.toString()
                '\\' -> {
                    check(i < text.length) { "unterminated escape at offset $i" }
                    when (val esc = text[i++]) {
                        '"', '\\', '/' -> sb.append(esc)
                        'n' -> sb.append('\n')
                        't' -> sb.append('\t')
                        'r' -> sb.append('\r')
                        'u' -> {
                            check(i + 4 <= text.length) { "bad \\u escape at offset $i" }
                            sb.append(text.substring(i, i + 4).toInt(16).toChar())
                            i += 4
                        }
                        else -> error("unsupported escape '\\$esc' at offset $i")
                    }
                }
                else -> sb.append(ch)
            }
        }
    }

    fun readInt(): Int {
        skipWs()
        val start = i
        if (i < text.length && text[i] == '-') i++
        while (i < text.length && text[i].isDigit()) i++
        check(i > start) { "expected integer at offset $start" }
        return text.substring(start, i).toInt()
    }

    fun readScoreObject(): PreviewScore {
        var score: Int? = null
        var reason: String? = null
        expect('{')
        do {
            val key = readString()
            expect(':')
            when (key) {
                "score" -> score = readInt()
                "reason" -> reason = readString()
                else -> error("unknown key '$key' at offset $i")
            }
        } while (tryConsume(','))
        expect('}')
        return PreviewScore(
            score = checkNotNull(score) { "missing 'score' at offset $i" },
            reason = checkNotNull(reason) { "missing 'reason' at offset $i" },
        )
    }
}
