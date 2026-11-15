#!/usr/bin/env python3
"""
generate_play_protect_appeal.py — Hunch Play Protect Appeal Packet Generator

Usage:
    python tools/generate_play_protect_appeal.py [--apk <path_to_signed.apk>] [--out <output_dir>]

Outputs:
    docs/PLAY_PROTECT_APPEAL_SUBMISSION_READY.md
"""

from __future__ import annotations

import argparse
import hashlib
import os
import re
import shutil
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path


# ── Constants ──────────────────────────────────────────────────────────────────

REPO_URL = "https://github.com/abhigyan24-ops/hunch-attention-twin"
VIRUSTOTAL_URL = "https://www.virustotal.com"
APPEALS_FORM_URL = "https://support.google.com/googleplay/android-developer/contact/protectappeals"


# ── Gradle parsing ─────────────────────────────────────────────────────────────

def parse_gradle_kts(gradle_path: Path) -> dict[str, str]:
    """Extract applicationId, versionCode, versionName from build.gradle.kts."""
    text = gradle_path.read_text(encoding="utf-8")
    result: dict[str, str] = {}

    m = re.search(r'applicationId\s*=\s*"([^"]+)"', text)
    if m:
        result["applicationId"] = m.group(1)

    m = re.search(r'versionCode\s*=\s*(?:.*?)(\d+)', text)
    if m:
        result["versionCode"] = m.group(1)

    m = re.search(r'versionName\s*=.*?"(\d+\.\d+\.\d+)"', text)
    if m:
        result["versionName"] = m.group(1)

    return result


# ── APK digest ─────────────────────────────────────────────────────────────────

def sha256_file(path: Path) -> str:
    """Compute lowercase hex SHA-256 of a file."""
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()


def extract_cert_fingerprint(apk_path: Path) -> str | None:
    """Extract signing certificate SHA-256 fingerprint via apksigner or keytool."""
    # Try apksigner first
    apksigner = shutil.which("apksigner")
    if apksigner:
        try:
            result = subprocess.run(
                [apksigner, "verify", "--verbose", "--print-certs", str(apk_path)],
                capture_output=True, text=True, timeout=30
            )
            for line in result.stdout.splitlines():
                m = re.search(r'certificate SHA-256 digest:\s*([0-9a-fA-F]{64})', line)
                if m:
                    return m.group(1).lower()
        except Exception:
            pass

    # Try keytool
    keytool = shutil.which("keytool")
    if keytool:
        try:
            result = subprocess.run(
                [keytool, "-printcert", "-jarfile", str(apk_path)],
                capture_output=True, text=True, timeout=30
            )
            for line in result.stdout.splitlines():
                m = re.search(r'SHA256:\s*([0-9A-Fa-f:]{95})', line)
                if m:
                    return m.group(1).replace(":", "").lower()
        except Exception:
            pass

    return None


# ── Markdown generation ────────────────────────────────────────────────────────

def generate_appeal_md(
    gradle_info: dict[str, str],
    apk_sha256: str | None,
    cert_fingerprint: str | None,
    apk_path: str | None,
) -> str:
    application_id = gradle_info.get("applicationId", "com.dhyan.app")
    version_name = gradle_info.get("versionName", "2.0.0")
    version_code = gradle_info.get("versionCode", "2")
    generated_at = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC")

    apk_sha256_line = apk_sha256 if apk_sha256 else "_(run with --apk <signed.apk> to compute)_"
    cert_line = cert_fingerprint if cert_fingerprint else "3ec8e983132712b44585e4986378a42beba71bfd48d4d20f3452d3b860d653d4"
    apk_name = Path(apk_path).name if apk_path else "Hunch-v2.0.0-release.apk"

    return f"""# Hunch — Google Play Protect Appeal Submission Packet

**Generated:** {generated_at}  
**Package:** `{application_id}`  
**Version:** `{version_name}` (versionCode `{version_code}`)  
**Repository:** {REPO_URL}

---

## ⚠️ Step 0 — VirusTotal Upload Checklist (REQUIRED FIRST)

> Google's appeal backend fetches your APK by its SHA-256 hash from VirusTotal.
> You MUST upload the APK to VirusTotal BEFORE submitting the appeal form.

1. Go to [{VIRUSTOTAL_URL}]({VIRUSTOTAL_URL})
2. Upload `{apk_name}`
3. Wait for the analysis to complete (usually < 2 minutes)
4. Copy the VirusTotal analysis URL (e.g. `https://www.virustotal.com/gui/file/<sha256>/detection`)
5. Paste it into the **"VirusTotal link"** field in the appeals form

**APK SHA-256 (for VirusTotal verification):**
```
{apk_sha256_line}
```

---

## Step 1 — Appeals Form Field Values

**Form URL:** [{APPEALS_FORM_URL}]({APPEALS_FORM_URL})

Copy-paste each value into the corresponding form field:

| Field | Value |
|---|---|
| **Package name** | `{application_id}` |
| **App version** | `{version_name}` |
| **Version code** | `{version_code}` |
| **APK SHA-256** | `{apk_sha256_line}` |
| **Signing certificate SHA-256** | `{cert_line}` |
| **VirusTotal URL** | _(paste after uploading in Step 0)_ |

---

## Step 2 — Technical Appeal Statement

Copy the entire block below into the **"Description / Technical Details"** field:

---

**Subject: Play Protect Appeal — Hunch v{version_name} (com.dhyan.app) — Attention-Aware Notification Manager**

We are requesting Google Play Protect review for our app **Hunch** (`{application_id}`, version `{version_name}`, versionCode `{version_code}`).

Hunch is a local-first attention management system. It models user attention patterns entirely on-device using a pure-Kotlin/Python survival ML model. No data is ever transmitted to external servers.

### Why Play Protect may flag Hunch

Hunch uses three system-level Android APIs that are routinely used by productivity and parental control apps:
1. **Notification Listener Service** — to hold low-priority notifications during focus sessions and release them in batches.
2. **Accessibility Service** — to detect which app is in the foreground (package name only; `typeWindowStateChanged` events only).
3. **Usage Stats** — to compute per-app time-on-screen metrics.

None of these APIs are used for data collection, surveillance, ad targeting, or any form of off-device transmission.

### Play Protect & MUwS Compliance Guarantees

**G1 — Zero Prohibited Permissions:**
`AndroidManifest.xml` does not declare `READ_SMS`, `RECEIVE_SMS`, `SEND_SMS`, `READ_CALL_LOG`, `WRITE_CALL_LOG`, `PROCESS_OUTGOING_CALLS`, `READ_CONTACTS`, `GET_ACCOUNTS`, `RECORD_AUDIO`, `CAMERA`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `REQUEST_INSTALL_PACKAGES`, or `QUERY_ALL_PACKAGES`.

**G2 — Non-Deceptive Accessibility:**
`accessibility_service_config.xml` explicitly sets `android:isAccessibilityTool="false"` and `android:canRetrieveWindowContent="false"`. Only `typeWindowStateChanged` events are captured (foreground app package transitions). Screen text, keystrokes, passwords, and UI content are never read.

**G3 — System Alert Non-Suppression (MUwS):**
`NotificationTriageEngine.SYSTEM_SECURITY_EXEMPT_PACKAGES` ensures that notifications from `android`, `com.android.systemui`, `com.android.vending` (Play Protect), `com.google.android.gms`, `com.google.android.dialer`, `com.android.server.telecom`, `com.android.incallui`, and `com.android.settings` are always immediately passed through — never held, deferred, or canceled — even during active Focus Shield sessions.

**G4 — Prominent Disclosure Gate:**
`ProminentDisclosureGate.kt` enforces that no Android system settings intent (`ACTION_USAGE_ACCESS_SETTINGS`, `ACTION_NOTIFICATION_LISTENER_SETTINGS`, `ACTION_ACCESSIBILITY_SETTINGS`, `ACTION_MANAGE_OVERLAY_PERMISSION`) can ever be launched without prior explicit user acceptance of a plain-English prominent disclosure card. Consent is tracked per permission tier in SharedPreferences.

**G5 — MUwS Safe Language:**
No string, comment, or onboarding instruction in this app ever asks the user to disable, turn off, or bypass Google Play Protect.

**G6 — Automated Compliance Verification:**
`PlayProtectComplianceTest.kt` (JVM unit test suite) programmatically parses `AndroidManifest.xml`, `accessibility_service_config.xml`, `backup_rules.xml`, and `data_extraction_rules.xml` on every build to prevent regressions.

### Data & Privacy Summary

| Data Type | Collected? | Stored Where? | Transmitted? |
|---|---|---|---|
| Notification text/title | ❌ Never | — | — |
| Screen content / UI text | ❌ Never | — | — |
| Location | ❌ Never | — | — |
| Contacts / SMS / Calls | ❌ Never | — | — |
| App usage timestamps | ✅ Yes | Local SQLite (AES-256-GCM encrypted) | ❌ Never |
| Foreground app package | ✅ Yes (hashed) | Local SQLite | ❌ Never |
| Notification metadata (category only) | ✅ Yes | Local SQLite | ❌ Never |

### Signing Certificate
SHA-256: `{cert_line}`

### Source Code
Full open-source repository: {REPO_URL}
CI build workflow: {REPO_URL}/blob/main/.github/workflows/release.yml

We welcome any additional technical review. Thank you for your time.

---

## Step 3 — Post-Submission Checklist

- [ ] VirusTotal analysis URL copied into the form
- [ ] APK SHA-256 matches the uploaded file
- [ ] All 6 `PlayProtectComplianceTest` tests pass in CI
- [ ] `docs/HUNCH_PLAY_PROTECT_COMPLIANCE_SPEC.md` committed to main branch
- [ ] `tools/generate_play_protect_appeal.py` CI step included in release.yml

---

*Generated by `tools/generate_play_protect_appeal.py` — Hunch v{version_name}*
"""


# ── Main ───────────────────────────────────────────────────────────────────────

def main() -> None:
    parser = argparse.ArgumentParser(description="Generate Hunch Play Protect appeal packet")
    parser.add_argument("--apk", metavar="PATH", help="Path to the signed release APK")
    parser.add_argument("--out", metavar="DIR", default="docs",
                        help="Output directory for PLAY_PROTECT_APPEAL_SUBMISSION_READY.md (default: docs)")
    args = parser.parse_args()

    # Locate project root (directory containing phone/ and engine/)
    script_dir = Path(__file__).resolve().parent
    project_root = script_dir.parent  # tools/ is one level below project root

    # Parse build.gradle.kts
    gradle_path = project_root / "phone" / "app" / "build.gradle.kts"
    if not gradle_path.exists():
        gradle_path = project_root / "phone" / "app" / "build.gradle"
    gradle_info = parse_gradle_kts(gradle_path) if gradle_path.exists() else {}
    if not gradle_info:
        # Fallback defaults
        gradle_info = {"applicationId": "com.dhyan.app", "versionCode": "2", "versionName": "2.0.0"}

    # APK digest
    apk_sha256: str | None = None
    cert_fingerprint: str | None = None
    apk_path_str: str | None = args.apk

    if args.apk:
        apk_path = Path(args.apk)
        if apk_path.exists():
            print(f"Computing SHA-256 for {apk_path}...")
            apk_sha256 = sha256_file(apk_path)
            print(f"APK SHA-256: {apk_sha256}")
            print("Extracting signing certificate fingerprint...")
            cert_fingerprint = extract_cert_fingerprint(apk_path)
            if cert_fingerprint:
                print(f"Cert SHA-256: {cert_fingerprint}")
        else:
            print(f"WARNING: APK file not found: {args.apk}", file=sys.stderr)

    # Generate markdown
    md = generate_appeal_md(gradle_info, apk_sha256, cert_fingerprint, apk_path_str)

    # Write output
    out_dir = project_root / args.out
    out_dir.mkdir(parents=True, exist_ok=True)
    out_path = out_dir / "PLAY_PROTECT_APPEAL_SUBMISSION_READY.md"
    out_path.write_text(md, encoding="utf-8")
    print(f"\n✅ Appeal packet written to: {out_path}")

    # Print summary for CI $GITHUB_STEP_SUMMARY
    print("\n" + "=" * 60)
    print("PLAY PROTECT APPEAL PACKET SUMMARY")
    print("=" * 60)
    print(f"Package:     {gradle_info.get('applicationId', 'com.dhyan.app')}")
    print(f"Version:     {gradle_info.get('versionName', '2.0.0')} (code {gradle_info.get('versionCode', '2')})")
    print(f"APK SHA-256: {apk_sha256 or '(no APK provided)'}")
    print(f"Cert SHA-256:{cert_fingerprint or '(no APK provided)'}")
    print(f"Output:      {out_path}")
    print("=" * 60)


if __name__ == "__main__":
    main()
