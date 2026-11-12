# Hunch Attention Digital Twin — Ecosystem Upgrades Specification

**Repository:** `https://github.com/abhigyan24-ops/hunch-attention-twin`
**Components:** `phone/` (Android Companion), `laptop/` (Electron Dashboard & PiP), `engine/` (Python FastAPI Sidecar), `vscode-ext/` (VS Code Sensor & Companion)
**Version:** 2.0
**Status:** Implementation in progress

## Upgrade 1: Circadian Focus & Pre-Bedtime Wind-Down Sync
- Phone BedtimeManager → POST /v1/wellbeing/circadian → engine readiness
- 50m focus blocks (normal sleep) / 25m blocks (< 6h sleep) 
- 30-min pre-bedtime wind-down: VS Code status bar prompt, Laptop amber shift

## Upgrade 2: Smart Notification Triage — OTP Pass-Through & VIP Whitelist
- NotificationTriageEngine.kt: volatile-only OTP classifier (never persisted)
- 4–8 digit OTP regex + authentication keyword matching
- VIP whitelist (SharedPreferences-backed)
- Repeated sender (≥3 in 180s rolling window) always passes

## Upgrade 3: QR + mDNS + Offline Event Queue
- DiscoveryManager.kt: NsdManager for _hunch._tcp auto-discovery
- OfflineEventQueue.kt: bounded 5000-event FIFO with flush
- Zero data loss during connectivity gaps

## Upgrade 4: OEM Survival Shield
- OemSurvivalHelper.kt: per-OEM battery guidance (Xiaomi/Samsung/OnePlus/Oppo/Vivo/Nothing/Google)
- ServiceWatchdog.kt: NotificationListenerService rebind on disconnect

## Upgrade 5: Privacy Ledger — 1-Tap Forget Today
- PrivacyLedgerManager.kt: AtomicInteger counters for all data flows
- Hardcoded zero for keystrokes, notification text bytes, source code
- POST /v1/privacy/forget-today engine endpoint for server-side wipe
- PrivacyLedgerCard.kt: Compose UI card with Forget Today button

## Ports & Compatibility
- Engine HTTP: 8765 (unchanged)
- Relay WebSocket: 47821 (unchanged)
- Package: com.dhyan.app (Dhyan is internal codename, Hunch is product name)
