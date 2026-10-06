# Neon Glow

Neon Glow is a free and open-source neon glow plugin for JetBrains IDEs. Keep your theme, add some light.

> **Support Neon Glow**
>
> If you find this plugin useful, support its development via **[GitHub Sponsors](https://github.com/sponsors/hsz)**, **[Ko-fi](https://ko-fi.com/hszanowski)**, or **[PayPal](https://paypal.me/hsz)**.
>
> You can also find my other projects on [GitHub](https://github.com/hsz).

Neon Glow paints a soft, blurred halo behind text and icons across the whole IDE: editor, tool windows, tabs,
menus, popups and dialogs. Coloured code gets layered neon in the style of SynthWave '84. Plain text stays
crisp unless you ask for it to glow too.

Three dark themes come bundled, but you don't need them. The glow works with whatever theme you already use.

## Install

1. `Settings | Plugins | Marketplace`, search for **Neon Glow**, install.
2. Open `Settings | Appearance & Behavior | Neon Glow`. Glow is on by default; adjust and press **Apply**.
3. Optional: pick **Neon Glow**, **Neon Glow Midnight** or **Neon Glow Accessible** under
   `Settings | Appearance & Behavior | Appearance`.

`View | Appearance | Neon Glow` toggles the glow on and off. Nothing needs a restart.

## Settings

| Setting | Default | What it does |
|---|---|---|
| Enable glow | on | Master switch. Same as `View \| Appearance \| Neon Glow`. |
| Editor text | on | Code, console and diff text, gutters. |
| UI text | on | Tool windows, tabs, menus, popups, dialogs. |
| Icons | on | Standard SVG and raster icons. |
| Editor / UI / Icon strength | 75 / 50 / 50 % | Halo opacity per target. 0 % restores original rendering for that target. |
| Brightness | 50 % | Overall halo opacity (maximum of 25 % by default on light themes). |
| Radius | 6 px | How far the halo reaches. |
| Intensity | 200 % | Boosts coverage before brightness. Thin fonts need more. |
| Neon text styling | on | Layered neon for coloured text. Picks text by colour and contrast, so a colourful comment glows and a grey one doesn't. On dark backgrounds letters get a pale tint; on light backgrounds they keep their colour. |
| Regular text glow | off | Same-colour halos for everything Neon text styling leaves alone. Turn on to glow all text. |
| Performance mode | off | Caps new glow masks per repaint. Some halos may show up one repaint late. |

Each slider has a numeric input beside it for entering an exact value in percent or pixels.

**Reset to Defaults** restores these glow settings without changing your theme or editor colours. Press **Apply** to save.

For a look close to the VS Code extension: Neon text styling on, brightness 45 %, intensity 100 %, radius 6 px.
Exact colour rules and how they differ from the CSS original: [docs/synthwave-options.md](docs/synthwave-options.md).

`View | Appearance | Neon Glow Tools` has **Reset Neon Glow Caches** (if a halo looks stale) and
**Copy Neon Glow Diagnostics** (versions, settings, cache counts; no file paths or project content).

## What it doesn't do

- No animation. It paints only when the IDE repaints.
- No glow in Power Save mode.
- No glow on native menu bars and title bars, embedded browsers, terminal panes that bypass Swing text
  drawing, icons painted from vector shapes, or images over 128 px.
- Performance mode limits new work; it isn't a frame-time guarantee.
- Themes don't change glow settings, and glow doesn't change your theme, font or layout.

The glow hooks into Swing painting and uses three experimental editor APIs for its fallback path. Details,
measurements and the open manual checks: [docs/glow-investigation.md](docs/glow-investigation.md) and
[docs/theme-quality.md](docs/theme-quality.md).

## Compatibility

IntelliJ Platform 2025.3 and newer (`since-build=253`, no upper bound). Built against 2025.3.6.1 on Java 21.
Plugin Verifier passes on 2025.3.6.1, 2026.1, 2026.1.5 and 2026.2.3. Other JetBrains products should work
but haven't been checked visually.

## Development

```shell
./gradlew test                          # unit + light platform tests
./gradlew runIde                        # sandbox IDE, logs glow stats with -Dide.neon.glow.debug=true
./gradlew buildPlugin
./gradlew verifyPlugin                  # the four versions above
./gradlew test -PplatformVersion=2026.2.3   # needs a Java 25 toolchain
```

Settings live in `neon-glow.xml`. With the debug flag the plugin writes a `[NeonGlow] paints=… paint avg=…µs`
line to `idea.log` every 10 seconds.

The commit history was rebuilt from an AI-assisted implementation; dates are not the original timeline.
See [docs/history-reconstruction.md](docs/history-reconstruction.md).

## License

[Apache License 2.0](LICENSE)

Inspired by Robb Owen's [SynthWave '84](https://github.com/robb0wen/synthwave-vscode) (MIT), with no affiliation
to its author. A personal project, not an official JetBrains product.
