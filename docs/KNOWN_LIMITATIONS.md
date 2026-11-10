# Known Limitations & Beta Notice

Hunch is currently distributed as a **public beta (`v1.0.0-beta.1`)**. Please review these known limitations:

1. **Operating System Constraints:**
   - Supported on Windows (10/11) and Android (14/15/16).
   - macOS and Linux builds are experimental and not bundled in the automated beta pipeline.
   - iOS is not supported due to platform sandboxing restrictions that prohibit background notification inspection.

2. **Unsigned Desktop Installer:**
   - Windows installer binaries are not yet code-signed with an EV certificate. Windows SmartScreen will display an unrecognized publisher warning upon execution. Users should verify installers against `SHA256SUMS.txt`.

3. **Google Play Store Policy:**
   - The Android app requires the Accessibility Service to detect scrolling activity and provide app restriction features. Because Google Play restricts accessibility service usage, Hunch is distributed as a direct APK download.

4. **Independent Cryptographic Audit:**
   - The custom device-pairing protocol and sync encryption layer have not yet undergone an independent third-party security audit.
