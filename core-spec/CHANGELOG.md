# Changelog

## 1.0.0 — Stage 1A baseline, 2026-10-25

- Versioned envelope and 20 strict telemetry payload variants; ContextSnapshot and relay frame contracts.
- Epoch ms, tsAdj = ts + clockOffset(device), receiver ingestedTs, deduplication by (device, seq) and id.
- 8192-byte complete event cap and recursive forbidden-field validation.
- Separate telemetry replay from command authorization/expiry/acknowledgement/idempotency.
- Exact Appendix A defaults, section 8.9 priors, 14 categories, 6 surfaces, and synthetic positive/negative fixtures.
- Reserved encrypted marker only; open decisions and production recovery/security requirements remain tracked. No device behavior or encryption implementation is claimed.
