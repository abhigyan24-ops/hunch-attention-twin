# Hunch Privacy Architecture & Data Specification

Hunch is architected from the ground up around **zero content collection** and **mathematical privacy guarantees**.

```json:privacy-manifest
{
  "contractVersion": "1.0.0",
  "laptop": {
    "records": ["app_category", "hashed_task_id", "idle_time_seconds", "activity_counts"],
    "forbidden": ["typed_text", "keystroke_chars", "window_contents", "screenshots"]
  },
  "phone": {
    "records": ["app_category", "screen_state", "unlock_events", "notification_category", "hashed_sender", "interaction_counts"],
    "forbidden": ["notification_text", "message_body", "notification_title", "contacts", "gps_location"]
  },
  "vscode": {
    "records": ["edit_counts", "save_counts", "switch_counts", "task_category", "salted_workspace_hash"],
    "forbidden": ["source_code", "file_name", "file_path", "branch_name", "commit_message", "terminal_command"]
  }
}
```

## 1. Privacy Principles
1. **Counts and Durations Only:** We count edits, app switches, notifications, and duration of active spans. We never record what you type, read, or see.
2. **Salted One-Way Hashes:** Identifiers such as workspace projects, branches, and senders are hashed using `HMAC-SHA256` with an installation-unique 32-byte cryptographic salt generated on first run. Hashes cannot be reversed to discover workspace or contact names.
3. **Local State Isolation:** Editor snapshots for resume assistance remain strictly in `workspaceState` and never enter outbox queues or network sockets.
4. **Fail-Open Actuation:** If the attention engine or sync link is unavailable, notification delivery immediately defaults to unblocked pass-through.
5. **No Analytics or Telemetry:** No user analytics, error tracking SDKs, or background telemetry are compiled into any binary.

## 2. Retention and Data Deletion
- **Local Storage:** Phone records are persisted in on-device Room SQLite databases (`dhyan.db`).
- **One-Tap Reset:** Settings features a "Wipe & Reset My Data" action that deletes all historical decisions, held notifications, and telemetry from both the phone and the connected workstation companion.
