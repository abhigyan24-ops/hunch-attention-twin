# Hunch Play Protect preflight

1. Audit the merged Android manifest for restricted permissions.
2. Limit accessibility to window-state events in XML and at runtime. Keep content retrieval disabled.
3. Check that protected system packages exit the notification listener before any hold logic.
4. Show the capability disclosure and record acceptance before opening sensitive settings.
5. Keep Google Play Protect enabled.
6. Run the compliance and update checker tests. Separately inspect the exact release APK with aapt and apksigner.

Run tools/generate_play_protect_appeal.py with Android SDK build tools available. It checks local APKs, the published v2.0.0 release, then the signed Gradle build when signing is configured. It rejects debug, stale, and incorrectly signed artifacts. The packet is generated only when the real APK verifies. A VirusTotal URL is a hash lookup and does not mean the APK has been scanned. Signing and preflight checks do not establish Play Protect approval.
