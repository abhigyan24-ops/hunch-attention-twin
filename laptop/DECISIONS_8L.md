# Stage 8L decisions

The prompt expressly permits one root script check:s8l despite the general Task1A root-package restriction; only that key is in scope. Frozen contracts remain unchanged.

Receiver adjusted timestamps are stored as local metadata beside the canonical event, never extra core event fields. Durable batch settlement must include valid events and metadata-only tombstones before ACK. Existing own-event JSONL remains the laptop sender's source of truth. Pairing/session credentials require encrypted safeStorage; no plaintext fallback. No auto-confirm option is exposed in the real app; tests inject a synthetic confirmation callback.
