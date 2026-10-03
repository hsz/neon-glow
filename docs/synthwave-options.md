# SynthWave '84: upstream options and IntelliJ equivalents

External-source research, checked 2026-10-03. The [Marketplace listing][marketplace] currently publishes
`RobbOwen.synthwave-vscode` **0.1.20** (last updated 2025-07-17). The author's repository manifest also reports
0.1.20; source citations below pin the inspected `master` snapshot to
`ecfa2fe1279f7233663fa3f98a96e6756000567b`. [Manifest][manifest]

## 1. Exact user-facing surface

The manifest contributes one dark theme, **SynthWave '84** (`uiTheme: vs-dark`), exactly two configuration
properties, and exactly two commands. Selecting the base theme does not itself install Neon Dreams. [Manifest][manifest], [README][readme]

| Setting | Type / default | Range and actual meaning |
|---|---|---|
| `synthwave84.brightness` | Number / `0.45` | Documented float range **0–1**, with 0 described as transparent and 1 fully bright. The manifest has no `minimum`/`maximum`; implementation parses the value, clamps to 0–1, and falls back to `0.45` for `NaN`. It sets selected shadow-color alpha, not blur radius, foreground opacity, or an intensity multiplier. |
| `synthwave84.disableGlow` | Boolean / `false` | `true` skips editor token glow replacements while retaining injected chrome styling; `false` permits token replacements when Neon Dreams is installed and the SynthWave theme is detected. This setting does not install/uninstall Neon Dreams. |

Sources: [manifest][manifest], [README brightness/chrome instructions][readme],
[configuration parsing and template generation][extension], [conditional token replacement][template].

| Command ID | Exact contributed title | Effect |
|---|---|---|
| `synthwave84.enableNeon` | Synthwave '84: Enable Neon Dreams | Generates `neondreams.js` with settings and chrome CSS, writes it into the VS Code installation, and injects its script tag into workbench HTML if absent. Re-running regenerates the script. Prompts for window reload; not an immediate runtime toggle. |
| `synthwave84.disableNeon` | Synthwave '84: Disable Neon Dreams | Removes the injected script tag, disabling both token replacements and injected chrome after reload. Does not change either setting or deselect the base theme; reports “isn't running” if the tag is absent. |

Sources: [command declarations][manifest], [command implementation][extension]. There is no contributed
`enabled` setting, radius setting, intensity setting, or separate editor/UI/icon switch. Installation of
Neon Dreams is explicit; `disableGlow: false` does **not** mean it is installed by default. [Manifest][manifest], [extension][extension]

The README requires re-running Enable Neon Dreams after setting changes, and re-enabling after VS Code updates.
The implementation reads settings **once at extension activation**, not on every command or through a change
listener: a same-session setting edit may require extension-host reactivation/reload before regeneration,
then the requested reload to load the generated script. Modifying core files requires write permissions and
can trigger VS Code's corruption/unsupported warning; these are not extra SynthWave settings. [README][readme], [extension][extension]

## 2. What brightness really controls

- Brightness becomes `floor(brightness × 255)`, encoded as hex and appended to selected shadow colors.
  Default `0.45` produces `72` hex: alpha **114/255 ≈ 0.4471**. This is per-shadow color alpha, not CSS
  element `opacity` and not RGB luminance adjustment. [Extension][extension], [CSS color specification][hex]
- The template replaces five token colors with hard-coded foregrounds and layered `text-shadow` values.
  Red, yellow and blue become near-white; green remains green and pink remains pink. The README's “text
  will remain white” is therefore a simplification: brightness does not control foreground color, but not
  every replacement is white. [Template][template], [README][readme]
- Adjustable alpha appears in red/yellow/green/blue shadows. Pink uses fixed `#dc078e33` and `#fff3`
  shadows (both alpha 0.2), and other base shadows stay fixed. Blur lengths are hard-coded, spanning
  **2–35 CSS px** across token shadow layers: this is not an exposed radius range or a single default radius.
  [Template][template]
- **Implementation caveat:** the hex conversion is not zero-padded. For `0 ≤ brightness < 16/255`
  (about `0.06275`), it appends one digit to a six-digit color, producing an invalid seven-digit CSS hex
  color. Thus documented zero is not a correctly encoded transparent shadow, and pink remains unaffected
  regardless. Do not copy this behavior into a port. [Extension][extension], [template][template], [valid hex lengths][hex]

## 3. Theme, chrome and unsupported controls

The base theme supplies both editor/token colors and workbench colors (sidebar, tabs, status/title bars,
terminal, etc.) through the standard theme file. It is not “editor colors only,” and disabling Neon Dreams
leaves that normal theming intact. [Theme definition][theme], [manifest][manifest], [extension][extension]

Injected chrome adds an editor background gradient, badge gradients, active tab/sidebar accents and shadows,
and a specific lightbulb icon replacement with a fixed **5 CSS px** cyan drop-shadow. These are always appended,
even with `disableGlow: true`, and contain no brightness placeholder. Consequently neither `disableGlow: true`
nor brightness zero means “turn off every UI halo.” [Template][template], [chrome CSS][chrome]

Upstream has **no configurable general UI-text glow, all-icon glow, independent target toggles, glow radius,
alpha-intensity multiplier, or theme-independent glow mode**. Its token replacement bootstrap checks for the
SynthWave theme and expected token colors; its special lightbulb styling is not a general icon renderer.
The base theme's UI color entries are theme data, not additional `synthwave84.*` settings. [Manifest][manifest], [template][template], [chrome CSS][chrome], [theme][theme]

## 4. Recommended IntelliJ semantics (recommendations, not upstream options)

| Existing/runtime concept | Recommended meaning and relationship to upstream |
|---|---|
| Master glow toggle | Keep a positive enable/disable control for runtime halo rendering, independent of editor/UI theme colors. Enable/Disable Neon Dreams is the closest operational analogy, but IntelliJ should not need installation-file patching or restart. A global halo-off switch is **not** equivalent to upstream `disableGlow`, which retains chrome effects. |
| Glow radius | Keep the existing blur-spread control and its defaults/range. Upstream exposes no counterpart. The optional style mode uses 6 px as the reference for upstream per-layer blur sizes and scales every layer proportionally at other radii; it is not one shared CSS blur length. Do not label radius as brightness. |
| Glow intensity | Keep the existing **alpha multiplier** terminology and defaults/range. Conceptually, `A_halo = clamp(intensity × A_blurred, 0, 1)`: zero suppresses alpha, one preserves the blurred mask, values above one amplify until saturation. This is not an opacity fraction and must not be relabeled or restricted to 0–1 merely because upstream brightness is. |
| Separate brightness control | Keep a 0–1 fraction **in addition to**, not as a renamed intensity control. In same-colour mode, `A_final = brightness × clamp(intensity × A_blurred, 0, 1)`, without fading originals. Optional style mode uses brightness on upstream variable coloured layers and adaptive neon layers; fixed upstream pink/dark layers retain their alpha while enabled. Neither model promises pixel-identical CSS rendering. |
| Editor text / UI text / icons | Keep target enablement independent. Mapping upstream `disableGlow` most closely means disabling editor token glow while preserving chrome/theme styling—not disabling all targets. Independent UI-text and general-icon targets are IntelliJ extensions, not upstream settings. |

### Implemented equivalents

IDE Synthwave now exposes independent editor-text, UI-text and icon switches, plus a 0–100% brightness slider
separate from radius and intensity. In the default same-colour mode, brightness multiplies each cached halo's
alpha after intensity saturation, without changing originals. The optional style mode below has fixed-alpha
exceptions. Unlike upstream, brightness defaults to 100% and style defaults to off to preserve this plugin's
existing appearance. Disable editor text for editor-only suppression, or use the master toggle for all-target
suppression. Brightness zero, master off, target off, target strength zero and Power Save mode all restore original
rendering, including any mapped foreground cores, for the affected targets. This also suppresses fixed-alpha layers;
brightness zero intentionally differs from upstream's pink-shadow behavior and malformed zero-alpha CSS.
All controls take effect on **Apply**, without installation-file edits or restart.

### Optional SynthWave '84 style

**SynthWave '84-style text** is an explicit opt-in checkbox persisted as `State.synthwaveStyle = false` by
default. It works with your current IDE theme on enabled editor-text and UI-text targets; no theme installation,
automatic opt-in or new preset is required. Existing saved settings and presets stay unchanged. Keep your theme,
enable **Enable glow** and **Editor text**, expand **Fine-Tune Glow**, check **SynthWave '84-style text**, then
**Apply** or **OK**. Recommended values are **45% brightness**, **100% intensity** and **6 px radius**.

The five exact RGB rules from the [upstream token template][template] remain on known dark backgrounds. These
preserve historical styling, not contrast certification; the hotpink core in particular is not certified contrast.
On darker intermediate backgrounds, an exact mapped core below 3:1 flat contrast retains the source core while
keeping the upstream shadow layers. Light backgrounds retain original cores but now support background-aware
coloured layers; unknown backgrounds use same-colour glow controlled by **Regular text glow**.

All layer offsets are zero. Blur lengths below are the upstream sizes in logical pixels at the default radius
of 6 px, in source layer order. “Dark base” denotes the fixed dark 2 px shadow, not the mapped foreground.

| Exact source RGB | Foreground core | Ordered halo layers at radius 6 px |
|---|---|---|
| `#36f9f6` | `#fdfdfd` | 2 px dark base; 3 px, 5 px and 8 px cyan `#03edf9`. |
| `#fede5d` | `#f4eee4` | 2 px dark base; 8 px and 2 px orange `#f39f05`. |
| `#fe4450` | `#fff5f6` | 2 px dark base; 10 px, 5 px and 25 px red `#fc1f2c`. |
| `#ff7edb` | `#f92aad` | 2 px dark base; 5 px `#dc078e` at fixed alpha 0.2; 10 px white at fixed alpha 0.2. |
| `#72f1b8` | `#72f1b8` (unchanged) | 2 px dark base; 10 px `#257c55`; 35 px `#212724`. |

Other colours, including near matches, get pale same-hue-tinted cores and a **2 px dark base plus 3/7/12 px
saturated same-hue neon layers** at radius 6 px only when all these conditions hold:

- Known background luminance **<=0.12**.
- Source saturation **>=0.35** and value **>=0.5**.
- Original flat text/background contrast **>=3:1**.

On light backgrounds (luminance **>0.12**), saturation **>=0.35** and source contrast **>=3:1** qualify without
a minimum source value, so dark syntax colours can glow. Original cores remain unchanged. Three **3/7/12 px**
same-hue coloured layers use saturation at least 0.85 and value at most 0.6, darkened further until the flat
halo colour contrasts at least 3:1 with the backdrop. There is no fixed dark base or white core in this path;
all three layers follow brightness. This is a visibility heuristic, not an accessibility certification.
Eligible layered text glows even when **Regular text glow** is off, in both text targets and editor fallback.

Neutral, muted and low-contrast colours keep original cores and existing same-colour glow. Eligibility is based
on colour, not token semantics: the renderer cannot identify comments from colour alone, and vivid comments can
qualify. Same-colour text halos follow **Regular text glow**; disabling it leaves such text crisp.
Icons keep their existing colours, same-colour glow and crisp originals; this setting never layers icons.
Original text alpha is preserved in every path.

UI backdrop detection is best-effort through component opacity and known solid fills, not a guarantee for arbitrary
custom painting. Layered masks cache the resolved rule so light/dark decisions do not mix.

Brightness applies to upstream variable coloured layers and adaptive neon layers, not foreground RGB or blur size. Pink's two
coloured layers remain at alpha 0.2 and dark base layers stay fixed while the effect is active. Intensity
remains the blurred-coverage multiplier before layer alpha is applied; it is not a second brightness slider.
Radius scales **all** text-layer blur sizes proportionally by `radiusPx / 6`, including fixed-alpha and dark
layers: for example, radius 12 px doubles the 2/3/5/8 px cyan sizes to 4/6/10/16 px. At brightness zero, no
layers or foreground mapping are applied, even for pink; disabling the master or relevant text target, setting
target strength to zero or entering Power Save likewise restores the complete original rendering.

Defaults remain brightness 100% and intensity 300% for backward compatibility; opting into
style does not silently change those values. Swing's Gaussian rasterization and CSS text shadows are not
pixel-exact equivalents, and this mode does not reproduce upstream's theme/chrome or installation patching.

**Fallback limitation:** without graphics interception, the editor under-glow fallback uses the scheme backdrop
to choose upstream/adaptive layered halos or same-colour glow, painted beneath the original text. It cannot replace
foreground cores; that requires graphics interception. Styled cores are therefore not promised in fallback-only
rendering; backdrop/eligibility and off/zero/Power Save restoration rules still apply.

[marketplace]: https://marketplace.visualstudio.com/items?itemName=RobbOwen.synthwave-vscode
[readme]: https://github.com/robb0wen/synthwave-vscode/blob/ecfa2fe1279f7233663fa3f98a96e6756000567b/README.md
[manifest]: https://github.com/robb0wen/synthwave-vscode/blob/ecfa2fe1279f7233663fa3f98a96e6756000567b/package.json
[extension]: https://github.com/robb0wen/synthwave-vscode/blob/ecfa2fe1279f7233663fa3f98a96e6756000567b/src/extension.js
[template]: https://github.com/robb0wen/synthwave-vscode/blob/ecfa2fe1279f7233663fa3f98a96e6756000567b/src/js/theme_template.js
[chrome]: https://github.com/robb0wen/synthwave-vscode/blob/ecfa2fe1279f7233663fa3f98a96e6756000567b/src/css/editor_chrome.css
[theme]: https://github.com/robb0wen/synthwave-vscode/blob/ecfa2fe1279f7233663fa3f98a96e6756000567b/themes/synthwave-color-theme.json
[hex]: https://www.w3.org/TR/css-color-4/#hex-notation
