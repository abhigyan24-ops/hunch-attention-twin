# Hunch v2.0.0 — Google Play Protect & MUwS Zero-Failure Compliance Specification

**Repository:** https://github.com/abhigyan24-ops/hunch-attention-twin  
**Version:** v2.0.0 (versionCode = 2)  
**Effective Date:** 2026-11-16  
**Owner:** Principal Android Security Architect

---

## Overview

This document specifies all mandatory technical guardrails that ensure Hunch v2.0.0 passes Google Play Protect automated PHA (Potentially Harmful Application) scanning and human Mobile Unwanted Software (MUwS) review without any whitelisting appeals.

---

## Guardrail 1 — Zero Prohibited Manifest Permissions

**File:** `phone/app/src/main/AndroidManifest.xml`

Hunch never declares any of the following high-risk or spyware-associated permissions:

| Banned Permission | Risk Category |
|---|---|
| `READ_SMS`, `RECEIVE_SMS`, `SEND_SMS` | SMS surveillance |
| `READ_CALL_LOG`, `WRITE_CALL_LOG`, `PROCESS_OUTGOING_CALLS` | Call log surveillance |
| `READ_CONTACTS`, `GET_ACCOUNTS` | Contact exfiltration |
| `RECORD_AUDIO`, `CAMERA` | Covert recording |
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | Location tracking |
| `REQUEST_INSTALL_PACKAGES`, `QUERY_ALL_PACKAGES` | Dropper/stalkerware indicators |

**Permitted permissions (all declared):**

| Permission | Purpose |
|---|---|
| `INTERNET` | Local network sync to companion Python engine on same Wi-Fi |
| `ACCESS_NETWORK_STATE` | Detect offline mode for graceful degradation |
| `POST_NOTIFICATIONS` | Show digest cards to the user |
| `PACKAGE_USAGE_STATS` | App usage timestamps (granted via Settings → Usage access) |
| `FOREGROUND_SERVICE` | Keep attention monitoring alive |
| `RECEIVE_BOOT_COMPLETED` | Restart monitoring after reboot |
| `VIBRATE` | Doomscroll brake haptic feedback |

**Automated verification:** `PlayProtectComplianceTest.manifest_neverDeclaresBannedPhaPermissions`

---

## Guardrail 2 — Non-Deceptive Accessibility Service Configuration

**File:** `phone/app/src/main/res/xml/accessibility_service_config.xml`

| Attribute | Required Value | Reason |
|---|---|---|
| `android:isAccessibilityTool` | `"false"` | Prevents "Deceptive Accessibility Tool" Play Protect warning |
| `android:canRetrieveWindowContent` | `"false"` | Proves Hunch cannot read screen text, passwords, or private UI trees |
| `android:accessibilityEventTypes` | `"typeWindowStateChanged"` | Scoped to foreground package detection only |
| `android:accessibilityFeedbackType` | `"feedbackGeneric"` | Standard non-haptic feedback category |
| `android:notificationTimeout` | `"300"` | Minimum polling interval |
| `android:description` | `"@string/accessibility_service_prominent_disclosure"` | References the mandatory prominent disclosure string |

**Prohibited event types:** `typeViewTextChanged`, `typeViewClicked`, `typeViewScrolled` (screen-scraping / input-logging risk)

**Automated verification:** `PlayProtectComplianceTest.accessibilityConfig_enforcesNonDeceptiveFlags`

---

## Guardrail 3 — System & Security Alert Pass-Through

**File:** `phone/app/src/main/java/com/dhyan/app/safety/NotificationTriageEngine.kt`

Per Google MUwS policy: *"Do not suppress alerts to the user from other apps or from the operating system, notably those which inform the user of changes to their OS."*

The following packages are classified as `SYSTEM_SECURITY_EXEMPT_PACKAGES` and bypass all notification holding mechanisms unconditionally, even during active Focus Shield sessions:

| Package | Description |
|---|---|
| `android` | Android OS |
| `com.android.systemui` | System UI (status bar, lock screen) |
| `com.android.vending` | Google Play Store / Play Protect alerts |
| `com.google.android.gms` | Google Play Services |
| `com.google.android.dialer` | Google Phone |
| `com.android.server.telecom` | Telecom stack |
| `com.android.incallui` | In-call UI |
| `com.android.settings` | Android Settings |
| `com.google.android.apps.nbu.paisa.user` | Google Pay (India) |
| `com.phonepe.app` | PhonePe |
| `net.one97.paytm` | Paytm |

**Automated verification:** `PlayProtectComplianceTest.systemSecurityNotifications_areNeverSuppressed`

---

## Guardrail 4 — Enforced Prominent Disclosure State Machine

**File:** `phone/app/src/main/java/com/dhyan/app/compliance/ProminentDisclosureGate.kt`

Before any system settings intent is launched, the user must have explicitly accepted a prominent disclosure for that permission tier. `canLaunchSystemSettingsIntent(tier)` returns `false` unless consent is recorded in SharedPreferences.

| Tier | Intent Guarded |
|---|---|
| `USAGE_STATS` | `ACTION_USAGE_ACCESS_SETTINGS` |
| `NOTIFICATION_LISTENER` | `ACTION_NOTIFICATION_LISTENER_SETTINGS` |
| `ACCESSIBILITY_HUD` | `ACTION_ACCESSIBILITY_SETTINGS` |
| `OVERLAY_HUD` | `ACTION_MANAGE_OVERLAY_PERMISSION` |

**Automated verification:** `PlayProtectComplianceTest.prominentDisclosureGate_disclosureStringsContainNoPlayProtectDisableLanguage`

---

## Guardrail 5 — MUwS Safe-Language Enforcement

No UI string, comment, onboarding helper, or documentation may ever contain:
- `"disable Play Protect"`
- `"turn off Play Protect"`
- `"disable Google Play Protect"`

All sideload guidance strictly addresses Android 13+ `App Info → Allow restricted settings`. This is enforced by `PlayProtectComplianceTest.prominentDisclosureGate_disclosureStringsContainNoPlayProtectDisableLanguage`.

---

## Guardrail 6 — Automated Pre-Flight Verification

**Test file:** `phone/app/src/test/java/com/dhyan/app/compliance/PlayProtectComplianceTest.kt`

| Test | What it verifies |
|---|---|
| `manifest_neverDeclaresBannedPhaPermissions` | 14 banned permissions absent from AndroidManifest.xml |
| `accessibilityConfig_enforcesNonDeceptiveFlags` | isAccessibilityTool=false, canRetrieveWindowContent=false, no typeViewTextChanged/Clicked/Scrolled |
| `backupRules_excludePlaintextAndOnlyAllowEncryptedVault` | Only hunch_vault_backup.enc is backed up |
| `systemSecurityNotifications_areNeverSuppressed` | 4 core system packages always bypass holding |
| `prominentDisclosureGate_disclosureStringsContainNoPlayProtectDisableLanguage` | No forbidden phrases in any disclosure copy |
| `prominentDisclosureGate_onDeviceGuaranteeStates0BytesUploaded` | Every tier states "0 bytes" on-device guarantee |

**Appeal packet generator:** `tools/generate_play_protect_appeal.py`

---

## Backup & Data Extraction Policy

**Files:** `backup_rules.xml`, `data_extraction_rules.xml`

Only `hunch_vault_backup.enc` (AES-256-GCM encrypted vault backup) is included in cloud backup and device transfer. All other app data (SQLite database, SharedPreferences, raw files) is excluded by default.

**Automated verification:** `PlayProtectComplianceTest.backupRules_excludePlaintextAndOnlyAllowEncryptedVault`

---

## Network Security Policy

**File:** `network_security_config.xml`

- Cleartext traffic is **disabled globally** (`cleartextTrafficPermitted="false"`)
- Exception: `localhost`, `127.0.0.1`, `10.0.2.2` (local Python engine on same device/emulator)
- Only system CA certificates are trusted (no user-installed CAs, no certificate pinning bypasses)

---

## Appeal Submission Checklist

Before submitting to `https://support.google.com/googleplay/android-developer/contact/protectappeals`:

- [ ] Run `python tools/generate_play_protect_appeal.py --apk <signed.apk>`
- [ ] Upload the APK to `https://www.virustotal.com` and get the VirusTotal analysis URL
- [ ] Paste the SHA-256 hash from `generate_play_protect_appeal.py` into the form
- [ ] Attach `docs/PLAY_PROTECT_APPEAL_SUBMISSION_READY.md` technical statement
- [ ] Run all 6 `PlayProtectComplianceTest` tests and confirm 100% pass rate
