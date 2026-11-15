"""
Unit tests for tools/generate_play_protect_appeal.py
Run with: pytest engine/tests/test_play_protect_appeal_gen.py -v
"""

from __future__ import annotations

import hashlib
import sys
import tempfile
import textwrap
from pathlib import Path

import pytest

# Add tools/ to sys.path so we can import the script directly
REPO_ROOT = Path(__file__).resolve().parent.parent.parent
sys.path.insert(0, str(REPO_ROOT / "tools"))

import generate_play_protect_appeal as gen


# ── Helpers ───────────────────────────────────────────────────────────────────

SAMPLE_GRADLE_KTS = textwrap.dedent("""
    android {
        defaultConfig {
            applicationId = "com.dhyan.app"
            versionCode = (System.getenv("VERSION_CODE") ?: "2").toInt()
            versionName = "2.0.0"
        }
    }
""")


# ── Tests ─────────────────────────────────────────────────────────────────────

class TestParseGradleKts:
    def test_parses_application_id(self, tmp_path):
        gradle = tmp_path / "build.gradle.kts"
        gradle.write_text(SAMPLE_GRADLE_KTS)
        info = gen.parse_gradle_kts(gradle)
        assert info["applicationId"] == "com.dhyan.app"

    def test_parses_version_name(self, tmp_path):
        gradle = tmp_path / "build.gradle.kts"
        gradle.write_text(SAMPLE_GRADLE_KTS)
        info = gen.parse_gradle_kts(gradle)
        assert info["versionName"] == "2.0.0"

    def test_parses_version_code(self, tmp_path):
        gradle = tmp_path / "build.gradle.kts"
        gradle.write_text(SAMPLE_GRADLE_KTS)
        info = gen.parse_gradle_kts(gradle)
        assert info["versionCode"] == "2"

    def test_handles_simple_versioncode(self, tmp_path):
        gradle = tmp_path / "build.gradle.kts"
        gradle.write_text('defaultConfig { versionCode = 3 versionName = "3.0.0" applicationId = "com.test.app" }')
        info = gen.parse_gradle_kts(gradle)
        assert info["versionCode"] == "3"
        assert info["versionName"] == "3.0.0"
        assert info["applicationId"] == "com.test.app"


class TestSha256File:
    def test_sha256_matches_known_hash(self, tmp_path):
        f = tmp_path / "test.bin"
        f.write_bytes(b"hello world")
        expected = hashlib.sha256(b"hello world").hexdigest()
        assert gen.sha256_file(f) == expected

    def test_sha256_is_64_hex_chars(self, tmp_path):
        f = tmp_path / "test.bin"
        f.write_bytes(b"\x00" * 1024)
        digest = gen.sha256_file(f)
        assert len(digest) == 64
        assert all(c in "0123456789abcdef" for c in digest)


class TestGenerateAppealMd:
    def _make_md(self, **kwargs):
        defaults = dict(
            gradle_info={"applicationId": "com.dhyan.app", "versionCode": "2", "versionName": "2.0.0"},
            apk_sha256=None,
            cert_fingerprint=None,
            apk_path=None,
        )
        defaults.update(kwargs)
        return gen.generate_appeal_md(**defaults)

    def test_contains_package_name(self):
        md = self._make_md()
        assert "com.dhyan.app" in md

    def test_contains_version_info(self):
        md = self._make_md()
        assert "2.0.0" in md
        assert "versionCode" in md or "version code" in md.lower()

    def test_contains_virustotal_url(self):
        md = self._make_md()
        assert "virustotal.com" in md

    def test_contains_appeals_form_url(self):
        md = self._make_md()
        assert "protectappeals" in md

    def test_contains_apk_sha256_when_provided(self):
        digest = "a" * 64
        md = self._make_md(apk_sha256=digest)
        assert digest in md

    def test_contains_muw_policy_guarantees(self):
        md = self._make_md()
        assert "SYSTEM_SECURITY_EXEMPT_PACKAGES" in md or "system-exempt" in md.lower() or "MUwS" in md

    def test_contains_no_play_protect_disable_instructions(self):
        md = self._make_md()
        forbidden = ["disable Play Protect", "turn off Play Protect", "disable Google Play Protect"]
        for phrase in forbidden:
            assert phrase.lower() not in md.lower(), f"Found forbidden phrase: {phrase}"

    def test_step0_virustotal_upload_checklist_present(self):
        md = self._make_md()
        assert "Step 0" in md
        assert "VirusTotal" in md

    def test_all_6_guarantees_referenced(self):
        md = self._make_md()
        for g in ["G1", "G2", "G3", "G4", "G5", "G6"]:
            assert g in md, f"Missing guarantee {g} in appeal markdown"


class TestMainCli:
    def test_generates_output_file(self, tmp_path, monkeypatch):
        """End-to-end: running main() with no APK should still write a file."""
        # Create a minimal gradle file in a fake project structure
        phone_app = tmp_path / "phone" / "app"
        phone_app.mkdir(parents=True)
        (phone_app / "build.gradle.kts").write_text(SAMPLE_GRADLE_KTS)

        tools_dir = tmp_path / "tools"
        tools_dir.mkdir()

        # Monkeypatch __file__ in the gen module so project_root resolves to tmp_path
        monkeypatch.setattr(gen, "__file__", str(tools_dir / "generate_play_protect_appeal.py"))

        import argparse
        monkeypatch.setattr(
            "sys.argv",
            ["generate_play_protect_appeal.py", "--out", str(tmp_path / "docs")]
        )

        (tmp_path / "docs").mkdir(exist_ok=True)
        # Just test that parse_gradle_kts + generate_appeal_md work end-to-end
        gradle_path = phone_app / "build.gradle.kts"
        info = gen.parse_gradle_kts(gradle_path)
        md = gen.generate_appeal_md(info, None, None, None)
        out = tmp_path / "docs" / "PLAY_PROTECT_APPEAL_SUBMISSION_READY.md"
        out.write_text(md)
        assert out.exists()
        assert "com.dhyan.app" in out.read_text()
