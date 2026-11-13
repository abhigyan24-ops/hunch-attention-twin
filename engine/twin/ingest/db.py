import os
import shutil
import sqlite3
import sys
import time
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

SCHEMA_PATH = Path(__file__).parent / "schema.sql"


def _default_db_path() -> Path:
    env_path = os.getenv("HUNCH_DB_PATH") or os.getenv("DHYAN_DB_PATH")
    if env_path:
        return Path(env_path)
    if getattr(sys, "frozen", False):
        base = os.getenv("LOCALAPPDATA") or os.getenv("APPDATA") or str(Path.home())
        return Path(base) / "Dhyan" / "dhyan_twin.db"
    return Path("dhyan_twin.db")


DEFAULT_DB_PATH = _default_db_path()
CURRENT_SCHEMA_VERSION = 1


class Database:
    def __init__(self, db_path: Optional[Path] = None):
        self.db_path = Path(db_path) if db_path else _default_db_path()
        self.init_db()

    def get_connection(self) -> sqlite3.Connection:
        conn = sqlite3.connect(str(self.db_path), timeout=10.0, check_same_thread=False)
        conn.row_factory = sqlite3.Row
        conn.execute("PRAGMA journal_mode = WAL;")
        conn.execute("PRAGMA busy_timeout = 5000;")
        conn.execute("PRAGMA synchronous = NORMAL;")
        return conn

    def _backup_db(self, old_ver: int) -> Path:
        bak_path = self.db_path.with_name(f"{self.db_path.name}.bak-v{old_ver}")
        shutil.copy2(self.db_path, bak_path)
        # Prune old backups, keeping only the last 2
        backups = sorted(self.db_path.parent.glob(f"{self.db_path.name}.bak-v*"), key=os.path.getmtime)
        while len(backups) > 2:
            try:
                backups.pop(0).unlink()
            except OSError:
                break
        return bak_path

    def _restore_db(self, old_ver: int) -> None:
        bak_path = self.db_path.with_name(f"{self.db_path.name}.bak-v{old_ver}")
        if bak_path.exists():
            shutil.copy2(bak_path, self.db_path)

    def _migrate(self, conn: sqlite3.Connection, from_ver: int, to_ver: int) -> None:
        # Schema migration hooks for forward transitions
        pass

    def init_db(self) -> None:
        if self.db_path.parent and not self.db_path.parent.exists():
            self.db_path.parent.mkdir(parents=True, exist_ok=True)
        with self.get_connection() as conn:
            cur = conn.cursor()
            cur.execute("PRAGMA user_version;")
            row = cur.fetchone()
            version = row[0] if row else 0

            if version == 0:
                if SCHEMA_PATH.exists():
                    with open(SCHEMA_PATH, "r", encoding="utf-8") as f:
                        conn.executescript(f.read())
                cur.execute(f"PRAGMA user_version = {CURRENT_SCHEMA_VERSION};")
                conn.commit()
            elif version < CURRENT_SCHEMA_VERSION:
                self._backup_db(version)
                try:
                    self._migrate(conn, version, CURRENT_SCHEMA_VERSION)
                    cur.execute(f"PRAGMA user_version = {CURRENT_SCHEMA_VERSION};")
                    conn.commit()
                except Exception as err:
                    conn.rollback()
                    self._restore_db(version)
                    raise RuntimeError(f"Database migration from v{version} failed, restored backup: {err}")

    def calculate_ntp_offset(
        self,
        t1_client_send: int,
        t2_server_recv: int,
        t3_server_reply: Optional[int] = None,
        t4_client_recv: Optional[int] = None,
    ) -> Tuple[int, int]:
        """
        Calculates NTP clock offset (ms) and round-trip delay (ms).
        If 4-timestamp handshake is available:
            offset = ((t2 - t1) + (t3 - t4)) // 2
            delay = (t4 - t1) - (t3 - t2)
        If one-way handshake:
            offset = t2 - t1
            delay = 0
        """
        if t3_server_reply is not None and t4_client_recv is not None:
            offset = ((t2_server_recv - t1_client_send) + (t3_server_reply - t4_client_recv)) // 2
            delay = (t4_client_recv - t1_client_send) - (t3_server_reply - t2_server_recv)
            return int(offset), max(0, int(delay))
        offset = t2_server_recv - t1_client_send
        return int(offset), 0

    def record_device_sync(self, device_id: str, offset_ms: int, delay_ms: int = 0) -> None:
        now_ms = int(time.time() * 1000)
        with self.get_connection() as conn:
            conn.execute(
                """
                INSERT INTO device_sync (device_id, clock_offset_ms, round_trip_ms, last_sync_ts)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(device_id) DO UPDATE SET
                    clock_offset_ms = excluded.clock_offset_ms,
                    round_trip_ms = excluded.round_trip_ms,
                    last_sync_ts = excluded.last_sync_ts
                """,
                (device_id, offset_ms, delay_ms, now_ms),
            )
            conn.commit()

    def get_device_offset(self, device_id: str) -> int:
        with self.get_connection() as conn:
            cur = conn.cursor()
            cur.execute("SELECT clock_offset_ms FROM device_sync WHERE device_id = ?", (device_id,))
            row = cur.fetchone()
            return int(row["clock_offset_ms"]) if row else 0

    def get_event_count(self) -> int:
        with self.get_connection() as conn:
            cur = conn.cursor()
            cur.execute("SELECT COUNT(*) AS cnt FROM events")
            row = cur.fetchone()
            return int(row["cnt"]) if row else 0

    def insert_events(self, events: List[Dict[str, Any]]) -> int:
        if not events:
            return 0
        now_ms = int(time.time() * 1000)
        inserted = 0
        with self.get_connection() as conn:
            for ev in events:
                conn.execute(
                    """
                    INSERT INTO events (device_id, ts, event_type, payload, created_at)
                    VALUES (?, ?, ?, ?, ?)
                    """,
                    (
                        ev.get("device_id", "unknown"),
                        ev.get("ts", now_ms),
                        ev.get("event_type", "generic"),
                        ev.get("payload", "{}"),
                        now_ms,
                    ),
                )
                inserted += 1
            conn.commit()
        return inserted

    def insert_input_summaries(self, summaries: List[Dict[str, Any]]) -> int:
        if not summaries:
            return 0
        inserted = 0
        with self.get_connection() as conn:
            for s in summaries:
                conn.execute(
                    """
                    INSERT INTO input_summary (
                        device_id, window_start, window_end, keystrokes,
                        mouse_px, idle_s, active_app, category
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    (
                        s.get("device_id", "laptop"),
                        s.get("window_start", 0),
                        s.get("window_end", 0),
                        s.get("keystrokes", 0),
                        s.get("mouse_px", 0.0),
                        s.get("idle_s", 0.0),
                        s.get("active_app", ""),
                        s.get("category", "unknown"),
                    ),
                )
                inserted += 1
            conn.commit()
        return inserted

    def insert_notification(self, notif: Dict[str, Any]) -> None:
        with self.get_connection() as conn:
            conn.execute(
                """
                INSERT OR REPLACE INTO notifications (
                    notif_id, device_id, post_time, app_pkg, category,
                    sender_hash, is_vip, urgency_score, action_taken, hold_until
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    notif["notif_id"],
                    notif.get("device_id", "phone"),
                    notif["post_time"],
                    notif.get("app_pkg", ""),
                    notif.get("category", "communication"),
                    notif.get("sender_hash"),
                    1 if notif.get("is_vip") else 0,
                    float(notif.get("urgency_score", 0.0)),
                    notif.get("action_taken", "ALLOW"),
                    notif.get("hold_until"),
                ),
            )
            conn.commit()

    def insert_nudge_telemetry(
        self,
        shown_delta: int = 0,
        accepted_delta: int = 0,
        dismissed_delta: int = 0,
        multiplier: float = 1.0,
        stage: int = 1,
        context: str = "",
    ) -> None:
        now_ms = int(time.time() * 1000)
        with self.get_connection() as conn:
            conn.execute(
                """
                INSERT INTO nudge_telemetry (
                    ts, nudges_shown, nudges_accepted, nudges_dismissed,
                    threshold_multiplier, stage, context
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                (now_ms, shown_delta, accepted_delta, dismissed_delta, multiplier, stage, context),
            )
            conn.commit()

    def get_nudge_totals(self) -> Dict[str, Any]:
        with self.get_connection() as conn:
            cur = conn.cursor()
            cur.execute(
                """
                SELECT
                    COALESCE(SUM(nudges_shown), 0) AS shown,
                    COALESCE(SUM(nudges_accepted), 0) AS accepted,
                    COALESCE(SUM(nudges_dismissed), 0) AS dismissed,
                    COALESCE((SELECT threshold_multiplier FROM nudge_telemetry ORDER BY id DESC LIMIT 1), 1.0) AS multiplier
                FROM nudge_telemetry
                """
            )
            row = cur.fetchone()
            return {
                "nudges_shown": int(row["shown"]) if row else 0,
                "nudges_accepted": int(row["accepted"]) if row else 0,
                "nudges_dismissed": int(row["dismissed"]) if row else 0,
                "threshold_multiplier": float(row["multiplier"]) if row else 1.0,
            }

    def clear_all_data(self) -> None:
        """Wipes all data tables to completely reset the twin database to 0 events."""
        with self.get_connection() as conn:
            conn.execute("DELETE FROM events;")
            conn.execute("DELETE FROM input_summary;")
            conn.execute("DELETE FROM notifications;")
            conn.execute("DELETE FROM focus_blocks;")
            conn.execute("DELETE FROM notif_outcomes;")
            conn.execute("DELETE FROM self_reports;")
            conn.execute("DELETE FROM nudge_telemetry;")
            conn.execute("DELETE FROM device_sync;")
            try:
                conn.execute("DELETE FROM sqlite_sequence;")
            except Exception:
                pass
            conn.commit()

    def get_distinct_apps(self) -> List[str]:
        """
        Returns distinct real apps observed in notifications and input summaries.
        Falls back to standard messaging/social packages only if DB is empty.
        """
        with self.get_connection() as conn:
            cur = conn.cursor()
            cur.execute(
                """
                SELECT DISTINCT app_pkg AS app FROM notifications WHERE app_pkg IS NOT NULL AND app_pkg != ''
                UNION
                SELECT DISTINCT active_app AS app FROM input_summary WHERE active_app IS NOT NULL AND active_app != ''
                """
            )
            apps = [str(r["app"]) for r in cur.fetchall() if r["app"]]
        if not apps:
            return [
                "com.whatsapp",
                "com.google.android.gm",
                "com.slack",
                "com.google.android.apps.messaging",
                "com.instagram.android",
            ]
        return apps

    def get_counts(self) -> Dict[str, int]:
        with self.get_connection() as conn:
            cur = conn.cursor()
            cur.execute("SELECT COUNT(*) AS cnt FROM events")
            events = int(cur.fetchone()["cnt"])
            cur.execute("SELECT COUNT(*) AS cnt FROM notifications")
            notifs = int(cur.fetchone()["cnt"])
            cur.execute("SELECT COUNT(*) AS cnt FROM focus_blocks")
            blocks = int(cur.fetchone()["cnt"])
            cur.execute("SELECT COUNT(*) AS cnt FROM input_summary")
            inputs = int(cur.fetchone()["cnt"])
            cur.execute("SELECT COUNT(*) AS cnt FROM notif_outcomes")
            outcomes = int(cur.fetchone()["cnt"])
        return {
            "events": events,
            "notifications": notifs,
            "focus_blocks": blocks,
            "input_summaries": inputs,
            "notif_outcomes": outcomes,
        }



    def record_activity_bucket(self, bucket: Dict[str, Any]) -> None:
        with self.get_connection() as conn:
            conn.execute(
                """
                INSERT OR REPLACE INTO real_activity_buckets (
                    bucket_start_ts, hour_of_day, day_of_week, coding_active_seconds,
                    app_switch_count, phone_unlock_count, notifications_held_count, was_distracted
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    bucket["bucket_start_ts"],
                    bucket.get("hour_of_day", 0),
                    bucket.get("day_of_week", 0),
                    bucket.get("coding_active_seconds", 0),
                    bucket.get("app_switch_count", 0),
                    bucket.get("phone_unlock_count", 0),
                    bucket.get("notifications_held_count", 0),
                    1 if bucket.get("was_distracted") else 0,
                ),
            )
            conn.commit()

    def record_focus_streak(self, streak: Dict[str, Any]) -> None:
        with self.get_connection() as conn:
            conn.execute(
                """
                INSERT OR REPLACE INTO focus_streaks (
                    streak_id, started_at_ts, ended_at_ts, duration_minutes,
                    hour_of_day, broke_by_source
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
                (
                    streak["streak_id"],
                    streak["started_at_ts"],
                    streak["ended_at_ts"],
                    streak["duration_minutes"],
                    streak.get("hour_of_day", 0),
                    streak.get("broke_by_source", "natural_break"),
                ),
            )
            conn.commit()

    def record_context_snapshot(self, snapshot: Dict[str, Any]) -> None:
        with self.get_connection() as conn:
            conn.execute(
                """
                INSERT OR REPLACE INTO context_snapshots (
                    snapshot_id, created_at_ts, workspace_name, file_path,
                    line_number, column_number, git_branch, user_note, resumed_at_ts
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    snapshot["snapshot_id"],
                    snapshot.get("created_at_ts") or int(time.time() * 1000),
                    snapshot.get("workspace_name", ""),
                    snapshot.get("file_path", ""),
                    snapshot.get("line_number", 0),
                    snapshot.get("column_number", 0),
                    snapshot.get("git_branch", ""),
                    snapshot.get("user_note", ""),
                    snapshot.get("resumed_at_ts"),
                ),
            )
            conn.commit()

    def get_latest_context_snapshot(self) -> Optional[Dict[str, Any]]:
        with self.get_connection() as conn:
            cur = conn.cursor()
            cur.execute("SELECT * FROM context_snapshots ORDER BY created_at_ts DESC LIMIT 1")
            row = cur.fetchone()
            return dict(row) if row else None

    def resume_context_snapshot(self, snapshot_id: str, resumed_at_ts: int) -> None:
        with self.get_connection() as conn:
            conn.execute(
                "UPDATE context_snapshots SET resumed_at_ts = ? WHERE snapshot_id = ?",
                (resumed_at_ts, snapshot_id),
            )
            conn.commit()

    def record_held_notification(self, notif: Dict[str, Any]) -> None:
        with self.get_connection() as conn:
            conn.execute(
                """
                INSERT OR REPLACE INTO held_notifications (
                    id, arrived_at_ts, app_label, category, count, delivered_at_break
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
                (
                    notif["id"],
                    notif.get("arrived_at_ts") or int(time.time() * 1000),
                    notif.get("app_label", "Unknown"),
                    notif.get("category", "general"),
                    notif.get("count", 1),
                    1 if notif.get("delivered_at_break") else 0,
                ),
            )
            conn.commit()

    def get_held_notifications(self, undelivered_only: bool = True) -> List[Dict[str, Any]]:
        with self.get_connection() as conn:
            cur = conn.cursor()
            if undelivered_only:
                cur.execute("SELECT * FROM held_notifications WHERE delivered_at_break = 0 ORDER BY arrived_at_ts DESC")
            else:
                cur.execute("SELECT * FROM held_notifications ORDER BY arrived_at_ts DESC")
            return [dict(r) for r in cur.fetchall()]

    def get_total_real_activity_minutes(self) -> int:
        with self.get_connection() as conn:
            cur = conn.cursor()
            cur.execute("SELECT COUNT(*) AS bucket_count FROM real_activity_buckets")
            row = cur.fetchone()
            bucket_mins = int(row["bucket_count"]) * 5 if row else 0
            cur.execute("SELECT SUM(duration_minutes) AS streak_mins FROM focus_streaks")
            row = cur.fetchone()
            streak_mins = int(row["streak_mins"]) if row and row["streak_mins"] else 0
            cur.execute("SELECT SUM(duration_s) AS block_s FROM focus_blocks")
            row = cur.fetchone()
            block_mins = int(row["block_s"] / 60) if row and row["block_s"] else 0
            cur.execute("SELECT COUNT(*) AS input_count FROM input_summary")
            row = cur.fetchone()
            input_mins = int(row["input_count"] / 6) if row and row["input_count"] else 0
            return max(bucket_mins, streak_mins, block_mins, input_mins)

    def get_focus_streaks(self, hour_of_day: Optional[int] = None) -> List[Dict[str, Any]]:
        with self.get_connection() as conn:
            cur = conn.cursor()
            if hour_of_day is not None:
                cur.execute("SELECT * FROM focus_streaks WHERE hour_of_day = ? ORDER BY started_at_ts DESC", (hour_of_day,))
            else:
                cur.execute("SELECT * FROM focus_streaks ORDER BY started_at_ts DESC")
            return [dict(r) for r in cur.fetchall()]

    def get_hourly_activity_stats(self, days: int = 14) -> List[Dict[str, Any]]:
        cutoff_ts = int(time.time() * 1000) - (days * 86400 * 1000)
        with self.get_connection() as conn:
            cur = conn.cursor()
            cur.execute(
                """
                SELECT
                    hour_of_day,
                    COUNT(*) AS bucket_count,
                    SUM(coding_active_seconds) AS total_coding_s,
                    SUM(app_switch_count) AS total_app_switches,
                    SUM(phone_unlock_count) AS total_phone_unlocks,
                    SUM(notifications_held_count) AS total_held,
                    SUM(was_distracted) AS total_distracted
                FROM real_activity_buckets
                WHERE bucket_start_ts >= ?
                GROUP BY hour_of_day
                ORDER BY hour_of_day ASC
                """,
                (cutoff_ts,),
            )
            return [dict(r) for r in cur.fetchall()]


_db_instance: Optional[Database] = None


def get_db(db_path: Optional[Path] = None) -> Database:
    global _db_instance
    if _db_instance is None or (db_path and _db_instance.db_path != db_path):
        _db_instance = Database(db_path)
    return _db_instance
