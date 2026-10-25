# Hidden ground-truth personas

This directory is **HIDDEN GROUND TRUTH** for development-time simulation, parameter recovery and tests. It must never be imported by phone/ or laptop/, including model initialization or runtime decisions. Use the canonical population priors for model initialization; revealing a persona's answer to the learner invalidates recovery experiments.

Run node tools/personas/validate-personas.mjs from the repository root. All three fixtures use persona.schema.json (JSON Schema 2020-12). Rahul's numeric values and sender tokens are exact section 15.1 values; role comes from 15.2. Anika/Kabir are explicitly assumed, with notes. Kabir's 4.5 self-initiated events per hour is the one specified value for that persona. Mu and sigma describe natural-log durations in minutes, matching the spec's duration model; sigma and fatigue multipliers are strictly positive.

These fictional identities/sender tokens are not real-user records. Schema validity does not establish calibration, causal validity, parameter recovery or any measured focus-loss claim. The 14-day event/history generator and recovery experiment are later Stage 4 work. No headline metric is hard-coded here. All persona assumptions are preserved in root DECISIONS_PENDING.md, under the consolidated Stage 1E notes.
