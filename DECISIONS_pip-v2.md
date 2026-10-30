# Pip v2 M0: pending owner decisions

All proposals below are ASSUMED, not approvals. No product behavior has changed. The supplied prompt explicitly stops after M0; OWNER_DECISIONS.md has not been edited.

| ID | Proposal for review | Evidence / unresolved condition |
|---|---|---|
| P-1 | Provisionally choose B: one overlay per display | Lower scripted-drag CPU in the isolated trial; physical click-through, mixed DPI, fullscreen and cross-display handoff remain unverified. Fall back only on measured failure. |
| P-2 | Permit L0–L4 | Subject to suppression, dismissal, budgets and non-blocking placement; no product implementation yet. |
| P-3 | Start with the supplied thresholds/budgets | Every proposed numeric value is centralized in laptop/test/pip-v2-spike/pip-config.proposed.json. No efficacy claim or tuning performed. |
| P-4 | social, short_video, games, shopping, news; feed/shorts evidence; watch thresholds doubled | Clarify whether video/watch alone qualifies, or only watch in an approved category. Default gaming DND must override gaming evidence. |
| P-5 | No sound, no sound setting | Matches supplied design. |
| P-6 | Quiet hours 22:00–07:00 local time | Proposed configurable default; clock/day rollover tests required. |
| P-7 | Control+Alt+P, configurable | Registration can fail. Tray Pause remains available; report collision rather than claiming registration succeeded. |

## Additional decisions needed before implementation

1. **Evidence adapter:** PetInputBus has no category/surface. Propose a new pet-only adapter using existing sanitized LaptopPipeline.onEvent output and FeedRouter.snapshot freshness/tabActive. Pass only category/surface enums to pure policy, never whole events or process identifiers. Do not alter 7A exports or semantics.
2. **Break path:** No canonical declared break exists. The declared-session task enum excludes break. Approve a local Pip-only breakUntilMs gate; do not invent a sync task or command. Reuse the existing main-process declareLocally callback for focus actions. The panel's unpaired Start Focus alert is not a working declaration path.
3. **Narrow scope extension:** Secure new interaction IPC needs laptop/src/preload/index.ts; a separate pet entry may also need electron.vite.config.ts. These are outside the prompt's explicit ownership list. Request approval for pet-only changes there, with sender/frame/payload validation; no such edits made at M0.
4. **Declared work precedence:** The design forbids movement/popups during a declared focus session, while later ladder examples presume recent work. Propose declared active work suppresses autonomous slides and bubbles; owner must confirm precedence. Flowing always suppresses motion.
5. **Missing-data gaps:** Propose no dwell accumulation or nudges during missing/stale evidence; retain episode identity for gaps up to 20 seconds, reset beyond it. This does not override existing 15-second local staleness. Confirm before freezing tests.
6. **Leisure check-in:** “Long leisure” has no defined onset. Leave optional leisure check-in disabled pending a definition; the proposed one-hour interval alone does not define onset.
7. **Delegation:** Referenced DHYAN_NEXT_PROMPTS.md section L is absent. No subagents were used. Signed DD-15 permits at most three disjoint streams; the S1–S4 plan is a scope proposal, not concurrent authorization beyond that limit.

Windows input behavior, multi-monitor operation and product idle CPU remain UNKNOWN / not verified on target. M0 numeric results do not accept P-1 or replace owner testing.

## Owner update and M1 interpretation

Owner approved the proposed defaults in chat on this continuation: P-1–P-7, declared-focus suppression, approved-category-only watch evidence and gaps without dwell accumulation. Implementation scope extensions remain pending.

New explicit interpretation pending review: qualify recent work at episode entry, then retain it through continuous distraction. This permits L4 at 20 minutes despite the 15-minute lookback. The pure policy currently uses this interpretation, with no runtime wiring. Do not mark M1 accepted until reviewed.
