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
  - `ClockOffset`: `com.dhyan.app.ingest.ClockOffset` (exists, to be extended with Section 7.1 rolling estimator)
  - `RoomDecisionLogStore`: `com.dhyan.app.data.RoomDecisionLogStore` (exists, records actuation decisions)
  - `SyncCursorDao` & `SettingsDao`: exist in Room DB
  - Room version: 2 (exported schema at `phone/app/schemas/com.dhyan.app.data.DhyanDatabase/2.json`)
- **Key Architectural Decisions:**
  - Android 15 foreground service: updated to `specialUse` per DD-10 to survive >6 hour limits.
  - Room migration 2 -> 3: non-destructive migration adding `cmd_log` table and updating `events` with canonical envelope identity & tombstone status.
  - Transactional settlement: bad identity rejects whole batch; malformed payload commits metadata-only tombstone and never raw text; atomic Room transaction before cumulative ACK dispatch.

---

## Milestone M1: Plumbing
- Status: IN PROGRESS
- Deliverables:
  - Manifest: Add `android.permission.INTERNET`, update `DhyanForegroundService` to `specialUse` with property subtype.
  - Network Security: `network_security_config.xml` (debug permits `ws://` to LAN and `10.0.2.2`; release strict TLS `wss://` only).
  - Dependencies: Add OkHttp (`com.squareup.okhttp3:okhttp:4.12.0`) in `phone/app/build.gradle.kts`.
  - Room Database v3:
    - Entity `CmdLogEntity` & DAO `CmdLogDao`
    - Update `EventEntity` with canonical envelope columns (`eventId`, `seq`, `deviceId`, `settledStatus`, `schemaVersion`)
    - Migration `MIGRATION_2_3` in `DhyanDatabase.kt`
    - Export schema `3.json`
    - Migration test verifying non-destructive upgrade.
  - Schema version bump to `1.1.0`.
