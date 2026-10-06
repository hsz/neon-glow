<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Neon Glow Changelog

## Unreleased

### Changed

- For light themes, use a maximum of 25% brightness by default.

### Added

- Glow behind text in editors, consoles, diffs, gutters and all Swing UI (tool windows, tabs, menus, popups,
  dialogs, welcome screen), using each text run's own colour.
- Glow behind standard SVG and raster icons, keeping original colours and transparency.
- Neon text styling: layered SynthWave '84-style halos for coloured text, chosen by colour and background
  contrast. The five upstream colour rules are kept on dark backgrounds; other vivid colours get same-hue neon.
- Separate on/off switches and strengths for editor text, UI text and icons; brightness, radius and intensity
  sliders; a regular text glow switch for plain text.
- Performance mode that caps new glow masks per repaint.
- Three themes with paired editor, console and terminal schemes: Neon Glow, Neon Glow Midnight and
  Neon Glow Accessible.
- `View | Appearance | Neon Glow` toggle and a Neon Glow Tools menu with settings, cache reset and a
  diagnostics copy that contains no file paths or project content.
- Glow follows theme and colour scheme changes, pauses in Power Save mode and applies without a restart.
- Compatible with IntelliJ Platform 2025.3 and newer.
