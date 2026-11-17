#!/usr/bin/env python3
"""
automate_play_protect_appeal.py — End-to-End VirusTotal v3 API + Playwright Uploader
& Google Play Protect Appeals Form Auto-Filler for Hunch v2.0.0.

Zero dependency on `gh` CLI. Supports both authenticated REST API v3 uploads and
keyless Playwright browser automation fallback.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time

try:
    import requests
except ImportError:
    requests = None

try:
    from playwright.sync_api import TimeoutError as PlaywrightTimeoutError, sync_playwright, TimeoutError as PlaywrightTimeoutError
except ImportError:
    sync_playwright = None
    PlaywrightTimeoutError = Exception


# ── Constants ──────────────────────────────────────────────────────────────────

DEFAULT_PACKAGE_NAME = "com.dhyan.app"
DEFAULT_VERSION_NAME = "2.0.0"
DEFAULT_VERSION_CODE = 2
DEFAULT_SIGNING_CERT = "3ec8e983132712b44585e4986378a42beba71bfd48d4d20f3452d3b860d653d4"
APPEAL_FORM_URL = "https://support.google.com/googleplay/android-developer/contact/protectappeals"
VT_API_BASE = "https://www.virustotal.com/api/v3"
VT_UPLOAD_THRESHOLD_BYTES = 32 * 1024 * 1024  # 32 MB
DEFAULT_DEVELOPER_NAME = "Abhigyan (Hunch Open Source Project)"
DEFAULT_SUBJECT = "Request for Play Protect review of Hunch v2.0.0"

TECHNICAL_STATEMENT = """Please review Hunch, an open-source focus companion, for a possible incorrect classification.
G1: Review manifest permissions against the restricted permission list.
G2: Accessibility configuration declares window-state events and no window-content access. Review runtime behavior in A11yCollector as well.
G3: System and security notifications should bypass filtering. The source is available for review.
G4: Optional sensitive permissions require a user disclosure and an explicit Android Settings grant.
G5: Hunch supports leaving Play Protect enabled.
G6: The repository includes compliance and update-check unit tests. The packet verifies the APK identity and signatures separately.
A VirusTotal link identifies the exact file by hash and does not indicate that an analysis exists."""


# ── Environment & Key Resolution ───────────────────────────────────────────────

def get_vt_api_key(cli_key: str | None = None, repo_root: Path | None = None) -> str | None:
    """Resolve VT_API_KEY from CLI arg, os.environ, or root .env file."""
    if cli_key and cli_key.strip():
        return cli_key.strip()
    env_val = os.environ.get("VT_API_KEY")
    if env_val and env_val.strip():
        return env_val.strip()
    if repo_root:
        env_file = repo_root / ".env"
        if env_file.is_file():
            try:
                for line in env_file.read_text(encoding="utf-8").splitlines():
                    line = line.strip()
                    if line.startswith("VT_API_KEY="):
                        val = line.split("=", 1)[1].strip().strip('"').strip("'")
                        if val:
                            return val
            except Exception:
                pass
    return None


# ── Stage 1: Load or Generate Appeal Payload ───────────────────────────────────

def compute_file_sha256(path: Path) -> str:
    """Compute 64-char lowercase SHA-256 hex digest of a file."""
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest().lower()


def verify_locked_apk(repo_root: Path, explicit_apk: str | None = None) -> dict:
    payload = json.loads((repo_root / "docs/play_protect_appeal_payload.json").read_text(encoding="utf-8"))
    path = Path(explicit_apk) if explicit_apk else repo_root / "phone/app/build/outputs/apk/release/app-release.apk"
    digest = compute_file_sha256(path)
    if digest != payload.get("apkSha256"):
        raise ValueError("APK SHA-256 does not match the locked appeal payload")
    if payload.get("signingCertSha256") != DEFAULT_SIGNING_CERT:
        raise ValueError("Appeal payload does not identify the pinned release signer")
    return dict(payload, apkPath=str(path.resolve()), apkSizeBytes=path.stat().st_size,
                appealFormUrl=APPEAL_FORM_URL,
                virusTotalUrl=f"https://www.virustotal.com/gui/file/{digest}/detection")


def upload_apk_to_virustotal(
    payload: dict,
    vt_api_key: str | None = None,
    skip_browser: bool = False,
    headless: bool = False,
    repo_root: Path | None = None,
    session: requests.Session | None = None,
) -> str:
    """
    Upload APK to VirusTotal via REST API v3 or keyless Playwright fallback.
    Returns the VirusTotal file detection URL.
    """
    key = get_vt_api_key(vt_api_key, repo_root)
    sha256 = payload.get("apkSha256", "").lower()
    vt_url = payload.get("virusTotalUrl", f"https://www.virustotal.com/gui/file/{sha256}/detection")
    apk_path_str = payload.get("apkPath", "")
    apk_path = Path(apk_path_str) if apk_path_str else None

    # ── Mode A: REST API v3 ────────────────────────────────────────────────────
    if key:
        http = session or (requests.Session() if requests else None)
        if http is None:
            print("Warning: requests library not available; cannot use REST API.")
            return vt_url

        headers = {"x-apikey": key, "Accept": "application/json"}

        # 1. Check if already indexed
        check_url = f"{VT_API_BASE}/files/{sha256}"
        try:
            resp = http.get(check_url, headers=headers, timeout=15)
            if resp.status_code == 200:
                print(f"[VirusTotal] APK {sha256} is already indexed on VirusTotal.")
                return vt_url
        except Exception as exc:
            print(f"[VirusTotal] Index check failed: {exc}")

        # 2. Upload file
        if not apk_path or not apk_path.is_file():
            print(f"[VirusTotal] APK file not found on disk ({apk_path}); skipping upload.")
            return vt_url

        file_size = apk_path.stat().st_size
        analysis_id = None

        try:
            if file_size < VT_UPLOAD_THRESHOLD_BYTES:
                upload_target = f"{VT_API_BASE}/files"
            else:
                url_resp = http.get(f"{VT_API_BASE}/files/upload_url", headers=headers, timeout=15)
                url_resp.raise_for_status()
                upload_target = url_resp.json().get("data")

            with open(apk_path, "rb") as f:
                files = {"file": (apk_path.name, f, "application/vnd.android.package-archive")}
                post_resp = http.post(upload_target, headers={"x-apikey": key}, files=files, timeout=120)
                post_resp.raise_for_status()
                analysis_data = post_resp.json().get("data", {})
                analysis_id = analysis_data.get("id")
                print(f"[VirusTotal] Upload successful. Analysis ID: {analysis_id}")
        except Exception as exc:
            print(f"[VirusTotal] Upload request failed: {exc}")

        # 3. Poll analysis
        if analysis_id:
            poll_url = f"{VT_API_BASE}/analyses/{analysis_id}"
            for _ in range(6):  # Poll up to ~30s
                time.sleep(5)
                try:
                    poll_resp = http.get(poll_url, headers=headers, timeout=10)
                    if poll_resp.status_code == 200:
                        status = poll_resp.json().get("data", {}).get("attributes", {}).get("status")
                        print(f"[VirusTotal] Analysis status: {status}")
                        if status == "completed":
                            break
                except Exception:
                    break

        return vt_url

    # ── Mode B: Keyless Playwright Upload Fallback ──────────────────────────────
    if skip_browser:
        print(f"[VirusTotal] No API key and browser skipped. File URL: {vt_url}")
        return vt_url

    if not apk_path or not apk_path.is_file():
        print(f"[VirusTotal] No APK file found for browser upload. Using URL: {vt_url}")
        return vt_url

    if sync_playwright is None:
        print(f"[VirusTotal] Playwright not installed. Using URL: {vt_url}")
        return vt_url

    try:
        print("[VirusTotal] Initiating keyless browser upload via Playwright...")
        with sync_playwright() as p:
            browser = p.chromium.launch(headless=headless)
            page = browser.new_page()
            page.goto("https://www.virustotal.com/gui/home/upload", timeout=30000)
            page.locator('input[type="file"]').first.set_input_files(str(apk_path))
            try:
                confirmation = page.locator("#confirmUpload").or_(
                    page.get_by_role("button", name=re.compile(r"^Confirm upload$", re.I))
                ).first
                try:
                    confirmation.click(timeout=15000)
                except PlaywrightTimeoutError:
                    pass
                page.wait_for_url(f"**/gui/file/{sha256}**", timeout=120000)
                artifacts = (repo_root or Path.cwd()) / "build/appeal_artifacts"
                artifacts.mkdir(parents=True, exist_ok=True)
                page.screenshot(path=str(artifacts / "virustotal_upload_confirmed.png"))
                print(f"[VirusTotal] Browser upload navigation complete: {page.url}")
            except Exception:
                print("[VirusTotal] Browser upload could not be verified.")
            browser.close()
    except Exception as exc:
        print(f"[VirusTotal] Browser upload error (proceeding with URL): {exc}")

    return vt_url


# ── Stage 3: Form Field Classification & Playwright Auto-Filler ───────────────

def classify_form_input(
    name: str = "",
    label: str = "",
    placeholder: str = "",
    aria_label: str = "",
    tag: str = "input",
) -> str | None:
    """
    Pure classifier mapping HTML field attributes to appeal payload keys.
    Returns: 'apkSha256' | 'virusTotalUrl' | 'packageName' | 'technicalStatement' | 'developerName' | None
    """
    combined = f"{name} {label} {placeholder} {aria_label}".lower()

    if any(k in combined for k in ["sha-256", "sha256", "apk hash", "hash", "checksum", "digest"]):
        return "apkSha256"

    if any(k in combined for k in ["virustotal", "virus total", "scan url", "scan link"]):
        return "virusTotalUrl"

    if any(k in combined for k in ["package name", "package", "application id", "packagename", "app id", "com."]):
        return "packageName"

    if tag == "textarea" or any(k in combined for k in ["technical details", "statement", "description", "details", "explanation", "reasons", "appeal", "why"]):
        return "technicalStatement"

    if any(k in combined for k in ["developer name", "your name", "contact name", "developer", "name"]):
        return "developerName"

    return None


def copy_to_clipboard(text: str) -> bool:
    """Safely copy technical statement to system clipboard across OS platforms."""
    try:
        if os.name == "nt":
            subprocess.run(["clip.exe"], input=text, text=True, check=True)
            return True
        elif sys.platform == "darwin":
            subprocess.run(["pbcopy"], input=text, text=True, check=True)
            return True
        elif shutil.which("xclip"):
            subprocess.run(["xclip", "-selection", "clipboard"], input=text, text=True, check=True)
            return True
    except Exception:
        pass
    return False


def autofill_google_appeal_form(
    payload: dict,
    repo_root: Path,
    skip_browser: bool = False,
    headless: bool = False,
    auto_submit: bool = False,
) -> dict:
    """
    Automate filling the Google Play Protect Appeals form.
    Copies technical details to clipboard, takes screenshot, and safely handles CAPTCHAs.
    """
    statement = payload.get("technicalStatement", TECHNICAL_STATEMENT)
    copied = copy_to_clipboard(statement)
    if copied:
        print("[Appeals Form] Technical statement successfully copied to system clipboard.")

    artifacts_dir = repo_root / "build" / "appeal_artifacts"
    artifacts_dir.mkdir(parents=True, exist_ok=True)
    screenshot_path = artifacts_dir / "play_protect_form_filled.png"

    if skip_browser:
        print("[Appeals Form] --skip-browser active; browser form filling bypassed.")
        return {"status": "skipped", "screenshot": None, "copiedToClipboard": copied}

    if sync_playwright is None:
        print("[Appeals Form] Playwright not installed; cannot launch browser.")
        return {"status": "playwright_missing", "screenshot": None, "copiedToClipboard": copied}

    filled_fields: dict[str, str] = {}
    form_url = payload.get("appealFormUrl", APPEAL_FORM_URL)

    try:
        print(f"[Appeals Form] Opening {form_url} via Playwright Chromium...")
        with sync_playwright() as p:
            browser = p.chromium.launch(headless=headless)
            page = browser.new_page()
            page.goto(form_url, timeout=30000)

            # Wait for form elements
            page.wait_for_timeout(2000)

            # Map field values from payload
            field_values = {
                "developerName": payload.get("developerName", DEFAULT_DEVELOPER_NAME),
                "packageName": payload.get("packageName", DEFAULT_PACKAGE_NAME),
                "apkSha256": payload.get("apkSha256", ""),
                "virusTotalUrl": payload.get("virusTotalUrl", ""),
                "technicalStatement": statement,
            }

            # Locate all candidate input/textarea elements
            elements = page.query_selector_all("input:not([type='hidden']), textarea")
            for el in elements:
                try:
                    tag_name = el.evaluate("el => el.tagName.toLowerCase()")
                    name_attr = el.get_attribute("name") or ""
                    placeholder_attr = el.get_attribute("placeholder") or ""
                    aria_attr = el.get_attribute("aria-label") or ""

                    # Check parent/sibling label text
                    label_text = el.evaluate(
                        """el => {
                            const id = el.id;
                            if (id) {
                                const l = document.querySelector(`label[for='${id}']`);
                                if (l) return l.innerText;
                            }
                            const parent = el.closest('label');
                            return parent ? parent.innerText : '';
                        }"""
                    ) or ""

                    field_type = classify_form_input(
                        name=name_attr,
                        label=label_text,
                        placeholder=placeholder_attr,
                        aria_label=aria_attr,
                        tag=tag_name,
                    )

                    if field_type and field_type in field_values and field_type not in filled_fields:
                        val = field_values[field_type]
                        el.fill(val)
                        filled_fields[field_type] = val
                        print(f"[Appeals Form] Filled {field_type}")
                except Exception:
                    continue

            # Fallback by element order if classification missed fields
            text_inputs = page.query_selector_all("input[type='text'], input:not([type])")
            if len(text_inputs) >= 4 and len(filled_fields) < 4:
                ordered_keys = ["developerName", "packageName", "apkSha256", "virusTotalUrl"]
                for i, k in enumerate(ordered_keys):
                    if k not in filled_fields and i < len(text_inputs):
                        try:
                            text_inputs[i].fill(field_values[k])
                            filled_fields[k] = field_values[k]
                            print(f"[Appeals Form] Filled {k} by fallback index {i}")
                        except Exception:
                            pass

            textareas = page.query_selector_all("textarea")
            if textareas and "technicalStatement" not in filled_fields:
                try:
                    textareas[0].fill(field_values["technicalStatement"])
                    filled_fields["technicalStatement"] = field_values["technicalStatement"]
                    print("[Appeals Form] Filled technicalStatement by fallback textarea index 0")
                except Exception:
                    pass

            # Take screenshot
            page.screenshot(path=str(screenshot_path), full_page=True)
            print(f"[Appeals Form] Verification screenshot saved to {screenshot_path}")

            # Check for CAPTCHA or Google Accounts login barrier
            page_content = page.content().lower()
            has_captcha = any(k in page_content for k in ["recaptcha", "hcaptcha", "challenge", "bot check"])
            has_auth_gate = any(k in page_content for k in ["sign in with google", "accounts.google.com"])

            if has_captcha or has_auth_gate:
                reason = "CAPTCHA" if has_captcha else "Google Authentication Gate"
                print(f"[Appeals Form] ⚠️ {reason} detected on form. Auto-submission prevented. Please submit manually in browser.")
            elif auto_submit:
                submit_btn = page.query_selector("button[type='submit'], input[type='submit']")
                if submit_btn:
                    print("[Appeals Form] Clicking submit button (--auto-submit active)...")
                    submit_btn.click()
                    page.wait_for_timeout(3000)

            browser.close()

        return {
            "status": "success",
            "screenshot": str(screenshot_path),
            "filledFields": list(filled_fields.keys()),
            "copiedToClipboard": copied,
        }
    except Exception as exc:
        print(f"[Appeals Form] Form interaction encountered error: {exc}")
        return {
            "status": "error",
            "error": str(exc),
            "screenshot": None,
            "copiedToClipboard": copied,
        }


# ── Main Entrypoint ────────────────────────────────────────────────────────────

def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", help="Explicit path to signed release APK")
    parser.add_argument("--vt-api-key", help="VirusTotal API v3 key (or via VT_API_KEY env)")
    parser.add_argument("--skip-browser", action="store_true", help="Bypass browser Playwright steps")
    parser.add_argument("--headless", action="store_true", help="Run Playwright Chromium in headless mode")
    parser.add_argument("--auto-submit", action="store_true", help="Click submit if no CAPTCHA/login detected")
    args = parser.parse_args()

    repo_root = Path(__file__).resolve().parents[1]

    print("=" * 60)
    print("HUNCH GOOGLE PLAY PROTECT APPEAL AUTOMATION")
    print("=" * 60)

    # Stage 1: Load or Generate Payload
    print("\n[Stage 1] Loading or generating appeal payload...")
    try:
        payload = verify_locked_apk(repo_root, explicit_apk=args.apk)
    except (OSError, ValueError) as exc:
        parser.exit(1, f"Verification failed: {exc}\n")
    print(f"Package:     {payload.get('packageName')}")
    print(f"Version:     {payload.get('versionName')} (code {payload.get('versionCode')})")
    print(f"APK SHA-256: {payload.get('apkSha256')}")

    # Stage 2: VirusTotal Upload
    print("\n[Stage 2] Executing VirusTotal upload...")
    vt_url = upload_apk_to_virustotal(
        payload=payload,
        vt_api_key=args.vt_api_key,
        skip_browser=args.skip_browser,
        headless=args.headless,
        repo_root=repo_root,
    )
    payload["virusTotalUrl"] = vt_url
    print(f"VirusTotal URL: {vt_url}")

    # Stage 3: Autofill Google Appeal Form
    print("\n[Stage 3] Autofilling Google Play Protect Appeals Form...")
    result = autofill_google_appeal_form(
        payload=payload,
        repo_root=repo_root,
        skip_browser=args.skip_browser,
        headless=args.headless,
        auto_submit=args.auto_submit,
    )
    print(f"Result: {result.get('status')}")
    if result.get("screenshot"):
        print(f"Screenshot: {result.get('screenshot')}")

    print("\n" + "=" * 60)
    print("APPEAL AUTOMATION PIPELINE COMPLETE")
    print("=" * 60)


if __name__ == "__main__":
    main()
