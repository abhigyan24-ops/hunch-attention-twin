# Hunch Android companion upgrades

Codex owns this document, the qs and widget packages and tests, their two widget resources, and the tile and widget declarations in AndroidManifest.xml. Antigravity owns digest, phantom, audio, doomscroll, pocketpark, and dashboard integration. Existing permissions and components remain in place.

## Quick Settings and lock-screen actions

The system tile displays shield state, remaining minutes, and held count from private hunch_companion_prefs. A tap changes the preference and refreshes the widget. A locked device prompts for unlock before changing the tile state. The lock-screen helper exposes immutable explicit PendingIntents for extending an active shield by ten minutes, opening Pocket Park, and opening Break Digest. Notification attachment and app routing belong to the notification and dashboard owners. Keep notes and sender names out of lock-screen content.

The state contract uses hunch_shield_active, hunch_shield_remaining_mins (default 25), hunch_held_count, hunch_deep_work_mins_today, and hunch_peak_window_label. The dashboard and session owner must connect these preferences to actual focus control and publish observed metrics. A preference update alone does not change notification holding or a session deadline.

## Home-screen Flow widget

The resizable 2×2 widget displays flow minutes today, shield status, held alerts, and the peak window. Toggle Shield updates shared state and Pocket Park opens the app with the quick-note action. The dashboard owner consumes the action to open capture. Call the widget refresh helper when observed metrics change.

## Interactive Break Digest

Group held notifications by app with label, category, count, latest timestamp, and up to three recent sender labels. Keep sender labels and original PendingIntents in memory only. Opening a group sends the original PendingIntent when valid, otherwise opens the app. Remove the group after successful opening. Never persist message bodies.

## Phantom unlock detection

Detect at least three unlocks lasting at most 20 seconds in 15 minutes, excluding utility use. Also detect a feed app opening within three seconds of an unlock when another unlock occurred within ten minutes. Enforce a 20-minute cooldown and offer a dismissible prompt.

## Earbuds-in flow

Observe personal audio outputs through AudioManager device callbacks. Offer a 25-minute focus session only when no shield or call is active, bedtime is inactive, and the last prompt was at least 30 minutes ago. The user chooses whether to start.

## Doomscroll breaker and Pocket Park

Track foreground dwell time in configured feeds. During active focus, peak focus or bedtime, nudge at four minutes and offer a firmer break at eight. Otherwise use eight and fifteen minutes. A switch of at most 15 seconds preserves dwell and a switch of at least 60 seconds resets it. Pocket Park captures a user-entered note for the next Break Digest and syncs it through the existing paired path. Keep note capture and replay user-controlled.
