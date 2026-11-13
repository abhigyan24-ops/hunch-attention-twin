import time
import pytest
from fastapi.testclient import TestClient


@pytest.fixture
def client(tmp_path, monkeypatch):
    db_file = tmp_path / "test_flow.db"
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


def test_call_state_pauses_distraction_countdown(client):
    # Set call state to in_call = True
    r = client.post("/v1/flow/call-state", json={"in_call": True, "source": "phone"})
    assert r.status_code == 200
    assert r.json()["call_state"]["in_call"] is True

    # Check GET /v1/flow/call-state
    r = client.get("/v1/flow/call-state")
    assert r.status_code == 200
    assert r.json()["in_call"] is True

    # Human summary should indicate paused by call
    r = client.get("/v1/insights/human-summary?current_streak_minutes=15")
    assert r.status_code == 200
    data = r.json()
    assert data["distractionForecast"]["isPausedByCall"] is True
    assert "paused" in data["distractionForecast"]["humanHeadline"].lower()

    # Clear call state
    client.post("/v1/flow/call-state", json={"in_call": False, "source": "phone"})
    r = client.get("/v1/insights/human-summary?current_streak_minutes=15")
    assert r.json()["distractionForecast"]["isPausedByCall"] is False


def test_compile_shield_lifecycle_and_saves(client):
    # Activate compile shield
    r = client.post("/v1/flow/compile-shield", json={"active": True, "task_name": "gradlew assembleDebug"})
    assert r.status_code == 200

    r = client.get("/v1/flow/compile-shield")
    assert r.status_code == 200
    assert r.json()["active"] is True
    assert r.json()["task_name"] == "gradlew assembleDebug"

    # Finish build (saved distraction)
    r = client.post("/v1/flow/compile-shield", json={
        "active": False,
        "task_name": "gradlew assembleDebug",
        "duration_ms": 12500,
        "last_outcome": "success",
    })
    assert r.status_code == 200

    # Total saves should be incremented
    r = client.get("/v1/flow/compile-shield")
    assert r.status_code == 200
    assert r.json()["active"] is False
    assert r.json()["total_saves"] >= 1


def test_parked_tabs_lifecycle(client):
    # Park a tab
    r = client.post("/v1/flow/park-tab", json={
        "url": "https://news.ycombinator.com",
        "title": "Hacker News",
        "from_workspace": "hunch-attention-twin",
    })
    assert r.status_code == 200
    tab_id = r.json()["tab_id"]
    assert tab_id.startswith("tab_")

    # List parked tabs
    r = client.get("/v1/flow/parked-tabs")
    assert r.status_code == 200
    tabs = r.json()["tabs"]
    assert len(tabs) >= 1
    assert any(t["tab_id"] == tab_id for t in tabs)

    # Consume tab
    r = client.post("/v1/flow/park-tab/consume", json={"tab_id": tab_id})
    assert r.status_code == 200

    # Should no longer be in unconsumed list
    r = client.get("/v1/flow/parked-tabs?unconsumed_only=true")
    assert not any(t["tab_id"] == tab_id for t in r.json()["tabs"])


def test_active_coding_tracking(client):
    r = client.post("/v1/flow/active-coding", json={
        "is_coding": True,
        "active_workspace": "hunch-attention-twin",
    })
    assert r.status_code == 200

    r = client.get("/v1/flow/active-coding")
    assert r.status_code == 200
    assert r.json()["is_coding"] is True
    assert r.json()["active_workspace"] == "hunch-attention-twin"


def test_enriched_context_snapshot_and_weekly_wrapped(client):
    now_ts = int(time.time() * 1000)

    # Record enriched snapshot
    r = client.post("/v1/insights/context-snapshot", json={
        "snapshot_id": "snap_test_enrich",
        "created_at_ts": now_ts,
        "workspace_name": "hunch-attention-twin",
        "file_path": "engine/twin/vault.py",
        "line_number": 88,
        "column_number": 12,
        "git_branch": "main",
        "user_note": "Refactoring DPAPI encryption",
        "enclosing_symbol": "store()",
        "git_stat_summary": "1 file changed, +12 / -2 lines",
        "last_build_summary": "pytest passed in 3s",
    })
    assert r.status_code == 200

    # Retrieve latest snapshot
    r = client.get("/v1/insights/context-snapshot")
    assert r.status_code == 200
    snap = r.json()["snapshot"]
    assert snap["enclosing_symbol"] == "store()"
    assert snap["git_stat_summary"] == "1 file changed, +12 / -2 lines"
    assert snap["last_build_summary"] == "pytest passed in 3s"

    # Seed focus streak for weekly wrapped
    client.post("/v1/insights/focus-streak", json={
        "streak_id": "stk_wrapped_1",
        "started_at_ts": now_ts - 3600000,
        "ended_at_ts": now_ts,
        "duration_minutes": 60,
        "broke_by_source": "natural_break",
    })

    # Test GET /v1/insights/weekly-wrapped
    r = client.get("/v1/insights/weekly-wrapped?days=7")
    assert r.status_code == 200
    wrapped = r.json()
    assert "totalDeepWorkHours" in wrapped
    assert "longestStreakMinutes" in wrapped
    assert "compileShieldSaves" in wrapped
    assert "tabsParkedCount" in wrapped
    assert "days" in wrapped
    assert len(wrapped["days"]) == 7
