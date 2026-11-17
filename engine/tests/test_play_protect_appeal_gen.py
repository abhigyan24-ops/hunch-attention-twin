"""
test_play_protect_appeal_gen.py — Tests for tools/generate_play_protect_appeal.py
"""

from __future__ import annotations

import json
from pathlib import Path
import sys
from unittest.mock import patch

import pytest

REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT / "tools"))

import generate_play_protect_appeal as gen


class TestStatement:
    def test_statement_contains_all_guarantees(self):
        for g in ["G1:", "G2:", "G3:", "G4:", "G5:", "G6:"]:
            assert g in gen.STATEMENT, f"Missing guarantee {g} in STATEMENT"

    def test_statement_contains_no_disable_play_protect(self):
        forbidden = ["disable Play Protect", "turn off Play Protect"]
        for phrase in forbidden:
            assert phrase.lower() not in gen.STATEMENT.lower()

    def test_statement_mentions_virustotal(self):
        assert "VirusTotal" in gen.STATEMENT


class TestMainExecution:
    def test_main_generates_appeal_files(self, tmp_path, monkeypatch):
        fake_metadata = {
            "packageName": "com.dhyan.app",
            "versionCode": 2,
            "versionName": "2.0.0",
            "apkSizeBytes": 1024000,
            "apkSha256": "313d0f10ccfe9ef8233579b070a72aa77edbac7753decd99725846c4ae946c76",
            "signingCertSha256": "3ec8e983132712b44585e4986378a42beba71bfd48d4d20f3452d3b860d653d4",
            "virusTotalUrl": "https://www.virustotal.com/gui/file/313d0f10ccfe9ef8233579b070a72aa77edbac7753decd99725846c4ae946c76/detection",
            "appealFormUrl": "https://support.google.com/googleplay/android-developer/contact/protectappeals",
        }

        monkeypatch.setattr(gen, "find_or_build_apk", lambda root, explicit: Path("fake.apk"))
        monkeypatch.setattr(gen, "verify_release", lambda path: dict(fake_metadata))

        out_dir = tmp_path / "docs"
        monkeypatch.setattr("sys.argv", ["generate_play_protect_appeal.py", "--out", str(out_dir)])

        gen.main()

        payload_path = out_dir / "play_protect_appeal_payload.json"
        md_path = out_dir / "PLAY_PROTECT_APPEAL_SUBMISSION_READY.md"

        assert payload_path.is_file()
        assert md_path.is_file()

        payload = json.loads(payload_path.read_text(encoding="utf-8"))
        assert payload["packageName"] == "com.dhyan.app"
        assert payload["apkSha256"] == fake_metadata["apkSha256"]
        assert gen.STATEMENT in payload["technicalStatement"]

        md_text = md_path.read_text(encoding="utf-8")
        assert fake_metadata["apkSha256"] in md_text
        assert "VirusTotal" in md_text
