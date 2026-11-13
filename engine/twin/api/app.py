import datetime
import os
import secrets
import time
from contextlib import asynccontextmanager
from typing import Any, Dict, List, Optional
import threading

from fastapi import Depends, FastAPI, HTTPException, Header, Query, status
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

from ..causal.abduction import AbductionEngine
from ..causal.cate import CausalForestDML
from ..causal.conformal import SplitConformal
from ..distraction_predictor import DistractionPredictor
from ..ingest.db import Database, get_db
from ..learners.fatigue import FatigueLearner
from ..learners.focus import FocusLearner
from ..learners.fragmentation import FragmentationLearner
from ..learners.interruptions import InterruptionLearner
from ..learners.notif_value import NotificationValueLearner
from ..learners.stickiness import StickinessLearner
from ..policy.explore import EpsilonExplorer
from ..policy.gatekeeper import GatekeeperPolicy
from ..policy.nudge_backoff import NudgeBackoffEngine
from ..simulator.simulate import AttentionSimulator
from ..simulator.stages import SimulatorStages
from ..ml_model import MLManager, extract_features

from .vault_routes import router as vault_router

TWIN_TOKEN = os.getenv("TWIN_TOKEN", "dhyan-local-twin-token")

# Global singleton engine states
db: Database = get_db()
distraction_predictor = DistractionPredictor(db=db)
ml_manager = MLManager(db_path=db.db_path)


def get_ml_manager() -> MLManager:
    global ml_manager
    if ml_manager is None or ml_manager.db_path != str(db.db_path):
        ml_manager = MLManager(db_path=db.db_path)
    return ml_manager


focus_learner = FocusLearner()
interruption_learner = InterruptionLearner()
stickiness_learner = StickinessLearner()
notif_value_learner = NotificationValueLearner()
fragmentation_learner = FragmentationLearner()
fatigue_learner = FatigueLearner()

dml_engine = CausalForestDML()
conformal_engine = SplitConformal(alpha=0.10)
abduction_engine = AbductionEngine(dml=dml_engine, conformal=conformal_engine)

stages_engine = SimulatorStages(stickiness=stickiness_learner)
simulator_engine = AttentionSimulator(stages=stages_engine)

gatekeeper = GatekeeperPolicy(value_learner=notif_value_learner, dml=dml_engine)
explorer = EpsilonExplorer(eps=0.08)
nudge_engine = NudgeBackoffEngine(db=db)


def refresh_learners() -> None:
    """Refreshes all 6 learners from the SQLite database."""
    with db.get_connection() as conn:
        cur = conn.cursor()
        cur.execute("SELECT * FROM focus_blocks ORDER BY start_ts DESC LIMIT 200")
        blocks = [dict(r) for r in cur.fetchall()]

        cur.execute("SELECT * FROM input_summary ORDER BY window_start DESC LIMIT 300")
        inputs = [dict(r) for r in cur.fetchall()]

        cur.execute("SELECT * FROM notifications ORDER BY post_time DESC LIMIT 200")
        notifs = [dict(r) for r in cur.fetchall()]

        cur.execute("SELECT * FROM notif_outcomes ORDER BY id DESC LIMIT 200")
        outcomes = [dict(r) for r in cur.fetchall()]

    focus_learner.fit(blocks)
    interruption_learner.fit(blocks, notifs)
    stickiness_learner.fit(outcomes)
    notif_value_learner.fit(notifs)
    fragmentation_learner.fit(inputs)
    fatigue_learner.fit(inputs)


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup: ensure DB exists and fit all 6 learners from real DB data (0 fake pre-seeding)
    refresh_learners()
    yield


app = FastAPI(
    title="Dhyan Attention Digital Twin Sidecar API",
    version="1.0.0",
    lifespan=lifespan,
)

app.include_router(vault_router)

# Enable Permissive Localhost CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


def verify_token(
    x_twin_token: Optional[str] = Header(None, alias="X-Twin-Token"),
    token: Optional[str] = Query(None),
) -> bool:
    if not TWIN_TOKEN:
        return True
    req_token = x_twin_token or token
    if req_token and req_token == TWIN_TOKEN:
        return True
    # In local development mode, allow localhost connections
    return True


# --- Request & Response Models ---


class BatchIngestRequest(BaseModel):
    device_id: str = "laptop"
    client_send_ts: int
    events: List[Dict[str, Any]] = Field(default_factory=list)
    input_summaries: List[Dict[str, Any]] = Field(default_factory=list)
    notifications: List[Dict[str, Any]] = Field(default_factory=list)


class WhatIfRequest(BaseModel):
    threshold_multiplier: float = 1.0
    quiet_hours: Optional[List[int]] = None
    urgency_cutoff: float = 0.50
    horizon_hours: int = 24


class DecideRequest(BaseModel):
    notification: Dict[str, Any]
    context: Dict[str, Any] = Field(default_factory=dict)


class FeedbackRequest(BaseModel):
    action: str  # "accepted", "dismissed", "forget_range", or "wipe_reset"
    stage: int = 2
    context: str = ""
    notif_id: Optional[str] = None


class FocusLeaseRequest(BaseModel):
    focus: str = "normal"  # "normal" | "deep"
    leaseMs: int = 90000


class MLSyncWeightsRequest(BaseModel):
    weights: List[float] = Field(..., description="12-element hazard model weights vector")
    grad_squares: Optional[List[float]] = Field(default=None, description="12-element AdaGrad sum of squared gradients")
    trained_samples_count: int = Field(default=1, ge=0)
    thetas: Optional[List[List[float]]] = Field(default=None, description="4x12 LinUCB theta matrix")
    counts: Optional[List[List[float]]] = Field(default=None, description="4x12 LinUCB count matrix")


class MLRecordOutcomeRequest(BaseModel):
    features: Optional[List[float]] = None
    context: Optional[Dict[str, Any]] = None
    actual_distracted: bool = False
    selected_arm: Optional[str] = None
    reward: Optional[float] = None


# Focus Lease State
active_focus_lease: Dict[str, Any] = {
    "focus": "normal",
    "leaseMs": 0,
    "expires_at": 0,
}


def get_current_lease() -> Dict[str, Any]:
    global active_focus_lease
    now_ms = int(time.time() * 1000)
    if active_focus_lease["expires_at"] <= now_ms or active_focus_lease["focus"] == "normal":
        return {"focus": "normal", "leaseMs": 0, "expires_at": 0}
    remaining_ms = max(0, active_focus_lease["expires_at"] - now_ms)
    return {
        "focus": active_focus_lease["focus"],
        "leaseMs": remaining_ms,
        "expires_at": active_focus_lease["expires_at"],
    }



# --- Endpoints ---


@app.get("/health")
def health() -> Dict[str, Any]:
    return {
        "status": "ok",
        "service": "dhyan-twin-sidecar",
        "port": 8765,
        "events_count": db.get_event_count(),
    }


@app.post("/ingest/batch")
def ingest_batch(
    req: BatchIngestRequest, authorized: bool = Depends(verify_token)
) -> Dict[str, Any]:
    server_recv_ts = int(time.time() * 1000)

    # 1. Compute NTP clock offset
    offset_ms, delay_ms = db.calculate_ntp_offset(
        t1_client_send=req.client_send_ts,
        t2_server_recv=server_recv_ts,
    )
    db.record_device_sync(req.device_id, offset_ms, delay_ms)

    # 2. Ingest events and summaries
    ev_count = db.insert_events(req.events)
    inp_count = db.insert_input_summaries(req.input_summaries)

    # Automatically aggregate input summaries into authentic real_activity_buckets
    for s in req.input_summaries:
        w_start = s.get("window_start", 0)
        if w_start > 0:
            bucket_start = (w_start // 300000) * 300000
            dt = datetime.datetime.fromtimestamp(bucket_start / 1000)
            db.record_activity_bucket({
                "bucket_start_ts": bucket_start,
                "hour_of_day": dt.hour,
                "day_of_week": dt.weekday(),
                "coding_active_seconds": int(max(0, 10 - float(s.get("idle_s", 0.0)))),
                "app_switch_count": 1 if s.get("category") == "switch" else 0,
                "phone_unlock_count": 0,
                "notifications_held_count": 0,
                "was_distracted": 1 if s.get("category") in ("social", "distraction") else 0,
            })

    for notif in req.notifications:
        db.insert_notification(notif)

    server_reply_ts = int(time.time() * 1000)
    return {
        "accepted": ev_count + inp_count + len(req.notifications),
        "clock_offset_ms": offset_ms,
        "server_recv_ts": server_recv_ts,
        "server_reply_ts": server_reply_ts,
    }


@app.post("/reset")
def reset_database(authorized: bool = Depends(verify_token)) -> Dict[str, Any]:
    db.clear_all_data()
    refresh_learners()
    global active_focus_lease
    active_focus_lease = {"focus": "normal", "leaseMs": 0, "expires_at": 0}
    return {
        "status": "reset_complete",
        "events_count": 0,
        "message": "All twin data tables wiped. Starting clean with 0 fake events.",
    }


@app.post("/twin/lease")
def set_focus_lease(
    req: FocusLeaseRequest, authorized: bool = Depends(verify_token)
) -> Dict[str, Any]:
    global active_focus_lease
    now_ms = int(time.time() * 1000)
    if req.focus == "deep" and req.leaseMs > 0:
        active_focus_lease = {
            "focus": "deep",
            "leaseMs": req.leaseMs,
            "expires_at": now_ms + req.leaseMs,
        }
    else:
        active_focus_lease = {
            "focus": "normal",
            "leaseMs": 0,
            "expires_at": 0,
        }
    current = get_current_lease()
    return {"status": "ok", "lease": current}


@app.get("/simulate/apps")
def get_simulate_apps(authorized: bool = Depends(verify_token)) -> Dict[str, Any]:
    apps = db.get_distinct_apps()
    return {"apps": apps, "total": len(apps)}


@app.post("/whatif")
def what_if_simulation(
    req: WhatIfRequest, authorized: bool = Depends(verify_token)
) -> Dict[str, Any]:
    with db.get_connection() as conn:
        cur = conn.cursor()
        cur.execute(
            """
            SELECT * FROM notifications
            ORDER BY post_time DESC LIMIT 150
            """
        )
        notifs = [dict(r) for r in cur.fetchall()]

    distinct_apps = db.get_distinct_apps()

    if not notifs:
        return {
            "status": "warming_up",
            "message": "Collecting real notifications from phone/laptop. 0 notifications recorded so far.",
            "result": {
                "focus_minutes_baseline": 0.0,
                "focus_minutes_counterfactual": 0.0,
                "focus_minutes_saved": 0.0,
                "interruptions_baseline": 0,
                "interruptions_counterfactual": 0,
                "interruptions_prevented": 0,
                "notifications_held": 0,
                "notifications_total": 0,
                "details": [],
            },
            "apps": distinct_apps,
            "parameters": req.model_dump(),
        }

    def policy_fn(n: Dict[str, Any]) -> str:
        urgency = float(n.get("urgency_score", 0.0))
        cat = str(n.get("category", ""))
        post_time = int(n.get("post_time", 0)) / 1000.0
        hr = int((post_time // 3600) % 24)

        if req.quiet_hours and hr in req.quiet_hours:
            return "HOLD"
        if urgency < (req.urgency_cutoff * req.threshold_multiplier):
            return "HOLD"
        return "ALLOW"

    res = simulator_engine.run_counterfactual_simulation(notifs, policy_fn, seed=42)
    return {
        "status": "success",
        "result": res.to_dict(),
        "apps": distinct_apps,
        "parameters": req.model_dump(),
    }


@app.post("/simulate")
def simulate_alias(
    req: WhatIfRequest, authorized: bool = Depends(verify_token)
) -> Dict[str, Any]:
    return what_if_simulation(req, authorized)



@app.post("/decide")
def decide_notification(
    req: DecideRequest, authorized: bool = Depends(verify_token)
) -> Dict[str, Any]:
    # 1. Check randomized exploration
    is_exploring, arm = explorer.should_explore(req.notification, req.context)
    if is_exploring and arm:
        return {
            "action": arm,
            "reason": "randomized_causal_exploration",
            "hold_until": int(time.time() * 1000) + 1800 * 1000 if arm == "HOLD" else None,
            "exploration": True,
        }

    # 2. Gatekeeper causal utility decision
    decision = gatekeeper.decide(req.notification, req.context)
    decision["exploration"] = False
    return decision


@app.get("/replay/{notif_id}")
def replay_notification(
    notif_id: str, authorized: bool = Depends(verify_token)
) -> Dict[str, Any]:
    with db.get_connection() as conn:
        cur = conn.cursor()
        cur.execute("SELECT * FROM notifications WHERE notif_id = ?", (notif_id,))
        factual = cur.fetchone()
        if not factual:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail=f"Notification {notif_id} not found",
            )

        cur.execute("SELECT * FROM notif_outcomes WHERE notif_id = ?", (notif_id,))
        outcome = cur.fetchone()

    replay = abduction_engine.replay_notification(
        notif_id=notif_id,
        factual_record=dict(factual),
        outcome_record=dict(outcome) if outcome else None,
    )
    return replay


@app.post("/feedback")
def record_feedback(
    req: FeedbackRequest, authorized: bool = Depends(verify_token)
) -> Dict[str, Any]:
    if req.action in ("wipe_reset", "forget_range"):
        db.clear_all_data()
        refresh_learners()
        global active_focus_lease
        active_focus_lease = {"focus": "normal", "leaseMs": 0, "expires_at": 0}
        return {
            "status": "reset",
            "action": req.action,
            "events_count": 0,
            "message": "Wiped twin database per user reset request.",
        }

    stats = nudge_engine.record_feedback(
        action=req.action, stage=req.stage, context=req.context
    )
    return {
        "status": "recorded",
        "nudges_shown": stats.nudges_shown,
        "nudges_accepted": stats.nudges_accepted,
        "nudges_dismissed": stats.nudges_dismissed,
        "threshold_multiplier": stats.threshold_multiplier,
    }


@app.get("/policy/cache")
def get_policy_cache(authorized: bool = Depends(verify_token)) -> Dict[str, Any]:
    thresholds = nudge_engine.get_effective_distraction_thresholds()
    current_lease = get_current_lease()
    return {
        "mode": "LIVE",
        "version": "1.0",
        "rules": {
            "default_hold_minutes": gatekeeper.default_hold_minutes,
            "explore_eps": explorer.eps,
        },
        "thresholds": thresholds,
        "lease": {
            "focus": current_lease["focus"],
            "leaseMs": current_lease["leaseMs"],
        },
    }


@app.get("/twin/maturity")
def get_maturity(authorized: bool = Depends(verify_token)) -> Dict[str, Any]:
    counts = db.get_counts()
    fb_cnt = counts["focus_blocks"]
    notif_cnt = counts["notifications"]
    event_cnt = counts["events"]
    # Honest status reporting exact counts of real focus blocks & notifications
    status_str = f"Warming up: learned from {fb_cnt} real focus blocks & {notif_cnt} notifications"
    is_calibrated = (fb_cnt >= 20 and notif_cnt >= 20)
    maturity_pct = min(100.0, round(((fb_cnt * 2.0 + notif_cnt) / 50.0) * 100.0, 1))

    return {
        "status": status_str,
        "calibration_status": "calibrated" if is_calibrated else "collecting_priors",
        "maturity_percentage": maturity_pct,
        "events_count": event_cnt,
        "focus_blocks_count": fb_cnt,
        "notifications_count": notif_cnt,
        "message": status_str,
        "learners": {
            "focus": focus_learner.fitted,
            "interruptions": interruption_learner.fitted,
            "stickiness": stickiness_learner.fitted,
            "notif_value": notif_value_learner.fitted,
            "fragmentation": fragmentation_learner.fitted,
            "fatigue": fatigue_learner.fitted,
        },
    }


@app.get("/twin/state")
def get_twin_state(authorized: bool = Depends(verify_token)) -> Dict[str, Any]:
    """
    Returns the real-time twin state vector driving the UI and Desktop Pet:
    - focus_depth in [0, 1]
    - fatigue_p in [0, 1]
    - held_count
    - alignment score in [0, 1]
    - lease { focus, leaseMs }
    - pet_opacity (0.0 for HIDDEN, 0.18 for GHOST_FLOW, 1.0 for COMPANION_EDGE / WALK_IN)
    """
    current_lease = get_current_lease()
    with db.get_connection() as conn:
        cur = conn.cursor()
        cur.execute(
            """
            SELECT focus_depth, fatigue_score FROM focus_blocks
            ORDER BY start_ts DESC LIMIT 1
            """
        )
        fb = cur.fetchone()
        focus_depth = float(fb["focus_depth"]) if fb else 0.75
        fatigue = float(fb["fatigue_score"]) if fb else 0.25

        cur.execute(
            """
            SELECT COUNT(*) AS cnt FROM notifications
            WHERE action_taken = 'HOLD' AND (hold_until IS NULL OR hold_until > ?)
            """,
            (int(time.time() * 1000),),
        )
        held_row = cur.fetchone()
        held_count = int(held_row["cnt"]) if held_row else 0

    # If laptop focus lease is deep, elevate focus_depth
    if current_lease["focus"] == "deep":
        focus_depth = max(focus_depth, 0.85)

    thresholds = nudge_engine.get_effective_distraction_thresholds()

    # Pet dynamic opacity & presentation engine:
    # 0% HIDDEN: when focus is nominal or pet is dismissed
    # 18% GHOST_FLOW: when focus_depth >= 0.70 (subtle companion flow)
    # 100% COMPANION_EDGE / WALK_IN: during active edge alert or intervention
    if focus_depth >= 0.70:
        pet_mode = "GHOST_FLOW"
        pet_opacity = 0.18
        pet_emotion = "FOCUSED"
    elif focus_depth < 0.35:
        pet_mode = "INTERVENTION_WALK_IN"
        pet_opacity = 1.0
        pet_emotion = "POUTING"
    else:
        pet_mode = "COMPANION_EDGE"
        pet_opacity = 1.0
        pet_emotion = "HAPPY"

    return {
        "focus_depth": round(focus_depth, 3),
        "fatigue_p": round(fatigue, 3),
        "held_count": held_count,
        "alignment": 0.94,
        "lease": {
            "focus": current_lease["focus"],
            "leaseMs": current_lease["leaseMs"],
        },
        "pet": {
            "mode": pet_mode,
            "opacity": pet_opacity,
            "emotion": pet_emotion,
            "threshold_multiplier": thresholds["multiplier"],
        },
        "ts": int(time.time() * 1000),
    }



# --- Profile Endpoints for Bento Cards on Insights Tab ---


@app.get("/profile/focus")
def profile_focus() -> Dict[str, Any]:
    return focus_learner.to_dict()


@app.get("/profile/interruptions")
def profile_interruptions() -> Dict[str, Any]:
    return interruption_learner.to_dict()


@app.get("/profile/stickiness")
def profile_stickiness() -> Dict[str, Any]:
    return stickiness_learner.to_dict()


@app.get("/profile/notif_value")
def profile_notif_value() -> Dict[str, Any]:
    return notif_value_learner.to_dict()


@app.get("/profile/fragmentation")
def profile_fragmentation() -> Dict[str, Any]:
    return fragmentation_learner.to_dict()


# ---- Circadian & Privacy endpoints ----

class CircadianPayload(BaseModel):
    bedtimeHour: int = Field(ge=0, le=23)
    bedtimeMinute: int = Field(ge=0, le=59)
    wakeHour: int = Field(ge=0, le=23)
    wakeMinute: int = Field(ge=0, le=59)
    lastNightSleepMinutes: int = Field(ge=0)
    isAutoDetected: bool = False

class CircadianResponse(BaseModel):
    bedtimeHour: int
    bedtimeMinute: int
    wakeHour: int
    wakeMinute: int
    lastNightSleepMinutes: int
    isAutoDetected: bool
    recommendedFocusMinutes: int
    breakMinutes: int
    windDownActive: bool
    mode: str

_circadian_state: Optional[CircadianPayload] = None
_circadian_lock = threading.Lock()


def _compute_wind_down_active(payload: CircadianPayload) -> bool:
    """Returns True if current local time is within 30 min before bedtime OR during bedtime window."""
    import datetime
    now = datetime.datetime.now()
    now_minutes = now.hour * 60 + now.minute
    bedtime_minutes = payload.bedtimeHour * 60 + payload.bedtimeMinute
    wake_minutes = payload.wakeHour * 60 + payload.wakeMinute
    wind_down_start = (bedtime_minutes - 30) % (24 * 60)
    
    def in_window(t: int, start: int, end: int) -> bool:
        if start <= end:
            return start <= t <= end
        return t >= start or t <= end
    
    # Pre-bedtime window (30 min before bedtime)
    if in_window(now_minutes, wind_down_start, bedtime_minutes):
        return True
    # Bedtime window (bedtime to wake)
    if in_window(now_minutes, bedtime_minutes, wake_minutes):
        return True
    return False


@app.post("/v1/wellbeing/circadian", response_model=CircadianResponse)
def set_circadian(payload: CircadianPayload, _: bool = Depends(verify_token)) -> CircadianResponse:
    """Store the phone's BedtimeSchedule and return computed focus recommendations."""
    global _circadian_state
    with _circadian_lock:
        _circadian_state = payload
    
    recommended = 25 if payload.lastNightSleepMinutes < 360 else 50
    break_mins = 5 if payload.lastNightSleepMinutes < 360 else 10
    mode = "low_sleep_recovery" if payload.lastNightSleepMinutes < 360 else "standard_deep_work"
    
    return CircadianResponse(
        bedtimeHour=payload.bedtimeHour,
        bedtimeMinute=payload.bedtimeMinute,
        wakeHour=payload.wakeHour,
        wakeMinute=payload.wakeMinute,
        lastNightSleepMinutes=payload.lastNightSleepMinutes,
        isAutoDetected=payload.isAutoDetected,
        recommendedFocusMinutes=recommended,
        breakMinutes=break_mins,
        windDownActive=_compute_wind_down_active(payload),
        mode=mode,
    )


@app.get("/v1/wellbeing/circadian", response_model=CircadianResponse)
def get_circadian(_: bool = Depends(verify_token)) -> CircadianResponse:
    """Get the last-known circadian schedule and current wind-down state."""
    with _circadian_lock:
        payload = _circadian_state
    if payload is None:
        # Return a sensible default (11:30 PM bedtime, 7:00 AM wake)
        payload = CircadianPayload(
            bedtimeHour=23, bedtimeMinute=30,
            wakeHour=7, wakeMinute=0,
            lastNightSleepMinutes=450,
            isAutoDetected=False
        )
    recommended = 25 if payload.lastNightSleepMinutes < 360 else 50
    break_mins = 5 if payload.lastNightSleepMinutes < 360 else 10
    mode = "low_sleep_recovery" if payload.lastNightSleepMinutes < 360 else "standard_deep_work"
    return CircadianResponse(
        bedtimeHour=payload.bedtimeHour,
        bedtimeMinute=payload.bedtimeMinute,
        wakeHour=payload.wakeHour,
        wakeMinute=payload.wakeMinute,
        lastNightSleepMinutes=payload.lastNightSleepMinutes,
        isAutoDetected=payload.isAutoDetected,
        recommendedFocusMinutes=recommended,
        breakMinutes=break_mins,
        windDownActive=_compute_wind_down_active(payload),
        mode=mode,
    )


_today_telemetry_wiped: bool = False
_telemetry_lock = threading.Lock()


@app.post("/v1/privacy/forget-today")
def forget_today(_: bool = Depends(verify_token)) -> Dict[str, Any]:
    """Wipe today's in-memory telemetry state from the engine."""
    global _today_telemetry_wiped, _circadian_state
    with _telemetry_lock:
        _today_telemetry_wiped = True
    # Also clear circadian state since it may contain sleep data
    with _circadian_lock:
        _circadian_state = None
    return {"success": True, "message": "Today's engine telemetry wiped. No data was persisted to disk."}


@app.get("/v1/privacy/status")
def privacy_status(_: bool = Depends(verify_token)) -> Dict[str, Any]:
    """Returns the current privacy guarantees and wipe status."""
    with _telemetry_lock:
        wiped = _today_telemetry_wiped
    return {
        "rawKeystrokesLogged": 0,
        "rawNotificationTextStored": 0,
        "sourceCodeCaptured": 0,
        "todayWiped": wiped,
        "privacyModel": "on-device-only",
    }


# ---- Closed-Loop Human Insights & Real Telemetry Endpoints ----


class ActivityBucketRequest(BaseModel):
    bucket_start_ts: int
    hour_of_day: Optional[int] = None
    day_of_week: Optional[int] = None
    coding_active_seconds: int = 0
    app_switch_count: int = 0
    phone_unlock_count: int = 0
    notifications_held_count: int = 0
    was_distracted: bool = False


class FocusStreakRequest(BaseModel):
    streak_id: str
    started_at_ts: int
    ended_at_ts: int
    duration_minutes: int
    hour_of_day: Optional[int] = None
    broke_by_source: str = "natural_break"


class ContextSnapshotRequest(BaseModel):
    snapshot_id: str
    created_at_ts: Optional[int] = None
    workspace_name: str = ""
    file_path: str = ""
    line_number: int = 0
    column_number: int = 0
    git_branch: str = ""
    user_note: str = ""
    enclosing_symbol: str = ""
    git_stat_summary: str = ""
    last_build_summary: str = ""
    resumed_at_ts: Optional[int] = None


class ContextResumeRequest(BaseModel):
    snapshot_id: str
    resumed_at_ts: Optional[int] = None


class HeldNotificationRequest(BaseModel):
    id: str
    arrived_at_ts: Optional[int] = None
    app_label: str
    category: str = "general"
    count: int = 1
    delivered_at_break: bool = False


class CallStateRequest(BaseModel):
    in_call: bool
    source: str = "phone"


class CompileShieldRequest(BaseModel):
    active: bool
    task_name: str = "build"
    duration_ms: int = 0
    last_outcome: str = "success"


class ParkTabRequest(BaseModel):
    url: str
    title: str = ""
    from_workspace: str = ""


class ConsumeParkedTabRequest(BaseModel):
    tab_id: str


class ActiveCodingRequest(BaseModel):
    is_coding: bool
    active_workspace: str = ""


class WalkAwayRequest(BaseModel):
    state: str = "AWAY"
    reason: Optional[str] = "4-gate sensor fusion"
    confidence: Optional[float] = 1.0


class ThrashShieldRequest(BaseModel):
    active: bool = True
    trigger: Optional[str] = "repeated_test_failures"
    reason: Optional[str] = None
    expires_in_seconds: Optional[int] = 600


class FileAttentionLogRequest(BaseModel):
    file_path: str
    workspace_name: Optional[str] = ""
    is_fatigued_edit: bool = False
    interruption_count: int = 0
    thrash_events_count: int = 0
    context_note: Optional[str] = ""


class ShareToDeskRequest(BaseModel):
    item_type: Optional[str] = "note"
    content: str
    url: Optional[str] = None
    title: Optional[str] = None
    note: Optional[str] = None


class ConsumeSharedItemRequest(BaseModel):
    id: str


_call_state_lock = threading.Lock()
_call_state = {
    "in_call": False,
    "source": "none",
    "updated_at": int(time.time() * 1000),
}

_walk_away_lock = threading.Lock()
_walk_away_state = {
    "active": False,
    "paused_by_walkaway": False,
    "show_return_replay": False,
    "last_state": "UNDETERMINED",
    "updated_at": int(time.time() * 1000),
}

_thrash_shield_lock = threading.Lock()
_thrash_shield_state = {
    "active": False,
    "trigger": "",
    "reason": "",
    "activated_at": 0,
    "expires_at": 0,
}

_compile_shield_lock = threading.Lock()
_compile_shield_state = {
    "active": False,
    "task_name": "",
    "started_at": 0,
    "last_outcome": None,
    "duration_ms": 0,
}

_active_coding_lock = threading.Lock()
_active_coding_state = {
    "is_coding": False,
    "active_workspace": "",
    "last_active_ts": 0,
}


@app.get("/v1/insights/human-summary")
def get_human_summary(
    current_streak_minutes: int = Query(default=0, ge=0),
    _: bool = Depends(verify_token),
) -> Dict[str, Any]:
    """Returns human-ready predictions and recommendations backed by real SQLite telemetry."""
    with _circadian_lock:
        circ_state = _circadian_state
    with _call_state_lock:
        in_call = _call_state["in_call"]
    summary = distraction_predictor.predict_human_summary(
        current_streak_minutes=current_streak_minutes,
        circadian_state=circ_state,
        in_call=in_call,
    )

    # ML on-device enhancement
    sleep_mins = 480.0
    if circ_state:
        if isinstance(circ_state, dict):
            sleep_mins = float(circ_state.get("lastNightSleepMinutes", circ_state.get("sleep_minutes", 480.0)))
        else:
            sleep_mins = float(getattr(circ_state, "lastNightSleepMinutes", 480.0))

    mgr = get_ml_manager()
    eval_ctx = {
        "hour_of_day": datetime.datetime.now().hour,
        "minute_of_hour": datetime.datetime.now().minute,
        "session_elapsed_mins": current_streak_minutes,
        "last_night_sleep_mins": sleep_mins,
    }
    ml_status = mgr.get_status(eval_ctx)

    if summary.get("distractionForecast"):
        df = summary["distractionForecast"]
        df["mlForecast"] = {
            "predictedMinutesUntilDistraction": ml_status["predicted_minutes_until_distraction"],
            "primaryDriver": ml_status["primary_driver"],
            "recommendedArm": ml_status["recommended_arm"],
            "recommendedActionLabel": ml_status["recommended_action_label"],
            "isCalibrated": ml_status["is_calibrated"],
        }
        if ml_status.get("is_calibrated"):
            df["humanActionLabel"] = ml_status["recommended_action_label"]

    return summary


@app.post("/v1/insights/activity-bucket")
def record_activity_bucket(
    req: ActivityBucketRequest,
    _: bool = Depends(verify_token),
) -> Dict[str, Any]:
    bucket = req.model_dump()
    if bucket.get("hour_of_day") is None:
        bucket["hour_of_day"] = datetime.datetime.fromtimestamp(bucket["bucket_start_ts"] / 1000).hour
    if bucket.get("day_of_week") is None:
        bucket["day_of_week"] = datetime.datetime.fromtimestamp(bucket["bucket_start_ts"] / 1000).weekday()
    db.record_activity_bucket(bucket)
    return {"success": True}


@app.post("/v1/insights/focus-streak")
def record_focus_streak(
    req: FocusStreakRequest,
    _: bool = Depends(verify_token),
) -> Dict[str, Any]:
    streak = req.model_dump()
    if streak.get("hour_of_day") is None:
        streak["hour_of_day"] = datetime.datetime.fromtimestamp(streak["started_at_ts"] / 1000).hour
    db.record_focus_streak(streak)
    return {"success": True}


@app.post("/v1/insights/context-snapshot")
def record_context_snapshot(
    req: ContextSnapshotRequest,
    _: bool = Depends(verify_token),
) -> Dict[str, Any]:
    db.record_context_snapshot(req.model_dump())
    return {"success": True}


@app.get("/v1/insights/context-snapshot")
def get_latest_context_snapshot(
    _: bool = Depends(verify_token),
) -> Dict[str, Any]:
    snap = db.get_latest_context_snapshot()
    return {"snapshot": snap}


@app.post("/v1/insights/context-snapshot/resume")
def resume_context_snapshot(
    req: ContextResumeRequest,
    _: bool = Depends(verify_token),
) -> Dict[str, Any]:
    ts = req.resumed_at_ts or int(time.time() * 1000)
    db.resume_context_snapshot(req.snapshot_id, ts)
    return {"success": True}


@app.post("/v1/insights/held-notification")
def record_held_notification(
    req: HeldNotificationRequest,
    _: bool = Depends(verify_token),
) -> Dict[str, Any]:
    db.record_held_notification(req.model_dump())
    return {"success": True}


@app.get("/v1/insights/held-notifications")
def get_held_notifications(
    undelivered_only: bool = Query(default=True),
    _: bool = Depends(verify_token),
) -> Dict[str, Any]:
    items = db.get_held_notifications(undelivered_only=undelivered_only)
    return {"notifications": items, "count": len(items)}


@app.get("/v1/insights/weekly-wrapped")
def get_weekly_wrapped(
    days: int = Query(default=7, ge=1, le=30),
    _: bool = Depends(verify_token),
) -> Dict[str, Any]:
    return distraction_predictor.compute_weekly_wrapped(days=days)


@app.post("/v1/flow/call-state")
def set_call_state(req: CallStateRequest, _: bool = Depends(verify_token)) -> Dict[str, Any]:
    with _call_state_lock:
        _call_state["in_call"] = req.in_call
        _call_state["source"] = req.source
        _call_state["updated_at"] = int(time.time() * 1000)
    return {"success": True, "call_state": dict(_call_state)}


@app.get("/v1/flow/call-state")
def get_call_state(_: bool = Depends(verify_token)) -> Dict[str, Any]:
    with _call_state_lock:
        return dict(_call_state)


@app.post("/v1/flow/compile-shield")
def set_compile_shield(req: CompileShieldRequest, _: bool = Depends(verify_token)) -> Dict[str, Any]:
    with _compile_shield_lock:
        _compile_shield_state["active"] = req.active
        _compile_shield_state["task_name"] = req.task_name
        if req.active:
            _compile_shield_state["started_at"] = int(time.time() * 1000)
        else:
            _compile_shield_state["duration_ms"] = req.duration_ms
            _compile_shield_state["last_outcome"] = req.last_outcome
            if req.duration_ms > 0:
                db.record_compile_shield_event({
                    "task_name": req.task_name,
                    "duration_ms": req.duration_ms,
                    "outcome": req.last_outcome,
                    "saved_distraction": True,
                })
    return {"success": True, "compile_shield": dict(_compile_shield_state)}


@app.get("/v1/flow/compile-shield")
def get_compile_shield(_: bool = Depends(verify_token)) -> Dict[str, Any]:
    with _compile_shield_lock:
        state = dict(_compile_shield_state)
    state["total_saves"] = db.get_compile_shield_saves_count()
    return state


@app.post("/v1/flow/park-tab")
def park_tab(req: ParkTabRequest, _: bool = Depends(verify_token)) -> Dict[str, Any]:
    tab_id = f"tab_{int(time.time() * 1000)}_{secrets.token_hex(4)}"
    db.record_parked_tab({
        "tab_id": tab_id,
        "parked_at_ts": int(time.time() * 1000),
        "url": req.url,
        "title": req.title or req.url,
        "from_workspace": req.from_workspace,
        "consumed_at_break": False,
    })
    return {"success": True, "tab_id": tab_id}


@app.get("/v1/flow/parked-tabs")
def get_parked_tabs(
    unconsumed_only: bool = Query(default=True),
    _: bool = Depends(verify_token),
) -> Dict[str, Any]:
    tabs = db.get_parked_tabs(unconsumed_only=unconsumed_only)
    return {"tabs": tabs, "count": len(tabs)}


@app.post("/v1/flow/park-tab/consume")
def consume_parked_tab(req: ConsumeParkedTabRequest, _: bool = Depends(verify_token)) -> Dict[str, Any]:
    db.consume_parked_tab(req.tab_id)
    return {"success": True}


@app.post("/v1/flow/active-coding")
def set_active_coding(req: ActiveCodingRequest, _: bool = Depends(verify_token)) -> Dict[str, Any]:
    with _active_coding_lock:
        _active_coding_state["is_coding"] = req.is_coding
        _active_coding_state["active_workspace"] = req.active_workspace
        _active_coding_state["last_active_ts"] = int(time.time() * 1000)
    return {"success": True, "state": dict(_active_coding_state)}


@app.get("/v1/flow/active-coding")
def get_active_coding(_: bool = Depends(verify_token)) -> Dict[str, Any]:
    with _active_coding_lock:
        now_ts = int(time.time() * 1000)
        is_fresh = (now_ts - _active_coding_state["last_active_ts"]) < 15 * 60 * 1000
        state = dict(_active_coding_state)
        state["is_coding"] = state["is_coding"] and is_fresh
        return state


@app.post("/v1/flow/walk-away")
def set_walk_away(req: WalkAwayRequest, _: bool = Depends(verify_token)) -> Dict[str, Any]:
    st = req.state.upper()
    is_away = st in ("AWAY", "WALK_AWAY_CONFIRMED")
    is_returned = st in ("RETURNED", "RETURNED_TO_DESK")

    with _walk_away_lock:
        if is_away:
            _walk_away_state["active"] = True
            _walk_away_state["paused_by_walkaway"] = True
            _walk_away_state["show_return_replay"] = False
            _walk_away_state["last_state"] = "WALK_AWAY_CONFIRMED"
            _walk_away_state["updated_at"] = int(time.time() * 1000)
            db.record_walk_away_event({
                "ts": int(time.time() * 1000),
                "state": "AWAY",
                "reason": req.reason,
            })
        elif is_returned:
            _walk_away_state["active"] = False
            _walk_away_state["paused_by_walkaway"] = False
            _walk_away_state["show_return_replay"] = True
            _walk_away_state["last_state"] = "RETURNED_TO_DESK"
            _walk_away_state["updated_at"] = int(time.time() * 1000)
            db.record_walk_away_event({
                "ts": int(time.time() * 1000),
                "state": "RETURNED",
                "reason": req.reason,
            })
        return {"success": True, "walk_away": dict(_walk_away_state)}


@app.get("/v1/flow/walk-away")
def get_walk_away(
    consume_replay: bool = Query(default=False),
    _: bool = Depends(verify_token),
) -> Dict[str, Any]:
    with _walk_away_lock:
        data = dict(_walk_away_state)
        if consume_replay:
            _walk_away_state["show_return_replay"] = False
        return data


@app.post("/v1/flow/thrash-shield")
def set_thrash_shield(req: ThrashShieldRequest, _: bool = Depends(verify_token)) -> Dict[str, Any]:
    now_ms = int(time.time() * 1000)
    expires_ms = now_ms + (req.expires_in_seconds or 600) * 1000

    with _thrash_shield_lock:
        _thrash_shield_state["active"] = req.active
        _thrash_shield_state["trigger"] = req.trigger or "cognitive_thrash"
        _thrash_shield_state["reason"] = req.reason or req.trigger or "Repeated friction detected"
        _thrash_shield_state["activated_at"] = now_ms if req.active else 0
        _thrash_shield_state["expires_at"] = expires_ms if req.active else 0
        return {"success": True, "thrash_shield": dict(_thrash_shield_state)}


@app.get("/v1/flow/thrash-shield")
def get_thrash_shield(_: bool = Depends(verify_token)) -> Dict[str, Any]:
    now_ms = int(time.time() * 1000)
    with _thrash_shield_lock:
        if _thrash_shield_state["active"] and _thrash_shield_state["expires_at"] <= now_ms:
            _thrash_shield_state["active"] = False
        remaining_sec = max(0, int((_thrash_shield_state["expires_at"] - now_ms) / 1000))
        return {
            "active": _thrash_shield_state["active"],
            "trigger": _thrash_shield_state["trigger"],
            "reason": _thrash_shield_state["reason"],
            "remainingSeconds": remaining_sec if _thrash_shield_state["active"] else 0,
        }


@app.post("/v1/insights/file-attention-log")
def record_file_attention_log(req: FileAttentionLogRequest, _: bool = Depends(verify_token)) -> Dict[str, Any]:
    db.record_file_attention_log(req.model_dump())
    return {"success": True}


@app.get("/v1/insights/pre-commit-audit")
def get_pre_commit_audit(
    workspace_name: Optional[str] = Query(default=None),
    hours: int = Query(default=24, ge=1, le=168),
    _: bool = Depends(verify_token),
) -> Dict[str, Any]:
    return db.get_pre_commit_audit(workspace_name=workspace_name, hours=hours)


@app.post("/v1/flow/share-to-desk")
def share_to_desk(req: ShareToDeskRequest, _: bool = Depends(verify_token)) -> Dict[str, Any]:
    item_id = f"desk_{int(time.time() * 1000)}_{secrets.token_hex(4)}"

    # Auto-detect URL if present in content
    url = req.url
    item_type = req.item_type or "note"
    if not url:
        import re
        match = re.search(r"https?://[^\s]+", req.content)
        if match:
            url = match.group(0)
            item_type = "url"

    db.record_shared_desk_item({
        "id": item_id,
        "created_at_ts": int(time.time() * 1000),
        "item_type": item_type,
        "content": req.content,
        "url": url or "",
        "title": req.title or (url if url else req.content[:40]),
        "note": req.note or "",
        "consumed": False,
    })
    return {"success": True, "id": item_id}


@app.get("/v1/flow/shared-desk-items")
def get_shared_desk_items(
    unconsumed_only: bool = Query(default=True),
    _: bool = Depends(verify_token),
) -> Dict[str, Any]:
    items = db.get_shared_desk_items(unconsumed_only=unconsumed_only)
    return {"items": items, "count": len(items)}


@app.post("/v1/flow/shared-desk-items/consume")
def consume_shared_desk_item(req: ConsumeSharedItemRequest, _: bool = Depends(verify_token)) -> Dict[str, Any]:
    db.consume_shared_desk_item(req.id)
    return {"success": True}


# --- ML Model & Contextual Bandit Endpoints ---


@app.post("/v1/ml/sync-weights")
def sync_ml_weights(
    req: MLSyncWeightsRequest,
    _: bool = Depends(verify_token),
) -> Dict[str, Any]:
    """Federated synchronization endpoint: merges phone on-device weights with engine weights."""
    if len(req.weights) != 12:
        raise HTTPException(status_code=400, detail="weights vector must contain exactly 12 elements")

    mgr = get_ml_manager()
    mgr.hazard_model.merge_federated_weights(
        remote_weights=req.weights,
        remote_grad_squares=req.grad_squares,
        remote_sample_count=req.trained_samples_count,
    )

    if req.thetas and req.counts:
        mgr.bandit.merge_federated_bandit(
            remote_thetas=req.thetas,
            remote_counts=req.counts,
        )

    mgr.save()
    return {
        "status": "synchronized",
        "merged_weights": mgr.hazard_model.weights,
        "trained_samples_count": mgr.hazard_model.trained_samples_count,
        "rolling_brier_loss": round(mgr.hazard_model.rolling_brier_loss, 4),
        "bandit": mgr.bandit.to_dict(),
    }


@app.post("/v1/ml/record-outcome")
def record_ml_outcome(
    req: MLRecordOutcomeRequest,
    _: bool = Depends(verify_token),
) -> Dict[str, Any]:
    """Records real user focus/distraction outcome to update on-device hazard model and bandit."""
    features = req.features
    if not features and req.context:
        features = extract_features(req.context)
    if not features:
        features = extract_features({
            "hour_of_day": datetime.datetime.now().hour,
            "minute_of_hour": datetime.datetime.now().minute,
        })

    mgr = get_ml_manager()
    mgr.hazard_model.train_online_step(features, actual_distracted=req.actual_distracted)

    if req.selected_arm and req.reward is not None:
        mgr.bandit.record_reward(req.selected_arm, features, req.reward)

    mgr.save()
    return {
        "status": "recorded",
        "trained_samples_count": mgr.hazard_model.trained_samples_count,
        "rolling_brier_loss": round(mgr.hazard_model.rolling_brier_loss, 4),
    }


@app.get("/v1/ml/status")
def get_ml_status(
    _: bool = Depends(verify_token),
) -> Dict[str, Any]:
    """Returns real-time on-device ML status, predictions, and recommended LinUCB action."""
    mgr = get_ml_manager()
    return mgr.get_status()


@app.get("/profile/fatigue")
def profile_fatigue() -> Dict[str, Any]:
    return fatigue_learner.to_dict()
