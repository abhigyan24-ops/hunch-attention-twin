# Hunch System Architecture

```
┌────────────────────────────────┐         Encrypted Sync         ┌────────────────────────────────┐
│       Android Phone App        │ ◄────────────────────────────► │      Laptop Companion App      │
│   (Kotlin / Compose / Room)    │     (Local WebSocket / Relay)  │     (Electron / TypeScript)    │
│                                │                                │                                │
│ • NotifCollector (Metadata)    │                                │ • Tray & Focus Lease Controller│
│ • A11yCollector (Scroll Counts)│                                │ • SafeStorage Credentials Store│
│ • FocusLeaseController         │                                │ • Activity Sensor Client       │
│ • Decision Engine Client       │                                └──────────────┬─────────────────┘
└────────────────────────────────┘                                               │ Local IPC
                                                                                 ▼
┌────────────────────────────────┐         Local Ingest           ┌────────────────────────────────┐
│      VS Code Extension         │ ─────────────────────────────► │     Causal & Learning Engine   │
│  (PrivacyGate / Resume Assist) │         (127.0.0.1)            │     (Python / FastAPI / Stats) │
└────────────────────────────────┘                                └────────────────────────────────┘
```

## Subsystems

1. **Android Phone (`phone/`):** Listens to notification events (extracting only application category, timestamp, and salted sender hash). Manages notification batching and digest release.
2. **Laptop Desktop App (`laptop/`):** Displays session status in the tray and main window, negotiates cross-device focus leases, and bridges local sensor streams.
3. **Causal Engine (`engine/`):** Pure mathematical core modeling survival functions, interruption cost estimation, and counterfactual loss prediction.
4. **VS Code Extension (`vscode-ext/`):** Aggregates 10-second coding cadence summaries through `PrivacyGate.sanitize` and assists resumption after interruptions.
5. **Encrypted Relay (`relay/`):** Stateless in-memory WebSocket broker using authenticated end-to-end encryption when devices are on separate local networks.
