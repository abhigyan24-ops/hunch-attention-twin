# Dhyan Daylight UI architecture

This presentation-only redesign builds on the existing Kotlin 2.3.10 / Compose BOM 2025.10.00 app and its version-4 Room compatibility fix. No ViewModel, policy, model, collector, persistence contract, pairing state machine or runtime dependency changes.

## Tokens

- Light: canvas #F7F8FA, white cards, #EAEEF4 borders, charcoal #121417 actions and type. Sage #E8F3E8, lavender #E9EAF8, oat #EFEBE4 and sky #E3F4F4 group related content. Saturated pastel mascot colors are decoration, never small-text foregrounds.
- Dark: slate #11151B canvas, #1B2028 surfaces, paired dark sage/lavender/oat/sky containers and high-contrast light type. The existing DhyanTheme selects the complete palette; no dynamic-color override or downloaded font.
- System sans-serif; editorial 36/43 sp headers and 64/72 sp numeric display. Layout uses natural height and scroll containers rather than clipping scaled text. Card radius 28 dp, navigation 36 dp, pill actions/chips, 20–24 dp content insets and 12–24 dp gaps.
- PaletteContrastTest checks actual foreground/background token pairs. Actions keep their existing enabled/disabled states and callbacks. Live-mode confirmation remains mandatory.

## Stateless components

DaylightComponents.kt owns PastelSquircleCard, StatusPillBadge, SemicircularTickDial, RoundedBarChart, MiniSparklineCard, FloatingCapsuleNavBar, RecordedShareRing and CalendarDayStrip. No component performs I/O or invents observations.

- Dial: 41 Canvas ticks, endpoint-inclusive semicircle mapping, triangle marker, explicit number/unit. Touch/drag and accessibility SetProgress call the same supplied callback. Simulate retains its precise slider too. The displayed input is hours worked today, not an invented meditation countdown.
- Charts: rounded bars and labels use supplied recorded action counts. Share ring uses allowed / total decisions and is labelled Allowed, not confidence or completion. Missing/invalid denominators show an em dash. All-zero data stays an explicit empty state.
- Mini cards draw a sparkline only when at least two real samples are supplied. Current Insights has no historical series, so its cards say No trend history alongside real counts. No fabricated waveform.
- Calendar strip highlights the current calendar day only, with a visible calendar-only/history-unavailable caption. It is not a streak or a daily completion record.
- Navigation preserves the same three destinations and saveable selection, with role=Tab semantics, selected state and nav-simulate/nav-live/nav-insights tags. Floating capsule respects navigation-bar insets. Existing content scrolls above it.

## Screen and state map

| Surface | Existing source | Presentation and preserved actions |
|---|---|---|
| Root | MainActivity + existing ViewModelProvider | Same DhyanTheme → DhyanPetOverlay → DhyanMainScreen. Existing fused declared-session start/expiry binding is unchanged. Settings sheet and saveable destination stay wired. |
| Simulate hero/persona | SimulateUiState.selectedPersona/isTrained/isReplaying/replayProgress | Explicit synthetic sandbox, same three personas, replay switch and progress. No platform action implied. |
| Context | taskType/hoursWorked | Pastel task grid invokes setTask; dial and original slider invoke setHoursWorked. No new task enum or input range. |
| Interruption | simCategory/simNotifCat/simIsVip | All four category pairs, trusted-sender switch and never-hold explanation retained. |
| Advanced context | attentionState/minutesInState/currentHour/hourBand/simUrgency/simRepeatCount | Same disclosure, controls, ranges and callbacks. Attention label remains display context only. |
| Simulation result | isFailOpen/reasonCode/simulationResult | Fail-open message or existing verdict, loss comparison and simulation interval. No not-ready zero-loss claim. |
| Twin Lab | provenance/nEff/modelVersion/priorAlpha/priorBeta/posterior/updateCount | Same disclosure, model evidence, prior/posterior charts and readiness gating. The endpoint crash correction is retained. |
| Live policy | mode/killSwitchActive/isFakeProvider | Explicit OFF/DRY_RUN/LIVE behavior, sandbox-provider warning, stop/resume callback. |
| Live context | contextSnapshot/laptopAvailability/provenance | Existing no-data state and state ring; rule-based, not calibrated wording retained. Laptop stale/offline wording is not replaced by a decorative Connected badge. |
| Digest | heldQueue/decisionFeed | Categories only, Show now, Important, Noise, Release all, feedback-unavailable and delivery-unconfirmed wording retained. |
| Records | decisionFeed/historyStrip | Same record disclosure, reason/safety reason, timestamps, propensity, simulated loss, exploration, failed-open, actuation status and settled history. |
| Insights | InsightsUiState action/outcome totals | Sage total card, allowed share ring, four-action count chart, released/failed-open mini cards; original full count disclosure and refresh remain. |
| Six insights | No measured history exposed by current ViewModel | All six existing disclosures retained, with explicit unavailable/pending wording. Intentionality/calibration/saved-minutes section also retained. |
| Settings | SettingsUiState | Mode selection with confirmation, kill switch, all existing digest interval options including current/default, trusted sender add/remove, caution/exploration and debug post unchanged. |
| Sync | syncStatus/relayUrl/hasStoredCredentials/activeDeclaredSession/recentCommands | Badge lights only for actual Connected status. Every status description, SAS compare/confirm/reject, pair/unpair confirmation, active session, relay and command details remain. Secrets are not logged or saved by UI. |

## Cloud Spirit

DhyanPetCanvas uses a single cubic multi-puff silhouette, radial body shading, clipped frosted rim/specular highlight, ambient shadow and aura. Curious/focused states add a translucent joined eye mask; eyes retain white sclera, dark pupils, dual catchlights, gaze springs, blinking, arc morphs and dizzy spirals. Sprout, particles, speech and all ten emotions remain procedural Compose drawing.

The public character/overlay signatures, 96 dp gesture box, passive global Initial observer, seed sources, physics integration, minimization and session binding stay unchanged. The viewport reserves 104 dp plus a font-scale allowance above the system navigation inset for the floating capsule. This is presentation geometry, not a change to gesture recognition or inference. Cosmetic FOCUSED is not a calibrated attention measure. No image assets or external libraries were added.

## Verification

New unit tests check semicircle endpoints, invalid pointer input and observed-share empty/invalid cases. New instrumented tests check actual capsule taps/selection, dial accessibility and pointer drag, and honest empty analytics. Existing pet and startup regressions remain. Executed commands/results and remaining limitations are recorded in DECISIONS_daylight-ui.md; previews alone do not constitute device verification.
