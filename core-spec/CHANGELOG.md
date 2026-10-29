# Changelog

## 1.0.0 — Stage 1A baseline, 2026-10-25

- Versioned envelope and 20 strict telemetry payload variants; ContextSnapshot and relay frame contracts.
- Epoch ms, tsAdj = ts + clockOffset(device), receiver ingestedTs, deduplication by (device, seq) and id.
- 8192-byte complete event cap and recursive forbidden-field validation.
- Separate telemetry replay from command authorization/expiry/acknowledgement/idempotency.
- Exact Appendix A defaults, section 8.9 priors, 14 categories, 6 surfaces, and synthetic positive/negative fixtures.
- Reserved encrypted marker only; open decisions and production recovery/security requirements remain tracked. No device behavior or encryption implementation is claimed.

## 1.1.0 — W3 owner-approved reconciliation, 2026-10-29

- Signed DD-1–DD-9: core envelope version1.1.0; tab_category optional videoState playing/paused/none; old incompatible schema versions reject explicitly.
- Archived flat relay schema is deprecated as a wire authority. Its reviewed command argument shapes use heldId/all, kill_switch.on, bounded declared-session args and independent cmd_ack. New sync outer/encrypted schemas remain authoritative; production relay code is untouched until8L.
- gap roles remain phone/laptop, with local gaps using the emitter for both roles; reasons remain nonempty controlled tokens, documented by the sync contract. No enum narrowing or gap time-field invention.
- Positive/negative reconciliation matrices supplement, not replace, the20event/8negative baseline fixtures. SchemaVersion constant and matching version assertion receive only the literal version synchronization; no phone runtime/migration code.
- Existing1.0.0 stored events require a reviewed version mapping before1.1.0 sync ingestion; no automatic rewrite of user history is claimed. Phone Room envelope migration remains8P1 after approvedM0/Gate2.
