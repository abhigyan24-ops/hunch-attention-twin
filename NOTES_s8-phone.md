# NOTES_s8-phone.md: Phone Sync Core (Stage 8P1)

**Branch:** `s8-phone`  
**Base:** `main` (commit `d572f3e` with Gate 2 PASSED on Reno 14 target hardware)  
**Contract Baseline:** `SYNC_CONTRACT.md` v1.1.0  
**Owner Decisions:** `docs/OWNER_DECISIONS.md` (DD-1..DD-15 signed)  

---

## Milestone M0: Implementation Plan & Repository Inventory
- **Status:** APPROVED by Owner on 2026-10-29.
- **Inventory Confirmed:**
  - `PhoneControl`: `com.dhyan.app.actuator.PhoneControl` (exists, public facade)
  - `HeldItemsView`: `com.dhyan.app.actuator.HeldItemsView` (exists, read-only snapshot provider)
  - `SnapshotSource`: `com.dhyan.app.ui.model.SnapshotSource` (exists, interface)
  - `ClockOffset`: `com.dhyan.app.ingest.ClockOffset` (extended with Section 7.1 rolling estimator)
  - `RoomDecisionLogStore`: `com.dhyan.app.data.RoomDecisionLogStore` (exists, records actuation decisions)
  - `SyncCursorDao` & `SettingsDao`: exist in Room DB
  - Room version: 2 -> upgraded to 3 (schema `3.json` exported)
- **Key Architectural Decisions:**
  - Android 15 foreground service: updated to `specialUse` per DD-10 to survive >6 hour limits.
  - Room migration 2 -> 3: non-destructive migration adding `cmd_log` table and updating `events` with canonical envelope identity & tombstone status.
  - Transactional settlement: bad identity rejects whole batch; malformed payload commits metadata-only tombstone and never raw text; atomic Room transaction before cumulative ACK dispatch.

---

## Milestone M1: Plumbing (Commit `615894a`)
- **Manifest:** Added `android.permission.INTERNET`, updated `DhyanForegroundService` to `specialUse|dataSync` with `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` per DD-10.
- **Network Security:** `network_security_config.xml` (debug permits cleartext `ws://` to LAN IPs and `10.0.2.2`; release keeps strict TLS `wss://` only).
- **Dependencies:** Added OkHttp (`com.squareup.okhttp3:okhttp:4.12.0`) in `phone/app/build.gradle.kts`.
- **Room Database v3:**
  - Entity `CmdLogEntity` & DAO `CmdLogDao`
  - Canonical envelope columns in `EventEntity`: `eventId`, `seq`, `deviceId`, `settledStatus`, `schemaVersion`.
  - Non-destructive `MIGRATION_2_3` in `DhyanDatabase.kt`.
  - Exported schema `phone/app/schemas/com.dhyan.app.data.DhyanDatabase/3.json` (22.0 KB).
  - Verified `DhyanDatabaseMigrationTest` PASS.
- **Schema version:** Bumped to `"1.1.0"` in `SchemaVersion.kt` and `SchemaVersionTest.kt`.
- **Test Vectors Copy Task:** Configured `copySyncTestVectors` Gradle task.

---

## Milestone M2: JCA Cryptography (Commit `6776b7c`)
- **Pure JCA Implementation (`SyncCrypto.kt`):**
  - Ephemeral P-256 key generation (`KeyPairGenerator`).
  - SEC1 uncompressed point encoding and decoding (`0x04 || X || Y`).
  - ECDH shared secret derivation (32-byte X-coordinate via `KeyAgreement`).
  - RFC 5869 HKDF-SHA256 (Extract and Expand).
  - Transcript hash (`SHA-256("dhyan-transcript-v1" || ...)`).
  - 6-digit Short Authentication String (`XXX XXX`, `790 350` vector match).
  - Confirmation MACs (`HMAC-SHA256` with `kConf`).
  - Permanent pairing key `PK` and session room `sid` derivation.
  - Hello MAC derivation (`kHello`) and calculation.
  - Session key derivation (`k_l2p` and `k_p2l`).
  - AES-256-GCM authenticated encryption/decryption with 12-byte big-endian counter nonces and ASCII AAD.
- **Keystore Key Wrapper (`KeystoreKeyWrapper.kt`):**
  - Wraps permanent pairing key using Android Keystore AES-GCM (with JVM test fallback).
- **Vector Verification (`SyncCryptoVectorsTest.kt`):**
  - Verified 100% byte-for-byte match on all 7 test vector steps in `contracts/sync/test-vectors.json`.

---

## Milestone M3: Pairing Controller (Commit `6f24485`)
- **State Machine (`PairingController.kt`):**
  - Finite state machine per Contract Section 4.
  - States: `Idle`, `Joining`, `WaitingOffer`, `WaitingConfirmation(sas)`, `Confirming`, `Paired`, `Failed`.
  - Enforces commit verification before showing SAS.
  - Immediate zeroization and cleanup of ephemeral key material upon completion or failure.
  - Secure credential persistence in `settings` table.
  - Clean `unpair()` removing stored keys.
- **Unit Tests (`PairingControllerTest.kt`):**
  - Full handshake, commit mismatch rejection, user cancellation, and unpair verified PASS.

---

## Milestone M4: Session Client (`RelayClient`) (Commit `3197906`)
- **Session Framing (`RelayClient.kt`, `SessionState.kt`, `SessionTransport.kt`):**
  - Authenticated hello handshake with fresh 16-byte random nonces (`snP`, `snL`).
  - Mutual HMAC-SHA256 hello MAC verification (3 failures in 60s close connection with code 1008).
  - Symmetric key derivation from `snL || snP`.
  - AES-256-GCM framing with 12-byte nonces (`0x00000000 || ctr`) and AAD (`dhyan|1|<type>|p2l|<ctr>`).
  - Strict counter sequencing; rejection of replayed or reordered frames (`ctr <= inboundCtr`).
  - Strict 32 KB plaintext limit enforcement.
  - Policy enforcement: 3 tag failures in 60s close connection with code 1008 (`tag_failure_limit`).
  - Built-in ping/pong response and keepalive.
  - Exponential reconnect backoff with jitter.
- **Unit Tests (`RelayClientTest.kt`):**
  - 5/5 tests PASS (handshake, framing, replay rejection, tag limit, pong response).

---

## Milestone M5: Data Flow (`SyncDataEngine`) (Commit `d91ed02`)
- **Outbound Streaming:**
  - Bounded batch exporter (<= 50 events, 32 KB) querying `EventDao.getOutboundAfterSeq`.
  - Encrypted `events` frame transmission.
- **Inbound Settlement (Contract Section 16):**
  - Transactional batch verification: monotonic seqs, valid UUID, sender device identity, schemaVersion compatibility.
  - Partial acceptance: valid events saved with `settledStatus = "valid"`.
  - Privacy violations or future timestamps (> 5 min) saved as metadata-only tombstones (`settledStatus = "tombstone"`, never storing rejected payload or text).
  - Deduplication: duplicate IDs settled as controlled duplicate tombstones.
  - Atomic Room commit before sending cumulative `ack { upToSeq }`.
- **Clock Offset Estimator (`ClockOffset.kt`):**
  - Contract Section 7.1 rolling estimator: RTT filtering (<= 2000 ms), window of 8 samples, selection of sample with minimum RTT.
  - Adjusts peer timestamps into phone reference time.
- **Privacy Enforcement (`PrivacyGuard.kt`):**
  - Strict recursive inspection against 22 forbidden fields and canary regex patterns (URLs, emails, canary tokens).
- **Context State Sync:**
  - Periodic (5 s) snapshot publishing using `SnapshotSource` (`confidence = "uncalibrated"`).

---

## Milestone M6: Commands & Mirror (`SyncCommandDispatcher`) (Commit `d91ed02`)
- **Command Handling:**
  - Strict 60s expiry check against local clock and clock offset.
  - 24h idempotency via `CmdLogDao`.
  - Remote kill switch authorization: `kill_switch { on: false }` from laptop peer is REJECTED with `cmd_ack{status:"rejected", reason:"unauthorized"}`. Fail-open `kill_switch { on: true }` is applied.
  - Remote `show_now { heldId }` or `show_now { all: true }` releases held items via `PhoneControl`.
  - Remote `declare_session` and `end_session` applied.
- **Actuation & Held Items Mirror:**
  - `sendHeldList()` exports up to 50 active held items (opaque `heldId`, `appCat`, `heldAt`, `expiresAt`, `status`; zero notification text or titles).
  - `sendPetEvent()` mirrors actuation events (`held`, `released`, `nudge`, `celebrate`).

---

## Milestone M7: Interoperability Harness & Runbook (Commit `d91ed02`)
- **Runbook:** Created `phone/README-sync.md` documenting PowerShell commands for running `tools/mock-relay`, `tools/mock-peer`, pairing, and running attack suites (`bad-sas`, `disable-kill-switch`, `tag-failure`, `replay`, `expired`).
- **Full Test Suite Status:**
  - Phone unit tests: **283 / 283 PASS, 0 failures, 0 errors**.
  - Root checks:
    - `npm run checks`: PASS (1.1.0, 444 twin files, 336 production files).
    - `npm run check:contracts`: 59 / 59 PASS, 0 failures.


---

## Stage 8P2: Multi-Device Fusion, Second-Screen Overlap, Pairing UI & Mirror

### Milestone M0: Plan & Architecture
- **Status:** APPROVED by Owner in `stage8p2-plan.md` artifact.
- **Scope:** Zero modifications to `twin/**`, `actuator/**`, `safety/**`, or `core-spec/`. Fail open and never-hold rules preserved.

### Milestone M1: Context Fuser & Live Context Wiring
- Created `phone/app/src/main/java/com/dhyan/app/fusion/FusionConfig.kt`: governs 15s staleness timeout, 0.2 relay disconnect drop, 0.3 stale laptop drop, 10s debounce, 5s hysteresis.
- Created `phone/app/src/main/java/com/dhyan/app/fusion/ContextFuser.kt`: implements `SnapshotSource`. Fuses local phone telemetry and remote laptop activity into unified `ContextSnapshot`. Enforces staleness reset to `OFFLINE` and prefers remote declared focus sessions.
- Updated `phone/app/src/main/java/com/dhyan/app/ui/model/LiveContextHolder.kt`: added `attachFusedSource(source: SnapshotSource)` to delegate snapshot reading directly to `ContextFuser` without touching collectors or decision pipeline.

### Milestone M2: Second-Screen Overlap & Inferrer
- Created `phone/app/src/main/java/com/dhyan/app/fusion/SecondScreenDetector.kt`: enforces 10s continuous debounce on entry when phone is active while laptop is in `{FLOWING, STUCK, IDLE_OPEN}`, and 5s hysteresis on exit. Immediately resets to false if laptop state is stale or offline.
- Created `phone/app/src/main/java/com/dhyan/app/inference/RuleBasedFocusStateInferrer.kt`: minimal rule-based focus state inferrer for fused phone/laptop contexts.

### Milestone M3: Pairing UI & Top-Level Coordinator
- Created `phone/app/src/main/java/com/dhyan/app/ui/settings/SyncUiState.kt`: `SyncUiStatus` sealed class and pure mapper `SyncUiStateMapper`.
- Created `phone/app/src/main/java/com/dhyan/app/sync/SyncManager.kt`: top-level coordinator managing pairing lifecycle, encrypted session transport, bidirectional event streaming, command dispatching, context fusion, and actuation mirroring.
- Updated `phone/app/src/main/java/com/dhyan/app/DhyanApplication.kt`: initializes `SyncManager` at application startup.
- Updated `phone/app/src/main/java/com/dhyan/app/ui/viewmodel/SettingsViewModel.kt`: exposes pairing actions, sync status, relay URL, credentials flag, remote declared session, and recent remote command logs.
- Updated `phone/app/src/main/java/com/dhyan/app/ui/tabs/LiveDigestTab.kt`: renders `LaptopCompanionBadge` showing companion state, freshness indicator, and purple `SECOND-SCREEN OVERLAP` pill.
- Updated `phone/app/src/main/java/com/dhyan/app/ui/settings/SettingsBottomSheet.kt`: added `CrossDeviceSyncSection` with status badge, SAS 6-digit confirmation cards with Match/Mismatch buttons, Pairing inputs, Paired companion controls with Unpair dialog, Remote declared session card, and recent remote command audit table.
- Added `OkHttpRendezvousTransport` in `phone/app/src/main/java/com/dhyan/app/sync/pairing/RendezvousTransport.kt`.

### Milestone M4: Actuation Mirroring Verification
- Wired `ActuationCoordinator` pet events (`held`, `released`, `nudge`) and `HeldItemsView` stream to encrypted outbound messages via `SyncCommandDispatcher`.
- Authored `ActuationMirrorTest.kt` verifying pet event translation and held list emission.

### Milestone M5: Tests & Gate 3 Runbook
- Authored comprehensive test classes:
  - `phone/app/src/test/java/com/dhyan/app/fusion/ContextFuserTest.kt`
  - `phone/app/src/test/java/com/dhyan/app/fusion/SecondScreenDetectorTest.kt`
  - `phone/app/src/test/java/com/dhyan/app/inference/RuleBasedFocusStateInferrerTest.kt`
  - `phone/app/src/test/java/com/dhyan/app/sync/ActuationMirrorTest.kt`
  - `phone/app/src/test/java/com/dhyan/app/ui/settings/SyncUiStateMapperTest.kt`
- Authored `phone/MANUAL_CHECKS-gate3.md` detailing the 6 step-by-step target hardware verification checks for the owner on Oppo Reno 14.
- Ran all verification suites:
  - Phone JVM Unit Tests: **304 / 304 PASS, 0 failures, 0 errors**.
  - Contract Checks: **59 / 59 PASS, 0 failures**.
  - Root Tests (`npm test`): **117 / 117 PASS, 0 failures**.
- Target hardware status: **VERIFIED ON TARGET** on Oppo Reno 14 (CPH2737 / GAZ9KV9DLN6DIZGE, Android 15 / ColorOS 15) via automated ADB test script (`scratch/gate3_e2e_reno14.mjs`):
  - P-256 ECDH Commit-Reveal & SAS Match verified on real hardware (`061 394` identical).
  - Keystore wrapping & Room persistence verified (`09ab54762631caf50cd2ecc1e1a62fd65db42ac184fa1df37afd0320de3b0776`).
  - Encrypted AES-256-GCM session established with local relay over `adb reverse tcp:8080 tcp:8080`.
  - Remote declared focus session mirrored into UI (`REMOTE DECLARED FOCUS SESSION`, `STRICT`, `TASK: CODING`).
  - Remote command executed & applied on Reno 14: `show_now` confirmed in UI with status `APPLIED`.
