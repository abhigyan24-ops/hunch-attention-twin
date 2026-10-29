# Stage7C decisions

Owner delegated the undefined auth-failure identity: use the loopback socket IP, not an invented remote-port range. Five failures in60seconds block NEW auth/upgrade attempts for5minutes. One faulty local client can temporarily block other new clients; existing valid authenticated sessions continue. No arbitrary user identifiers are collected. This is the explicit7C implementation decision for the earlier contract ambiguity.

The owner permits a narrow compatibility fix to the shared attack suite (correct /ingest path, real-shaped extension ID and its CLI result typo). All other shared tools/contracts remain read-only.

Feed liveness follows local heartbeat receipt time, not a sender-controlled timestamp. Latest state stays meaningful while that connection sends heartbeats; edit rate additionally expires after its declared window. Reconnect clears previous state. No numeric stuck threshold is invented; only fresh focused vscode_state=stuck supplies stuck evidence. Offline feed data contributes neither focus nor distraction.

Resume correlation: canonical event payload stays {delayMs}; transient local association is {lostTs,returnedTs,observedTs,delayMs} or null if no matching focus return. No invented correlation/event keys enter persisted telemetry.
