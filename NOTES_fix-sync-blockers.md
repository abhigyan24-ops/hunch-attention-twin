# NOTES: fix-sync-blockers (Prompt AG-FIX Milestone M0)

## 1. Ground Truth Table

| Item | Location / Entity | Ground Truth Evidence |
|---|---|---|
| **Root Workspace** | `c:\Users\abhig\OneDrive\Desktop\IIITN` | `main` at commit `dc29742` (`docs: add Grounding Protocol and VS Code Sensor v2 design`). Working tree clean. [VERIFIED: git status] |
| **Phone Worktree** | `C:\Users\abhig\OneDrive\Desktop\dhyan-s8p` | Branch `fix-sync-blockers` branched from `s9-phone` at commit `55a40d6` (`fix(sync): support peer session re-handshake and record Gate 3 target hardware verification`). [VERIFIED: git log] |
| **Relay Server** | `http://127.0.0.1:8080/healthz` | Running locally as background daemon task-7927. Returned `HTTP 200 OK` (`ok`). [VERIFIED: Invoke-WebRequest] |
| **Target Hardware** | Oppo Reno 14 (Android 16 / API 36) | Currently **disconnected** from adb (`adb devices -l` returned empty). [VERIFIED: adb devices] |
| **Review Document** | `docs/reviews/CX6_REVIEW.md` | Committed on `main` at `ec76473`. Baseline reviewed by Codex: detached commit `b5278ee`. [VERIFIED: git log] |
| **JVM Environment** | OpenJDK 25.0.1, Gradle 9.4.1 | Android SDK `C:\Users\abhig\AppData\Local\Android\Sdk`, platform `android-37.0`. [VERIFIED: gradlew.bat] |

---

## 2. Verification of Codex Findings Against Current Code

### B1 / F1: Duplicate authenticated hello resets counters under unchanged keys
- **Location:** `phone/app/src/main/java/com/dhyan/app/sync/session/RelayClient.kt:256-271`
- **Code Quote:**
  ```kotlin
  val localSn = if (_state.value is SessionState.Connected) {
      secureRandomBytes(16).also { localSnBytes = it }
  } else {
      localSnBytes ?: secureRandomBytes(16).also { localSnBytes = it }
  }
  sendLocalHello(localSn)

  val keys = SyncCrypto.deriveSessionKeys(pk, peerSn, localSn)
  sessionKeys = keys
  outboundCtr = 0L
  inboundCtr = 0L
  ```
- **Security Impact:** [VERIFIED] If peer delivers a second hello on an already-connected socket, `RelayClient` renegotiates keys and resets `outboundCtr = 0L` and `inboundCtr = 0L` on the same connection. This violates Contract Section 5.4 (rekey requires disconnect/reconnect) and risks AES-GCM nonce reuse.
- **Fail-Closed Requirement:** Same-connection duplicate hello or mid-stream renegotiation must be rejected and close the connection with WebSocket code 1008 `"duplicate_hello"`.

### B2 / F2: Phone can persist pairing before local human confirmation
- **Location:** `phone/app/src/main/java/com/dhyan/app/sync/pairing/PairingController.kt:252-276`
- **Current Status:** [VERIFIED] In commit `b5278ee`, peer confirmation persisted immediately without checking local consent. In commit `31ee9a1`, `localConfirmed` and `peerConfirmed` flags were introduced in `persistIfConfirmed()`.
- **Remaining Gap:** `PairingController` depends directly on concrete `KeystoreKeyWrapper` class instead of a `KeyWrapper` interface, forcing JVM tests to use an insecure fallback constructor. Furthermore, pairing lacks a 3-failure lockout rate limit.

### B3 / F3: Production Keystore failure falls back to a known wrapping key
- **Location:** `phone/app/src/main/java/com/dhyan/app/sync/crypto/KeystoreKeyWrapper.kt:20, 26-29, 73-78`
- **Code Quote:**
  ```kotlin
  class KeystoreKeyWrapper(
      private val keyAlias: String = "dhyan_sync_master_key",
      private val isTestFallback: Boolean = false
  ) { ...
      if (isTestFallback) {
          val rawKey = ByteArray(32).also { secureRandom.nextBytes(it) }
          testFallbackKey = SecretKeySpec(rawKey, "AES")
      }
  ```
- **Security Impact:** [VERIFIED] Production class in `main/java` contains test fallback logic and constructor flags. If AndroidKeyStore is unavailable or corrupted, code must fail closed with `IllegalStateException("secure_storage_unavailable")`.
- **Architectural Requirement:** Extract `KeyWrapper` interface into `main/java`. Remove all fallback fields/parameters from `KeystoreKeyWrapper`. Place `TestKeyWrapper` strictly in `src/test/java`.

### B4 / F4 & F5: Envelopes, UUIDs, Room constraints and deduplication
- **Location:** `phone/app/src/main/java/com/dhyan/app/sync/data/SyncDataEngine.kt:112-120` and `phone/app/src/main/java/com/dhyan/app/data/EventEntity.kt:18`
- **Code Quote:**
  ```kotlin
  val reason = when {
      !schemas.event(event) && event["kind"] == null -> "schema_invalid"
      ...
  ```
- **Correctness Impact:** [VERIFIED] `event["kind"] == null` check allowed legacy flat events (missing `type`, `source`, `synthetic`, and nested `payload`) to bypass schema validation and get inserted as `valid` rows instead of `tombstone`.
- **Database Gap:** `EventEntity.kt` has a non-unique compound index `Index(value = ["device_id", "seq", "event_id"])` instead of unique constraints on `(device_id, seq)`.

### B5 / F6, F7, F8: Command validation, reservation, and live context aging
- **Location:** `phone/app/src/main/java/com/dhyan/app/sync/command/SyncCommandDispatcher.kt:41, 73, 94`, `phone/app/src/main/java/com/dhyan/app/fusion/ContextFuser.kt:38-41`, `phone/app/src/main/java/com/dhyan/app/ingest/DhyanForegroundService.kt:105-120`
- **Code Quote:**
  ```kotlin
  if (!schemas.valid("command.schema.json", parsed)) return@withLock false
  ...
  val on = args["on"] as? Boolean ?: true
  val task = args["task"] as? String ?: "coding"
  ```
- **Correctness Impact:** [VERIFIED]
  1. If incoming command fails `command.schema.json`, dispatcher returns `false` without emitting a `cmd_ack` with `status: rejected, reason: schema_invalid`.
  2. Arguments fall back to default values rather than strictly failing closed.
  3. `ContextFuser.snapshot.value` does not automatically age out when read directly after 15 seconds without a manual recompute trigger.
  4. `DhyanForegroundService` never fed live screen or app usage events into `contextFuser.updatePhoneState`.

---

## 3. Real Reproduction Test Outputs (Pasted Execution Traces)

All reproduction tests were executed using `phone/gradlew.bat testDebugUnitTest` in `C:\Users\abhig\OneDrive\Desktop\dhyan-s8p\phone`.

### Test 1: B1 / F1 (Duplicate Hello Counter Reset Reproduction)
- **Command:** `.\gradlew.bat testDebugUnitTest --tests "com.dhyan.app.sync.session.RelayClientTest.secondHelloOnSameConnectionFailsClosedWith1008DuplicateHello"`
- **Exit Code:** `1`
- **Pasted Summary Line:**
  ```text
  > Task :app:testDebugUnitTest FAILED
  RelayClientTest > secondHelloOnSameConnectionFailsClosedWith1008DuplicateHello FAILED
      java.lang.AssertionError at RelayClientTest.kt:389
  1 test completed, 1 failed
  BUILD FAILED in 6s
  ```
- **Failure Trace:**
  ```text
  java.lang.AssertionError: Expected transport to be closed on duplicate hello
      at org.junit.Assert.fail(Assert.java:89)
      at org.junit.Assert.assertTrue(Assert.java:42)
      at com.dhyan.app.sync.session.RelayClientTest.secondHelloOnSameConnectionFailsClosedWith1008DuplicateHello(RelayClientTest.kt:389)
  ```

### Test 2: B3 / F3 (Production Keystore Contains Test Fallback Parameters)
- **Command:** `.\gradlew.bat testDebugUnitTest --tests "com.dhyan.app.sync.crypto.KeystoreKeyWrapperTest.productionKeystoreWrapperHasNoFallbackConstructorOrField"`
- **Exit Code:** `1`
- **Pasted Summary Line:**
  ```text
  > Task :app:testDebugUnitTest FAILED
  KeystoreKeyWrapperTest > productionKeystoreWrapperHasNoFallbackConstructorOrField FAILED
      java.lang.AssertionError at KeystoreKeyWrapperTest.kt:34
  1 test completed, 1 failed
  BUILD FAILED in 3s
  ```
- **Failure Trace:**
  ```text
  java.lang.AssertionError: Production wrapper must not have isTestFallback parameter in constructor
      at org.junit.Assert.fail(Assert.java:89)
      at org.junit.Assert.assertFalse(Assert.java:65)
      at com.dhyan.app.sync.crypto.KeystoreKeyWrapperTest.productionKeystoreWrapperHasNoFallbackConstructorOrField(KeystoreKeyWrapperTest.kt:34)
  ```

### Test 3: B4 / F4 (Legacy Flat Events Bypass Schema Validation)
- **Command:** `.\gradlew.bat testDebugUnitTest --tests "com.dhyan.app.sync.data.SyncDataEngineTest.inboundLegacyFlatEventWithoutCanonicalEnvelopeMustBeRejectedAsSchemaInvalid"`
- **Exit Code:** `1`
- **Pasted Summary Line:**
  ```text
  > Task :app:testDebugUnitTest FAILED
  SyncDataEngineTest > inboundLegacyFlatEventWithoutCanonicalEnvelopeMustBeRejectedAsSchemaInvalid FAILED
      org.junit.ComparisonFailure at SyncDataEngineTest.kt:107
  1 test completed, 1 failed
  BUILD FAILED in 3s
  ```
- **Failure Trace:**
  ```text
  org.junit.ComparisonFailure: expected:<[tombstone]> but was:<[valid]>
      at org.junit.Assert.assertEquals(Assert.java:117)
      at org.junit.Assert.assertEquals(Assert.java:146)
      at com.dhyan.app.sync.data.SyncDataEngineTest$inboundLegacyFlatEventWithoutCanonicalEnvelopeMustBeRejectedAsSchemaInvalid$1.invokeSuspend(SyncDataEngineTest.kt:130)
  ```

### Test 4: B5 / F8 (Malformed Command Does Not Emit Rejected CmdAck)
- **Command:** `.\gradlew.bat testDebugUnitTest --tests "com.dhyan.app.sync.command.SyncCommandDispatcherTest.malformedCommandArgsMustSendRejectedCmdAckWithSchemaInvalid"`
- **Exit Code:** `1`
- **Pasted Summary Line:**
  ```text
  > Task :app:testDebugUnitTest FAILED
  SyncCommandDispatcherTest > malformedCommandArgsMustSendRejectedCmdAckWithSchemaInvalid FAILED
      java.lang.AssertionError at SyncCommandDispatcherTest.kt:236
  1 test completed, 1 failed
  BUILD FAILED in 3s
  ```
- **Failure Trace:**
  ```text
  java.lang.AssertionError
      at org.junit.Assert.fail(Assert.java:87)
      at org.junit.Assert.assertTrue(Assert.java:42)
      at com.dhyan.app.sync.command.SyncCommandDispatcherTest$malformedCommandArgsMustSendRejectedCmdAckWithSchemaInvalid$1.invokeSuspend(SyncCommandDispatcherTest.kt:253)
  ```

### Test 5: B5 / F7 (ContextFuser Direct Snapshot Flow Does Not Age Out)
- **Command:** `.\gradlew.bat testDebugUnitTest --tests "com.dhyan.app.fusion.ContextFuserTest.advancingClockPast15sWithoutManualRecomputeLeavesDirectSnapshotStale"`
- **Exit Code:** `1`
- **Pasted Summary Line:**
  ```text
  > Task :app:testDebugUnitTest FAILED
  ContextFuserTest > advancingClockPast15sWithoutManualRecomputeLeavesDirectSnapshotStale FAILED
      java.lang.AssertionError at ContextFuserTest.kt:148
  1 test completed, 1 failed
  BUILD FAILED in 3s
  ```
- **Failure Trace:**
  ```text
  java.lang.AssertionError: expected:<OFFLINE> but was:<FLOWING>
      at org.junit.Assert.fail(Assert.java:89)
      at org.junit.Assert.assertEquals(Assert.java:120)
      at com.dhyan.app.fusion.ContextFuserTest.advancingClockPast15sWithoutManualRecomputeLeavesDirectSnapshotStale(ContextFuserTest.kt:148)
  ```

---

## 4. Minimal Fix Design per Milestone (M1 through M5)

### Milestone M1: Crypto & Session Lifecycle (B1 / F1)
1. **RelayClient.kt:**
   - In `handlePeerHello`: If `_state.value is SessionState.Connected`, immediately call `transport.close(1008, "duplicate_hello")`, set `_state.value = SessionState.Failed("duplicate_hello")`, and return without modifying keys or counters.
   - Outbound counter serialization: Ensure `sendEncrypted` atomically allocates `outboundCtr` and computes AES-GCM ciphertext within `@Synchronized` block so concurrent coroutines cannot interleave nonce assignment.

### Milestone M2: Pairing & Key Storage (B2 / F2 & B3 / F3)
1. **KeyWrapper Interface:**
   - Define in `main/java/com/dhyan/app/sync/crypto/KeyWrapper.kt`:
     ```kotlin
     interface KeyWrapper {
         fun wrap(plaintext: ByteArray): String
         fun unwrap(wrappedBase64: String): ByteArray
     }
     ```
2. **KeystoreKeyWrapper.kt:**
   - Implement `KeyWrapper`. Remove `isTestFallback` constructor argument and `testFallbackKey` field. Throw `IllegalStateException("secure_storage_unavailable")` immediately if AndroidKeyStore fails to initialize.
3. **TestKeyWrapper.kt:**
   - Define strictly in `src/test/java/com/dhyan/app/sync/crypto/TestKeyWrapper.kt` implementing `KeyWrapper` for local unit tests.
4. **PairingController.kt:**
   - Inject `KeyWrapper` interface instead of concrete `KeystoreKeyWrapper`.
   - Add attempt-bound failure counter: 3 failed MAC verifications close transport with `1008` and trigger `PairingState.Failed("pairing_lockout")`.

### Milestone M3: Data Engine, Envelopes & Room Persistence (B4 / F4 & F5)
1. **SyncDataEngine.kt:**
   - In `processInboundBatch`: Enforce strict `WireSchemas.current.event(event)`. Drop the `event["kind"] == null` workaround. Legacy non-canonical flat events must be stored as `settledStatus = "tombstone"` with `reason = "schema_invalid"`.
2. **EventEntity.kt & Room Constraints:**
   - Ensure deduplication within batch and database enforces uniqueness on `(device_id, seq)` and `event_id`.
3. **DhyanForegroundService.kt:**
   - In `onUsageWindow` and `onScreenEvent`: Call `SyncManager.recordLocalEvent` so local phone usage/screen events are saved to `EventDao` and sent to `ContextFuser`.

### Milestone M4: Commands & Context Fusion (B5 / F6, F7, F8)
1. **SyncCommandDispatcher.kt:**
   - Validate `command.schema.json` closed schema. If schema invalid, extract `cmdId` and send `cmd_ack` with `status: "rejected"`, `reason: "schema_invalid"`.
   - Remove argument defaulting (`kill_switch.on` must be boolean, `declare_session` must have `task`, `plannedMin`, `strict`).
2. **ContextFuser.kt:**
   - In `snapshot` StateFlow: Ensure reading `currentSnapshot()` or accessing `snapshot` flow evaluates staleness against `config.laptopStaleMs` so expired laptop state is transitioned to `OFFLINE`.

### Milestone M5: Episodes, Semantics & Verification (F9, F10)
1. **EpisodeCloser.kt:**
   - Treat `SUPERSEDED` episodes as censored/excluded from negative pickup updates per Spec 25.4 rule 2 (remove negative pickup update for `SUPERSEDED`).
2. **Validation:**
   - Verify `Stage4BParameterRecoveryTest` passes with unchanged thresholds (`+-0.08`).

---

## 5. Explicit Scope & Untouchable Files List

- **Untouchable / Read-Only Files:**
  - `twin/**` (except `twin/episodes/**` for F9)
  - `core-spec/**` (frozen)
  - `contracts/**` (frozen)
  - `laptop/**` and `relay/**`
  - Manifest permissions (already contains `specialUse`)
- **Owned Scope for M1–M5 Fixes:**
  - `phone/app/src/main/java/com/dhyan/app/sync/**`
  - `phone/app/src/main/java/com/dhyan/app/fusion/**`
  - `phone/app/src/main/java/com/dhyan/app/ingest/**`
  - `phone/app/src/main/java/com/dhyan/app/twin/episodes/**`
  - `phone/app/src/test/java/com/dhyan/app/sync/**`
  - `phone/app/src/test/java/com/dhyan/app/fusion/**`
  - `phone/app/src/test/java/com/dhyan/app/twin/episodes/**`
