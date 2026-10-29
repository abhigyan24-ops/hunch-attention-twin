# MANUAL_CHECKS-stage9.md: Stage 9 Cross-Device Episode & Provenance Verification

This runbook guides manual target-hardware verification for Stage 9 (cross-device distraction episodes, outcome attribution, and snapshot provenance) on the Oppo Reno 14 (CPH2737) and laptop companion.

---

## 1. Prerequisites and Setup

1. **Hardware & Environment:**
   - Phone: Oppo Reno 14 5G (ColorOS 15 / Android 15), connected via USB.
   - Laptop: Development workstation with Node 20+, Electron, and ActivityWatch.
   - ADB reverse active:
     ```powershell
     adb reverse tcp:8080 tcp:8080
     ```
2. **Permissions on Phone:**
   - Notification Listener enabled: Settings -> Apps -> Special app access -> Notification access -> Dhyan (Allow).
   - Usage Access enabled: Settings -> Apps -> Special app access -> Usage access -> Dhyan (Allow).
   - Accessibility Service enabled: Settings -> Accessibility -> Installed apps -> Dhyan Ingest (Turn ON).
   - Battery Optimization: Set to "Don't optimize" / "Unrestricted".
3. **Build and Install Debug APK:**
   ```powershell
   cd phone
   .\gradlew.bat installDebug
   ```

---

## 2. Test Scenarios

### Scenario 1: Clean Cross-Device Distraction and Proxy Resumption (Observed Provenance)
- **Goal:** Verify phone dwell and laptop resumption combine into an attributed outcome with `OBSERVED` snapshot provenance.
- **Procedure:**
  1. Start local relay (`npm run relay`) and laptop sync client (`npm run dev:laptop`).
  2. Launch Dhyan on Reno 14. Confirm pairing status shows `Connected (Live Companion)`.
  3. On laptop: Open VS Code and focus a Kotlin/TypeScript file (laptop state becomes `FLOWING`).
  4. Trigger a test notification on phone (or receive real messaging notification).
  5. Pick up phone, unlock, and interact with the app for 1 to 2 minutes.
  6. Lock phone screen (press power button).
  7. On laptop: Type actively in VS Code for 15 seconds (emitting `edit_burst`).
- **Expected Results:**
  - Phone logcat indicates `EpisodeBuilder` transitioned: `NOTIFIED` -> `IN_APP` -> `RESUMING` -> `FOCUSED`.
  - Episode outcome recorded:
    - `pickedUp = true`
    - `dwellMin` between 1.0 and 2.0 min
    - `resumeDelayMin` matches elapsed time between screen lock and laptop typing
    - `laptopLeftWork = false`
    - `isResumeEstimated = false`
    - `isCensored = false`
  - In Live Digest UI: `LAPTOP: FLOWING` badge displays `OBSERVED` badge in cyan/teal.
  - In Twin Lab: Beta pickup and dwell/resume lognormal distributions incorporate the new observation.

---

### Scenario 2: Distraction Spillover to Laptop (YouTube / Media Diversion)
- **Goal:** Verify that opening non-work content on laptop during resumption flags `laptopLeftWork = true`.
- **Procedure:**
  1. Repeat steps 1–6 from Scenario 1.
  2. Instead of resuming work in VS Code, open a browser window and navigate to YouTube Shorts / Netflix.
  3. Observe incoming `window_change` / `tab_category` (`appCat = "media"` / `domainCat = "media"`).
- **Expected Results:**
  - Episode outcome records `laptopLeftWork = true`.
  - Attention engine flags distraction spillover.
  - Fused context snapshot focus state degrades from `WORKING` / `DEEP_FOCUS` to `DISTRACTED` / `DRIFTING`.

---

### Scenario 3: Missing Laptop / Telemetry Dropout (Estimated Fallback)
- **Goal:** Verify dwell-only fallback when laptop is offline/stale, with `ESTIMATED` provenance and parameter hygiene.
- **Procedure:**
  1. Stop the local relay (`Ctrl+C` in relay terminal) or disconnect USB / WiFi.
  2. Receive a notification on the phone, pick up for 1 minute, and lock phone.
  3. Wait 30 minutes (or advance outcome window in debug tools).
- **Expected Results:**
  - Episode closes at window expiration:
    - `pickedUp = true`
    - `dwellMin ~ 1.0`
    - `resumeDelayMin = 3.0` (prior default fallback)
    - `isResumeEstimated = true`
    - `isCensored = false`
  - **Parameter Hygiene Check:** Learned resume delay lognormal distribution is NOT updated (no contamination from prior estimate).
  - In Live Digest UI: `LAPTOP: OFFLINE` badge displays `ESTIMATED` badge in amber/drifting color.
  - Explanatory message: `"Unsynced: laptop telemetry is stale (>15s). Context snapshot marked ESTIMATED."`

---

### Scenario 4: Local Collector Dropout (Censored Telemetry)
- **Goal:** Verify that local sensor failure censors the episode and excludes it from false ignore updates (Spec 25.4).
- **Procedure:**
  1. Trigger an incoming notification on phone.
  2. While phone is awaiting pickup, simulate or trigger a local collector failure (e.g., revoke Usage Access in Settings or restart accessibility service).
- **Expected Results:**
  - Active episode immediately closes with `label = CENSORED` and `isCensored = true`.
  - `EpisodeCloser` does NOT increment failure count on the beta pickup distribution.
  - Posterior evidence count ($n_{\text{eff}}$) is untouched.
