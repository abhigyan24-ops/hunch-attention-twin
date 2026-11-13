# Hunch master upgrade specification

Codex owns this specification and the Android vault, gesture and calls packages, their matching tests, and the scoped backup resources and manifest references. Antigravity owns engine, VS Code, browser and laptop changes. Preserve the onboarding and wellbeing implementations. Wiring sensor callbacks into the session owner is a separate integration step outside these paths.

## Authentic telemetry

Populate SQLite only from observed events. Use five-minute real_activity_buckets for hourly risk and peak focus, and focus_streaks for the median uninterrupted work interval. Show a learning state until 30 real minutes have been measured. Surface understandable times and actions rather than model parameters. Store context_snapshots with explicit workspace breadcrumbs and user notes, and parked_tabs with user-selected destinations. Never use synthetic data as real activity.

## Encrypted uninstall recovery

1. Android portable recovery uses the document picker and a strong recovery passphrase. HNCHV1 contains the six-byte magic, random 16-byte salt, random 12-byte IV, and AES-256-GCM ciphertext with a 16-byte tag. PBKDF2-HMAC-SHA256 runs 210,000 iterations. Authenticate the entire file before parsing. The profile codec accepts the existing bedtime-only files. The current settings adapter captures and restores bedtime. Live VIP and calibration data still need authoritative adapters.
2. Cloud backup and device transfer whitelist only hunch_vault_backup.enc. Android decides whether backup and restore are available. The original recovery passphrase is required after reinstall.
3. The laptop mirror stores opaque envelopes under another AES-256-GCM layer, with current-user Windows DPAPI or owner-only POSIX key storage. Pairing must authorize upload and retrieval separately from decryption. Automatic paired restore and coordinated wipe are integration targets. Never promise a fixed restoration latency or physical erasure of copies on flash storage or in external backups.

See [encrypted persistence and restore](HUNCH_ENCRYPTED_PERSISTENCE_AND_RESTORE_SPEC.md) for the endpoint and transport contract.

## Flow protection and context replay

| Feature | Behavior |
| --- | --- |
| Flip-to-Focus | In an opted-in foreground lifecycle, use gravity or accelerometer plus proximity. Require a near, flat, face-down placement for 1.5 seconds. Trigger once until lifted. Missing sensors leave manual focus available. |
| Call Auto-Pause | Observe ringing, call, and communication audio modes. Pause only the current focus session and resume only a pause owned by this monitor. Some meetings do not expose communication audio mode. Do not access the microphone or call records. |
| Build-Time Compile Shield | Protect focus during an observed build. Release only the build's own shield when the process ends, cancels, or expires. |
| 10-Second Mental Replay | Show the last saved workspace, file and line, build result, and optional note on return. Resume only on request. |
| Browser Rabbit-Hole Tab Parking | Let users park a tab for a later break. Preserve it before closing and make reopening explicit. |
| Sunday Attention Wrapped | Summarize the actual week with local time boundaries and show missing data clearly. Make recap notifications optional. |

The Android detector and monitor expose lifecycle and callback APIs. The app owner must connect them to an opt-in session controller before they can start or pause focus in production.
