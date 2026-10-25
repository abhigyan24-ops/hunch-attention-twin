# ACTUATOR_CONTRACT.md

Version: 1.0 — Stage 6, branch `stage6-actuator`

This document is the reference for the Stage 6 actuator subsystem. It describes state machines, status semantics, retention rules, and interface contracts.

---

## 1. State Machine — `HeldNotifEntity.state`

```
 [posted]
    │
    ▼
 PENDING ──► (recheck abort / gateway error) ──► FAILED_OPEN
    │
    ▼ (gateway ok)
 HELD ──────────────────────────────────────────────┐
 SNOOZED ───────────────────────────────────────────┤
    │                                               │
    ├─► (user: Show-now) ──────────────────────────►│
    │                                               │
    ▼ (holdUntilMs passed / ReleaseWorker)          │
 RELEASED ◄─────────────────────────────────────────┘
    │
    ▼ (expiresAtMs passed)
 [deleted by deleteExpired()]

 ACTUATION_UNKNOWN  ──► (surface immediately — not held)
```

| State | Meaning |
|---|---|
| `PENDING` | Written to DB before platform call; crash-safe marker |
| `HELD` | `cancelNotification()` confirmed; in digest queue |
| `SNOOZED` | `snoozeNotification()` confirmed; auto-releases via WorkManager |
| `RELEASED` | Released by sweep, user Show-now, or kill switch |
| `FAILED_OPEN` | Any error/unknown → user keeps notification |
| `ACTUATION_UNKNOWN` | Platform called but not confirmed in 3s; surfaced immediately |

---

## 2. ActuationStatus (from `twin/decision/DecisionContract.kt`)

```kotlin
enum class ActuationStatus {
    HELD, SNOOZED, SCHEDULED, RELEASED, EXPIRED, FAILED_OPEN, ACTUATION_UNKNOWN
}
```

Written to `DecisionEntity.actuation_status` after platform call.

---

## 3. Mechanism Chooser

| Action | Package / AppCat | Mechanism |
|---|---|---|
| `ALLOW` | any | `NONE` (no hold) |
| `DELAY` | messaging packages / `msg` cat | `SNOOZE` |
| `DELAY` | other | `CANCEL_DIGEST` |
| `DIGEST` | any | `CANCEL_DIGEST` |
| `SILENT` | any | `CANCEL_DIGEST` |
| OFF or DRY_RUN mode | any | `NONE` |

Snooze packages (always `SNOOZE`): WhatsApp, WhatsApp Business, Telegram, Slack, Teams, Discord.

---

## 4. Fail-Open Rules (non-negotiable)

- OFF mode → FAILED_OPEN, zero gateway calls
- DRY_RUN mode → FAILED_OPEN, zero gateway calls
- Kill switch active → FAILED_OPEN, zero gateway calls
- Loop guard hit → FAILED_OPEN, zero gateway calls
- ALLOW action → NONE mechanism → FAILED_OPEN
- `recheck()` timeout (50ms) → Abort → FAILED_OPEN
- Gateway timeout (3000ms) → ACTUATION_UNKNOWN → surface immediately
- Gateway error → FAILED_OPEN
- Any unhandled exception → FAILED_OPEN

---

## 5. Privacy Invariants

- `held_notifs.ciphertext` is the ONLY column that may contain notification text.
- All other columns: hashes, enums, and timestamps only.
- `decisions` table: no text columns (enforced by `check-actuator-contract.mjs`).
- `EXTRA_TITLE` / `EXTRA_TEXT` access: only in `NotifExtractor` (M6), never stored.
- `cancelNotification` / `snoozeNotification` (or `NotificationManager.cancel()`): ONLY in `AndroidNotificationGateway`.

---

## 6. Retention

- `held_notifs`: rows kept for `RETENTION_DAYS` (7) days after `heldSinceMs`.
- `expiresAtMs = heldSinceMs + 7 * 86_400_000`.
- `deleteExpired()` called on every sweep. Only non-active rows deleted.
- `decisions`: prunable older than 30 days (not auto-pruned in Stage 6).

---

## 7. Loop Guard

- `ReleasedKeyRegistry`: tracks keyHash → releasedAtMs for 24h.
- Purpose: prevents re-actuation of the same notification key within the guard window.
- Cleared on process restart (in-memory only). Persistent loop guard deferred to Stage 9.

---

## 8. Constants (`ActuatorConfig`)

| Constant | Value | Meaning |
|---|---|---|
| `CONFIRM_TIMEOUT_MS` | 3000ms | Platform call confirm window |
| `DECISION_TIMEOUT_MS` | 250ms | Full decide+actuate budget |
| `SWEEP_INTERVAL_MIN` | 15 min | WorkManager periodic sweep |
| `PENDING_RECOVERY_MS` | 60000ms | Stuck PENDING → FAILED_OPEN after |
| `LOOP_GUARD_WINDOW_MS` | 86400000ms | 24h loop guard window |
| `RETENTION_DAYS` | 7 | Days to keep held_notifs rows |
| `DEFAULT_SNOOZE_MS` | 1800000ms | Default snooze duration (30 min) |
| `KEYSTORE_ALIAS` | `"dhyan_held_text"` | AndroidKeyStore AES-256-GCM key |

---

## 9. Interfaces

### `NotificationGateway`
Single implementation: `AndroidNotificationGateway`.
Never instantiated outside `actuator/`.
`FakeNotificationGateway` available in production code for compose testing only (no platform calls).

### `HeldStore`
- `RoomHeldStore` (production)
- `InMemoryHeldStore` (JVM tests)

### `ReleaseJobScheduler`
- `WorkManagerReleaseJobScheduler` (production)
- `NoOpReleaseJobScheduler` (JVM tests)

### `DigestPoster`
- `AndroidDigestPoster` (production)
- `FakeDigestPoster` (JVM tests)

### `DecisionLogStore` (from contract)
- `RoomDecisionLogStore` (production)

---

## 10. WorkManager Jobs

| Job | Tag | Type | Trigger |
|---|---|---|---|
| `ReleaseWorker` | `dhyan_release` | One-time | After successful hold; delay = holdUntilMs - nowMs |
| `SweepWorker` | `dhyan_sweep` | Periodic (15min) | Started by boot receiver and on app start |

Kill switch cancels all `dhyan_release` and `dhyan_sweep` jobs.
