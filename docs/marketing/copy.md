# Neon Glow copy pack

**Unpublished drafts — 2026-10-04.** Resolve destinations through [campaign.md](campaign.md); do not ship
placeholders. No public listing, approved demo, price, Pro package or trial has been verified for this campaign.
Choose exactly one CTA variant per asset after its destination gate passes.

## Marketplace overview draft

Use the existing product name **Neon Glow**. The opening line is functional rather than a slogan so the
listing preview explains the product. Review against the release before pasting into the listing editor.

### Short summary

Configurable neon glow for JetBrains IDEs. Keep your theme, add some light.

### Description

Add configurable text and icon glow to your current JetBrains IDE theme. Neon Glow also includes three optional
SynthWave-inspired dark themes with matching editor, console and terminal palettes and recoloured standard icons.

- **Neon Glow (Classic):** the classic deep-purple, neon-inspired palette.
- **Neon Glow Midnight:** deeper, quieter surfaces.
- **Neon Glow Accessible:** brighter text alternatives for a readable flat appearance. Disable glow separately
  for the flat experience; the name does not mean certified accessibility.

Choose your theme in **Settings | Appearance & Behavior | Appearance**. Then configure effects separately in
**Settings | Appearance & Behavior | Neon Glow**: enable editor text, UI text and standard icon glow independently,
tune their strengths, or start with Classic, Neon, Focus or Accessible glow presets. Selecting a preset fills the
draft controls immediately; **Apply** or **OK** updates the live IDE without restarting. Theme selection does not replace your glow preferences.

Keep your current IDE theme if you prefer: optional **SynthWave '84-style text** adapts eligible vivid colours on
dark backgrounds with pale tinted cores and layered neon, while preserving five upstream colour rules. Neutral,
muted and low-contrast text keeps its core and follows **Regular text glow**. Eligible colours on light backgrounds
keep their original cores with layered halos; unknown backgrounds use regular glow. Icons are unchanged.
This is an adaptation, not pixel-identical CSS rendering, contrast certification or an endorsed official port.

Glow is static and respects Power Save mode. Performance mode limits new mask work, but does not eliminate
overhead or guarantee total repaint time. The isolated draft preview is not proof of live IDE rendering.

**Compatibility evidence:** Neon Glow passes 177 tests on IntelliJ IDEA 2025.3.6.1. The pre-rename implementation
passed 176 tests on 2026.1.5 and 2026.2.3; those test suites were not rerun for the rename. Plugin Verifier reports
Compatible for the renamed artifact on 2025.3.6.1, 2026.1, 2026.1.5 and 2026.2.3. This is binary/automated evidence,
not complete visual certification. The plugin declares a 2025.3 minimum and no upper bound; untested future
releases and other IDE products are not guaranteed.

**Rendering limits:** glow remains experimental. OS-native menu/title text, browser-rendered panels, custom
shape-painted icons and terminal paths outside Swing text painting are not universally covered. Some uncached
halos can appear only on later natural repaints in performance mode. Three experimental editor-fallback API
usages and outstanding visual/display-scale checks are documented in the quality checklist.

Inspired by Robb Owen's SynthWave '84. Upstream MIT notices are retained alongside this project's Apache-2.0
licence. No endorsement by Robb Owen or JetBrains is implied.

Owner publication insertions, **not yet public links**:

- Installation and demo: `[DEMO_GUIDE_URL]`.
- Feedback/support: `[FEEDBACK_URL]`.
- Versioned quality evidence: owner-approved public link to `docs/theme-quality.md` for the advertised release.

Use Marketplace's Media section for reviewed captures. Do not add a paid-feature table, price or trial badge.

## Founder announcement

**Title:** Your IDE. After dark.

I've been building Neon Glow for IntelliJ IDEA: three SynthWave-inspired dark themes, matching editor,
console and terminal palettes, and optional glow for code, UI text and standard icons.

The part I most want to show is control. You can tune each glow target independently, choose Midnight with
Focus for editor-only glow, or select Accessible and explicitly switch effects off. Themes and glow settings
are separate—nothing automatically changes your font or layout.

The attached demo shows the same workspace with settings applied live, not just the draft preview. Glow is
experimental: native/browser-rendered surfaces and custom shape-only icons are outside universal coverage,
and performance mode is a best-effort work limit, not a zero-overhead claim.

**Insert one closing, only after its gate passes:**

- Before listing: **See the demo and installation guide:** `[DEMO_GUIDE_URL]`.
- Verified listing: **Install Neon Glow:** `[LISTING_URL]`.

I'm the author. If you try it, which looks better to you: Classic glow or Midnight with Focus? I'd also welcome
specific reports of hard-to-read selections, installation friction or rendering problems at `[FEEDBACK_URL]`.

Publication condition: attach the authentic captioned demo, replace links and obtain owner/channel approval.
Do not say “the attached demo” unless it is actually attached and plays.

## Installation guide draft

### 1. Get the plugin

**Public Marketplace installation is pending verification.** Do not claim the plugin is searchable until
`[LISTING_URL]` is checked. Once verified:

1. Open IntelliJ IDEA **Settings | Plugins | Marketplace** and search for **Neon Glow**.
2. Check the listing's vendor **Jakub Chrzanowski**, plugin ID `info.chrzanowski.neonglow`, compatible
   version and linked installation destination before installing.
3. Install and follow the IDE's prompts, including a restart if requested. Restart-free *settings changes*
   do not imply restart-free plugin installation.

Before publication, only provide a local-install route when a trusted owner-approved release archive is
actually available. Use **Settings | Plugins | gear menu | Install Plugin from Disk**, select the plugin ZIP,
and follow the IDE's prompts. No public binary/download URL is established here; do not substitute an arbitrary
third-party archive. Developers can follow the repository's build instructions to produce their own archive.

### 2. Keep or choose your theme

Keep your current IDE theme, or open **Settings | Appearance & Behavior | Appearance** and choose **Neon Glow** (Classic),
**Neon Glow Midnight** or **Neon Glow Accessible**, then Apply. Bundled themes load their paired editor scheme;
glow preferences remain independent. You can use the themes with glow disabled.

### 3. Choose and apply glow

For SynthWave-style text with your current theme, open **Settings | Appearance & Behavior | Neon Glow**, enable
**Enable glow** and **Editor text**, expand **Fine-Tune Glow**, check **SynthWave '84-style text**, then **Apply** or
**OK**. Recommended: **45% brightness**, **100% intensity**, **6 px radius**. No preset is needed; existing settings
and presets are unchanged.

Alternatively, choose a glow preset and customize before Apply/OK. Selecting a preset fills only the draft controls
immediately; Reset discards unapplied changes.

| Starting point | Theme selected separately | Glow preset | Expected effect |
|---|---|---|---|
| Classic neon | Neon Glow (Classic) | Classic | Upstream-inspired editor text, restrained UI and icon strengths |
| Quieter focus | Neon Glow Midnight | Focus | Editor-only glow; UI text/icons off; performance mode on |
| Flat option | Neon Glow Accessible | Accessible | Master glow off; selecting the theme alone does not turn glow off |

Use the **Editor text**, **UI text** and **Icons** switches and their strength controls independently. To
remove all effects, uncheck **Enable glow** and Apply, or toggle **View | Appearance | Neon Glow**.
The switch preserves target choices for later. Applied glow settings do not need an IDE restart.

Adaptive text needs a known dark background (luminance <=0.12), saturation >=0.35, value >=0.5 and original flat
contrast >=3:1. It is colour-based, so vivid comments may qualify; not every token changes. Historical exact
upstream styling is not contrast certification, especially hotpink. No preset automatically chooses your IDE theme.

### 4. If the effect is missing or too strong

- Check master enablement, target switches, strengths and brightness; zero strength/brightness restores original
  rendering. Check Power Save mode, which suppresses live glow.
- Apply settings before inspecting the actual workspace. **Show draft preview** is isolated and ignores live
  Power Save/performance limits; it can look different from the live IDE.
- Reduce UI/icon strengths or use Focus. Try performance mode for bounded new-mask work; it may delay uncached
  halos and is not a total repaint-time guarantee. Switch glow off if it affects readability or responsiveness.
- Native/browser/custom-painted exclusions are expected, not proof of a broken installation. Terminal palette
  support is separate from terminal glow coverage.
- Use **View | Appearance | Neon Glow Tools | Reset Neon Glow Caches** if a halo appears stale.
- For a report, use **Copy Neon Glow Diagnostics**, review clipboard text and submit voluntarily to the verified
  `[FEEDBACK_URL]`. Avoid project code, paths, credentials and personal data in added screenshots or text.

## FAQ draft

**Which IDE versions have evidence?**
The quality checklist records 177 passing tests on IntelliJ IDEA 2025.3.6.1 for Neon Glow, and historical pre-rename
results of 176 tests on 2026.1.5 and 2026.2.3. Plugin Verifier passed for the renamed artifact on 2025.3.6.1, 2026.1,
2026.1.5 and 2026.2.3. The minimum platform is 2025.3, with no upper installation
bound. That does not certify every newer build, other JetBrains products or all visual/display-scale states.

**Does it glow everywhere?**
No. It targets Swing text drawing and standard raster/SVG icon image painting. Native menus/title bars,
browser-rendered panels, shape-only custom icons and non-Swing terminal paths are outside universal coverage.
Large images are excluded. Graphics interception enables styled text cores; editor fallback paints halos only.

**Is glow required? Does Accessible automatically disable it?**
No and no. The themes work without effects. Theme selection does not change existing glow preferences; use
the Accessible *glow preset* or disable the master switch and Apply for a genuinely flat configuration.
Accessible is a theme name, not a claim of certified accessibility.

**Are there performance costs?**
Yes. Cached masks and optional performance mode constrain work, but they are not zero overhead. Performance
mode admits at most 24 new glyph masks and 4 icon masks per graphics paint tree with a shared 2 ms admission
deadline; one admitted operation or total repaint can take longer. Original text/icons remain visible.
The effect is static, with no animation or forced warming timer, and live glow stops in Power Save mode.

**Why does the draft preview differ from my IDE?**
It uses isolated caches and ignores live Power Save and cold-work limits to show the intended appearance.
Apply, then inspect real editor and UI surfaces; the preview alone cannot verify live rendering.

**Is this the same as the VS Code extension?**
It is inspired by Robb Owen's SynthWave '84, not affiliated with or endorsed by its author. Swing Gaussian
blurs are not pixel-identical CSS shadows. The optional text style preserves five upstream rules on known dark
backgrounds and adapts other eligible vivid colours with same-hue neon. Light backgrounds keep original cores with
layered halos for eligible colours; unknown backgrounds use regular glow. UI backdrop detection is best-effort,
and editor fallback cannot replace cores. Icons are
unchanged. Upstream copyright and MIT permission notices are preserved.

**Is there a Pro plan, price or trial?**
No commercial package or licensing is implemented in the inspected descriptor. This campaign makes no paid
offer. Do not interpret earlier monetization ideas as current pricing or availability.

**Does this campaign add tracking?**
No in-plugin telemetry, fingerprints or advertising pixels are added. Evaluation uses channel reports,
available aggregate Marketplace analytics and voluntary feedback, not measured activation or retention.

## Before any copy leaves this repository

- Resolve links and choose one CTA; delete internal instructions/placeholders from the approved public variant.
- Recheck claims against the advertised release and its quality evidence; disclose experimental rendering.
- Attach authentic media where referenced; do not use mockups, draft preview or borrowed upstream screenshots
  as proof of live IntelliJ IDEA rendering.
- Confirm local promotion rules, author/sponsor disclosure, privacy and explicit owner authorization.
- Never publish an availability, performance, accessibility or compatibility claim broader than the evidence.