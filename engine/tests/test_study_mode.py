import sys
import time
import pytest
from fastapi.testclient import TestClient

from engine.twin.ingest.db import Database
import engine.twin.ingest.db as db_module
from engine.twin.distraction_predictor import DistractionPredictor
from engine.twin.ml_model import MLManager
from engine.twin.api.app import app


@pytest.fixture
def client(tmp_path, monkeypatch):
    db_path = tmp_path / "test_study.db"
    monkeypatch.setenv("HUNCH_DB_PATH", str(db_path))
    test_db = Database(db_path)
    db_module._db_instance = test_db

    for mod_name in ["twin.api.app", "engine.twin.api.app"]:
        if mod_name in sys.modules:
            mod = sys.modules[mod_name]
            mod.db = test_db
            mod.distraction_predictor = DistractionPredictor(test_db)
            mod.ml_manager = MLManager(db_path=db_path)

    return TestClient(app)


def test_study_mode_lifecycle(client):
    # Initial study mode state should be inactive
    r = client.get("/v1/flow/study-mode")
    assert r.status_code == 200
    data = r.json()
    assert data["studyModeActive"] is False
    assert data["remainingMinutes"] == 0

    # Enable study mode for 60 minutes
    r = client.post(
        "/v1/flow/study-mode",
        json={"active": True, "durationMinutes": 60, "source": "user", "topicTitle": "MIT 6.824 Lecture 1"},
    )
    assert r.status_code == 200
    res = r.json()
    assert res["success"] is True
    assert res["studyModeActive"] is True
    assert res["remainingMinutes"] == 60
    assert res["topicTitle"] == "MIT 6.824 Lecture 1"

    # Query GET /v1/flow/study-mode
    r = client.get("/v1/flow/study-mode")
    assert r.status_code == 200
    data = r.json()
    assert data["studyModeActive"] is True
    assert data["remainingMinutes"] >= 59
    assert data["activeTopic"] == "MIT 6.824 Lecture 1"

    # Query GET /v1/flow/active-context
    r = client.get("/v1/flow/active-context")
    assert r.status_code == 200
    ctx = r.json()
    assert ctx["studyModeActive"] is True
    assert ctx["activeTopic"] == "MIT 6.824 Lecture 1"
    assert ctx["remainingStudyMinutes"] >= 59

    # Disable study mode
    r = client.post("/v1/flow/study-mode", json={"active": False})
    assert r.status_code == 200
    assert r.json()["studyModeActive"] is False

    r = client.get("/v1/flow/study-mode")
    assert r.json()["studyModeActive"] is False


def test_code_along_horizon_and_active_coding(client):
    target_mod = sys.modules.get("engine.twin.api.app") or sys.modules.get("twin.api.app")
    # Reset last code activity to older than 180 seconds
    with target_mod._code_activity_lock:
        target_mod._last_code_activity_ts = time.time() - 250

    r = client.get("/v1/flow/active-context")
    assert r.json()["isCodingActiveRecently"] is False

    # Simulate active coding event
    r = client.post("/v1/flow/active-coding", json={"is_coding": True, "active_workspace": "twin-engine"})
    assert r.status_code == 200

    r = client.get("/v1/flow/active-context")
    ctx = r.json()
    assert ctx["isCodingActiveRecently"] is True
    assert ctx["activeWorkspace"] == "twin-engine"


def test_timestamped_youtube_park_tab(client):
    # Park a YouTube tab with resumeTimestampSeconds = 860 (14 minutes 20 seconds)
    r = client.post(
        "/v1/flow/park-tab",
        json={
            "url": "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "title": "Never Gonna Give You Up",
            "resumeTimestampSeconds": 860,
            "from_workspace": "frontend",
        },
    )
    assert r.status_code == 200
    tab_id = r.json()["tab_id"]

    # Check parked tabs list
    r = client.get("/v1/flow/parked-tabs?unconsumed_only=true")
    assert r.status_code == 200
    tabs = r.json()["tabs"]
    assert len(tabs) >= 1
    target = next((t for t in tabs if t["tab_id"] == tab_id), None)
    assert target is not None
    assert target["resume_timestamp_seconds"] == 860
    assert "t=860s" in target["url"]
    assert target["paused_badge"] == "Paused at 14:20"


def test_study_mode_protects_ml_activity_buckets(client):
    # When study mode is active, activity bucket was_distracted must be forced to 0
    client.post("/v1/flow/study-mode", json={"active": True, "durationMinutes": 30, "topicTitle": "Rust Concurrency"})

    now_ms = int(time.time() * 1000)
    r = client.post(
        "/v1/insights/activity-bucket",
        json={
            "bucket_start_ts": now_ms,
            "coding_active_seconds": 0,
            "app_switch_count": 5,
            "phone_unlock_count": 0,
            "notifications_held_count": 2,
            "was_distracted": True, # Client reports distraction on youtube
        },
    )
    assert r.status_code == 200

    # Verify directly from database that was_distracted was coerced to 0
    target_mod = sys.modules.get("engine.twin.api.app") or sys.modules.get("twin.api.app")
    with target_mod.db.get_connection() as conn:
        cur = conn.cursor()
        cur.execute("SELECT was_distracted FROM real_activity_buckets WHERE bucket_start_ts = ?", (now_ms,))
        row = cur.fetchone()
        assert row is not None
        assert row[0] == 0 # Protected as non-distracted study session!

    # Clean up study mode
    client.post("/v1/flow/study-mode", json={"active": False})
