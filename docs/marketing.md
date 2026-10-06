# Launch notes

Everything needed to put Neon Glow on the Marketplace and tell people about it. Nothing here is published yet.

## Listing

Marketplace takes the description from `plugin.xml`, so edit it there. One-liner for the listing header and
social previews:

> Neon glow for JetBrains IDEs. Keep your theme, add some light.

## Screenshots to take

All at 1920 × 1080, same Kotlin file, same font and zoom, same window layout. Hide notifications and recent
projects first. Use this sample code:

```kotlin
data class Track(val title: String, val durationSeconds: Int)

fun playlistDuration(tracks: List<Track>): Int =
    tracks.sumOf { it.durationSeconds }

fun main() {
    val tracks = listOf(Track("After Dark", 180), Track("Neon Skyline", 240))
    println("Playlist: ${playlistDuration(tracks)} seconds")
}
```

| File | Theme | Glow settings |
|---|---|---|
| `classic.png` | Neon Glow | Defaults |
| `midnight-editor-only.png` | Neon Glow Midnight | Defaults, UI text and Icons off |
| `your-theme.png` | Darcula or Dark | Defaults (shows it works without the bundled themes) |
| `settings.png` | any | The Neon Glow settings page |
| `before.png` | Darcula or Dark | Enable glow off (for a before/after pair) |

Marketplace wants at least 1200 × 760 and a consistent aspect ratio. A short screen recording (under a minute,
hosted on YouTube) can go in the Media section too.

## Announcement post

Adjust to taste. Replace `LISTING_URL` after the plugin is approved.

> **Neon Glow: neon glow for JetBrains IDEs**
>
> I wanted the SynthWave '84 glow from VS Code in IntelliJ, so I built it as a plugin. It paints a blurred halo
> behind text and icons across the IDE, not just in the editor. Coloured code gets layered neon, plain text
> stays sharp.
>
> It works with any theme. If you want the full look there are three bundled dark themes too.
>
> Everything is adjustable: editor, UI and icon glow have their own switches and strengths, plus brightness,
> radius and intensity. There's a performance mode if your machine is slow to repaint.
>
> Known gaps: native menus, embedded browsers and icons drawn from vector shapes don't glow. It's Java2D, so
> it's close to the CSS original, not identical.
>
> LISTING_URL
>
> Bug reports and screenshots of odd rendering welcome.

Where to post: your own blog/social accounts, r/IntelliJIDEA, r/Kotlin, and the JetBrains Platform Slack
`#plugins` channel. Mention you're the author.

## Before publishing

1. Run `./gradlew verifyPlugin` on the exact build you upload.
2. Take the screenshots above and look at them at listing size.
3. Install the ZIP from disk into a clean IDE and walk through the README's Install steps.
4. Create the GitHub repository as `neon-glow`, push `main` only, add the URL to `plugin.xml` as `<vendor url>`
   or a link in the description.
5. Upload to the Marketplace, add screenshots, configure the Donation URL (https://github.com/sponsors/hsz or https://ko-fi.com/hszanowski), wait for approval, then post.
