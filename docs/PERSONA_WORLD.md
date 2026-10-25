# Persona world — Stage 4A-1

## Personas

This is hidden synthetic ground truth, not a learned model or measured user behavior. The independent generator must never import twin code. Stage 4A-1 implements definitions, a strict dependency-free JSON reader, loader and bundled catalog; generation, outcomes, replay and calibration are not implemented yet. No metrics are tuned to the pitch.

`PersonaLoader.load(json, defaults?)` accepts a String independently of Android assets or disk access. `PersonaCatalog.byName("Rahul"|"Anika"|"Kabir")` bundles exact Stage 1 JSON, validated on every load. Tests compare every source field with the real files. Read-only collections are defensively copied and frozen. Syntax errors reject the document; semantic validation collects all independent errors. Errors name fields and constraints, never input contents.

PersonaDef retains role, assumed and notes as provenance. `pickupProb` is exactly the source table; `effectivePickupProb` combines source probabilities with explicitly assumed missing contexts. Assumptions cannot override a source key. Durations are lognormal parameters in ln-minutes; selfInitiatedPerHour is pickups/hour. Fatigue multiplies odds, not probabilities (application awaits generator).

| Persona | Pickup probabilities | Dwell mu/sigma | Resume mu/sigma | Fatigue | Peaks | Self/hour | Important sender aliases |
|---|---|---|---|---|---|---|---|
| Rahul | social_notif/coding .62; msg_known/coding .55; promo/coding .12 | social 2.6/.7; short_video 2.8/.9 | coding 2/.6 | 1,1.2,1.5 | 10,11,15,21 | 1.4 | mom,teammate_a |
| Anika | lecture_watch/studying .04; shorts/studying .78; social_notif/studying .58; msg_known/studying .38; promo/studying .08 | learning 3/.35; short_video 3.2/.85; social 2.3/.7 | studying 1.7/.6 | 1,1.15,1.4 | 21,22,23,0 | 1.2 | family_a,study_group_a |
| Kabir | social_notif/coding .1; msg_known/coding .12; promo/coding .03 | social 2/.7; short_video 2.3/.8; games 3.2/.6 | coding 1.5/.5; gaming .8/.4 | 1,1.2,1.5 | 10,15,22 | 4.5 | family_a,teammate_a |

Rahul matches spec 15.1, with added role=developer. Anika is role=student and all numbers are Stage 1 assumptions. Kabir is role=gamer-dev; only self rate 4.5 is specified by 15.2, all other numbers are assumed. Sender aliases are fictional inputs, never wire sender IDs; the later generator must hash them.

## Explicit A4-2 defaults (assumed, not calibrated)

- Common notification mix/hour: social_notif .6 (appCat social), msg_known .3 (communication), promo .2 (shopping). Each uses destination social/feed weight .6 or short_video/shorts weight .4. Destination is the distraction category, not necessarily the notifying app. This deliberately uses available dwell distributions; it is not evidence that a message causes social browsing.
- Anika additionally has lecture_watch .2/hour -> learning/watch weight1, and shorts .4/hour -> short_video/shorts weight1.
- Rahul/Kabir coding blocks Monday–Friday 10–12 and 14–17. Anika studying blocks Monday–Friday 18–20 and 21–24. Kabir also gaming Monday–Sunday 20–23. Weekdays are ISO 1=Monday through 7=Sunday. Ranges are half-open local hours.
- Kabir gaming pickup additions: social_notif|gaming .05; msg_known|gaming .06; promo|gaming .015. These are visible in defaults.assumedPickupProb, not inserted into the original source table.
- Pickup latency ln-minutes: mu=-1.6, sigma=.5 for all personas. Fixed timezone Asia/Kolkata.
- Default seed1, startEpochMs=1767551400000 (2026-01-05 00:00 Asia/Kolkata), days14. Window30 minutes, glance .4 minutes, truncationfalse, MATCHED, ShowAll, phone/laptop skew0.
- Configuration validates finite quantities, positive window, nonnegative glance, both device keys, and days1..3660 as an explicit bounded configuration limit. RandomizedHold validates eps and maxImportance in [0,1]. Policy execution is a later milestone.

The owner approved proceeding with documented assumptions on 2026-10-25; the new numeric defaults above are implementation assumptions for review, not separately owner-confirmed measurements. Work outside scheduled blocks, screen behavior, sender/importance selection and fatigue inputs remain later world-design decisions. A4-3 late/peak modulation will compose multiplicatively. Hour-crossing hazard and overlapping trial handling still need explicit implementation notes in 4A-2.

## Validation and limitations

Loader rejects invalid probability, nonfinite mu, nonpositive sigma, missing class/task probabilities, missing resume/destination distributions, invalid weights/rates, noninteger hours, invalid schedules, duplicate senders/hours, unknown JSON fields and attempted source overrides. The reader rejects duplicate keys, malformed numbers, nonfinite overflow, invalid escapes/surrogates, trailing data, excessive size (>1 Mi characters) and nesting (>64).

The generator-specific fatigue minimum is 1; Stage 1's broader schema permits positive values below1. Existing fixtures satisfy both; no shared schema is changed. Bundled fixtures must stay synchronized with Stage 1 files, enforced by tests. No Android, Room, twin dependency or new library is introduced. Nothing is verified on target.
