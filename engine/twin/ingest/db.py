import os
import re
import secrets
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


def ensure_schema_migrations(conn: sqlite3.Connection) -> None:
    """Idempotently inspects PRAGMA table_info for all tables and adds any missing columns."""
    schema_expectations = {
        "real_activity_buckets": {
            "was_distracted": "INTEGER DEFAULT 0",
            "notifications_held_count": "INTEGER DEFAULT 0",
            "phone_unlock_count": "INTEGER DEFAULT 0",
            "app_switch_count": "INTEGER DEFAULT 0",
            "coding_active_seconds": "INTEGER DEFAULT 0",
            "hour_of_day": "INTEGER DEFAULT 12",
            "day_of_week": "INTEGER DEFAULT 0",
        },
        "focus_streaks": {
            "broke_by_source": "TEXT DEFAULT 'phone_pickup'",
            "hour_of_day": "INTEGER DEFAULT 12",
            "duration_minutes": "INTEGER DEFAULT 0",
        },
        "context_snapshots": {
            "enclosing_symbol": "TEXT",
            "git_stat_summary": "TEXT",
            "last_build_summary": "TEXT",
            "resumed_at_ts": "INTEGER",
            "user_note": "TEXT",
            "git_branch": "TEXT",
            "column_number": "INTEGER DEFAULT 0",
            "workspace_name": "TEXT",
        },
        "notifications": {
            "urgency_score": "REAL DEFAULT 0.0",
            "is_vip": "INTEGER DEFAULT 0",
            "hold_until": "INTEGER",
            "sender_hash": "TEXT",
        },
        "focus_blocks": {
            "fatigue_score": "REAL DEFAULT 0.0",
            "primary_category": "TEXT",
            "focus_depth": "REAL DEFAULT 0.5",
            "interruptions_count": "INTEGER DEFAULT 0",
        },
        "file_attention_logs": {
            "is_fatigued_edit": "INTEGER DEFAULT 0",
            "interruption_count": "INTEGER DEFAULT 0",
            "thrash_events_count": "INTEGER DEFAULT 0",
            "context_note": "TEXT",
            "workspace_name": "TEXT",
        },
        "shared_desk_items": {
            "url": "TEXT",
            "title": "TEXT",
            "note": "TEXT",
            "consumed": "INTEGER DEFAULT 0",
        },
        "walk_away_events": {
            "reason": "TEXT",
            "duration_seconds": "INTEGER DEFAULT 0",
        },
        "held_notifications": {
            "count": "INTEGER DEFAULT 1",
            "delivered_at_break": "INTEGER DEFAULT 0",
            "category": "TEXT",
        },
        "parked_tabs": {
            "from_workspace": "TEXT",
            "consumed_at_break": "INTEGER DEFAULT 0",
            "resume_timestamp_seconds": "INTEGER DEFAULT NULL",
            "paused_badge": "TEXT DEFAULT NULL",
        },
    }

    cur = conn.cursor()
    for table, cols in schema_expectations.items():
        cur.execute("SELECT name FROM sqlite_master WHERE type='table' AND name=?", (table,))
        if not cur.fetchone():
            continue
        cur.execute(f"PRAGMA table_info({table})")
        existing_cols = {row[1] for row in cur.fetchall()}
        for col_name, col_def in cols.items():
            if col_name not in existing_cols:
                try:
                    cur.execute(f"ALTER TABLE {table} ADD COLUMN {col_name} {col_def}")
                except sqlite3.OperationalError:
                    pass
    conn.commit()


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

        # Ensure latest tables and columns exist safely across versions
        with self.get_connection() as conn:
            conn.execute("""
                CREATE TABLE IF NOT EXISTS parked_tabs (
                    tab_id TEXT PRIMARY KEY,
                    parked_at_ts INTEGER NOT NULL,
                    url TEXT NOT NULL,
                    title TEXT NOT NULL,
                    from_workspace TEXT,
                    consumed_at_break INTEGER DEFAULT 0,
                    resume_timestamp_seconds INTEGER DEFAULT NULL,
                    paused_badge TEXT DEFAULT NULL
                )
            """)
            conn.execute("""
                CREATE TABLE IF NOT EXISTS study_mode_sessions (
                    session_id TEXT PRIMARY KEY,
                    started_at_ts INTEGER NOT NULL,
                    until_ts INTEGER NOT NULL,
                    duration_minutes INTEGER NOT NULL,
                    source TEXT NOT NULL,
                    topic_title TEXT,
                    active INTEGER DEFAULT 1
                )
            """)
            conn.execute("""
                CREATE TABLE IF NOT EXISTS compile_shield_events (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    ts INTEGER NOT NULL,
                    task_name TEXT NOT NULL,
                    duration_ms INTEGER DEFAULT 0,
                    outcome TEXT DEFAULT 'success',
                    saved_distraction INTEGER DEFAULT 1
                )
            """)
            conn.execute("""
                CREATE TABLE IF NOT EXISTS file_attention_logs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    file_path TEXT NOT NULL,
                    workspace_name TEXT,
                    logged_at_ts INTEGER NOT NULL,
                    is_fatigued_edit INTEGER DEFAULT 0,
                    interruption_count INTEGER DEFAULT 0,
                    thrash_events_count INTEGER DEFAULT 0,
                    context_note TEXT
                )
            """)
            conn.execute("""
                CREATE TABLE IF NOT EXISTS shared_desk_items (
                    id TEXT PRIMARY KEY,
                    created_at_ts INTEGER NOT NULL,
                    item_type TEXT NOT NULL,
                    content TEXT NOT NULL,
                    url TEXT,
                    title TEXT,
                    note TEXT,
                    consumed INTEGER DEFAULT 0
                )
            """)
            conn.execute("""
                CREATE TABLE IF NOT EXISTS walk_away_events (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    ts INTEGER NOT NULL,
                    state TEXT NOT NULL,
                    reason TEXT,
                    duration_seconds INTEGER DEFAULT 0
                )
            """)
            conn.commit()
            ensure_schema_migrations(conn)

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
                    line_number, column_number, git_branch, user_note,
                    enclosing_symbol, git_stat_summary, last_build_summary,
                    resumed_at_ts
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                    snapshot.get("enclosing_symbol", ""),
                    snapshot.get("git_stat_summary", ""),
                    snapshot.get("last_build_summary", ""),
                    snapshot.get("resumed_at_ts"),
                ),
            )
            conn.commit()

    def get_latest_context_snapshot(self, workspace_name: Optional[str] = None) -> Optional[Dict[str, Any]]:
        with self.get_connection() as conn:
            cur = conn.cursor()
            if workspace_name:
                cur.execute(
                    "SELECT * FROM context_snapshots WHERE workspace_name = ? ORDER BY created_at_ts DESC LIMIT 1",
                    (workspace_name,),
                )
                row = cur.fetchone()
                if row:
                    return dict(row)
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

    def record_parked_tab(self, tab: Dict[str, Any]) -> None:
        url = tab.get("url", "")
        resume_secs = tab.get("resume_timestamp_seconds")
        paused_badge = tab.get("paused_badge")

        if resume_secs is not None and resume_secs > 0:
            if not paused_badge:
                if resume_secs >= 3600:
                    h = resume_secs // 3600
                    m = (resume_secs % 3600) // 60
                    s = resume_secs % 60
                    paused_badge = f"Paused at {h}:{m:02d}:{s:02d}"
                else:
                    m = resume_secs // 60
                    s = resume_secs % 60
                    paused_badge = f"Paused at {m}:{s:02d}"

            # Format YouTube URL with timestamp parameter
            lower_url = url.lower()
            if "youtube.com" in lower_url or "youtu.be" in lower_url:
                clean_url = re.sub(r'([?&])t=\d+s?', '', url).rstrip('&?')
                sep = '&' if '?' in clean_url else '?'
                url = f"{clean_url}{sep}t={resume_secs}s"

        with self.get_connection() as conn:
            conn.execute(
                """
                INSERT OR REPLACE INTO parked_tabs (
                    tab_id, parked_at_ts, url, title, from_workspace, consumed_at_break,
                    resume_timestamp_seconds, paused_badge
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    tab["tab_id"],
                    tab.get("parked_at_ts") or int(time.time() * 1000),
                    url,
                    tab.get("title", ""),
                    tab.get("from_workspace", ""),
                    1 if tab.get("consumed_at_break") else 0,
                    resume_secs,
                    paused_badge,
                ),
            )
            conn.commit()

    def record_study_mode(
        self,
        until_ts: int,
        duration_minutes: int,
        source: str = "user",
        topic_title: Optional[str] = None,
        active: bool = True,
    ) -> str:
        session_id = f"study_{int(time.time() * 1000)}_{secrets.token_hex(4)}"
        with self.get_connection() as conn:
            conn.execute("UPDATE study_mode_sessions SET active = 0 WHERE active = 1")
            conn.execute(
                """
                INSERT INTO study_mode_sessions (
                    session_id, started_at_ts, until_ts, duration_minutes, source, topic_title, active
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    session_id,
                    int(time.time() * 1000),
                    until_ts,
                    duration_minutes,
                    source,
                    topic_title,
                    1 if active else 0,
                ),
            )
            conn.commit()
        return session_id

    def get_active_study_mode(self) -> Optional[Dict[str, Any]]:
        with self.get_connection() as conn:
            cur = conn.cursor()
            now_ms = int(time.time() * 1000)
            cur.execute(
                "SELECT * FROM study_mode_sessions WHERE active = 1 AND until_ts > ? ORDER BY started_at_ts DESC LIMIT 1",
                (now_ms,),
            )
            row = cur.fetchone()
            return dict(row) if row else None

    def deactivate_study_mode(self) -> None:
        with self.get_connection() as conn:
            conn.execute("UPDATE study_mode_sessions SET active = 0 WHERE active = 1")
            conn.commit()

    def get_parked_tabs(self, unconsumed_only: bool = True) -> List[Dict[str, Any]]:
        with self.get_connection() as conn:
            cur = conn.cursor()
            if unconsumed_only:
                cur.execute("SELECT * FROM parked_tabs WHERE consumed_at_break = 0 ORDER BY parked_at_ts DESC")
            else:
                cur.execute("SELECT * FROM parked_tabs ORDER BY parked_at_ts DESC")
            return [dict(r) for r in cur.fetchall()]

    def consume_parked_tab(self, tab_id: str) -> None:
        with self.get_connection() as conn:
            conn.execute(
                "UPDATE parked_tabs SET consumed_at_break = 1 WHERE tab_id = ?",
                (tab_id,),
            )
            conn.commit()

    def record_compile_shield_event(self, event: Dict[str, Any]) -> None:
        with self.get_connection() as conn:
            conn.execute(
                """
                INSERT INTO compile_shield_events (
                    ts, task_name, duration_ms, outcome, saved_distraction
                ) VALUES (?, ?, ?, ?, ?)
                """,
                (
                    event.get("ts") or int(time.time() * 1000),
                    event.get("task_name", "build"),
                    event.get("duration_ms", 0),
                    event.get("outcome", "success"),
                    1 if event.get("saved_distraction", True) else 0,
                ),
            )
            conn.commit()

    def get_compile_shield_saves_count(self, since_ts: Optional[int] = None) -> int:
        with self.get_connection() as conn:
            cur = conn.cursor()
            if since_ts:
                cur.execute("SELECT COUNT(*) AS saves FROM compile_shield_events WHERE ts >= ? AND saved_distraction = 1", (since_ts,))
            else:
                cur.execute("SELECT COUNT(*) AS saves FROM compile_shield_events WHERE saved_distraction = 1")
            row = cur.fetchone()
            return int(row["saves"]) if row and row["saves"] else 0

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

    def record_file_attention_log(self, log: Dict[str, Any]) -> None:
        with self.get_connection() as conn:
            conn.execute(
                """
                INSERT INTO file_attention_logs (
                    file_path, workspace_name, logged_at_ts, is_fatigued_edit,
                    interruption_count, thrash_events_count, context_note
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    log["file_path"],
                    log.get("workspace_name", ""),
                    log.get("logged_at_ts") or int(time.time() * 1000),
                    1 if log.get("is_fatigued_edit") else 0,
                    log.get("interruption_count", 0),
                    log.get("thrash_events_count", 0),
                    log.get("context_note", ""),
                ),
            )
            conn.commit()

    def get_pre_commit_audit(self, workspace_name: Optional[str] = None, hours: int = 24) -> Dict[str, Any]:
        cutoff_ts = int(time.time() * 1000) - (hours * 3600 * 1000)
        with self.get_connection() as conn:
            cur = conn.cursor()
            query = """
                SELECT
                    file_path,
                    MAX(is_fatigued_edit) AS was_fatigued,
                    SUM(interruption_count) AS total_interruptions,
                    SUM(thrash_events_count) AS total_thrash,
                    MAX(logged_at_ts) AS last_edit_ts,
                    COUNT(*) AS edit_sessions
                FROM file_attention_logs
                WHERE logged_at_ts >= ?
            """
            params: List[Any] = [cutoff_ts]
            if workspace_name:
                query += " AND workspace_name = ?"
                params.append(workspace_name)
            query += " GROUP BY file_path ORDER BY was_fatigued DESC, total_interruptions DESC"

            cur.execute(query, params)
            rows = cur.fetchall()

            flagged_files = []
            for r in rows:
                is_fatigued = bool(r["was_fatigued"])
                interruptions = int(r["total_interruptions"] or 0)
                thrash = int(r["total_thrash"] or 0)
                if is_fatigued or interruptions >= 3 or thrash >= 1:
                    fname = Path(r["file_path"]).name
                    reasons = []
                    if is_fatigued:
                        reasons.append("edited past bedtime / low sleep")
                    if interruptions > 0:
                        reasons.append(f"{interruptions} interruptions")
                    if thrash > 0:
                        reasons.append(f"{thrash} thrash bursts")
                    reason_str = " · ".join(reasons)
                    flagged_files.append({
                        "filePath": r["file_path"],
                        "fileName": fname,
                        "isFatigued": is_fatigued,
                        "interruptions": interruptions,
                        "thrashCount": thrash,
                        "lastEditTs": r["last_edit_ts"],
                        "warningMessage": f"Sanity Check: {fname} was {reason_str} — review lines before committing.",
                    })

            has_high_risk = len(flagged_files) > 0
            if has_high_risk:
                summary = f"{len(flagged_files)} file(s) edited under high cognitive fatigue or fragmentation."
            else:
                summary = "All recent edits made under calm, focused conditions."

            return {
                "flaggedFiles": flagged_files,
                "hasHighRisk": has_high_risk,
                "summary": summary,
            }

    def record_shared_desk_item(self, item: Dict[str, Any]) -> None:
        with self.get_connection() as conn:
            conn.execute(
                """
                INSERT OR REPLACE INTO shared_desk_items (
                    id, created_at_ts, item_type, content, url, title, note, consumed
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    item["id"],
                    item.get("created_at_ts") or int(time.time() * 1000),
                    item.get("item_type", "note"),
                    item.get("content", ""),
                    item.get("url", ""),
                    item.get("title", ""),
                    item.get("note", ""),
                    1 if item.get("consumed") else 0,
                ),
            )
            conn.commit()

    def get_shared_desk_items(self, unconsumed_only: bool = True) -> List[Dict[str, Any]]:
        with self.get_connection() as conn:
            cur = conn.cursor()
            if unconsumed_only:
                cur.execute("SELECT * FROM shared_desk_items WHERE consumed = 0 ORDER BY created_at_ts DESC")
            else:
                cur.execute("SELECT * FROM shared_desk_items ORDER BY created_at_ts DESC")
            return [dict(r) for r in cur.fetchall()]

    def consume_shared_desk_item(self, item_id: str) -> None:
        with self.get_connection() as conn:
            conn.execute("UPDATE shared_desk_items SET consumed = 1 WHERE id = ?", (item_id,))
            conn.commit()

    def record_walk_away_event(self, event: Dict[str, Any]) -> None:
        with self.get_connection() as conn:
            conn.execute(
                """
                INSERT INTO walk_away_events (
                    ts, state, reason, duration_seconds
                ) VALUES (?, ?, ?, ?)
                """,
                (
                    event.get("ts") or int(time.time() * 1000),
                    event.get("state", "AWAY"),
                    event.get("reason", ""),
                    event.get("duration_seconds", 0),
                ),
            )
            conn.commit()

    def prune_stale_telemetry(self, retention_days: int = 90) -> Dict[str, int]:
        """Prunes raw telemetry older than retention_days while preserving summary streaks and ML weights."""
        cutoff_ts = int((time.time() - retention_days * 86400) * 1000)
        pruned: Dict[str, int] = {}
        with self.get_connection() as conn:
            for table, col in [
                ("real_activity_buckets", "bucket_start_ts"),
                ("file_attention_logs", "logged_at_ts"),
                ("events", "ts"),
                ("input_summary", "window_end"),
                ("notifications", "post_time"),
                ("compile_shield_events", "ts"),
                ("walk_away_events", "ts"),
            ]:
                try:
                    cur = conn.cursor()
                    cur.execute(f"DELETE FROM {table} WHERE {col} < ?", (cutoff_ts,))
                    pruned[table] = cur.rowcount
                except Exception:
                    pruned[table] = 0
            conn.commit()
        return pruned


_db_instance: Optional[Database] = None


def get_db(db_path: Optional[Path] = None) -> Database:
    global _db_instance
    if _db_instance is None or (db_path and _db_instance.db_path != db_path):
        _db_instance = Database(db_path)
    return _db_instance
