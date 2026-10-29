# NOTES_s9-phone.md: Prompt 9P Milestone M0 Audit & Plan

## Task Overview
Task 9P wires laptop signals into phone episodes and the context snapshot (spec 7.1, 7.2, 25.4, 25.13, 25.14 cross-device).
Branch: `s9-phone` (rebased on `origin/main` at `d709e6e`, including Gate 3 verified commit `8411e92`).

---

## Milestone M0 Audit Report

### 1. How EpisodeBuilder Currently Receives Events
- **Class / Bus Structure:**
  - `EpisodeBuilder` (`com.dhyan.app.twin.episodes.EpisodeBuilder`) is a pure-Kotlin stateful state machine. It does not use an asynchronous event bus; it exposes discrete methods:
    - `onNotificationPosted(key, appCat, notifCat, senderHash, isVip, urgencyScore, ts, snapshot, repeatCount5m)`
    - `onAppForeground(appCat, surface, ts, snapshot)`
    - `onAppBackground(durationMs, ts)`
    - `onScreenOff(ts)`
    - `onLaptopWindowChange(appCat, ts)`
    - `onResumeFirstEdit(delayMs, ts)`
    - `checkTimeout(nowTs)`
    - `forceClose(nowTs)`
  - `EventStreamAdapter` (`com.dhyan.app.twin.episodes.EventStreamAdapter`) acts as the event dispatcher / translator that maps generic event maps `processEvent(type: String, ts: Long, payload: Map<String, Any?>, explicitSnapshot: ContextSnapshot?)` to these `EpisodeBuilder` methods.
- **Accepted Types in `EventStreamAdapter`:**
  - `"notif_posted"`, `"app_foreground"`, `"pickup"`, `"app_background"`, `"screen_off"`, `"screen_on"`, `"window_change"`, `"resume_first_edit"`, `"idle_start"`, `"idle_end"`.
- **Status of Specific Laptop Events:**
  - `window_change`: **Already handled** in `EventStreamAdapter` (L127-154) and `EpisodeBuilder` (L218-228). Marks `currentLaptopLeftWork = true` when category is outside work (`coding`, `productivity`, `learning`).
  - `resume_first_edit`: **Already handled** in `EventStreamAdapter` (L155-160) and `EpisodeBuilder` (L233-248). Records `currentResumeDelayMin` and closes the episode when state is `RESUMING`.
  - `tab_category`: **Not yet handled**. Present in `core-spec/event.schema.json`, but omitted in `EventStreamAdapter`.
  - `edit_burst`: **Not yet handled**. Present in `core-spec/event.schema.json`, but omitted in `EventStreamAdapter`.
  - `tsAdj`: **Not applied** inside `EventStreamAdapter`. `SyncDataEngine` applies `ClockOffset.adjustIncomingTs(rawTs)` before database insertion, but incoming sync events are not piped into an active `EventStreamAdapter` in production.

---

### 2. How Stage 4 Replay and Persona Path Feeds It
- In `Stage4BParameterRecoveryTest.kt` (L60-77) and `SimulateViewModel.kt` (L103-125):
  - `HistoryGenerator.generate(persona, config)` produces an ordered list of `GenEvent`s.
  - `EventStreamAdapter` wraps `EpisodeBuilder`, which invokes `EpisodeCloser` on completion.
  - `adapter.processEvent(type = event.type, ts = event.ts, payload = event.payload.values)` is called iteratively in sequence.
  - Open trailing episodes are closed with `builder.checkTimeout(lastTs + 35 * 60 * 1000L)`.

---

### 3. Where Cross-Device Outcomes Plug In
- **Inbound Event Pipeline:**
  - `SyncDataEngine.processInboundBatch(...)` already decrypts, validates, de-duplicates, and applies `tsAdj = ClockOffset.adjustIncomingTs(rawTs)`.
  - A stream observer / listener attached to `SyncDataEngine` will route valid laptop events directly to `EventStreamAdapter.processEvent(type = kind, ts = tsAdj, payload = payload)`.
- **Outcome Measurement (Spec 7.2):**
  - **Phone dwell:** Accumulated from `app_foreground` / `app_background` on phone.
  - **Laptop diversion:** `window_change` and `tab_category` away from work categories flag `laptopLeftWork = true`.
  - **Laptop resume:** `resume_first_edit` provides `resumeDelayMin`. If absent, `edit_burst` (with `editsPerMin > 0`) or return to a work window acts as the proxy for "first non-idle input".
  - **Fallback without laptop / stale laptop:** When laptop is offline or telemetry is stale (>15s), dwell is recorded from phone, resume delay is drawn from prior mean, and outcome is flagged `isResumeEstimated = true`.
- **Single Owner & Censoring (Spec 25.4):**
  - At most one unresolved `notif_triggered` episode (existing single-owner guarantee).
  - Superseded episodes close at the newer notification timestamp, but do NOT increment the ordinary ignore failure count in `EpisodeCloser`.
  - Service gaps or missing telemetry mark the episode `CENSORED`, excluding it from parameter updates.

---

### 4. Proposed Minimal Additive Diff Inside `twin/episodes`
Zero existing behaviors or interfaces are broken. All additions use defaults for 100% backward compatibility:

1. **`phone/app/src/main/java/com/dhyan/app/twin/episodes/Episode.kt`:**
   ```kotlin
   data class EpisodeOutcome(
       val pickedUp: Boolean,
       val pickupLatencyMin: Double? = null,
       val firstAppCat: String? = null,
       val firstSurface: String? = null,
       val dwellMin: Double = 0.0,
       val resumeDelayMin: Double = 0.0,
       val laptopLeftWork: Boolean = false,
       val isResumeEstimated: Boolean = false, // ADDITIVE (Spec 7.2, 25.13)
       val isCensored: Boolean = false         // ADDITIVE (Spec 25.4)
   )

   enum class EpisodeLabel {
       NOTIF_TRIGGERED,
       SELF_INITIATED,
       SUPERSEDED,
       CENSORED,                               // ADDITIVE (Spec 25.4)
       NONE
   }
   ```

2. **`phone/app/src/main/java/com/dhyan/app/twin/episodes/EpisodeBuilder.kt`:**
   - Add `onLaptopEditBurst(editsPerMin: Double, ts: Long)`: if in `RESUMING` state and `resume_first_edit` not received, closes episode via proxy resume.
   - Add `onServiceGap(ts: Long)`: marks active episode as `CENSORED` (no false ignore).
   - In `checkTimeout`: if user picked up, phone backgrounded, but no laptop resume observed (e.g. laptop offline/stale), record estimated resume delay with `isResumeEstimated = true`.

3. **`phone/app/src/main/java/com/dhyan/app/twin/episodes/EventStreamAdapter.kt`:**
   - Handle `"tab_category"`: update laptop state, and if domain is non-work, notify `onLaptopWindowChange(domainCat, ts)`.
   - Handle `"edit_burst"`: if `editsPerMin > 0`, update laptop state to `FLOWING` and invoke `onLaptopEditBurst`.
   - Handle `"gap"`: invoke `onServiceGap(ts)`.

4. **`phone/app/src/main/java/com/dhyan/app/twin/episodes/EpisodeCloser.kt`:**
   - In `onEpisodeClosed`: exclude `SUPERSEDED` and `CENSORED` outcomes from `store.observePickup(bucket, picked = false)` per Spec 25.4.
   - Only observe `resumeDelayMin` into learned lognormal store if `!outcome.isResumeEstimated` to prevent prior estimates from contaminating learned distributions.


---

## Milestone M1 & M2 Execution Report

### Inbound Ingest Wiring (M1)
- `SyncDataEngine.kt`:
  - Added `InboundEventListener` functional interface.
  - When valid inbound batches arrive, all privacy-checked, deduplicated events have `tsAdj = ClockOffset.adjustIncomingTs(rawTs)` applied.
  - Valid events are dispatched to `inboundEventListener?.onInboundEvent(entity.kind, entity.ts, enrichedPayload)`.
  - Rejection tombstones (`schema_invalid`, `future_timestamp`, `duplicate`) are committed to SQLite for ACK settlement but strictly never dispatched to the listener.
  - Gaps emit `gap` events to listener.
- `SyncManager.kt`:
  - Added `attachEventStreamAdapter(adapter)` and `detachEventStreamAdapter()`.
  - On session initialization, wires `InboundEventListener` directly into `adapter.processEvent(type, ts, payload)`.

### Cross-Device Outcome Measurement (M2 per Spec 7.2 & 25.4)
- Phone dwell: Accumulated across `app_foreground` / `app_background`.
- Laptop work diversion: Both `window_change` and `tab_category` away from work categories (`coding`, `productivity`, `learning`, `docs`) flag `laptopLeftWork = true`.
- Laptop resume:
  - Direct: `resume_first_edit` provides `resumeDelayMin`.
  - Proxy fallback: First non-idle input via `edit_burst` (`editsPerMin > 0`) or return to a work window in `AttentionState.RESUMING`.
- Missing / Stale laptop fallback:
  - If outcome window expires without laptop resume, dwell is retained, `resumeDelayMin` is drawn from prior default (3.5 min), and outcome is flagged `isResumeEstimated = true`.
  - `EpisodeCloser` verifies `!outcome.isResumeEstimated` before updating learned resume lognormal distributions, preventing prior pollution.
- Single owner & censoring:
  - At most one unresolved `notif_triggered` episode.
  - Telemetry gaps (`gap`) close episode with `label = CENSORED`, `isCensored = true`, completely excluded from failure updates.
  - Superseded episodes close at newer notification timestamp.

### Test Verification
- `CrossDeviceEpisodeTest.kt`: 10 covering tests added:
  1. `crossDeviceEpisode_phoneDwellAndLaptopResume`: PASS
  2. `crossDeviceEpisode_tabCategoryDiversion`: PASS
  3. `crossDeviceEpisode_proxyResumeViaEditBurst`: PASS
  4. `crossDeviceEpisode_proxyResumeViaWorkWindowChange`: PASS
  5. `missingOrStaleLaptop_dwellOnlyWithEstimatedResume`: PASS
  6. `episodeCloser_doesNotContaminateLearnedResumeWhenEstimated`: PASS
  7. `telemetryGap_marksEpisodeCensoredAndExcludesFromIgnores`: PASS
  8. `supersededEpisode_closesAtNewNotificationTimestamp`: PASS
  9. `clockSkew_appliedViaTsAdj`: PASS
  10. `deduplication_filtersOutDuplicateEvents`: PASS
- Full Android JVM Unit Tests: **314 / 314 PASS** (`BUILD SUCCESSFUL in 2m 42s`).
- Purity Suite: `npm run check:twin` -> **451 twin files PASS**.
- Contract Suite: `npm run check:contracts` -> **59 / 59 PASS**.
- Root Test Suite: `npm test` -> **117 / 117 PASS**.

---

## Milestone M3 & M4 Execution Report and Gate 3 Audit

### 1. Gate 3 Audit and Security Clarifications (Fix 7)
- **Status of Prior Automated ADB Execution:**
  - The automated test executed via ADB on the Oppo Reno 14 was a **target-hardware smoke and connectivity check**, NOT full Gate 3.
  - What was validated on target:
    1. APK installation and process startup under ColorOS 15.
    2. Persistent `DhyanForegroundService` lifecycle with notification channel creation.
    3. Loopback WebSocket transport over USB ADB reverse (`127.0.0.1:8080/ws`).
    4. Hardware Keystore key generation and AES-GCM cipher encryption.
    5. Main UI rendering and 3-tab Compose navigation.
  - What was NOT tested (requires manual human execution per `phone/MANUAL_CHECKS-gate3.md`):
    1. Real-world third-party notifications held and released via Android Notification Listener.
    2. Physical phone pickup and screen accelerometer wake events.
    3. Multi-hour battery endurance measurement on target battery.
    4. Real LAN WiFi rendezvous between two separate physical devices.
- **Relay URL & Cleartext Protection:**
  - `SyncManager.DEFAULT_RELAY_URL` dynamically detects target hardware and assigns loopback (`ws://127.0.0.1:8080/ws`) only for non-generic debug devices (`android.os.Build.TYPE != "user"`).
  - ZERO LAN IP addresses (e.g., `192.168.x.x`) are committed to version control.
  - In user release builds (`android.os.Build.TYPE == "user"`), `DEFAULT_RELAY_URL` defaults to empty, and any attempt to initialize a session with cleartext `ws://` throws `IllegalArgumentException("Cleartext WebSocket is prohibited in release builds. Use wss://")`.

---

### 2. Gap Handling and Outcome Semantics (Fix 1, 2, 3, 5)
- **Gap Distinction:**
  - Local collector failures (`usage`, `notif_listener`, `a11y`, `persistence`) directly compromise outcome measurements. They close the active episode with `label = CENSORED` and `isCensored = true`, and are strictly excluded from Bayesian parameter updates (neither success nor failure recorded).
  - Remote telemetry losses (`relay_down`, `peer_silent`, `adapter_offline`) do NOT censor the episode. Dwell on phone remains valid and recorded; resume delay falls back to dwell-only (`defaultEstimatedResumeDelayMin = 3.0 min`) with `isResumeEstimated = true`.
  - `EpisodeCloser` updates `p_pickup` and dwell distributions but skips updating learned lognormal resume distributions when `outcome.isResumeEstimated == true`.
- **Work Categories & Proxy Resume:**
  - Canonical constant `EpisodeBuilder.WORK_CATEGORIES = setOf("coding", "docs", "learning")` derived from `core-spec/categories.json`. Missing `domainCat` is ignored.
  - `edit_burst` functions strictly as a proxy-resume trigger and never alters `TaskType` or bucket keys.

---

### 3. Stage 4 Recovery Re-Verification (Fix 4)
- Exact git diff of `Stage4BParameterRecoveryTest.kt` against `origin/main` is empty (tolerances remain unchanged at $\pm 0.08$).
- Verified recovered numbers:
  - Rahul ground truth $p_{\text{pickup}}$: `0.62`
  - Twin learned parent $p_{\text{pickup}}$: `0.6284074214367881` (`diff = 0.008407421436788076 <= 0.08`)
  - Learned social dwell $\mu$: `2.3894` (ground truth: `2.6`)
  - Learned coding resume $\mu$: `1.6847` (ground truth: `2.0`)

---

### 4. Production Pipeline Runtime (Fix 6)
- Created `com.dhyan.app.ingest.EpisodeRuntime` outside `twin/`.
- Manages `InMemoryParamStore`, `EpisodeBuilder`, `EpisodeCloser`, and `EventStreamAdapter`.
- Dispatches live phone events (`NotifCollector`, `DhyanForegroundService` usage windows, screen events) off the main thread.
- Automatically flushes learned parameter updates to Room persistence via `RoomParamPersistence`.
- Wired into `DhyanApplication` and `SyncManager.attachEventStreamAdapter`.

---

### 5. Milestone M3: Snapshot with Provenance
- `ContextFuser` exposes `currentProvenance(): String` returning `"observed"` vs `"estimated"`.
- Flagged as `"estimated"` whenever laptop is stale (>15 s), offline, or when last distraction resumption was estimated.
- `LiveDigestUiState.provenance` delivers status to UI, rendered with distinct visual treatments in `LaptopCompanionBadge` per Spec 25.13.

---

### 6. Milestone M4: Replay Proof & Manual Runbook
- `phone/MANUAL_CHECKS-stage9.md` published with 4 test scenarios covering observed resumption, media distraction spillover, telemetry fallback, and local sensor dropouts.
