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

# DHYAN Sync and Ingest Contract v1.0.0

Commit this file as `docs/contracts/SYNC_CONTRACT.md` on `main`. It is the single authority for everything that crosses a process or device boundary in Stages 7 to 9: the extension-to-laptop ingest protocol, the pairing flow, the relay protocol, session encryption, commands, time and ordering.

- Sources: spec sections 6, 12.4, 13, 14, 25.8, 25.9, 25.11, 25.14.
- Where this contract adds to or replaces spec wording, the item is listed in section 14 (CA-1 to CA-9) for owner approval.
- **Change control:** nobody changes this file, the JSON schemas under `contracts/sync/`, or the test vectors without a Contract Change Request (CCR). See the guide, section 6. Any change bumps `contracts/sync/VERSION`, regenerates the vectors, and both platforms must pass before merge.
- **Not production security.** See section 12 for the threat model and the things this design does not claim.

All times are epoch milliseconds (UTC). All binary fields are standard base64 with padding. All JSON is UTF-8, one JSON object per WebSocket text frame.

---

## 1. Parties and roles

| Party | Role |
|---|---|
| Phone app | Reference time base. Runs the twin and the actuator. Authoritative for held items and decisions |
| Laptop app (Electron main process) | Sensor, pet, state inference. Hosts the local ingest server. Authoritative for laptop events |
| Relay | Dumb forwarder (Node `ws`). Never decrypts. In-memory only |
| VS Code extension, browser extension | Report counts to the laptop app over localhost only. Never talk to the relay or the phone |

Telemetry (events, state, pet events, held list) flows both ways but is never executed. Commands are separate messages with their own authorization (section 8).

---

## 2. Constants

| Name | Value | Notes |
|---|---|---|
| `PROTOCOL_VERSION` | `1` | integer, in every frame as `v` |
| Max frame size (relay) | 65536 bytes | relay closes with 1009 above this |
| Max plaintext body | 32768 bytes | after decryption |
| Relay rate limit | 50 messages per second per client | spec 14.3 |
| Events batch | at most 50 events or 32 KB, flushed within 2 s | spec 14.3 |
| Heartbeat `state` interval | 5 s | spec 12.4 |
| Peer silent threshold | 30 s | emit a `gap` event, spec 14.3 |
| Laptop snapshot stale threshold | 15 s | three missed heartbeats; then `laptopState = offline` |
| Phone snapshot stale threshold | 30 s | shown as stale on the laptop |
| Pairing code | 6 characters from `23456789ABCDEFGHJKLMNPQRSTUVWXYZ`, uppercase | about 30 bits; a rendezvous token, not a secret |
| Pairing validity | 5 minutes | or 3 failed confirmations, whichever first |
| Relay join attempts on rendezvous rooms | 10 per minute per IP | |
| Ping interval | 30 s | clock offset estimation |
| Command default expiry | 30 s, maximum 60 s | |
| Command idempotency window | 24 h | |
| Reconnect backoff | 1, 2, 4, 8, 16, 30 s then 30 s, with 20% jitter | |
| Unacked event retention (sender) | 72 h or 50 000 events, then drop oldest and emit `gap queue_overflow` | |
| Local ingest default port | `47821` (configurable) | assumption CA-9 |
| Ingest max frame | 4096 bytes; 20 messages per second per connection | |

---

## 3. Relay-visible frames (outer layer)

The relay validates only this layer: valid JSON object, size limit, rate limit, `v == 1`, `t` in the allowlist for the room type.

### 3.1 Room types and URLs

| Room | URL | Who joins | Allowed `t` |
|---|---|---|---|
| Rendezvous | `wss://<relay>/ws?room=<rv>&device=phone\|laptop` with `rv = hex(sha256("dhyan-rendezvous-v1\|" + CODE))` (64 hex) | the two devices, only during pairing | `pair_offer`, `pair_join`, `pair_reveal`, `pair_confirm`, `pair_cancel` |
| Session | `wss://<relay>/ws?room=<sid>&device=phone\|laptop` with `sid` = 64 hex from section 5.4 | the paired devices | `hello`, `ping`, `pong`, `events`, `ack`, `state`, `pet_event`, `held_list`, `command`, `cmd_ack`, `error` |

The relay tells the two room types apart by a registry it keeps: a room is rendezvous if its first frame is `pair_offer`, otherwise session. Rendezvous rooms expire after 5 minutes. Session rooms expire after 24 hours idle. Max two clients per room, one per `device` role; a second client for the same role is rejected with close code 1008.

### 3.2 Frame shapes

Clear pairing frames and `hello` have their own fields (sections 4 and 5). Every other session frame is an encrypted frame:

```json
{ "v": 1, "t": "events", "d": "l2p", "ctr": 17, "c": "<base64 ciphertext+tag>" }
```

`d` is `l2p` (laptop to phone) or `p2l`. `ctr` is a strictly increasing integer starting at 1 per direction per session.

The relay never sees a body, a `mid`, a timestamp, or any payload field. It does see: message type, direction, counter, size, timing, room id. This metadata leak is accepted for the prototype (section 12).

---

## 4. Pairing (authenticated key agreement with human confirmation)

Goal: no reliance on a low-entropy code as a key. The code only gets both devices into the same rendezvous room. Keys come from ECDH. Humans compare a short code on both screens (this satisfies spec 25.8 "explicit confirmation on both devices").

Curve: **P-256 (secp256r1)**. Public keys are 65-byte uncompressed points (`0x04 || X || Y`), base64. ECDH output is the 32-byte shared X coordinate. HKDF is HKDF-SHA256 (RFC 5869). Implement HKDF by hand on Android (a 15-line HMAC loop); Node has `crypto.hkdfSync`.

Sequence (all frames in the rendezvous room, all `v:1`):

1. Laptop shows `CODE` and creates the rendezvous room. It generates an ephemeral key pair (`skL`, `pkL`) and a 16-byte random `nL`. It sends
   `pair_offer {device:"laptop", commit: b64(SHA256(pkL || nL))}`.
2. Phone user types `CODE`. Phone generates (`skP`, `pkP`) and 16-byte `nP`, sends
   `pair_join {device:"phone", pk: b64(pkP), n: b64(nP)}`.
3. Laptop sends `pair_reveal {device:"laptop", pk: b64(pkL), n: b64(nL)}`. Phone checks `SHA256(pkL || nL)` equals the commit from step 1. On mismatch it sends `pair_cancel {reason:"commit_mismatch"}` and aborts.
4. Both compute `secret = ECDH(sk_self, pk_peer)` and the transcript
   `T = SHA256("dhyan-pair-v1" || pkL || pkP || nL || nP)`.
   The **SAS** (short authentication string) is `uint32_be(T[0..4]) mod 1000000` shown as six digits, zero padded, grouped `123 456`.
5. Both screens show the SAS and ask "Do the digits match?". Only after the local user presses Yes does a device send
   `pair_confirm {device, mac: b64(HMAC-SHA256(kConf, "confirm|" + device))}` with `kConf = HKDF(secret, salt=T, info="dhyan-pair-confirm-v1", 32)`.
6. Pairing completes on a device when it has (a) the local user's Yes and (b) a valid peer `pair_confirm`. Then both derive and persist:
   - `PK = HKDF(secret, salt=T, info="dhyan-pairing-key-v1", 32)`
   - `sid = hex(HKDF(PK, salt="", info="dhyan-room-v1", 32))` (64 hex, the permanent session room)
   Both then leave the rendezvous room. The laptop invalidates the code.
7. Failure paths: user presses No, wrong confirm MAC, timeout, or a third client attempts to join: send `pair_cancel {reason}` and discard all key material. After 3 failures or 5 minutes the laptop generates a new code. A visible, recoverable error is shown on both devices.

**What the SAS buys:** an active attacker in the middle (including the relay operator who can brute-force the code from its hash) cannot make the SAS match without about a one-in-a-million guess, because the laptop committed to its key before seeing the phone's. This is why commit-reveal is mandatory.

**Storage of `PK`:** laptop uses Electron `safeStorage` (Windows DPAPI) writing an encrypted file in `userData`; phone wraps it with an Android Keystore AES-GCM key and stores the blob in the app-private settings table. `PK`, `skL`, `skP`, `secret`, `T`, SAS, code: never logged, never in source control, never in events.

**Unpair / re-pair:** "Unpair" on either device deletes `PK` and `sid`, sends nothing, and the other device finds out when its next hello fails. Re-pairing is a fresh run of this section. Replacing a lost device means unpair on the surviving device, then pair again.

---

## 5. Session establishment and encryption

### 5.1 Derived keys

```
kHello = HKDF(PK, salt="", info="dhyan-hello-mac-v1", 32)
```

### 5.2 `hello` (clear, authenticated)

```json
{ "v":1, "t":"hello", "device":"phone", "sn":"<b64 16 random bytes>",
  "proto":[1], "lastSeqAcked": 1234, "mac":"<b64>" }
```

`mac = HMAC-SHA256(kHello, "hello|" + device + "|" + sn + "|" + lastSeqAcked)`.

- `sn` is fresh random per connection. `lastSeqAcked` is the highest peer event `seq` this device has durably stored (0 if none).
- A device that receives a `hello` with a bad MAC ignores it and counts a failure; three failures in 60 s close the connection.
- A `proto` list with no common version produces `error{code:"unsupported_version"}` and a visible "update the app" state.

### 5.3 Session keys

When both hellos are valid, with `snL` the laptop's and `snP` the phone's:

```
okm   = HKDF(PK, salt = snL || snP, info = "dhyan-session-v1", 64)
k_l2p = okm[0..32)     k_p2l = okm[32..64)
```

Keys are fresh for every connection, so the counters restart at 1 safely and nonce reuse under one key is impossible if the counter is strictly increasing.

### 5.4 Encrypted frame rules

- Cipher: AES-256-GCM, 16-byte tag appended to the ciphertext.
- Nonce (12 bytes): `0x00000000 || ctr` where `ctr` is the 64-bit big-endian counter. The nonce is not transmitted.
- AAD (authenticated, not encrypted): ASCII `"dhyan|1|" + t + "|" + d + "|" + ctr`.
- Plaintext: UTF-8 JSON `{ "mid": "<uuid>", "ts": <ms>, "b": { ...body } }`.
- Sender: use `k_l2p` when `d=="l2p"`, `k_p2l` when `d=="p2l"`. Never wrap the counter; at `ctr > 10^9` reconnect to rekey.
- Receiver: reject frames whose `d` is not the peer's direction, whose `ctr <= lastCtr` (replay or reorder), whose tag fails, whose `t` is not allowed, or whose body fails schema validation. Count each rejection by reason. Three tag failures within 60 s close the session. A rejected frame is never partially applied.
- Key rotation: automatic at every reconnect. Long-lived `PK` rotation is by unpair and re-pair.

### 5.5 Forward secrecy

None for this prototype: a stolen `PK` plus recorded traffic exposes past sessions. Listed in section 12.

---

## 6. Message bodies (inside the encrypted frame, field `b`)

All bodies have `additionalProperties: false`. Unknown fields are rejected. Schemas live in `contracts/sync/schemas/`.

| `t` | Direction | Body |
|---|---|---|
| `ping` | both | `{t0}` (sender's clock) |
| `pong` | both | `{t0, t1, t2}`: `t1` responder receive time, `t2` responder send time |
| `events` | both | `{fromSeq, events:[Event...]}` with `Event` exactly as in `core-spec/event.schema.json`. Only that sender's own events |
| `ack` | both | `{upToSeq}` cumulative |
| `state` | both | `{ts, focusState, laptopState?, phoneState?, confidence, declared, heldCount, minutesInState?}` using the enums in `core-spec/context-snapshot.schema.json` |
| `pet_event` | phone to laptop | `{kind: "held"\|"released"\|"nudge"\|"celebrate", count}` |
| `held_list` | phone to laptop | `{total, items:[{heldId, appCat, heldAt, expiresAt, status}]}` at most 50 items; **no text, no sender, no package** |
| `command` | both | `{cmdId, cmd, args, issuedTs, exp}` (section 8) |
| `cmd_ack` | both | `{cmdId, status: "applied"\|"rejected"\|"expired"\|"duplicate", reason?}` |
| `error` | both | `{code, detail?}` codes: `schema_invalid`, `unsupported_version`, `unauthorized`, `expired`, `replay`, `rate_limited`, `too_large` |

Validation on receive: JSON schema for the body, then for `events` each event against the **core-spec event schema plus the forbidden-field walk** (`core-spec/forbidden-fields.json`). A bad event is rejected and counted; good events in the same batch are still applied. `heldId` is a random opaque id with no relation to the notification key.

---

## 7. Time, order, backfill

### 7.1 Clock offset

Every 30 s each side sends `ping {t0}`. The peer answers `pong {t0, t1, t2}`. The requester records `t3` on receipt and computes

```
rtt    = (t3 - t0) - (t2 - t1)
offset = ((t1 - t0) + (t2 - t3)) / 2        # peer clock minus my clock
```

Keep the last 8 samples, ignore samples with `rtt > 2000 ms`, use the sample with the smallest `rtt`. If there is no valid sample, `offset = 0` and the receiver marks cross-device timing as `estimated`.

`tsAdj = ts + offsetToReceiver`. The **receiver** computes `tsAdj` and sets `ingestedTs`. The sender never rewrites `ts`. The phone is the reference time base: laptop events get `tsAdj` in phone time on the phone.

Events with `tsAdj` more than 5 minutes in the future are rejected and counted. Old events are accepted.

### 7.2 Sequence, ack, dedup, replay

- Each device keeps one `seq` counter for its own events (core-spec envelope). `seq` is persisted. **Holes are allowed** (events lost in a crash before they were stored). Holes never block an ack.
- `ack {upToSeq}` means: every event with `seq <= upToSeq` that the sender transmitted has been durably stored. Because the transport is ordered, the receiver acks the highest `seq` it has stored in the batch.
- On `hello`, each side replays its stored events with `seq > peer.lastSeqAcked`, in order, in batches.
- Dedup: by `(device, seq)` and by event `id`. Duplicates are dropped silently and counted. Dedup state survives restarts (it is the stored data itself).
- Reconnect never re-executes anything: telemetry replays are idempotent by dedup; commands are only valid live (section 8).
- If no frame arrives from the peer for 30 s while the relay connection is up, emit `gap {deviceFrom, deviceTo, reason:"peer_silent"}`. If the relay connection is down, emit `gap ... "relay_down"` once at disconnect and once at recovery with the outage window. Schema mismatch emits `gap ... "schema_mismatch"` and the batch is dropped with an `error{schema_invalid}`.

---

## 8. Commands (control plane)

Commands are never inferred from telemetry and are never replayed on reconnect.

| `cmd` | Allowed direction | `args` | Effect | Extra rule |
|---|---|---|---|---|
| `declare_session` | both | `{task, plannedMin (5..480), strict}` | set a declared session (`taskSource=declared`) | cannot override never-hold rules |
| `end_session` | both | `{}` | end it | |
| `show_now` | laptop to phone | `{heldId}` or `{all:true}` | release held items now | always allowed |
| `kill_switch` | both | `{on}` | `on:true` stops all holding (fail open) | **`on:false` is accepted only from the phone** |

Authorization and handling:
1. The frame must be authenticated AEAD from the paired peer's direction. Nothing else is trusted.
2. `issuedTs` and `exp` are checked against the local clock plus the clock offset; `exp - issuedTs <= 60 s`. Expired: reply `cmd_ack{expired}`.
3. `cmdId` (uuid) is looked up in the idempotency store (24 h). Duplicate: reply `cmd_ack{duplicate}` and do nothing.
4. Apply through the phone's or laptop's single public control entry point. Reply `cmd_ack{applied|rejected, reason}`.
5. No command can add or remove never-hold rules, VIPs, allow-lists, change `kappa`, or touch parameters. The set above is closed.
6. Every received command and its result is logged as a diagnostic with ids and status only (no args beyond enums).

---

## 9. Local ingest protocol (extensions to laptop app)

The laptop app runs a WebSocket server bound to **`127.0.0.1` only** at `ws://127.0.0.1:<port>/ingest`.

### 9.1 Handshake and authentication

1. **Origin check at upgrade.** A request whose `Origin` is present must be exactly `chrome-extension://<id>` where `<id>` is in the configured allow-list (`ingest.allowedExtensionIds`). Any `http://`, `https://`, `file://`, or unknown origin is rejected with HTTP 403. A request with **no** `Origin` (the VS Code extension host, Node clients) is allowed to proceed to token authentication.
2. **Token.** The laptop app generates a 32-byte random token on first run, stores it with `safeStorage`, and shows it in the panel with a Copy button. The first client message must be `{"kind":"auth","token":"<b64url>","client":"vscode"|"browser","clientVersion":"x.y.z"}` within 3 seconds, or the socket is closed. Token comparison is constant time. Wrong token: close 1008; five failures in a minute from the same remote port range block that client for 5 minutes.
3. The server replies `{"kind":"auth_ok","serverVersion":"1","config":{"heartbeatSec":5}}` or closes.
4. Only one connection per `client` kind at a time; a new authenticated one replaces the old.

Browsers do not allow custom headers on `WebSocket`, which is why the token travels in the first message and the Origin check does the cross-site protection. Web pages cannot read the extension's storage, and the Origin header of a page script is its own page origin, so the Origin allow-list blocks them.

### 9.2 Messages, extension to laptop

All fields are required unless marked optional. No other fields are allowed.

| `kind` | Fields |
|---|---|
| `auth` | see above |
| `ext_heartbeat` | `{ts}` every 5 s while active |
| `edit_burst` | `{ts, editsPerMin (int 0..2000), saves (int), errors (int), warnings (int), windowSec (int 5..120)}` |
| `resume_first_edit` | `{ts, delayMs (int 0..3600000)}` |
| `vscode_state` | `{ts, state: "flowing"\|"stuck"\|"idle_open", windowFocused (bool), debugging (bool, optional)}` |
| `tab_category` | `{ts, domainCat (one of the 14 categories), surface (feed\|shorts\|watch\|chat\|editor\|other), videoState?: "playing"\|"paused"\|"none", scrollCount? (int), tabActive (bool)}` |

Rules:
- **Counts and categories only.** No document text, file names, URLs, page titles, domains, query strings, search text. The browser extension maps a domain to a category with a built-in table and sends only the category. The `/shorts` vs `/watch` path pattern is used immediately and only the `surface` enum leaves the page.
- The laptop app wraps accepted messages into core-spec events (`edit_burst`, `resume_first_edit`, `tab_category`) with `device:"laptop"`, its own `seq`, `source:"vscode"` or `"browser"`. **Extensions never assign `id` or `seq`.**
- Rate limit 20 messages per second per connection, frame limit 4096 bytes, strict schema, forbidden-field walk, invalid messages dropped and counted (`ingest.invalid`).

### 9.3 Messages, laptop to extension

`auth_ok`, `error {code, detail?}`, `config {heartbeatSec}`. Nothing else.

### 9.4 Stale and offline

If no `ext_heartbeat` for 15 s the laptop marks that extension feed `offline`. Offline feeds never count as focus and never as distraction; inference falls back to window and idle signals.

---

## 10. Persistence and recovery obligations (sync scope)

| Item | Where | Rule |
|---|---|---|
| Outbound event queue | phone: events table + `sync_cursor`; laptop: JSONL + `sync_cursor.json` | replay from `lastSeqAcked`; survives restart |
| Command idempotency store | phone: Room table; laptop: small JSON file | 24 h, bounded size |
| `PK` | see section 4 | never plaintext on disk |
| Laptop JSONL | rotation at 10 MB, keep 14 days, partial last line discarded on start, disk-full means pause writes, keep ring buffer, show a visible warning and emit `gap` | assumptions CA-9 |
| Relay | none | in-memory only; restart means devices reconnect and replay |

---

## 11. Required tests (the contract's test suite)

1. Cross-language crypto vectors (`contracts/sync/test-vectors.json`): fixed private keys give identical ECDH, HKDF outputs, SAS digits, `PK`, `sid`, hello MAC, session keys, and the same ciphertext for fixed plaintexts. Node reference and Kotlin must both match.
2. Schema validation: valid and invalid fixtures for every message and every ingest message.
3. Attack tests via the mock peers: flipped ciphertext byte, replayed frame, old counter, wrong direction, wrong key, expired command, duplicate command, unknown `t`, oversized frame, unauthorized `kill_switch on:false` from laptop, third client joining a rendezvous room, wrong SAS, wrong Origin, wrong token. Each must be rejected with a counted reason, with no state change.
4. Backfill: drop the connection for 60 s while the peer generates events; on reconnect the receiver stores each event exactly once; `tools/verify-sync.mjs` proves it from the two exported files.
5. Privacy regression: scan relay logs and captured relay frames, laptop JSONL, phone export, ingest logs and diagnostics for forbidden keys and for canary strings.

---

## 12. Threat model and non-claims

**Protects against:** a passive network observer; a relay that reads or alters ciphertext after pairing (tampering is detected, replays are rejected); a relay or third party that guesses the pairing code (the SAS confirmation exposes an active man-in-the-middle during pairing); a web page trying to feed fake focus events to the laptop (Origin allow-list plus token plus 127.0.0.1 binding).

**Does not protect against:** compromised endpoints or malware on either device; a user who confirms mismatching digits; metadata leakage (message types, sizes, timing, room id are visible to the relay); loss of forward secrecy if `PK` is stolen; denial of service by the relay (it can drop or delay everything); physical access to an unlocked device; a malicious VS Code or browser extension with the token.

**Claims allowed in the pitch:** "end-to-end encrypted with an authenticated pairing flow and a human confirmation step; prototype-grade; not audited." **Not allowed:** "unbreakable", "production-grade", "zero-knowledge".

---

## 13. Versioning

`contracts/sync/VERSION` holds `1.0.0`. Bump rules: patch for clarifications and test fixtures, minor for backward-compatible additions (new optional field, new message type with a version gate), major for incompatible changes (needs a new `PROTOCOL_VERSION`). `contracts/sync/CHANGELOG.md` records each change and the vectors hash.

---

## 14. Additions and deviations from the spec (owner approval needed)

| ID | Item | Why |
|---|---|---|
| CA-1 | `cmd_ack` message | 25.9: commands need acknowledgement and idempotent handling |
| CA-2 | `held_list` message | 12.5: the laptop panel shows held items with Show-now; counts alone are not enough |
| CA-3 | `error` message and codes | visible, recoverable failures (25.13) |
| CA-4 | `pair_*` messages and SAS | 25.8: authenticated pairing with confirmation on both devices |
| CA-5 | Local ingest messages (section 9) | spec 13 names the channel but not its protocol |
| CA-6 | P-256 ECDH plus commit-reveal SAS replaces "PBKDF2 from the code" | 25.8 says PBKDF2 alone is not adequate; it allows "an appropriate key-agreement design". The code stays as a rendezvous token only |
| CA-7 | `gap` reasons `peer_silent`, `relay_down`, `schema_mismatch` | needed to record sync failures; requires a core-spec minor bump if missing (handled in Prompt C0) |
| CA-8 | `kill_switch on:false` only from the phone | a laptop must never be able to re-enable holding on the phone |
| CA-9 | Constants chosen without spec values: ingest port 47821, JSONL rotation 10 MB and 14 days, stale thresholds 15 s and 30 s, pairing validity 5 min | tune later without a protocol change |

# DHYAN: Stage 7, 8, 9 prompt pack (Codex and Antigravity)

Read `DHYAN_STAGE789_GUIDE.md` first (ownership, branches, merge order, gates). The contract is `docs/contracts/SYNC_CONTRACT.md`.

## Index

| ID | Agent | Branch | When |
|---|---|---|---|
| **Preamble** | both | n/a | paste at the top of **every** prompt below |
| **C0** | whichever is paused | `contracts` | as soon as the contract md is committed; never delays Stage 5 or 6 |
| **7A** | Codex | `s7-laptop` | after Stage 5 is merged |
| **7B** | Codex | `s7-laptop` | after 7A |
| **7C** | Codex | `s7-laptop` | after 7B |
| **8L** | Codex | `s8-laptop-sync` | after `s7-laptop` merged |
| **9W** | Codex | `s9-win-notif` | after `s7-laptop`; last in Stage 9 |
| **9X1** | Antigravity | `s9-ext` | after C0 (idle window before Stage 6 M6) |
| **9X2** | Antigravity | `s9-ext` | after 9X1 |
| **8P1** | Antigravity | `s8-phone` | **only after Stage 6 is merged and Gate 2 passed** |
| **8P2** | Antigravity | `s8-phone` | after 8P1 |
| **9P** | Antigravity | `s9-phone` | after `s8-phone` merged |
| **R** | the *other* agent | read-only | review of a finished branch |
| **Resume** | either | any | session stopped or drifted |

Antigravity first-session settings carry over from the Stage 3 guide: Planning mode, terminal policy **Request review**, one agent at a time, browser off. Codex: run in the worktree directory of the prompt's branch.

---

## Preamble (paste at the top of every prompt)

```text
You are working on Dhyan (an Attention Digital Twin: Kotlin phone app, Electron laptop app, Node relay, two extensions). You are one of two coding agents (Codex and Antigravity) working in parallel in separate git worktrees. The owner coordinates. These rules apply to the whole task:

READ FIRST (read-only unless stated): AGENTS.md, docs/SPEC.md (sections named in the task plus 25.1, 25.8, 25.9, 25.11, 25.12), docs/contracts/SYNC_CONTRACT.md, contracts/sync/ (schemas, vectors, README) when present, COORDINATION.md, HANDOFF_STATUS.md, DECISIONS_PENDING.md, and the NOTES files of the branches you depend on.

IDENTITY: your branch, worktree and notes file are named in the task. Write a running log to NOTES_<branch>.md from the first step: milestone status, commands run with REAL output summaries, assumptions, questions for owner, Contract Change Requests, things NOT verified on target, integration TODOs.

OWNERSHIP: you may edit ONLY the paths listed under "Scope" in the task. Everything else is read-only for you, including core-spec/, docs/contracts/, contracts/sync/, tools/mock-*, tools/verify-sync.mjs, every other platform's directories, and the governance docs (TRACEABILITY.md, HANDOFF_STATUS.md, STAGE_GATES.md, DECISIONS_PENDING.md, COORDINATION.md). If you need a change outside your scope, STOP that part, write it in your notes file ("Questions for owner" or "CCR-<n>: what, why, smallest change, tests affected"), and continue only with unaffected work.

CONTRACT: the sync contract and test vectors are frozen. If code and contract disagree, the contract and vectors win. Never "fix" a mismatch by editing the contract or vectors.

DISCOVER, DO NOT ASSUME: names of classes, packages, buses, DI modules and files from earlier stages must be found by reading the repo. If a name or interface you need does not exist, say so in your notes and report; do not invent a competing version of an existing module.

PRIVACY (non-negotiable): categories and counts only. No notification text, window titles, URLs, domains in events, file names, document text, keystrokes, or message content in any event, JSONL, Room row, relay frame, extension message, log, test fixture committed to the repo, or crash output. Pairing secrets, keys and tokens never go to logs, source control or events.

HONESTY: never claim a command, test, build, install, pairing, relay connection or device behavior worked unless you ran it in this session and can paste its real summary. Anything involving the Reno 14, a real Windows desktop session, a real browser, VS Code, the deployed relay or TLS is "not verified on target" unless you actually did it. Never tune results to match numbers in the spec.

GIT: commit on your branch after each milestone with the message "<branch>-M<n>: <summary>". Never push. Never switch branches. Never touch main. Do not rebase or merge unless the task says so.

DEPENDENCIES: add none unless the task allows it; if allowed, list each in your notes with version and reason. No native addons on the laptop (spec 12.1). No new crypto libraries anywhere (Node `crypto`, Android JCA only).

SHELL: the owner is on Windows PowerShell. Run one command per line; do not rely on "&&". Gradle: `.\gradlew.bat` from phone/. If a command needs approval, ask and wait; never try to bypass the terminal policy.

STOP CONDITIONS: stop and report if tests of an earlier milestone fail twice after fixes, if a contract or spec conflict blocks you, or if you would have to break an ownership rule.
```

---

## Prompt C0: contract implementation (docs-to-code, mocks, vectors)

Branch `contracts`, worktree `..\dhyan-contracts`. Run when the contract md is committed and one agent is paused. Fast to Planning mode is fine; Codex is a good fit (Node and JSON).

```text
[PASTE PREAMBLE]

Task C0: turn docs/contracts/SYNC_CONTRACT.md into executable artifacts. Branch contracts. Notes file NOTES_contracts.md.

Scope (only these): contracts/sync/**, tools/mock-ingest/**, tools/mock-relay/**, tools/mock-peer/**, tools/interop/**, tools/verify-sync.mjs, tools/privacy-scan.mjs, tools/lib/contract-*.mjs, tools/test/contract-*.test.mjs, docs/contracts/README.md, two root package.json script keys (check:contracts, plus a build-vectors script), and, for step C0.0 ONLY, core-spec/ (controlled minor bump).
Allowed dependencies: ajv (already present) and ws (pure JS). Nothing else.

C0.0 Schema delta (the one task allowed to edit core-spec/): check core-spec/event.schema.json gap.reason for peer_silent, relay_down, schema_mismatch. If any is missing, add ONLY the missing ones, bump core-spec/SCHEMA_VERSION and the schema version const to the next minor, update CHANGELOG.md and fixtures, update phone SchemaVersion.kt ONLY IF tools/check-schema-sync.mjs requires it to stay green (that single constant is the only phone edit allowed here; report it), run npm test and tools/check-schema-sync.mjs. If nothing is missing, say so and skip.

C0.1 Schemas: contracts/sync/VERSION (1.0.0), CHANGELOG.md, and JSON Schemas (2020-12, additionalProperties false, size limits) for: outer frames (pair_offer, pair_join, pair_reveal, pair_confirm, pair_cancel, hello, encrypted frame), every encrypted body in contract section 6 (reuse core-spec schemas by $ref or by validator composition for events and state), and every ingest message in section 9. A shared validator module tools/lib/contract-validate.mjs exporting validateFrame, validateBody(type, body), validateIngest(msg), plus the forbidden-field walk from core-spec/forbidden-fields.json. Valid and invalid fixtures for every message.

C0.2 Reference crypto (Node crypto only): contracts/sync/ref/crypto.mjs implementing exactly contract sections 4 and 5: P-256 key pair from a raw private scalar, uncompressed public point, ECDH x-coordinate, HKDF-SHA256, commitment, transcript hash T, SAS six digits, kConf and pair confirm MAC, PK, session room id sid, kHello and hello MAC, session keys from snL||snP, nonce from ctr, AAD string, AES-256-GCM encrypt/decrypt of frames, rendezvous room id from the code. Include the code alphabet and code generator using crypto.randomInt.
Then tools/gen-vectors.mjs producing contracts/sync/test-vectors.json from FIXED inputs (documented constants: two private scalars, nonces, plaintext bodies, ctr values, a sample code) with every intermediate value in hex/base64 and explicit labels. A test regenerates the vectors in memory and asserts they equal the committed file, so drift fails the build. Write contracts/sync/README.md explaining how a Kotlin test should load the vectors and which intermediate values to assert (so the Android implementer can match them step by step).

C0.3 mock-ingest: tools/mock-ingest/server.mjs implementing contract section 9 exactly (127.0.0.1, Origin allow-list from --allow-ext-id, first-message auth with 3 s timeout, constant-time token compare, strict schemas, forbidden-field walk, 20 msg/s limit, 4096 byte frames, one connection per client kind, heartbeat offline detection). CLI: --port 47821 --token <t> --allow-ext-id <id> (repeatable) --log-events (prints category/counter summaries only, never message bodies beyond enums and counts) --emit-demo (sends nothing; prints connection status). Also tools/mock-ingest/attack-suite.mjs: a client test suite that takes --url and --token and runs: wrong origin, no origin with wrong token, no auth in 3 s, oversize frame, rate flood, unknown kind, forbidden key, extra field, second connection replacing the first. It must pass against the mock and be reusable against the real laptop server later.

C0.4 mock-relay: tools/mock-relay/server.mjs, an in-memory relay implementing contract section 3 (room types, allowlists per room type, 2 clients per room, one per role, rendezvous expiry configurable, close codes, size and rate limits) for local tests only. No body logging.

C0.5 mock-peer: tools/mock-peer/index.mjs with --role laptop|phone --relay ws://127.0.0.1:<port> --code <CODE>|--session <pk file>. Implements: pairing (laptop creates code and commit; phone joins; prints the SAS; --auto-confirm confirms automatically for CI), session (hello MAC, keys, encrypted frames with counters, replay rejection), clock offset pings, state heartbeat, event generation (valid core-spec events with correct device, id, seq, tsAdj handling; configurable rate; synthetic:true), ack and backfill from lastSeqAcked (persistent queue file), commands (send and receive, cmd_ack, idempotency), held_list and pet_event (phone role), and ATTACK FLAGS that each produce one specific bad behavior: --tamper, --replay, --old-ctr, --wrong-direction, --wrong-key, --expired-cmd, --duplicate-cmd, --unauthorized-killswitch-off, --bad-sas, --third-client, --oversize, --unknown-type, --schema-bad-event, --forbidden-key. The mock must itself reject the same things when it is the receiver and print a one-line counted reason per rejection ("rejected: replay").

C0.6 Tools: tools/verify-sync.mjs <laptop events.jsonl> <phone export.jsonl> proving every laptop-origin event id appears exactly once on the phone side and every phone-origin event appears exactly once on the laptop side (when provided), no duplicates by (device, seq) or id, per-device seq monotonic apart from allowed holes, reporting holes and tsAdj anomalies, exit non-zero on failure. tools/privacy-scan.mjs <paths...> scanning JSON, JSONL and text logs for forbidden keys (core-spec/forbidden-fields.json), any strings given by --canary (repeatable), pairing code patterns after the --code value, long base64 key-looking blobs in logs, with a clear report and non-zero exit.

C0.7 Tests and scripts: contract tests under tools/test/ for schemas, vectors, mock-ingest against attack-suite, mock-relay plus two mock-peers end to end (pair, session, 100 events, drop and backfill, attacks rejected), verify-sync, privacy-scan (positive and negative). Root scripts: "check:contracts": runs all of them. Run npm test, npm run check:contracts, npm run checks. Paste the real summaries.

Finish with NOTES_contracts.md: what exists, how to run each mock (exact commands for PowerShell), what is not verified, open questions. Do not edit docs/contracts/SYNC_CONTRACT.md. If the contract is ambiguous or inconsistent anywhere you noticed while implementing, list each as a proposed CCR with the smallest fix.
```

---

## Prompt 7A: laptop core (Codex)

Branch `s7-laptop`, worktree `..\dhyan-s7`, notes `NOTES_s7-laptop.md`. Starts after Stage 5 is merged.

```text
[PASTE PREAMBLE]

Task 7A: laptop core (spec 12.1 to 12.4, 25.9 laptop recovery). Branch s7-laptop.
Scope: laptop/**, one root package.json script key (check:s7). Not allowed: relay/, phone/, core-spec/, contracts, tools/mock-*.
Allowed dependencies: ajv and ajv-formats (pure JS). Everything else already present from Prompt 1C. No native addons.

Review the existing laptop/ scaffold first. Then build:

1. Main process: single-instance lock; settings store (JSON in userData) with keys ingest.port (47821), ingest.allowedExtensionIds ([]), jsonl.maxBytes (10 MB), jsonl.retentionDays (14), deviceId (random, generated once, pseudonymous), seq state; a diagnostics object with counters (events written, dropped, invalid, write failures).

2. adapters/: LaptopAdapter interface (spec 12.2). AwAdapter polling every 5 s: discover buckets at runtime by prefix (aw-watcher-window, aw-watcher-afk, aw-watcher-web, optionally aw-watcher-vscode) via GET /api/0/buckets; verify the live API shape instead of assuming it and record what you found in notes. Extract ONLY process/app name, afk status, and for web events the hostname for in-memory category lookup. RawLaptopEvent has no title or url field. Hostname is mapped to a category and dropped; it is never persisted. ActiveWinFallback: foreground process name via a PowerShell one-liner child process (no window title) plus powerMonitor.getSystemIdleTime() behind the same interface; used when AW is unreachable for more than 15 s. Both are tested with fakes.

3. normalizer/: process or domain to category using core-spec/categories.json (import it from the repo path through the build config; do NOT copy and edit it), unknown process stays unknown, label-once store (JSON) with an API the panel can call, games by known list or user label, debounce of window changes shorter than 3 s into the previous state, output window_change and idle_start/idle_end events exactly per core-spec (device laptop). Event envelope per core-spec/event.schema.json: id, schemaVersion, deviceId, device, seq, ts, source, type, payload, synthetic false. A PrivacyGuard validates every event with ajv against the core-spec schema plus the forbidden-field walk BEFORE it reaches the store; rejects are counted, never written.

4. jsonlStore.ts (spec D16, 25.9): in-memory ring buffer (last 2000 events) plus an append-only events.jsonl via a serialized fs.promises.appendFile queue. Rotation at jsonl.maxBytes (rename with timestamp, new file), retention deletion of files older than jsonl.retentionDays. Startup recovery: read the tail, discard a truncated last line (count it), find the max seq. seq allocation: monotonic, persisted high-water mark in seq.json updated every 100 events; after a crash holes are allowed (documented in notes; contract section 7.2). Disk full (ENOSPC) or any write error: pause file writes, keep the ring buffer, set a visible health flag, emit a gap event once, retry with backoff; never throw into collectors.

5. state/focusState.ts: the rule table in spec 12.4 evaluated every 5 s over the last 60 s (away, idle_open, flowing, stuck, browsing, gaming, offline). Pure function plus a thin runner with an injectable clock. Inputs from the adapter and normalizer now; vscode/browser inputs are optional hooks (7C wires them). An onState emitter publishing {laptopState, minutesInState, confidence, ts} on change and every 5 s as a heartbeat. offline when the adapter has reported nothing for 15 s, plus a gap event.

6. Tests (node:test or vitest if already configured): AwAdapter against a fake local HTTP AW server (include responses containing CANARY_TITLE_7f3a and a canary URL in titles/urls; assert they never appear in any event or file); normalizer categories, debounce, label-once; PrivacyGuard rejects forbidden keys; jsonlStore rotation, retention, truncated-last-line recovery, ENOSPC simulation, seq holes after simulated crash; focusState table for every row of spec 12.4 with fixtures; offline detection.

7. Scripts: "check:s7": typecheck, build, tests, and a script that runs the privacy canary test. Run npm install, npm run typecheck, npm run build, npm run check:s7 in laptop/ and the root checks. Paste real summaries. State plainly that `npm run dev` Electron launch and anything on real Windows is not verified unless you actually ran it.

Report: files, real command output, assumptions (note rotation size and retention are assumption CA-9), not verified list, integration hooks 7B and 7C will use (names and signatures).
```

---

## Prompt 7B: Pip, panel, tray (Codex)

```text
[PASTE PREAMBLE]

Task 7B: Pip window, state machine, panel, tray (spec 12.5, 25.13). Branch s7-laptop (continue). Scope: laptop/**. Notes: append to NOTES_s7-laptop.md.

Build:
1. pet/petStateMachine.ts: a PURE TypeScript state machine with an injectable clock. Inputs come from a PetInputBus (define it in laptop/src/pet/petInputBus.ts): local laptop state and focusState; and remote inputs that 8L will feed later (heldCount, heldItems, pet_event kinds held/released/nudge/celebrate, fatigue band, secondScreenOverlap, declared session). Seven behaviors exactly per spec 12.5: deep_focus (sleeping, breathing), notification held (perks up, "1 held" badge), drifting (soft nudge), distracted (worried, offers "resume task"), recovering (cheers), fatigued (yawns, suggests a break), second-screen overlap (raised eyebrow). Define priority and transition rules in a table in code comments; missing or stale inputs must NOT produce deep_focus (gaps are never focus); with no remote inputs only the local behaviors are reachable.
2. pet/bubbles.ts: short, kind, never nagging; per-type cooldown of 10 minutes (injectable clock); a pure function returning the bubble to show or none; tests for the cooldown.
3. Pet window in main: BrowserWindow transparent, frame false, alwaysOnTop with the 'screen-saver' level, skipTaskbar, resizable false. Click-through exactly as spec 12.5: setIgnoreMouseEvents(true, { forward: true }) on the window, #pip-hitbox mouseenter/mouseleave sending 'set-ignore-mouse' over IPC, handler calling setIgnoreMouseEvents(ignore, { forward: true }). Preload with contextIsolation on, nodeIntegration off, a minimal typed IPC surface. Sprites: simple CSS or SVG placeholders per behavior (no copyrighted assets); put credits placeholder in laptop/ASSETS.md so real free assets can be swapped later.
4. Panel (React or plain HTML, whichever 1C used): today's focus time (minutes aggregated from the JSONL of today, with an explicit provenance label "observed"), held items view fed by PetInputBus (explicit empty state: "Not connected to phone" or "None held"), declare-session form and tray entry ("Focus: coding, 2h, strict") that writes a declared_session event with taskSource=declared and updates local state, pairing section as a placeholder behind an interface `PairingUi` (shows "Not paired" now; 8L implements it), what-if mini section marked clearly as pending until the phone is synced (25.13: show an explicit pending state, never fabricated numbers), extension status placeholder (7C fills it).
5. Tray: menu with Declare session, Open panel, Pause Pip, Quit. Pip must be hide-able and must never steal focus.
6. States are first-class: offline, stale, unsynced, no data. No silent zeros.
7. Tests: state machine (each behavior reachable, priority, gap never focus), bubble cooldown, today's-focus aggregation from a fixture JSONL, declared session event shape validated with the PrivacyGuard from 7A.
8. Write laptop/MANUAL_CHECKS.md: exact steps the owner follows to verify click-through to the window behind Pip (open VS Code under Pip's transparent area, click through, then hover the hitbox and see it capture the mouse), the 7 behaviors by injecting inputs via a dev-only "simulate input" menu or CLI flag, and a note about exclusive-fullscreen games hiding Pip.
9. Run typecheck, build, check:s7, root checks; paste real summaries. Click-through and animations are NOT verified until the owner runs MANUAL_CHECKS.md; say so.
```

---

## Prompt 7C: local ingest server and feed adapters (Codex)

```text
[PASTE PREAMBLE]

Task 7C: localhost ingest server (contract section 9) and the VS Code and browser feed adapters (spec 12.4, 13). Branch s7-laptop (continue). Scope: laptop/**. Notes: append to NOTES_s7-laptop.md. Allowed dependency: ws (already present). Use the schemas in contracts/sync/schemas through a build alias or relative import; do not copy them.

Build laptop/src/ingest/:
1. IngestServer: WebSocket server bound to 127.0.0.1 ONLY on ingest.port, path /ingest. At upgrade enforce the Origin rule: an Origin header present must equal chrome-extension://<id> with the id in ingest.allowedExtensionIds, otherwise HTTP 403; no Origin proceeds to token auth. First message must be {kind:"auth", token, client, clientVersion} within 3 s; compare tokens in constant time (crypto.timingSafeEqual); generate a 32-byte random token on first run and store it with Electron safeStorage (inject a fake in tests); five failures per minute blocks that client 5 minutes; one connection per client kind (new authenticated replaces old); reply auth_ok with config. After auth, strict schema validation of every message (contract 9.2), forbidden-field walk, 4096 byte frames, 20 msg/s, invalid messages dropped and counted in diagnostics (ingest.invalid, by reason), no message bodies in logs.
2. Mapping: edit_burst, resume_first_edit, tab_category become core-spec events with device laptop, source vscode or browser, laptop-assigned id and seq, validated by the PrivacyGuard and written through jsonlStore. Extensions never assign id or seq.
3. Feeds: adapters/VsCodeFeed.ts and adapters/BrowserFeed.ts maintain the latest vscode_state, edit rate, error trend, and browser surface/videoState/scroll counts with freshness (15 s without ext_heartbeat means that feed is offline; offline feeds count as neither focus nor distraction). Wire them into state/focusState.ts per spec 12.4: stuck requires vscode_state stuck (or errors rising with low edits), flowing can use edit bursts, browsing uses tab_category surfaces feed/shorts/watch with distracting categories. Keep the table from 7A and add rows' extra conditions; update the tests; do not weaken existing ones.
4. resume delay: when resume_first_edit arrives, attach it to the most recent laptop focus-loss/return window as an outcome signal (store as the event only; EpisodeBuilder on the phone consumes it later). Document the shape in notes.
5. Panel: show the ingest token with a Copy button (token never logged), the allowed extension ids setting, and per-extension status connected/offline with last-seen time.
6. Tests: run the shared attack suite tools/mock-ingest/attack-suite.mjs against YOUR real server (add a test that starts it on a random port with a test token and an allowed id and runs the suite); unit tests for mapping, feeds freshness, state table rows with the feeds; privacy test with canary strings inside extension message fields that must be rejected.
7. Run typecheck, build, check:s7, root checks and paste real summaries. Mark Windows firewall prompts, real VS Code and real browser behavior as not verified.
```

---

## Prompt 8L: relay final and laptop sync (Codex)

Branch `s8-laptop-sync` (rebase on `main` after `s7-laptop` merged), worktree `..\dhyan-s8l`.

```text
[PASTE PREAMBLE]

Task 8L: final relay and the laptop side of sync (contract sections 3 to 8, 10; spec 14, 25.8, 25.9). Branch s8-laptop-sync. Notes NOTES_s8-laptop-sync.md.
Scope: relay/**, laptop/**, one root script key (check:s8l). Not allowed: phone/, extensions, contracts, tools/mock-*, core-spec/.
Allowed dependencies: none new (Node crypto, ws).

Part A, relay/ (follow contract section 3 exactly):
1. Update relay/server.js: two room types (rendezvous vs session) with their own type allowlists; rendezvous rooms created by the first pair_offer, expire at 5 minutes; session rooms expire at 24 h idle; max two clients per room, one per device role, a duplicate role is rejected with 1008; limit 10 join attempts per minute per client IP on rendezvous rooms (when behind Caddy trust X-Forwarded-For only from 127.0.0.1); size limit 64 KB (1009), rate limit 50 msg/s; frames must be valid JSON objects with v === 1 and an allowed t; unknown types close 1008; NEVER log bodies, only room hash prefix, role, t, size, close reason; keep /healthz. The relay never decrypts and never inspects ciphertext.
2. Tests: run against tools/mock-peer in both roles: pair, session, 100 events, rate limit, size limit, unknown type, third client, duplicate role, expiry (injectable TTL). Keep the Caddyfile and deploy.sh working; do not deploy anything.

Part B, laptop/src/sync/ (all crypto through Node crypto; port the logic from contracts/sync/ref/crypto.mjs into TypeScript and prove it with a test that loads contracts/sync/test-vectors.json and asserts EVERY intermediate value):
3. crypto.ts, pairing.ts: laptop-side pairing exactly as contract section 4: generate the code with crypto.randomInt from the contract alphabet, show it via the PairingUi interface from 7B, create the rendezvous room, commit, reveal after pair_join, derive the transcript and the six-digit SAS, wait for the local user's Yes or No (IPC to the panel), exchange pair_confirm MACs, derive and persist PK with Electron safeStorage (never logged), expiry at 5 minutes or 3 failures, visible error states, unpair deleting PK and sid. Rendezvous and session room ids per the contract.
4. session.ts: connect to the relay (wss URL from settings; ws://127.0.0.1 allowed only in dev/test builds), hello with MAC and fresh sn, derive session keys, encrypt and decrypt frames with the counter and AAD rules, reject replays, reorders, wrong direction, bad tags; three tag failures in 60 s close the session; reconnect with the contract backoff and jitter; visible connection states (unpaired, pairing, connecting, connected, unsynced, relay down, error).
5. Data flow: outbound events from the JSONL/ring buffer after lastSeqAcked in batches of at most 50 events, 32 KB, 2 s; inbound events validated one by one against the core-spec schema and forbidden-field walk, deduped by (device, seq) and id, written with tsAdj and ingestedTs, acked cumulatively; ping/pong every 30 s with the offset estimator (min-RTT of last 8, drop RTT over 2000 ms); state heartbeat every 5 s from the laptop state emitter; gap events peer_silent (30 s) and relay_down; schema mismatch handling; unacked retention limits and queue_overflow gap.
6. Commands: send declare_session, end_session, show_now, kill_switch; receive and authorize per contract section 8 (direction rules, expiry, idempotency store file for 24 h, cmd_ack results), apply received declare_session/end_session to the local declared session, never replay on reconnect. Contract rule CA-8: kill_switch with on:false is accepted only from the phone. So the laptop NEVER sends kill_switch on:false (only on:true), and your command-authorization code must implement the generic direction rule so a mock phone-role peer can verify it.
7. Mirror: feed PetInputBus with remote state, heldCount, held_list, pet_event; panel shows held items from held_list with a Show-now button sending show_now (empty/offline/stale states per 25.13); implement PairingUi in the panel (code, SAS, Yes/No, status, unpair with confirmation).
8. Tests: vectors; unit tests for each rule; integration tests with tools/mock-relay and tools/mock-peer --role phone and then the REAL relay/server.js locally: successful pairing with --auto-confirm, bad SAS, expired code, third client, reconnect and backfill after a 60 s simulated drop (use short timers), duplicate-free result checked with tools/verify-sync.mjs on the produced files, every mock-peer attack flag rejected with a counted reason and no state change, privacy scan (tools/privacy-scan.mjs) over relay logs, JSONL and diagnostics with canaries. Script "check:s8l" runs it all.
9. Run laptop typecheck, build, tests, relay tests, root checks, npm run check:contracts. Paste real summaries. Real phone, deployed relay, TLS and Windows are NOT verified.

Report: files, results, assumptions, any contract ambiguity as a proposed CCR, the exact PowerShell commands the owner uses at Gate 3 steps 1 to 9 for the laptop side.
```

---

## Prompt 9W: Windows notification bridge (Codex, time-boxed)

```text
[PASTE PREAMBLE]

Task 9W: Windows notifications bridge (spec 12.6, 25.11). Branch s9-win-notif. Notes NOTES_s9-win-notif.md. Scope: laptop/** (adapters, normalizer, main wiring, panel status line), one root script key (check:s9w). Not allowed: everything else.
This feature is NOT optional scope, but it is explicitly allowed to end in a documented negative result (spec 12.6). Never fake it.

Step 1, feasibility spike (time-box about 30 minutes of effort): determine whether an unpackaged Electron app on this Windows machine can observe toast notifications with category-level information only. Candidates: WinRT UserNotificationListener from a PowerShell child process (check whether package identity or an access prompt blocks it), any other Windows API reachable without a native Node addon. For each candidate record the exact commands, the real output or error text, and the conclusion in the notes. Do not install native build toolchains and do not add node-gyp dependencies.

Step 2, if a candidate works: implement adapters/WinNotifBridge.ts. A child process emits JSON lines containing ONLY the app user model id and a timestamp (the script must never read notification visuals, title or body text). Map the app user model id to appCat through laptop/src/normalizer/aumid-map.json (unknown stays unknown; mark uncertain entries "verify": true). Emit core-spec notif_posted events with device laptop: key = keyed hash, pkg = the app user model id, notifCat other, senderId = keyed hash of the app id, isVip false, urgencyScore a documented conservative constant, hasReplyAction false, repeatCount5m from an in-memory window, isMessagingStyle false. Validate through the PrivacyGuard. Handle bridge crash and restart with backoff, a rate limit, and a visible status in the panel (available, unavailable with reason, not permitted). Feature flag in settings.

Step 3, tests: fake bridge process feeding fixtures; a privacy test where fixture toasts contain CANARY_TITLE_7f3a / CANARY_BODY_7f3a that must appear nowhere (events, JSONL, logs, diagnostics) using tools/privacy-scan.mjs; crash/restart; unknown app ids.

If no candidate works: leave the code behind a disabled flag, set the panel status to "Windows notifications not available" with the reason, write the evidence and the limitation in the notes for the demo notes (spec 12.6), and add a post-demo checklist item (owner, dependency, acceptance test). Do not ship half-working code enabled by default.

Run typecheck, build, check:s9w, root checks. Paste real summaries. Real toast capture on the owner's desktop is not verified until the owner runs it; write the exact manual test steps in laptop/MANUAL_CHECKS.md.
```

---

## Prompt 9X1: VS Code extension (Antigravity)

Branch `s9-ext`, worktree `..\dhyan-ext`. Use Planning mode.

```text
[PASTE PREAMBLE]

You are in Antigravity: use Planning mode. Task 9X1: VS Code extension (spec 13.1, contract section 9). Branch s9-ext. Notes NOTES_s9-ext.md.
Scope: vscode-ext/**, tools/check-ext-privacy.mjs, root script keys check:ext. Not allowed: laptop/, phone/, relay/, contracts, tools/mock-*, core-spec/.
Allowed dependencies: typescript, esbuild (dev), ws (runtime), @types/vscode, @types/node, @vscode/vsce optional dev. Nothing else.

MILESTONE M0 (plan only, then STOP for owner approval): an Implementation Plan artifact with file list, the exact VS Code API events you will use, the privacy approach, the tests, and anything in the contract that is unclear. Write no code before approval.

M1 scaffold: package.json with the extension manifest (activation onStartupFinished), tsconfig, esbuild bundling, settings dhyan.ingestPort (default 47821) and a command "Dhyan: Set ingest token" that stores the token in context.secrets (SecretStorage), a status bar item (connected / offline / not configured). Never log the token.
M2 pure logic (no vscode imports, fully unit tested): aggregator turning counted events into the contract messages: edit_burst every 15 s window (editsPerMin, saves, errors, warnings, windowSec), vscode_state (flowing, stuck, idle_open) with documented rules, resume_first_edit (delayMs from window focus regained to the first edit). Counting rules: edits = number of onDidChangeTextDocument change events; replace/delete proxy = changes with rangeLength > 0; saves; diagnostics counts via languages.getDiagnostics severity counts; debug/task start and end; git commits via the built-in Git extension API (count only, never messages, hashes, or paths); window focus via window.onDidChangeWindowState.
M3 PRIVACY: the extension must never read document text, file names, paths, URIs, language ids, commit messages or diagnostics messages. Do not access contentChanges[].text, document.getText, fileName, uri.fsPath, lineAt, diagnostic.message. tools/check-ext-privacy.mjs fails the build if any of those identifiers appear in vscode-ext/src (allow-list nothing). Unit test that the aggregator's output schema has only the contract fields.
M4 transport: WebSocket client to ws://127.0.0.1:<port>/ingest (reject any other host in settings), first message auth per contract 9.1, handle auth_ok and errors, 5 s ext_heartbeat, reconnect with backoff 1 to 30 s, offline buffering of at most the last 60 s (drop older), rate limit under 20 msg/s, strict message shapes validated against contracts/sync/schemas before sending.
M5 tests and proof: run the extension's transport against tools/mock-ingest (start it in the test with a test token) and assert messages are accepted; run tools/mock-ingest/attack-suite.mjs's client-side expectations where applicable (for example, that your client never sends before auth_ok). Script "check:ext" runs privacy check, typecheck, unit tests. Write vscode-ext/MANUAL_CHECKS.md with exact steps: open the vscode-ext folder in VS Code, press F5 (Extension Development Host), set the token from the laptop panel, type rapidly for 30 seconds, expect edit_burst and state flowing in the laptop panel; create errors and pause to see stuck; check the status bar states.
Report with real command output. Not verified: real VS Code runtime, real laptop server.
```

---

## Prompt 9X2: browser extension (Antigravity)

```text
[PASTE PREAMBLE]

You are in Antigravity: use Planning mode. Task 9X2: browser extension, Manifest V3 for Chrome and Edge (spec 13.2, contract section 9). Branch s9-ext (continue). Notes: append to NOTES_s9-ext.md.
Scope: browser-ext/**, tools/check-ext-privacy.mjs (extend), tools/ext-id.mjs, root script key check:ext. Allowed dependencies: none at runtime; dev: esbuild or none, nothing else.

MILESTONE M0 (plan only, then STOP for approval): plan artifact: manifest and permissions (justify each), file list, the message flow content script to service worker to laptop, how MV3 service worker restarts are handled, the stable-extension-id approach, the tests.

M1 manifest and ID: manifest_version 3; permissions as small as possible (storage, alarms, idle; justify anything more); content script on all pages; service worker. Set a manifest "key" so the extension id is stable: tools/ext-id.mjs generates an RSA key pair with Node crypto, writes the PRIVATE key to a gitignored path outside browser-ext/, puts the public key (base64 DER) into the manifest, and prints the derived extension id (first 16 bytes of SHA-256 of the DER public key, each hex digit mapped 0-f to a-p). The owner pastes that id into the laptop's ingest.allowedExtensionIds.
M2 content script: passive listeners counting scroll and wheel events per minute, video element state (playing, paused, seeks counted, playbackRate), YouTube path pattern (/shorts vs /watch) read from location.pathname and converted IMMEDIATELY to the surface enum; the domain is mapped to one of the 14 categories through browser-ext/src/domain-map.json (a small built-in table; mark uncertain entries "verify": true). Only category, surface, videoState, scrollCount, tabActive leave the content script. NEVER send or store location.href, hostnames, titles, text content, query strings.
M3 service worker: WebSocket to ws://127.0.0.1:<port>/ingest (reject any other host), token and port from an options page stored in chrome.storage.local, first-message auth per contract 9.1, 5 s ext_heartbeat while a tab is active, tab_category on active-tab change, surface change, and every 15 s while active; track the active tab with chrome.tabs.onActivated and visibility messages, not URLs; chrome.idle for idle state; reconnect via chrome.alarms (the service worker can be killed any time) with state persisted in chrome.storage so a restart resumes; backoff 1 to 30 s; strict message shapes validated before sending.
M4 privacy and tests: extend tools/check-ext-privacy.mjs to fail on location.href, document.title, textContent, innerText, hostname leaving the content script, URL objects passed to messages. node:test unit tests for the pure aggregator (scroll counts, surface mapping, category map) and for the message builder against contracts/sync/schemas; transport test against tools/mock-ingest including the rule that a client never sends before auth_ok and never connects to a non-127.0.0.1 host.
M5 docs: browser-ext/MANUAL_CHECKS.md: load unpacked in Chrome and Edge, set token and port in the options page, add the extension id to the laptop settings, open a YouTube Shorts page for 60 seconds and then a /watch page, expect tab_category short_video/shorts then video/watch in the laptop panel and Pip reaction; check behavior after the service worker is stopped from the extensions page.
Report with real output. Not verified: real browsers, real laptop server, real YouTube pages.
```

---

## Prompt 8P1: phone sync core (Antigravity)

**Hard precondition:** Stage 6 merged to `main`, Gate 2 passed. Branch `s8-phone`, worktree `..\dhyan-s8p`. Planning mode.

```text
[PASTE PREAMBLE]

You are in Antigravity: use Planning mode. Task 8P1: phone sync core (contract sections 3 to 8, 10; spec 14, 25.8, 25.9, 25.10). Branch s8-phone. Notes NOTES_s8-phone.md.
PRECONDITION: Stage 6 is merged and the owner has passed Gate 2. If COORDINATION.md says otherwise, stop and report.
Scope: phone/** EXCEPT phone/.../twin/**, actuator/**, safety/** (read-only, call their public entry points). You own the manifest, Gradle files, Room version, DI additions, sync/ package, data/ additions. Allowed dependencies: OkHttp (WebSocket). Crypto ONLY through JCA (KeyPairGenerator "EC", KeyAgreement "ECDH", Mac HmacSHA256, Cipher "AES/GCM/NoPadding", Android Keystore). kotlinx-serialization is already present (Stage 2).

MILESTONE M0 (plan only, then STOP for approval): Implementation Plan artifact: the real names found in the repo for PhoneControl, HeldItemsView, SnapshotSource, the actuation event stream, EventWriter, ClockOffset, sync_cursor, settings table, Koin modules, current Room version and schema export; a file-by-file plan; the Room migration plan (version N to N+1 with a real Migration, exported schema committed, migration test, NO destructive migration); how the test vectors reach the Kotlin tests (Gradle copy of contracts/sync/test-vectors.json into test resources); any contract ambiguity. If PhoneControl, HeldItemsView or SnapshotSource do not exist, say so; do NOT edit actuator/ or safety/ to create them without owner approval.

M1 plumbing: manifest INTERNET permission only; OkHttp dependency; a debug-only network security config permitting cleartext ws:// to the developer's LAN IP and 10.0.2.2 for testing against mock-relay (release config keeps cleartext off; wss only); Room migration for a cmd_log table (cmdId primary key, status, ts) and any pairing metadata table; migration test with the exported schemas.
M2 crypto: sync/crypto/ with P-256 key generation, raw uncompressed point encode/decode (0x04||X||Y), ECDH x-coordinate, hand-written HKDF-SHA256, commitment, transcript hash, SAS six digits, pair confirm MAC, PK, sid, hello MAC, session keys, nonce, AAD, AES-GCM frame encrypt/decrypt, rendezvous room id and the code alphabet. A JVM unit test loads contracts/sync/test-vectors.json and asserts EVERY intermediate value step by step. If any value differs, fix YOUR code, never the vectors; if you believe the vectors are wrong, write a CCR.
M3 pairing: a PairingController state machine exactly per contract section 4 (phone role: join the rendezvous room, send pair_join, verify the laptop's commit, compute SAS, wait for the user's Yes/No, exchange pair_confirm, derive PK and sid, store PK wrapped with an Android Keystore AES-GCM key in the settings table, unpair deletes it). All failure paths visible and recoverable. PK, SAS, code, keys never logged. Expose states as a StateFlow for the UI (8P2). Unit tests for every transition and failure. Then STOP for owner review of M2 and M3.
M4 session client: RelayClient on OkHttp WebSocket: connect to the session room, hello with MAC and fresh sn, session keys, encrypted frames, counters, replay and tamper rejection, three-failure close, reconnect backoff with jitter, visible connection states. Transport behind an interface so JVM tests can use a loopback.
M5 data flow: outbound batches of events after lastSeqAcked (<= 50 events, 32 KB, 2 s) from the events table; inbound events validated one by one with the existing PrivacyGuard plus the schema checks, deduped by (device, seq) and id through the existing unique indexes, saved through EventWriter with tsAdj via ClockOffset (finish ClockOffset using the contract 7.1 estimator) and ingestedTs; cumulative acks; gap events peer_silent, relay_down, schema_mismatch, queue_overflow; state messages on change and every 5 s using SnapshotSource; unacked retention limits.
M6 commands and mirror: received commands authorized per contract section 8, expiry, idempotency through cmd_log, applied ONLY through the PhoneControl facade (never touching safety rules or twin parameters), cmd_ack results; a received kill_switch with on:false from the laptop is rejected (cmd_ack rejected, reason unauthorized) while on:true from the laptop is applied through PhoneControl (turning holding off is always safe; turning it back on is the phone user's decision); outbound pet_event from the actuation event stream and held_list from HeldItemsView on change and on connect.
M7 interop harness: a debug "Sync debug" screen (debug builds only) to set the relay URL and paste a code; tools/interop/ notes already describe running tools/mock-relay and tools/mock-peer --role laptop on the laptop; add phone/README-sync.md with exact PowerShell commands for the owner to run the phone against the mock peers over the LAN, and to run the attack flags against the phone client. JVM tests: attack scenarios using the loopback transport (tamper, replay, old ctr, wrong direction, expired command, duplicate command, unauthorized kill switch, unknown type).
Run .\gradlew.bat :app:testDebugUnitTest, assembleDebug if the SDK exists, npm run checks, npm run check:contracts. Paste real summaries. NOT verified: real device, real relay, TLS, ColorOS network behavior. Commit after each milestone. Stop points: after M0 and after M3.
```

---

## Prompt 8P2: fusion, pairing UI, mirror (Antigravity)

```text
[PASTE PREAMBLE]

You are in Antigravity: use Planning mode. Task 8P2: fusion, second-screen overlap, pairing UI, mirror (spec 8.2 inputs, 11.6, 14.3, 25.9, 25.13). Branch s8-phone (continue). Notes: append to NOTES_s8-phone.md.
Scope: phone/** except twin/**, actuator/**, safety/**. If FocusStateInferrer or the fused snapshot need changes inside those, report.

MILESTONE M0 (plan only, then STOP): plan artifact: what exists in fusion/ and inference/ (read the repo; do not assume), how SnapshotSource is currently bound in Koin, the fusion rules below, UI changes, tests.

M1 ContextFuser: merge the phone snapshot with the laptop state stream (from sync) into a fused SnapshotSource implementation and swap the Koin binding in ONE place without touching collectors or the twin. Rules: laptop data older than 15 s means laptopState = offline and lower confidence, and offline or stale laptop data is NEVER focus; if the relay is down the snapshot says so and confidence drops; declared sessions from either device set taskSource=declared; staleness thresholds are constants in one config object.
M2 second-screen overlap (ASSUMPTION, document in notes): overlap = true when phoneState != screen_off for at least 10 s while laptopState is in {flowing, stuck, idle_open}, with 5 s hysteresis; unit tests including boundary cases and stale laptop. If inference/FocusStateInferrer does not exist in a usable form, implement a minimal rule-based version, mark it clearly as new, and test it.
M3 pairing UI: wire the existing pairing-code field in the Persona and Settings sheet: enter code, show the six SAS digits with Yes/No, show every state from the PairingController and RelayClient (not paired, pairing, SAS check, paired, connecting, connected, unsynced, relay down, wrong code, expired, cancelled, error) as first-class UI states with plain explanations and a retry; Unpair with a confirmation dialog; remote declared-session display; "Unsynced" banner and laptop badge in the Live tab from the fused state (do not alter other tab layouts). UI honesty: provenance and stale labels (spec 25.13).
M4 mirror: confirm pet_event and held_list emission from 8P1 work with the real actuation stream; show remote commands received and their results in a small diagnostics list in the settings sheet (ids and statuses only).
M5 tests: fusion matrix (online, stale, offline, relay down, declared sessions), overlap, PairingController-to-UI state mapping (pure functions), Compose previews or simple UI tests where cheap. Run .\gradlew.bat :app:testDebugUnitTest, assembleDebug if possible, npm run checks. Paste real summaries. Write phone/MANUAL_CHECKS-gate3.md with the owner steps from Gate 3 (guide section 11) for the phone side. NOT verified: device behavior.
Stop points: after M0 and after M3.
```

---

## Prompt 9P: cross-device episodes and snapshot wiring (Antigravity)

Branch `s9-phone`, after `s8-phone` merged.

```text
[PASTE PREAMBLE]

You are in Antigravity: use Planning mode. Task 9P: wire laptop signals into phone episodes and the context snapshot (spec 7.1, 7.2, 25.4, 25.14 cross-device). Branch s9-phone. Notes NOTES_s9-phone.md.
Scope: phone/** except twin/**, actuator/**, safety/**, plus possibly additive changes in twin/episodes ONLY IF the owner approves them at the M0 stop.

MILESTONE M0 (plan only, then STOP): read the repo and report exactly: how EpisodeBuilder currently receives events (bus, types accepted, whether it already uses laptop events window_change, resume_first_edit, tab_category, edit_burst and tsAdj); how the Stage 4 replay and persona path feeds it; where cross-device outcomes would plug in. Say precisely what, if anything, would need an additive change inside twin/episodes, and propose the smallest diff. Do not edit twin/ before approval.

M1 ingest of laptop events: make sure laptop-origin events received by sync reach the same event bus the EpisodeBuilder uses, with tsAdj applied and without duplicates; test with the Stage 4 replay fixtures extended by a laptop event stream.
M2 outcome measurement per spec 7.2: phone dwell from app_foreground/background; laptop outcome from window_change work to non-work and resume from resume_first_edit, falling back to the first non-idle input proxy; without a laptop, dwell only and resumeDelay from the prior flagged estimated. One owner per outcome (25.4); censored and unresolved outcomes stay excluded from ordinary updates. Tests: cross-device episode, resume delay from VS Code, missing laptop, stale laptop, duplicate and late laptop events, clock skew.
M3 snapshot: the fused snapshot now carries laptopState, secondScreenOverlap, recentInterrupts from both devices; verify the fused values reach the Simulate and Live tabs through the existing SnapshotSource; add the provenance label observed/estimated per spec 25.13.
M4 replay proof: extend the replay CLI (read-only usage; do not change the persona generator) to run a recorded laptop+phone fixture and show episodes with cross-device outcomes. Run unit tests, npm run checks, npm run check:contracts. Paste real summaries. Write phone/MANUAL_CHECKS-stage9.md: the Stage 9 exit check steps for the phone side (VS Code edit burst and Shorts session changing the snapshot). NOT verified: devices.
Stop points: after M0 and after M2.
```

---

## Prompt R: independent review of a finished branch (read-only, the other agent)

```text
You are reviewing branch <BRANCH> of Dhyan. DO NOT EDIT ANY FILE and do not commit. You may run tests and checks.

Read: docs/contracts/SYNC_CONTRACT.md, contracts/sync/ (schemas, vectors), docs/SPEC.md sections 12 to 14 and 25.8, 25.9, 25.14, COORDINATION.md, NOTES_<BRANCH>.md, and every file in the branch's scope.

Report:
1. CONTRACT CONFORMANCE: for each contract section that applies, list the code and test that implements it; mark missing or deviating items with file:line.
2. SECURITY FINDINGS: nonce and counter handling, replay and reorder rejection, AAD coverage, SAS commit-reveal order, key storage and logging of secrets, command authorization and idempotency, localhost ingest Origin/token/binding, timing-safe comparisons, resource limits, error paths that leak or fail open incorrectly. Severity: blocker, major, minor.
3. PRIVACY: any path where raw text, titles, URLs, domains, file names, keys or the code could reach events, JSONL, Room, logs, relay frames, fixtures, or screenshots.
4. OWNERSHIP: any edit outside the branch scope or to frozen files.
5. TEST QUALITY: tests that could pass while the code is wrong; propose five concrete mutations (for example skip the commit check, reuse a counter, accept on:false from the laptop, drop the Origin check, accept an expired command) and say which test would catch each. Do not apply them.
6. HONESTY: claims in the notes or docs not backed by command output, and everything marked verified that is really "not verified on target".
7. VERDICT: ready, ready after listed fixes, or not ready, with the shortest list of required changes.
```

---

## Prompt Resume (either agent)

```text
[PASTE PREAMBLE]

Continue the task named below in this worktree. First, without changing anything: run git log --oneline -15 and git status; read NOTES_<branch>.md; determine the last completed milestone from the commit messages "<branch>-M<n>"; run that task's checks and paste the real summary; report any uncommitted work and whether tests pass before touching it. Then continue from the next unfinished milestone. If an earlier milestone's tests fail, fix those first and say what was wrong.
Task and branch: <PROMPT ID AND BRANCH>
Owner correction for this session (may be empty): <ONE LINE>
Remember the stop points defined in the original prompt.
```
</USER_REQUEST>
<ADDITIONAL_METADATA>
The current local time is: 2026-10-25T21:45:56+05:30.
</ADDITIONAL_METADATA>