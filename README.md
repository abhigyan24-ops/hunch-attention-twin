# Dhyan (ध्यान)

Dhyan is a local-first, cross-device attention digital twin. The planned phone engine learns from categories and counts, simulates notification choices, and drives safe notification actions and Pip on the laptop. All features in sections 1–25 remain planned. Stage 1A contracts, Stage 1B local relay, Stage 1C laptop source scaffold, Stage 1D Android skeleton and Stage 1E hidden personas/offline dataset tool are implemented. Stage 1F local verification is recorded; the full Stage 1 exit remains pending owner hardware/cloud/schema checks. See HANDOFF_STATUS.md for tested behavior and incomplete runtime/deployment checks.

Canonical specification: [docs/SPEC.md](docs/SPEC.md). Read [AGENTS.md](AGENTS.md) and [HANDOFF_STATUS.md](HANDOFF_STATUS.md) before editing. Section 25 overrides older safety, measurement, privacy, and validation wording.

## Setup and tests

Node.js and npm are required. Relay target is Node 20; see handoff for the actual local runtime.

```sh
npm install
npm test
npm run checks
node tools/validate-events.mjs core-spec/fixtures/valid/notif_posted.json
```

The validator checks JSON Schema 2020-12, recursive banned keys, and the UTF-8 serialized event limit of 8192 bytes. Diagnostics never echo rejected content. Negative fixtures are synthetic privacy regression inputs, not collected user content. Checks on absent phone/laptop modules are structural checks only; hardware and production integration are not verified on target.

## Planned repository layout (spec 5.3)

```
dhyan/
├── core-spec/                # event schema JSON, category map, state defs, default params
├── phone/                    # Android Studio project
│   └── app/src/main/java/.../
│       ├── collectors/       # NotifCollector, UsageCollector, ScreenReceiver, A11yCollector
│       ├── ingest/           # Normalizer, AppCategorizer, LabelStore, ClockOffset
│       ├── fusion/           # ContextFuser (phone + laptop → in-memory StateFlow<ContextSnapshot>)
│       ├── inference/        # FocusStateInferrer, CompulsiveScore, FatigueIndex
│       ├── twin/             # (pure Kotlin, no android.* imports)
│       │   ├── params/       # BetaStat, LogNormalStat, InMemoryParamStore, Hierarchy
│       │   ├── episodes/     # EpisodeBuilder, OutcomeWindow, EpisodeCloser
│       │   ├── simulator/    # SemiMarkovSim, PairedMonteCarlo
│       │   ├── counterfactual/ # CounterfactualEngine, ExplorationPolicy, Regret
│       │   └── decision/     # Importance, CostFunction, ActionSelector, Explainer
│       ├── safety/           # NeverHoldRules, KillSwitch, Undo
│       ├── actuator/         # Holder, DigestBuilder, ReleaseScheduler
│       ├── personas/         # PersonaDef, HistoryGenerator, FastForward
│       ├── sync/             # RelayClient, OutboundQueue, Backfill
│       ├── data/             # Room entities + DAOs (async write-through)
│       ├── metrics/          # Calibration, ParamRecovery, SavedMinutes
│       └── ui/               # SimulateLabTab, LiveDigestTab, InsightsTab, PersonaSettingsSheet
├── laptop/                   # Electron + TS (electron-vite template)
│   └── src/
│       ├── main/             # app lifecycle, pet window, tray, jsonlStore.ts
│       ├── adapters/         # AwAdapter, ActiveWinFallback, VsCodeFeed, BrowserFeed
│       ├── normalizer/       # categories.json, normalize.ts
│       ├── state/            # focusState.ts
│       ├── sync/             # relayClient.ts
│       ├── pet/              # petStateMachine.ts, sprites/, bubbles.ts
│       └── panel/            # React/HTML panel: today, held items, declare session
├── relay/                    # server.js, Caddyfile, deploy.sh
├── vscode-ext/               # Stage 9
├── browser-ext/              # Stage 9
└── tools/                    # persona dataset generator (LLM), schema validator, replay CLI
```

Additional Stage 1A governance: TRACEABILITY.md, STAGE_GATES.md, HANDOFF_STATUS.md, and DECISIONS_PENDING.md. Directories shown above describe the full planned scope. phone/, laptop/, and relay/ now contain Stage 1B–1D scaffolds; extensions remain placeholders and later product modules remain planned.

## ActivityWatch credit

The planned laptop collection backbone uses [ActivityWatch](https://activitywatch.net/), an open-source activity tracker, behind a replaceable adapter. ActivityWatch is credited as the collection foundation; its integration is not implemented or verified here.
