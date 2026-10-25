# Actuator Compatibility Matrix

Status: **Pre-release Template** (per Spec 25.10 & 25.14).

All hardware-specific, OS-specific, and third-party app observations are marked **not verified on target** until executed on physical device by the operator. The agent does not execute device commands or fabricate device results.

---

## Target Device Specifications

- **Device:** Oppo Reno 14
- **OS Platform:** ColorOS (Android 14 / API 34+)
- **Architecture:** ARM64
- **Battery Optimization Policy:** Must be set to "Unrestricted" / background allowed for `com.dhyan.app`

---

## App & Mechanism Matrix

| App | Package Name | Mechanism | Expected Platform API | Fallback Mechanism | Status / Observed Result | Notes |
|---|---|---|---|---|---|---|
| **Dhyan Test** | `com.dhyan.app` | CANCEL_DIGEST | `NotificationManager.cancel(id)` | None | not verified on target | Stage 6 self-test channel. Low-risk verification. |
| **Gmail** | `com.google.android.gm` | CANCEL_DIGEST | `NotificationManager.cancel(id)` | Silent channel override | not verified on target | Batched in 2-hour digest. |
| **Instagram** | `com.instagram.android` | CANCEL_DIGEST | `NotificationManager.cancel(id)` | Silent channel override | not verified on target | Non-messaging social alerts held. Direct messages exempted if VIP. |
| **WhatsApp** | `com.whatsapp` | SNOOZE | `NotificationListenerService.snoozeNotification(key, ms)` | `NotificationManager.cancel(id)` + re-post | not verified on target | MessagingStyle detection active. Never-hold on emergency/OTP. |
| **Telegram** | `org.telegram.messenger` | SNOOZE | `NotificationListenerService.snoozeNotification(key, ms)` | `NotificationManager.cancel(id)` + re-post | not verified on target | MessagingStyle detection active. |
| **Google Messages** | `com.google.android.apps.messaging` | SNOOZE | `NotificationListenerService.snoozeNotification(key, ms)` | `NotificationManager.cancel(id)` + re-post | not verified on target | Strict OTP regex check (D56-1) always bypasses to ALLOW. |
| **Phone / Dialer** | `com.google.android.dialer` | NONE | Bypassed (Never-hold) | None | not verified on target | Category `call` is absolute never-hold. |
| **Clock / Alarm** | `com.google.android.deskclock` | NONE | Bypassed (Never-hold) | None | not verified on target | Category `alarm` is absolute never-hold. |
| **Google Maps** | `com.google.android.apps.maps` | NONE | Bypassed (Never-hold) | None | not verified on target | Category `navigation` is absolute never-hold. |

---

## Operating Mode Verification States

1. **OFF (Default):**
   - Zero gateway calls executed.
   - Zero cancellations, zero snoozes.
   - Status: Verified via unit test (`ActuationCoordinatorTest`). Device execution: not verified on target.

2. **DRY_RUN:**
   - Full `DecisionPipeline` evaluated and logged to Room `decisions` table.
   - Zero platform gateway calls executed.
   - UI shows simulated/intended verdict with provenance label.
   - Status: Verified via unit test (`ActuationCoordinatorTest`). Device execution: not verified on target.

3. **LIVE (Live-Scope Allow-List Only):**
   - Active cancel/snooze platform calls executed ONLY for packages in `PolicySettings.allowListPackages` (defaults to `com.dhyan.app` test notifications).
   - Encrypted in `held_notifs` (AES-256-GCM via Keystore).
   - Real digest posted to `"dhyan_digest"` channel.
   - Status: Verified in JVM test harness. Device execution: not verified on target.

---

## ColorOS Specific Quirks & Mitigations

1. **NotificationListenerService Disconnection:**
   - ColorOS aggressive power management may unbind listener services on idle.
   - Mitigation: `NotifCollector.onListenerDisconnected()` invokes `requestRebind()`.
   - Result: not verified on target.

2. **Snooze API Support:**
   - `snoozeNotification(key, durationMs)` is standard Android 8.0+ API, but custom OEM ROMs may handle expiration differently.
   - Mitigation: Gateway wraps snooze call in `runCatching` with fallback to `NotificationManager.cancel()` + `ReleaseJobScheduler`.
   - Result: not verified on target.

3. **Quick Settings Tile Sync:**
   - `DhyanTileService` reflects `PolicySettingsHolder.get().killSwitch`.
   - ColorOS tile state refresh requires `requestListeningState()`.
   - Result: not verified on target.
