# SynthWave quality and release checklist

## Design contract

- Themes and effects are separate: switching a theme never overwrites saved glow preferences, fonts, zoom,
  keymaps, window layouts or background-image preferences.
- Classic coordinates exact upstream text RGBs with the optional mapped glow. Midnight uses darker surfaces;
  Accessible uses brighter text where necessary. All themes must remain usable without glow.
- Use dark-purple surface hierarchy, crisp light labels, cyan keyboard focus, pink active-tab accents, amber
  warnings and mint success/additions. Preserve recognisable JetBrains icon silhouettes and state cues.
- Prefer supported static theme resources over global replacement painters. No animation, scanlines, native
  transparency hacks, binary patching or timers. Gradients/artwork and custom-vector icon effects are deliberately
  not prerequisites for the usable theme; this release does not implement them.

## New features and safety boundaries

- Four glow presets and independent target strengths make code brighter than dense UI controls if desired.
  Presets are suggestions, not locked modes; old XML loads retain the prior defaults.
- Draft preview uses its own bounded caches and retains the finished raster instead of reallocating on every
  repaint. It ignores actual Power Save and cold-work limits to show the intended appearance; real IDE painting
  obeys both. Target/strength-only settings changes preserve warm live masks.
- Performance mode shares 24 glyph / 4 image cold-mask admissions and a 2 ms deadline across graphics copies.
  Cache hits do not consume the budget. The deadline is checked **before** work: a single render or total IDE
  repaint can take longer. Skipped halos await natural repaints; there is no artificial warming loop.
- Fully clipped glyphs/icons avoid mask generation. Glyph blur allocations are rejected above one million
  padded pixels or 2048 pixels per side. Text and icon originals are never dropped by these safeguards.
- Both atlases are bounded; colour/settings changes invalidate them on the EDT. Native menu/title text, JCEF,
  custom shape-painted icons and non-Swing terminal rendering are outside universal glow coverage.
- Editor registration uses a snapshot-safe concurrent set. Unload detaches highlighters, document listeners and
  editor user data before releasing the painting hook and caches; editors need not be closed first.
- Copy Diagnostics records versions, preferences, scheme name, cache counters and hook status. It collects no
  paths or document text and makes no network requests; users should review the report before sharing.

## Automated checks

- Resource parsing, named-colour references, provider/scheme linkage and platform scheme loading.
- Palette contrast against editor/chrome/control surfaces and focused/unfocused selections; semantic diagnostic
  distinctions and representative syntax/console/terminal attributes.
- Legacy XML, normalization, fresh preset instances, transactional draft settings and isolated preview.
- Original rendering on disabled/zero-strength/Power Save paths, mapped cores, all drawing overloads,
  target scopes/copies, clipped content, budget sharing, warm-cache use and oversized glyph transforms.
- Existing editor lifecycle, root restoration, mutable icons and 1×/2× raster tests.
- Run `./gradlew test buildPlugin`, the IDE build, and Plugin Verifier against the declared target.

## Manual release matrix

Check each theme with glow off, Classic/Neon/Focus presets, maximum settings, Power Save and performance mode:

1. Main toolbar/status bar, selected/unselected editor tabs, tool-window buttons and headers.
2. Project tree, lists/tables: focused/unfocused selections, hover, disabled rows and VCS statuses.
3. Menus, Settings, dialogs, fields, checkboxes, buttons, sliders, keyboard focus and validation errors.
4. Search Everywhere, completion, quick documentation, notifications and tooltips.
5. Java/Kotlin plus installed language plugins, gutter/folding, breadcrumbs, sticky lines, inlays/code vision,
   debugger execution/breakpoints, inspections, diffs/merge views, consoles and both terminal engines.
6. Welcome/empty editor, multiple project windows, popups, font/zoom changes, 100/125/150/200% display scale,
   mixed-scale monitor moves, theme/scheme changes and plugin disable/unload.

Screenshot comparison and repaint/allocation measurements on target runtimes are still required before claiming
cross-product visual certification. Flat colour contrast checks are useful evidence, not complete accessibility
certification. Third-party custom painters and icons may use their own colours.

## Sources and licensing

- [IntelliJ theme structure](https://plugins.jetbrains.com/docs/intellij/theme-structure.html)
- [Theme customization](https://plugins.jetbrains.com/docs/intellij/themes-customize.html)
- [Islands guidance](https://plugins.jetbrains.com/docs/intellij/supporting-islands-theme.html)
- [SynthWave '84 upstream](https://github.com/robb0wen/synthwave-vscode)
- Existing glow mappings and options: [source comparison](synthwave-options.md).

Theme keys must be checked against the installed target's resources/schema; live SDK pages are not compatibility
proof for older builds. Preserve `LICENSE.upstream` for adapted upstream material, including packaged notices.

## Implementation review

### Standards

The independent standards review identified editor-registry threading as a defensive gap. The registry is now
snapshot-safe across lifecycle callbacks; cache mutation remains serialized on the EDT. Pixel snapshot keys are
intentional for mutable/animated icons, and root wrapping remains an experimental, reversible coverage mechanism.
These existing rendering tradeoffs were retained rather than replaced with identity-only caching or a glass pane
that cannot draw underneath the original text.

### Spec

The independent spec review found the selected theme, settings, preview, rendering-safety, diagnostics and
packaging requirements implemented. Decorative artwork/gradient painters and exhaustive cross-product visual
certification remain explicitly outside this release. The review is static evidence, not a performance or
accessibility certification; the experimental fallback and manual matrix above still apply.

Review totals: one actionable Standards threading concern (addressed); no selected-feature Spec gaps. A subsequent
unload reproduction exposed surviving editor highlighters/listeners; complete detachment is now regression-tested.

## Validation snapshot (2026-10-04)

- `./gradlew test buildPlugin verifyPlugin`: successful; **141 tests, zero failures, zero skipped**. Earlier
  clipped-paint and unload reproducers failed before their fixes; the concurrent snapshot test caught and now
  covers Kotlin's size/iterator shortcut racing with registry updates.
- IntelliJ IDE build and whitespace checks passed. Plugin Verifier against **IU-253.33813.55 / 2025.3.6.1** reports
  **Compatible**, no internal-API usages, and three experimental editor-fallback usages: `CustomHighlighterOrder`,
  `AFTER_BACKGROUND`, and `CustomHighlighterRenderer.getOrder`. This is not verification of every `253.*` build.
- Resource loading tests exercise the installed platform; Accessible declared-text contrast covers 1,553
  foreground/background pairings with a measured minimum of 5.19:1. Inherited/third-party states still need the
  manual matrix, and glow is not used to inflate the flat-colour measurements.
- Sandbox startup completed without errors in the current run. Its persisted UI-text/icon targets were disabled;
  this was a startup smoke test, not a comparison of all themes/effects. The sandbox and its children were stopped.
- The distributable contains all three theme/scheme pairs and the upstream MIT notice. No commits were created.

## 2026.1+ compatibility review (2026-10-04)

### Standards

The second independent source audit found no new significant standards/lifecycle/thread-safety defects.
Concurrent editor snapshots, normalized settings copies and reversible root restoration retain their existing
coverage. These findings do not certify the global painting architecture against every future platform.

### Spec

The second API/correctness audit found no new source migration blockers. The actual installation blocker was
the generated `until-build=253.*`; it is now absent while `since-build=253` remains. `platformVersion` selects
test and sandbox targets, but release packages must use the default 2025.3.6.1 API/Java 21 baseline. All normal
rendering/settings behavior is unchanged. Review totals: zero new source findings on each axis; one compatibility
metadata blocker fixed separately.

### Repeatable checks and results

```shell
./gradlew test buildPlugin verifyPlugin
./gradlew test -PplatformVersion=2026.1.5
./gradlew test -PplatformVersion=2026.2.3
```

| IntelliJ version | Build | Test suite | Baseline artifact Plugin Verifier |
|---|---|---|---|
| 2025.3.6.1 | 253.33813.55 | 141 passed | Compatible |
| 2026.1 | 261.22158.277 | Not run | Compatible |
| 2026.1.5 | 261.27258.48 | 141 passed | Compatible |
| 2026.2.3 | 262.10968.63 | 141 passed | Compatible |

No tests failed or were skipped in the successful full runs. Each verifier target retains only the same three
experimental editor-fallback API usages listed above, with no internal API or binary compatibility findings.
2026.2 uses Java 25; target tests use the runtime selected by Gradle. The release artifact remains Java 21.

The first 2026.2 run exposed a test-launcher issue: automatically adding all unrelated bundled plugin jars to
the shared classloader resolves colliding obfuscated classes and breaks bundled Ultimate startup. The documented
`org.jetbrains.intellij.platform.testIdeBundledPluginsClasspathEnabled=false` option restores the platform-only
test classpath appropriate to this plugin's sole `com.intellij.modules.platform` dependency. A single-test
reproducer failed before this change and passed afterwards; no plugin tests/assertions were disabled or replaced.
The later full run also found a truncated Marketplace plugin-ID cache generated in the new test sandbox.
JSON parsing confirmed it was incomplete; removing only that session-generated file allowed all 141 tests to
pass. User IDE caches were not modified.

The release has no upper installation bound, following
[JetBrains' forward-compatibility guidance](https://platform.jetbrains.com/t/2026-1-is-coming-time-to-check-your-plugin-compatibility/3865).
Release/build versions were confirmed through the
[official product releases API](https://data.services.jetbrains.com/products/releases?code=IIU&type=release).
The [Gradle test-classpath option](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-gradle-properties.html)
does not constrain the production plugin or the verifier's full IDE distributions. Open-ended metadata is not
proof for untested future releases. No new live visual comparison was performed; the manual release matrix
and cross-product/display-scale limitations above remain outstanding.

## Reliability and simplicity audit (2026-10-04)

The subsequent audit assesses the whole current plugin, not only its release diff. Existing rendering,
resource and settings coverage remains useful, but is not evidence that every user will adopt every feature.
No advertising-plan steps were reopened, new effects added, preferences migrated or telemetry introduced.

### Standards

The lifecycle review found a retained plugin-owned root wrapper when another plugin nests it in a layered pane.
The reproduction failed before the fix. Unload now removes our nested wrapper while preserving the foreign
layered pane, original pane identity, component order/layers, bounds and lightweight popup ownership. A separate
test ensures an unrelated replacement root is not overwritten. This verifies layered-pane composition, not
every possible third-party layout or reference held outside the Swing hierarchy.

### Spec

The editor review found that bleed repaint ended after the first visual line of an edited logical line.
Soft-wrapped insertions and deletions both reproduced the problem. Repaints now cover the whole affected
visual-line range and union the pre-edit and post-edit bounds, clearing the former wrapped tail after deletion.
The tests capture real repaint requests and require their inflated bounds to reach the old/new visual tail.

### Settings and first-use path

Two regression tests reproduced fractional-value loss in preview/Apply: integer sliders rounded untouched
saved values. Bindings and preview now preserve raw values unless their displayed slider tick changes.
Presets still deliberately replace only the draft; Apply/Reset/discard semantics and saved defaults are unchanged.

The initial page keeps presets, master/target switches, brightness and performance mode visible. Independent
strengths, mapped style, radius and intensity remain available in collapsed **Fine-Tune Glow**. Every slider
has a localized accessible name and numeric readout. Tests verify initial visibility, readouts after edits,
presets and Reset, precise values and unchanged transactional behavior even for collapsed controls.

Review totals: one actionable Standards lifecycle finding and one actionable Spec repaint finding, both
reproduced and fixed; the parent audit additionally fixed fractional settings and simplified the page.

### Fresh automated evidence

| IntelliJ version | Full test suite | Baseline artifact Plugin Verifier |
|---|---|---|
| 2025.3.6.1 | 149 passed, zero failures/skips | Compatible |
| 2026.1 | Not run | Compatible |
| 2026.1.5 | 149 passed, zero failures/skips | Compatible |
| 2026.2.3 | 149 passed, zero failures/skips | Compatible |

The eight new tests cover precision (two), settings visibility/readouts (two), wrapped-line repaints (two),
and nested/unrelated root restoration (two). No assertions or tests were disabled. The release is built on
the default Java 21/2025.3 baseline; the same three experimental editor-fallback API usages remain.

### Human evidence still required

The [five-developer usability trial](usability-checklist.md) is **not run**. Its setup-time, safe-disable and
comprehension goals are proposed acceptance targets, not observed results. The full live visual matrix,
mixed-display checks and real-work responsiveness observations remain open. Report voluntary follow-up as
small-sample self-report, not measured retention. Until that evidence exists, “users will use it” is unproven.

## SynthWave text glow regression (2026-10-04)

Enabling mapped style previously suppressed every unmatched text colour, including ordinary UI labels and
other editor schemes. Two new tests reproduced missing halos in graphics interception and the editor fallback.
Both paths now use the special layered masks only for the five exact matches; other colours retain same-colour
halos and original text cores. Brightness and independent strengths remain effective for both paths.

The graphics regression compares rendered pixels against same-colour mode for three unmatched colours, editor
and UI scopes, and 1×/2× scales. The editor regression checks ordinary-text pixels, glyph counts and brightness
zero. Existing mapped-core/layer, restoration, icon and numeric-readout tests remain enabled.

All **151 tests** pass with zero failures, errors or skips on **2025.3.6.1**, **2026.1.5** and **2026.2.3**.
IDE compilation and baseline plugin packaging pass. The settings page already has numeric readouts for every
slider: brightness/intensity/strengths in percent and radius in pixels, verified through edits, presets and Reset.
The readouts absent from the supplied screenshot are present in the current implementation; running IDEs need
the rebuilt plugin to display code changes. No new live visual comparison was performed; the manual matrix remains open.

## Preset selection usability fix (2026-10-04)

Selecting a glow preset now fills the draft controls and preview immediately. The extra Use Preset button is
removed; Apply or OK is the only live confirmation. Opening the page, Reset, Apply and reopening show a
neutral “Choose a preset...” prompt instead of implying that saved settings match Classic. No theme, font,
layout or saved settings change just from opening the page. Reset and disposal still discard unapplied presets.

Regression tests first reproduced the inert selector and misleading initial selection. The settings tests now
cover all four immediate selections, numeric readouts, same-preset reselection after customization, exactly one
apply per preset, selecting the already-live preset, and Reset/disposal/reopening isolation.

- All **153 tests** pass on **2025.3.6.1**, with zero failures, errors or skips.
- All **21 settings-page tests** pass on **2026.1.5** and **2026.2.3**; these were focused reruns, not new full-suite results.
- IDE compilation, baseline plugin packaging and whitespace checks pass. No new live visual/user trial or Plugin Verifier run was performed for this settings-only change.

## Theme-adaptive SynthWave text (2026-10-04)

The existing opt-in style now works with ordinary dark IDE themes without changing their scheme, fonts, layout,
saved preferences or presets. Exact upstream colours retain their established layers on dark surfaces; eligible
nonexact vivid colours get pale tinted cores and separate, same-hue neon layers. Neutral, muted and low-contrast
colours retain original cores and same-colour halos. Colour eligibility cannot identify semantic comments.
Light or unknown backgrounds keep original cores and same-colour glow, including exact upstream colours.

Background-aware resolution uses opaque component ancestry, the root content backdrop and recent clipped fills,
and survives target scopes and graphics copies. A real-root regression initially exposed missing background
context for plain Swing labels; the root now supplies its content backdrop. Mixed dark/light editor and UI tests
verify independent decisions and unchanged editor schemes. Background inference is best-effort, not proof of
every custom painter's backdrop. Gradient/unknown fills deliberately disable bright-core adaptation.

Resolved rules are part of the glyph cache key. The editor fallback shares adaptive layered masks using its
scheme backdrop but still cannot replace cores. Existing master/target/strength/brightness/Power Save gates,
clipping, icons, draft isolation and cache bounds remain effective. Eleven new tests cover hue families, near
matches, restrained colours, sampled adaptive-core contrast, light/unknown backgrounds, cache palettes and
brightness, fractional/HiDPI graphics copies, light selections, restoration and real-editor/fallback rendering.
The upstream hotpink core is intentionally retained, not certified as accessible; contrast tests for adaptive
cores do not certify complete themes or rendered text including alpha, antialiasing and glow.

| IntelliJ version | Full test suite | Baseline artifact Plugin Verifier |
|---|---|---|
| 2025.3.6.1 | 164 passed, zero failures/skips | Compatible |
| 2026.1 | Not run | Compatible |
| 2026.1.5 | 164 passed, zero failures/skips | Compatible |
| 2026.2.3 | 164 passed, zero failures/skips | Compatible |

IDE compilation and release packaging pass on the Java 21/2025.3 baseline. Verifier results retain the three
experimental highlighter-order usages and additionally report the existing preset renderer's
`SimpleListCellRenderer.create(String, Function)` as deprecated/scheduled for removal on 2026.2.3; there are no
binary compatibility failures. Migration of that renderer remains a future-compatibility follow-up, not an
adaptive-rendering failure. The successful baseline suite also logged a bundled Grazie coroutine shutdown
warning, without a failed/skipped test. No new live visual comparison, user trial or performance claim is made;
the manual matrix remains open.

## Light-theme syntax glow regression (2026-10-04)

The shared style policy rejected every light background. With **Regular text glow** off, this also suppressed
coloured syntax such as keywords, functions and identifiers. Three new regression tests failed before the fix,
covering policy resolution, graphics interception and the editor highlighter fallback.

On known backgrounds above 0.12 luminance, colours with saturation at least 0.35 and original flat contrast
at least 3:1 now get three same-hue coloured layers while retaining their original foreground cores. There is
no minimum source brightness, white-core replacement or fixed dark outline on this path. Halo colours are
darkened for backdrop visibility; every layer follows brightness. Neutral/muted/low-contrast text still follows
**Regular text glow**. Unknown backgrounds remain conservative; colour eligibility is not a semantic token classifier.
Dark-background upstream/adaptive rules and saved settings are unchanged.

Tests cover representative dark syntax colours, sampled hue/value/background combinations, original cores,
light/dark cache separation, warm-cache reuse, brightness, both text targets, regular-glow independence,
master/target/strength/style/Power Save gates, and rendered pixels on white at 1×/1.25×/2×. Existing safety
assertions were updated for light eligibility, not disabled. Flat halo-colour contrast checks do not certify
the complete rendered appearance, antialiasing or transparent text as accessible.

- All **173 tests** pass on **2025.3.6.1**, **2026.1.5** and **2026.2.3**, with zero failures, errors or skips.
- IDE compilation and baseline plugin packaging pass. No new API dependencies were introduced; Plugin Verifier was not rerun for this policy-only fix.
- No live visual comparison or user trial was performed. Light-theme appearance and mixed-display visual checks remain manual validation items.