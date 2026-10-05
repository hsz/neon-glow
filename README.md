# Neon Glow

Configurable neon glow for JetBrains IDEs. Keep your theme, add some light.

Editors, tool windows, tabs, menus, popups and dialogs can get blurred text/icon halos. An optional SynthWave '84
text style adds layered halos to eligible coloured text: tinted cores on dark backgrounds, original cores on light
backgrounds, without changing your IDE theme. Bundled SynthWave-inspired themes also coordinate editor, console
and terminal palettes.

> The declarative themes work without glow. Glow remains an experimental Java2D enhancement:
> [`docs/glow-investigation.md`](docs/glow-investigation.md) explains the approach
> (global Swing text/image interception, editor highlighter fallback, bounded halo caches, HiDPI-exact blits)
> and records the measured paint cost.

## What it does

- Bundles **Neon Glow** (Classic), **Neon Glow Midnight** and **Neon Glow Accessible**, with matching editor schemes,
  familiar recoloured SVG icons, dark-purple surfaces, clear keyboard focus and distinct selection states.
  Amber warnings remain distinguishable from mint success/additions. Accessible colours are tested without glow.
- Offers **Classic**, **Neon**, **Focus** and **Accessible** glow presets, independent target strengths and an
  isolated optional draft preview. Existing saved settings are not silently replaced by a preset.
- Optional **Performance mode** bounds new masks per paint; clipping and raster-size guards avoid unnecessary
  or excessive allocations. Recovery tools reset caches and copy a project-content-free diagnostic report.
- Paints a Gaussian-blurred copy of text throughout Swing IDE windows, including console/diff editors and the
  welcome screen. By default the halo uses the actual text drawing colour, including coloured tree/table cells
  and styled text. Optional SynthWave '84 style preserves five upstream colour rules on known dark backgrounds
  and adapts other eligible vivid colours; neutral, muted and low-contrast text retains its core and same-colour glow.
- Adds multicolour halos to standard SVG and raster icons, preserving transparency and crisp original pixels.
  Image halos use a separate LRU cache capped at 256 entries / 16 MiB and follow the same glow settings; the
  optional text style never maps icon colours or gives icons layered text glow.
- Static: no animation, no ticker. It only paints when the UI repaints, plus a small inflated repaint around
  edited lines so the halo never leaves stale fragments.
- Masks are cached by glyph / font / colour / display scale / radius in an LRU atlas
  (1 500 entries / 32 MiB). Cache eviction or parameter changes may require rendering again.
- Follows colour scheme and theme changes, and paints nothing while Power Save mode is on.
- Covers existing and newly opened Swing windows; removes its painting hooks and restores window roots on unload.
- OS-rendered menu bars/title bars, browser/terminal surfaces that bypass Swing text drawing, and bitmap glyphs
  are outside this Java2D effect.
- Custom icons painted exclusively with vector primitives are not intercepted. Images larger than 128 logical
  pixels or 512 device pixels per side are excluded to avoid glowing backgrounds and offscreen UI buffers.

## Quick start

1. Keep your current IDE theme. Open `Settings | Appearance & Behavior | Neon Glow` and enable **Enable glow**
   and **Editor text**.
2. Expand **Fine-Tune Glow**, enable **SynthWave '84-style text**, then **Apply** or **OK**. No preset is needed.
   For the recommended look, use **45% brightness**, **100% intensity** and **6 px radius**.
3. Optional: choose **Neon Glow Midnight** or **Neon Glow** under `Settings | Appearance & Behavior | Appearance`,
   or try **Focus** (editor-only) or **Classic** glow presets. Selecting a preset fills the draft controls immediately;
   Apply/OK commits them. Every slider shows its numeric value.

Use **Accessible** as a glow preset, or turn off **Enable glow**, for flat rendering. The similarly named theme
does not disable glow automatically. `View | Appearance | Neon Glow` is the quick on/off switch.
Presets and edits remain drafts until Apply; Reset discards them. Existing preferences are never replaced just
by opening this page, and untouched fractional values remain precise even though sliders use whole ticks.

To keep ordinary text crisp while coloured text gets layered neon, uncheck **Regular text glow**, leave
**SynthWave '84-style text** enabled, then **Apply** or **OK**. This works in both enabled text targets;
icons remain independent. With SynthWave styling off, disabling Regular text glow disables all text glow.

## Settings

Choose the IDE theme under `Settings | Appearance & Behavior | Appearance`; its paired editor scheme loads with it.
Themes do not enable glow or overwrite glow preferences. Choose the Accessible glow preset (or disable glow) for
an entirely flat experience; choosing the Accessible **theme** alone does not override an existing enabled effect.

`Settings | Appearance & Behavior | Neon Glow`

| Setting | Default | Meaning |
|---|---|---|
| Enable glow | on | Also toggled by `View | Appearance | Neon Glow`. |
| Editor text | on | Code, console/diff text and editor gutters. |
| UI text | on | Tool windows, tabs, menus, popups, dialogs and other Swing UI text. |
| Regular text glow | on | Same-colour halos for text without an eligible SynthWave-style rule, in both enabled text targets. Off leaves original cores with no halos; eligible layered text and icons are unaffected. With style off, this controls all text glow. |
| Icons | on | Standard SVG and raster icons, independently of text. |
| Editor / UI / Icon strength | 100 % each | Separate 0–100% halo multipliers; zero also disables styled-core replacement for that target. |
| Performance mode | off | At most 24 new glyph masks and 4 new icon masks per graphics paint tree, with a shared 2 ms admission deadline. Cached masks remain available. |
| SynthWave '84-style text | off | Opt-in layered halos for eligible coloured text. Dark backgrounds get tinted cores; light backgrounds keep original cores with coloured halos, even when Regular text glow is off. Unknown backgrounds use regular glow. Icons are unchanged. |
| Brightness | 100 % | Halo opacity, from 0–100%, applied after intensity. In style mode it controls variable coloured layers, including adaptive layers, not fixed upstream pink/dark layers. Zero restores all original rendering, including text cores. |
| Radius | 6 px | Same-colour halo reach in user-space pixels. In style mode, 6 px uses reference layer blur sizes; other values scale all layers proportionally. |
| Intensity | 300 % | Coverage multiplier before brightness; thin fonts need more than bold ones. |

Settings are stored in `neon-glow.xml`. Target choices are independent; turning the master switch off and
back on preserves them. Changes take effect on **Apply**, without an IDE restart.

Choosing a preset changes the draft immediately, not the live IDE. Classic uses upstream-inspired editor glow and restrained UI
glow; Neon increases saturation; Focus lights only editor text and enables performance mode; Accessible disables
glow. Customize any preset, use the optional **Show draft preview**, then Apply/OK or Reset. Preview ignores live
Power Save and cold-work limits so you can inspect the intended appearance; the actual IDE always respects
Power Save. No theme, font or layout is automatically changed.

`View | Appearance | Neon Glow Tools` and Find Action expose **Neon Glow Settings**, **Reset Neon Glow Caches**
and **Copy Neon Glow Diagnostics**. Diagnostics include IDE/runtime versions, rendering status, settings, scheme
name and cache counts—not project paths or document contents. Review the clipboard text before sharing.

Performance mode is a best-effort cold-work limiter, **not** a total repaint-time guarantee: one mask can exceed
the admission deadline. Some uncached halos can remain absent until a later natural repaint; there are no
background workers or forced repaint timers. Original text/icons always remain visible. Oversized glyph halos
are skipped even outside performance mode. See [the validation checklist](docs/theme-quality.md) for coverage
and remaining visual checks, and [the usability trial](docs/usability-checklist.md) for the human validation gate.

### SynthWave '84 options

The [original extension](https://marketplace.visualstudio.com/items?itemName=RobbOwen.synthwave-vscode) exposes
brightness (0–1, default 0.45) and editor glow disablement, plus commands to install/remove Neon Dreams styling.
For an upstream-inspired look, enable **SynthWave '84-style text** and use **45% brightness**, **100% intensity**
and the default **6 px radius**; no preset or theme change is required. The five exact upstream rules below remain
on known dark backgrounds. They are historical styling, not contrast certification—especially the hotpink core.
The Classic/Midnight schemes coordinate their token colours with those rules; Accessible intentionally uses brighter
alternatives where needed. All shadows are centred; blur sizes below are at radius 6 px.

| Source RGB | Foreground core | Layered text halo |
|---|---|---|
| `#36f9f6` | `#fdfdfd` | 2 px dark base; 3, 5 and 8 px cyan `#03edf9`. |
| `#fede5d` | `#f4eee4` | 2 px dark base; 8 and 2 px orange `#f39f05`. |
| `#fe4450` | `#fff5f6` | 2 px dark base; 10, 5 and 25 px red `#fc1f2c`. |
| `#ff7edb` | `#f92aad` | 2 px dark base; 5 px `#dc078e` at alpha 0.2; 10 px white at alpha 0.2. |
| `#72f1b8` | `#72f1b8` (unchanged) | 2 px dark base; 10 px `#257c55`; 35 px `#212724`. |

Near/nonexact vivid colours get pale same-hue-tinted cores, a 2 px dark base and 3/7/12 px saturated same-hue neon
layers when saturation is at least 0.35, value at least 0.5, original flat contrast at least 3:1 and background
luminance at most 0.12. Neutral, muted and low-contrast colours keep original cores and same-colour glow. Eligibility
is colour-based, not semantic: vivid comments can qualify. Icons are unchanged.

Same-colour glow in these cases follows **Regular text glow**; turn it off to retain original text without halos.
The setting defaults to on, including when loading older preferences, preserving the existing appearance.

On light backgrounds (luminance above 0.12), saturated text with original flat contrast at least 3:1 keeps its
original core and gets three same-hue coloured halos at 3/7/12 px. Dark syntax colours qualify too: there is no
minimum source brightness here. Halo colours are darkened for visibility against the backdrop, without white
cores or a fixed dark outline. These layered halos remain enabled when **Regular text glow** is off. Neutral,
muted and low-contrast text still follows that switch; eligibility is colour-based, not a syntax-role guarantee.

Unknown backgrounds use regular glow. On darker intermediate backgrounds, exact mapped cores below 3:1 flat
contrast retain the source core with upstream shadow layers. UI backdrop detection is best-effort, using component
opacity and known solid fills; arbitrary custom painting is not guaranteed. Layered-mask caches distinguish
resolved rules to avoid light/dark mixing.

Original text alpha is preserved. Brightness changes variable coloured layers, including adaptive neon; upstream
pink's alpha-0.2 layers and dark bases remain fixed while active. Intensity multiplies blurred coverage, and radius
scales every text layer by `radius / 6`. Swing Gaussian blurs are not pixel-exact CSS shadows.

Brightness zero, the master switch off, a target off, target strength zero or Power Save mode restores original
rendering, including foreground cores, for the affected targets. Uncheck **Editor text** to leave UI text and icon glow enabled.
Without graphics interception the editor under-glow fallback uses the scheme backdrop for upstream/adaptive layered
halos or same-colour fallback; it cannot replace foreground cores. Core replacement requires graphics interception.

The default same-colour mode and 100% brightness preserve this plugin's previous look. All options work with
your current theme, with no restart or changes to IDE installation files. Saved settings and presets are unchanged. See
[`docs/synthwave-options.md`](docs/synthwave-options.md) for the source-backed comparison and rendering differences.

## Development

Use `neon-glow` as the repository name; the Gradle project name is `neon-glow`. The plugin ID, Gradle group and
Kotlin namespace are `info.chrzanowski.neonglow`. The pre-release rename changes plugin/theme IDs and settings storage. Uninstall any
older development build before installing Neon Glow; previous development settings are not migrated.

```shell
./gradlew test       # unit + light platform tests (incl. GlowPaintBenchmarkTest, which prints paint costs with -i)
./gradlew runIde     # sandbox IDE with -Dide.neon.glow.debug=true
./gradlew buildPlugin
./gradlew verifyPlugin   # 2025.3.6.1, 2026.1, 2026.1.5 and 2026.2.3
./gradlew test -PplatformVersion=2026.1.5
./gradlew test -PplatformVersion=2026.2.3
./gradlew runIde -PplatformVersion=2026.1.5
```

With `-Dide.neon.glow.debug=true` the plugin logs a `[NeonGlow] paints=… paint avg=…µs …` line to `idea.log` every
10 seconds with glow cost, glyphs per text run (or editor fallback paint), layout fallbacks and atlas statistics.

The plugin supports IntelliJ Platform **2025.3 and newer, including 2026.1 and 2026.2** (`since-build=253`,
no upper bound). Release artifacts are built against the default **2025.3.6.1** API/Java 21 baseline to retain
backward compatibility. Use `platformVersion` for additional test/sandbox targets; build release packages without
that override. Building/testing against 2026.2 requires a Java 25 toolchain; Gradle selects the target's runtime.

Neon Glow passes all **177 tests** on 2025.3.6.1, including the new identity regression check. Its baseline-built
artifact passes Plugin Verifier on all four versions above. Pre-rename verification recorded 176 passing tests
on 2025.3.6.1, 2026.1.5 and 2026.2.3; the newer-platform test suites were not rerun for the rename.
Tests use platform APIs without unrelated bundled plugin jars on their shared test classpath; this avoids
obfuscated-class collisions in 2026.2, without disabling tests
or changing the production plugin. Plugin Verifier checks the baseline-built artifact against the four versions
above. An open upper bound allows future installation, not a guarantee about untested releases or IDE products.
The three experimental editor-fallback API usages, a 2026.2 preset-renderer deprecation and manual visual checks are documented in
[`docs/theme-quality.md`](docs/theme-quality.md).

The commit sequence was reconstructed from an existing AI-assisted implementation. Its assigned dates are
not the original development timeline; see [the history note](docs/history-reconstruction.md).

## License

[Apache License 2.0](LICENSE)

SynthWave '84 palette/style adaptations retain Robb Owen's MIT copyright and permission notice in
[`LICENSE.upstream`](LICENSE.upstream), also packaged as `META-INF/LICENSE.synthwave84`.
