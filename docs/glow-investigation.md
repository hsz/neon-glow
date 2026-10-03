# Glow investigation: a real, blurred halo behind IDE text

This document records the proof of concept behind IDE Synthwave: **can a plugin paint a real neon glow — a blurred,
tinted copy of every glyph — under the text of an IntelliJ editor with plain Java2D, at a cost that keeps up with
typing and scrolling?** Short answer: yes, with one caveat about full-viewport repaints on HiDPI, see §6.
The original editor-only measurements below remain historical; the IDE-wide extension is described in §10.

## 1. What "real" means here

```
glyph outline → antialiased alpha raster (device px) → separable Gaussian blur (σ) → tint → ARGB_PRE mask
                                                              ↓ cached per glyph
editor background pass: for every visible glyph, drawImage(mask) at integer device pixels, then the editor draws the glyph on top
```

The halo is the glyph's own coverage convolved with a Gaussian, coloured with the token's foreground. Nothing is
approximated with stroke rings or gradients.

## 2. Render hook: `CustomHighlighterRenderer`, not the glass pane

Two hooks were considered (see the sibling `ide-atmosphere` repository for the glass-pane experience):

| | Highlighter under-glow (chosen) | Glass-pane over-glow |
|---|---|---|
| Where it paints | Inside `EditorPainter`'s pass, `CustomHighlighterOrder.AFTER_BACKGROUND`: over the line background and selection, **under** the glyphs | `IdeGlassPane.addPainter(editor.contentComponent, …)`: over everything, including the glyphs |
| Repaints | Come for free from the editor: scroll (blit + exposed strip), edits (changed lines), caret blink (caret rect) | Must be driven by own Document / VisibleArea / Caret listeners |
| Z-order | Correct by construction; the glyph stays crisp on top of its halo | Halo tints the glyph itself; reads as emission on dark themes but blurs the text |
| Risk | Painting on every editor repaint — must be cheap | Dirty-rect bookkeeping, same as `ide-atmosphere`'s `DirtyRegionCollector` |

Implementation: `EditorGlow` adds one document-wide `RangeHighlighter` (`HighlighterLayer.FIRST`, greedy on both
sides, `EXACT_RANGE`) to the **editor** markup model and sets `GlowHighlighterRenderer` as its custom renderer.
`EditorPainter` collects `AFTER_BACKGROUND` renderers while walking the markup and paints them after the background
and before `paintTextWithEffects`, with the graphics translated back to content-component coordinates.

Platform facts verified against 2025.3.6.1 sources:

- `CustomHighlighterRenderer.getOrder()` / `CustomHighlighterOrder` exist (`@ApiStatus.Experimental`, since 2022.3).
- `RangeMarkerImpl.applyChange` keeps a greedy marker covering `[0, length]` across inserts, deletes and full
  `setText` replacements ("Changes inside marker's area. Expand/collapse"). `EditorGlow` still re-validates the
  range on every document change as a cheap safety net.
- Light-test fixture editors (`myFixture.configureByText`) are `EditorKind.UNTYPED`; the listener attaches only to
  `MAIN_EDITOR`, so tests create editors via `EditorFactory.createEditor(document, project, EditorKind.MAIN_EDITOR)`.

## 3. Token colours and glyph layout

- **Colours** come from the lexer-based `EditorHighlighter` (`editorEx.highlighter.createIterator(offset)`):
  `textAttributes.foregroundColor ?: scheme.defaultForeground`. They are available synchronously inside paint and
  consistent with the document. Semantic (annotator) colours — Java fields, Kotlin smart casts, injected
  languages — are **not** merged yet (follow-up: walk `MarkupModelEx` highlighters overlapping the token).
- **Layout** (`TokenLayout`): each token is split into single-line, tab-free segments; a segment is anchored at
  `editor.offsetToPoint2D(start, leanForward = true)` with the baseline at `y + editor.ascent`, and one
  `GlyphVector` supplies the per-glyph advances. The total advance is verified against `offsetToPoint2D(end)`
  (tolerance 0.5 px); on a mismatch — soft wrap inside the segment, inline inlays, a font that cannot display the
  text — every code point is positioned with its own `offsetToPoint2D` call and the font
  `ComplementaryFontsRegistry.getFontAbleToDisplay` picks, exactly like the editor does.
- **Font render context**: the editor adopts the `FontRenderContext` of the `Graphics` it is painted with
  (`EditorView.checkFontRenderContext`) and forces the IDE's editor fractional-metrics hint
  (`UISettings.editorFractionalMetricsHint`). `TokenLayout.editorFontRenderContext` mirrors that, so glyph-vector
  advances match the editor's layout to the float: the benchmark below reports **0 fallback segments** for ASCII
  code at 1x and 2x. Surrogate pairs stay on the fast path (the low surrogate maps to an invisible glyph);
  colour-emoji glyphs have no outline and therefore no halo.

## 4. Masks, HiDPI and the atlas

- `GlowMaskRenderer` rasterises the outline **in device pixels** (`sysScale` from `JBUIScale.sysScale(g)`: the
  graphics configuration's scale on screen, the explicit transform scale on an offscreen image),
  blurs an alpha-only `FloatArray` with `GaussianBlur.separable` (kernel radius `3σ`, raster padded by the same)
  and colourises into `TYPE_INT_ARGB_PRE` via `setRGB`, which premultiplies and keeps the image *manageable*
  (eligible for the Metal/D3D texture cache) — writing into the `DataBuffer` directly would not.
- `σ = radius · sysScale / 2`: the visible halo reaches about `2σ` (= the configured radius in user pixels), the
  raster `3σ`. Intensity multiplies the blurred alpha before clamping; thin strokes blur to low coverage, so the
  default is `3`.
- Blitting: on a Retina surface `Graphics2D.getTransform()` does **not** contain the device scale (it lives in the
  surface and is re-applied by `setTransform`), so the renderer never swaps transforms. Each mask is drawn through
  a `1 / sysScale` transform positioned at `devicePixel / sysScale`; the composite transform is a pure integer
  device translation and the blit is 1:1, so a 2x mask lands pixel-exact on a 2x display (no resampling, no
  mushy halo).
- `GlyphGlowAtlas`: access-ordered LRU, keyed by `(glyphCode, family, style, size2D, argb, sysScale, radiusPx)`,
  default capacity 1 500. Only `clear()` (colour scheme / settings change) or eviction drops entries; edits and
  scrolling never do. Measured footprint for one scheme: 56 masks ≈ 146 KB at 1x, ≈ 565 KB at 2x — a full
  atlas of 1 500 entries stays well under 20 MB at 2x.

## 5. Bleed handling

A halo spills `3σ` beyond its glyph, into the neighbouring lines. Two repaint paths keep it consistent:

1. **Into the clip**: the renderer inflates the clip by `GlowHighlighterRenderer.repaintInflation(radius)`
   (`ceil(1.5 · radius) + 1` user px, scale-independent) before mapping it to lines, so halos of lines just outside
   the clip are redrawn where they overlap it. Painting outside the real clip is discarded by the graphics clip.
2. **Out of the edited line**: the editor repaints exactly the changed lines; `EditorGlow`'s bulk-aware
   `DocumentListener` additionally repaints the changed visual-line range inflated by the same padding, full width,
   so stale halo fragments never survive on adjacent lines. Bulk updates repaint the whole component once at the end.

## 6. Measured cost

Numbers from `GlowPaintBenchmarkTest` (headless **software** pipeline, `BufferedImage` target, Apple Silicon,
JBR 25, JetBrains Mono 13, 22 px lines, radius 6 px, intensity 3). The viewport is 1200×900 user px showing
40 lines of Kotlin-like text = **2 265 glyphs**. The editor is painted through its real component so it adopts the
same font render context as in the IDE.

| | 1x | 2x |
|---|---|---|
| Editor text paint alone (median) | 3.8 ms | 2.8 ms |
| Glow, cold atlas (56 misses, incl. blur) | 20 ms | 24 ms |
| Glow, warm atlas, full viewport (min / median) | 4.6 / 5.4 ms | 8.9 / 9.1 ms |
| Editor + glow, full viewport (median) | 6.0 ms | 11.0 ms |
| Glow for one edited line + bleed (197 glyphs) | 0.24 ms | 0.52 ms |
| Fallback (per-character) segments | 0 | 0 |
| Atlas after warm-up | 56 masks, 146 KB | 56 masks, 565 KB |

Reading the numbers:

- **Typing** (one line plus bleed): well under 1 ms at both scales — the target was met with margin.
- **Scrolling**: Swing's `JViewport` blit-scrolls and repaints only the exposed strip, so a scroll costs a few
  lines, i.e. the typing case times a small factor.
- **Full viewport** (resize, tool-window toggle, theme switch, first show): 5 ms at 1x, 9 ms at 2x in software,
  roughly 4 µs per glyph. That is above the "low single-digit ms" goal at 2x. The cost is composited pixel area:
  with `3σ` padding every mask is ~5× the glyph's own area (≈ 50×56 device px per glyph at 2x). On the IDE's Metal
  pipeline the masks are cached as textures after two draws and the per-glyph cost becomes draw-call overhead; the
  sandbox run confirmed the glow renders correctly in LightEdit at 2x (syntax-coloured halos, no errors in
  `idea.log`), but a modal dialog prevented collecting `GlowStats` lines in that session — run
  `./gradlew runIde`, open a file, type and scroll for 10 s and read the `synthwave:` lines in
  `.intellijPlatform/sandbox/ide-synthwave/IU-2025.3.6.1/log_runIde/idea.log` to get Metal numbers.
- **Cold atlas**: ~0.4 ms per mask (blur dominates); a new colour scheme or font triggers one such burst for the
  visible glyph set and is not noticeable.

Cheap follow-ups if the 2x full-viewport cost matters: pad the raster at `2.5σ` instead of `3σ` (≈ 20 % fewer
pixels, tail below 1/255 at intensity 3), composite a whole segment's masks into one per-line scratch image when a
line is painted repeatedly, or drop the glyph-level cache for a per-visual-line cache keyed by `(text hash, fonts,
colours, scale)` which turns a full repaint into ~40 blits.

## 7. Debug statistics

With `-Dide.synthwave.debug=true` (set by `./gradlew runIde`) `GlowStats` logs every 10 s:

```
synthwave: paints=… paint avg=…µs max=…µs (… glyphs) glyphs/paint=… fallback segments=… atlas hits=… misses=… size=… (… MB) evictions=… cpu≈…%
```

`fallback segments` should stay at 0 for ordinary code; a non-zero value points at ligatures, inlays or fonts that
disagree with the glyph-vector advances and are handled by the slower per-character path.

## 8. Known limitations and follow-ups

- The editor highlighter fallback uses lexer colours only, and its fast path lays out text left-to-right. The
  IDE-wide text hook instead follows the actual glyph vectors and foregrounds, including bidi and styled runs.
- Colour-emoji and other bitmap glyphs have no outline and therefore no glow.
- Fold placeholders are skipped by the editor highlighter fallback; actual placeholder text drawing is covered
  by the IDE-wide hook.
- The paint path reuses its clip, point, transform and layout buffers, but still allocates one small `GlyphKey`
  per glyph for the atlas lookup (plus one `String` per segment for the glyph vector); a mutable lookup key or a
  two-level `(font, colour) → glyph code` cache would make it allocation-free.
- OS-native menu/title text and embedded browser or terminal surfaces that bypass Swing text drawing cannot be
  intercepted by this Java2D hook. Non-colour paints and non-source-over composites are left unchanged.
- UI halos obey component/text clips, so a tightly clipped label or cell can truncate its halo. No animation.

## 9. Manual sandbox checklist

1. `./gradlew runIde`, open a project and a Kotlin/Java file: every glyph has a halo in its token colour.
2. Type on a long line: no stale halo fragments above or below the edited line; the caret keeps up.
3. Scroll fast with the wheel and with Page Down: no tearing, no lag.
4. Compare a 1x and a 2x display (or change the display scaling): the halo keeps the same extent in points and
   stays crisp at 2x.
5. `File | Power Save Mode`: the glow disappears; switching it off brings it back.
6. Switch the colour scheme / Light–Dark theme: halos recolour immediately.
7. `Settings | Appearance & Behavior | Synthwave`: radius and intensity apply on *Apply*; the checkbox and
   `View | Appearance | Synthwave Glow` toggle it.
8. Inspect tool-window trees, table cells, editor tabs, status labels, buttons, search popups and settings dialogs:
   text and standard icons glow in their current colours; selection, hover and disabled states remain usable.
9. Open a new dialog or detached tool window after startup, then close it. Toggle glow and Power Save while a
   popup is open; all Swing windows update without reopening them.
10. Unload/reload the plugin, resize windows and exercise lightweight popups: original layered panes and popup
    layers must be restored, with no duplicate halo or input/focus changes.

## 10. IDE-wide text rendering

`UiGlow` registers a disposable `JBSwingUtilities.addGlobalCGTransform` hook. Its `GlowGraphics2D` delegates all
shape operations, but intercepts strings, attributed strings, char/byte arrays, glyph vectors and images (§11).
`TextLayout` supplies shaped/bidi and styled glyph runs; each glyph's own font, position, transform and current
foreground select a mask from the same bounded atlas. The mask is drawn first, then the crisp glyph. Graphics
copies retain the wrapper; nested platform hooks detect an existing wrapper instead of applying glow twice.
Full font attributes and individual glyph transforms are part of UI mask keys to prevent cache collisions.

Not every platform component invokes the global hook. For complete Swing subtree coverage, existing and newly
opened `RootPaneContainer` windows receive a transparent layered-pane wrapper that invokes it. The original pane
remains intact as a full-size child: its layout, content/menu identities, custom painters and layers are preserved.
Lightweight popups inherit the wrapper; heavyweight popup/dialog windows are installed on window-open/show events.
On window close or service disposal, the original pane is restored and any new popup/content children are moved
back with their layers and positions. The global hook and AWT listener are removed on disposal.

Application-frame/welcome-screen listeners initialize this without requiring an editor. A project startup
activity also handles loading into an already-running project. Headless application startup installs no hooks;
tests explicitly own and dispose their hooks. Settings, theme and Power Save changes repaint window roots as well
as attached editors. Existing main-editor highlighters still provide inflated document-edit repaints and serve
as a fallback for paint paths without a wrapped graphics; their renderer skips under-glow when the text hook is
already active, avoiding a doubled editor halo. Console, diff and preview editors inherit the UI hook naturally.

`GlowGraphics2DTest`, `UiGlowTest` and `UiEditorGlowTest` cover pixel halos/colours at 1x and 2x, child graphics,
attributed text and drawing APIs, disabled/Power Save states, cache reuse, root restoration and popup layers,
nested-hook deduplication, and actual main/console editor paint paths. Debug statistics include UI glyph-run glow
cost; those paint counts are not directly comparable to the historical full-viewport editor measurements in §6.
Live UI performance and visual checks still require the manual sandbox checklist above.

A sandbox startup check on 2026-10-03 (IntelliJ 2025.3.6.1, default radius/intensity, existing project restored)
confirmed the hook was active without painting exceptions. The first 10-second statistics window reported 4 039
glyph-run paints, 93 µs average glow cost, a 55.6 ms cold/startup maximum, 356 atlas misses and 364 entries using
6.8 MB. Subsequent mostly idle windows averaged 29–47 µs per small run with no new misses. These are UI-run
measurements, not a full-viewport scrolling benchmark or exhaustive visual sign-off. The sandbox also reported
bundled-plugin dependency and workspace-cache metadata warnings, outside the glow painting stack.

## 11. Icon rendering

`GlowGraphics2D` intercepts image drawing, including scaled, cropped, mirrored and affine-transformed images,
background-colour overloads, buffered-image filters and buffered rendered images. IntelliJ's cached SVG icons
are rasterised before reaching this boundary, so they receive the same effect as PNG/raster icons. The halo
is painted first; the original draw call and its return value are preserved. Shape painting remains untouched.

`ImageGlowAtlas` rasterises the visible source region in device space, then Gaussian-blurs alpha and all three
premultiplied colour channels. Transparent RGB cannot contaminate the halo; multicolour and partially transparent
icons retain their own hues and opacity. Radius and intensity come from the existing settings. Display scale is
retained by child graphics even when platform icon painters temporarily unscale their contexts; HiDPI wrappers
are unwrapped before pixel access. Blits preserve the clip and composite without altering the caller's graphics.

The image cache is access-ordered, capped at 256 masks and 16 MiB of retained pixel data (including source keys).
Keys include source pixels, dimensions, normalised device transform, blur radius/scale and intensity. This avoids
re-blurring unchanged icons while detecting image mutations, at the cost of a small pixel snapshot on each paint.
Theme/settings invalidation and disposal clear the cache. Disabled glow and Power Save perform no halo rendering.

Images larger than 128 logical pixels or 512 device pixels per side are deliberately left unchanged, as are
non-source-over composites and images that have not finished loading. This excludes backgrounds and large UI
buffers. Icons painted exclusively with vector primitives, and OS-native/non-Swing surfaces, remain outside this
image interception. Component clips can truncate icon halos just as they can text halos.

`IconGlowGraphics2DTest`, `ImageGlowAtlasTest` and `UiIconGlowTest` cover multicolour/transparency, unchanged original
pixels, image overloads, crops, transforms, filters, HiDPI equivalence, unscaled children, disabled/Power Save and
composite guards, cache reuse/mutation/eviction/budgets, clearing/disposal, Swing label/menu icons and a real
platform SVG loaded through `IconLoader` at 1x and 2x. Headless platform tests explicitly activate the real icon
loader rather than using dummy icons. The debug paint statistics still measure text, not image halo cost; live
icon performance and exhaustive visual verification remain part of the manual sandbox checklist.

## 12. Configurable targets and brightness

`GlowSettings` persists independent `editorText`, `uiText` and `icons` switches, defaulting to true. Old XML states
without these fields keep all targets enabled. The master switch preserves individual choices, and Apply/Reset
continues to use an independent draft, with cache invalidation and repainting on Apply.

`UiGlow` classifies editor content/gutter components (and their children) at the global graphics hook. When a root
wrapper is already inherited, `TextTargetGraphics` scopes only text operations to the current component's target:
it neither unwraps platform graphics decorators nor adds another halo renderer. Child graphics retain the scope;
temporary policy changes are restored in `finally`, so ordinary Swing siblings cannot inherit an editor choice.
The main-editor highlighter fallback and edited-line bleed repaint also respect the editor switch.

Brightness is a separate finite 0–1 halo-opacity fraction, applied at blit time after the atlas has amplified and
clamped coverage: `A_halo = brightness × clamp(intensity × A_blurred, 0, 1)`, multiplied by the caller's composite
and foreground alpha where applicable. Each glyph/icon halo is then source-over composited; overlapping halos
accumulate, so the total alpha of a complete text run does not scale linearly. The original text/icon draws and
the caller's graphics state are unchanged. Brightness zero skips all halo work, including mask cache lookups and
editor bleed repaints. Masks do not depend on brightness; direct opacity changes can reuse them.

The default is 100% to preserve earlier rendering. SynthWave '84's brightness default is 45%; our slider exposes
that same opacity fraction, but not its hard-coded colours, selective shadows or injection mechanism. Disabling
editor text while keeping UI/icon switches on is the closest runtime counterpart of upstream `disableGlow`.
See [the cited upstream investigation](synthwave-options.md) for exact option/command semantics and differences.

Automated tests cover all target combinations, nested/decorated graphics and all text entry points, sibling scope
isolation, real editor/UI painting under one Swing root without duplicate glow, auxiliary editor selection, XML
round trips/missing fields, Apply/Reset/discard, finite/clamped brightness, 1x/2x opacity and original-pixel
preservation, zero-work suppression and exact per-glyph compositing for overlapping fallback halos.
