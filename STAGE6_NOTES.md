# STAGE 6 NOTES

Branch: `stage6-actuator`

## M0 Pre-flight Findings

### Existing DB state (version = 1, exportSchema = false)
All required tables already defined as Room entities:
- `held_notifs` — `HeldNotifEntity` (needs rework: missing `mechanism`, `state`, `ciphertext`, `hold_until_ms`, `expires_at_ms`; has `deliver_after_ms`, `decision`, `delivered` instead)
- `decisions` — `DecisionEntity` (good shape; no text columns ✓)
- `feedback` — `FeedbackEntity` (has free-text `comment` — user-initiated, kept)
- `settings` — `SettingsEntity` ✓
- `vip` — `VipEntity` ✓

**Action:** Upgrade `HeldNotifEntity` to full Stage 6 schema, bump DB version to 2 with a real migration, enable `exportSchema = true`.

### Decision contract types (twin/decision/, FROZEN)
- `Action` = ALLOW | DELAY | DIGEST | SILENT
- `ActuationStatus` = HELD | SNOOZED | SCHEDULED | RELEASED | EXPIRED | FAILED_OPEN | ACTUATION_UNKNOWN
- `PolicyMode` = OFF | DRY_RUN | LIVE
- `KillSource` = USER_TILE | USER_NOTIFICATION | USER_SETTINGS | SYSTEM
- `DecisionProvider.decide(facts, snapshot, settings, nowMs): Decision` — synchronous, no-throw
- `DecisionProvider.recheck(facts, decision, settings, nowMs): Recheck` — synchronous, no-throw
- `DecisionLogStore.append(record: DecisionRecord)` — suspend

### Actuator package (actuator/) — currently empty. Build from scratch.

### UI package (ui/) — fake DhyanStateHolder exists. Will be replaced with real ViewModels in M3/M4/M5.

### NotifCollector — `onEvent` lambda exists. M6 will wire it to the actuation pipeline.

---

## File List Per Milestone

### M1 — Persistence
- `data/HeldNotifEntity.kt` — upgraded (mechanism, state, ciphertext, hold_until_ms, expires_at_ms)
- `data/HeldNotifDao.kt` — upgraded (state queries)
- `data/DecisionEntity.kt` — add actuationStatus column
- `data/DecisionDao.kt` — add actuationStatus update
- `data/DhyanDatabase.kt` — version 2, exportSchema=true, Migration1_2
- `phone/app/schemas/com.dhyan.app.data.DhyanDatabase/2.json` — exported schema
- `actuator/Clock.kt` — Clock interface + SystemClock + FakeClock
- `actuator/HeldStore.kt` — interface + RoomHeldStore + InMemoryHeldStore (fake)
- `actuator/cipher/HeldTextCipher.kt` — interface
- `actuator/cipher/KeystoreHeldTextCipher.kt` — AES-256-GCM, random 12-byte IV
- `actuator/cipher/FakeHeldTextCipher.kt` — javax.crypto, for JVM tests
- `actuator/ActuatorConfig.kt` — all constants
- `data/RoomDecisionLogStore.kt` — implements DecisionLogStore
- `docs/ACTUATOR_CONTRACT.md` — tables, state transitions, status semantics
- `phone/app/src/test/.../actuator/HeldStoreFakeTest.kt`
- `phone/app/src/test/.../actuator/cipher/CipherRoundTripTest.kt`

### M2 — Actuator Core
- `actuator/NotificationGateway.kt` — interface + AndroidNotificationGateway + FakeNotificationGateway
- `actuator/ActuationCoordinator.kt` — 11-step state machine
- `actuator/MechanismChooser.kt` — pure function
- `actuator/digest/DigestPoster.kt` — interface + AndroidDigestPoster
- `actuator/digest/DigestBuilder.kt`
- `actuator/release/ReleaseService.kt`
- `actuator/release/ReleaseJobScheduler.kt` — interface + WorkManagerReleaseJobScheduler
- `actuator/ReleasedKeyRegistry.kt` — 24h loop guard
- `actuator/KillSwitchController.kt` + `KillSwitchEffects.kt`
- `actuator/BootActuatorReceiver.kt`
- `actuator/DhyanTileService.kt` — Quick Settings tile
- `actuator/debug/FakeDecisionProvider.kt` — debug/test source set ONLY
- `tools/check-actuator-contract.mjs`
- `package.json` — add `check:actuator` script

### M3 — Tab 2, Live & Digest (real data)
- `ui/viewmodel/LiveDigestViewModel.kt`
- `ui/tabs/LiveDigestTab.kt` — rewrite with real data
- `ui/model/UiModels.kt` — add real held notif + decision row models

### M4 — Tab 1, Simulate & Lab
- `ui/viewmodel/SimulateViewModel.kt`
- `actuator/PersonaSandbox.kt` — separate InMemoryParamStore
- `ui/tabs/SimulateTab.kt` — rewrite with real TwinEngine

### M5 — Tab 3 + Settings
- `ui/viewmodel/InsightsViewModel.kt` + `SettingsViewModel.kt`
- `ui/tabs/InsightsTab.kt` — scaffold + real counts
- `ui/settings/SettingsBottomSheet.kt` — real settings persistence

### M6 — Integration (after Stage 5 merged)
- `collectors/NotifExtractor.kt` — add isOtpLike, hasEmergencyKeyword, hold payload
- Wire `NotifCollector.onEvent` → pipeline
- Replace FakeDecisionProvider with real in production DI
- `docs/ACTUATOR_COMPAT_MATRIX.md`
- Gate 2 checklist in this file

---

## Assumptions

- **S6-1:** DB is version 1 in the wild (only debug builds). Safe to do a real migration.
- **S6-2:** `exportSchema = true` with path set in build.gradle. Schema JSON exported to `phone/app/schemas/`.
- **S6-3:** The existing `HeldNotifEntity` columns that conflict with the new schema (`deliver_after_ms`, `decision`, `delivered`) will be replaced in the migration.
- **S6-4:** `FakeDecisionProvider` lives in `phone/app/src/debug/java/...` — excluded from release variant.
- **S6-5:** Keystore alias = `"dhyan_held_text"`. AES-256-GCM, no user-auth requirement.
- **S6-6:** Digest notification channel = `"dhyan_digest"`, importance = LOW (not DEFAULT), VISIBILITY_PRIVATE, with a public version showing count only.
- **S6-7:** `recheck()` timeout = 50ms (same executor as decide, no extra overhead).
- **S6-8:** PENDING row written before platform call; if crash before confirm, sweep recovers as FAILED_OPEN.
- **S6-9:** Confirm timeout = 3000ms; no confirmation → ACTUATION_UNKNOWN → surface immediately (fail open).
- **S6-10:** `cancelNotification` and `snoozeNotification` ONLY inside `AndroidNotificationGateway`.
- **S6-11:** Release sweep = 15-minute periodic WorkManager + one-time due jobs.
- **S6-12:** Loop guard window = 24h in `ReleasedKeyRegistry`.
- **S6-13:** OFF mode = zero gateway calls. DRY_RUN = zero gateway calls, logs decision. LIVE = live-scope only.
- **S6-14:** VIP hashing scheme matches Stage 2 `senderHash` (same salt, same SHA-256 truncation).
- **S6-15:** Fast-forward sandbox uses a separate `InMemoryParamStore` instance — never writes to real Room.
- **S6-16:** All numbers in UI carry provenance label: simulated / estimated / observed / learned / low evidence.
- **S6-17:** FAKE banner shown whenever FakeDecisionProvider is active.
- **S6-18:** Hard-coded pitch numbers (24 min, 0.62, 0.09) forbidden.
- **S6-19:** `comment` column in FeedbackEntity is acceptable (user-initiated free text, not notification text).
- **S6-20:** WorkManager tag = `"dhyan_release"` for all release jobs; sweep tag = `"dhyan_sweep"`.

---

## Cross-Stage Requests

*(Report needed changes to frozen stages here)*

- None yet.

---

## Gate 2 Checklist (filled after M6)

- [ ] Real notification on Reno 14 is HELD (DRY_RUN first, then LIVE)
- [ ] Digest notification appears with correct count
- [ ] Show-now releases the notification immediately
- [ ] Simulate tab shows both timelines for a chosen persona
- [ ] Kill switch releases all held notifications and cancels jobs
- [ ] Uninstall NOT performed; app installed over existing with Android Studio Run

---

## STOP Conditions

- Any DB change that requires `fallbackToDestructiveMigration` → STOP, file S6-STOP-1
- Any `cancelNotification`/`snoozeNotification` found outside `actuator/` → STOP, file S6-STOP-2
- Any notification text/title/body stored in non-encrypted column → STOP, file S6-STOP-3
- Any twin/decision or safety/ edit → STOP, file S6-STOP-4
