# Hunch: your attention digital twin

> Knows what that notification will cost you.

Unrelated to other products named Hunch. Hunch is a working name for a prototype.

Codename note: internal package names, protocol constants and storage names still say `dhyan` so existing data and pairings keep working.

[![CI](https://github.com/abhigyan24-ops/cascade-3.3/actions/workflows/release.yml/badge.svg)](https://github.com/abhigyan24-ops/cascade-3.3/actions)
[![Android](https://img.shields.io/badge/Android-14%20%7C%2015%20%7C%2016-brightgreen.svg)](phone/)
[![Electron](https://img.shields.io/badge/Electron-45.0-blue.svg)](laptop/)
[![Python](https://img.shields.io/badge/Python-3.10%2B-blue.svg)](engine/)

> **Dhyan** is a privacy-first, cross-device attention digital twin. It coordinates your cognitive state between your laptop and Android smartphone in real time—protecting deep work sessions by leasing system-level Do-Not-Disturb, physically intercepting disruptive smartphone notifications, and learning your personal focus dynamics strictly from real telemetry.

---

## Architecture Overview

```
                                      ┌────────────────────────┐
                                      │   Android Phone App    │
                                      │ (Compose + Room DB)    │
                                      │                        │
                                      │ • FocusLeaseController │
                                      │ • NotifCollector       │
                                      │ • Held Notifs Digest   │
                                      │ • Simulate Lab         │
                                      └───────────┬────────────┘
                                                  │
                                      E2E Encrypted LAN Sync
                                      (Local Relay / WebSockets)
                                                  │
┌──────────────────────────────┐                  │
│       Python Sidecar         │                  │
│        (:8765 engine)        │                  │
│                              │                  │
│ • Causal Forest DML (CATE)   │◄── Local IPC ────┴──► ┌────────────────────────┐
│ • Semi-Markov Simulator      │                       │     Laptop App         │
│ • Conformal Prediction       │                       │  (Electron + React)    │
│ • Bayesian Shrinkage Learners│                       │                        │
│ • Zero Key Leakage Broker    │                       │ • Pip Companion Pet    │
└──────────────────────────────┘                       │ • Bento Live Dashboard │
                                                       │ • Window Context Feed  │
                                                       │ • Focus Lease Manager  │
                                                       │ • SafeStorage Keys     │
                                                       │ • Silent Auto-Updater  │
                                                       └────────────────────────┘
```

---

## Key Features & Invariants

### 1. Real Laptop <-> Phone Focus Lease & Notification Interception
- **Cross-Device Focus Lease**: When the laptop detects active productive work (IDE, coding, terminal, docs) or when a focus session is declared, it broadcasts a 90-second focus lease (`STATE { focus: "deep", leaseMs: 90000 }`) every 30 seconds.
- **Pre-Emptive Android Priority DND**: Upon receiving a focus lease, the phone switches `NotificationManager` into `INTERRUPTION_FILTER_PRIORITY`, suppressing heads-up banners and vibration. A 90-second countdown timer auto-restores prior DND settings if the laptop disconnects or ceases focus.
- **Physical Notification Snoozing (`snoozeNotification`)**: Incoming notifications that are deferred during focus blocks are physically removed from the screen and notification shade using Android's `snoozeNotification(sbn.key, durationMs)`.
- **Never-Hold Safety Guarantee**: Phone calls (`CATEGORY_CALL`), alarms (`CATEGORY_ALARM`), OTPs, emergency keywords, and VIP senders always bypass suppression immediately.
- **Held Notification Digest**: Intercepted notifications are recorded in Room `held_notifs` with counterfactual time saved (`+Xm saved`) and an instant **[Show Now]** release button, synchronized live to the laptop Bento dashboard.

### 2. Cold-Start Bayesian Learning from Real Data
- **Zero Synthetic Pre-Seeding**: Live runtime databases initialize clean with `0` events. Fake persona pre-seeding has been purged from production paths.
- **ActivityWatch Telemetry & Fallback Collectors**: Queries local ActivityWatch buckets (with automatic fallback to native foreground polling) while strictly enforcing metadata reduction and privacy canary gates.
- **Bayesian Shrinkage Priors**: Learners smoothly shrink towards uninformative Bayesian priors (`Beta(1,1)` for notification utility; empirical Bayes for hourly focus quality) when fewer than 5 real observations exist.
- **Dynamic App Extraction**: Telemetry app selectors are populated dynamically from real observed applications on the user's phone and laptop.
- **Wipe & Reset My Data**: Settings feature that atomically clears phone Room tables, resets unique persistent device IDs (`UUID.randomUUID()`), and clears laptop SQLite tables.

### 3. Hardware-Backed Secret Storage & LLM Broker
- **No Plaintext Fallback**: Laptop API keys (Groq, Gemini) are encrypted at rest with OS facilities (`safeStorage` via Windows DPAPI / macOS Keychain / Linux libsecret). If secure storage is unavailable, the system refuses to write unencrypted files and operates in session-only memory.
- **Main Process LLM Broker**: The Python engine never receives, holds, or logs raw API keys. All completions route through Electron main with automatic privacy redaction (stripping emails, passwords, tokens, URLs), rate limiting, and metadata-only audit logging.
- **Zero Secret Sync to Phone**: Cloud LLM keys are never transmitted over the phone sync link. Android secrets use `AndroidKeyStore` AES-256 GCM (`SecretStore.kt`) and are strictly excluded from Android cloud backups and device-to-device transfers.

### 4. Silent, Focus-Aware Auto-Updates
- **Zero Modal Interruptions**: Updates are checked 60 seconds after launch and every 6 hours with jitter. New versions download silently in the background.
- **Focus Awareness**: If deep focus is active, update notifications are deferred until the user takes a break.
- **Process Tree Safety**: `killTree(pid)` terminates `dhyan-engine.exe` and helper processes before `quitAndInstall()` to prevent NSIS "file in use" conflicts.

---

## Repository Structure

```
dhyan/
├── laptop/                    # Electron desktop app (TypeScript, React, electron-vite)
│   ├── src/main/              # Lifecycle, Pip pet window, Bento panel, SafeStorage keys, LLM broker, updater
│   ├── src/adapters/          # Window active trackers, feed clock, fallback collectors
│   ├── src/pet/               # Pip physics, gazes, expressions, and collision state machine
│   ├── src/panel/             # Live cognitive dashboard (LiveDigest, SimulateLab, Insights)
│   └── src/sync/              # E2E encrypted LAN synchronization and pairing
├── phone/                     # Android Studio project (Kotlin, Jetpack Compose, Room)
│   └── app/src/main/java/com/dhyan/app/
│       ├── actuator/          # FocusLeaseController, AndroidNotificationGateway
│       ├── collectors/        # NotifCollector (snoozing & Never-Hold), UsageCollector
│       ├── data/              # Room database, HeldNotifDao, DecisionDao, EventDao
│       ├── privacy/           # SecretStore (AndroidKeyStore AES-GCM)
│       ├── sync/              # DeviceIdProvider, LAN SyncManager, PhoneEventStore
│       └── ui/                # LiveDigestTab (permission chips & test button), SimulateTab, InsightsTab
├── engine/                    # Python FastAPI twin engine & causal inference sidecar
│   ├── twin/api/app.py        # REST endpoints (:8765), lease state, dynamic apps, reset
│   ├── twin/causal/           # CATE DML, Conformal prediction, Abduction engine
│   ├── twin/learners/         # Bayesian shrinkage focus, fatigue, stickiness, interruption learners
│   ├── twin/sessionize/       # 1s timeline merging and cross-device excursion labeling
│   ├── dhyan-engine.spec      # PyInstaller freeze configuration
│   └── requirements.lock.txt  # Pinned reproducible dependencies
├── relay/                     # In-memory WebSocket pairing and event relay
├── vscode-ext/                # VS Code attention sensor companion
└── tools/                     # Architectural and contract verification scripts
```

---

## Quick Start Guide

### Prerequisites
- **Node.js**: v20 or v22 LTS
- **Python**: 3.10 – 3.14 with virtual environment
- **Java / Android SDK**: JDK 17+ and Android SDK platform tools (API 34–36)

### 1. Run the Laptop Companion & Engine
```bash
# Clone the repository
git clone https://github.com/abhigyan24-ops/cascade-3.3.git
cd cascade-3.3

# Install root & laptop dependencies
npm install
cd laptop && npm install && cd ..

# Setup Python Engine
cd engine
python -m venv venv
source venv/bin/activate  # Or `venv\Scripts\activate` on Windows
pip install -r requirements.lock.txt
cd ..

# Start the sync relay
cd relay && node server.js &

# Start the Laptop Application
cd laptop
npm run build
npm run dev
```

### 2. Build & Install Android Mobile App
```bash
cd phone
./gradlew assembleDebug

# Install on connected device (e.g. via USB / ADB)
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Grant required permissions (or tap chips on the Live screen)
adb shell cmd notification allow_listener com.dhyan.app/com.dhyan.app.collectors.NotifCollector
adb shell appops set com.dhyan.app ACCESS_NOTIFICATIONS allow
adb shell appops set com.dhyan.app GET_USAGE_STATS allow
```

---

## Keyboard Shortcuts & Controls

| Shortcut | Action | Description |
| :--- | :--- | :--- |
| `Ctrl` + `Alt` + `P` (or `Alt` + `Shift` + `P`) | **Toggle Pip Pet** | Instantly hides or restores Pip on your desktop |
| `Ctrl` + `Alt` + `D` (or `Alt` + `Shift` + `D`) | **Toggle Bento Panel** | Opens or focuses the live cognitive dashboard |
| **System Tray Icon** | **Dhyan Menu** | Access session declarations, Pip controls, and update actions |

---

## Verification & Automated Test Suite

All tests across all three runtimes are verified and passing 100% green:

```bash
# Run Laptop Unit Tests (115 tests)
npm --prefix laptop test

# Run Android Unit Tests (422 tests)
cd phone && ./gradlew testDebugUnitTest && cd ..

# Run Python Engine Tests (8 test suites)
python -m pytest engine/tests

# Run Safety & Contract Purity Checks (6 checks, 0 violations)
npm run checks
npm run check:actuator
npm run check:contracts
```

---

## Privacy Invariants

- **Categories and Counts Only**: Zero notification message bodies, titles, sensitive URLs, or keystrokes are stored or transmitted.
- **Fail-Open Policy**: If the laptop or engine experiences an unexpected crash or disconnection, the phone focus lease automatically expires within 90 seconds and restores the user's prior interruption filter.
- **Never-Hold Absolute**: Emergency communications (calls, OTPs, critical alarms) are never suppressed or delayed.

---

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.
