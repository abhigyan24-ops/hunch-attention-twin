# Shared contract 1.0.0

JSON Schema dialect: 2020-12. SCHEMA_VERSION and event.schema.json are canonical. Categories and counts only; forbidden-fields.json applies recursively, including arrays. No arbitrary metadata bags are permitted. Maximum serialized telemetry event size is 8192 UTF-8 bytes, including the envelope. JSON Schema alone cannot enforce byte length: use tools/validate-events.mjs at ingress. No raw rejected values may appear in logs.

## Time, identity, and replay

All timestamps are nonnegative integer epoch milliseconds. ts is the emitting device clock; tsAdj = ts + clockOffset(device), with a positive offset meaning the receiving reference clock is ahead. ingestedTs is stamped by the receiver on ingestion and preserves arrival time; it is optional only for a pre-ingestion outbound event (explicit Task 1A instruction), and must exist in persisted receiver records. Keep original ts, not a destructive rewrite. The offset comes from NTP-style exchange in 14.3; do not invent a stale threshold.

Deduplicate by both (device, seq) and id before learning or persistence side effects. deviceId is pseudonymous. Persist per-install monotonic seq across restarts; device identity/reset semantics remain pending. Late/out-of-order events retain original timestamps and require deterministic replay; a clock jump does not make missing telemetry focus. Production deduplication, clock-jump handling, reconnect/backfill, migrations, rotation/retention, partial JSONL lines, and restart recovery remain not_started and are tracked in the ledger.

## Taxonomy and parameters

categories.json contains the exact 14 categories and 6 surfaces. Process/domain/package maps are starter classification rules, not observed installation facts. Uncertain package candidates are in androidPackageNotes with verify: true. Browsers default to unknown until normalized domain/surface evidence exists. Never persist a URL. Sender IDs must be keyed/salted pseudonyms; hashing and rotation are later implementation work.

default-params.json preserves all Appendix A rows as symbol/default/unit text, including ranges and eligibility conditions, without choosing invented scalar defaults. Section 8.9 priors are numeric Beta parameters and explicit logarithmic expressions for lognormal means; durations there are minutes. These are a parameter contract, not an implemented model.

## Telemetry versus commands

event.schema.json discriminates exactly 20 telemetry types with closed payloads. source matches the collector or persona for synthetic generation; enforcement of synthetic provenance beyond that is downstream work. ContextSnapshot has its own closed schema. notifCat is an open nonempty classification token per owner clarification; it must never contain raw content. Optional videoState is rejected pending its vocabulary.

relay-frame.schema.json uses flat frames with a type discriminator following section 14.3. Commands are separate from telemetry and carry commandId (UUID), issuedAt, expiresAt, cmd, and closed command-specific args. declare_session uses task/plannedMin/strict, show_now uses notifKey; end_session and kill_switch currently accept empty args only. No command acknowledgement is implied by a telemetry sequence ack. Commands require paired-peer authorization, runtime expiry checks (including expiresAt > issuedAt), acknowledgement and persistent idempotency before actuation. JSON Schema validates structure only. Replaying telemetry must never actuate a command.

The encrypted type is reserved as {"type":"encrypted"} only. It carries no ciphertext and is not an operational encryption capability. Do not accept arbitrary ciphertext fields until Stage 8 defines authenticated metadata, nonce uniqueness, replay rejection, rotation, and a reviewed pairing design under 25.8. WSS, rate limits, per-frame byte caps and nested event byte validation require later relay implementation; no relay deployment is verified.

See DECISIONS_PENDING.md for unresolved contracts. SCHEMA_VERSION 1.0.0 is the Stage 1A baseline; downstream work must resolve pending items through an explicitly approved canonical-contract change rather than silently broadening a schema.

## W3 reconciliation (1.1.0)

`docs/OWNER_DECISIONS.md` DD-1–DD-9 authorizes the controlled1.1.0 event bump and reviewed field changes. The core event envelope remains the telemetry authority. `relay-frame.schema.json` is now explicitly archival/deprecated: it is not a Stage7–9 network validator. Use `contracts/sync/schemas/` and `docs/contracts/SYNC_CONTRACT.md` for outer frames, authenticated hello, AEAD, state, control and acknowledgements. Production relay migration belongs8L. gap reason tokens remain open strings; local collector gaps use the emitting role as both endpoints. See the sync contract registry for documented values. No wire command contains a notification key; show_now uses only opaque heldId or all:true.
