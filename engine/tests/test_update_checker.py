import pytest
from fastapi.testclient import TestClient

from engine.twin.api.app import app, compare_semver, parse_semver, ENGINE_VERSION


@pytest.fixture
def client():
    return TestClient(app)


def test_parse_semver():
    assert parse_semver("v2.0.0") == (2, 0, 0, False)
    assert parse_semver("2.1.3") == (2, 1, 3, False)
    assert parse_semver("2.0.0-beta") == (2, 0, 0, True)
    assert parse_semver("1.0.0-alpha.1") == (1, 0, 0, True)
    assert parse_semver("") == (0, 0, 0, False)


def test_compare_semver_rules():
    # Higher major
    assert compare_semver("1.0.0", "v2.0.0") is True
    assert compare_semver("1.9.9", "2.0.0") is True

    # Higher minor
    assert compare_semver("2.0.0", "v2.1.0") is True
    assert compare_semver("2.0.0", "2.1.0") is True

    # Higher patch
    assert compare_semver("2.0.0", "v2.0.1") is True

    # Prerelease vs stable
    assert compare_semver("2.0.0-beta", "v2.0.0") is True
    assert compare_semver("2.0.0-alpha", "2.0.0") is True

    # Identical or older
    assert compare_semver("2.0.0", "v2.0.0") is False
    assert compare_semver("2.0.0", "2.0.0") is False
    assert compare_semver("2.0.0", "v1.0.0") is False
    assert compare_semver("2.1.0", "v2.0.9") is False
    assert compare_semver("2.0.0", "") is False


def test_v1_health_reports_v2(client):
    res = client.get("/v1/health")
    assert res.status_code == 200
    data = res.json()
    assert data["status"] == "ok"
    assert data["service"] == "hunch-engine"
    assert data["version"] == "2.0.0"
    assert ENGINE_VERSION == "2.0.0"


def test_system_update_check_endpoint(client):
    res = client.get("/v1/system/update-check")
    assert res.status_code == 200
    data = res.json()
    assert data["currentVersion"] == "2.0.0"
    assert "updateAvailable" in data
    assert isinstance(data["updateAvailable"], bool)
    assert "downloadUrl" in data
    assert "releaseHtmlUrl" in data
    assert "releaseNotes" in data
    assert "lastCheckedAt" in data
