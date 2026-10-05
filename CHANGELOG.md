<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Neon Glow Changelog

## Unreleased

### Changed

- Set fresh-install defaults to regular text glow off, SynthWave-style text on, 50% brightness, 75% editor
  strength, 50% UI/icon strength, 6 px radius and 200% intensity. Retain explicit saved choices and named presets;
  missing settings and non-finite numeric values use the new defaults.
- Rename the project to Neon Glow before publication, including plugin ID and namespace
  `info.chrzanowski.neonglow`, bundled themes, actions, settings storage and the `neon-glow` build artifact.
  Retain SynthWave '84 inspiration and upstream licence notices. Older development settings are not migrated.
- Extend the existing opt-in SynthWave '84-style text to regular IDE themes: eligible vivid colours on dark
  backgrounds get pale same-hue cores and layered neon. Preserve the five upstream rules on known dark backgrounds,
  original alpha, saved settings and presets. No theme change or preset is required.
- Keep presets, glow switches, brightness and performance mode visible; put strengths, mapped text style,
  radius and intensity in a collapsed Fine-Tune Glow section. Give every slider a numeric readout and accessible name.
- Allow installation on IntelliJ 2026.1 and newer by removing the 253.* upper bound, retaining the 2025.3
  API/Java 21 release baseline. Verify the packaged plugin on 2025.3.6.1, 2026.1, 2026.1.5 and 2026.2.3.
- Add a `platformVersion` test/sandbox override and keep unrelated bundled plugin jars off the shared test
  classpath to avoid obfuscated-class collisions in 2026.2. All tests and assertions remain enabled.

### Removed

- Show draft preview and its isolated sample renderer. Apply/OK still commits draft settings; Reset discards them.

### Fixed

- Restore SynthWave-style glow for eligible coloured syntax on light backgrounds even with Regular text glow off.
  Keep original readable foregrounds and use background-aware coloured layers, including for dark syntax colours;
  neutral/muted/low-contrast text remains independently controlled. Shared policy covers graphics and editor fallback.
- Selecting a glow preset immediately fills the draft controls; removed the redundant Use Preset button.
  Apply/OK remains the only live confirmation. Opening, Reset and reopening no longer imply Classic is selected.
- Keep original cores and same-colour glow for neutral, muted and low-contrast text when SynthWave '84 style
  is enabled, rather than removing unmatched halos. Unknown backgrounds retain this rendering even for exact
  upstream colours; light backgrounds now support eligible layered halos without core replacement.
  Darker intermediate backgrounds keep source cores when mapped cores fall below 3:1 contrast.
- Resolve layered-mask cache entries by backdrop-aware rule to avoid light/dark mixing. UI backdrop detection
  uses component opacity and known solid fills on a best-effort basis, not a universal custom-painter guarantee.
- Preserve untouched fractional settings during Apply instead of rounding every saved value to slider ticks.
- Repaint all affected soft-wrapped visual lines, including the old tail after deletion, to clear glow bleed.
- Remove the Neon Glow root wrapper when nested in another layered pane on unload, preserving foreign panes,
  popup layers, bounds and component order.

### Added

- Regular text glow switch: independently disable same-colour text halos while retaining eligible SynthWave-style
  layered text and icon glow. Applies to both enabled text targets and editor fallback; persists with an
  initially all-on default (now off) and follows the existing Apply/Reset workflow. With text style off, it controls all text glow.
- Complete Classic, Midnight and Accessible IDE themes with paired syntax, console and terminal schemes,
  semantic SVG palette patching, consistent dark-purple surfaces and readable interaction states.
- Classic, Neon, Focus and Accessible glow presets and independent editor/UI/icon strengths,
  preserving existing settings and transactional Apply/Reset behavior.
- Optional bounded cold-mask work in performance mode, fully clipped painting exclusion and oversized-raster
  protection. Cache invalidation and settings updates are serialized on the UI thread.
- Thread-safe editor registration and complete unload cleanup of highlighters, document listeners and editor
  ownership, including editors that remain open when the application service is disposed.
- Settings, cache-reset and project-content-free diagnostic-copy actions under Neon Glow Tools; upstream MIT
  notices included in the distributable plugin.
- Optional SynthWave '84-style text colour mapping and layered glow, disabled by default to preserve same-colour
  rendering when first introduced; now enabled by default. Beyond the five upstream rules, near/nonexact colours
  qualify at saturation >=0.35, value >=0.5,
  original flat contrast >=3:1 and background luminance <=0.12. Adaptive layers use a 2 px dark base and 3/7/12 px
  same-hue neon at radius 6 px; icons are unchanged. Eligibility is colour-based, so vivid comments may qualify.
  Settings persist and apply/reset with the page's draft. Radius scales all layers; intensity remains a coverage
  multiplier. Brightness controls adaptive/other variable layers, not fixed dark bases or upstream pink layers.
  Recommended brightness 45%, intensity 100% and radius 6 px are upstream-inspired, not pixel-exact CSS.
  Brightness zero, disabled targets/master, target strength zero and Power Save restore original rendering,
  including cores. The editor fallback uses the scheme backdrop and layered halos but cannot replace cores.
  Exact colours, historical hotpink contrast caveats and rendering conditions are documented in `docs/synthwave-options.md`.
- Independent glow targets for editor text (including auxiliary editors and gutters), other UI text and icons,
  persisted with backward-compatible all-on defaults and applied without restarting the IDE.
- SynthWave '84-inspired brightness control (0–100% halo opacity), separate from radius/intensity; 100% preserves
  the previous appearance and 45% matches upstream's opacity setting. Source comparison in `docs/synthwave-options.md`.
- IDE-wide glow for standard SVG and raster icons, retaining their colours and transparency, with a bounded
  image-halo cache and shared glow settings, HiDPI and Power Save support.
- IDE-wide text glow for Swing tool windows, tabs, menus, popups, dialogs, welcome screens and auxiliary editors,
  using their actual foreground colours and the same enable/radius/intensity settings.
- Proof of concept: a real Gaussian-blurred glow behind every glyph in main editors, in the token's own colour
  scheme foreground, painted under the text via a document-wide `CustomHighlighterRenderer`.
- Per-glyph glow mask atlas (LRU, HiDPI device-pixel masks) so typing and scrolling never re-blur.
- Bleed-safe repaints around edited lines; colour scheme / theme change and Power Save awareness.
- `Settings | Appearance & Behavior | Neon Glow` (enable, radius, intensity) and `View | Appearance | Neon Glow`.
- Debug statistics in `idea.log` with `-Dide.neon.glow.debug=true`; measurements in `docs/glow-investigation.md`.
