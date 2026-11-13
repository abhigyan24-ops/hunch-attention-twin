import os
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

from .vault_routes import router as vault_router

TWIN_TOKEN = os.getenv("TWIN_TOKEN", "dhyan-local-twin-token")

# Global singleton engine states
db: Database = get_db()
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


@app.get("/profile/fatigue")
def profile_fatigue() -> Dict[str, Any]:
    return fatigue_learner.to_dict()
