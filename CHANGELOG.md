# Changelog

All notable changes to **Hunch** (`hunch-attention-twin`) are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [2.0.0] - 2026-11-15 — Generational Production Release

`v2.0.0` is the production milestone release of **Hunch** (formerly Dhyan). It unifies cross-device attention modeling, on-device machine learning, zero-knowledge encrypted backup vaults, dual-use study mode, and seamless in-place APK upgrade capabilities.

### 🛡️ Trust Architecture & Distribution
- **Hunch Identity & Package Rebrand:** Cleanly migrated repository to `hunch-attention-twin` with updated branding across Android, Electron, VS Code, and Chrome extensions.
- **Permanent Release Key & V1–V4 APK Signing:** Signed with dedicated `ANDROID_KEYSTORE_B64` certificate (`SHA-256: 3ec8e983...53d4`), verified across V1, V2, V3, and V4 signing schemes via `apksigner`.
- **4-Tier Progressive Trust Onboarding:** Replaced all-at-once permission walls with contextual justification sheets:
  1. Base Flow (Notification Listener for calm break digest)
  2. Deep Context (Usage Stats for automatic circadian sleep inference)
  3. Flow Shield (Accessibility & Overlay for doomscroll breaker)
  4. Desk Hardware (Physical activity & sensor fusion)
- **Android 13–15 Restricted Settings Unlocker:** 2-step guided resolution dialog for sideloaded accessibility permissions under modern Android OS security gates.
- **OTA GitHub Release Update Checker:** Integrated 24-hour cached update check in Android (`phone/update/`) and Python Engine (`/v1/system/update-check`) enabling 1-tap APK sideload updates without Google Play Services.

### 🧠 On-Device Machine Learning & Contextual Bandit
- **12-Feature Online Logistic Hazard Survival Model:** Pure Kotlin & Python standard math survival model computing continuous hazard rates $h(t)$ with zero external binary dependencies.
- **18-Step Distraction Rollout Simulation:** Vectorized time-to-distraction inference predicting high-risk distraction cliffs up to 90 minutes in advance.
- **4-Arm Diagonal LinUCB Contextual Bandit:** Real-time intervention optimization balancing exploration bonus $\alpha \sqrt{\mathbf{x}_t^T \mathbf{A}_a^{-1} \mathbf{x}_t}$ across 4 non-annoying intervention arms (`SILENT_OBSERVE`, `SUBTLE_NUDGE`, `QUIET_HOLD`, `FULL_SHIELD`).
- **Federated Model Weight Sync:** Privacy-preserving cross-device parameter averaging between Android companion and Python engine via LAN pairing.

### 🔐 Zero-Knowledge Uninstall Survival
- **3-Layer Encrypted Local Vault:** `AES-256-GCM` with `PBKDF2-HMAC-SHA256` key derivation, 12-byte random IVs, and authenticated tags protecting historical telemetry.
- **Cross-Platform Rehydration:** Survives app uninstalls via scoped Android backup XML and Windows DPAPI storage (`.hunchvault`), recovering complete context in $< 1$ second upon re-installation.

### 🌙 Digital Wellbeing & Circadian Rhythms
- **Automatic 7-Day Bedtime Inference:** Passive statistical analysis of phone screen-off intervals and charging states via `UsageStatsManager` with manual override options.
- **Sleep-Adjusted Focus Sizing:** Dynamically resizes focus work blocks (25m during circadian vulnerability vs. 50m during peak rested alertness).
- **30-Minute Pre-Bedtime Wind-Down:** Procedural flow audio gracefully fades binaural frequencies to brown noise with gentle volume attenuation before detected sleep windows.

### 📚 Dual-Use Study Mode & Proactive Flow Protection
- **Strict Browser Exemption:** Web browsers (Chrome, Firefox, Brave, Edge) are 100% exempt from doomscroll breakers and phantom-unlock penalties on Android and Desktop.
- **Surgical YouTube & Reddit Classifier:**
  - YouTube Shorts (`/shorts/*`) and rapid vertical swiping trigger gentle velocity nudges at 90s–240s.
  - Educational titles, technical subreddits (`r/programming`, `r/rust`, etc.), and active IDE coding bypass blockers automatically.
  - Ambiguous long-form watch sessions trigger an 8-minute non-blocking choice pill: **[Yes, Studying (Allow 60m)]** or **[Park Video at Current Timestamp]**.
- **Cross-Device Code-Along Horizon:** Coding activity within 180 seconds ($t \le 180\text{s}$) synchronizes across VS Code, laptop, and phone to whitelist technical reference reading.
- **Hardware Sensor Fusion:**
  - 4-Gate Walk-Away Step Detector: Auto-pauses focus sessions when the developer leaves their desk.
  - Flip-to-Focus Desk Gesture: Entering flow by placing phone face-down on desk.
  - Active Call Auto-Pause: Temporarily mutes flow audio and pauses timers during incoming/outgoing phone calls.
  - Build-Time Compile Shield: Protects developer flow during intensive compiler runs.
  - 10-Second Mental Replay: Snapshot of last edited file, function symbol, and git diff rendered upon returning to desk.
  - Actionable Break Digest: Notifications held quietly in memory with 1-tap native `PendingIntent` launch.
  - Android OS Shell Integration: Quick Settings Flow Tile and Glanceable Home-Screen Widget.

### ⚙️ Production Reliability Hardening
- **SQLite Concurrency & WAL Mode:** Enabled `PRAGMA journal_mode=WAL;`, `busy_timeout=5000;`, and `synchronous=NORMAL;` with idempotent schema migrations.
- **WSL/Virtual-Adapter Safe IP Discovery:** Ignores Hyper-V, WSL, Docker, and VirtualBox network adapters to bind LAN mDNS pairing exclusively to physical subnets.
- **4:00 AM Logical Day Rollover:** Prevents late-night hacking sessions from splitting focus streaks into artificial daily buckets.
- **Battery Duty-Cycling:** Hardware accelerometer and ambient light sampling throttled to sleep-wake cycles preserving phone battery.

---

## [1.0.0-beta.1] - 2026-10-31
- Initial public beta release under Dhyan.
- Local telemetry ingestion over WebSocket port 8765.
- Basic break detector and focus session timer in VS Code.
- Electron companion panel with prototype insights.
