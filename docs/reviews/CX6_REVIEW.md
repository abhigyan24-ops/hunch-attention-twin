# CX-6: independent phone review

Verdict: **NOT READY for Gate 3.** Existing JVM tests pass, but the production wiring and contract compatibility have blocking gaps. This review does not accept 8L or certify any phone/cloud result.

Reviewed detached commit `b5278eecc708efeea5843866f911c729a63f1c39` (s9-phone), including ancestor `8411e9220ce624b538bad82ac2741bd9114c585b` (s8-phone). Comparison baseline: `d709e6e`. Main remains `ff47466`. Review completed 2026-10-30, Asia/Calcutta.

All findings below are source-derived unless explicitly marked executed. No defect was reproduced on a device, and no mutation was applied. No tracked source/test/docs were edited; no commit, merge, push, deployment, adb command or hardware test was performed. Reports and terminal captures live outside the repository. Tests ran in a separate managed detached worktree, with one Gradle build at a time.

## Executed checks

| Command | Actual result |
| --- | --- |
| Phone: `$env:ANDROID_HOME='C:\\Users\\abhig\\AppData\\Local\\Android\\Sdk'; .\\gradlew.bat --offline --no-daemon --max-workers=1 :app:testDebugUnitTest` | Exit 0; BUILD SUCCESSFUL in 7m 50s; 26 actionable tasks: 26 executed |
| JUnit XML aggregation | 75 suites; 314 tests; failures 0; errors 0; skipped 0 |
| `npm run checks` | Exit 0; schema-sync PASS 1.1.0 / 1 Kotlin file; twin-purity PASS 97 files; no-persona-imports PASS 376 production files |
| `npm run check:contracts` | Exit 0; tests 59; pass 59; fail 0; cancelled 0; skipped 0; duration_ms 1240.018 |
| `npm run check:twin` | Exit 0; twin-purity PASS 97 files |

Environment: Java 25.0.1, Gradle wrapper 9.4.1, installed Android platform android-37.0. Unit-test compilation ran; assembleDebug, release build and APK installation were not run in this review. The wrapper status showed no running Gradle daemons before starting. No second Gradle build was started.

Raw captures: [phone-unit.log](C:/Users/abhig/.codex/visualizations/2026/10/25/01a17242-41b4-7ad1-9939-146f993dce6d/CX6-phone-review/phone-unit.log), [checks.log](C:/Users/abhig/.codex/visualizations/2026/10/25/01a17242-41b4-7ad1-9939-146f993dce6d/CX6-phone-review/checks.log), [contracts.log](C:/Users/abhig/.codex/visualizations/2026/10/25/01a17242-41b4-7ad1-9939-146f993dce6d/CX6-phone-review/contracts.log), [twin.log](C:/Users/abhig/.codex/visualizations/2026/10/25/01a17242-41b4-7ad1-9939-146f993dce6d/CX6-phone-review/twin.log). Passing frozen-contract tests does not exercise the phone implementation against the laptop.

## Findings requiring fixes

### F1 — P1: duplicate authenticated hello resets counters under unchanged keys

[phone/app/src/main/java/com/dhyan/app/sync/session/RelayClient.kt:185](C:/Users/abhig/.codex/worktrees/cx6-readonly-review/IIITN/phone/app/src/main/java/com/dhyan/app/sync/session/RelayClient.kt:185), especially lines 225–231, derives session keys and resets both counters on every valid peer hello. There is no already-handshaken/seen-nonce guard. Replaying the same authenticated hello on the same socket therefore derives the same keys and restarts outbound counter 1. This permits AES-GCM nonce reuse and resets inbound replay protection. The client also resends its own hello each time.

Fix: make same-connection duplicate hellos idempotent, prohibit renegotiation/reset under an existing nonce pair, and serialize outbound counter allocation with encryption/send. Current `sendEncrypted` mutates shared outbound state without synchronization. Add duplicate-hello, old-frame-after-hello, and concurrent-send tests. Existing replay tests cover ordinary encrypted-frame replay, not this reset path.

### F2 — P1: phone can persist pairing before local human confirmation

[phone/app/src/main/java/com/dhyan/app/sync/pairing/PairingController.kt:229](C:/Users/abhig/.codex/worktrees/cx6-readonly-review/IIITN/phone/app/src/main/java/com/dhyan/app/sync/pairing/PairingController.kt:229) verifies the laptop confirmation MAC and then persists the pairing key/room at lines 247–255 without checking that the phone owner pressed Yes. `confirmSas(true)` sends a MAC but records no local-consent flag. A valid peer confirmation arriving while the phone is still displaying SAS can complete pairing.

Also, lines 96–100 send pair_join immediately instead of waiting for the immutable laptop offer; the controller has no implemented pairing expiry timer. Fix with an explicit attempt-bound state machine: offer first, local confirmation AND authenticated peer confirmation before persistence, cancellation/expiry invalidating pending work. Clear byte-array secrets where feasible. Existing happy-path test presses Yes before delivering the peer MAC, so misses the order that matters.

### F3 — P1: production Keystore failure silently selects a known shared wrapping key

[phone/app/src/main/java/com/dhyan/app/sync/crypto/KeystoreKeyWrapper.kt:25](C:/Users/abhig/.codex/worktrees/cx6-readonly-review/IIITN/phone/app/src/main/java/com/dhyan/app/sync/crypto/KeystoreKeyWrapper.kt:25) falls back automatically when AndroidKeyStore is unavailable; lines 67–69 also catch key-creation errors and install a deterministic source-defined AES key. This is reachable with the default production constructor, not just explicit JVM test mode. A Keystore failure must fail closed with a visible re-pair/storage error; a test wrapper must be explicitly injected and unreachable in production. The current test only checks round-trip using `isTestFallback=true`.

### F4 — P1: event and state encoders do not implement the frozen wire contract

[phone/app/src/main/java/com/dhyan/app/sync/data/SyncDataEngine.kt:30](C:/Users/abhig/.codex/worktrees/cx6-readonly-review/IIITN/phone/app/src/main/java/com/dhyan/app/sync/data/SyncDataEngine.kt:30) defaults peerDeviceId to the role string `laptop`; lines 104–107 compare that against incoming `deviceId`. Real 8L uses an installation identifier, so canonical laptop events fail identity validation and receive no ACK. `SyncManager` constructs the engine without replacing these defaults at [phone/app/src/main/java/com/dhyan/app/sync/SyncManager.kt:200](C:/Users/abhig/.codex/worktrees/cx6-readonly-review/IIITN/phone/app/src/main/java/com/dhyan/app/sync/SyncManager.kt:200).

Outbound encoding at [phone/app/src/main/java/com/dhyan/app/sync/data/SyncDataEngine.kt:57](C:/Users/abhig/.codex/worktrees/cx6-readonly-review/IIITN/phone/app/src/main/java/com/dhyan/app/sync/data/SyncDataEngine.kt:57) uses top-level `kind/appCat/surface/durationMin` and omits required `source/type/synthetic`; it can generate a new UUID each export for an entity with no id. Inbound parsing at lines 123–126 reads `kind` and top-level appCat instead of canonical `type` and payload; listener enrichment overwrites actual payload appCat with the fallback. PrivacyGuard is not a full closed-schema validator.

Other incompatible bodies: line 238 sends string `confidence:"uncalibrated"` where [contracts/sync/schemas/state.schema.json:41](C:/Users/abhig/.codex/worktrees/cx6-readonly-review/IIITN/contracts/sync/schemas/state.schema.json:41) requires a number. [phone/app/src/main/java/com/dhyan/app/sync/command/SyncCommandDispatcher.kt:107](C:/Users/abhig/.codex/worktrees/cx6-readonly-review/IIITN/phone/app/src/main/java/com/dhyan/app/sync/command/SyncCommandDispatcher.kt:107) lowercases held-item status, while frozen fixtures use uppercase `HELD`; its total counts only the truncated list. These must be fixed without changing frozen schemas. Add real canonical fixtures and cross-implementation tests.

### F5 — P1: persistent identity, dedup and durable resume are incomplete

[phone/app/src/main/java/com/dhyan/app/data/EventEntity.kt:44](C:/Users/abhig/.codex/worktrees/cx6-readonly-review/IIITN/phone/app/src/main/java/com/dhyan/app/data/EventEntity.kt:44) defaults id to null and seq to zero. The migration sets existing rows to those defaults. There is no complete production local-event allocation path populating canonical identities; foreground collection still has persistence TODOs at [phone/app/src/main/java/com/dhyan/app/ingest/DhyanForegroundService.kt:95](C:/Users/abhig/.codex/worktrees/cx6-readonly-review/IIITN/phone/app/src/main/java/com/dhyan/app/ingest/DhyanForegroundService.kt:95). Export queries use `seq > afterSeq`, skipping default-zero rows when the cursor is zero.

[phone/app/src/main/java/com/dhyan/app/data/EventEntity.kt:18](C:/Users/abhig/.codex/worktrees/cx6-readonly-review/IIITN/phone/app/src/main/java/com/dhyan/app/data/EventEntity.kt:18) has a non-unique compound index, not constraints enforcing independent (device,seq) and id uniqueness. Inbound dedup at [phone/app/src/main/java/com/dhyan/app/sync/data/SyncDataEngine.kt:142](C:/Users/abhig/.codex/worktrees/cx6-readonly-review/IIITN/phone/app/src/main/java/com/dhyan/app/sync/data/SyncDataEngine.kt:142) checks the database before the batch insert, missing repeated ids within the same batch. Re-receiving an existing id inserts another tombstone row rather than reusing its settlement. Room's list insert can be transactional; that alone does not make identity checks plus persisted cursor atomic.

`highestSettledSeq` is memory-only (line 36), not restored from SyncCursorDao. Process restart advertises a reset cursor. Header checking accepts schema versions starting with `1.` and numeric-to-Long coercions. Stored ts is overwritten with tsAdj (line 154), losing the original timestamp rather than preserving both. Require stable identities, schema-exact validation, database uniqueness, durable settlement/cursor recovery and original-ts/tsAdj separation. Test same-batch duplicates, conflicting seq, crash/restart and legitimate holes.

### F6 — P1: sync and episode methods are not connected to the live application

Production-source search finds no callers of `exportOutboundBatch`, `sendStateSnapshot`, `sendPing` or `emitGapEvent` outside their definitions/internal recursion. `onPongReceived` is not connected to ClockOffset. [phone/app/src/main/java/com/dhyan/app/sync/SyncManager.kt:245](C:/Users/abhig/.codex/worktrees/cx6-readonly-review/IIITN/phone/app/src/main/java/com/dhyan/app/sync/SyncManager.kt:245) handles state/events/command but no ACK/backfill state. There is no application heartbeat/peer-silence loop, outbound drain or ACK-driven retry loop.

`attachEventStreamAdapter` at [phone/app/src/main/java/com/dhyan/app/sync/SyncManager.kt:78](C:/Users/abhig/.codex/worktrees/cx6-readonly-review/IIITN/phone/app/src/main/java/com/dhyan/app/sync/SyncManager.kt:78) has no production caller. Actual EpisodeBuilder/Closer/adapter construction is in the synthetic SimulateViewModel; the live inbound listener uses a nullable adapter that is never attached. Thus direct episode tests do not establish real-event learning/persistence. Wire the existing public facades to a lifecycle-owned live consumer and Room-backed param store, with startup/shutdown/restart integration tests. Do not change frozen twin semantics to conceal missing wiring.

### F7 — P1: local context is disconnected and cached laptop state does not age out

[phone/app/src/main/java/com/dhyan/app/fusion/ContextFuser.kt:65](C:/Users/abhig/.codex/worktrees/cx6-readonly-review/IIITN/phone/app/src/main/java/com/dhyan/app/fusion/ContextFuser.kt:65) exposes `updatePhoneState` but has no production caller. [phone/app/src/main/java/com/dhyan/app/ui/model/LiveContextHolder.kt:16](C:/Users/abhig/.codex/worktrees/cx6-readonly-review/IIITN/phone/app/src/main/java/com/dhyan/app/ui/model/LiveContextHolder.kt:16) delegates to the fuser while `update` writes only the separate local snapshot. Once fusion emits, local activity can be masked by default phone SCREEN_OFF/task UNKNOWN.

The fuser only recomputes on explicit updates; `currentSnapshot` returns the cached value (line 35). No timer/read-time refresh enforces the 15-second stale boundary. A last FLOWING state can remain cached while the laptop intentionally suppresses unmapped state snapshots but keeps transport alive. Remote declared sessions also win even when stale/disconnected (line 127). adapterHealthy is not consumed. Add a fake-clock live-wiring test that advances time without manually calling recompute, plus collector-offline versus healthy-unclassified cases.

### F8 — P1: command effects precede durable idempotency reservation

[phone/app/src/main/java/com/dhyan/app/sync/command/SyncCommandDispatcher.kt:51](C:/Users/abhig/.codex/worktrees/cx6-readonly-review/IIITN/phone/app/src/main/java/com/dhyan/app/sync/command/SyncCommandDispatcher.kt:51) checks cmd_log, executes PhoneControl, and only then records the result. Two coroutines can both pass the lookup; a crash after actuation but before logging permits repeated execution. Reserve the command durably before effects and define recovery for in-flight/unknown outcomes.

Closed-schema command validation is absent: missing kill_switch.on defaults to true (line 60); declare_session fills missing fields (lines 81–83). Expiry accepts now==exp, does not validate exp>=issuedTs, and uses local time without the sync clock. Arbitrary command/id strings can enter logs before schema validation. Reject malformed inputs with controlled reasons and bounded metadata; test boundary time/skew, concurrency and restart. Positive: laptop `kill_switch on:false` is rejected, and actual effects use PhoneControl rather than bypassing actuator internals.

### F9 — P2: 9P violates requested episode boundaries

[phone/app/src/main/java/com/dhyan/app/twin/episodes/EventStreamAdapter.kt:175](C:/Users/abhig/.codex/worktrees/cx6-readonly-review/IIITN/phone/app/src/main/java/com/dhyan/app/twin/episodes/EventStreamAdapter.kt:175) changes TaskType to CODING on edit_burst, which CX-6 explicitly prohibits. Lines 192–193 censor on every gap without checking that it is a local collector gap. Remote/relay gaps must not become phone ignore/censor evidence. Work-category membership is duplicated between adapter and builder, not one WORK_CATEGORIES constant.

[phone/app/src/main/java/com/dhyan/app/twin/episodes/EpisodeCloser.kt:67](C:/Users/abhig/.codex/worktrees/cx6-readonly-review/IIITN/phone/app/src/main/java/com/dhyan/app/twin/episodes/EpisodeCloser.kt:67) still treats SUPERSEDED as an ordinary negative pickup observation. That behavior predates 9P; this review does not call it a new regression, but the new [phone/app/src/test/java/com/dhyan/app/twin/episodes/CrossDeviceEpisodeTest.kt:331](C:/Users/abhig/.codex/worktrees/cx6-readonly-review/IIITN/phone/app/src/test/java/com/dhyan/app/twin/episodes/CrossDeviceEpisodeTest.kt:331) explicitly expects the misleading failure update. Resolve against spec25.4 and the requested censor semantics rather than preserving a contradictory assertion.

Positive: estimated resume is excluded from observeResume in both relevant branches; the new test verifies no learned-resume update. Stage4BParameterRecoveryTest remains unchanged, including its 0.08 threshold. Episode changes are not purely additive behavior: existing edit/gap interpretation changes as well as adding fields/methods.

### F10 — P2: DD-10 does not actually select only specialUse at runtime

[phone/app/src/main/AndroidManifest.xml:72](C:/Users/abhig/.codex/worktrees/cx6-readonly-review/IIITN/phone/app/src/main/AndroidManifest.xml:72) declares `specialUse|dataSync`; [phone/app/src/main/java/com/dhyan/app/ingest/DhyanForegroundService.kt:51](C:/Users/abhig/.codex/worktrees/cx6-readonly-review/IIITN/phone/app/src/main/java/com/dhyan/app/ingest/DhyanForegroundService.kt:51) still calls two-argument startForeground. Adding specialUse is not evidence that the dataSync type/time limit has been avoided. Use an explicit reviewed runtime service-type selection and handle applicable timeout behavior.

Android documents a six-hour-per-24-hour background allowance for dataSync on apps targeting Android15+; the two-argument foreground call uses manifest service types. See [Service reference](https://developer.android.com/reference/android/app/Service) and [foreground-service timeout rules](https://developer.android.com/develop/background-work/services/fgs/timeout). A 12-hour Reno survival claim remains **not verified on target**.

## Required review matrix

| Area | Assessment |
| --- | --- |
| Crypto primitives | P-256 SEC1 uncompressed points, ECDH, HKDF-SHA256, direction keys, 12-byte zero-prefix/big-endian counter nonce and AAD are implemented. Seven crypto vector tests ran as part of the 314 tests. |
| Every intermediate vector asserted? | Not completely. Tests cover room id, point encoding, commit, ECDH, transcript/SAS, confirmations/PK/room and directional keys, and decrypt both fixed frames. Step7 encrypts then round-trips but does not assert encryption equals the fixed expected ciphertext. Explicit nonce/AAD intermediate assertions and wrong/malformed-point cases are missing. Primitive success cannot validate counter lifecycle (F1). |
| Pairing/session | Visible pairing/connected/reconnecting/failed state types and UI mapping exist. Commitment and peer-MAC checks exist, but F1/F2/F3 block acceptance. Commit/confirm use contentEquals; hello uses MessageDigest.isEqual. Three recent hello-MAC/tag failures have close paths and tests. No equivalent attempt-bound pairing timeout/three-failure policy was found. Backoff is 1.5^attempt with early integer rounding, not the contract 1/2/4/8/16/30 schedule. |
| Frames/queues | Direction l2p and increasing ctr checks exist; authenticated plaintext over32768 bytes is rejected. There is no complete closed-schema validation before dispatch. Outbound batching caps 50 and shrinks large batches, but no complete persistent bounded-queue/retention/gap pipeline is wired (F6). Gap emitter uses kind laptop_state and arbitrary reason rather than canonical gap envelope/token validation. |
| Room migration | v2→v3 uses ALTER/CREATE, not destructive migration; exported schema3 exists. The test records execSQL strings through a proxy: it does not open a v2 database, preserve rows, migrate, and validate Room's v3 schema. F5 remains. |
| ACK/clock | ACK is sent after list insertion, which is the correct ordering direction. No ACK on malformed identities is sensible. Cursor restart, uniqueness, missing sender ACK handler and missing ClockOffset hookup prevent claiming reliable durable backfill. Original ts must be retained alongside adjusted time. |
| Commands | PhoneControl is used; laptop kill-switch-off rejected; cmd_ack and sequential duplicate/expiry tests exist. F8 prevents exactly-once/strict-validation claims. |
| Episodes | Direct tests cover cross-device dwell/resume, estimated exclusion, censoring, skew and duplicates. No live attachment/persistence evidence; task/gap/superseded issues remain. Four twin/episodes production files changed; no other twin production paths changed in this branch range. |
| Manifest/build | INTERNET was already present; new permission is FOREGROUND_SERVICE_SPECIAL_USE, consistent with DD-10 intent. Release cleartext stays disabled; permissive network config is debug-only. Debug config includes literal private-network .0 addresses; includeSubdomains does not make these CIDR ranges, so a real LAN host is not covered. No device-specific host address was verified. |
| Logging/privacy | No println/Log/logger/printStackTrace calls found under production sync/fusion paths. Tombstones omit rejected raw payloads, a positive. This is not a runtime privacy-scan of device logs/storage. Strict schema validation is missing, so PrivacyGuard alone is insufficient. |
| Scope/frozen files | d709e6e..b5278ee has no changes under core-spec/, contracts/ or docs/contracts/. Changes are phone implementation/tests and phone notes/runbook. No frozen schema was changed to make these tests pass. Shared governance was not updated by this branch range. |
| Claims/process | Commit8411e92 says Gate3 verified on Reno14, and NOTES_s8-phone includes device-verification assertions. This review cannot promote those to owner acceptance. A runtime SAS is recorded in notes; redact it. An additional hex value is described as Keystore-persistence evidence: classify whether it is SID or key material before deciding rotation; this report deliberately does not reproduce it or assume it is PK. |

## Five mutation/regression probes (proposed, not executed)

| Mutation / failure injection | Test required to catch it |
| --- | --- |
| Reprocess the same valid peer hello after sending ctr1 | Add RelayClientTest.duplicateHelloDoesNotResetCounters: next outbound ctr must increase; replayed old ciphertext must remain rejected. Existing normal replay test is insufficient. |
| Deliver valid laptop pair_confirm before local Yes | Add PairingControllerTest.peerConfirmBeforeLocalConsentDoesNotPersist: settings writes remain zero until both confirmations; No/cancel/timeout must invalidate it. |
| Force production Keystore creation/load failure | Add KeystoreKeyWrapperTest.productionFailureNeverUsesFallback using an injected key backend: visible failure, no wrapped credential saved; explicit test-only backend still round-trips. |
| Remove/alter canonical event type/source or use an installation UUID instead of role as deviceId | Add SyncDataEngine canonical-vector/real-8L interoperability tests validating outbound through frozen validators and importing canonical laptop frames; existing hand-built kind/role fixtures must be replaced. |
| Advance fake clock past15s without sending a state update, while heartbeat continues | Add a runtime fusion test reading SnapshotSource directly: laptop becomes stale/OFFLINE, never focus; local phone events must still propagate. Existing tests that explicitly call recompute conceal the missing scheduler. |

Additional essential tests: real SQLite v2→v3 preservation/Room validation; same-batch duplicate id and seq conflicts; cursor recovery after restart; concurrent command reservation and crash recovery; malformed command args; gap-origin separation; edit_burst preserves task; all supplied mock-peer attack modes against the actual phone session. None of those additional probes were executed or added in this read-only review.

## Shortest required fix sequence

1. Antigravity fixes session nonce lifecycle, both-human pairing consent/expiry and production Keystore fail-closed behavior, each with regression tests.
2. Implement frozen canonical event/body validation, stable identity and durable cursor/command reservation; exercise real laptop↔phone contract interchange.
3. Wire outbound/ACK/heartbeat/clock/gap loops and live episode/param persistence; connect local context and enforce stale expiry independently of incoming updates.
4. Correct 9P task/gap/superseded semantics through reviewed changes; preserve the Stage4 recovery threshold. Make DD-10 runtime selection explicit.
5. Re-run unit/contract checks and real migration/interoperability tests; obtain independent AG-4 review of8L. Only after reviewed phone integration, current relay deployment and owner-recorded Gate2 should the owner perform real Gate3 and record evidence.

No fixes, merge, push or deployment are authorized by this report itself. Cloud wss/TLS, real-network pairing, Keystore hardware behavior, Reno14 APK/UI/service survival and real Gate3: **not verified on target**.

