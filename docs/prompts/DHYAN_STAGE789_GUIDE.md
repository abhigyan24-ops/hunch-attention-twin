# DHYAN: Stages 7, 8, 9 coordination guide (Codex + Antigravity)

Companion files:
- `DHYAN_SYNC_CONTRACT_v1.md`: the frozen contract. Commit as `docs/contracts/SYNC_CONTRACT.md`.
- `DHYAN_STAGE789_PROMPTS.md`: every prompt, with a shared coordination preamble.

Spec: section 17 (Stages 7 to 9), 12 (laptop), 13 (extensions), 14 (sync, relay), 25.8, 25.9, 25.10 to 25.14.
I have not seen your Stage 5 and Stage 6 prompt files or repo, so names from them (for example `ActuationEventBus`, `SnapshotSource`) are things the agents must **discover and report**, not assume.

---

## 1. The picture

```
 Codex                                          Antigravity
 -----                                          -----------
 Stage 5 (5B..5E)  ---- merge ----+             Stage 6 (M0..M5) -> stop -> M6 -> Gate 2 (you)
 7A laptop core                    |             9X1 VS Code ext  (idle window, mock ingest)
 7B Pip + panel                    |             9X2 Browser ext   (idle window, mock ingest)
 7C local ingest + feed adapters   |
 8L relay + laptop sync  ----------+--- needs Stage 6 merged + Gate 2 -----> 8P1 phone sync core
 9W Windows notification bridge                                             8P2 fusion + pairing UI + mirror
                                                                            9P  phone wiring (cross-device episodes)
                                    Gate 3 (you, both devices)   ->   Stage 9 exit check (you)   ->   Stage 10
```

Rule of thumb: **one agent owns each platform at any time.** Codex owns the laptop side (TypeScript, Node, relay, Windows). Antigravity owns the phone side (Kotlin, Compose, Android) and the two extensions.

---

## 2. Decisions that need your approval

| ID | Decision | Recommendation |
|---|---|---|
| **C-1** | **Spec 17.3 says "Do not start Stages 7 to 9 until Gate 2 is passed."** Your plan overlaps them with Stages 5 and 6. | **Overlap mode, with guard rails** (below). If you want strict spec compliance, use **Strict mode** instead. |
| C-2 | Contract additions CA-1..CA-9 (`DHYAN_SYNC_CONTRACT_v1.md` section 14), including P-256 ECDH + SAS pairing instead of PBKDF2-from-code | Approve. Spec 25.8 explicitly allows a better key-agreement design |
| C-3 | Who runs Prompt C0 (contract implementation, mocks, vectors) | Whichever agent is paused at a review stop, in a third worktree. Never delay Stage 5 or 6 gates for it |
| C-4 | `held_list` message so the laptop panel can show held items | Approve (CA-2) |
| C-5 | Max two agents working at the same time | Yes |

### C-1 in detail

**Overlap mode (recommended, needs your explicit OK).** Allowed before Gate 2, because they touch no phone file and cannot endanger the main demo:
- C0 contract implementation and mocks (`contracts/`, `tools/`, `docs/contracts/`)
- Extensions (`vscode-ext/`, `browser-ext/`) against the mock ingest server
- Laptop work after Stage 5 is merged (`laptop/`, `relay/`)

Not allowed before Gate 2 is passed and Stage 6 is merged: **anything under `phone/`**, i.e. 8P1, 8P2, 9P.

**Guard rails:**
1. Your review time goes to Stage 5 and Stage 6 stops first. Laptop reviews wait.
2. If Gate 2 slips by more than 30 minutes (spec 17.3), **pause the laptop track**: leave its branches unmerged, no new prompts. Resume when Gate 2 passes.
3. If any Gate fails, stop dependent integration work (spec 25.11).
4. Merge order is fixed (section 4). Stage 7 to 9 branches never merge before Stage 6.

**Strict mode.** Only C0 (prep, not product code) runs now. Stages 7 to 9 all start after Gate 2. It is safer and slower; the two-track gain shrinks to the extension window.

Record your choice in `COORDINATION.md` (template in section 7).

---

## 3. Ownership matrix

"Frozen" means changes only through a Contract Change Request (CCR).

| Path | Owner | Notes |
|---|---|---|
| `core-spec/` | frozen | version bump only through you |
| `docs/contracts/`, `contracts/sync/` (schemas, vectors, VERSION) | frozen after C0 | CCR only |
| `tools/mock-ingest/`, `tools/mock-peer/`, `tools/verify-sync.mjs`, `tools/interop/` | C0 author, then frozen | both agents use them, neither edits them |
| `laptop/**` | Codex | |
| `relay/**` | Codex | |
| `vscode-ext/**`, `browser-ext/**` | Antigravity | |
| `phone/**` (everything, including Gradle, manifest, Room, DI, UI, `sync/`, `fusion/`) | Antigravity | and only after Stage 6 is merged for 8P and 9P |
| `phone/.../twin/**`, `actuator/**`, `safety/**` | **nobody in Stages 7 to 9** | Stage 8 and 9 call the documented entry points; they do not edit these |
| `AGENTS.md` | you | |
| `TRACEABILITY.md`, `HANDOFF_STATUS.md`, `STAGE_GATES.md`, `DECISIONS_PENDING.md`, `COORDINATION.md` | **you only** | agents write their own `NOTES_<branch>.md` |
| root `package.json` scripts | each agent adds only keys prefixed with its own stage (`check:s7`, `check:s8l`, ...) | merge by hand |

Violation of ownership is a stop-and-report event (prompts say so), not something to fix quietly.

---

## 4. Branches, worktrees, merge order

One worktree and one branch per prompt, one agent per worktree.

| Branch | Prompt | Agent | Depends on | Merges after |
|---|---|---|---|---|
| `contracts` | C0 | whichever is paused | contract md committed | immediately, before everything else |
| `stage5` | 5B..5E | Codex | existing | `contracts` |
| `stage6` | M0..M6 | Antigravity | Stage 5 merged for M6 | `stage5` |
| `s9-ext` | 9X1, 9X2 | Antigravity | `contracts` (mock ingest) | `contracts` (independent dirs) |
| `s7-laptop` | 7A, 7B, 7C | Codex | `stage5` merged; `contracts` | `stage6` (kept after for safety under C-1) |
| `s8-laptop-sync` | 8L | Codex | `s7-laptop`; `contracts` | `s7-laptop` |
| `s8-phone` | 8P1, 8P2 | Antigravity | `stage6` merged, Gate 2 passed | `s8-laptop-sync` |
| `s9-win-notif` | 9W | Codex | `s7-laptop` | `s8-laptop-sync` |
| `s9-phone` | 9P | Antigravity | `s8-phone` merged | `s8-phone` |

**Merge order:** `contracts` > `stage5` > `stage6` > `s9-ext` and `s7-laptop` (any order) > `s8-laptop-sync` > `s8-phone` > `s9-phone` > `s9-win-notif`.
Before each merge: rebase on `main`, run the full checks (section 9), you read the diff and the agent's notes file.

Worktree hygiene on Windows:
- `git worktree add ..\dhyan-<name> -b <branch>` from the main repo.
- A worktree does not carry `phone\local.properties` or `node_modules`. Copy `local.properties`, run `npm install`.
- Platform split removes most Gradle contention: after Stage 5 merges, only Antigravity runs Gradle. If both ever run Gradle, expect slowdowns; do not run two Gradle builds at once on a laptop with under 16 GB RAM.
- **Phone installs only from a build of current `main`** (or the `stage6` worktree for Gate 2). Installing from different worktrees leaves you debugging the wrong build. Never uninstall the app: Room data is your real-data log.

---

## 5. Timeline (two tracks; the minute counts are my estimates, not guarantees)

The spec allots 210 minutes to Stages 7 to 9 in sequence (70 + 60 + 80). With two tracks the critical path is the laptop track plus the phone integration, roughly 140 to 160 minutes of agent time plus your review time. Your review is the real bottleneck.

| Phase | Codex | Antigravity | You |
|---|---|---|---|
| 0 (now) | Stage 5 | Stage 6 up to its next stop | Approve C-1..C-5, commit contract md, paste cross-stage note into Stage 6 prompt, set up worktrees |
| 0b | pause point | M5 review stop | Run **C0** in a third worktree on whichever agent is idle. Review it quickly |
| 1 | finish Stage 5, then **7A** | idle window then **9X1, 9X2**, then M6 | Review Stage 5 merge, review Stage 6, run **Gate 2** |
| 2 | **7B**, **7C** | M6 done, Gate 2 passed, `stage6` merged | Review 7A..7C, merge |
| 3 | **8L** (relay and laptop sync) | **8P1**, **8P2** (phone sync, fusion, pairing UI) | Merge 8L, then 8P, run **Gate 3** |
| 4 | **9W** (Windows notifications) | **9P** (phone wiring of laptop events) | Stage 9 exit check, then Stage 10 |

If anything in the "Not allowed before Gate 2" list is reached early, **stop** and wait.

---

## 6. Coordination mechanisms

### 6.1 Frozen contract, executable
The contract is not just prose. C0 produces JSON schemas, a Node reference implementation of the crypto, **cross-language test vectors**, and two mocks. The vectors are the arbiter when the TypeScript side (Codex) and the Kotlin side (Antigravity) disagree: both must reproduce the same ECDH output, HKDF keys, SAS digits, hello MAC and ciphertext from the same fixed inputs.

### 6.2 Mocks so neither agent waits for the other
| Mock | Plays | Used by |
|---|---|---|
| `tools/mock-ingest/server.mjs` | the laptop's local ingest server | extensions (9X1, 9X2) |
| `tools/mock-peer/` (`--role laptop` and `--role phone`) | the other device, full pairing, encryption, events, backfill, commands, and attack modes (`--tamper`, `--replay`, `--expired-cmd`, `--bad-origin`...) | 8L tests the laptop client against a phone mock; 8P1 tests the phone client against a laptop mock |
| `tools/verify-sync.mjs` | checker | Gate 3: laptop JSONL vs phone export, exactly once, in order |

Real integration happens at Gate 3 on your devices. Anything not proven there stays "not verified on target".

### 6.3 Contract Change Requests (CCR)
An agent that needs a contract change **does not edit the contract.** It writes `CCR-<n>` in its notes file: what, why, the smallest proposed change, which tests change. You decide, bump the version (a tiny prompt on `main`), regenerate vectors, and both agents rebase. Until then the agent continues only with unaffected work.

### 6.4 Notes and handoff
Each agent keeps `NOTES_<branch>.md` as a running log: milestone status, commands run with real output summaries, assumptions, questions for owner, CCRs, not-verified list, integration TODOs. You fold these into the shared governance docs after each merge.

### 6.5 Interface hand-offs between platforms
| From | To | Interface | Frozen where |
|---|---|---|---|
| Phone Stage 6 | phone Stage 8 | `PhoneControl` facade (`showNow`, `setKillSwitch`, `declareSession`, `endSession`), `HeldItemsView` read API, `SnapshotSource` | cross-stage note (section 10); Stage 6 notes |
| Laptop 7B | laptop 8L | `PetInputBus` (local state plus remote events) | `NOTES_s7-laptop.md`, tests |
| Laptop 7C | extensions | local ingest protocol | contract section 9, mock-ingest |
| Laptop 8L | phone 8P | relay protocol | contract sections 3 to 8, vectors |

---

## 7. `COORDINATION.md` (create on `main`, edit only by you)

```
# Coordination board
Mode: [Overlap | Strict]      Gate 2: [pending | passed <date>]      Contract version: 1.0.0

| Branch | Prompt | Agent | Status | Blocked by | Last commit | Notes file | Reviewed by owner |
|---|---|---|---|---|---|---|---|
| contracts | C0 | | not started | contract md committed | | | |
| stage5 | 5B-5E | Codex | in progress | | | | |
| stage6 | M0-M6 | Antigravity | M? | stage5 merge (M6) | | | |
| s9-ext | 9X1, 9X2 | Antigravity | not started | contracts | | | |
| s7-laptop | 7A-7C | Codex | not started | stage5 merged | | | |
| s8-laptop-sync | 8L | Codex | not started | s7-laptop | | | |
| s8-phone | 8P1, 8P2 | Antigravity | not started | stage6 merged + Gate 2 | | | |
| s9-win-notif | 9W | Codex | not started | s7-laptop | | | |
| s9-phone | 9P | Antigravity | not started | s8-phone merged | | | |

Open CCRs: (none)      Paused tracks: (none)      Pause reason: -
```

---

## 8. Task catalogue

| Prompt | What it builds | Exit check |
|---|---|---|
| **C0** | schemas, reference crypto, vectors, mock-ingest, mock-peer, verify-sync, privacy scan tool, schema delta for `gap` reasons | all contract tests green; vectors reproduce on a second run; mocks start and pass their own tests |
| **7A** | Electron shell, `AwAdapter` + `ActiveWinFallback`, normalizer, JSONL store with rotation and recovery, rule-based laptop state, heartbeat | unit tests with a fake AW server; state transitions from synthetic window sequences; JSONL recovery tests |
| **7B** | Pip window with hitbox click-through, 7 behaviors with cooldowns, panel (today, held items view, declare session, pairing placeholder), tray | tests for the state machine and bubbles; **you** verify click-through and the 7 behaviors on Windows |
| **7C** | localhost ingest server (127.0.0.1, Origin, token), feed adapters, `stuck`/`flowing` integration into state inference | attack tests pass against the real server; 9X extensions connect |
| **8L** | final relay, laptop pairing, session crypto, protocol client, backfill, mirror, commands, privacy scan | all attack tests green vs `mock-peer --role phone`; relay tests green |
| **9W** | Windows notification bridge, or a documented negative result with evidence | emits categorized `notif_posted` (`device:laptop`, no text) from a real Windows toast, or `NOTES` shows evidence and limitation |
| **9X1** | VS Code extension | tests pass against mock-ingest; **you** run it in VS Code |
| **9X2** | MV3 browser extension | tests pass against mock-ingest; **you** load it in Chrome/Edge |
| **8P1** | phone crypto, pairing, `RelayClient`, queue, backfill, clock offset, commands, Room migration, manifest and Gradle changes | JVM tests match the vectors; client passes attack tests against `mock-peer --role laptop` |
| **8P2** | `ContextFuser`, second-screen overlap, pairing screen, mirror (`pet_event`, `held_list`), unpair | fusion unit tests; stale and offline handling; UI states |
| **9P** | laptop events feed `EpisodeBuilder` and snapshot, cross-device outcomes (spec 7.2) | unit and replay tests; Stage 9 exit check on devices |

---

## 9. Checks to run before every merge

```
npm run checks                # root: from Stage 1 and 2 (schema, privacy grep, observe-only until Stage 6)
npm run check:contracts       # C0: schemas, vectors, attack suite
npm run check:s7              # Codex laptop checks (after 7A)
npm run check:s8l             # relay + laptop sync (after 8L)
npm run check:ext             # extension tests (after 9X)
cd phone ; .\gradlew.bat :app:testDebugUnitTest   # Antigravity
```

The prompts create the script keys. Paste real summaries into the notes files.

---

## 10. Cross-stage note for the Stage 6 agent (paste now, even if it is already running)

If your Stage 6 prompts already contain these, skip it.

```text
CROSS-STAGE NOTE (Stage 6, from the owner). Stage 8 (phone sync) will call into this stage without editing it. Please make sure these exist, are documented in NOTES, and have unit tests:
1. A small facade `PhoneControl` with: showNow(heldId or all), setKillSwitch(on), declareSession(task, plannedMin, strict), endSession(). Each returns a result (applied/rejected + reason). It is the ONLY entry point remote commands will use. It must enforce the existing safety rules itself (never-hold, kill switch semantics).
2. A read-only `HeldItemsView` exposing opaque heldId, appCat, heldAt, expiresAt, status for current held items (no text, no package, no sender) plus a Flow of changes.
3. A `SnapshotSource` interface (current ContextSnapshot provider/StateFlow) that the fused snapshot can replace later without touching collectors or the twin.
4. An actuation event stream (held / released / failed-open / actuation-unknown) that Stage 8 can subscribe to for pet events.
5. Do NOT add INTERNET, OkHttp, or any sync dependency. Stage 8 owns those changes. Do NOT bump the Room version for sync tables; Stage 8 does it with a proper migration.
Report the real names and file paths in NOTES.
```

---

## 11. Gate 3 and Stage 9 exit protocol (you run these)

**Prerequisites:** relay deployed (Stage 1) with `wss` echo working; Stage 8 branches merged; both apps built from `main`; phone permissions still granted; laptop ActivityWatch running.

**Gate 3 (spec: both screens show the same focus state; a drop-and-reconnect replays the missed events with no duplicates):**
1. Laptop panel shows code. Phone Settings: enter code. Both show the **same six digits**. Press Yes on both. Expect "Paired" on both.
2. Close and reopen the laptop app. Expect automatic reconnect with no new pairing.
3. Declare a session on the laptop (`Focus: coding, 2h, strict`). Expect the phone to show it.
4. Trigger a held notification on the phone (Stage 6 flow). Expect the laptop Pip "1 held" and `held_list` in the panel. Press Show now on the laptop; expect release on the phone.
5. Compare focus state on both screens for a few minutes. Expect agreement; record any disagreement with timestamps.
6. **Drop and reconnect:** turn the phone to airplane mode for 60 seconds while you use the laptop; turn it off. Expect `relay_down` gap events, then replay.
7. Export both event files, run `node tools/verify-sync.mjs <laptop events.jsonl> <phone export.jsonl>`. Expect every laptop event present exactly once; zero duplicates.
8. Attack spot checks: run the mock-peer attack modes against the real clients (`--tamper`, `--replay`, `--expired-cmd`). Expect rejections and visible counters.
9. Privacy scan: run the scan tool over relay logs, laptop JSONL, phone export. Expect zero forbidden keys; canary strings absent.

**Stage 9 exit check (spec):** a VS Code edit burst and a browser Shorts session each change Pip's state and the phone's context snapshot.
1. Edit rapidly in VS Code for 30 s. Expect `edit_burst` events and laptop state `flowing`; phone snapshot `laptopState=flowing`.
2. Stop and open Chrome/Edge on a Shorts page for 60 s. Expect `tab_category` `short_video/shorts`, state `browsing`, Pip reaction, phone snapshot update.
3. Windows notification: send yourself a toast; expect a categorized `notif_posted` with `device:laptop`, or the documented limitation in `NOTES_s9-win-notif.md`.

Record each result with command, device, version, output, failures in `STAGE_GATES.md`. Do not mark anything passed that you did not run.

---

## 12. Security and recovery test matrix (spec 25.14)

| Area | Test | Where |
|---|---|---|
| Pairing | wrong SAS rejected; expired code rejected; third client rejected; commit mismatch aborts | mock-peer attack modes, 8L and 8P1 tests |
| AEAD | tampered ciphertext rejected; wrong key rejected; replayed `ctr` rejected; wrong direction rejected; nonce uniqueness covered by the counter rule | contract tests, both clients |
| Commands | unauthorized direction rejected; `kill_switch on:false` from laptop rejected; expired rejected; duplicate acked once; commands not replayed on reconnect | both |
| Relay | unknown type rejected; oversize rejected; rate limit; no body in logs | relay tests |
| Ingest | bad Origin, wrong token, token timeout, oversize, rate limit, forbidden key | 7C real server and mock |
| Privacy | scan of relay logs, JSONL, Room export, ingest logs, diagnostics | scan tool at Gate 3 |
| Recovery | partial JSONL line; disk-full pause; restart replay; clock skew; stale laptop state; schema mismatch | 7A, 8L, 8P2 |
| Honest UI | offline, stale, unsynced, pairing failed are first-class states, not zeros (25.13) | 7B, 8P2 |

---

## 13. Risks and fallbacks

| Risk | Fallback |
|---|---|
| Windows notification API needs app identity (an unpackaged Electron app may be refused) | 9W is a time-boxed spike. A documented negative result with evidence is allowed by spec 12.6; keep the item on the traceability ledger as incomplete (25.11) |
| MV3 service worker killed | persist state in `chrome.storage`, reconnect on alarms; tests simulate restart |
| Stable browser extension ID needed for the Origin allow-list | 9X2 sets a manifest `key` and a helper to print the ID; the laptop setting stores it |
| ColorOS kills the phone's network or foreground service | health screen from Stage 2; reconnect backoff; report in compatibility matrix |
| Relay on a free tier sleeps | ping before the demo; switch to EC2 if needed |
| Android API differences for P-256 raw points | tests with vectors; use only `EC` + `ECDH` + `AES/GCM/NoPadding`; no new libraries |
| Agent disagreement between TS and Kotlin crypto | vectors win; a mismatch is a CCR, not a local fix |
| Review backlog | two agents max; unreviewed branches do not start new work |

---

## 14. Traceability rows to add (you, in `TRACEABILITY.md`)

Local ingest server; VS Code extension (`edit_burst`, `resume_first_edit`, `stuck`); browser extension (scroll, video, `/shorts`, domain category); Windows notification bridge; laptop JSONL rotation/recovery; relay final; pairing + SAS; session AEAD; clock offset; backfill and dedup; commands + idempotency; `held_list` mirror; fusion and second-screen overlap; cross-device episodes; Pip 7 behaviors; click-through; panel features; unpair/re-pair; privacy scan; each attack test. Each starts `not_started`; "implemented" is not "tested"; "tested" means a test or device check that actually ran.

---

## 15. What to do right now

1. Decide C-1 (overlap or strict) and approve C-2..C-5.
2. Commit `docs/contracts/SYNC_CONTRACT.md` on `main`.
3. Paste the cross-stage note (section 10) into the running Stage 6 session.
4. Create `COORDINATION.md`.
5. When an agent is paused at a review stop, run **Prompt C0** in a third worktree.
6. Keep reviewing Stage 5 and Stage 6 first.