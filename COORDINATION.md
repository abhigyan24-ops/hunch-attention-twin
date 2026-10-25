# Coordination board
Mode: Overlap      Gate 2: pending      Contract version: 1.0.0

| Branch | Prompt | Agent | Status | Blocked by | Last commit | Notes file | Reviewed by owner |
|---|---|---|---|---|---|---|---|
| contracts | C0 | Antigravity | merged | - | 8eef3ee | NOTES_contracts.md | yes |
| stage5 | 5B-5E | Codex | merged | - | 3632c6f | STAGE5_NOTES.md | yes |
| stage6 | M0-M6 | Antigravity | ready for merge | Gate 2 | 4c923a3 | STAGE6_NOTES.md | pending Gate 2 |
| s9-ext | 9X1, 9X2 | Antigravity | in progress | - | | | |
| s7-laptop | 7A-7C | Codex | ready to start | - | | | |
| s8-laptop-sync | 8L | Codex | not started | s7-laptop | | | |
| s8-phone | 8P1, 8P2 | Antigravity | not started | stage6 merged + Gate 2 | | | |
| s9-win-notif | 9W | Codex | not started | s7-laptop | | | |
| s9-phone | 9P | Antigravity | not started | s8-phone merged | | | |

Open CCRs: (none)      Paused tracks: (none)      Pause reason: -

## Decisions C-1 to C-5: Approved
- **C-1:** Overlap mode approved (with guard rails).
- **C-2:** Contract additions CA-1 to CA-9 approved.
- **C-3:** C0 runs on branch `contracts` in worktree `..\dhyan-contracts`.
- **C-4:** `held_list` message approved.
- **C-5:** Maximum two agents working at the same time.
