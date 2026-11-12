"""Tests for POST /v1/wellbeing/circadian and GET /v1/wellbeing/circadian endpoints."""
import pytest
from fastapi.testclient import TestClient


@pytest.fixture
def client():
    from twin.api.app import app
    return TestClient(app)


def test_post_circadian_normal_sleep(client):
    """Standard sleep (≥6h) → 50m focus blocks."""
    r = client.post("/v1/wellbeing/circadian", json={
        "bedtimeHour": 23, "bedtimeMinute": 30,
        "wakeHour": 7, "wakeMinute": 0,
        "lastNightSleepMinutes": 450,
        "isAutoDetected": True,
    })
    assert r.status_code == 200
    data = r.json()
    assert data["recommendedFocusMinutes"] == 50
    assert data["mode"] == "standard_deep_work"
    assert data["bedtimeHour"] == 23
    assert data["wakeHour"] == 7


def test_post_circadian_low_sleep(client):
    """Low sleep (<6h) → 25m focus blocks, low_sleep_recovery mode."""
    r = client.post("/v1/wellbeing/circadian", json={
        "bedtimeHour": 1, "bedtimeMinute": 0,
        "wakeHour": 5, "wakeMinute": 30,
        "lastNightSleepMinutes": 270,
        "isAutoDetected": False,
    })
    assert r.status_code == 200
    data = r.json()
    assert data["recommendedFocusMinutes"] == 25
    assert data["mode"] == "low_sleep_recovery"
    assert data["breakMinutes"] == 5


def test_post_circadian_exactly_360_minutes(client):
    """Exactly 360 minutes = standard (not low sleep)."""
    r = client.post("/v1/wellbeing/circadian", json={
        "bedtimeHour": 23, "bedtimeMinute": 0,
        "wakeHour": 5, "wakeMinute": 0,
        "lastNightSleepMinutes": 360,
        "isAutoDetected": True,
    })
    assert r.status_code == 200
    assert r.json()["recommendedFocusMinutes"] == 50


def test_get_circadian_returns_last_posted(client):
    """GET returns the most recently POSTed schedule."""
    client.post("/v1/wellbeing/circadian", json={
        "bedtimeHour": 22, "bedtimeMinute": 45,
        "wakeHour": 6, "wakeMinute": 15,
        "lastNightSleepMinutes": 450,
        "isAutoDetected": True,
    })
    r = client.get("/v1/wellbeing/circadian")
    assert r.status_code == 200
    data = r.json()
    assert data["bedtimeHour"] == 22
    assert data["bedtimeMinute"] == 45


def test_get_circadian_default_when_no_post(client):
    """GET returns sensible default if nothing has been posted yet."""
    # Reset state by posting a known value then calling forget-today
    client.post("/v1/privacy/forget-today")
    r = client.get("/v1/wellbeing/circadian")
    assert r.status_code == 200
    data = r.json()
    # Default: 23:30 bedtime, 7:00 wake
    assert data["bedtimeHour"] == 23
    assert data["bedtimeMinute"] == 30
    assert data["wakeHour"] == 7


def test_circadian_wind_down_field_is_boolean(client):
    r = client.post("/v1/wellbeing/circadian", json={
        "bedtimeHour": 23, "bedtimeMinute": 59,
        "wakeHour": 7, "wakeMinute": 0,
        "lastNightSleepMinutes": 420,
        "isAutoDetected": False,
    })
    assert isinstance(r.json()["windDownActive"], bool)
