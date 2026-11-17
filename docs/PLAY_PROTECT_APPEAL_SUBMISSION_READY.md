# Request for Play Protect review of Hunch v2.0.0

- **packageName:** `com.dhyan.app`
- **versionName:** `2.0.0`
- **versionCode:** `2`
- **apkSizeBytes:** `10261129`
- **apkSha256:** `313d0f10ccfe9ef8233579b070a72aa77edbac7753decd99725846c4ae946c76`
- **signingCertSha256:** `3ec8e983132712b44585e4986378a42beba71bfd48d4d20f3452d3b860d653d4`

[VirusTotal file lookup](https://www.virustotal.com/gui/file/313d0f10ccfe9ef8233579b070a72aa77edbac7753decd99725846c4ae946c76/detection) Â· [Google appeal form](https://support.google.com/googleplay/android-developer/contact/protectappeals)

Package Name: com.dhyan.app
Version: 2.0.0 (versionCode 2)
APK SHA-256: 313d0f10ccfe9ef8233579b070a72aa77edbac7753decd99725846c4ae946c76
Signing Certificate SHA-256: 3ec8e983132712b44585e4986378a42beba71bfd48d4d20f3452d3b860d653d4
VirusTotal Analysis URL: https://www.virustotal.com/gui/file/313d0f10ccfe9ef8233579b070a72aa77edbac7753decd99725846c4ae946c76/detection

Please review Hunch, an open-source focus companion, for a possible incorrect classification.
G1: Review manifest permissions against the restricted permission list.
G2: Accessibility configuration declares window-state events and no window-content access. Review runtime behavior in A11yCollector as well.
G3: System and security notifications should bypass filtering. The source is available for review.
G4: Optional sensitive permissions require a user disclosure and an explicit Android Settings grant.
G5: Hunch supports leaving Play Protect enabled.
G6: The repository includes compliance and update-check unit tests. The packet verifies the APK identity and signatures separately.
A VirusTotal link identifies the exact file by hash and does not indicate that an analysis exists.
