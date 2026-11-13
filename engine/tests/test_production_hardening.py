"""Production Readiness & Hardening Regression Test Suite.

Verifies SQLite WAL mode, idempotent schema migrations, smart LAN IP detection,
4:00 AM logical circadian day boundary, phone re-hydration handshake, and 90-day retention pruning.
"""

import datetime
import sqlite3
import time
import pytest
from fastapi.testclient import TestClient

from engine.twin.ingest.db import Database, ensure_schema_migrations
import engine.twin.ingest.db as db_module
from engine.twin.lan_resolver import is_virtual_or_unreachable_ip, get_reachable_lan_ips
from engine.twin.logical_day import get_logical_day_key, get_logical_day_start_ts, clamp_monotonic_duration
from engine.twin.ml_model import MLManager, extract_features
from engine.twin.distraction_predictor import DistractionPredictor
from engine.twin.api.app import app
import engine.twin.api.app as app_module


@pytest.fixture
def test_db_path(tmp_path):
    return tmp_path / "test_prod_hunch.db"


@pytest.fixture
def test_db(test_db_path):
    db = Database(test_db_path)
    return db


@pytest.fixture
def client(test_db_path, monkeypatch):
    monkeypatch.setenv("HUNCH_DB_PATH", str(test_db_path))
    test_db = Database(test_db_path)
    db_module._db_instance = test_db

    import sys
    for mod_name in ["twin.api.app", "engine.twin.api.app"]:
        if mod_name in sys.modules:
            mod = sys.modules[mod_name]
            mod.db = test_db
            mod.distraction_predictor = DistractionPredictor(test_db)
            mod.ml_manager = MLManager(db_path=test_db_path)

    return TestClient(app)


def test_sqlite_wal_and_idempotent_schema_migrations(tmp_path):
    """Legacy tables missing newly added columns are safely and idempotently migrated."""
    legacy_db_file = tmp_path / "legacy.db"
    conn = sqlite3.connect(str(legacy_db_file))
    conn.row_factory = sqlite3.Row

    # Create an outdated schema missing several columns
    conn.execute("""
        CREATE TABLE real_activity_buckets (
            bucket_start_ts INTEGER PRIMARY KEY,
            hour_of_day INTEGER
        )
    """)
    conn.execute("""
        CREATE TABLE context_snapshots (
            snapshot_id TEXT PRIMARY KEY,
            created_at_ts INTEGER
        )
    """)
    conn.commit()

    # Verify initial column count
    cur = conn.cursor()
    cur.execute("PRAGMA table_info(real_activity_buckets)")
    initial_cols = [r[1] for r in cur.fetchall()]
    assert "was_distracted" not in initial_cols

    # Run idempotent schema migrator
    ensure_schema_migrations(conn)

    cur.execute("PRAGMA table_info(real_activity_buckets)")
    migrated_cols = [r[1] for r in cur.fetchall()]
    assert "was_distracted" in migrated_cols
    assert "notifications_held_count" in migrated_cols
    assert "coding_active_seconds" in migrated_cols

    cur.execute("PRAGMA table_info(context_snapshots)")
    snap_cols = [r[1] for r in cur.fetchall()]
    assert "enclosing_symbol" in snap_cols
    assert "last_build_summary" in snap_cols

    # Idempotence check: Running again must not raise any error
    ensure_schema_migrations(conn)

    # Verify WAL mode on connection
    conn.execute("PRAGMA journal_mode = WAL;")
    cur.execute("PRAGMA journal_mode;")
    mode = cur.fetchone()[0]
    assert mode.lower() == "wal"
    conn.close()


def test_get_reachable_lan_ips_filters_virtual_adapters():
    """Smart LAN IP resolver rejects WSL, Docker, loopback, and link-local addresses."""
    assert is_virtual_or_unreachable_ip("127.0.0.1") is True
    assert is_virtual_or_unreachable_ip("0.0.0.0") is True
    assert is_virtual_or_unreachable_ip("169.254.42.1") is True
    assert is_virtual_or_unreachable_ip("192.168.65.3") is True  # Docker Desktop
    assert is_virtual_or_unreachable_ip("10.255.0.1") is True    # Docker Desktop
    assert is_virtual_or_unreachable_ip("172.18.0.1") is True    # WSL / Hyper-V
    assert is_virtual_or_unreachable_ip("172.28.14.99") is True  # WSL2 vEthernet

    # Real consumer Wi-Fi and corporate LAN addresses are accepted
    assert is_virtual_or_unreachable_ip("192.168.1.100") is False
    assert is_virtual_or_unreachable_ip("10.0.0.15") is False

    primary_ip, candidates = get_reachable_lan_ips()
    assert isinstance(primary_ip, str)
    assert len(candidates) >= 1
    assert "127.0.0.1" in candidates


def test_logical_day_key_circadian_rollover():
    """4:00 AM circadian boundary maps pre-4 AM late night coding to previous work day."""
    late_night = datetime.datetime(2026, 11, 13, 1, 30, 0)
    assert get_logical_day_key(late_night, rollover_hour=4) == "2026-11-12"

    early_morning_prev = datetime.datetime(2026, 11, 13, 3, 59, 59)
    assert get_logical_day_key(early_morning_prev, rollover_hour=4) == "2026-11-12"

    fresh_morning = datetime.datetime(2026, 11, 13, 5, 0, 0)
    assert get_logical_day_key(fresh_morning, rollover_hour=4) == "2026-11-13"

    # Start timestamp for late night maps back to 4:00 AM on Nov 12
    start_ts = get_logical_day_start_ts(late_night, rollover_hour=4)
    expected_start = int(datetime.datetime(2026, 11, 12, 4, 0, 0).timestamp() * 1000)
    assert start_ts == expected_start

    # Clamping prevents negative clock skew
    assert clamp_monotonic_duration(-120) == 0.0
    assert clamp_monotonic_duration(45.5) == 45.5


def test_sync_handshake_rehydrates_reinstalled_phone(client):
    """POST /v1/sync/handshake returns existing profile and trained ML weights to fresh client."""
    # Seed ML manager with some trained outcomes
    features = extract_features({"session_elapsed_mins": 30.0})
    for _ in range(8):
        client.post(
            "/v1/ml/record-outcome",
            json={"actual_distracted": True, "selected_arm": "auto_quiet_shield", "reward": 0.8},
        )

    # Set circadian schedule
    client.post(
        "/v1/wellbeing/circadian",
        json={
            "bedtimeHour": 23,
            "bedtimeMinute": 30,
            "wakeHour": 7,
            "wakeMinute": 30,
            "lastNightSleepMinutes": 480,
        },
    )

    # Newly reinstalled phone connects with 0 trained samples
    res = client.post(
        "/v1/sync/handshake",
        json={
            "device_id": "phone_reinstalled_1",
            "device_type": "phone",
            "app_version": "1.0.0",
            "trained_samples_count": 0,
        },
    )
    assert res.status_code == 200
    data = res.json()
    assert data["status"] == "connected"
    assert data["restored_profile"] is True
    assert data["ml_model"]["trained_samples_count"] >= 8
    assert data["circadian"] is not None
    assert data["circadian"]["bedtimeHour"] == 23


def test_prune_stale_telemetry_keeps_streaks_and_trims_buckets(test_db):
    """prune_stale_telemetry deletes 5-minute buckets older than 90 days while preserving focus streaks."""
    now_ms = int(time.time() * 1000)
    old_ts = now_ms - (100 * 86400 * 1000)  # 100 days old
    recent_ts = now_ms - (10 * 86400 * 1000)  # 10 days old

    with test_db.get_connection() as conn:
        # Insert 1 stale bucket and 1 recent bucket
        conn.execute(
            """
            INSERT OR REPLACE INTO real_activity_buckets (
                bucket_start_ts, hour_of_day, day_of_week, coding_active_seconds
            ) VALUES (?, ?, ?, ?)
            """,
            (old_ts, 14, 2, 280),
        )
        conn.execute(
            """
            INSERT OR REPLACE INTO real_activity_buckets (
                bucket_start_ts, hour_of_day, day_of_week, coding_active_seconds
            ) VALUES (?, ?, ?, ?)
            """,
            (recent_ts, 15, 2, 300),
        )

        # Insert 1 stale focus streak that should NOT be pruned
        conn.execute(
            """
            INSERT OR REPLACE INTO focus_streaks (
                streak_id, started_at_ts, ended_at_ts, duration_minutes, hour_of_day, broke_by_source
            ) VALUES (?, ?, ?, ?, ?, ?)
            """,
            ("old_streak_1", old_ts, old_ts + 1800000, 30, 14, "phone_pickup"),
        )
        conn.commit()

    pruned = test_db.prune_stale_telemetry(retention_days=90)
    assert pruned.get("real_activity_buckets", 0) >= 1

    with test_db.get_connection() as conn:
        cur = conn.cursor()
        cur.execute("SELECT COUNT(*) FROM real_activity_buckets WHERE bucket_start_ts = ?", (old_ts,))
        assert cur.fetchone()[0] == 0  # Pruned

        cur.execute("SELECT COUNT(*) FROM real_activity_buckets WHERE bucket_start_ts = ?", (recent_ts,))
        assert cur.fetchone()[0] == 1  # Preserved

        cur.execute("SELECT COUNT(*) FROM focus_streaks WHERE streak_id = 'old_streak_1'")
        assert cur.fetchone()[0] == 1  # Preserved summary streak


def test_v1_health_and_pairing_endpoints(client):
    """GET /v1/health and GET /v1/sync/pairing-info return expected metadata."""
    res_health = client.get("/v1/health")
    assert res_health.status_code == 200
    h_data = res_health.json()
    assert h_data["status"] == "ok"
    assert h_data["service"] == "hunch-engine"
    assert h_data["version"] == "1.0.0"

    res_pair = client.get("/v1/sync/pairing-info")
    assert res_pair.status_code == 200
    p_data = res_pair.json()
    assert "primaryHost" in p_data
    assert "candidateHosts" in p_data
    assert p_data["service"] == "_hunch._tcp"
    assert "qrPayload" in p_data
