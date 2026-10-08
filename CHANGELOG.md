<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Neon Glow Changelog

## [Unreleased]

### Fixed

- Fix editor, headers and other components flashing (content disappearing for a frame) by no longer replacing Swing's `RepaintManager`; neighbouring-line halos during partial editor repaints are now supplied by the editor highlighter instead.

## [0.3.3] - 2026-10-08

### Fixed

- Prevent `Access is allowed from Event Dispatch Thread (EDT) only` threading exceptions during background caret animations and editor repaints.

## [0.3.2] - 2026-10-08

### Fixed

- Fix neon text glow and styles disappearing near the cursor during editor caret movement, selection changes, and partial repaints.
- Prevent IDE instability and window hierarchy corruption ([IJPL-257899](https://youtrack.jetbrains.com/issue/IJPL-257899)) by replacing invasive root layered-pane interception with platform global graphics transforms.
- Eliminate spurious component removal and lifecycle notifications during window creation and disposal.

### Added

- Add "Reset to Defaults" button in settings to restore default glow settings without changing theme.
- Add numeric input fields for slider controls with bidirectional synchronization and input validation.
- Visual screenshots and demo file showcasing Neon Glow themes in README.

### Changed

- Preserve user active theme and color scheme on dynamic install and startup.

## [0.3.1] - 2026-10-07

### Fixed

- Ensure temporary dispose marker is set on the root pane during layered pane operations to prevent unexpected disposal side effects.

## [0.3.0] - 2026-10-06

### Added

- Add "Reset to Defaults" button in settings to restore default glow settings without changing theme.
- Add numeric input fields for slider controls with bidirectional synchronization and input validation.
- Visual screenshots and demo file showcasing Neon Glow themes in README.

### Changed

- Preserve user active theme and color scheme on dynamic install and startup.

## [0.2.0] - 2026-10-06

### Added

- New plugin icon with radial gradient background and refined neon stroke effects.
- Support section with donation links in README, plugin settings, and plugin metadata.

### Changed

- Rebrand plugin and resources to "Neon Glow", updating plugin ID, namespaces, themes, settings, and documentation.
- Set fresh-install defaults: enable SynthWave-style text, disable regular text glow, and adjust brightness, intensity, and strength settings.
- For light themes, use a maximum of 25% brightness by default.
- Keep user's active theme enabled on first install and run without automatically activating bundled themes.
- Clarify glow target descriptions and neon text styling in settings UI.

### Removed

- Glow presets and draft preview in favor of live settings controls.

## [0.1.0] - 2026-10-05

### Added

- Glow behind text in editors, consoles, diffs, gutters, and all Swing UI (tool windows, tabs, menus, popups, dialogs, welcome screen), using each text run's own colour.
- Glow behind standard SVG and raster icons, keeping original colours and transparency.
- Neon text styling: layered SynthWave '84-style halos for coloured text, chosen by colour and background contrast. The five upstream colour rules are kept on dark backgrounds; other vivid colours get same-hue neon.
- Separate on/off switches and strengths for editor text, UI text, and icons; brightness, radius, and intensity sliders; a regular text glow switch for plain text.
- Performance mode that caps new glow masks per repaint.
- Three themes with paired editor, console, and terminal schemes: Neon Glow, Neon Glow Midnight, and Neon Glow Accessible.
- `View | Appearance | Neon Glow` toggle and a Neon Glow Tools menu with settings, cache reset, and a diagnostics copy that contains no file paths or project content.
- Glow follows theme and colour scheme changes, pauses in Power Save mode, and applies without a restart.
- Compatible with IntelliJ Platform 2025.3 and newer.

[Unreleased]: https://github.com/hsz/neon-glow/compare/v0.3.3...HEAD
[0.3.3]: https://github.com/hsz/neon-glow/compare/v0.3.2...v0.3.3
[0.3.2]: https://github.com/hsz/neon-glow/compare/v0.3.1...v0.3.2
[0.3.1]: https://github.com/hsz/neon-glow/compare/v0.3.0...v0.3.1
[0.3.0]: https://github.com/hsz/neon-glow/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/hsz/neon-glow/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/hsz/neon-glow/commits/v0.1.0
