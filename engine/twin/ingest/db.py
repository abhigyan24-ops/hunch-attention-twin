import os
import sqlite3
import time
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

SCHEMA_PATH = Path(__file__).parent / "schema.sql"
DEFAULT_DB_PATH = Path(os.getenv("DHYAN_DB_PATH", "dhyan_twin.db"))


class Database:
    def __init__(self, db_path: Optional[Path] = None):
        self.db_path = Path(db_path) if db_path else DEFAULT_DB_PATH
        self.init_db()

    def get_connection(self) -> sqlite3.Connection:
        conn = sqlite3.connect(str(self.db_path), timeout=10.0, check_same_thread=False)
        conn.row_factory = sqlite3.Row
        conn.execute("PRAGMA journal_mode = WAL;")
        conn.execute("PRAGMA busy_timeout = 5000;")
        conn.execute("PRAGMA synchronous = NORMAL;")
        return conn

    def init_db(self) -> None:
        if self.db_path.parent and not self.db_path.parent.exists():
            self.db_path.parent.mkdir(parents=True, exist_ok=True)
        with self.get_connection() as conn:
            if SCHEMA_PATH.exists():
                with open(SCHEMA_PATH, "r", encoding="utf-8") as f:
                    conn.executescript(f.read())
            conn.commit()

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


_db_instance: Optional[Database] = None


def get_db(db_path: Optional[Path] = None) -> Database:
    global _db_instance
    if _db_instance is None or (db_path and _db_instance.db_path != db_path):
        _db_instance = Database(db_path)
    return _db_instance
