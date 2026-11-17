"""Resolve and verify the actual Hunch release APK before an appeal."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
from urllib.request import Request, urlopen

CERT = "3ec8e983132712b44585e4986378a42beba71bfd48d4d20f3452d3b860d653d4"
REPO = "abhigyan24-ops/hunch-attention-twin"
FORM = "https://support.google.com/googleplay/android-developer/contact/protectappeals"


def _tool(name):
    filename = name + (".bat" if os.name == "nt" and name == "apksigner" else ".exe" if os.name == "nt" else "")
    for root in (os.getenv("ANDROID_HOME"), os.getenv("ANDROID_SDK_ROOT"),
                 Path(os.getenv("LOCALAPPDATA", "")) / "Android/Sdk"):
        if root:
            for candidate in sorted(Path(root).glob(f"build-tools/*/{filename}"), reverse=True):
                if candidate.is_file():
                    return str(candidate)
    return shutil.which(filename) or name


def compute_apk_metadata(apk_path: Path) -> dict:
    apk_path = apk_path.resolve(strict=True)
    hash_value = hashlib.sha256()
    size = 0
    with apk_path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            hash_value.update(chunk)
            size += len(chunk)
    digest = hash_value.hexdigest()
    return dict(apkPath=str(apk_path), apkSizeBytes=size, apkSha256=digest,
                virusTotalUrl=f"https://www.virustotal.com/gui/file/{digest}/detection",
                appealFormUrl=FORM)


def verify_release(path: Path) -> dict:
    path = path.resolve(strict=True)
    before = compute_apk_metadata(path)
    badging = subprocess.check_output([_tool("aapt"), "dump", "badging", str(path)], text=True, encoding="utf-8")
    package = re.search(r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging, re.M)
    if not package or package.groups() != ("com.dhyan.app", "2", "2.0.0"):
        raise ValueError("APK is not com.dhyan.app version 2.0.0 (code 2)")
    report = subprocess.check_output([_tool("apksigner"), "verify", "--verbose", "--print-certs", str(path)], text=True, encoding="utf-8")
    signers = set(re.findall(r"certificate SHA-256 digest: ([0-9a-fA-F]{64})", report))
    if {value.lower() for value in signers} != {CERT}:
        raise ValueError("APK does not match the pinned release signer")
    if before != compute_apk_metadata(path):
        raise ValueError("APK changed while being verified")
    return dict(before, packageName=package[1], versionCode=int(package[2]), versionName=package[3],
                signingCertSha256=CERT)


def find_or_build_apk(repo_root: Path, explicit_apk: str | None = None) -> Path:
    if explicit_apk is not None:
        path = Path(explicit_apk)
        verify_release(path)
        return path
    output = repo_root / "phone/app/build/outputs/apk"
    candidates = sorted(output.glob("*/*.apk"), key=lambda p: p.stat().st_mtime, reverse=True)
    for path in candidates:
        try:
            verify_release(path)
            return path
        except (ValueError, OSError, subprocess.CalledProcessError):
            continue
    destination = repo_root / "build/appeal_artifacts/Hunch-v2.0.0-release.apk"
    try:
        with urlopen(Request(f"https://api.github.com/repos/{REPO}/releases/tags/v2.0.0",
                             headers={"User-Agent": "Hunch-appeal-generator"}), timeout=15) as response:
            release = json.load(response)
        for asset in release.get("assets", []):
            url = asset["browser_download_url"]
            if not url.startswith(f"https://github.com/{REPO}/releases/download/v2.0.0/") or not url.endswith(".apk"):
                continue
            destination.parent.mkdir(parents=True, exist_ok=True)
            temporary = destination.with_suffix(".apk.part")
            try:
                with urlopen(url, timeout=30) as response, temporary.open("wb") as stream:
                    shutil.copyfileobj(response, stream)
                verify_release(temporary)
                temporary.replace(destination)
                return destination
            except (OSError, ValueError, subprocess.CalledProcessError):
                continue
            finally:
                temporary.unlink(missing_ok=True)
    except (OSError, ValueError):
        pass
    if all(os.getenv(key) for key in ("ANDROID_KEYSTORE_PATH", "ANDROID_KEYSTORE_PASSWORD",
                                      "ANDROID_KEY_ALIAS", "ANDROID_KEY_PASSWORD")):
        wrapper = repo_root / "phone" / ("gradlew.bat" if os.name == "nt" else "gradlew")
        subprocess.run([str(wrapper), ":app:assembleRelease"], cwd=wrapper.parent, check=True)
        path = output / "release/app-release.apk"
        verify_release(path)
        return path
    raise FileNotFoundError("No verified v2.0.0 release APK. Provide --apk or configure the Android SDK and release signing environment.")
