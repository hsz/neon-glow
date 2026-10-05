# Neon Glow visual production kit

**Production specifications, not finished captures.** No approved screenshots or recordings are available in
the repository inventory. See [media status](media/README.md). Missing captures block visual launch; never
substitute synthetic IDE images or borrowed upstream screenshots.

## Capture contract

1. Use a clean IntelliJ IDEA workspace and non-sensitive Java/Kotlin sample. Reuse the same code, font, zoom,
   editor size, Project tree, tab order and window layout across comparisons. Do not alter viewers' settings
   automatically or imply the plugin changes spacing/fonts. Use an owner-selected tested version; record the
   exact IDE build, plugin release/archive, runtime, OS and display scale in the media manifest.
2. Capture applied **live** editor, Project tree, tabs and standard icons.
   Use Settings only to show controls, then Apply and return to the workspace. Wait for normal rendering to
   settle; do not postprocess halos or claim every surface glows.
3. For reproducibility, use 100% display scale on the selected capture machine where possible and record
   actual scaling if different. A chosen capture scale does not close the mixed-scale manual test matrix.
   Choose a legible font/zoom once (e.g. 16–18 px code) and hold it constant. Save original lossless captures.
4. Use the same sample code for theme variants. Example author-written Kotlin content:

```kotlin
data class Track(val title: String, val durationSeconds: Int)

fun playlistDuration(tracks: List<Track>): Int =
    tracks.sumOf { it.durationSeconds }

fun main() {
    val tracks = listOf(Track("After Dark", 180), Track("Neon Skyline", 240))
    println("Playlist: ${playlistDuration(tracks)} seconds")
}
```

5. Remove personal paths, account avatars/emails, recent projects, real branch names, secrets, terminal history,
   notifications, bookmarks, clipboard content and unrelated browser/desktop windows **before** capture.
   A neutral demo project name is fine. Do not expose private diagnostics or logs in settings shots.
6. Record source footage without effects, transitions or audio embellishment. No flashing cuts, scanlines,
   simulated gradients/artwork, enhanced glow, speed-ramped rendering or licensed music. Simple labelled cuts
   and captions are allowed. Separate settings dwell time in raw footage from the final edit; do not imply
   multiple Apply operations happened instantaneously.

## Reproducible configurations

Select themes separately under **Settings | Appearance & Behavior | Appearance**. Configure glow under
**Settings | Appearance & Behavior | Neon Glow** using the manual controls below, then **Apply** or **OK**.
Record all applied values; do not capture preserved unknown settings.

| Capture ID | Theme | Applied glow | Values to record/check |
|---|---|---|---|
| `classic` | Neon Glow (Classic) | Manually configured neon | Enabled; editor/UI/icons on; strengths 100/25/35%; brightness 45%; intensity 100%; radius 6 px; mapped text and regular text glow on; performance off |
| `midnight-focus` | Neon Glow Midnight | Manually configured editor-only glow | Enabled; editor on at 100%; UI/icons off at 0%; brightness 45%; intensity 100%; radius 6 px; mapped text and regular text glow on; performance on |
| `accessible-flat` | Neon Glow Accessible | Master glow off | **Master glow off**; verify live editor/UI/icons are flat; other saved values do not imply enabled glow |
| `control-demo` | Neon Glow (Classic) | Manually configured same-colour glow | Enabled; editor/UI/icons on; mapped text **off**, regular text glow **on**; strengths 100/25/35%; brightness 45%; intensity 100%; radius 6 px; performance off; show supported UI text/icon target control live |
| `before` | Owner-selected built-in dark theme, named in manifest | Master glow off | Same content/layout/font/zoom; label baseline theme and glow state |

Style mode preserves five upstream rules on known dark backgrounds and adapts other eligible vivid colours;
neutral, muted and low-contrast text keeps original cores and same-colour glow, as do ordinary white UI labels. Light/unknown backgrounds
preserve originals and same-colour glow. This is colour-based, not semantic comment exclusion or contrast certification.
UI backdrop detection is best-effort; the editor fallback can layer halos but cannot replace cores. The control demo
uses same-colour mode deliberately; show that setting in its raw source and label the configuration. Do not
edit in a stronger halo than the recorded setting produces. If UI/icon differences are not clear at display
size, improve framing or recapture—not the pixels. Power Save must be off for enabled-glow shots; record it.

## Hero storyboard: 20 seconds

Master: 1920 × 1080, 16:9, 20 seconds; production choice of 30 fps, MP4/H.264, no required audio. Keep a
captioned export and editable caption text. Timecodes below are edit segments, not a plugin speed benchmark.

| Time | Live shot and configuration | Visible caption |
|---|---|---|
| 0–3 s | Same workspace: labelled built-in dark baseline/glow off → Neon Glow (Classic)/control-demo applied | Your IDE. After dark. |
| 3–8 s | Steady editor + Project tree + selected tabs + standard icons, control-demo configuration | More than syntax colours. |
| 8–13 s | Brief controls close-up; three matched live cuts isolate editor, UI text and icons by changing only the named target, Apply between raw takes | Glow where you want it. |
| 13–17 s | 2 s Midnight/editor on, UI/icons off → 2 s Accessible/master off; exact same workspace | Go neon. Or keep it quiet. |
| 17–20 s | Legible settings-path card and the verified primary destination; retain genuine workspace thumbnail | Make it yours. |

For target-control takes, start with all targets off; enable editor only, then UI only, then icons only. Restore
control-demo settings for other shots. The edited montage needs small persistent labels (“Editor,” “UI text,”
“Icons”); keep the raw Apply takes.

Final card: show **Appearance: choose theme / Neon Glow: tune glow**; the guide supplies full paths. Use one CTA
from [campaign.md](campaign.md): prelisting “See the demo and installation guide” only when that destination
exists, otherwise “Install Neon Glow” only after public listing verification. If the CTA cannot fit legibly,
put its full wording in the post and retain one short destination on the card. No fake listing QR code.

Caption/narration text (also suitable as descriptive post text):

> Your IDE. After dark. SynthWave-inspired themes, beyond syntax colours. Control code, UI text and standard
> icon glow separately. Choose Midnight with editor glow on and UI/icon glow off, or Accessible with master glow off.
> Select your theme in Appearance;
> tune glow in Neon Glow settings. Experimental glow; some native, browser and custom-painted surfaces are excluded.

Keep captions outside important code/control areas; use an opaque readable backing and check at actual player
size with sound off. Add a small “Experimental glow” label during effect shots; provide the full limitations in
the linked guide. Captions must describe what the footage actually demonstrates. Avoid shrinking two full
IDE windows side-by-side until neither can be read; a matched cut is preferable.

## Three screenshot layouts

Production canvas: 1920 × 1080 PNG, sRGB, consistent 16:9. Capture the IDE itself without desktop/browser.
Use approximately 70% editor and 30% Project/navigation; include tabs and standard icons. Keep an uncropped
raw capture; any title band is visibly editorial and must not cover meaningful live UI.

| Export name | Visible configuration | Title / caption | Alt text draft |
|---|---|---|---|
| `classic.png` | `classic` | Classic palette. Optional neon. | IntelliJ IDEA with Neon Glow (Classic) and manually configured neon glow; Kotlin code, Project tree, tabs and standard icons. |
| `midnight-focus.png` | `midnight-focus` | Midnight. Focus on code. | Same IntelliJ IDEA workspace with Neon Glow Midnight; editor glow enabled, UI text and icon glow disabled. |
| `accessible-flat.png` | `accessible-flat` | Accessible palette. Glow off. | Same IntelliJ IDEA workspace with Neon Glow Accessible and the master glow switch disabled. |

Alt text must be revised to match actual capture content, not copied blindly. “Accessible” is the theme name,
not accessibility certification. If a screenshot is too dense on mobile, make a clearly labelled genuine crop
of editor + one UI surface; do not composite independent UI fragments as if they were one live screen.

## Advertising treatments

Use one CTA from the registry per asset. Start with A and B in the same eligible audience; C responds to real
“too much glow” objections rather than splitting the small paid budget three ways.

| ID / angle | Headline | Supporting line | Genuine proof and composition |
|---|---|---|---|
| A / transformation | Your IDE. After dark. | Deep-purple themes. Optional neon text and icon glow. | Matched baseline/after cut or one legible Classic live screenshot; consistent baseline labelling |
| B / personal control | Neon where you want it. | Tune code, UI text and icon glow independently. | Control-demo target montage with settings applied, not a UI mockup |
| C / restrained | Neon Glow, without the overload. | Choose Midnight, keep only editor glow on, or switch effects off. | Midnight/editor-only and Accessible/master-off matched cuts, settings explicitly labelled |

For each: product name, one headline, genuine capture, one CTA. Keep limitations in nearby supporting copy and
the destination; if platform format cannot support an honest explanation, do not use that placement.

## Destination dimensions and exports

Checked primary sources on 2026-10-04. These are production specs, not an assertion that an absent file passed
upload. Recheck current limits and preview crops before publishing; existing account surfaces vary.

| Destination | Checked guidance | Planned output / remaining gate |
|---|---|---|
| Marketplace Media screenshots | Minimum **recommended** 1200 × 760; theme screenshots required; consistent aspect ratios | 1920 × 1080 PNG set; actual legibility/upload review blocked until exports exist |
| Marketplace video | Supports YouTube URL; recommends short demo under 5 min | 20 s hero; owner-approved hosted URL still pending; do not treat MP4 as direct listing upload |
| Reddit image experiment | Business learning hub lists 16:9 at 1920 × 1080 and 1:1 at 1080 × 1080; JPG/PNG, max 3 MB | Use two 1920 × 1080 assets under 3 MB; choose square only if a genuine readable crop is better |
| Reddit video if used instead | Video page lists MP4/MOV, ≤1 GB, 1:1/4:5/16:9, ≤15 min (≤60 s recommended) | 20 s, 1920 × 1080 MP4; check account's exact format, crop and subtitles; not required for the $100 pilot |
| Owner's existing social/blog channel | No channel/account was supplied | Use hero master as source; verify destination's current limits, caption support and display size before adapting |

Reddit's image CTA is chosen from its predefined list. Prefer **Learn More** if available; asset/post wording
still uses the truthful campaign CTA. Verify that the selected platform button and destination agree; never
select Shop Now for an unimplemented paid offering. Do not claim hard-coded specs for unspecified channels.

## Asset acceptance checklist

- [ ] File exists and is an authentic live capture; raw take and applied-settings manifest retained.
- [ ] Exact IDE/plugin build, theme, effect state, font/zoom/scale and privacy review recorded.
- [ ] Before/after use identical code/layout; cuts/crops clearly labelled; no unsupported visual effects.
- [ ] Capture legible at listing, feed and mobile player sizes; no clipped text, unreadable controls or flash cuts.
- [ ] Actual width/height, aspect ratio, bytes, codec/duration (video) and upload preview match destination limits.
- [ ] Captions, alt text and text-only description match actual visuals; sound-off comprehension checked.
- [ ] Accessible capture is explicitly master-off; Midnight capture has editor glow on and UI/icons off; effect settings are applied, not draft-only.
- [ ] No personal paths, code, accounts, secrets, notifications or third-party assets with unclear rights.
- [ ] One verified CTA/destination; no placeholders, price/trial offer, endorsement implication or inflated claim.
- [ ] Full experimental/coverage limits linked; owner approval recorded before upload/publication.

Sources: [Marketplace listing guidance](https://plugins.jetbrains.com/docs/marketplace/best-practices-for-listing.html),
[Reddit image guidance](https://www.business.reddit.com/learning-hub/articles/reddit-image-ad-specs),
[Reddit video guidance](https://www.business.reddit.com/advertise/ad-types/video-ads).
Product evidence: [README](../../README.md), [quality checklist](../theme-quality.md).