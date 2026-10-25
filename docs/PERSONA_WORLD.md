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

## Trial rules (4A-3)

Each eligible notification (not inSession, not in an overlapping window) triggers one TRIAL. All 9 draws happen unconditionally in fixed order regardless of branch, so costs are always computable:

| Draw index | Variable | Purpose |
|---|---|---|
| 1 | uSelf | Baseline inter-arrival uniform |
| 2 | destSelf | Self-initiated destination selector |
| 3,4 | zDself, zRself | Dwell/resume standard normals for self-initiated |
| 5 | uPick | Pickup Bernoulli |
| 6 | uLatency | Pickup latency uniform |
| 7 | destNotif | Notification destination selector |
| 8,9 | zDnotif, zRnotif | Dwell/resume standard normals for notification-triggered |

tSelf = -ln(1-uSelf)/(lambda/60). Baseline exists iff tSelf < W. pPickup adjusted for fatigue via odds multiplier (A4-4). Owner: earliest event wins, tie to notification. lShow = cGlance + D_owner + R_owner. lSuppress = D_self + R_self if baseline exists else 0. deltaL = lShow - lSuppress (can be negative when baseline cost exceeds notification episode cost).

## Truth fields (4A-3)

`TruthRecord` contains: persona, dayIndex, notificationIndex, ts, context (taskType, hourBand, fatigueBand, hoursWorked, notifClass), inSession, trialEligible, exclusionReason, trial (all 9 draws plus derived fields), realizedArm (SHOW|SUPPRESS|null), propensity, eligibleForExploration, world.

`TrialDraws` contains: the 9 raw draws, lambdaPerHour, pPickup (fatigue-adjusted, drift-applied), selfDelayMin, notifDelayMin, baselineExists, notifEventExists, owner, lShowMin, lSuppressMin. deltaLMin = lShowMin - lSuppressMin.

## Worlds (4A-3)

**MATCHED**: draws follow the same competing-risk structure as the twin contract. Parameter recovery from these fixtures tests estimator consistency, not real-world validity.

**MILD_MISSPEC**: pPickup multiplied by a deterministic per-day drift in [0.85, 1.15] (seeded). 10% of dwell draws use mu+ln(3) (heavy tail). The truth records say which world made them. Neither world is validated against real users.

## Logging policies (4A-3)

**ShowAll**: all eligible notifications shown; pi=1.0; arm=SHOW. Matches observe-only reality.

**RandomizedHold(eps, maxImportance)**: eligible if not inSession, not important sender, urgencyScore < maxImportance. If eligible, hold with probability eps (SUPPRESS arm, pi=eps) else SHOW (pi=1-eps). Policy draws use a separate RNG fork (tag=500) so toggling policies does not change world draws. Held notifications emit notif_removed(reason=held).

## What this does not validate (4A-3)

- Generator parameters are not fitted to real users. Recovering them from synthetic fixtures tests the estimator, not the model.
- The MATCHED world circularity: the generator and evaluator share structure. Real user behavior may not follow this structure.
- No edit_burst, declared_session, or gap events in 4A.
- Persona arrival rates and destination weights are explicit assumptions (A4-2), not calibrated.
- No hardware validation; not run on Reno 14.

## Replay and temporal isolation (4A-5)

**tsAdj**: ts + clockOffset(device). Events are sorted by (tsAdj, device, seq). Deduplication by (device, seq) removes exact duplicates. Out-of-order events within `outOfOrderToleranceMs` are re-ordered; beyond tolerance they are delivered in arrival order and counted as `lateEvents`.

**t0 semantics**: GuardedEventLog.eventsBefore/between/latestBefore all enforce `upperBound <= t0` (strictly less than). Any attempt with `upperBound > t0` throws TemporalLeakException immediately, never leaking event content. `latestBefore(t0)` never returns an event with tsAdj >= t0. Training-side code cannot reach holdout events before `sealTraining()`.

**runUntil(t0)**: delivers only events with tsAdj < t0 (strictly less). An event exactly at t0 is excluded.

**FastForward**: generates the full history for a persona and replays it through ReplayDriver. Does not know about the twin; the sink decides what to do with events.
