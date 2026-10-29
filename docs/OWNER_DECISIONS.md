# Owner decisions (authority for agents; unticked = stop and ask)

- [x] DD-1  core-spec may be bumped ONCE, to 1.1.0, only inside task W3, with CHANGELOG, fixtures and tools/check-schema-sync.mjs green.
- [x] DD-2  videoState enum: "playing" | "paused" | "none".
- [x] DD-3  Commands: kill_switch args {on:boolean}; end_session args {}; declare_session args {task: taskType enum, plannedMin 5..480, strict:boolean}; show_now args {heldId} or {all:true}. (notifKey is NOT exposed to the laptop.)
- [x] DD-4  Command acknowledgement uses the cmd_ack message from DHYAN_SYNC_CONTRACT_v1 section 6.
- [x] DD-5  state.declared uses the active-session shape {task, plannedMin, strict, startedTs} or null.
- [x] DD-6  Local collector gaps use deviceFrom = deviceTo = the emitting device; reason is a free token from the contract list (peer_silent, relay_down, schema_mismatch, queue_overflow, notif_listener_disconnected, a11y_disconnected, usage_query_failed, service_restarted, device_rebooted, clock_changed, permission_revoked, persistence_failure, adapter_offline).
- [x] DD-7  The sync contract's outer/encrypted frames supersede the old flat relay frames; the relay is rewritten in task 8L (not before). core-spec keeps the event envelope.
- [x] DD-8  If the phone Room events table does not persist id, seq, deviceId and schemaVersion, task 8P1 may add them with a real Room migration (no destructive migration) and a mapping layer, after the owner approves the 8P1 M0 plan.
- [x] DD-9  "Active but unclassified" laptop activity: NO new laptopState enum value. Add an optional boolean `adapterHealthy` to the sync `state` body (sync contract 1.1.0, protocol minor). laptopState stays `offline` for unclassified activity, and receivers use adapterHealthy to tell "collector silent" from "active but unclassified".
- [x] DD-10 Phone foreground service type: 8P1 M0 must check current Android docs for the target SDK; if `dataSync` has a time limit that threatens 12 h collection, propose `specialUse`. Apply only after owner approval of the M0 plan.
- [x] DD-11 Communication apps (Slack, Teams, etc.) count as work only when the user labels them; otherwise unclassified. Games: known list plus user label only, never paths.
- [x] DD-12 "stuck": the laptop maps the VS Code extension's own vscode_state=stuck to setStuckEvidence(true). No numeric stuck thresholds are added on the laptop.
- [x] DD-13 Rule-based laptop confidence is "uncalibrated"; never shown as a percentage or probability anywhere (laptop, phone, pitch).
- [x] DD-14 Gate 2 is run by the owner on a build of main AFTER F1 is merged. No phone Stage 8 code starts before Gate 2 evidence is recorded in STAGE_GATES.md.
- [x] DD-15 Parallelism: up to 3 parallel work streams per agent are allowed ONLY with disjoint file scopes (section 3); merges go through the lead agent; the owner reviews branch by branch.
