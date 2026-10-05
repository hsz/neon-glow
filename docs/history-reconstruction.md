# Reconstructed commit history

This branch was assembled on 2026-10-04 from an existing AI-assisted implementation. It starts with a plugin
scaffold, then adds rendering, editor integration, Swing glow, settings, themes and documentation in focused commits.
The October 1–3 timestamps were assigned for this reconstruction. They do not record when those features were
originally developed. The commits retain AI co-author credit and both project and upstream licences.

At reconstruction, production source matched the working implementation checkpoint. The test suite also retained
three extra blur boundary tests from the scaffold. See [the reconstruction verification results](theme-quality.md#public-history-verification-2026-10-04).

On 2026-10-05, the project was renamed to **Neon Glow**, with `neon-glow` as the Gradle project and intended repository name and
`info.chrzanowski.neonglow` as the plugin ID and Kotlin namespace. Theme identities, resources, settings storage,
actions and diagnostics were renamed too; rendering behaviour, SynthWave '84 attribution and licences were retained.
The current source therefore differs from the checkpoint by this rename and its identity regression test.
See [the rename verification results](theme-quality.md#neon-glow-rename-verification-2026-10-05).

## Local recovery copies at reconstruction

The original `main` branch was unchanged during reconstruction. `archive/working-plugin-2026-10-04` checkpointed
the full working plugin; the original staged index was saved in `.git/pre-reconstruction-20261004.index`.
These were local recovery copies, not ancestors of the reconstructed branch. Local plans and upstream research
inputs remain on disk and are excluded through `.git/info/exclude` rather than added to the public history.

The reconstructed history is now on `main`. No repository or Marketplace upload was performed. Publish only
`main`, not all local branches or recovery refs.