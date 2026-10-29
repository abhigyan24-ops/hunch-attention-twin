# UI implementation decisions

- Owner approved the Phase1/2 sage/obsidian plus warm-light design and requested less clutter. Preserve the attention-twin product; do not invent audio, meditation or streak features.
- Approve only official ui-tooling-preview and debug ui-tooling under existing BOM2025.10.00. No Haze/Lottie/Vico/Coil/icon-pack runtime dependency or Kotlin/BOM upgrade.
- Existing top-level color names will remain compatible while their final values read semantic Material3 roles inside composition. Remove fixed text colors from typography before migrating color accessors; theme construction must use pure palette values to avoid composition cycles.
- Existing ViewModel APIs remain authoritative. Shell badges must use real live state, not legacy demo queues. Preview-only fixtures must be visibly synthetic and must not create databases or train models.
- Missing analytic series remain marked unavailable; no fabricated scores, heatmaps, uncertainty bands or update counts. A time-in-state ring is not a timer goal or a calibrated confidence score.
- Feedback buttons require a real matching decision record; do not invent record id0. Where current data cannot supply that link, keep the feature visible with a plain unavailable explanation.
- Preserve existing action callbacks and report their functional limitations; a visual tap is not proof of notification delivery. Do not change actuator/model/collector/sync contracts during this UI task.
- Retain only harmless tab/expansion state through recreation. Pairing code, SAS, contact input and raw notification content must not be persisted or logged.
- Current model recovery, actual rendering on Reno14, live pairing and cloud deployment remain separate verification limits. Never describe a compiled preview as a target-device check.
- Visual inspection confirmed the existing digest default is 120 minutes. Preserve it and any current configured interval in the selector; do not replace that value with the previous UI's 15–60-minute choices. This is a presentation fix using the authoritative existing constant, not an inferred scheduling change.
