"""
test_play_protect_automation.py — Unit test suite for Play Protect Appeal Automation.

Verifies:
1. upload_apk_to_virustotal with mocked HTTP responses:
   - 200: already indexed (no upload POST made)
   - 404 & < 32 MB: direct POST /api/v3/files
   - 404 & >= 32 MB: GET /api/v3/files/upload_url, then POST to returned upload URL
2. Form field classifier / matcher logic against Google Play Protect Appeals form descriptors.
3. compare_semver compatibility and upgrade rules:
   - "1.0.0" -> "v2.0.0" is True
   - "2.0.0" -> "v2.0.0" is False
   - "2.0.0-beta" -> "v2.0.0" is True
"""

from __future__ import annotations

import json
from pathlib import Path
import sys
from unittest.mock import MagicMock, patch

import pytest
import requests

# Add tools/ and engine/ to sys.path
REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT / "tools"))
sys.path.insert(0, str(REPO_ROOT / "engine"))

import automate_play_protect_appeal as auto_appeal
from twin.api.app import compare_semver


# ── 1. compare_semver Tests ────────────────────────────────────────────────────

class TestCompareSemver:
    def test_older_version_needs_update(self):
        assert compare_semver("1.0.0", "v2.0.0") is True
        assert compare_semver("v1.0.0", "2.0.0") is True
        assert compare_semver("1.9.9", "2.0.0") is True

    def test_same_version_no_update(self):
        assert compare_semver("2.0.0", "v2.0.0") is False
        assert compare_semver("v2.0.0", "2.0.0") is False
        assert compare_semver("2.0.0", "2.0.0") is False

    def test_newer_version_no_update(self):
        assert compare_semver("2.1.0", "2.0.0") is False
        assert compare_semver("3.0.0", "v2.0.0") is False

    def test_prerelease_upgrade_to_final(self):
        assert compare_semver("2.0.0-beta", "v2.0.0") is True
        assert compare_semver("2.0.0-rc1", "2.0.0") is True


# ── 2. Form Field Classifier Tests ─────────────────────────────────────────────

class TestFormFieldClassifier:
    def test_classifies_apk_sha256(self):
        assert auto_appeal.classify_form_input(name="apk_sha256") == "apkSha256"
        assert auto_appeal.classify_form_input(label="Enter SHA-256 Checksum") == "apkSha256"
        assert auto_appeal.classify_form_input(placeholder="64-character APK hash") == "apkSha256"
        assert auto_appeal.classify_form_input(aria_label="APK SHA256 digest") == "apkSha256"

    def test_classifies_virustotal_url(self):
        assert auto_appeal.classify_form_input(name="virustotal_link") == "virusTotalUrl"
        assert auto_appeal.classify_form_input(label="VirusTotal URL or scan link") == "virusTotalUrl"
        assert auto_appeal.classify_form_input(placeholder="https://www.virustotal.com/gui/file/...") == "virusTotalUrl"

    def test_classifies_package_name(self):
        assert auto_appeal.classify_form_input(name="package_name") == "packageName"
        assert auto_appeal.classify_form_input(label="Android Application ID / Package Name") == "packageName"
        assert auto_appeal.classify_form_input(placeholder="com.example.app") == "packageName"

    def test_classifies_technical_statement(self):
        assert auto_appeal.classify_form_input(tag="textarea") == "technicalStatement"
        assert auto_appeal.classify_form_input(name="appeal_details") == "technicalStatement"
        assert auto_appeal.classify_form_input(label="Technical details or explanation of why this app is safe") == "technicalStatement"

    def test_classifies_developer_name(self):
        assert auto_appeal.classify_form_input(name="developer_name") == "developerName"
        assert auto_appeal.classify_form_input(label="Your name / Developer Name") == "developerName"

    def test_unknown_field_returns_none(self):
        assert auto_appeal.classify_form_input(name="random_unrelated_field") is None


# ── 3. upload_apk_to_virustotal Mocked Tests ───────────────────────────────────

class TestUploadApkToVirusTotal:
    @pytest.fixture
    def mock_apk_small(self, tmp_path):
        f = tmp_path / "test-small.apk"
        f.write_bytes(b"PK\x03\x04" + b"x" * 1024)
        return f

    @pytest.fixture
    def mock_apk_large(self, tmp_path):
        f = tmp_path / "test-large.apk"
        # Create a sparse file or small dummy that we can mock stat for
        f.write_bytes(b"PK\x03\x04" + b"x" * 1024)
        return f

    def test_already_indexed_returns_200_no_upload(self, mock_apk_small):
        session = MagicMock(spec=requests.Session)
        get_resp = MagicMock()
        get_resp.status_code = 200
        session.get.return_value = get_resp

        payload = {
            "apkSha256": "a" * 64,
            "apkPath": str(mock_apk_small),
            "virusTotalUrl": f"https://www.virustotal.com/gui/file/{'a' * 64}/detection",
        }

        vt_url = auto_appeal.upload_apk_to_virustotal(
            payload=payload,
            vt_api_key="test_api_key_123",
            session=session,
        )

        assert vt_url == f"https://www.virustotal.com/gui/file/{'a' * 64}/detection"
        # GET was called to check files/sha256
        session.get.assert_called_once()
        assert f"/files/{'a' * 64}" in session.get.call_args[0][0]
        # No POST upload was performed
        session.post.assert_not_called()

    def test_not_indexed_small_file_posts_directly_to_files(self, mock_apk_small):
        session = MagicMock(spec=requests.Session)

        # 1. GET /files/{sha256} -> 404
        check_resp = MagicMock()
        check_resp.status_code = 404

        # 2. POST /files -> 200 with analysis id
        post_resp = MagicMock()
        post_resp.status_code = 200
        post_resp.json.return_value = {"data": {"id": "analysis-id-xyz-123", "type": "analysis"}}

        # 3. GET /analyses/{id} -> 200 with status completed
        poll_resp = MagicMock()
        poll_resp.status_code = 200
        poll_resp.json.return_value = {"data": {"attributes": {"status": "completed"}}}

        session.get.side_effect = [check_resp, poll_resp]
        session.post.return_value = post_resp

        payload = {
            "apkSha256": "b" * 64,
            "apkPath": str(mock_apk_small),
            "virusTotalUrl": f"https://www.virustotal.com/gui/file/{'b' * 64}/detection",
        }

        with patch("time.sleep", return_value=None):
            vt_url = auto_appeal.upload_apk_to_virustotal(
                payload=payload,
                vt_api_key="test_api_key_123",
                session=session,
            )

        assert vt_url == f"https://www.virustotal.com/gui/file/{'b' * 64}/detection"
        # Assert POST was called to direct /files endpoint
        session.post.assert_called_once()
        assert session.post.call_args[0][0] == "https://www.virustotal.com/api/v3/files"

    def test_not_indexed_large_file_fetches_upload_url_first(self, mock_apk_large):
        session = MagicMock(spec=requests.Session)

        # 1. GET /files/{sha256} -> 404
        check_resp = MagicMock()
        check_resp.status_code = 404

        # 2. GET /files/upload_url -> 200 with custom upload target
        upload_url_resp = MagicMock()
        upload_url_resp.status_code = 200
        upload_url_resp.json.return_value = {"data": "https://custom-upload.virustotal.com/upload-target-456"}

        # 3. POST https://custom-upload... -> 200
        post_resp = MagicMock()
        post_resp.status_code = 200
        post_resp.json.return_value = {"data": {"id": "analysis-large-789", "type": "analysis"}}

        # 4. GET /analyses/{id} -> 200 completed
        poll_resp = MagicMock()
        poll_resp.status_code = 200
        poll_resp.json.return_value = {"data": {"attributes": {"status": "completed"}}}

        session.get.side_effect = [check_resp, upload_url_resp, poll_resp]
        session.post.return_value = post_resp

        payload = {
            "apkSha256": "c" * 64,
            "apkPath": str(mock_apk_large),
            "virusTotalUrl": f"https://www.virustotal.com/gui/file/{'c' * 64}/detection",
        }

        # Mock stat size to simulate >= 32 MB
        with patch.object(Path, "stat") as mock_stat, patch("time.sleep", return_value=None):
            stat_obj = MagicMock()
            stat_obj.st_size = 40 * 1024 * 1024  # 40 MB
            mock_stat.return_value = stat_obj

            vt_url = auto_appeal.upload_apk_to_virustotal(
                payload=payload,
                vt_api_key="test_api_key_123",
                session=session,
            )

        assert vt_url == f"https://www.virustotal.com/gui/file/{'c' * 64}/detection"
        # Assert POST was directed to custom upload target returned by /files/upload_url
        session.post.assert_called_once()
        assert session.post.call_args[0][0] == "https://custom-upload.virustotal.com/upload-target-456"

    def test_keyless_and_skip_browser_returns_url_cleanly(self):
        payload = {
            "apkSha256": "d" * 64,
            "apkPath": "dummy.apk",
            "virusTotalUrl": f"https://www.virustotal.com/gui/file/{'d' * 64}/detection",
        }
        vt_url = auto_appeal.upload_apk_to_virustotal(
            payload=payload,
            vt_api_key=None,
            skip_browser=True,
        )
        assert vt_url == f"https://www.virustotal.com/gui/file/{'d' * 64}/detection"


# ── 4. Payload & Key Resolution Tests ──────────────────────────────────────────

class TestPayloadResolution:
    def test_resolve_key_from_env(self, monkeypatch):
        monkeypatch.setenv("VT_API_KEY", "env_key_secret_abc")
        assert auto_appeal.get_vt_api_key() == "env_key_secret_abc"

    def test_resolve_key_from_cli_overrides_env(self, monkeypatch):
        monkeypatch.setenv("VT_API_KEY", "env_key_secret_abc")
        assert auto_appeal.get_vt_api_key(cli_key="cli_key_explicit") == "cli_key_explicit"

    def test_verified_payload_rejects_changed_apk(self, tmp_path):
        import json
        apk = tmp_path / "candidate.apk"
        apk.write_bytes(b"test fixture")
        docs = tmp_path / "docs"
        docs.mkdir()
        digest = auto_appeal.compute_file_sha256(apk)
        (docs / "play_protect_appeal_payload.json").write_text(json.dumps({
            "apkSha256": digest, "signingCertSha256": auto_appeal.DEFAULT_SIGNING_CERT,
        }))
        assert auto_appeal.verify_locked_apk(tmp_path, str(apk))["apkSha256"] == digest
        apk.write_bytes(b"changed")
        with pytest.raises(ValueError, match="locked"):
            auto_appeal.verify_locked_apk(tmp_path, str(apk))


def test_generated_statement_includes_locked_artifact_details():
    import json
    from pathlib import Path

    root = Path(__file__).resolve().parents[2]
    payload = json.loads((root / "docs/play_protect_appeal_payload.json").read_text(encoding="utf-8"))
    statement = payload["technicalStatement"]
    assert statement.startswith(f"Package Name: {payload['packageName']}\n")
    assert f"APK SHA-256: {payload['apkSha256']}" in statement
    assert f"Signing Certificate SHA-256: {payload['signingCertSha256']}" in statement
    assert f"VirusTotal Analysis URL: {payload['virusTotalUrl']}" in statement
