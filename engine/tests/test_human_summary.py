"""Tests for Human-First Insights & Closed-Loop SQLite Telemetry.
Verifies GET /v1/insights/human-summary, calibration status, and prediction engine.
"""

import time
import pytest
from fastapi.testclient import TestClient


@pytest.fixture
def client(tmp_path, monkeypatch):
    db_file = tmp_path / "test_hunch.db"
    monkeypatch.setenv("HUNCH_DB_PATH", str(db_file))

    from twin.ingest.db import Database
    import twin.ingest.db as db_module
    from twin.api.app import app
    import sys
    app_module = sys.modules["twin.api.app"]
    from twin.distraction_predictor import DistractionPredictor

    test_db = Database(db_file)
    db_module._db_instance = test_db
    app_module.db = test_db
    app_module.distraction_predictor = DistractionPredictor(test_db)

    return TestClient(app)


def test_cold_start_calibration_uncalibrated(client):
    """Cold start (0 minutes) must report honest uncalibrated status."""
    res = client.get("/v1/insights/human-summary")
    assert res.status_code == 200
    data = res.json()

    cal = data["calibrationStatus"]
    assert cal["isCalibrated"] is False
    assert cal["realMinutesTracked"] == 0
    assert cal["minMinutesRequired"] == 30
    assert "Learning your natural focus rhythm" in cal["statusLabel"]

    forecast = data["distractionForecast"]
    assert forecast["typicalStreakMinutes"] == 25
    assert forecast["preDistractionShieldTrigger"] is False
    assert "Learning your rhythm" in forecast["humanHeadline"]

    peak = data["peakFocusWindow"]
    assert peak["windowLabel"] == "Calibrating"


def test_activity_buckets_and_streaks_achieve_calibration(client):
    """Recording >30 minutes of real streaks achieves calibrated state and computes median."""
    now_ms = int(time.time() * 1000)
    import datetime
    cur_hour = datetime.datetime.now().hour

    # Record 3 real focus streaks: 25m, 29m, 32m (total = 86 mins > 30m required)
    client.post("/v1/insights/focus-streak", json={
        "streak_id": "streak-1",
        "started_at_ts": now_ms - 7200000,
        "ended_at_ts": now_ms - 5700000,
        "duration_minutes": 25,
        "hour_of_day": cur_hour,
        "broke_by_source": "phone_pickup"
    })
    client.post("/v1/insights/focus-streak", json={
        "streak_id": "streak-2",
        "started_at_ts": now_ms - 5400000,
        "ended_at_ts": now_ms - 3660000,
        "duration_minutes": 29,
        "hour_of_day": cur_hour,
        "broke_by_source": "phone_pickup"
    })
    client.post("/v1/insights/focus-streak", json={
        "streak_id": "streak-3",
        "started_at_ts": now_ms - 3600000,
        "ended_at_ts": now_ms - 1680000,
        "duration_minutes": 32,
        "hour_of_day": cur_hour,
        "broke_by_source": "app_switch"
    })

    res = client.get("/v1/insights/human-summary?current_streak_minutes=26")
    assert res.status_code == 200
    data = res.json()

    cal = data["calibrationStatus"]
    assert cal["isCalibrated"] is True
    assert cal["realMinutesTracked"] >= 86
    assert cal["statusLabel"] == "Personalized to your rhythm"

    forecast = data["distractionForecast"]
    # Median of [25, 29, 32] is 29
    assert forecast["typicalStreakMinutes"] == 29
    # Current streak is 26, so minutesUntil = 29 - 26 = 3
    assert forecast["minutesUntilLikelyDistraction"] == 3
    # 3 mins until distraction with streak >= 5 triggers preDistractionShieldTrigger!
    assert forecast["preDistractionShieldTrigger"] is True
    assert "You usually get distracted around minute 29" in forecast["humanHeadline"]
    assert "(~3 mins from now)" in forecast["humanHeadline"]


def test_activity_buckets_hourly_stats(client):
    """Real activity buckets identify peak work and distraction hours."""
    now_ms = int(time.time() * 1000)

    # 10 AM: High coding, zero distractions (Deep work)
    for i in range(6):
        client.post("/v1/insights/activity-bucket", json={
            "bucket_start_ts": now_ms - (86400000 * 2) + (i * 300000),
            "hour_of_day": 10,
            "day_of_week": 1,
            "coding_active_seconds": 290,
            "app_switch_count": 1,
            "phone_unlock_count": 0,
            "notifications_held_count": 0,
            "was_distracted": False
        })

    # 3 PM: Heavy app switching and distractions (High-risk window)
    for i in range(6):
        client.post("/v1/insights/activity-bucket", json={
            "bucket_start_ts": now_ms - (86400000 * 2) + 18000000 + (i * 300000),
            "hour_of_day": 15,
            "day_of_week": 1,
            "coding_active_seconds": 40,
            "app_switch_count": 18,
            "phone_unlock_count": 4,
            "notifications_held_count": 2,
            "was_distracted": True
        })

    # Total minutes is 60m (12 buckets * 5m) >= 30m calibrated
    res = client.get("/v1/insights/human-summary")
    assert res.status_code == 200
    data = res.json()

    assert data["calibrationStatus"]["isCalibrated"] is True
    assert "3:00 PM" in data["distractionForecast"]["nextHighRiskWindow"]
    assert "10:00 AM" in data["peakFocusWindow"]["windowLabel"]


def test_context_snapshot_park_and_resume(client):
    """Context snapshots support 1-click recovery for 'Where I Left Off'."""
    snap_id = "snap-test-42"
    res = client.post("/v1/insights/context-snapshot", json={
        "snapshot_id": snap_id,
        "workspace_name": "hunch-attention-twin",
        "file_path": "engine/twin/api/app.py",
        "line_number": 128,
        "column_number": 4,
        "git_branch": "main",
        "user_note": "Implement human summary endpoints"
    })
    assert res.status_code == 200
    assert res.json()["success"] is True

    # Retrieve latest
    get_res = client.get("/v1/insights/context-snapshot")
    assert get_res.status_code == 200
    snap = get_res.json()["snapshot"]
    assert snap["snapshot_id"] == snap_id
    assert snap["file_path"] == "engine/twin/api/app.py"
    assert snap["line_number"] == 128
    assert snap["user_note"] == "Implement human summary endpoints"
    assert snap["resumed_at_ts"] is None

    # Resume
    now = int(time.time() * 1000)
    resume_res = client.post("/v1/insights/context-snapshot/resume", json={
        "snapshot_id": snap_id,
        "resumed_at_ts": now
    })
    assert resume_res.status_code == 200

    # Verify updated
    get_res2 = client.get("/v1/insights/context-snapshot")
    assert get_res2.json()["snapshot"]["resumed_at_ts"] == now


def test_held_notifications_lifecycle(client):
    """Held notifications are recorded and queried for the break digest."""
    client.post("/v1/insights/held-notification", json={
        "id": "notif-hash-1",
        "app_label": "Slack",
        "category": "communication",
        "count": 3,
        "delivered_at_break": False
    })
    client.post("/v1/insights/held-notification", json={
        "id": "notif-hash-2",
        "app_label": "Instagram",
        "category": "social",
        "count": 1,
        "delivered_at_break": False
    })

    res = client.get("/v1/insights/held-notifications?undelivered_only=true")
    assert res.status_code == 200
    data = res.json()
    assert data["count"] == 2
    labels = [n["app_label"] for n in data["notifications"]]
    assert "Slack" in labels
    assert "Instagram" in labels
