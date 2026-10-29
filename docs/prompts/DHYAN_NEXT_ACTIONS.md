# DHYAN: corrective prompts and next actions (after the Stage 7A and phone-app reports)

Based on: your `HANDOFF_STATUS.md`, `DECISIONS_PENDING.md`, the Codex 7A report, and the phone agent's report that you pasted. I have not seen the repo, the pitch document, or the code behind that report.

## Order of work

1. **Stop and fix the Simulate tab honesty problem** (Prompt F1, phone agent). Do this before any more phone feature work or demo prep.
2. **Refresh `HANDOFF_STATUS.md`** (Prompt F2). It contradicts itself and does not match what the phone agent reports.
3. **Reconcile the sync contract with `core-spec`** (Prompt F3). Several pending decisions D1 to D6 conflict with the contract I wrote. Do this before Prompt C0 runs.
4. Let Codex continue 7A to 7C. It is isolated on `s7-laptop`, so it does not depend on 1 to 3.
5. Add the extra checks in section "Add to Prompt 8P1, milestone M0" before Stage 8 phone work starts.

---

## Prompt F1: Simulate tab honesty and wiring audit (phone agent, Planning mode)

```text
[PASTE THE COORDINATION PREAMBLE FROM DHYAN_STAGE789_PROMPTS.md]

You are in Antigravity: use Planning mode. Task F1: audit and correct the Simulate tab so that every number it shows is produced by the TwinEngine (or is clearly labeled otherwise). Spec sections: 8.3, 8.7, 9.1, 25.3, 25.13, 24 (assumptions). Branch: the current phone branch. Notes: NOTES_F1.md.
Scope: phone/** UI and ViewModel files for the Simulate tab, tests, the pitch document (docs or repo root, the file you called dhyan-pitch.md), NOTES_F1.md. NOT allowed: twin/**, actuator/**, safety/**, core-spec/.

BACKGROUND (do not just accept it, verify it in the code): a previous session reported that attention state, session depth, notification category and task type had little visible effect on the Simulate tab, and "fixed" it by adding hand-written multipliers (for example a pickup multiplier from 0.30 to 0.92 by attention state, a depth multiplier, category hazard multipliers, task cost multipliers) and by mapping the session-depth slider to hourBand. Those multipliers are not part of the twin model contract (docs/TWIN_MODEL_CONTRACT.md), they were not validated by the Stage 4 parameter-recovery tests, and results such as "DEEP_FOCUS + Social 45m gives deltaL about 11 min" would therefore be hand-tuned output presented as model output. That conflicts with spec 25.3 (do not hard-code results to match a pitch), 25.13 (provenance: observed, learned, simulated, estimated) and the Stage 3 rule not to tune outputs.

MILESTONE M0 (plan only, then STOP for owner approval):
1. List EVERY hand-written constant or multiplier in the Simulate ViewModel and anywhere in ui/ or fusion that changes a displayed result. For each: file:line, value, what it does, whether it is in the twin contract.
2. Find out WHY the original inputs seemed to have no effect. Write a JVM unit test (no UI) that calls TwinEngine with a cold store and varies, one at a time: appCat (social vs communication vs promo notifCat), fatigueBand (low, med, high), isVip, urgencyScore band, repeat band, taskType, hourBand. Report the actual deltaL means. Per the twin contract, under cold priors differences come only from the population priors (spec 8.9) and the fatigue multiplier on prior odds, so some inputs (for example focusState itself, which is not part of the bucket) are EXPECTED to change nothing. Distinguish expected insensitivity from wiring bugs (inputs not reaching NotificationInput or ContextSnapshot, a cached result, a bucket not passed to resolve, a seed that hides differences).
3. Propose the correct mapping of each UI control to real model inputs:
   - attention state shown in the UI: a display of focusState and minutesInState; it must not silently drive pickup probability;
   - session depth slider: hoursWorked input to FatigueIndex (fatigue band), and/or minutesInState, NOT hourBand;
   - time of day: its own control or the real clock, mapped to hourBand;
   - notification category, VIP flag, urgency, repeat count: fields of NotificationInput;
   - task type: ContextSnapshot.taskType.
4. Persona demo: the Simulate tab should use the Stage 4 trained store for the chosen persona (replay a persona history into an InMemoryParamStore through the existing learning pipeline) so that results come from learned parameters. Propose how, reusing existing Stage 4 code only.
5. If the owner wants attention state to change pickup probability, that is a MODEL CHANGE: write a CCR (what, why, equations, which tests and the parameter-recovery check would change). Do not implement it in the UI.
6. List every claim in the pitch document (numbers, metrics, example outputs). For each: the command or test that produced it, or "no source".
Then STOP.

M1 (after approval): remove the hand-written multipliers from the display path, or, if the owner wants a what-if, put them behind a clearly named "Heuristic preview (not the twin)" switch that is OFF by default and visibly labels every number while on. Wire the real inputs per the approved mapping.
M2: show provenance on the Simulate tab per 25.13: "Simulated by the twin", persona or cold priors vs trained, N, seed, model version, evidence strength, and a plain note when inputs have no effect under cold priors. No false precision.
M3: tests: the sensitivity test from M0 becomes a regression test with expected inequalities that follow from the twin contract (for example promo notifCat gives a different pickup prior than social; high fatigue raises prior odds), not hard-coded numeric targets. A test that fails if any constant outside twin/ multiplies deltaL or p_pickup.
M4: correct the pitch document: remove or relabel every claim without a source; replace example outputs with outputs copied from a real run, with the command, persona, seed and model version beside them; mark synthetic persona results as synthetic and device results as "not verified on target" unless the owner confirms them. Do NOT claim any focus benefit, accuracy or minutes saved that no test produced.
Also report: the app builds ViewModels manually with remember { ViewModel(...) } instead of Koin. Explain how SnapshotSource is bound today, because Stage 8 plans to replace it in one place.
Run unit tests and assembleDebug, paste real summaries, commit per milestone. Not verified: the device.
```

---

## Prompt F2: refresh `HANDOFF_STATUS.md` (any agent, owner-authorized edit of this one file)

```text
Task F2: refresh HANDOFF_STATUS.md with verified facts only (spec 25.12). You are authorized to edit ONLY HANDOFF_STATUS.md and, if needed, create docs/verification/HANDOFF_REFRESH_<date>.md. Change no code.

Facts to verify by running commands, not by reading old docs: git branch --all, git log --oneline --graph --all -40, git status --short --branch, the current commit on main and on every active branch (s7-laptop, the phone branch, any stage5/stage6 branches), which stage each branch implements, and whether it is merged into main.
Then: the current tree (replace stale text such as "phone: placeholders" and "No Git commit was performed" if no longer true; the file currently contradicts itself between section 2/7 and section 10); real environment versions; the last successful commands with real summaries (root npm test, npm run checks, relay tests, laptop tests, phone JUnit with the fresh --rerun form, twin/personas checks); failing commands with exact error text; implemented vs stubs vs hard-coded demo data (explicitly list any hand-written multipliers in the Simulate tab and any synthetic test notifications); per-stage status for stages 1 to 7A with evidence; the phone Room events table shape (columns), and whether events are stored as core-spec envelopes with id, seq, deviceId and schemaVersion; foreground service type and target SDK; compatibility results (all "not verified on target" unless the owner ran them); the next three tasks in dependency order. Keep sections that are still true. Mark anything you could not verify as "unverified" with the reason.
```

---

## Prompt F3: reconcile `core-spec` with the sync contract (Codex or any agent, two steps)

Your `DECISIONS_PENDING.md` records choices that conflict with `DHYAN_SYNC_CONTRACT_v1.md`. The audit is read-only; you decide; then a second prompt applies the approved changes. Replace "C0.0" in Prompt C0 with this.

```text
[PASTE PREAMBLE]

Task F3 step 1 (READ-ONLY AUDIT, no edits). Compare core-spec/ (schemas, fixtures, version), relay/ validation, DECISIONS_PENDING.md items D1 to D12, and docs/contracts/SYNC_CONTRACT.md. Produce docs/contracts/RECONCILIATION_REPORT.md (only this file) with a table: item, what core-spec/relay currently says (file:line), what the contract says, conflict yes/no, smallest proposed resolution, impact on already-built code and tests. Check at least:
1. videoState: core-spec rejects any value (D1); contract tab_category uses "playing" | "paused" | "none".
2. Commands: core-spec allows only empty args for end_session and kill_switch (D3); contract kill_switch args {on:boolean}; contract declare_session args {task (taskType enum), plannedMin 5..480, strict}.
3. show_now args: core-spec/owner clarification {notifKey}; contract {heldId} or {all:true}. Is notifKey in events a keyed hash? Which is safer to expose to the laptop?
4. Command acknowledgement (D4): core-spec has no frame; contract adds cmd_ack.
5. state.declared shape (D5): compare with the contract's `declared` field and the active-session shape {task, plannedMin, strict, startedTs}.
6. gap.deviceFrom/deviceTo and reason (D6, D7): contract uses deviceFrom = deviceTo = "phone" for local collector gaps and tokens peer_silent, relay_down, schema_mismatch, queue_overflow, notif_listener_disconnected, a11y_disconnected, usage_query_failed, service_restarted, device_rebooted, clock_changed, permission_revoked, persistence_failure. Reason is a free token in core-spec, so list which tokens the code already uses.
7. Relay frames: core-spec/relay validate flat frames with an "encrypted" marker (D2); contract uses an outer envelope with pairing frames, a clear authenticated hello, and encrypted frames {v,t,d,ctr,c}. Which schemas does the contract supersede, and what in relay/ must change in Prompt 8L?
8. Event persistence: confirm whether the phone Room events table stores id, seq, deviceId, schemaVersion per core-spec/event.schema.json; if not, say exactly what is stored (columns) and what mapping layer exists (for example EventStreamAdapter).
9. Anything else in the contract that core-spec or Stage 2 to 4 code contradicts.
Do not edit anything else. Stop after writing the report.

Task F3 step 2 (run ONLY after the owner has marked each proposed resolution approve/reject in the report). Apply the approved resolutions: a controlled core-spec minor bump (SCHEMA_VERSION, schema const, CHANGELOG, fixtures valid and invalid for every changed field, SchemaVersion.kt only if tools/check-schema-sync.mjs requires it), update relay/ frame validation ONLY if the owner approved it here (otherwise leave it for Prompt 8L), run npm test and npm run checks, paste real output. Then continue with Prompt C0 steps C0.1 onward, using the reconciled schemas as the single source of truth.
```

---

## Add to Prompt 8P1, milestone M0 (phone sync plan)

Add these items to the M0 plan the agent must produce:

1. **Event storage.** Your handoff describes the Room `EventEntity` as flat columns (`ts, kind, appCat, surface, durationMin, senderHash, notifCat, scrollCount, extrasJson`). Sync dedups and replays by `(device, seq)` and event `id`, and exports core-spec envelopes. State exactly how the phone stores or reconstructs `id`, `seq`, `deviceId`, `schemaVersion` today. If they are not persisted, M0 must propose a Room migration plus a mapping layer, and the plan needs your approval before any code.
2. **Foreground service type.** The handoff says `dataSync` with target SDK 37. As far as I know Android 15 time-limits `dataSync` foreground services (around 6 hours, verify in the current docs). That could kill your 12-hour data collection. M0 should check this and propose `specialUse` if confirmed.
3. **ViewModel and DI wiring.** Report how `SnapshotSource` and the ViewModels are constructed (manual `remember { ViewModel(...) }` vs Koin) so the fusion swap in 8P2 stays in one place.
4. **Which phone commit Stage 8 starts from**, and that Gate 2 evidence (a real notification held, digested and Show-now) is recorded in `STAGE_GATES.md`, not only in a chat message.

---

## `COORDINATION.md` values to fill in now

| Item | Value |
|---|---|
| Codex | `s7-laptop`, commit `9c53137` (7A milestone 1), in progress, not merged |
| Phone agent | report says stable on device; Stage 5/6 status unclear, needs F2 to confirm |
| Gate 2 | **not recorded in the files you sent**; do not mark passed |
| Contract | v1.0.0 drafted; conflicts with `core-spec` D1 to D6; run F3 before C0 |
</USER_REQUEST>
<ADDITIONAL_METADATA>
The current local time is: 2026-10-29T14:40:08+05:30.
</ADDITIONAL_METADATA>
<USER_SETTINGS_CHANGE>
The user changed setting `Model Selection` from Claude Sonnet 4.6 (Thinking) to Gemini 3.8 Flash (High). No need to comment on this change if the user doesn't ask about it. If reporting what model you are, please use a human readable name instead of the exact string.
</USER_SETTINGS_CHANGE>