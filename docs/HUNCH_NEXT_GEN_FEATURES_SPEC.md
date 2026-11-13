# Hunch next-generation cross-device features

Codex owns this document, the Android share package and matching tests, and only the share activity entry in AndroidManifest.xml. Antigravity owns Walk-Away, the engine, browser and VS Code extensions, and laptop integration.

## Share to Desk

Android accepts ACTION_SEND with text/plain. The parser preserves the note and title, extracts the first HTTP(S) URL, and classifies URL, code, or note. The app queues up to 200 items in private preferences and dismisses the share target. The toast confirms local storage. A network-constrained WorkManager worker posts JSON with id, rawText, extractedUrl, title, itemType and createdAtMillis. It retries failures and removes an item after a 2xx response. The server should deduplicate by id. Android backup excludes the queue.

The pairing integration must provision a trusted HTTPS engine_url and engine_token in private sync_config preferences. Delivery uses X-Twin-Token and refuses cleartext or redirects. Without those values or an accepting POST /v1/flow/share-to-desk endpoint, items remain queued. The receiving UI should present shared text as text and require user action before opening links.

## Walk-Away and checkpoint

Require a confidence score of at least 0.75. Award 0.50 for at least 12 steps within 12 seconds, median interval 380–850 ms, interval deviation at most 180 ms, and acceleration RMS 1.1–6.5 m/s². Award 0.25 for a 6 dBm Wi-Fi drop, recent unplug or lift within 30 seconds, or 20 rhythmic steps. Award 0.25 for laptop idle at least 45 seconds, or 15 seconds with both prior signals. Veto active calls, laptop idle under 15 seconds, and active at-desk phone use without valid cadence. Checkpoint on AWAY and resume only the owned pause on RETURNED.

## Cognitive thrash shield

Observe three consecutive failed tasks within 180 seconds, at least 12 undo/delete edits within 60 seconds, or at least eight switches across four files in 45 seconds without a save. Send a reason to the engine and apply an expiring ten-minute shield when enabled. Offer a short hypothesis prompt or a break. Avoid retaining source text.

## Fatigue-aware pre-commit review

Track workspace-relative paths and interruption counts. Mark edits during or within 30 minutes of bedtime or after less than six hours of observed sleep. The pre-commit audit surfaces affected files for a quick review and leaves the commit decision to the user. Report unknown evidence explicitly.

## Procedural offline flow audio

Generate filtered noise locally with Web Audio and offer a quiet optional stereo pulse. Start only when enabled for focus. Duck by 18 dB for calls or a build-complete transition and fade to silence over 60 seconds at pre-bedtime wind-down. Keep manual mute and volume independent of detection.
