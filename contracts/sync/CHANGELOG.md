# Sync Contract Changelog

## 1.0.0 - 2026-10-25
- Initial frozen sync contract v1.0.0 (`docs/contracts/SYNC_CONTRACT.md`).
- Outer frame schemas: pair_offer, pair_join, pair_reveal, pair_confirm, pair_cancel, hello, encrypted frame.
- Session message schemas: ping, pong, events, ack, state, pet_event, held_list, command, cmd_ack, error.
- Local ingest schemas: auth, ext_heartbeat, edit_burst, resume_first_edit, vscode_state, tab_category.
- P-256 ECDH + commit-reveal SAS reference crypto (`ref/crypto.mjs`).
- Test vectors generated from fixed scalars (`contracts/sync/test-vectors.json`).
- Mocks: mock-ingest, mock-relay, mock-peer with attack modes.
- Tools: verify-sync, privacy-scan.
