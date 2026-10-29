# Laptop lead review — 2026-10-29

Reviewed the1.1.0 stacked laptop branch against the owner addendum and preserved7A exports/semantics. No phone or Gradle work; Antigravity's Reno14 session remains untouched.

| Finding | Fix / evidence |
|---|---|
| A snapshot ending exactly at midnight, or with no positive observed duration, could yield a misleading0minutes today | Require positive observed interval; regression distinguishes no-data from genuinely observed nonfocus0minutes and counts only the cross-midnight overlap. |
| A same-timestamp gap could sort before a flowing snapshot and permit focus minutes | Gap timestamp blocks the conflicting interval regardless of sequence order; regression proves no focus claim. |
| Invalid declaration touched sequence persistence before validation | Canonical validation happens before allocation; regression proves zero storage work for invalid task. |
| Internal declaration helper could copy unexpected properties into local UI state | Copy only task/plannedMin/strict/startedTs/taskSource; canary test proves unexpected field excluded. |

Fresh final `npm run check:s7 --prefix laptop`: typecheck/buildPASS,43tests/pass43/fail0/duration_ms2292.0543; privacy3/pass3/fail0/duration_ms726.7503. `npm test` on combined contract+laptop tree:117/pass117/fail0/duration_ms3721.5341. Rootguards PASS(schema1.1.0,twin96,no-persona317). Native .node scan Count0. Fullstdout VERIFICATION_REVIEW.txt and VERIFICATION_REVIEW_ROOT.txt. No new dependency, no schema/inference model change. Frozen crypto vectors remain unchanged on the contracts branch.

State mapping remains honest: Collector offline requires positive gap evidence; Active but unclassified differs from it; ambiguous producer health stays unavailable; confidence stays rule-based/not calibrated. Current7A lacks direct current-health/persisted focus snapshots, so related no-data/unknown states remain explicit pending inputs rather than guessed values. Pairing/held actions/what-if remain pending actual phone sync. Reference tests never establish actual human focus, saved minutes or calibration.

Verdict: ready for owner approval of the scoped local code after these fixes; manual desktop acceptance is still pending. Run MANUAL_CHECKS.md for hover/click-through, seven dev-labelled behaviors, tray/focus behavior and fullscreen limitations. Do not treat this as target/hardware/Gate2 approval. Merge order, once approved: contracts first, then stacked s7-laptop. No merge or push performed. Untracked file t predates review and remains untouched.
