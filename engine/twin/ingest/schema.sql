-- Dhyan Attention Digital Twin SQLite Schema (WAL Mode)

PRAGMA journal_mode = WAL;
PRAGMA busy_timeout = 5000;
PRAGMA synchronous = NORMAL;

CREATE TABLE IF NOT EXISTS events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    device_id TEXT NOT NULL,
    ts INTEGER NOT NULL,
    event_type TEXT NOT NULL,
    payload TEXT NOT NULL,
    created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_events_ts ON events(ts);
CREATE INDEX IF NOT EXISTS idx_events_device ON events(device_id);

CREATE TABLE IF NOT EXISTS input_summary (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    device_id TEXT NOT NULL,
    window_start INTEGER NOT NULL,
    window_end INTEGER NOT NULL,
    keystrokes INTEGER DEFAULT 0,
    mouse_px REAL DEFAULT 0.0,
    idle_s REAL DEFAULT 0.0,
    active_app TEXT,
    category TEXT
);
CREATE INDEX IF NOT EXISTS idx_input_summary_window ON input_summary(window_start, window_end);

CREATE TABLE IF NOT EXISTS notifications (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    notif_id TEXT UNIQUE NOT NULL,
    device_id TEXT NOT NULL,
    post_time INTEGER NOT NULL,
    app_pkg TEXT NOT NULL,
    category TEXT NOT NULL,
    sender_hash TEXT,
    is_vip INTEGER DEFAULT 0,
    urgency_score REAL DEFAULT 0.0,
    action_taken TEXT NOT NULL,
    hold_until INTEGER
);
CREATE INDEX IF NOT EXISTS idx_notif_post_time ON notifications(post_time);

CREATE TABLE IF NOT EXISTS focus_blocks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    block_id TEXT UNIQUE NOT NULL,
    start_ts INTEGER NOT NULL,
    end_ts INTEGER NOT NULL,
    duration_s REAL NOT NULL,
    interruptions_count INTEGER DEFAULT 0,
    focus_depth REAL DEFAULT 0.5,
    primary_category TEXT,
    fatigue_score REAL DEFAULT 0.0
);
CREATE INDEX IF NOT EXISTS idx_focus_blocks_time ON focus_blocks(start_ts, end_ts);

CREATE TABLE IF NOT EXISTS notif_outcomes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    notif_id TEXT NOT NULL,
    shown INTEGER NOT NULL,
    excursion_s REAL DEFAULT 0.0,
    resume_s REAL DEFAULT 0.0,
    attended INTEGER DEFAULT 0,
    y_observed REAL DEFAULT 0.0,
    horizon_s REAL DEFAULT 3600.0,
    FOREIGN KEY(notif_id) REFERENCES notifications(notif_id)
);
CREATE INDEX IF NOT EXISTS idx_notif_outcomes_notif ON notif_outcomes(notif_id);

CREATE TABLE IF NOT EXISTS self_reports (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    ts INTEGER NOT NULL,
    focus_rating INTEGER,
    fatigue_rating INTEGER,
    notes TEXT
);

CREATE TABLE IF NOT EXISTS nudge_telemetry (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    ts INTEGER NOT NULL,
    nudges_shown INTEGER DEFAULT 0,
    nudges_accepted INTEGER DEFAULT 0,
    nudges_dismissed INTEGER DEFAULT 0,
    threshold_multiplier REAL DEFAULT 1.0,
    stage INTEGER DEFAULT 1,
    context TEXT
);

CREATE TABLE IF NOT EXISTS device_sync (
    device_id TEXT PRIMARY KEY,
    clock_offset_ms INTEGER DEFAULT 0,
    round_trip_ms INTEGER DEFAULT 0,
    last_sync_ts INTEGER NOT NULL
);
