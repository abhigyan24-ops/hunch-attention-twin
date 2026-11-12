# Android release trust and bedtime integration

The release workflow requires the permanent signing identity, verifies the APK signature and pinned certificate, and stores the APK and V4 signature sidecar together. The job summary includes the signer fingerprint. A manual run on main builds artifacts without publishing. Signing continuity supports Android updates with the same certificate. Google Play Protect makes its own decisions.

Run `tools/generate-android-keystore.ps1` on the Windows account that owns the signing material to confirm the fingerprint. Use `-CopyToClipboard` to export the existing encrypted keystore for Actions. Keep an encrypted backup of the keystore and password. The Windows password file is tied to the local account and machine. Reserve `-InitializeNewIdentity` for a new signing lineage.

## Dashboard integration handoff

Keep dashboard, manual focus, and manual bedtime available from launch. At the existing user-initiated entry points, present `ProgressiveTrustDialog` with the following capability:

| Trigger | Capability |
| --- | --- |
| Sync bedtime from device activity | `BEDTIME` |
| Hold distracting notifications | `QUIET_FOCUS` |
| Advanced app context | `ADVANCED_CONTEXT` |
| Floating focus HUD | `FLOATING_HUD` |

On `onSettingsReturned`, recheck the corresponding Android grant before marking the feature enabled or refreshing bedtime inference. Returning from App info only enables another attempt to open the feature settings. Android determines whether to show the restricted settings menu and whether a grant is permitted. Keep the app usable when access is declined. The dialog only opens Android's supported settings screens.

The manifest owner must reference `@xml/network_security_config` via `android:networkSecurityConfig` to activate the loopback and Android emulator host allowlist. Use encrypted transport for LAN and relay pairing. Android network security XML accepts named hosts and subdomains, so it cannot express the requested private CIDR ranges. Retain all existing permission declarations and verify the emulator and device pairing paths with the manifest owner before wiring the resource.

The Wellbeing owner is responsible for Usage Stats inference, manual override persistence, and the bedtime card. The onboarding dialog makes no changes to that state.
