# Hunch: your attention digital twin

> Knows what that notification will cost you.

Unrelated to other products named Hunch. Hunch is a working name for a prototype.

Codename note: internal package names, protocol constants and storage names still say `dhyan` so existing data and pairings keep working.

[![CI](https://github.com/<<GITHUB_OWNER>>/<<REPO_NAME>>/actions/workflows/release.yml/badge.svg)](https://github.com/<<GITHUB_OWNER>>/<<REPO_NAME>>/actions)
[![License: Apache-2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-14%20%7C%2015%20%7C%2016-brightgreen.svg)](phone/)
[![Electron](https://img.shields.io/badge/Electron-31.4-blue.svg)](laptop/)
[![Python](https://img.shields.io/badge/Python-3.10%2B-blue.svg)](engine/)

**Status: public beta (`v1.0.0-beta.1`).** Expect rough edges. See [Known limitations](docs/KNOWN_LIMITATIONS.md).

---

## What It Does

- **Learns your attention rhythm:** Models when you focus best, what disrupts your flow, which app contexts make you vulnerable to doomscrolling, when fatigue sets in, and what notifications are truly high-value.
- **Simulates interruption loss:** Computes counterfactual estimates: *"If I show this notification now, how many minutes of focus will be lost, and is the expected cost higher than holding it for a batch digest?"*
- **Cross-device quiet lease:** Coordinates with your Android phone to hold notifications while you code or write on your laptop, with sub-10ms decision latency and fail-open safety.
- **Resume assistance:** Captures local editor snapshots in VS Code so you can jump straight back to where you left off after an interruption.
- **Stays on your devices:** Stays on your devices. No accounts, no telemetry.

---

## Privacy at a Glance

| Component | What It Records | What It Never Records | Leaves Your Device? |
|---|---|---|---|
| **Laptop App** | Foreground app category, hashed task id, idle time, per-10-second activity counts (keys, correction keys, mouse clicks/distance) | Keystroke characters, typed text, window contents, document text, screenshots | No |
| **Android App** | Foreground app category, screen on/off state, unlock events, notification metadata (category, hashed sender, interaction counts) | Notification text, message bodies, notification titles, contacts, location | Only to your paired laptop via end-to-end encrypted local link or relay |
| **VS Code Extension** | Per-10-second counts (edits, saves, file switches), diagnostic counts, task category, salted HMAC workspace/branch hash | Source code, file names, file paths, branch names, commit messages, terminal command text | Only to `localhost` |
| **Optional Cloud Model** | Redacted context summaries sent strictly to the provider you configure with your own API key | Raw screen pixels, code, notification bodies, private keys | Yes, only if explicitly enabled by the user |

> The table above is strictly enforced by automated regression and drift tests. See [docs/PRIVACY.md](docs/PRIVACY.md).

---

## Install (Beta)

| Platform | Format | Installation Instructions |
|---|---|---|
| **Windows** | `Hunch-Setup-<version>.exe` | Download from [Releases](../../releases). The installer is currently **unsigned**, so Windows SmartScreen will prompt. Verify the download with `SHA256SUMS.txt`. |
| **Android** | `Hunch-<version>.apk` | Download from [Releases](../../releases) and permit installation from storage. (Not distributed via Google Play due to accessibility service policies). |
| **VS Code** | `dhyan.vsix` | Download from [Releases](../../releases), then run **Extensions: Install from VSIX...** in VS Code. |

---

## Quick Start

1. Install and launch the desktop application on Windows.
2. Install the Android APK on your phone, open Settings, and scan the pairing QR code displayed on desktop.
3. Install the VS Code extension (`dhyan.vsix`).
4. Work normally. Hunch initializes with zero prior assumptions and models your personal attention dynamics purely on-device.

---

## Known Limitations

- **Platform Scope:** Windows and Android only. iOS does not permit notification interception or cross-app usage inspection.
- **Actuation Strength:** Notification holding is strictly enforced on Android via NotificationListener; on desktop, focus alerts are advisory.
- **Security Audit:** The pairing and synchronization cryptography has **not yet undergone an independent third-party security audit**.
- **Play Store Eligibility:** Features utilizing the Android Accessibility Service cannot be hosted on Google Play under current store policies.

---

## Intended Use & Ethics

Hunch is built strictly for **individual self-use and self-directed attention agency**. It must never be installed on another individual's device or used for covert employee surveillance or monitoring. Doing so violates our ethical principles and may be unlawful in your jurisdiction.

---

## Contributing, Security & License

- [CONTRIBUTING.md](CONTRIBUTING.md) · [SECURITY.md](SECURITY.md) · [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) · [SUPPORT.md](SUPPORT.md)
- Licensed under the **[Apache-2.0 License](LICENSE)**. See [NOTICE](NOTICE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
