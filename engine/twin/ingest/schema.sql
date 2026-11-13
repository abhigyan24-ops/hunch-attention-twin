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

-- Authentic Closed-Loop Telemetry Tables

CREATE TABLE IF NOT EXISTS real_activity_buckets (
    bucket_start_ts INTEGER PRIMARY KEY,
    hour_of_day INTEGER NOT NULL,
    day_of_week INTEGER NOT NULL,
    coding_active_seconds INTEGER DEFAULT 0,
    app_switch_count INTEGER DEFAULT 0,
    phone_unlock_count INTEGER DEFAULT 0,
    notifications_held_count INTEGER DEFAULT 0,
    was_distracted INTEGER DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_activity_buckets_hour ON real_activity_buckets(hour_of_day);

CREATE TABLE IF NOT EXISTS focus_streaks (
    streak_id TEXT PRIMARY KEY,
    started_at_ts INTEGER NOT NULL,
    ended_at_ts INTEGER NOT NULL,
    duration_minutes INTEGER NOT NULL,
    hour_of_day INTEGER NOT NULL,
    broke_by_source TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_focus_streaks_hour ON focus_streaks(hour_of_day);

CREATE TABLE IF NOT EXISTS context_snapshots (
    snapshot_id TEXT PRIMARY KEY,
    created_at_ts INTEGER NOT NULL,
    workspace_name TEXT,
    file_path TEXT,
    line_number INTEGER DEFAULT 0,
    column_number INTEGER DEFAULT 0,
    git_branch TEXT,
    user_note TEXT,
    enclosing_symbol TEXT,
    git_stat_summary TEXT,
    last_build_summary TEXT,
    resumed_at_ts INTEGER
);
CREATE INDEX IF NOT EXISTS idx_context_snapshots_ts ON context_snapshots(created_at_ts);

CREATE TABLE IF NOT EXISTS held_notifications (
    id TEXT PRIMARY KEY,
    arrived_at_ts INTEGER NOT NULL,
    app_label TEXT NOT NULL,
    category TEXT,
    count INTEGER DEFAULT 1,
    delivered_at_break INTEGER DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_held_notifs_ts ON held_notifications(arrived_at_ts);

CREATE TABLE IF NOT EXISTS parked_tabs (
    tab_id TEXT PRIMARY KEY,
    parked_at_ts INTEGER NOT NULL,
    url TEXT NOT NULL,
    title TEXT NOT NULL,
    from_workspace TEXT,
    consumed_at_break INTEGER DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_parked_tabs_ts ON parked_tabs(parked_at_ts);

CREATE TABLE IF NOT EXISTS compile_shield_events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    ts INTEGER NOT NULL,
    task_name TEXT NOT NULL,
    duration_ms INTEGER DEFAULT 0,
    outcome TEXT DEFAULT 'success',
    saved_distraction INTEGER DEFAULT 1
);
CREATE INDEX IF NOT EXISTS idx_compile_shield_ts ON compile_shield_events(ts);

CREATE TABLE IF NOT EXISTS file_attention_logs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    file_path TEXT NOT NULL,
    workspace_name TEXT,
    logged_at_ts INTEGER NOT NULL,
    is_fatigued_edit INTEGER DEFAULT 0,
    interruption_count INTEGER DEFAULT 0,
    thrash_events_count INTEGER DEFAULT 0,
    context_note TEXT
);
CREATE INDEX IF NOT EXISTS idx_file_attention_path ON file_attention_logs(file_path);

CREATE TABLE IF NOT EXISTS shared_desk_items (
    id TEXT PRIMARY KEY,
    created_at_ts INTEGER NOT NULL,
    item_type TEXT NOT NULL,
    content TEXT NOT NULL,
    url TEXT,
    title TEXT,
    note TEXT,
    consumed INTEGER DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_shared_desk_ts ON shared_desk_items(created_at_ts);

CREATE TABLE IF NOT EXISTS walk_away_events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    ts INTEGER NOT NULL,
    state TEXT NOT NULL,
    reason TEXT,
    duration_seconds INTEGER DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_walk_away_ts ON walk_away_events(ts);
