# Hunch — Google Play Protect Appeal Submission Packet

**Generated:** 2026-11-15 19:51 UTC  
**Package:** `com.dhyan.app`  
**Version:** `2.0.0` (versionCode `2`)  
**Repository:** https://github.com/abhigyan24-ops/hunch-attention-twin

---

## ⚠️ Step 0 — VirusTotal Upload Checklist (REQUIRED FIRST)

> Google's appeal backend fetches your APK by its SHA-256 hash from VirusTotal.
> You MUST upload the APK to VirusTotal BEFORE submitting the appeal form.

1. Go to [https://www.virustotal.com](https://www.virustotal.com)
2. Upload `Hunch-v2.0.0-release.apk`
3. Wait for the analysis to complete (usually < 2 minutes)
4. Copy the VirusTotal analysis URL (e.g. `https://www.virustotal.com/gui/file/<sha256>/detection`)
5. Paste it into the **"VirusTotal link"** field in the appeals form

**APK SHA-256 (for VirusTotal verification):**
```
_(run with --apk <signed.apk> to compute)_
```

---

## Step 1 — Appeals Form Field Values

**Form URL:** [https://support.google.com/googleplay/android-developer/contact/protectappeals](https://support.google.com/googleplay/android-developer/contact/protectappeals)

Copy-paste each value into the corresponding form field:

| Field | Value |
|---|---|
| **Package name** | `com.dhyan.app` |
| **App version** | `2.0.0` |
| **Version code** | `2` |
| **APK SHA-256** | `_(run with --apk <signed.apk> to compute)_` |
| **Signing certificate SHA-256** | `3ec8e983132712b44585e4986378a42beba71bfd48d4d20f3452d3b860d653d4` |
| **VirusTotal URL** | _(paste after uploading in Step 0)_ |

---

## Step 2 — Technical Appeal Statement

Copy the entire block below into the **"Description / Technical Details"** field:

---

**Subject: Play Protect Appeal — Hunch v2.0.0 (com.dhyan.app) — Attention-Aware Notification Manager**

We are requesting Google Play Protect review for our app **Hunch** (`com.dhyan.app`, version `2.0.0`, versionCode `2`).

Hunch is a local-first attention management system. It models user attention patterns entirely on-device using a pure-Kotlin/Python survival ML model. No data is ever transmitted to external servers.

### Why Play Protect may flag Hunch

Hunch uses three system-level Android APIs that are routinely used by productivity and parental control apps:
1. **Notification Listener Service** — to hold low-priority notifications during focus sessions and release them in batches.
2. **Accessibility Service** — to detect which app is in the foreground (package name only; `typeWindowStateChanged` events only).
3. **Usage Stats** — to compute per-app time-on-screen metrics.

None of these APIs are used for data collection, surveillance, ad targeting, or any form of off-device transmission.

### Play Protect & MUwS Compliance Guarantees

**G1 — Zero Prohibited Permissions:**
`AndroidManifest.xml` does not declare `READ_SMS`, `RECEIVE_SMS`, `SEND_SMS`, `READ_CALL_LOG`, `WRITE_CALL_LOG`, `PROCESS_OUTGOING_CALLS`, `READ_CONTACTS`, `GET_ACCOUNTS`, `RECORD_AUDIO`, `CAMERA`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `REQUEST_INSTALL_PACKAGES`, or `QUERY_ALL_PACKAGES`.

**G2 — Non-Deceptive Accessibility:**
`accessibility_service_config.xml` explicitly sets `android:isAccessibilityTool="false"` and `android:canRetrieveWindowContent="false"`. Only `typeWindowStateChanged` events are captured (foreground app package transitions). Screen text, keystrokes, passwords, and UI content are never read.

**G3 — System Alert Non-Suppression (MUwS):**
`NotificationTriageEngine.SYSTEM_SECURITY_EXEMPT_PACKAGES` ensures that notifications from `android`, `com.android.systemui`, `com.android.vending` (Play Protect), `com.google.android.gms`, `com.google.android.dialer`, `com.android.server.telecom`, `com.android.incallui`, and `com.android.settings` are always immediately passed through — never held, deferred, or canceled — even during active Focus Shield sessions.

**G4 — Prominent Disclosure Gate:**
`ProminentDisclosureGate.kt` enforces that no Android system settings intent (`ACTION_USAGE_ACCESS_SETTINGS`, `ACTION_NOTIFICATION_LISTENER_SETTINGS`, `ACTION_ACCESSIBILITY_SETTINGS`, `ACTION_MANAGE_OVERLAY_PERMISSION`) can ever be launched without prior explicit user acceptance of a plain-English prominent disclosure card. Consent is tracked per permission tier in SharedPreferences.

**G5 — MUwS Safe Language:**
No string, comment, or onboarding instruction in this app ever asks the user to disable, turn off, or bypass Google Play Protect.

**G6 — Automated Compliance Verification:**
`PlayProtectComplianceTest.kt` (JVM unit test suite) programmatically parses `AndroidManifest.xml`, `accessibility_service_config.xml`, `backup_rules.xml`, and `data_extraction_rules.xml` on every build to prevent regressions.

### Data & Privacy Summary

| Data Type | Collected? | Stored Where? | Transmitted? |
|---|---|---|---|
| Notification text/title | ❌ Never | — | — |
| Screen content / UI text | ❌ Never | — | — |
| Location | ❌ Never | — | — |
| Contacts / SMS / Calls | ❌ Never | — | — |
| App usage timestamps | ✅ Yes | Local SQLite (AES-256-GCM encrypted) | ❌ Never |
| Foreground app package | ✅ Yes (hashed) | Local SQLite | ❌ Never |
| Notification metadata (category only) | ✅ Yes | Local SQLite | ❌ Never |

### Signing Certificate
SHA-256: `3ec8e983132712b44585e4986378a42beba71bfd48d4d20f3452d3b860d653d4`

### Source Code
Full open-source repository: https://github.com/abhigyan24-ops/hunch-attention-twin
CI build workflow: https://github.com/abhigyan24-ops/hunch-attention-twin/blob/main/.github/workflows/release.yml

We welcome any additional technical review. Thank you for your time.

---

## Step 3 — Post-Submission Checklist

- [ ] VirusTotal analysis URL copied into the form
- [ ] APK SHA-256 matches the uploaded file
- [ ] All 6 `PlayProtectComplianceTest` tests pass in CI
- [ ] `docs/HUNCH_PLAY_PROTECT_COMPLIANCE_SPEC.md` committed to main branch
- [ ] `tools/generate_play_protect_appeal.py` CI step included in release.yml

---

*Generated by `tools/generate_play_protect_appeal.py` — Hunch v2.0.0*
