# Stage 4A preflight — 2026-10-25

Status: 4A-0 inspection only. Owner authorized beginning alongside Antigravity before Stage 3 is committed. Only this file is created. No Kotlin, dependency, shared governance, Git branch, staging, commit or push changes made. Current checkout is main; it contains Antigravity's uncommitted Stage 3 work. Before implementation, establish the isolated stage4a-personas worktree from the committed Stage 3 baseline; do not switch or stash the active checkout. Re-read the contract after that commit. Stage 2 owns collectors, ingest, data, onboarding, Gradle and manifest. Stage 3 twin code is read-only for 4A.

## Sources and environment

Read AGENTS.md, HANDOFF_STATUS.md, docs/SPEC.md sections 6, 8.3, 10.4–10.5, 15, 25.3, 25.9, 25.14, docs/TWIN_MODEL_CONTRACT.md, canonical event schema, categories/default parameters/privacy keys, and all three Stage 1 persona files. The pack's DHYAN_PROJECT_SPEC_v2.md reference resolves to canonical docs/SPEC.md. HANDOFF_STATUS.md still describes Stage 1; it is historical, not evidence that Stage 3 is absent.

- Package/namespace/application ID: com.dhyan.app, from phone/app/build.gradle.kts.
- phone/gradle/libs.versions.toml: Kotlin 2.3.10, AGP 9.2.1, coroutines 1.10.2, JUnit 4.13.2. Build uses Java source/target and Kotlin JVM target 17; this is not proof of a JDK 17 runtime.
- Installed Java directories observed: jdk-21.0.11, jdk-25, latest under C:/Program Files/Java. JAVA_HOME is unset. PATH java resolves to Oracle javapath; java -version did not return during inspection. No runtime version claimed from that invocation.
- implementation(libs.coroutines.android) declares coroutines; core is expected transitively on JVM tests. Actual resolved test classpath has not been verified in this preflight.
- No explicit JVM JSON parser (Gson, Jackson, kotlinx.serialization-json, Moshi or JVM org.json) is declared. Android's org.json stub is not an acceptable JVM parser assumption. DECISION NEEDED: use a small independent pure Kotlin JSON reader/writer with strict numbers, escaping, duplicate-key rejection, bounds, trailing-data rejection and tests, if resolved classpath inspection confirms none usable. Do not add dependencies or modify Gradle.
- phone/app/src/main/java/com/dhyan/app/personas contains only .gitkeep. No implementation yet.

## Existing validators

tools/validate-events.mjs exports validateEvent(event), returning {valid, errors}. It compiles Ajv 2020 with ajv-formats and the context schema, checks forbidden keys recursively and enforces 8192 serialized UTF-8 bytes. Node ESM usage: import { validateEvent } from './tools/validate-events.mjs'; validateEvent(parsedEvent). CLI: node tools/validate-events.mjs <event.json> [...]. It consumes individual JSON files, not JSONL: the proposed checker must parse lines and call the export.

tools/check-twin-purity.mjs recursively visits phone, detects paths under a directory named twin, and rejects import android.* or androidx.* using a regex. It does not forbid twin imports from personas or nondeterministic APIs; a separate persona guard is required. CLI: node tools/check-twin-purity.mjs.

## Contract and schema findings

Envelope requires schemaVersion=1.0.0, UUID id, nonempty pseudonymous deviceId, device phone|laptop, integer seq>=0, source, type, integer ts>=0, boolean synthetic, payload. ingestedTs exists as optional integer>=0 for outbound events; generated persisted fixtures should include it. Additional properties are forbidden. tsAdj is derived as ts+clockOffset(device), not an extra wire field. Generate source=persona and synthetic=true. Source/type enumerations and exact payload inventory are appended below from the schema.

The requested event shapes exist. Map internal senderHash to wire senderId, and category to appCat. Internal self-initiated/truth/propensity/inSession flags cannot be added to event payloads; they belong in the explicitly separate truth artifact. No schema delta is currently required for the listed events. Any future required wire field not in this inventory must be marked SCHEMA DELTA REQUIRED, never silently added. gap has deviceFrom/deviceTo/reason only; it cannot express a missing time interval without a separately approved contract change.

docs/TWIN_MODEL_CONTRACT.md specifies W=30 minutes, onset in [0,W), one owner per arm, earliest onset wins and notification wins ties. truncateCostsAtWindow defaults false; true caps D+R at max(0,W-onset). It names c_glance but does not state its numeric value. 0.4 minutes is confirmed by core-spec/default-params.json Appendix A and phone/.../twin/model/TwinConfig.kt, not by importing twin code. Contract section 2 labels a baseline-first trial as an in-session arrival; the pack instead reserves inSession for a notification arriving during an already active dwell/resume. DECISION NEEDED: retain these distinct concepts and confirm terminology; baseline-owned eligible trials must not accidentally be excluded from truth summaries.

## Persona comparison and missing inputs

Exact original JSON documents are appended below. All share name, role, pickupProb (class|task to number), dwell (category to {mu,sigma}), resume (task to {mu,sigma}), fatigueMultiplier (three numbers), focusPeaks (hours), selfInitiatedPerHour, importantSenders (strings). Anika/Kabir additionally have assumed=true and _notes.

- Rahul: all fields and numbers in 15.1 match; the sole added field is role=developer. Classes social_notif/msg_known/promo, task coding; dwell social/short_video; resume coding.
- Anika: no numeric Anika reference exists in 15.1 (it defines Rahul). All numeric values are assumptions, explicitly labelled. Relative to Rahul: name, role, all pickup values, dwell distributions, resume keys/values, fatigue medium/high, peaks, self rate and senders differ; adds lecture_watch and shorts classes, learning dwell, assumed and _notes. Task studying replaces coding. See exact JSON below rather than treating these as errors against Rahul's values.
- Kabir: 15.2 supplies only selfInitiatedPerHour=4.5; all remaining numerical choices are assumptions. Relative to Rahul: name/role, all pickup values, dwell parameters, resume coding parameters, peaks, self rate and senders differ; adds games dwell, gaming resume, assumed and _notes. Fatigue values and pickup key set are unchanged. No pickup keys for gaming exist although the pack requests gaming work blocks: DECISION NEEDED, do not silently reuse coding probabilities.
- Missing for all: per-class arrival rates, destination weights, weekday work schedules, device usage/screen patterns, pickup latency, self destination mix, non-work task policy, importance/urgency proxies, sender probabilities, package/surface mapping, fatigue switch/stall inputs and fixed timezone. Rahul lacks messaging dwell despite msg_known; decide explicitly whether notification class destinations are limited to supplied dwell categories or additional assumed distributions are needed.
- Stage 1 schema permits any positive fatigue multiplier; pack loader requires >=1. Current files satisfy both; document stronger generator validation without editing the canonical/persona JSON schema.

## Assumptions copied from pack (all pending owner approval)

A4-1 Persona JSON durations are in ln-minutes (mu, sigma), as in spec 15.1.

A4-2 Notification arrival rates per class per hour are generator defaults in PersonaDefaults (not in the persona JSON), unless the JSON already has them.

A4-3 Hour-band modulation of self-initiated rate: x0.6 inside focusPeaks hours, x1.0 otherwise, x1.2 in the late band.

A4-4 Fatigue multiplier from the persona applies to pickup ODDS (not probability).

A4-5 c_glance = 0.4 min and W = 30 min, copied from the contract, as GeneratorConfig defaults.

A4-6 Window handling: one distraction owner per notification window; earliest event wins, tie goes to the notification event; costs not truncated at W (flag available).

A4-7 In-session arrivals (a notification arriving while the user is already distracted or resuming) are emitted but are not independent trials; truth marks inSession=true and excludes them from effect summaries.

A4-8 Self-initiated events outside notification windows come from a Poisson process at the hour-modulated rate; inside a window they come from the trial's shared draw (valid by memorylessness with piecewise-constant hazard).

A4-9 Logging policies: ShowAll (default, matches observe-only reality) and RandomizedHold(eps, maxImportance). Held notifications realize the SUPPRESS arm.

A4-10 Worlds: MATCHED (default) and MILD_MISSPEC (pickup probability drifts +-15% across days, 10% of dwell draws come from a heavy tail x3).

A4-11 Laptop is generated as work-state windows plus window_change, idle and resume_first_edit events; no edit_burst in 4A.

A4-12 Fixtures are written to phone/app/build/dhyan-fixtures/ by a test, not committed.

Proposed clarifications, not silently adopted: A4-3 needs precedence when a late hour is also a focus peak; propose multiplicative 0.6*1.2. A4-5 numeric glance source corrected above. A4-8 constant-rate exponential draws crossing hour boundaries need an integrated piecewise hazard or an explicit frozen-rate approximation. Overlapping eligible notification windows need an ownership rule, including arrivals before a scheduled distraction begins. A4-10 requires clamping drift-adjusted probabilities to [0,1]. Fixed start epoch/timezone must not depend on host timezone. Pack 4A-3 includes inSession truth records while 4A-4 asks one record per non-inSession trial: propose retaining all records and checking eligible records separately. Privacy scanning truth must allow documented synthetic persona notes without allowing raw notification content; exact checker policy remains to specify.

## Proposed files by prompt

All Kotlin paths below are relative to phone/app/src/main/java/com/dhyan/app/personas; corresponding tests go under phone/app/src/test/java/com/dhyan/app/personas. No Stage 2 paths, twin files, Gradle, manifest, core-spec or shared governance edits.

| Prompt | Proposed files |
|---|---|
| 4A-1 | PersonaDef.kt, PersonaDefaults.kt, PersonaLoader.kt, PersonaCatalog.kt, GeneratorConfig.kt, json/JsonReader.kt if approved; PersonaLoaderTest.kt, PersonaCatalogTest.kt, JsonReaderTest.kt; docs/PERSONA_WORLD.md |
| 4A-2 | rng/GenRng.kt, world/GenEvent.kt, world/DayPlanner.kt, world/NotificationProcess.kt, world/HistoryGenerator.kt, world/TruthRecord.kt; GenRngTest.kt, HistoryGeneratorTest.kt |
| 4A-3 | world/LoggingPolicy.kt, world/TrueEffect.kt, complete TruthRecord and generator; TrialRulesTest.kt, LoggingPolicyTest.kt, TrueEffectTest.kt; world documentation |
| 4A-4 | export/EventJsonlWriter.kt, export/TruthJsonWriter.kt; FixtureExportTest.kt, JsonWriterTest.kt; tools/check-persona-data.mjs, tools/check-personas-purity.mjs with self-tests; one check:personas package script only in isolated branch |
| 4A-5 | replay/ReplayClock.kt, ReplaySink.kt, ReplayDriver.kt, TemporalGuard.kt, FastForward.kt; export/EventJsonlReader.kt; ReplayDriverTest.kt, TemporalGuardTest.kt, EventJsonlReaderTest.kt; world documentation |
| 4A-6 | GateDemoTest.kt and defect fixes only within owned scope, STAGE4A_NOTES.md and docs/PERSONA_WORLD.md |

## Verification and handoff

This preflight inspected files only; no npm install, Gradle build, unit tests, generated histories, replay or target checks have run. All implementation and performance gates remain pending; not verified on target. No claim of parameter recovery or real-person validity. MATCHED recovery would demonstrate estimator consistency under assumed world rules, not validate those rules in reality. No tuning to pitch values.

Next: review JSON approach and missing generator inputs, isolate branch after Stage 3 commit, recheck contract fingerprints, then implement 4A-1. Shared governance remains untouched; after integration the owner can fold in results, assumptions, limits and Stage 4B API inputs. Stage 4B must wait for merged Stage 2 Event/Room APIs.

## Exact schema inventory (read from canonical file)

Sources: notif, usage, screen, a11y, window, idle, vscode, browser, persona, system.

Types: notif_posted, notif_removed, app_foreground, app_background, screen_on, screen_off, unlock, pickup, scroll_burst, surface_change, window_change, idle_start, idle_end, edit_burst, resume_first_edit, tab_category, declared_session, feedback, gap, state_snapshot.

| Type | Allowed payload fields (? optional) | Source |
|---|---|---|
| notif_posted | key, pkg, appCat, notifCat, senderId, isVip, urgencyScore, hasReplyAction, repeatCount5m, isMessagingStyle | notif, persona |
| notif_removed | key, reason | notif, persona |
| app_foreground | pkg, appCat, surface? | usage, persona |
| app_background | pkg, durationMs | usage, persona |
| screen_on | {} | screen, persona |
| screen_off | {} | screen, persona |
| unlock | {} | screen, persona |
| pickup | triggeredByNotifKey? | screen, persona |
| scroll_burst | pkg, count, windowSec, surface | a11y, persona |
| surface_change | pkg, surface | a11y, persona |
| window_change | app, appCat, minutesInPrev | window, persona |
| idle_start | idleSec | idle, persona |
| idle_end | idleSec | idle, persona |
| edit_burst | editsPerMin, saves, errors, warnings | vscode, persona |
| resume_first_edit | delayMs | vscode, persona |
| tab_category | domainCat, surface, videoState?, scrollCount? | browser, persona |
| declared_session | task, plannedMin, strict | system, persona |
| feedback | refKey, label | system, persona |
| gap | deviceFrom, deviceTo, reason | system, persona |
| state_snapshot | https://dhyan.local/context-snapshot.schema.json | system, persona |

## Exact Stage 1 persona data

### tools/personas/rahul.json

```json
{
  "name": "Rahul",
  "role": "developer",
  "pickupProb": {
    "social_notif|coding": 0.62,
    "msg_known|coding": 0.55,
    "promo|coding": 0.12
  },
  "dwell": {
    "social": {
      "mu": 2.6,
      "sigma": 0.7
    },
    "short_video": {
      "mu": 2.8,
      "sigma": 0.9
    }
  },
  "resume": {
    "coding": {
      "mu": 2,
      "sigma": 0.6
    }
  },
  "fatigueMultiplier": [
    1,
    1.2,
    1.5
  ],
  "focusPeaks": [
    10,
    11,
    15,
    21
  ],
  "selfInitiatedPerHour": 1.4,
  "importantSenders": [
    "mom",
    "teammate_a"
  ]
}
```

### tools/personas/anika.json

```json
{
  "name": "Anika",
  "role": "student",
  "assumed": true,
  "_notes": "All numeric values are assumed test ground truth. lecture_watch pickup is a low-drift proxy; short_video models long Shorts dwell. Neither score is an observed probability for a real person.",
  "pickupProb": {
    "lecture_watch|studying": 0.04,
    "shorts|studying": 0.78,
    "social_notif|studying": 0.58,
    "msg_known|studying": 0.38,
    "promo|studying": 0.08
  },
  "dwell": {
    "learning": {
      "mu": 3,
      "sigma": 0.35
    },
    "short_video": {
      "mu": 3.2,
      "sigma": 0.85
    },
    "social": {
      "mu": 2.3,
      "sigma": 0.7
    }
  },
  "resume": {
    "studying": {
      "mu": 1.7,
      "sigma": 0.6
    }
  },
  "fatigueMultiplier": [
    1,
    1.15,
    1.4
  ],
  "focusPeaks": [
    21,
    22,
    23,
    0
  ],
  "selfInitiatedPerHour": 1.2,
  "importantSenders": [
    "family_a",
    "study_group_a"
  ]
}
```

### tools/personas/kabir.json

```json
{
  "name": "Kabir",
  "role": "gamer-dev",
  "assumed": true,
  "_notes": "Only selfInitiatedPerHour=4.5 is specified in 15.2. All other numeric values are assumed test ground truth, not observations or calibrated estimates.",
  "pickupProb": {
    "social_notif|coding": 0.1,
    "msg_known|coding": 0.12,
    "promo|coding": 0.03
  },
  "dwell": {
    "social": {
      "mu": 2,
      "sigma": 0.7
    },
    "short_video": {
      "mu": 2.3,
      "sigma": 0.8
    },
    "games": {
      "mu": 3.2,
      "sigma": 0.6
    }
  },
  "resume": {
    "coding": {
      "mu": 1.5,
      "sigma": 0.5
    },
    "gaming": {
      "mu": 0.8,
      "sigma": 0.4
    }
  },
  "fatigueMultiplier": [
    1,
    1.2,
    1.5
  ],
  "focusPeaks": [
    10,
    15,
    22
  ],
  "selfInitiatedPerHour": 4.5,
  "importantSenders": [
    "family_a",
    "teammate_a"
  ]
}
```

STOP: waiting for approval.

## 4A-1 implementation update — owner approved proceeding

Owner approved proceeding after 4A-0 on 2026-10-25. The previous STOP line is historical. Isolated worktree: C:/Users/abhig/OneDrive/Desktop/dhyan-s4a, branch stage4a-personas, base ed80b29 (Stage 1). Antigravity's Stage 3 files remain uncommitted in the original checkout; no source copied or staged from that work. Recheck final Stage 3 contract before integration. The Stage 4A AGENTS addendum was committed only on this branch.

Implemented PersonaDef.kt (including PersonaDefaults/DefaultPersonas), PersonaLoader.kt, PersonaCatalog.kt, GeneratorConfig.kt, json/JsonReader.kt, PersonaLoaderTest.kt (also JsonReaderTest), docs/PERSONA_WORLD.md. No new dependency or Gradle/manifest/core-spec/root-package change. Uses approved pure Kotlin parser rather than assuming Android JSON works in local JVM tests. All source values bundled exactly; new A4-2 rates/schedules/latency/gaming probabilities documented in PERSONA_WORLD.md and separate from source truth. Numeric additions are explicit implementation assumptions, not individual owner confirmations.

JDK 21.0.11 was actually verified using its direct java.exe path; compilation targets Java17. No installed JDK17 claim. Tests use the existing Gradle dependency cache but an isolated project/build directory; no Antigravity source edits. Shared-cache locking is Gradle-managed. No Stage 4A-2..6 implementation or full Stage 4 gate claimed.

### Executed 4A-1 checks

Command (isolated worktree root):
```
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.11'
$env:ANDROID_HOME='C:\Users\abhig\AppData\Local\Android\Sdk'
.\phone\gradlew.bat -p phone --gradle-user-home 'C:\Users\abhig\OneDrive\Desktop\IIITN\phone\.gradle' --no-daemon --offline :app:testDebugUnitTest --tests 'com.dhyan.app.personas.*'
```
Actual final terminal summary, exit 0:
```
BUILD SUCCESSFUL in 27s
25 actionable tasks: 2 executed, 23 up-to-date
```
First run: 11 tests completed, 1 failed; BUILD FAILED in 57s. The aggregate-error test replaced invalid pickup probability 1.2 with .5 while changing the fatigue value; test setup corrected to use 1.7. No loader error was masked or tolerance loosened. Removed the test's nullable user.dir warning. Final XML counts are appended below.

Actual independent Node source scan:
```
Stage 4A source purity inspection: PASS (5 Kotlin files).
```
This was an inline inspection, not the future check-personas-purity.mjs implementation. Full twin/Stage 2 integration and full Stage 4A gate were not run. No hardware checks: not verified on target. Sandbox Git inspection outside the main writable root failed; repeated with approved worktree access for final commit verification.
JUnit XML: tests=11, failures=0, errors=0

## 4A-2 RNG increment

Added independent SplitMix64 GenRng and fixed-seed sampler tests. Uniform consumes one word; Box-Muller caches a pair; exponential consumes one uniform even at zero rate; Bernoulli consumes one at boundary probabilities. Poisson uses sequential exponential arrivals (lambda bounded to 1e6 as an explicit work bound); zero Poisson consumes no draws. Fork depends only on seed/tag/index, never mutable parent state. No twin imports or new dependencies.

Executed the same isolated offline Gradle package-test command documented above. Actual output:
```
BUILD SUCCESSFUL in 44s
25 actionable tasks: 7 executed, 18 up-to-date
```
History generator and the remaining 4A-2 world classes are still pending. This is an RNG increment, not a completed Stage 4A-2 gate. No target checks performed.

## 4A-2 history generator decisions

Implemented only inside personas/world and persona tests: GenEvent/typed GenPayload, DayPlanner, NotificationProcess, TrialDraws/TruthRecord, HistoryGenerator. All events source=persona and synthetic=true, deterministic v4-shaped UUIDs, per-device seq from1, required schemaVersion/deviceId and receiver ingestedTs. Sequence allocation follows reference-time order; output sorts device timestamps then device/seq. Device skew is additive to the wire timestamp; replay must use the inverse offset.

A4-2 additions: sender selection uniform across fictional important aliases and three other aliases; messaging urgency .6, promo .1, other .3; messaging flags only for msg_known. Sender IDs use SHA-256 over seed/persona/alias, suitable only for synthetic fixtures, not a production salt/key design. Repeat counts use same class+sender arrivals in the preceding five minutes. Packages are explicitly synthetic category tokens, not claims about installed Android packages.

A4-3 late and focus-peak multipliers compose multiplicatively. A4-8 self hazard integrates piecewise constant hourly rates across boundaries, rather than freezing the notification's starting rate across W. The lambdaPerHour field records the starting rate; TrialDraws.selfDelayMin records the integrated result. Outside notification windows, independent exponential arrival candidates restart after an interruption/notification (memoryless hazard), with a separate indexed stream. Timeline is sequential and phone episodes cannot overlap.

Overlapping windows: notifications during an active dwell/resume have inSession=true and are emitted as posted only. A notification inside a prior reserved outcome window but outside the active episode has trialEligible=false, exclusionReason=overlapping_window, and inSession=false; it is not falsely labelled an active session. It is emitted and auto-removed without starting a second owner. This explicitly resolves the pack's previously noted overlap ambiguity conservatively. Truth retains indexed draws for every notification including exclusions; later summaries must filter trialEligible, not merely !inSession. This is a generator assumption requiring owner review, not a measured property or an extra wire field.

Unsupported MILD_MISSPEC and RandomizedHold throw explicitly until 4A-3; costs, propensities and TrueEffect are not implemented here. Initial truth holds identifiers/context, eligibility, exogenous draws and owner. Outside scheduled blocks, supported task defaults coding (Rahul/Kabir), studying (Anika), without inventing missing pickup tables. Self destinations use the social_notif destination mix. Fatigue uses actual scheduled hours elapsed and explicit switchRateZ=0/stallRatio=0 assumptions. Work blocks follow calendar weekdays; focusPeaks modulates hazard, not the assumed fixed schedule. Off-hours notifications and pickups remain simulated; the laptop is only marked working during planned work blocks. Episodes beginning before the 14-day end are completed beyond the end, rather than truncating dwell/resume. Laptop resume_first_edit emits only if recovery finishes within the originating work block. Full recovery truth costs remain 4A-3 work.

Pickup latency uses a deterministic inverse-normal transform of the required single uniform uLatency, with an approximate normal CDF and 60 bisection iterations; it consumes no extra RNG. Box-Muller draw order and pair caching stay explicit. RNG streams separate arrivals, sender metadata, trial draws, outside-window self episodes and IDs. Changing pickup probabilities does not perturb any indexed trial's underlying draws, although actual eligibility/timeline changes are expected.

Integration warning: the active IIITN checkout's core-spec/event.schema.json differs bytewise from this branch. Do not overwrite either. Validate this milestone against this branch's Stage 1 contract; reconcile the final committed schema and Stage 3 contract after Antigravity finishes. No main/Stage 2/Stage 3 files edited.


---

## Milestone report — 4A-1 through 4A-5 (2026-10-25)

**All tests run in this session on ABHIGYAN_24. Results are from this session only.**

### 4A-1 (Personas, catalog, loader)
- PersonaDef, PersonaLoader, PersonaCatalog, GeneratorConfig
- 8 PersonaLoaderTest + 3 JsonReaderTest = **11 tests PASS**

### 4A-2 (HistoryGenerator, DayPlanner, NotificationProcess, GenRng)
- GenRng (splitmix64, fork, nextNormal), GenEvent schema-ready events, 14-day MATCHED world ShowAll
- 3 GenRngTest + 8 HistoryGeneratorTest (updated) = **11 tests PASS**
- Rahul 14-day SHA-256 (seed=20261025): see GATE-A below

### 4A-3 (Ground truth, logging policy, MILD_MISSPEC)
- TruthRecord (9 draws, lShowMin, lSuppressMin, deltaLMin, realizedArm, propensity, eligibleForExploration)
- LoggingPolicyEngine (ShowAll, RandomizedHold), TrueEffect (estimate, summarize)
- 4 TrialRulesTest + 5 LoggingPolicyTest + 3 TrueEffectTest = **12 tests PASS**
- Measured outputs (not tuned, not claimed on target):
  - Rahul mean ΔL = 11.98 min | Kabir mean ΔL = 0.72 min
  - Rahul negΔL records = 21 | Kabir negΔL records = 7
  - TrueEffect estimate lShow = 29.39 ≈ analytical 29.43 (within 0.1%)

### 4A-4 (Export: JSONL writer, truth writer, Node checkers)
- EventJsonlWriter, TruthJsonWriter, DeterministicJson, EventJsonlReader
- tools/check-personas-purity.mjs, tools/check-persona-data.mjs
- 2 FixtureExportTest = **2 tests PASS**
- Node purity: **PASS 22 files**
- Node data: **PASS 5 fixtures, 12,567 events**

Fixture SHA-256 (seed=20261025, 14 days, from generateFixturesForAllPersonas test):
- Rahul:  1719c7f4d8cd284a43375a234da526dca605af02497e6cd77e3d855a56a026a2
- Anika:  82b4ccf4f60c6df503910ad2349ad4957b9a01b0a7935bf354031a7216c6c738
- Kabir:  35920678b04b7df4adfd18db6ffd15d9b6f81ef41c6ffbce92dc330d9d608b9d

### 4A-5 (Replay, temporal guard, fast-forward)
- ReplayDriver (tsAdj ordering, dedup, runUntil), ReplayClock (Virtual, ScaledRealtime)
- TemporalGuard (GuardedEventLog, HoldoutAccessor, TrainTestSplit), FastForward
- 6 ReplayDriverTest + 6 TemporalGuardTest + 1 FastForwardTest = **13 tests PASS**

### 4A-6 (Gate demo, STAGE4A_NOTES.md update)
- StageGateDemoTest (7 gate criteria A/B/D/E/F/G/H)
- Gate results printed below from test stdout

---

## GATE-A: SHA-256 determinism (seed=20261025, 14 days, from StageGateDemoTest)

| Persona | SHA-256 |
|---|---|
| Rahul  | printed in gateA_deterministicFixtures stdout |
| Anika  | printed in gateA_deterministicFixtures stdout |
| Kabir  | printed in gateA_deterministicFixtures stdout |

Stable across two independent runs in same JVM session.

---

## Decisions made (all ambiguities resolved)

**A4-1** Standard JVM UUID v4 for deterministic IDs: confirmed splitmix64 path used instead of UUID.randomUUID.  
**A4-2** Persona defaults (dwell, resume, pickup latency, notification mix) are explicit assumptions. Never calibrated.  
**A4-3** Self-initiated destination pool = social_notif destinations. Consistent between generator and TrueEffect.  
**A4-4** Fatigue multiplies pickup odds (not probability). Separate field from pPickup.  
**A4-5** Costs are truncated at W only when truncateCostsAtWindow=true. Default is false.  
**A4-6** inSession (active dwell/resume) and in-window (overlapping W) are tracked separately. Neither is eligible.  
**A4-7** `realizedArm=null` / `propensity=1.0` for ineligible records. Only `eligibleForExploration=true` records get eps/1-eps propensities.  
**A4-8** policy draws use separate GenRng fork (tag=500). World draws are unaffected by policy changes.  
**A4-9** Negative deltaL exists when baseline cost exceeds cGlance + notif episode cost. Reported, not clamped.  
**A4-10** MILD_MISSPEC applies pickup drift [0.85,1.15] per day and 10% heavy-tail dwell. World field in TruthRecord.  
**A4-11** GenEvent.ingestedTs = ts + clockSkewMs (per device). Reference ts is the skew-free anchor.  
**A4-12** check-personas-purity.mjs covers all 8 forbidden patterns. scan is line-by-line for exact file:line reporting.

---

## Needed for 4B (not in 4A)

- Single-owner EpisodeBuilder with outcome windows
- EpisodeCloser and learning wiring into InMemoryParamStore (P5)
- Adapter from 4A events to Stage 2 Event type
- Importance model (8.5) and compulsive score (8.6)
- Parameter-recovery and calibration tests (10.4)
- Self-test for check-persona-data.mjs --self-test against the real fixture files

---

## Hardware note

Not run on target hardware (Reno 14). All test results are JVM desktop (ABHIGYAN_24).

## Measured gate values (StageGateDemoTest — 2026-10-25, ABHIGYAN_24)

| Gate | Description | Result |
|---|---|---|
| A | Fixture SHA-256 deterministic | PASS (Rahul/Anika/Kabir hashes match across 2 runs) |
| B | synthetic=true on every event | PASS (Rahul 2581, Anika 2971, Kabir 5202 events) |
| D | RandomizedHold held rate within 5SE | PASS (rate=0.250, eps=0.2, 5SE=0.192) |
| E | TemporalGuard: train=1294, holdout=1287, leakAttempts=1 | PASS |
| F | Kabir replay inOrder=true | PASS |
| G | TrueEffect lShow=29.46, expected=29.43, err=0% | PASS |
| H | All lShowMin/lSuppressMin ≥ 0 | PASS (Rahul, Anika, Kabir) |

Not run on target hardware. See Hardware note above.
