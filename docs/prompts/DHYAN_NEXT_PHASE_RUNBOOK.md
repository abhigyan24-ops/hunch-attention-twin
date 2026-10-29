# DHYAN: runbook for the next phase (works without Claude)

Companion: `DHYAN_NEXT_PROMPTS.md` (all prompts). Older files you already have and the agents will reuse: `DHYAN_STAGE789_PROMPTS.md`, `DHYAN_SYNC_CONTRACT_v1.md`, `DHYAN_STAGE789_GUIDE.md`, `DHYAN_NEXT_ACTIONS.md`, `DHYAN_CODEX_WORK_QUEUE.md`.

Everything here is built from the files and terminal output you sent. I have **not** seen the W1 reconciliation report, the F1 progress, or any code. Where that matters, the runbook gives a **default decision you sign** (section 2) so the agents can proceed without waiting for me.

---

## 0. Set-up that saves your Claude quota (do once, 10 minutes)

1. Put every prompt and guide file in the repo so the agents read them directly instead of you pasting:
   ```
   cd C:\Users\abhig\OneDrive\Desktop\dhyan   (your main worktree)
   mkdir docs\prompts
   copy <downloads>\DHYAN_*.md docs\prompts\
   git add docs\prompts
   git commit -m "docs: agent prompt library"
   ```
2. Create `docs/OWNER_DECISIONS.md` from section 2 below, tick the boxes you accept, commit it. **Agents treat this file as the owner's answer.** An unticked box means "stop and ask".
3. Add this to `AGENTS.md`: `Prompts live in docs/prompts/. Owner decisions live in docs/OWNER_DECISIONS.md and override defaults. Subagent rules: docs/prompts/DHYAN_NEXT_PROMPTS.md section L.`
4. Use **the other agent as reviewer** (Prompt R in `DHYAN_STAGE789_PROMPTS.md`, and the specialised reviews in `DHYAN_NEXT_PROMPTS.md`) instead of asking Claude. Your own review is the 10-minute checklist in section 6.
5. Run all commands in PowerShell (not `cmd`) or add `--no-pager` to git commands.

---

## 1. State as of your last evidence

| Item | State | Evidence |
|---|---|---|
| Stage 5 (decision, safety, counterfactuals) | merged | `e675e36` |
| C0 contracts, vectors, mocks | merged | `8eef3ee` |
| Stage 6 actuator + 3-tab UI | merged; **Gate 2 not run** | `08b379d`, board says "ready for Gate 2" |
| Extensions 9X1, 9X2 | merged | `0207d17` |
| `main` Simulate tab | contains **tuned multipliers** | `d3f4e13`; fix pending in F1 |
| F1 (phone-f1) | M0 audit only; M1-M4 pending | `88f4dc2` |
| 7A laptop core | done locally on `s7-laptop`, 31 tests; **not rebased, not merged** | `4cbbc6f` |
| W1 contract audit | report exists, **no owner marks yet** | `a59c208` on `contracts` |
| ActivityWatch live check | **not verified** (AW not running at last check) | smoke script |
| Electron GUI | install unconfirmed | `npm exec install-electron` printed nothing |
| 7B, 7C, 8L, 9W, 8P1, 8P2, 9P, Stage 10 | not started | board |

**Critical path:** F1 merge -> Gate 2 (you, on the Reno 14) -> phone Stage 8 (8P1, 8P2) -> Gate 3 -> 9P -> Stage 10.
**Laptop path (parallel):** rebase 7A -> 7B -> contract reconcile (W3) -> 7C -> 8L -> 9W.

---

## 2. Owner decisions with defaults (create `docs/OWNER_DECISIONS.md` from this)

Tick `[x]` to accept my recommended default. If you reject one, write your own text under it. Agents compare the W1 report with this file; where they differ they follow this file and note the difference.

```
# Owner decisions (authority for agents; unticked = stop and ask)

- [ ] DD-1  core-spec may be bumped ONCE, to 1.1.0, only inside task W3, with CHANGELOG, fixtures and tools/check-schema-sync.mjs green.
- [ ] DD-2  videoState enum: "playing" | "paused" | "none".
- [ ] DD-3  Commands: kill_switch args {on:boolean}; end_session args {}; declare_session args {task: taskType enum, plannedMin 5..480, strict:boolean}; show_now args {heldId} or {all:true}. (notifKey is NOT exposed to the laptop.)
- [ ] DD-4  Command acknowledgement uses the cmd_ack message from DHYAN_SYNC_CONTRACT_v1 section 6.
- [ ] DD-5  state.declared uses the active-session shape {task, plannedMin, strict, startedTs} or null.
- [ ] DD-6  Local collector gaps use deviceFrom = deviceTo = the emitting device; reason is a free token from the contract list (peer_silent, relay_down, schema_mismatch, queue_overflow, notif_listener_disconnected, a11y_disconnected, usage_query_failed, service_restarted, device_rebooted, clock_changed, permission_revoked, persistence_failure, adapter_offline).
- [ ] DD-7  The sync contract's outer/encrypted frames supersede the old flat relay frames; the relay is rewritten in task 8L (not before). core-spec keeps the event envelope.
- [ ] DD-8  If the phone Room events table does not persist id, seq, deviceId and schemaVersion, task 8P1 may add them with a real Room migration (no destructive migration) and a mapping layer, after the owner approves the 8P1 M0 plan.
- [ ] DD-9  "Active but unclassified" laptop activity: NO new laptopState enum value. Add an optional boolean `adapterHealthy` to the sync `state` body (sync contract 1.1.0, protocol minor). laptopState stays `offline` for unclassified activity, and receivers use adapterHealthy to tell "collector silent" from "active but unclassified".
- [ ] DD-10 Phone foreground service type: 8P1 M0 must check current Android docs for the target SDK; if `dataSync` has a time limit that threatens 12 h collection, propose `specialUse`. Apply only after owner approval of the M0 plan.
- [ ] DD-11 Communication apps (Slack, Teams, etc.) count as work only when the user labels them; otherwise unclassified. Games: known list plus user label only, never paths.
- [ ] DD-12 "stuck": the laptop maps the VS Code extension's own vscode_state=stuck to setStuckEvidence(true). No numeric stuck thresholds are added on the laptop.
- [ ] DD-13 Rule-based laptop confidence is "uncalibrated"; never shown as a percentage or probability anywhere (laptop, phone, pitch).
- [ ] DD-14 Gate 2 is run by the owner on a build of main AFTER F1 is merged. No phone Stage 8 code starts before Gate 2 evidence is recorded in STAGE_GATES.md.
- [ ] DD-15 Parallelism: up to 3 parallel work streams per agent are allowed ONLY with disjoint file scopes (section 3); merges go through the lead agent; the owner reviews branch by branch.
```

---

## 3. Parallel work model (works with or without subagents)

I could not confirm how Codex or Antigravity implement subagents, so the model does not depend on them. It works with **parallel sessions in separate git worktrees**, and if a tool offers subagents, the *lead* agent may use them on the same terms.

### 3.1 Streams

| Stream | Agent | Branch / worktree | Scope (only these paths) |
|---|---|---|---|
| **A1** F1 fix | Antigravity | `phone-f1` / existing | phone Simulate tab files, tests, pitch doc |
| **A2** phone sync | Antigravity | `s8-phone` / `..\dhyan-s8p` | `phone/**` except `twin/`, `actuator/`, `safety/` (after Gate 2) |
| **A3** review of Codex | Antigravity | read-only throwaway worktree | none (writes only `REVIEW_*.md` in the notes folder) |
| **C1** laptop UI | Codex | `s7-laptop` | `laptop/src/pet/`, `laptop/src/panel/`, `laptop/src/main/petWindow*`, tray, `MANUAL_CHECKS.md` |
| **C2** contract + relay | Codex | `contracts` then `s8-laptop-sync` | `docs/contracts/`, `contracts/sync/`, `tools/`, `relay/` |
| **C3** laptop sync/ingest | Codex | `s7-laptop` then `s8-laptop-sync` | `laptop/src/ingest/`, `laptop/src/sync/`, adapters |
| **C4** review of Antigravity | Codex | read-only throwaway worktree | none |

At most 3 active at once per agent (DD-15); you only review one branch at a time, so use this order: **F1 first, then C1, then contracts**.

### 3.2 Subagent protocol (the lead pastes this into every lead prompt; section L in the prompt file)

1. The **lead** reads all inputs, writes the interface file(s) first (types, function signatures, file ownership table), commits, then starts subagents.
2. Each subagent gets a **scope list of paths it may edit** and a test list. Scopes must be disjoint. Shared files (Gradle, manifest, Room DB, DI modules, root `package.json`, notes) belong to the lead only.
3. Subagents never touch another subagent's files. If they need a change, they write a request to the lead; the lead decides.
4. The lead merges subagent output sequentially, runs the full checks after each merge, and owns the single `NOTES_<branch>.md`.
5. If the tool has no subagents, run the same split as ordered milestones in one session; the scopes and interfaces still apply.
6. Never run two Gradle builds at once on this laptop. Only one stream per agent uses Gradle at a time.

### 3.3 Merge order
`contracts (W3 + C0 follow-ups)` -> `phone-f1` -> `s7-laptop` (7A, 7B) -> `s7-laptop` (7C) -> `s8-laptop-sync` -> `s8-phone` -> `s9-phone` -> `s9-win-notif`.
Rebase each branch on `main` before merging; fast-forward only when checks pass.

---

## 4. Work board

### Phase 1 (now): unblock Gate 2 and finish the laptop UI
| ID | Agent | Task | Depends on | Exit evidence |
|---|---|---|---|---|
| **AG-1** | Antigravity | F1 M1-M4 (strip multipliers, real inputs, provenance, regression tests, pitch corrections) | owner amendments (in prompt) | unit tests green, NOTES_F1 with real output, review by CX-6 |
| **CX-0** | Codex | rebase `s7-laptop` on main; confirm Electron install and live AW (owner assists) | none | checks green after rebase |
| **CX-1** | Codex | 7B Pip, panel, tray (lead + 3 subagents) | CX-0 | tests + MANUAL_CHECKS for owner |
| **CX-2** | Codex | W3: apply DD-1..DD-9 to core-spec and sync contract; update C0 mocks and schemas; contract 1.1.0 | OWNER_DECISIONS ticked | check:contracts green; merge to main |
| **CX-6** | Codex | review F1 (read-only) | AG-1 M1-M4 | verdict |

### Phase 2: Gate 2, then both Stage 8 halves in parallel
| ID | Agent | Task | Depends on |
|---|---|---|---|
| **OWNER** | you | **Gate 2** (section 5) on the Reno 14, build from main with F1 | F1 merged |
| **CX-3** | Codex | 7C ingest server + feeds (lead + 3 subagents) | CX-1, CX-2 |
| **CX-4** | Codex | 8L relay + laptop sync (lead + 4 subagents) | CX-3, CX-2 |
| **AG-2** | Antigravity | 8P1 phone sync core (lead + subagents), M0 stop first | Gate 2 passed, CX-2 merged |
| **AG-3** | Antigravity | 8P2 fusion, pairing UI, mirror; then 9P | AG-2 |
| **AG-4** | Antigravity | review Codex branches (read-only) | each Codex stop |

### Phase 3: Stage 9 close and Stage 10
| ID | Agent | Task |
|---|---|---|
| **OWNER** | you | **Gate 3** and **Stage 9 exit check** (section 5) |
| **CX-5** | Codex | 9W Windows notifications (time-boxed spike, negative result allowed) |
| **CX-7** | Codex | Stage 10 validation metrics, claims ledger, demo script docs |
| **AG-5** | Antigravity | Stage 10 Insights charts, Twin Lab diff, polish states, fast-forward animation |
| **OWNER** | you | rehearse demo twice, record backup video |

---

## 5. Owner-run checks (what only you can do)

### 5.1 Before starting agents
- ActivityWatch: start it, then `curl http://localhost:5600/api/0/info` must answer. Run `npm run check:aw` and `node scripts/live-aw-smoke.mjs` in `laptop\`; both print counts and ids only.
- Electron: `dir laptop\node_modules\electron\dist\electron.exe` must exist; if not, rerun `npm exec install-electron` and capture the error.

### 5.2 Gate 2 (after F1 is merged; build from `main`)
1. `cd phone` then `.\gradlew.bat assembleDebug` (or Android Studio Run). Install with `adb install -r`. Do not uninstall.
2. Open the app; confirm permissions and that the notification listener is connected.
3. Real hold: from another account or device send a notification from a **non-VIP social app**. Expect: held or snoozed, shown in the Live & Digest tab with an actuation status that reflects the confirmed result (spec 25.10), not just a decision.
4. Digest: expect the digest entry; press **Show now**; expect release; check **Undo**.
5. VIP/never-hold: confirm a VIP or urgent-pattern notification is never held.
6. Kill switch: toggle it; expect held items released and nothing new held.
7. Simulate tab: choose a persona; expect both timelines with provenance labels (simulated by the twin, N, seed, model version, persona mode banner "SYNTHETIC PERSONA"). No hand-tuned multipliers.
8. Record everything in `STAGE_GATES.md` (template below). Do not mark Gate 2 passed unless every step above ran.

### 5.3 Gate 3 and Stage 9 exit
Use `DHYAN_STAGE789_GUIDE.md` section 11 verbatim (pair with SAS digits, reconnect, held mirror, 60 s airplane-mode drop, `node tools/verify-sync.mjs`, attack flags, `node tools/privacy-scan.mjs`, VS Code burst, Shorts session, Windows toast).

### 5.4 Evidence template (paste into `STAGE_GATES.md`)
```
## <Gate name> - <date>
Commit tested: <hash>   Device/OS: <model, ColorOS/Windows version>
Commands run: <exact>
Steps and results: <step number: pass/fail + one line>
Logs/screenshots: <paths>
Known failures: <exact text>
Unverified assumptions: <list>
Blocked downstream: <list>
Result: PASS | FAIL | PARTIAL (owner-run)
```

---

## 6. Review without Claude

**Agent cross-review (preferred):** after each branch stop, run the matching read-only review in the *other* agent: `CX-6` / `AG-4` in `DHYAN_NEXT_PROMPTS.md`, or Prompt R in `DHYAN_STAGE789_PROMPTS.md`.

**Your 10-minute checklist per branch:**
1. `git --no-pager diff --name-only main...<branch>`; every path is inside the branch's scope (section 3.1).
2. Re-run the branch's check command yourself and compare counts with the notes.
3. Search for forbidden content: `findstr /s /i /m "document.title location.href getText" <dir>\*.ts` (extensions) and the repo `tools/privacy-scan.mjs` on any exported JSONL.
4. Read the notes' "NOT verified" list; copy it into `STAGE_GATES.md` as pending owner checks.
5. Any hard-coded number displayed as a result, any "passed" claim without output, any edit outside scope: reject and use the Resume prompt with the violation named.

---

## 7. Failure playbook and rules

| Situation | Action |
|---|---|
| Agent edits outside scope | `git checkout -- <paths>` in that worktree, Resume prompt naming the rule |
| Agent claims success without output | re-run the command yourself; if it fails, paste the real output into Resume |
| Merge conflict in `package.json` or `COORDINATION.md` | resolve by hand: keep both sides' keys/rows |
| Gate 2 slips more than 30 min | pause Codex streams C2, C3 (spec 17.3); keep branches unmerged |
| Quota ends mid-task | commit, run Resume next session; notes files hold the state |
| Two agents disagree on contract | vectors and `docs/contracts/SYNC_CONTRACT.md` win; write a CCR, do not edit locally |
| Subagent output conflicts | the lead decides; never let two subagents edit the same file |

Never: demo from the pre-F1 `main` Simulate tab; present persona ground truth as learned; claim anything verified on the Reno 14 or Windows without your own run; put raw text, URLs, titles or keys in logs or events.

**When to come back to Claude (minimal asks):** (1) after Gate 2 and Gate 3 results, (2) if two agent reviews disagree and you cannot judge, (3) for the final pitch/demo script review.

---

## 8. Stage 10 outline (for CX-7 and AG-5)

Insights charts (six, plus regret/saved minutes): focus-hour heatmap, interruption triggers, fatigue curve, fragmenting apps, notifications that matter, long-session surfaces, intentional-vs-compulsive; unimplemented charts show a marked pending state, never fabricated data (25.13). Validation: parameter recovery (+-0.08), calibration (Brier/ECE), "urgent delayed = 0" **with suite size**, counterfactual error vs persona truth, decision latency (state median, p95, machine), battery check. Polish: empty, error, offline states; simulated-sync toggle; fast-forward animation. Docs: demo script (spec 19), claims ledger mapping every pitch claim to a command, backup video, slides.