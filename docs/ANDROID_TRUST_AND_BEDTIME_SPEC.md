# Android distribution trust and bedtime

## Signing and distribution

Production APKs use the permanent `dhyan-release` RSA key. Configure the four GitHub Actions secrets `ANDROID_KEYSTORE_B64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`. The workflow decodes the keystore into runner temporary storage, supplies it to Gradle, and removes it at job completion. Gradle enables all four signature schemes. CI verifies V1 separately in legacy verifier mode, verifies V2/V3/V4 with the V4 sidecar, and compares the verified certificate to the pinned SHA-256 fingerprint before uploading. Release checksums cover the APK and sidecar.

A manual branch run may use an ephemeral key only when the permanent keystore secret is absent. Its artifacts are labeled `test-signed`. A tag run requires the permanent key. APKs signed with different identities cannot update one another in place.

Run `tools/generate-android-keystore.ps1` under the Windows account that owns the local signing material to verify the certificate. Add `-CopyToClipboard` to export the Base64 keystore directly to the clipboard. Use `-InitializeNewIdentity` only to establish a new signing lineage. Back up the keystore and its password securely. Windows encrypts the local password for the account and machine, so preserve a separate recoverable backup.

## Play Protect appeal

For an incorrect classification, use **File an appeal** on [Google's app verification page](https://support.google.com/googleplay/android-developer/answer/2992033). Record the exact warning, APK version, package ID, Android version, and release URL. Use the pinned release certificate fingerprint from the Actions summary and provide the matching APK or checksum if requested. Describe why each sensitive capability is optional and which data it uses. Keep Play Protect enabled. Signing verifies APK integrity and update identity, while Google makes its own classification decision.

## Transport

The manifest activates the network security policy. HTTPS and WSS trust system certificates. Cleartext is allowed only for the exact hosts `localhost`, `127.0.0.1`, and `10.0.2.2` (the Android emulator's host). Android's domain configuration does not express CIDR ranges. Use TLS for LAN addresses and relay endpoints. The existing sync client already restricts cleartext WebSocket transport to its local development paths. [Android network security configuration](https://developer.android.com/privacy-and-security/security-config)

## Progressive trust

| Tier | Capability | Activation |
| --- | --- | --- |
| 0 | Dashboard, manual focus, manual bedtime, laptop pairing | Launch normally with no special permission prompt |
| 1 | Bedtime inference and optional reminder notifications | User opens bedtime sync. Explain recent device activity use, then open Usage Access. Notification posting is a separate optional request. |
| 2 | Quiet Focus notification holding | User chooses notification holding. Explain notification access, then open Notification Listener settings. |
| 3 | Advanced app context and overlay status | Show the Accessibility disclosure from Focus Shield when requested. The helper can check overlay access and open its settings for a build that declares the permission. The current manifest has no system overlay grant. The in-app Pip companion remains available. |

Read permissions from the OS when returning to the app. Record dismissal locally. Permissions are never requested automatically on startup. Do not treat permission refusal as an error or prevent manual use. Use data-specific disclosures. Device activity for bedtime is processed locally. Optional laptop and relay pairing are separate features.

### Restricted settings

For a non-Play or unknown installer on Android 13+, offer optional guidance for Accessibility and Notification Listener. If Android marks the setting as restricted, let the user open **App info** and review **Allow restricted settings** if the system offers it. On return, let the user open the requested feature's settings and check whether the feature was granted. Android owns both decisions. Overlay access has its own settings and does not use this help path. [Android restricted settings help](https://support.google.com/android/answer/12623953)

## Digital Wellbeing contract

Auto bedtime uses up to seven days of on-device `UsageStatsManager` interaction events. Filter for inactivity gaps of four to twelve hours, starting between 20:00 and 04:00 and ending between 04:00 and 11:00. Compare sleep times relative to 18:00 so midnight does not skew the median. Use the median sleep and wake times and round to 15 minutes. Without Usage Access or valid history, show the fallback of 23:30–07:00 and label it as a default.

A manual selection persists both times and switches to manual mode until the user explicitly returns to auto. Auto recomputes when the user returns from granting Usage Access or requests a refresh. Keep the manual editor usable without any grant. The Wellbeing implementation and tests are owned separately.
