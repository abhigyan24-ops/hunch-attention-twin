# DHYAN: Codex work queue (while Antigravity runs F1 on the phone)

Antigravity is on **Prompt F1** (Simulate tab honesty), which touches only `phone/` UI files, the pitch document and `NOTES_F1.md`. Codex works in areas F1 never touches: `laptop/`, `tools/`, `docs/contracts/`, `contracts/`, and governance docs. Max two agents at once; this queue keeps it at two.

Uses prompts from `DHYAN_STAGE789_PROMPTS.md` (preamble, C0, 7B, 7C, 8L, R, Resume) and `DHYAN_NEXT_ACTIONS.md` (F2, F3). Two new prompts are below (W2, W5). Time estimates are my guesses.

---

## 1. Why this order

1. **W1, contract audit (read-only, short).** It produces decisions only you can make (D1 to D6 conflicts). Doing it first means your answers are ready by the time Codex needs them, so no agent waits on you.
2. **W2, finish Stage 7A.** The first milestone is committed (`9c53137`). The pipeline, main-process wiring and live ActivityWatch smoke test are still open.
3. **W3, reconcile and build the contract executables (F3 step 2, then C0).** Starts after you approve the audit. It gives both platforms the vectors and mocks.
4. **W4, 7B then 7C.** Pip, panel, ingest server.
5. **W5, cross-review of Antigravity's F1 branch (read-only).** Fills a gap while F1 waits for your M0 approval.
6. **W6, F2 handoff refresh.** Runs **after F1 is merged**, so the refresh describes the real final state. Not before.
7. **Later:** 8L, 9W (not yet).

---

## 2. Conflict map against Antigravity's F1

| Area | F1 (Antigravity) | Codex queue |
|---|---|---|
| `phone/**` UI, Simulate ViewModel | writes | **never** |
| `twin/**`, `actuator/**`, `safety/**` | read-only | read-only |
| Pitch document | writes (M4) | read-only (W5 review) |
| `core-spec/` | read-only | **W3 only**, after your approval; may require rebasing the F1 branch if `SchemaVersion.kt` changes |
| `HANDOFF_STATUS.md` | no | W6 only, after F1 merge |
| `laptop/`, `relay/`, `tools/`, `docs/contracts/`, `contracts/` | no | writes |

Rule: if the contract reconciliation bumps `core-spec` while F1 is still unmerged, **merge the core-spec bump to `main` first**, then have F1 rebase. It is a one-line constant conflict at worst.

---

## 3. Worktrees and branches

| Task | Worktree | Branch |
|---|---|---|
| W1 (audit) | `..\dhyan-contracts` | `contracts` |
| W2 (7A finish), W4 (7B, 7C) | `..\dhyan-s7` | `s7-laptop` (existing) |
| W3 (F3 step 2, C0) | `..\dhyan-contracts` | `contracts` (continue) |
| W5 (review) | none, read-only: checkout the F1 branch in a throwaway worktree `..\dhyan-review` | n/a |

Run one task at a time with Codex. Switch tasks by switching worktrees; never mix two tasks in one session.

---

## 4. The queue

### W1: contract audit (about 20 min)
- Prompt: **F3 step 1** from `DHYAN_NEXT_ACTIONS.md`, with the preamble from `DHYAN_STAGE789_PROMPTS.md`. Worktree `..\dhyan-contracts`, branch `contracts`.
- Output: `docs/contracts/RECONCILIATION_REPORT.md` only.
- **Your job:** open the report, mark each proposed resolution approve or reject, and check that item 8 (phone events table shape) is answered.
- My recommended defaults when you decide: the contract wins on `videoState` enum, `kill_switch {on}`, `cmd_ack`, `held_list`; `show_now` uses `{heldId}` or `{all:true}` (an opaque id is safer than a notification key on the laptop); `gap` reasons stay free tokens with the documented values.

### W2: finish Stage 7A (about 40 min). New prompt, below.
- Worktree `..\dhyan-s7`, branch `s7-laptop`.
- Stops for your review at the end. Do not start 7B before you have read the notes.

### W3: reconcile, then C0 (about 60 to 80 min)
- After you approve the report: **F3 step 2**, then **Prompt C0 steps C0.1 onward**, branch `contracts`.
- Merge `contracts` to `main` after review. Then rebase `s7-laptop` and the F1 branch if needed.

### W4: 7B then 7C (about 70 min together)
- Prompts 7B and 7C from `DHYAN_STAGE789_PROMPTS.md`, branch `s7-laptop`. 7C needs the C0 attack suite and schemas, so it starts after W3 is merged.
- You verify click-through and the seven Pip behaviors yourself from `laptop/MANUAL_CHECKS.md`.

### W5: review F1 (about 20 min). New prompt, below. Read-only. Start once Antigravity reports F1 M1 to M4 done.

### W6: F2 handoff refresh (about 15 min)
- Prompt F2 from `DHYAN_NEXT_ACTIONS.md`, after F1 is merged. It edits `HANDOFF_STATUS.md` only.

---

## 5. Prompt W2: finish Stage 7A (new)

```text
[PASTE THE COORDINATION PREAMBLE FROM DHYAN_STAGE789_PROMPTS.md]

Task W2: finish Stage 7A in the existing worktree. Branch s7-laptop (milestone 1 is commit 9c53137). Notes NOTES_s7-laptop.md (append).
Scope: laptop/**, one root package.json script key (check:s7). Not allowed: phone/, relay/, core-spec/, contracts, tools/mock-*, the pitch document, governance docs.
Dependencies: none new beyond what 7A already added.

STEP 0 (no changes yet): inspect the branch. Compare what exists against every numbered item of the original Prompt 7A in DHYAN_STAGE789_PROMPTS.md. In NOTES_s7-laptop.md write a table: item, done / partial / missing, evidence (file and test names), and the real output of the current checks. Do not rely on the earlier summary; verify by running npm run typecheck, npm run build, npm run check:s7 (create the key if missing) and the root checks.

STEP 1: close the gaps you found. At minimum make sure these exist and are tested:
1. Main process: single-instance lock, settings store in userData (ingest.port 47821, ingest.allowedExtensionIds [], jsonl.maxBytes 10 MB, jsonl.retentionDays 14, a random pseudonymous deviceId generated once), and a diagnostics object (events written, rejected by PrivacyGuard, dropped, write failures, adapter errors, truncated lines recovered).
2. A LaptopPipeline class wiring adapter, normalizer, PrivacyGuard, jsonlStore and the focusState runner: start/stop, one failing stage never crashes the others, adapter silence for 15 s switches to ActiveWinFallback and, if both are silent, produces laptopState offline plus ONE gap event, backpressure (bounded queue; overflow drops the oldest and counts it), clean shutdown flushing the store. Expose onEvent, onState and diagnostics for 7B and 7C, plus injectFeedEvent(...) for 7C's extension feeds. Document the exact signatures in the notes.
3. ActivityWatch: the adapter discovers buckets at runtime by prefix, tolerates missing buckets (web and vscode optional), and never requests or keeps titles/URLs beyond the in-memory hostname-to-category step. Extend `npm run check:aw` or add scripts/live-aw-smoke.mjs that runs the REAL AwAdapter against local ActivityWatch for 60 seconds and prints only: bucket ids found, event counts per category, and the number of state changes. Run it only if ActivityWatch is reachable on localhost:5600; otherwise record "not verified on target: ActivityWatch not running" and give the owner the exact PowerShell command.
4. End-to-end privacy test through the whole pipeline: a fake AW server emitting CANARY_TITLE_7f3a and a canary URL in titles and URLs; after running the pipeline, scan the JSONL, rotated files, diagnostics output and captured logs with the contract's privacy approach (forbidden keys plus canaries); nothing may appear. If tools/privacy-scan.mjs does not exist yet, write the scan inside the test and leave a TODO for C0.
5. Recovery tests: kill the pipeline mid-write (simulate by truncating the last line), restart, assert recovery and the seq rule (monotonic, holes allowed, documented); disk-full simulation pauses writes, keeps the ring buffer, emits one gap event, resumes; rotation and retention with a fake clock.
6. focusState: confirm every row of spec 12.4 has a fixture test, including the 60 s window evaluated every 5 s, and that nothing marks a gap as focus.
7. laptop/MANUAL_CHECKS.md section "7A live checks": exact PowerShell steps for the owner: start ActivityWatch, run check:aw and the live smoke, run npm run dev (only after the Electron binary is installed with npm exec install-electron), expected output, and what to send back.

STEP 2: run typecheck, build, check:s7, the root checks and the zero-native-addon scan; paste real summaries. Commit as "s7-laptop-M2". Report: the done/partial/missing table, files, real output, assumptions, not-verified list, the exported signatures 7B and 7C will use. STOP for owner review. Do not start 7B.
```

---

## 6. Prompt W5: review the F1 branch (new, read-only)

```text
You are reviewing Antigravity's F1 work (Simulate tab honesty fix) on the branch/worktree the owner names. DO NOT EDIT ANY FILE and do not commit. You may run tests and builds. Read: docs/SPEC.md sections 8.3, 8.7, 9.1, 25.3, 25.13, docs/TWIN_MODEL_CONTRACT.md, NOTES_F1.md, the git diff of the branch against main, and the pitch document.

Report:
1. HAND-WRITTEN NUMBERS: search the diff and the current tree under phone/ (excluding twin/) for numeric constants, multipliers or lookup tables that scale or replace deltaL, pFull, p_pickup, hazard, dwell or cost. List each with file:line and whether it is displayed as twin output. Verify that every number on the Simulate tab can be traced to a TwinEngine result or to a clearly labeled non-twin element. Confirm any "heuristic preview" switch is OFF by default and visibly labels all numbers when on.
2. INPUT MAPPING: check each UI control against the approved mapping (session depth to hoursWorked/fatigue, time of day to hourBand, notification fields to NotificationInput, task type to ContextSnapshot, attention state display only). Flag any control that silently changes pickup probability.
3. PERSONA/TRAINED STORE: confirm the persona demo uses a store trained through the existing Stage 4 learning pipeline, not hard-coded parameters; report modelVersion, evidence strength and seed shown.
4. TESTS: the sensitivity regression test asserts inequalities derived from the twin contract, not numeric targets; a test fails if a non-twin constant multiplies results. Run them and paste real summaries. Propose four mutations (for example reintroducing a multiplier, mapping depth to hourBand, using a cached result, swapping persona stores) and say which test would fail.
5. PITCH DOCUMENT: for every claim, metric, example output and formula, say whether it has a real source (command, test, replay run) and mark any claim of focus benefit, accuracy, minutes saved or latency without one. Check that synthetic results are labeled synthetic, device behavior is "not verified on target" unless evidenced, and that "Urgent delayed = 0" appears only with the test suite size and result (spec 25.13).
6. OWNERSHIP: any edit to twin/, actuator/, safety/, core-spec/, laptop/, relay/ or governance docs.
7. VERDICT: ready to merge, ready after listed fixes, or not ready, with the shortest list of required changes.
```

---

## 7. What to bring back to me

- `docs/contracts/RECONCILIATION_REPORT.md` with your approve/reject marks (so I can adjust the contract and the 7C and 8L prompts).
- `NOTES_s7-laptop.md` after W2.
- Antigravity's F1 M0 plan, if you want a second opinion before approving it.
- After F1 merges: the refreshed `HANDOFF_STATUS.md`, and I will tell you whether Stage 8 phone work is safe to start.

## 8. `COORDINATION.md` update

| Branch | Agent | Status |
|---|---|---|
| F1 (phone branch) | Antigravity | running; M0 plan awaiting your approval |
| contracts | Codex | W1 next |
| s7-laptop | Codex | milestone 1 committed (`9c53137`); W2 after W1 |
| everything under Stage 8 and 9 | n/a | not started; Gate 2 evidence not yet recorded |