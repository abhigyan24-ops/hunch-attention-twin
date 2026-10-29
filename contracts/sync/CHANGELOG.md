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

## 1.1.0 — W3 owner approvals, 2026-10-29
- Core event version1.1.0, videoState enum; exact command args/cmd_ack; nullable active declared session; optional state.adapterHealthy and canonical enums. No new laptop state.
- Original immutable rendezvous offer retained for late phone, role/order/expiry checks in mock only. Production relay unchanged until8L.
- Owner-delegated ACK choice: atomic durable settlement of accepted events and privacy-safe rejection tombstones; restart dedup/cumulative cursor tests. Canonical valid-invalid-valid payloads are partially accepted without false durability claims.
- Regenerated PUBLIC TEST ONLY vectors; byte-level crypto derivations and ciphertext unchanged from1.0.0; metadata now marks1.1.0. Hash recorded after final diff review below.
- Strict1.0state receivers may reject the new optional field; both peers must upgrade before emitting it. No silent old-event migration or capability negotiation claimed.8P1 Room migration and cross-language/target checks remain gated.
- Final vector hash (canonical UTF8/LF): `3a3e92d7952ee3ed4a95787771c7732daf3699c86cd5f09b37e8a820142202a9`; all intermediate crypto bytes/ciphertexts match previous fixed vectors.
