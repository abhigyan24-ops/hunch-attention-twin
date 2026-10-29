# DHYAN: Stage 4A (personas, history generator, replay, temporal guard), Codex pack

Spec sources: 15 (personas and history generator), 10.4 (validation on synthetic personas), 10.5 (temporal isolation), 15.5 (fast-forward), 17.2 Stage 4 (first two bullets), 25.3 (provenance), 25.9 (replay fixtures), 25.14.

## What 4A is, and what waits for 4B

| 4A (this pack, can run beside Stage 2) | 4B (after Stage 2 is merged) |
|---|---|
| `PersonaDef`, catalog, loader, validation | Single-owner `EpisodeBuilder`, outcome windows, `EpisodeCloser` |
| `HistoryGenerator`: 14 days of schema events with `synthetic:true` | Learning wiring: episodes into `InMemoryParamStore` (P5) |
| Hidden ground truth: true SHOW and SUPPRESS outcome per notification | Importance model (8.5), compulsive score (8.6) |
| Logging policy (show-all, or randomized hold) with exact propensities | Parameter-recovery and calibration tests (10.4) |
| JSONL export and a Node checker | Adapter from 4A events to Stage 2's `Event` |
| `ReplayDriver`, clocks, dedup, skew handling, `FastForward` | The "two notifications 3 minutes apart" test |
| `TemporalGuard` that fails on any future read (10.5) | Stage 4 exit check: replay recovers `p_pickup` within 0.08 |

Stage 4A never touches the twin, the collectors, Room, or the UI.

**Spec gate for 4A (my wording):** a seeded 14-day history for each of Rahul, Anika and Kabir is generated deterministically, passes the schema validator and privacy scan, carries true counterfactual outcomes, replays at 100x with no duplicates, and the temporal guard fails on a deliberate future read.

---

## 1. Honest limits to keep in mind (put these in the notes and the pitch)

1. **The generator knows the truth, which is the point, but it is not reality.** In the `MATCHED` world the generator follows the same competing-risk structure as the twin contract (`docs/TWIN_MODEL_CONTRACT.md`). Recovering parameters there shows the estimator is consistent. It does not show the model is right about real people. The `MILD_MISSPEC` world adds a drift and a heavy dwell tail to check the twin does not fall apart when the model is slightly wrong.
2. **Do not tune personas to hit the spec's pitch numbers** (24 min, 0.62, 0.09). Print what the world produces. Spec 25.3 requires headline numbers to come from the same visible fixture.
3. **The generator must not import `twin.*`.** Sharing code with the model under test makes validation circular. It gets its own small RNG and samplers.
4. Synthetic data is always labelled `synthetic: true` and must never be mixed with real logs in any chart without a label.

---

## 2. Prerequisites (you, about 10 minutes)

1. **Stage 3 is merged on `main`.** Stage 2 does not have to be.
2. **Worktree:**
   ```
   git worktree add ..\dhyan-s4a -b stage4a-personas
   ```
   Copy `phone\local.properties` into the worktree's `phone\`, run `npm install`, confirm `java -version` is 17, and run `.\gradlew.bat :app:testDebugUnitTest` once before starting Codex.
3. **File ownership while Stage 2 runs** (do not let either agent cross these lines):

   | Path | Stage 2 | Stage 4A |
   |---|---|---|
   | `phone/.../collectors,ingest,data,ui/onboarding` | owns | forbidden |
   | `phone/.../personas/**` (main and test) | forbidden | owns |
   | `phone/.../twin/**` | read-only | read-only |
   | `AndroidManifest.xml`, Gradle files | owns | forbidden |
   | `core-spec/` | forbidden | forbidden (read-only) |
   | root `package.json` | one key | one key (`check:personas`), merge by hand |
   | `tools/` | `check-phone-contract.mjs` | `check-persona-data.mjs`, `check-personas-purity.mjs` |

4. **Commit the AGENTS.md addendum** below on the `stage4a-personas` branch.
5. **Codex mode:** run each prompt in order, review the diff, run the commands yourself, then move on. One prompt per Codex task.

## 3. AGENTS.md addendum

```text
## Stage 4A rules (personas, generator, replay)
- Branch: stage4a-personas. Commit after each prompt ("stage4a: <name>"). Never push. Never touch main.
- OWNED: phone/app/src/main/java/com/dhyan/app/personas/**, phone/app/src/test/java/com/dhyan/app/personas/**, tools/check-persona-data.mjs, tools/check-personas-purity.mjs, docs/PERSONA_WORLD.md, STAGE4A_NOTES.md, one script key "check:personas" in root package.json.
- FORBIDDEN: core-spec/ (read-only), twin/** (do not import it), collectors/, ingest/, data/, ui/, sync/, safety/, actuator/, fusion/, inference/, Gradle files, AndroidManifest.xml, Koin/Hilt modules, and shared governance docs (TRACEABILITY.md, HANDOFF_STATUS.md, STAGE_GATES.md, DECISIONS_PENDING.md). Report needed changes in STAGE4A_NOTES.md.
- personas/ is pure Kotlin: no android.*, androidx.*, no Room, no twin.* imports. kotlinx.coroutines is allowed. No new dependencies.
- Determinism: all randomness from personas/rng/GenRng (splitmix64). Never Math.random, kotlin.random.Random.Default, UUID.randomUUID, System.currentTimeMillis or System.nanoTime for anything that affects output. Event IDs and timestamps derive from the seed and a fixed start epoch.
- Units: durations in minutes, hazard per hour. Name variables with the unit.
- Never hard-code results to match the pitch numbers. Never claim a test passed without running it and pasting the summary line.
- Synthetic output always has synthetic:true. No raw text, titles or URLs anywhere.
```

---

## 4. Prompts

### Prompt 4A-0: pre-flight (read-only report)

```text
Read AGENTS.md, DHYAN_PROJECT_SPEC_v2.md sections 6, 10.4, 10.5, 15, 25.3, 25.9, docs/TWIN_MODEL_CONTRACT.md, core-spec/, and the persona files from Stage 1. Do not edit anything except creating STAGE4A_NOTES.md.

Report with file paths:
1. Package root (expected com.dhyan.app), JDK, Kotlin version, whether kotlinx.coroutines and a JSON library usable from JVM unit tests are on the test classpath (check build.gradle(.kts) and version catalog; do NOT edit them).
2. Where the three persona JSON files are, and for each: the exact field names and structure. Compare to spec 15.1 and list every difference. List the notification classes and task types they cover, and any fields the generator will need that are MISSING (for example notification arrival rates per class per hour, work-block schedule, device usage pattern).
3. The core-spec event schema: envelope fields, source and type enums, payload fields for notif_posted, notif_removed, pickup, screen_on, screen_off, unlock, app_foreground, app_background, scroll_burst, surface_change, window_change, idle_start, idle_end, resume_first_edit, gap. Say whether schemaVersion and an ingestion timestamp exist.
4. What validator already exists in tools/ (schema validator from Stage 1) and how to call it from Node. Whether tools/check-twin-purity.mjs exists and how it works.
5. What constants in docs/TWIN_MODEL_CONTRACT.md the generator must mirror for the MATCHED world: window W, c_glance, single-owner rule, tie rule, truncation flag.
6. Whether personas/ already contains files.

Then write STAGE4A_NOTES.md with these findings, a proposed list of files per prompt 4A-1..4A-6, and the numbered assumptions A4-1..A4-12 from the pack (copy them from the list below) with any you would change. If the JSON library is missing, propose a small tested JSON reader and mark it DECISION NEEDED. If any schema field Stage 4A needs is missing, mark SCHEMA DELTA REQUIRED and do not work around it.
End with: "STOP: waiting for approval." Do not write Kotlin yet.

Assumptions list to copy:
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
```

### Prompt 4A-1: PersonaDef, loader, catalog

```text
Implement Stage 4A step 1 in phone/app/src/main/java/com/dhyan/app/personas/ (verify the real package root from STAGE4A_NOTES.md). Follow AGENTS.md. Only files under personas/ and its tests.

Create:
- PersonaDef.kt: immutable data classes holding the hidden truth per spec 15.1: name, pickupProb (map notifClass|taskType -> Double), dwell (category -> LogNormalSpec(mu, sigma) in ln-minutes), resume (taskType -> LogNormalSpec), fatigueMultiplier (3 values, odds multipliers), focusPeaks (hours), selfInitiatedPerHour, importantSenders. Add what the generator needs that the JSON lacks, as PersonaDefaults with the numbers marked as assumptions A4-2: notification mix (class, appCat, rate per hour, destination weights), laptop work pattern (work blocks as local hour ranges per weekday), phone destination table (category, surface) per notification app class, pickup latency LogNormalSpec, work task type per block (coding for Rahul, studying for Anika, mixed coding/gaming for Kabir).
- PersonaLoader.kt: parse the Stage 1 persona JSON files using the JSON approach approved in STAGE4A_NOTES.md. Validate: all probabilities in [0,1], sigma > 0, mu finite, fatigueMultiplier has 3 entries >= 1, focusPeaks in 0..23, required pickupProb keys present for every notification class in the mix. Collect ALL validation errors and throw one PersonaValidationException listing them; never "fix" values silently.
- PersonaCatalog.kt: loads the three personas from classpath test resources or from an in-code builder; provides byName("Rahul"|"Anika"|"Kabir"). The persona data must be bundled so the on-phone fast-forward works later; if resource loading from Android assets is needed later, keep the loader independent of how bytes arrive (takes a String).
- GeneratorConfig.kt: seed, startEpochMs (fixed default, e.g. 2026-01-05T00:00:00 local as epoch ms, a constant), days (14), windowMin = 30.0, cGlanceMin = 0.4, truncateCostsAtWindow = false, world = MATCHED | MILD_MISSPEC, loggingPolicy = ShowAll | RandomizedHold(eps, maxImportance), device clock skew per device default 0.
- docs/PERSONA_WORLD.md section "Personas": list each persona's parameters and which values are assumptions.

Tests: loader accepts the real three files; each invalid-field case produces the right error and all errors are reported together; catalog returns three personas; Kabir's selfInitiatedPerHour is higher than Rahul's (read from data, not hard-coded).
Run: cd phone; .\gradlew.bat :app:testDebugUnitTest --tests "com.dhyan.app.personas.*". Paste the summary line. Commit "stage4a: persona defs and loader". Report anything not run.
```

### Prompt 4A-2: RNG and the history generator (events only)

```text
Implement Stage 4A step 2: deterministic event generation. Only files under personas/ and tests. No twin.* imports.

Create personas/rng/GenRng.kt: splitmix64 seeding, xoshiro256** (or splitmix64 alone if simpler), nextDouble() in [0,1), nextNormal() (polar or Box-Muller with a cached pair; document draw counts), nextExp(rate), nextPoisson(lambda) (use sequential exponential inter-arrivals, not a rejection method with variable consumption across arms), fork(tag: Long, index: Long): GenRng deriving an independent stream from (seed, tag, index) via splitmix64 mixing. Samplers: logNormal(mu, sigma), bernoulli(p), categorical(weights). Unit tests: same seed same sequence, forks independent, mean and variance checks for normal and exp on several FIXED seeds (do not choose seeds to pass), categorical frequencies.

Create personas/world/ with:
- GenEvent.kt: the envelope per core-spec (id, ts, device, seq, source, type, payload as a typed or map structure with allow-listed keys, synthetic = true, plus schemaVersion and ingestion timestamp if the schema has them). IDs are deterministic UUID-v4-shaped strings built from GenRng, never UUID.randomUUID. seq is monotonic per device starting at 1. A payload builder per event type uses an allow-list; unknown keys are impossible by construction.
- DayPlanner.kt: for a persona and day index, plan laptop work blocks from the persona's work pattern and focusPeaks, with taskType per block; apply the fatigue index inputs (hoursWorked so far, switch rate proxy, stall ratio proxy) to choose fatigueBand 0,1,2 using spec 8.3 thresholds (0.33 / 0.66), and hourBand per spec 8.3.
- NotificationProcess.kt: arrival times for each notification class as a Poisson process at the persona's rate per hour (assumption A4-2); each arrival has class, appCat, senderHash (derived from the persona's sender list; important senders flagged), urgencyScore, hasReplyAction, isMessagingStyle, repeatCount5m computed from earlier arrivals.
- HistoryGenerator.kt: generate(persona, config): GeneratedHistory containing the list of GenEvents sorted by (ts, device, seq) and a list of TruthRecords (defined in 4A-3; in this prompt create the data class and fill it with only the identifying fields and leave outcome fields to 4A-3). Build the day timeline sequentially. For each notification arriving outside an active distraction or resume period, run a TRIAL (below). Notifications arriving during an active distraction or resume period are in-session arrivals: emit notif_posted only and mark inSession (A4-7). Outside any trial window, generate self-initiated pickups from a Poisson process at the hour-modulated rate (A4-3, A4-8), each a self-initiated episode with its own destination, dwell and resume.
- TRIAL (one per eligible notification), all draws from a fork keyed by (notificationIndex) and drawn ALWAYS in this fixed order regardless of branch: u_self, destSelf, zDself, zRself, uPick, uLatency, destNotif, zDnotif, zRnotif. Then: Tself = -ln(1-u_self)/(lambdaPerHour/60) (infinity if lambda is 0); baseline event exists iff Tself < W. picked = uPick < pPickup(class, task, fatigue odds multiplier, A4-4); Tnotif = lognormal pickup latency; notification event exists iff picked && Tnotif < W. Earliest existing event owns the SHOW window (tie to notification event); SHOW cost = cGlance + D + R of the owner; SUPPRESS cost = D_self + R_self if baseline exists else 0. (Cost computation is finalized in 4A-3; here just keep the draws and owner.)
- Realized events for the arm chosen by the logging policy (ShowAll in this prompt): notif_posted at t; if the notification event owns: screen_on, unlock, pickup(triggeredByNotifKey hint) if the screen was off, app_foreground(cat, surface) at t+Tnotif, app_background(durationMs = D) at t+Tnotif+D, laptop window_change away from work at that time and back at the end of the dwell, resume_first_edit(delayMs = R). If the baseline event owns: same shape starting at t+Tself, flagged self-initiated, with notif_posted left unopened and a later notif_removed. If neither: notif_removed(reason dismissed or auto) shortly after. Also emit phone screen_off, laptop idle_start/idle_end and window_change events for normal work blocks. Gap events are not generated here (see 4A-5 fixtures).
- Keep sorting stable and deterministic. No HashMap iteration order may affect output (use LinkedHashMap or sorted keys).

Tests: same (persona, config) twice gives byte-identical event lists; different seeds differ; all events have synthetic=true; seq strictly increasing per device with no gaps; no payload key outside the allow-list; ts monotonic after sort; 14 days produces a plausible count (assert broad ranges read from data, e.g. more than 0 notifications per class, not magic numbers tuned to pass); draw-count discipline test (changing pPickup under the same seed changes outcomes but not the timing of the self-initiated draws of later trials).
Run the package tests, paste the summary line, commit "stage4a: rng and history generator". State what you did not verify.
```

### Prompt 4A-3: ground truth, logging policy, true effect

```text
Implement Stage 4A step 3: hidden truth and logging policy. personas/ only.

Create:
- TruthRecord.kt: per notification: persona, dayIndex, notificationIndex, ts, context (taskType, hourBand, fatigueBand, notifClass fields), inSession (Boolean), exogenous draws summary (lambdaPerHour used, pPickup used), existence flags (baselineExists, notifEventExists), owner (NOTIF | SELF | NONE), costs: lShowMin and lSuppressMin (both ALWAYS computed from the same draws when not inSession), deltaLMin = lShow - lSuppress, realizedArm (SHOW | SUPPRESS), propensity pi (probability of the realized arm given the policy and eligibility), eligibleForExploration (Boolean), world (MATCHED | MILD_MISSPEC).
- LoggingPolicy.kt: ShowAll (pi = 1.0, arm SHOW) and RandomizedHold(eps, maxImportance): eligible iff not inSession, not an important sender, importance proxy < maxImportance (use the persona's importantSenders and notification class; no learned model here); if eligible, hold with probability eps and log pi = eps for SUPPRESS and 1-eps for SHOW; if not eligible pi = 1.0 and arm SHOW. The policy draws from its own fork so toggling policies does not change the world draws.
- Held notifications realize the SUPPRESS arm: emit notif_posted then notif_removed(reason = held); outcome events follow the baseline event only. Update HistoryGenerator accordingly.
- Cost finalization per contract: SHOW = cGlance + (D+R of owner) where the owner is notification or baseline; SUPPRESS = D_self + R_self if baseline exists else 0; truncateCostsAtWindow flag honored (charge min(D+R, W - tOnset)). Include a unit test of the truncation on/off.
- MILD_MISSPEC world (A4-10): pPickup multiplied by a day-indexed drift in [0.85, 1.15] (deterministic from seed) and 10% of dwell draws use mu + ln(3). MATCHED is untouched. The truth records must say which world made them.
- TrueEffect.kt: TrueEffect.estimate(persona, context, config, nDraws = 200_000, seed): computes the true E[deltaL], E[L_show], E[L_suppress] and P(L>1) for a context by simulating the SAME trial rules with the generator's own samplers (not the twin). Provide byContext helpers and a TruthSummary over a generated history (mean true deltaL by notification class and by hour band, excluding inSession records, with counts).
- docs/PERSONA_WORLD.md: add "Trial rules", "Truth fields", "Worlds", "Logging policies", and an explicit "What this does not validate" paragraph.

Tests (several fixed seeds, never tuned): with pPickup forced to 0, every non-inSession record has deltaL == cGlance exactly (and 0 when cGlance = 0); with lambda forced to 0 and no truncation, mean deltaL over many trials is within 4 standard errors of cGlance + p*(E[D]+E[R]) minus the window-cut mass (compute analytically in the test); with very high lambda, mean deltaL approaches cGlance; no record charges both a baseline cost and a notification cost to SHOW; deltaL can be negative for some records (assert at least one exists in a large high-lambda sample, and that this is allowed); RandomizedHold: propensities recorded equal the actual selection probabilities, ineligible records have pi = 1.0 and are never held, held rate over many eligible records is within 4 standard errors of eps; ShowAll and RandomizedHold produce identical world draws (same truth lShow/lSuppress per notification) for the same seed; Kabir's mean true deltaL is lower than Rahul's in the default personas (read from data; if this fails, report instead of tuning).
Run, paste the summary line, commit "stage4a: ground truth and logging policy". State what is not verified.
```

### Prompt 4A-4: export and Node checker

```text
Implement Stage 4A step 4. personas/ export code and tools/ only.

Kotlin, in personas/export/:
- EventJsonlWriter: serialize GenEvents to one JSON object per line in the core-spec envelope with deterministic key order and escaping; no trailing partial lines; LF line endings. If the approved JSON approach has no writer, write a small deterministic serializer limited to the allow-listed payload types and test it.
- TruthJsonWriter: write truth records to a JSON file (array or one per line, pick one and document it) including persona hidden parameters, config (seed, world, policy, startEpochMs), and TruthSummary.
- FixtureExportTest (a normal unit test, not skipped): generate each of Rahul, Anika and Kabir for 14 days with a fixed seed under MATCHED/ShowAll, and also Rahul under RandomizedHold(eps=0.2) and MILD_MISSPEC for 4 days, and write files to phone/app/build/dhyan-fixtures/ named <persona>_<world>_<policy>_seed<seed>.events.jsonl and .truth.json. Assert the files exist and are non-empty. Do not commit them.

Node:
- tools/check-persona-data.mjs: for every *.events.jsonl in phone/app/build/dhyan-fixtures/ (path overridable by argument): (a) validate every line against the core-spec event schema by reusing the Stage 1 validator you found in 4A-0 (do not reimplement the schema); (b) every event has synthetic true; (c) seq is strictly increasing per device with no duplicates; (d) ids are unique; (e) privacy scan: reject any payload key or string value that looks like free text or a URL (keys title, text, body, url, uri, content, message, query; values containing "://" or spaces longer than a small allowance); (f) the truth file parses, has one record per non-inSession notification trial with finite numbers, pi in (0,1], and realizedArm consistent with the corresponding notif_removed reason in the events. Exit non-zero with file:line style output on failure. Include a self-test mode that proves the script fails on a planted bad line (duplicate seq, missing field, free-text value).
- tools/check-personas-purity.mjs: fail if any file under personas/ imports android.*, androidx.*, com.dhyan.app.twin.*, uses Math.random, Random.Default, UUID.randomUUID, System.currentTimeMillis or System.nanoTime. Include a planted-violation self-test.
- Add ONE script key to root package.json: "check:personas": runs both scripts' normal modes. Do not edit other keys.

Run: .\gradlew.bat :app:testDebugUnitTest --tests "com.dhyan.app.personas.*"; npm run check:personas. Paste the real summary lines and the checker's final lines. Commit "stage4a: export and checks". State what is not verified (for example the schema validator version used).
```

### Prompt 4A-5: ReplayDriver, clocks, TemporalGuard, FastForward

```text
Implement Stage 4A step 5. personas/replay/ and tests only. No twin.* imports.

Create:
- ReplayClock (interface) with VirtualClock (no real delay, advances instantly; used in tests) and ScaledRealtimeClock(speed = 100.0) (suspends via delay for the scaled gap; speed injected so tests can use VirtualClock). The driver must be cancellable by coroutine cancellation.
- ReplaySink (interface): fun onEvent(e: GenEvent); optional fun onProgress(p: ReplayProgress).
- ReplayDriver: suspend fun run(events, sink, clock, options). Delivers events in order of tsAdj = ts + offset(device) (per-device clockOffsetMs in options), tie-break (device, seq). Deduplicates by (device, seq) and counts duplicates dropped. Late or out-of-order events within a configurable tolerance are re-ordered; events later than the tolerance are delivered in arrival order and counted as lateEvents (report, do not hide). Emits ReplayProgress(fraction, simulatedDay, delivered, duplicatesDropped, lateEvents) at a configurable cadence. runUntil(t0) delivers only events with tsAdj < t0 (strictly less) and stops.
- TemporalGuard.kt: TrainTestSplit(t0). GuardedEventLog(events, t0): the ONLY way training-side code may read history. Methods eventsBefore(t), between(a, b), latestBefore(t), count queries. Any request whose upper bound exceeds t0, or any returned event with tsAdj >= t0, throws TemporalLeakException with the offending bound (never event content). Keep an audit counter of attempted leaks. A HoldoutAccessor gives access to events at or after t0 only after the guard is explicitly sealed with sealTraining(), and not before.
- FastForward.kt: FastForward(persona, days = 14, seed, world, policy).run(sink, clock): Flow<ReplayProgress> or a suspend function with progress callback; generates the history, replays it through ReplayDriver at the clock's speed. It does not know about the twin; the sink decides what to do with events (4B and Stage 6 will pass learning sinks).
- docs/PERSONA_WORLD.md: add "Replay and temporal isolation" describing t0 semantics (strictly less than), skew handling, duplicate handling and tolerance.

Tests: ordering with skew (a device with +5 min offset interleaves correctly); duplicate (device, seq) dropped and counted; out-of-order within tolerance reordered, beyond tolerance counted as late; runUntil(t0) delivers nothing at or after t0 (including an event exactly at t0); VirtualClock run finishes without real sleeping (assert wall time small) while ScaledRealtimeClock computes the right scaled delays (inject a fake delay function); cancellation stops promptly; PARTIAL fixtures: a fixture with a truncated final line is rejected by the JSONL reader with a clear error and does not deliver a half event; the temporal guard test required by spec 10.5: a deliberately leaky feature function (reads between(0, t0 + 1)) throws TemporalLeakException, a compliant one passes, and a feature that reads latestBefore(t0) never returns an event at t0; training-side code cannot reach holdout events before sealTraining().
Add an EventJsonlReader in personas/export/ if missing (reads the fixtures back; strict, rejects partial lines). Run package tests and npm run check:personas, paste the real summary lines, commit "stage4a: replay and temporal guard". State what is not verified.
```

### Prompt 4A-6: gate record and handoff for 4B

```text
Close out Stage 4A. Do not change code except to fix defects you find while verifying. Only STAGE4A_NOTES.md and docs/PERSONA_WORLD.md may be edited, plus fixes within personas/.

1. Run and paste the real summary lines for: .\gradlew.bat :app:testDebugUnitTest --tests "com.dhyan.app.personas.*"; the full unit test suite; npm run check:personas; npm run check:twin; npm run check:twin-contract. State clearly anything that was not run.
2. Gate-demo test (add as a test that prints, do not assert on pitch numbers): generate 14 days for Rahul, Anika and Kabir under MATCHED/ShowAll, print per persona: event counts by type, notifications total / inSession / eligible trials, true mean L_show, L_suppress and deltaL with counts, owner shares (NOTIF/SELF/NONE), and the count of negative deltaL records. Print the result of one replay at 100x with VirtualClock (delivered, duplicates dropped, late). Paste the printed table into STAGE4A_NOTES.md as measured values.
3. Determinism proof: generate twice per persona and show the SHA-256 of the two JSONL outputs matching (compute in a test or by a Node one-liner; paste the real hashes).
4. STAGE4A_NOTES.md must contain: assumptions A4-1..A4-12 with final values and a column for my approval; the file list; what is implemented vs tested vs not run; "not verified on target" (nothing here touches the Reno 14, say so); known limitations (single distraction owner per window; matched-world circularity; no edit_burst; no declared_session events; persona arrival rates are assumptions); and the INPUTS FOR STAGE 4B: how to construct each persona's history, the TruthRecord fields, the event-to-Stage-2-Event mapping gaps you can see, the GuardedEventLog API, the policy and propensity fields for Stage 5 estimators, and fixture paths and names.
5. Do not edit shared governance docs. List what I must fold into TRACEABILITY.md, HANDOFF_STATUS.md, STAGE_GATES.md and DECISIONS_PENDING.md after the merge.
Commit "stage4a: gate record".
```

---

## 5. Gate 4A procedure (you run it)

1. From the worktree:
   ```
   cd phone
   .\gradlew.bat :app:testDebugUnitTest --tests "com.dhyan.app.personas.*"
   cd ..
   npm run check:personas
   npm run check:twin
   npm run check:twin-contract
   ```
   All green, and paste the real summary lines into `STAGE4A_NOTES.md`.
2. Read the printed gate-demo table:
   - Rahul's true `ΔL` should be clearly above Kabir's, because Kabir self-interrupts anyway. If it is not, **do not tune**; read the persona numbers and the owner shares to see why.
   - Some `ΔL` records are negative. That is expected and documented.
   - Owner shares should look sane: baseline-owned runs more common for Kabir.
3. Confirm the two SHA-256 hashes match for each persona.
4. Open one `*.events.jsonl` and one `*.truth.json` in `phone/app/build/dhyan-fixtures/` and scan them yourself for anything text-like.
5. Confirm the temporal guard test fails when you plant a future read (temporarily edit a test, run, revert).

## 6. Merge and handoff
- Merge order: Stage 2, then 4A. Resolve root `package.json` by hand (keep `check:phone-contract`, `check:twin`, `check:twin-contract`, `check:personas`). 4A touches no Gradle or manifest file, so conflicts there mean someone crossed the ownership lines.
- Rerun every check and the full unit tests after the merge.
- Fold `STAGE4A_NOTES.md` into the shared governance docs yourself.
- **Stage 4B inputs** (it needs both merged): the Stage 2 `Event` model and Room layer, Stage 3's learning API (`observePickup`, `observeDwell`, `observeResume`, `observeSelfPickups`, `observeDestination`), `ContextBucketer`, and 4A's `ReplayDriver`, `GuardedEventLog`, `TruthRecord`. Send me `HANDOFF_STATUS.md` and `DECISIONS_PENDING.md` and I will write 4B against the real code.

## 7. When things go wrong
| Problem | What to do |
|---|---|
| Codex imports `twin.*` into personas | Revert; the purity script should catch it; explain the circularity rule |
| Codex hard-codes a result or tunes a persona to match the pitch | Reject; persona values come from the Stage 1 JSON plus the listed assumptions only |
| Output differs between runs | Look for `HashMap` iteration order, a draw skipped inside an `if`, wall-clock time, or `UUID.randomUUID` |
| Truth `ΔL` does not behave as the contract identities say | Check draw order and the tie rule first; only then touch tolerances, and write down why |
| Validator rejects events | Compare against core-spec; if the schema lacks a field, that is a schema delta for you to apply on `main`, not for Codex to patch around |
| Gradle fails in the worktree | `local.properties`, JDK 17, or dependency download; fix manually |
| Codex says tests pass with no output | Do not accept; run the command yourself |