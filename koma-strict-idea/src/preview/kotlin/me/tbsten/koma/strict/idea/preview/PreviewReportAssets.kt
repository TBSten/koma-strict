package me.tbsten.koma.strict.idea.preview

/*
 * Inline CSS / JS assets for the generated preview pages ([writeVerifyReport] and the gallery).
 *
 * Everything is embedded into the emitted `index.html` (no CDN, no external files besides the PNGs
 * themselves), so both pages keep working when opened straight from `file://` or archived on CI.
 * [COPY_BTN_CSS] / [COPY_BTN_JS] are shared between the verify report and the gallery; the rest is
 * report-only.
 */

/** Styles for the verify report page (light theme by default, dark via `prefers-color-scheme`). */
internal val REPORT_CSS = """
:root {
  --bg: #f5f6f8;
  --panel: #ffffff;
  --border: #e3e6ea;
  --text: #1b1e23;
  --muted: #6b7280;
  --accent: #3b74d6;
  --changed-bg: #fdecec;
  --changed-fg: #c62828;
  --new-bg: #e7f0fe;
  --new-fg: #1565c0;
  --missing-bg: #fdf0e3;
  --missing-fg: #b35300;
  --unchanged-bg: #e8f4ea;
  --unchanged-fg: #2f7d32;
}
@media (prefers-color-scheme: dark) {
  :root {
    --bg: #17191d;
    --panel: #23262b;
    --border: #363b42;
    --text: #e7e9ec;
    --muted: #9aa1ab;
    --accent: #6ea8ff;
    --changed-bg: #4a2427;
    --changed-fg: #ff8a80;
    --new-bg: #24384f;
    --new-fg: #90caf9;
    --missing-bg: #46351f;
    --missing-fg: #ffb74d;
    --unchanged-bg: #23402a;
    --unchanged-fg: #a5d6a7;
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
a { color: var(--accent); }
code { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; }
.page-head { max-width: 1280px; margin: 0 auto; padding: 28px 4px 8px; }
.page-head h1 { margin: 0 0 8px; font-size: 22px; font-weight: 650; }
.summary { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin: 0; font-size: 13px; color: var(--muted); }
.chip { border-radius: 999px; padding: 2px 10px; font-size: 12.5px; font-weight: 600; }
.chip-changed { background: var(--changed-bg); color: var(--changed-fg); }
.chip-new { background: var(--new-bg); color: var(--new-fg); }
.chip-missing { background: var(--missing-bg); color: var(--missing-fg); }
.chip-unchanged { background: var(--unchanged-bg); color: var(--unchanged-fg); }
.chip-zero { opacity: 0.55; font-weight: 500; }
main { max-width: 1280px; margin: 0 auto; }
.status-section { margin-top: 34px; padding-top: 12px; border-top: 1px solid var(--border); }
.status-head { display: flex; align-items: baseline; gap: 10px; }
.status-head h2 { margin: 0; font-size: 17px; font-weight: 620; }
.count { font-size: 12px; color: var(--muted); border: 1px solid var(--border); border-radius: 999px; padding: 1px 8px; }
.section-blurb { margin: 4px 0 0; color: var(--muted); font-size: 13px; }
.card { margin: 16px 0 0; background: var(--panel); border: 1px solid var(--border); border-radius: 10px; overflow: hidden; }
.card-head { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; padding: 10px 14px; border-bottom: 1px solid var(--border); }
.fname { font-size: 12.5px; }
.badge { font-size: 11px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.03em; border-radius: 5px; padding: 1px 7px; }
.badge-changed { background: var(--changed-bg); color: var(--changed-fg); }
.badge-new { background: var(--new-bg); color: var(--new-fg); }
.badge-missing { background: var(--missing-bg); color: var(--missing-fg); }
.meta { font-size: 12.5px; color: var(--muted); }
.meta-warn { color: var(--missing-fg); font-weight: 600; }
.viewer, .single-body { padding: 12px 14px 14px; }
.mode-tabs { display: flex; gap: 6px; margin-bottom: 10px; }
.mode-btn { font-size: 12.5px; padding: 4px 12px; border-radius: 6px; border: 1px solid var(--border); background: var(--panel); color: var(--text); cursor: pointer; }
.mode-btn:hover { border-color: var(--accent); color: var(--accent); }
.mode-btn.active { background: var(--accent); border-color: var(--accent); color: #ffffff; }
.panel { display: none; }
.panel.active { display: block; }
.two-up { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.two-up figure { margin: 0; min-width: 0; }
.two-up figcaption { font-size: 12px; color: var(--muted); margin-bottom: 4px; }
.frame { overflow: auto; max-height: 72vh; border: 1px solid var(--border); border-radius: 6px; }
.frame > a { display: block; font-size: 0; }
.two-up .frame img { display: block; max-width: 100%; height: auto; }
.stage { overflow: auto; max-height: 72vh; border: 1px solid var(--border); border-radius: 6px; }
.stage > a { display: inline-block; font-size: 0; }
.stage img { display: block; }
.overlay { position: relative; display: inline-block; vertical-align: top; }
.overlay .img-over { position: absolute; top: 0; left: 0; }
.slide-divider { position: absolute; top: 0; bottom: 0; left: 50%; width: 2px; margin-left: -1px; background: var(--accent); pointer-events: none; }
.ctl { display: flex; align-items: center; gap: 10px; margin-top: 8px; font-size: 12px; color: var(--muted); }
.ctl input[type="range"] { flex: 1; max-width: 420px; margin: 0; }
.shot-light { background: #ffffff; }
.shot-dark { background: #2b2d30; }
.hint { margin: 8px 0 0; font-size: 12px; color: var(--muted); }
.unchanged-details summary { cursor: pointer; font-size: 13.5px; color: var(--muted); margin-top: 10px; }
.unchanged-details ul { margin: 8px 0 0; padding: 0; list-style: none; column-width: 320px; column-gap: 24px; }
.unchanged-details li { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 12px; color: var(--muted); padding: 1px 0; }
.page-foot { max-width: 1280px; margin: 48px auto 0; color: var(--muted); font-size: 12.5px; text-align: center; }
""".trim()

/**
 * Styles for the "Copy image" button, shared by the verify report and the gallery. Only relies on
 * the CSS variables both pages define (`--panel` / `--border` / `--text` / `--muted` / `--accent`);
 * report-only variables fall back to a literal.
 */
internal val COPY_BTN_CSS = """
.copy-btn { margin-left: auto; font-size: 12px; padding: 3px 10px; border-radius: 6px; border: 1px solid var(--border); background: var(--panel); color: var(--text); cursor: pointer; white-space: nowrap; }
.copy-btn:hover { border-color: var(--accent); color: var(--accent); }
.copy-btn.copied { border-color: var(--unchanged-fg, #2f7d32); color: var(--unchanged-fg, #2f7d32); }
.copy-btn.copy-fallback { cursor: default; color: var(--muted); border-style: dashed; }
""".trim()

/**
 * Wires every `.copy-btn` to copy its `data-src` PNG to the clipboard
 * (`fetch → blob → ClipboardItem`). Never throws to the page: when the browser refuses (typical on
 * `file://`, where `fetch` and/or clipboard writes are blocked) the button flips to a permanent
 * "right-click to copy" fallback label instead.
 */
internal val COPY_BTN_JS = """
(function () {
  'use strict';
  document.querySelectorAll('.copy-btn').forEach(function (btn) {
    btn.addEventListener('click', function () {
      var src = btn.getAttribute('data-src');
      var original = btn.textContent;
      Promise.resolve()
        .then(function () {
          if (!src) throw new Error('no data-src');
          if (typeof ClipboardItem === 'undefined' || !navigator.clipboard || !navigator.clipboard.write) {
            throw new Error('clipboard API unavailable');
          }
          return fetch(src);
        })
        .then(function (res) {
          if (!res.ok) throw new Error('fetch failed: ' + res.status);
          return res.blob();
        })
        .then(function (blob) {
          // MIME が空で返る環境 (file:// 等) でも image/png として書き込む。
          var png = blob.type === 'image/png' ? blob : new Blob([blob], { type: 'image/png' });
          return navigator.clipboard.write([new ClipboardItem({ 'image/png': png })]);
        })
        .then(function () {
          btn.textContent = 'Copied!';
          btn.classList.add('copied');
          setTimeout(function () {
            btn.textContent = original;
            btn.classList.remove('copied');
          }, 1500);
        })
        .catch(function () {
          // file:// では fetch / clipboard 書き込みが拒否されることがある。右クリック代替へ誘導。
          btn.textContent = 'Right-click the image to copy';
          btn.classList.add('copy-fallback');
          btn.disabled = true;
        });
    });
  });
})();
""".trim()

/**
 * Behaviour for each changed-image comparison viewer: mode-tab switching (`2-up` / `slide` /
 * `blend` / `diff`), the slide wipe (clip-path on the actual image + divider position) and the
 * blend opacity slider.
 */
internal val REPORT_VIEWER_JS = """
(function () {
  'use strict';
  document.querySelectorAll('[data-viewer]').forEach(function (viewer) {
    // モードタブ: 押されたボタンに対応する panel だけ表示する。
    var buttons = viewer.querySelectorAll('.mode-btn');
    var panels = viewer.querySelectorAll('.panel');
    buttons.forEach(function (btn) {
      btn.addEventListener('click', function () {
        var mode = btn.getAttribute('data-mode');
        buttons.forEach(function (b) { b.classList.toggle('active', b === btn); });
        panels.forEach(function (p) { p.classList.toggle('active', p.getAttribute('data-panel') === mode); });
      });
    });
    // slide: actual を左から p% だけ見せる wipe。divider も追従させる。
    var slideRange = viewer.querySelector('.slide-range');
    var slideOver = viewer.querySelector('[data-panel="slide"] .img-over');
    var slideDivider = viewer.querySelector('[data-panel="slide"] .slide-divider');
    function applySlide() {
      var p = Number(slideRange.value);
      slideOver.style.clipPath = 'inset(0 ' + (100 - p) + '% 0 0)';
      if (slideDivider) slideDivider.style.left = p + '%';
    }
    if (slideRange && slideOver) {
      slideRange.addEventListener('input', applySlide);
      applySlide();
    }
    // blend: actual の不透明度をスライダーで変える (0 = golden のみ, 100 = actual のみ)。
    var blendRange = viewer.querySelector('.blend-range');
    var blendOver = viewer.querySelector('[data-panel="blend"] .img-over');
    function applyBlend() {
      blendOver.style.opacity = String(Number(blendRange.value) / 100);
    }
    if (blendRange && blendOver) {
      blendRange.addEventListener('input', applyBlend);
      applyBlend();
    }
  });
})();
""".trim()
