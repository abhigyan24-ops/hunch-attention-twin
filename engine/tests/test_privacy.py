"""Tests for POST /v1/privacy/forget-today and GET /v1/privacy/status."""
import pytest
from fastapi.testclient import TestClient


@pytest.fixture
def client():
    from twin.api.app import app
    return TestClient(app)


def test_forget_today_returns_success(client):
    r = client.post("/v1/privacy/forget-today")
    assert r.status_code == 200
    data = r.json()
    assert data["success"] is True
    assert "wiped" in data["message"].lower() or "wipe" in data["message"].lower()


def test_privacy_status_zeros(client):
    r = client.get("/v1/privacy/status")
    assert r.status_code == 200
    data = r.json()
    assert data["rawKeystrokesLogged"] == 0
    assert data["rawNotificationTextStored"] == 0
    assert data["sourceCodeCaptured"] == 0


def test_privacy_status_today_wiped_flag(client):
    client.post("/v1/privacy/forget-today")
    r = client.get("/v1/privacy/status")
    assert r.status_code == 200
    assert r.json()["todayWiped"] is True


def test_forget_today_clears_circadian(client):
    # Post a schedule
    client.post("/v1/wellbeing/circadian", json={
        "bedtimeHour": 23, "bedtimeMinute": 0,
        "wakeHour": 7, "wakeMinute": 0,
        "lastNightSleepMinutes": 420,
        "isAutoDetected": True,
    })
    # Forget today
    client.post("/v1/privacy/forget-today")
    # GET circadian should return default now
    r = client.get("/v1/wellbeing/circadian")
    data = r.json()
    # After forget, state is cleared → returns default 23:30 / 7:00
    assert data["bedtimeHour"] == 23
    assert data["bedtimeMinute"] == 30
