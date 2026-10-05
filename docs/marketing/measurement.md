# Neon Glow pilot scorecard and decision runbook

**Template, not campaign results.** No publication, ad delivery, paid placement or spending has occurred as part
of this work. Use `PENDING` for missing setup and `N/A` for unavailable metrics, not invented zeroes. This pilot
adds no plugin telemetry, fingerprints, ad pixels or user-level tracking. Feedback is voluntary.

Start with [campaign gates](campaign.md#launch-gates), [assets](creative.md) and the
[distribution/approval record](distribution.md#owner-authorization-record).

## Funnel and evidence boundaries

**Channel impressions → channel clicks → reported listing visitors → aggregate plugin downloads.**
These are related aggregate observations, not one joined user-level funnel. Installation is not activation;
downloads are not active users. Do not report retention, productivity improvements or sales ROI from this pilot.

Current [Marketplace analytics documentation](https://plugins.jetbrains.com/docs/marketplace/analytics-tab.html),
checked 2026-10-04, describes the following owner dashboard metrics. Access to this plugin's actual dashboard
is **not verified**; the public listing and vendor permissions remain gates.

| Available documented view | What to record | Interpretation limit |
|---|---|---|
| Download summary and Plugin Downloads | Period/product filters, total vs unique mode, daily/monthly counts as available | Default uniqueness is Marketplace's OS-bound UUID, shared across IDEs on a computer—not people, paying customers or active installations |
| Downloads by product | Product and window actually shown; note all-time-only views | Do not compare an all-time product count to a weekly campaign delta |
| Plugin page visitors | Reported unique page users and exact displayed period | Periods/uniqueness differ from ad click counts; visits are not installs |
| Referrals and geography | Referring site/source/country breakdown actually available | No promise of full UTM breakdown or user-level click→download join; direct/IDE visits may lack campaign attribution |
| Sales summary, totals; Sales tab details | **Future only:** actual paid-product reports and purchase evidence | Not an available campaign outcome until commercial packaging/licensing and purchases exist |

Download processing latency is documented as **24 hours**; log collection time and exclude incomplete recent
windows. Reported uniques can span multiple IDEs, and multiple computers can represent one person. Do not collect
or export identifiers to reconstruct individuals. Keep only aggregate reports and non-sensitive feedback summaries.

## Campaign-link conventions

Use lower-case, stable values; no names, emails, licence IDs, project paths or per-person identifiers in tags.
Campaign: `utm_campaign=neon_glow_launch`. Maintain a link ledger and actual published URL for each placement.

| Placement | `utm_source` | `utm_medium` | `utm_content` |
|---|---|---|---|
| Founder demo on an existing owned account | `owned` (or actual platform, recorded consistently) | `organic_social` | `founder_demo` |
| GitHub installation walkthrough | `github` | `referral` | `install_guide` |
| Permitted Reddit community post | `reddit` | `community` | `intellij_demo` or `kotlin_walkthrough` |
| Optional Reddit creative A | `reddit` | `paid_social` | `transformation_a` |
| Optional Reddit creative B | `reddit` | `paid_social` | `control_b` |
| Creator placement | Stable channel alias, e.g. `creator_01` | `sponsored_content` or `creator_organic` as actually agreed | `hands_on_demo` |

Syntax illustration only, **not a live destination**:

```text
[VERIFIED_DESTINATION]?utm_source=reddit&utm_medium=paid_social&utm_campaign=neon_glow_launch&utm_content=control_b
```

Append `?` only when no query exists; otherwise append `&`. Preserve existing parameters, add before any URL
fragment, percent-encode values and check the final URL. Do not invent a Marketplace path, shorten links through
an unapproved tracker or build a new tracking website. A platform may reject/strip tags; use its accepted URL
and record attribution as limited rather than buying traffic on an untested link.

### Link verification ledger

| Placement / creative | Approved base destination | Final tagged URL | Logged-out click test/date; redirects/query observed | Dashboard attribution observable? | Approver / status |
|---|---|---|---|---|---|
| Owned founder | PENDING | PENDING | PENDING | PENDING | BLOCKED |
| Community post | PENDING | PENDING | PENDING | PENDING | BLOCKED |
| Reddit A | PENDING | PENDING | PENDING | PENDING | BLOCKED |
| Reddit B | PENDING | PENDING | PENDING | PENDING | BLOCKED |
| Creator | PENDING | PENDING | PENDING | PENDING | BLOCKED |

Open each final link in the intended browser and logged-out/mobile context, follow all redirects, inspect
rendered guide/listing and verify the compatible installation path. Query preservation alone is **not** proof
that Marketplace reports those tags. Check available referrals/reports after a real approved publication;
label unobservable details N/A. Do not promise complete channel-to-install attribution.

## Scorecard: capture evidence, not estimates disguised as facts

Use the same timezone and explicit inclusive/exclusive period convention throughout; record currency, channel
definition, source report and collection timestamp. Save aggregates manually in this document or an owner-held
spreadsheet; no plugin instrumentation or automation is needed. Snapshot before launch, at 48 hours, weekly
and at day 30. Read source definitions: outbound link clicks and all engagements are not interchangeable.

### Channel/creative report (one row per non-overlapping period)

| Window / timezone; collected at | Channel / placement / creative / revision | All-in actual media spend + accrued commitments; currency | Impressions / outbound clicks + metric definition | Reported destination visitors + attribution evidence | Reported attributable downloads or N/A | Source report / limitations | Objections / next action |
|---|---|---|---|---|---|---|---|
| PENDING | PENDING | PENDING; nothing spent by this work | PENDING | PENDING | N/A unless report truly supports attribution | PENDING | PENDING |

If a channel cannot report impressions or clicks, record N/A. Do not label local manual link tests as audience
visits. Never distribute a global download increase across creatives by click share; report it separately below.

### Marketplace context (one global row per window, not repeated per creative)

| Window/product/filter and total-or-unique mode | Listing visitors as reported | Downloads as reported | Referrals/geography available | Collection time / processing completeness | Release/update dates and organic confounders | Source / limits |
|---|---|---|---|---|---|---|
| Prelaunch baseline: PENDING | PENDING | PENDING | PENDING | PENDING | PENDING | Dashboard access not verified |
| Pilot window: PENDING | PENDING | PENDING | PENDING | PENDING | PENDING | No campaign data yet |

Where a listing already exists, choose a comparable prelaunch window (e.g. the prior 7 days) with the same
filters/timezone and note release dates. A newly published listing may have no meaningful baseline: use N/A,
not “infinite growth.” Updates, organic discovery, seasonality and other posts confound before/after differences.
Do not add overlapping unique-visitor windows or platform click counts as if they were deduplicated people.

### Costs: keep media, commitments and production time separate

| Date / line | Approved ceiling | Actual media cash cost incl. fees/taxes | Accrued unpaid commitment | Production hours / task | Optional explicit hourly valuation (not media spend) | Evidence / owner |
|---|---|---|---|---|---|---|
| Reddit A+B | $100 planning; NOT AUTHORIZED | N/A: not run | None authorized | PENDING | PENDING | PENDING |
| One creator | Up to $200 planning; NOT AUTHORIZED | N/A: not commissioned | None authorized | PENDING | PENDING | PENDING |
| Organic/production | $0 media planning | N/A: not published | None authorized | PENDING | PENDING | PENDING |

Reconcile cash and accrued commitments before approving more; an unpaid creator invoice still consumes the
allowance. The two paid line ceilings and combined $300 ceiling include required fees/taxes. Production time
and an optional labour valuation must be visible separately, even when media spend is zero.

### Diagnostic calculations

- CTR = outbound clicks / impressions, only when both refer to the same placement/window and compatible
  definitions. Show raw numerator/denominator as well as percent; zero/unknown denominator means N/A.
- Media CPC = actual scoped media cost / reported outbound clicks. Zero clicks means “no clicks; CPC undefined,”
  not $0 CPC. Include fees/taxes and identify whether a report excludes them.
- Cost per reported destination visit = scoped media cost / comparable attributed visits **only** when the
  report genuinely supplies them. Ad clicks are not a substitute for listing visitors.
- Listing visitor/download trends can be shown side-by-side, but are **not** click-to-install conversion rates.
  No per-creative download conversion or cost-per-install without actual supporting attribution.
- A “qualified destination visit” is an operational audience-fit proxy (relevant approved placement + actual
  reported destination visit). We cannot identify individual IntelliJ users. Explain any source-filtering or
  estimates; exclude known test traffic only where observable. Roughly 100 such visits is a planning target,
  not statistical significance. If only clicks are available, report clicks and qualification uncertainty.

## Preflight: before any publication or spend

- [ ] Owner authorized exact copy/asset/channel/date; paid audience and all-in budget separately approved.
- [ ] Public registry destinations work logged out; installing a compatible release and configuring theme/glow
  succeeds; no unpublished availability or unimplemented paid/trial claims.
- [ ] Authentic screenshots/demo passed privacy, legibility, dimensions, caption and applied-settings checks.
- [ ] Experimental rendering/coverage/performance caveats present; Accessible is explicitly glow-off in its shot.
- [ ] Local community rules/contact permission/sponsor disclosure satisfied; no endorsement implication.
- [ ] Tagged URLs checked through redirects and accepted by channel; reporting scope and limitations recorded.
- [ ] Baseline/window/timezone/source definitions recorded; missing dashboard fields labelled N/A.
- [ ] Paid only: eligible audience, minimums, expansion controls, fees, daily/lifetime cap and stop/end date verified.

If any required item fails, do not publish/buy traffic. Current state: **BLOCKED** by captures, unverified public
destinations and absent owner authorization. Preflight sign-off does not certify untested visual states.

## 48-hour delivery check

At 48 hours after each approved publication or paid start:

1. Reopen every live destination and install/guide route; inspect the actual posted asset/caption/CTA and any
   mobile crop. Correct misleading content or broken links immediately; pause dependent paid delivery first.
2. Verify ad approval/delivery, selected audience/expansion and actual all-in spend against cap/end date.
   Check source timestamps; Marketplace's last 24 hours may be incomplete, not zero interest.
3. Record impressions/clicks and referrals/visitors/downloads actually visible, not inferred. Zero delivery may
   indicate approval/minimum/format issues; diagnose before changing targeting or budget.
4. Review readability/rendering/installation complaints and voluntary feedback. A material rendering problem
   blocks paid delivery while investigated; do not dismiss it merely because tests passed.
5. Record decision, owner and next check. Low early numbers alone do not justify scaling or declare a winner.

## Weekly review and pause/continue rules

Review around days 7, 14, 21 and 28 from planning start; also inspect each live placement after its first 48
hours. If launch was delayed, record actual live windows rather than forcing calendar-day comparability.

| Observation | Required response |
|---|---|
| Broken destination/install, misleading capture, material rendering complaint, unapproved audience or cap/end reached | Pause affected paid delivery immediately; stop misleading publication; log issue/owner; resume only after correction and renewed approval |
| Clicks but guide/configuration friction | Improve installation/theme-vs-glow explanation first; no more traffic spend until walkthrough succeeds |
| Low/no delivery | Check moderation/format/audience/minimums without raising ceiling; stay organic if constraints cannot be met |
| Sparse or unmatched A/B exposure | Report raw counts, conditions and uncertainty; “inconclusive,” not a forced winner |
| Comparable A/B exposure, relevant visits and useful feedback | Prefer promising wording directionally; keep spend bounded; no significance or ROI claim |
| Requests for quieter visuals | Use C organically; demonstrate Midnight with editor glow on and UI/icons off, or Accessible/master-glow-off; don't split tiny paid budget three ways |
| Healthy destinations/controls, budget available and no material issues | Continue only within existing approved window/cap; new scope/budget needs explicit approval |
| Weak evidence at day 30 | Retain organic distribution; change guide/creative or stop paid rather than scale |

Comparison discipline: same audience, objective, placement, time window and destination; record automatic
allocation and other changes. Show raw exposure and clicks for A/B, not percentages alone. No promised
acquisition benchmark, retention effect or revenue. Roughly 100 relevant visits may support direction, but
uneven exposures, confounders or uncertain attribution can still leave the result inconclusive.

## Day-30 report template

**Status:** Not run. Fill with real evidence; no illustrative numbers in actual result cells.

| Report section | Owner entry |
|---|---|
| Planned vs actual dates, approver, product/release, listing/guide destinations | PENDING |
| Completed gates; blockers/delays and what actually shipped | PENDING |
| Channels/creatives/audiences run; rules/disclosures and live links | PENDING |
| Media spend/fees/taxes and unpaid commitments against caps | PENDING |
| Production hours and optional labour valuation, separate | PENDING |
| Per-channel raw impressions/clicks; comparable attributed visits if available | PENDING |
| Marketplace window/filters/visitors/downloads; reporting lag and source evidence | PENDING |
| A/B direction and exposure differences; uncertainties/confounders | PENDING |
| Recurring voluntary objections and guide/creative fixes | PENDING |
| Material rendering/installation complaints and resolution | PENDING |
| Decision: continue organic / change / stop paid; owner rationale | PENDING |
| Next bounded action, required evidence and any new approval | PENDING |

Suggested conclusion syntax:

> During `[ACTUAL_WINDOW]`, `[CHANNEL]` reported `[IMPRESSIONS]` impressions and `[OUTBOUND_CLICKS]` clicks.
> `[VISIT_REPORT_OR_UNAVAILABLE]`. Marketplace reported `[GLOBAL_COUNTS + FILTERS]`; these are not per-creative
> installs or active users. Media cost was `[ACTUAL_COST]` and production time `[HOURS]`. The A/B result is
> `[DIRECTIONAL_OR_INCONCLUSIVE]` because `[EVIDENCE + LIMITS]`. We recommend `[CONTINUE_ORGANIC / CHANGE / STOP]`.
> `[NEXT_GATE_AND_OWNER]` must be resolved before additional distribution/spending.

No fabricated testimonials, inferred individual conversions or download-growth attribution. If the campaign
never launched, report that and its blockers; do not issue a “performance winner.”

## Future paid-product economics — gated, not an offer

Do not measure trials/purchases/refunds/renewals or call pilot media spend sales acquisition until commercial
packaging/licensing, price/trial/cancellation terms, actual purchase reports and defensible purchase attribution
exist. Owner approval and seller arrangements are separate future work; `plugin.xml` has no paid descriptor now.

The earlier **hypothetical $15/year** price and documented **15% Marketplace fee** imply approximately
**$15 × 0.85 = $12.75** first-year proceeds per sale **before refunds, taxes, support and other costs**. This
simple list-price example ignores discounts and real settlement adjustments; it is neither pricing nor ROI.
Leave room for those costs rather than assuming $12.75 is an acceptable CAC ceiling.

For a future real paid offering: report observed scoped acquisition cost / genuinely attributable new paid
customers, with numerator/denominator, attribution method and uncertain/unattributed purchases separated.
Zero or unknown customers means CAC undefined/N/A. Keep earlier audience-learning spend visible and do not
relabel it retroactively as proven customer acquisition. Report renewal/refund economics only after actual
observation; no speculative lifetime value to justify scaling and no guaranteed ROI.

Sources: [Marketplace analytics](https://plugins.jetbrains.com/docs/marketplace/analytics-tab.html),
[Marketplace revenue sharing/fees](https://plugins.jetbrains.com/docs/marketplace/revenue-sharing-and-fees.html).
Compatibility claims rely on the existing [quality evidence](../theme-quality.md); no build/test reruns are
needed for this documentation-only campaign. Full visual certification, availability and commercial licensing
are not established by this runbook.