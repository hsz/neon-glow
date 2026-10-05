# Simple, powerful Neon Glow: usability gate

**Status: trial not run.** Automated checks establish specific behavior, not product demand, ease of use,
retention, accessibility certification or zero performance cost. This is a small formative trial, not a survey
that can predict adoption. Do not describe proposed targets below as measured outcomes.

## Keep the first experience small

The primary path is **choose theme → configure glow targets → Apply**. Themes remain usable with effects off.
Settings initially show master/target switches, brightness and performance mode; **Fine-Tune Glow**
holds independent strengths, mapped style, radius and intensity. Numeric readouts make custom settings
reproducible. Do not add more features or a first-run wizard merely to compensate for confusing labels.

| Feature | User job | Validation boundary |
|---|---|---|
| Three themes and matching schemes | Make editor and IDE surfaces coherent | Resource/contrast tests plus the live [visual matrix](theme-quality.md#manual-release-matrix) |
| Independent switches and strengths | Reduce visual noise selectively | Rendering/zero-strength tests; live screenshots and user comprehension still required |
| Brightness, radius, intensity and mapped style | Reproduce a preferred appearance | Numeric readouts and precise saved values; advanced controls stay optional |
| Performance mode and Power Save | Control optional work | Budget/original-rendering tests; real typing/scrolling responsiveness still requires observation |
| Toggle, cache reset and diagnostics | Recover or report without reinstalling | Lifecycle/action tests; reports are reviewed and shared voluntarily |

Glow settings do not change the theme, fonts, layout or keymap. Keep those guarantees when simplifying settings.
All three targets are enabled by default, with **Neon text styling** on and regular text glow off.
Explicit saved preferences are retained; recommending editor-only glow in onboarding is not silently migrating preferences.

## Five-developer formative trial

Recruit five consenting IntelliJ IDEA Java/Kotlin developers, including at least two who have not used the
plugin and one who prefers little or no glow. Use a trusted owner-approved build and a non-sensitive project.
Record exact IDE/plugin/OS/display scale, test date and whether installation or setup is being timed.
No plugin telemetry, fingerprints or new analytics are needed. Any session recording requires separate consent.

Give each task without naming the exact controls; observe before coaching:

1. Choose a quiet Neon Glow workspace and apply editor-only glow. Measure time from opening Settings, excluding
   download/restart time. Ask which operations change the live IDE and which only change the draft.
2. Increase glow brightness or strengths, then abandon the draft. Confirm the original settings and live rendering
   remain intact. Check that expanded fine-tuning is discoverable without being needed for initial setup.
3. Disable all glow without losing the selected theme or target preferences, then restore it. Ask what selecting
   the Accessible *theme* alone does; record confusion between theme selection and the master glow switch explicitly.
4. Find one exact slider value, change only that control and Apply. Check that untouched values survive. Inspect
   actual editor/UI/icons; confirm ordinary white UI-label glow follows **Regular text glow**.
5. Do normal work: type/delete on wrapped lines, scroll, use completion, navigate settings, change theme and
   move between available display scales. Record readability, stale pixels and perceived responsiveness. Request
   a diagnostic report only if needed; let the participant review it before voluntarily sharing it.

Use this worksheet per participant; identify people with consented study IDs, not project paths:

| Field | Result |
|---|---|
| Study ID, date, versions, OS/scale, previous familiarity | NOT RUN |
| Setup time, unassisted success and prompts needed | NOT RUN |
| Draft/Apply and theme/effect distinction understood | NOT RUN |
| Successful disable/restore and preserved preferences | NOT RUN |
| Chosen theme/glow targets, advanced controls used and reason | NOT RUN |
| Readability or responsiveness issue, reproduction | NOT RUN |
| Would use during normal work; why or why not | NOT RUN |
| Voluntary follow-up after 3–7 days: still using? Changed/disabled? Why? | NOT RUN |

Ask neutrally; a friendly “looks great” is not usage evidence. Follow-up is self-report, not instrumented
retention. Installations, downloads and an enabled checkbox do not establish active use.

## Predeclared decisions

- Aim for at least **4/5 unassisted setups within two minutes** after opening Settings and **5/5 safe
  disable/restore attempts**. These are product acceptance targets, not statistical-significance thresholds.
- Pause promotion for unreadable interaction states, stale artifacts, lost preferences, unload failures or
  reproducible responsiveness problems. Reproduce and fix those first; rerun affected automated/live checks.
- If fewer than 4/5 understand drafts versus Apply or theme versus glow, improve the wording/guide and repeat
  the task with new participants before expanding the feature set.
- Treat optional advanced controls as successful if users who need them find them; do not require every user
  to use every feature. If a control repeatedly confuses people and adds no demonstrated value, simplify it.
- Report follow-up answers and reasons as raw small-sample observations. No usage feedback means adoption is
  **unknown**, not a successful launch. Do not infer a conversion rate or guarantee from this trial.

Technical evidence: [quality checklist](theme-quality.md). First-run instructions: [README](../README.md#quick-start).
Release promotion remains subject to [campaign launch gates](marketing/campaign.md), genuine media and owner approval.