import time
import pytest
from fastapi.testclient import TestClient


@pytest.fixture
def client(tmp_path, monkeypatch):
    db_file = tmp_path / "test_next_gen.db"
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


def test_walk_away_flow_lifecycle(client):
    # 1. Trigger walk-away
    r = client.post("/v1/flow/walk-away", json={
        "state": "WALK_AWAY_CONFIRMED",
        "reason": "4-gate cadence + RSSI drop",
        "confidence": 0.85
    })
    assert r.status_code == 200
    data = r.json()["walk_away"]
    assert data["active"] is True
    assert data["paused_by_walkaway"] is True
    assert data["show_return_replay"] is False

    # Check GET /v1/flow/walk-away
    r = client.get("/v1/flow/walk-away")
    assert r.status_code == 200
    assert r.json()["active"] is True

    # 2. Return to desk
    r = client.post("/v1/flow/walk-away", json={
        "state": "RETURNED_TO_DESK",
        "reason": "laptop typing detected",
    })
    assert r.status_code == 200
    data = r.json()["walk_away"]
    assert data["active"] is False
    assert data["paused_by_walkaway"] is False
    assert data["show_return_replay"] is True

    # Check consuming replay flag
    r = client.get("/v1/flow/walk-away?consume_replay=true")
    assert r.status_code == 200
    assert r.json()["show_return_replay"] is True

    # Subsequent call should have consumed it
    r = client.get("/v1/flow/walk-away")
    assert r.json()["show_return_replay"] is False


def test_thrash_shield_lifecycle(client):
    r = client.post("/v1/flow/thrash-shield", json={
        "active": True,
        "trigger": "repeated_test_failures",
        "reason": "3 consecutive test runs failed",
        "expires_in_seconds": 600
    })
    assert r.status_code == 200
    assert r.json()["thrash_shield"]["active"] is True

    r = client.get("/v1/flow/thrash-shield")
    assert r.status_code == 200
    data = r.json()
    assert data["active"] is True
    assert data["trigger"] == "repeated_test_failures"
    assert data["remainingSeconds"] > 550

    # Deactivate
    client.post("/v1/flow/thrash-shield", json={"active": False})
    r = client.get("/v1/flow/thrash-shield")
    assert r.json()["active"] is False


def test_file_attention_and_pre_commit_audit(client):
    now_ts = int(time.time() * 1000)

    # Log clean file
    client.post("/v1/insights/file-attention-log", json={
        "file_path": "src/utils/helpers.ts",
        "workspace_name": "hunch",
        "is_fatigued_edit": False,
        "interruption_count": 0,
        "thrash_events_count": 0
    })

    # Log file written past bedtime with multiple interruptions
    client.post("/v1/insights/file-attention-log", json={
        "file_path": "src/auth/authController.ts",
        "workspace_name": "hunch",
        "is_fatigued_edit": True,
        "interruption_count": 4,
        "thrash_events_count": 1,
        "context_note": "Late night session"
    })

    # Run audit
    r = client.get("/v1/insights/pre-commit-audit?workspace_name=hunch")
    assert r.status_code == 200
    data = r.json()
    assert data["hasHighRisk"] is True
    assert len(data["flaggedFiles"]) >= 1

    auth_file = next(f for f in data["flaggedFiles"] if "authController.ts" in f["filePath"])
    assert auth_file["isFatigued"] is True
    assert auth_file["interruptions"] == 4
    assert auth_file["thrashCount"] == 1
    assert "Sanity Check" in auth_file["warningMessage"]


def test_share_to_desk_lifecycle(client):
    # Share a URL item
    r = client.post("/v1/flow/share-to-desk", json={
        "content": "Check this out https://github.com/abhigyan24-ops/hunch-attention-twin/issues/42 for bug details",
        "note": "Look at comment 3"
    })
    assert r.status_code == 200
    item_id = r.json()["id"]
    assert item_id.startswith("desk_")

    # Fetch shared items
    r = client.get("/v1/flow/shared-desk-items")
    assert r.status_code == 200
    items = r.json()["items"]
    assert any(i["id"] == item_id for i in items)

    matched = next(i for i in items if i["id"] == item_id)
    assert matched["item_type"] == "url"
    assert "https://github.com" in matched["url"]

    # Consume item
    r = client.post("/v1/flow/shared-desk-items/consume", json={"id": item_id})
    assert r.status_code == 200

    r = client.get("/v1/flow/shared-desk-items?unconsumed_only=true")
    assert not any(i["id"] == item_id for i in r.json()["items"])
