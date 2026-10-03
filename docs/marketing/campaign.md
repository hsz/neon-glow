# IDE Synthwave launch campaign

Planning baseline: 2026-10-04. **Draft materials only; nothing is published and no spending is authorized.**

## Positioning

**Your IDE. After dark.**

SynthWave-inspired themes with optional glow for code, UI text and standard icons—dial each one to your taste.

Sell the transformation, demonstrate the control, earn trust with honest limits. Start with a 30-day,
organic-first pilot for IntelliJ IDEA; consider paid distribution only after the launch gates pass.

| Audience | Need | Message and proof |
|---|---|---|
| Primary: IntelliJ IDEA Java/Kotlin developers who customize their workspace | A coordinated appearance, not just syntax colours | Same-workspace before/after; editor, Project tree, tabs and standard icons |
| Secondary: SynthWave enthusiasts moving from VS Code | Familiar inspiration with practical controls | Upstream-inspired palettes or keep your IDE theme with optional adaptive text glow; independent target switches |
| Developers who find neon distracting | A restrained alternative | Midnight with Focus; Accessible with glow explicitly disabled |

Do not broaden product-specific advertising to other JetBrains IDEs until genuine captures and installation
checks exist for them. Do not imply an upstream affiliation or pixel-identical VS Code rendering.

## Deliverables and boundaries

- [Copy pack](copy.md): Marketplace description, founder announcement, installation guide and FAQ.
- [Visual production kit](creative.md) and [media inventory](media/README.md): authentic exports still required.
- [Launch/outreach runbook](distribution.md): owner approval required.
- [Measurement/decision runbook](measurement.md): aggregate reports only; no campaign results yet.

No licensing implementation, website build, plugin telemetry, fingerprints, advertising pixels, automatic
posting, outreach sending, purchases or advertising-account changes. Do not remove existing features or
advertise Pro, a trial or a price. The earlier $15/year figure is a future pricing hypothesis, not an offer.

## Destination registry and CTA selection

Fill these fields only after opening and checking the actual public destination. `PENDING` is a blocker,
not a URL; never publish bracketed placeholders or invent a Marketplace ID.

| Key used in drafts | Current state | Acceptance check |
|---|---|---|
| `[LISTING_URL]` | PENDING: no verified public listing | Logged-out access; correct name, vendor and plugin ID `info.chrzanowski.idesynthwave`; usable compatible release; install route works |
| `[DEMO_GUIDE_URL]` | PENDING: no verified public demo/guide destination | Authentic demo and current installation guide accessible without an account; no unavailable installation promise |
| `[FEEDBACK_URL]` | PENDING: owner-approved public support/issue destination | Owner monitors it; submit/read permissions and privacy expectations checked |
| `[DEMO_URL]` | PENDING: recording not supplied | Captioned genuine live IDE footage; playback works; destination requirements checked |

- Before a listing is verified: **See the demo and installation guide** → `[DEMO_GUIDE_URL]`, but only once
  that page and its authentic demo are available. Until then, keep the copy unpublished.
- After a listing is verified: **Install IDE Synthwave** → `[LISTING_URL]`.
- One primary CTA per asset. Feedback may be a secondary invitation in longer organic posts, not a second ad button.
- Never use “Buy,” “Try Pro,” “Start your trial” or price messaging without actual commercial packaging,
  licensing, customer terms and owner approval.

## Launch gates

The owner records date, evidence link and approver for each gate. Unknown means blocked, not passed.

| Gate | Required evidence | Current status |
|---|---|---|
| Product facts | Copy checked against README, descriptor and quality checklist below | Grounded in repository; owner must recheck against release to publish |
| Live visuals | Three reviewed screenshots and captioned 20-second hero, with applied settings | BLOCKED: missing approved captures |
| Destination | Registry filled; public links opened; listing or honest prelaunch guide verified | BLOCKED: public destinations unverified |
| Installation/configuration | Owner follows guide in an intended supported IntelliJ IDEA build | BLOCKED: campaign-specific walkthrough not performed |
| Limitations | Experimental glow and coverage/performance caveats adjacent to detailed demo/guide | Drafted; must accompany publication |
| Rights and privacy | Non-sensitive sample, no personal paths, licensed assets, upstream notices retained | BLOCKED: capture review pending |
| Publication | Owner approves exact copy, asset, destination, channel and date | NOT AUTHORIZED |
| Optional paid pilot | All above plus audience availability, platform minimums, quote and capped spend approval | NOT AUTHORIZED |

A complete documentation kit is not a launched campaign. Missing visual evidence must remain a launch blocker.

## Claim checklist

Source baseline: [`README.md`](../../README.md), [`plugin.xml`](../../src/main/resources/META-INF/plugin.xml)
and [`docs/theme-quality.md`](../theme-quality.md), especially its 2026-10-04 validation snapshots.
Recheck these against the version actually distributed; historical results do not validate a changed binary.

| Permitted claim | Grounding | Necessary qualification |
|---|---|---|
| Three complete theme/scheme pairs | README “What it does”; three `themeProvider` entries | Display names: SynthWave '84, SynthWave Midnight, SynthWave Accessible; theme and glow are separate |
| Coordinated editor, console and terminal palettes; recoloured standard SVG icons | Descriptor description; README; resource checks in quality checklist | Not every third-party painter or terminal engine shares glow coverage |
| Classic, Neon, Focus and Accessible glow presets | README settings; quality checklist | Selecting a preset fills draft controls immediately; Apply/OK commits; no automatic theme/font/layout change |
| Independent editor/UI/icon switches and strengths | README settings; automated target tests documented in quality checklist | UI/icon glow is experimental; standard images, not all custom vector icons |
| Optional SynthWave-style text with your current IDE theme | README; implemented equivalents in `docs/synthwave-options.md` | Eligible vivid colours on dark backgrounds only; light/unknown backgrounds retain originals and same-colour glow; historical upstream hotpink is not contrast-certified |
| Glow settings apply without restarting | README settings | Applies to settings changes, not a guarantee that plugin installation needs no restart |
| Static glow respects Power Save | README; quality checklist | Actual live rendering, not isolated draft preview, is evidence |
| Performance mode limits new mask work | README; quality checklist | Best-effort admission limit, not zero overhead or a total repaint-time bound |
| Named compatibility evidence | Quality checklist compatibility matrix | 141 tests on 2025.3.6.1, 2026.1.5, 2026.2.3; Plugin Verifier also on 2026.1; no universal visual certification |
| Accessible is a brighter, flat-readable theme option | Quality checklist contrast checks | Name is not accessibility certification; select Accessible glow preset or disable glow separately |

Forbidden shortcuts: “all IDE surfaces/icons,” “zero overhead,” “certified accessible,” “boosts productivity,”
“guaranteed future compatibility,” fabricated reviews/downloads/ROI, or unverified availability/prices.
Native menus/title bars, browser-rendered panels, custom shape-painted icons and non-Swing terminal paths
remain excluded from universal glow claims. Three experimental editor-fallback API usages remain documented.

## Pilot direction

- Days 1–7: conversion pack, genuine captures, destinations and claim review.
- Days 8–14: approved owned-channel announcement and permitted, tailored community demonstrations.
- Days 15–21: five-contact creator shortlist; optional capped two-creative paid learning experiment.
- Days 22–30: answer real objections; review evidence and choose continue/change/stop.
- Organic cash route: $0. Optional planning ceiling: $100 Reddit + up to $200 one creator, $300 total.
  Production time is separate. No money or publication is authorized by this document.
- Measure impressions → clicks → reported listing visitors → aggregate downloads. Do not equate downloads
  with active users or claim user-level click-to-install attribution. Seek roughly 100 qualified destination
  visits for directional learning, not statistical significance or a promised outcome.

## Listing guidance

Current [Marketplace listing guidance](https://plugins.jetbrains.com/docs/marketplace/best-practices-for-listing.html)
checked 2026-10-04: concise English description, features shown in Media, genuine theme screenshots required.
Description edits may be managed on the listing; this work drafts copy only and does not change `plugin.xml`.
An owner must choose the listing's description source and approve publication. Featured placement and campaign
results are opportunities, not guarantees.