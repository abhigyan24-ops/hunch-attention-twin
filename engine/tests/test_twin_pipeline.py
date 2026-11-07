import os
import tempfile
import time
from pathlib import Path

import numpy as np
import pytest
from fastapi.testclient import TestClient

from engine.synth.persona import generate_dataset, get_persona, seed_db_if_needed
from engine.twin.api.app import app
from engine.twin.causal.abduction import AbductionEngine
from engine.twin.causal.cate import CausalForestDML
from engine.twin.causal.conformal import SplitConformal
from engine.twin.ingest.db import Database
from engine.twin.learners.fatigue import FatigueLearner
from engine.twin.learners.focus import FocusLearner
from engine.twin.learners.fragmentation import FragmentationLearner
from engine.twin.learners.interruptions import InterruptionLearner
from engine.twin.learners.notif_value import NotificationValueLearner
from engine.twin.learners.stickiness import StickinessLearner
from engine.twin.policy.explore import EpsilonExplorer
from engine.twin.policy.gatekeeper import GatekeeperPolicy
from engine.twin.policy.nudge_backoff import NudgeBackoffEngine
from engine.twin.sessionize.focus_blocks import extract_focus_blocks
from engine.twin.sessionize.outcomes import label_outcomes
from engine.twin.sessionize.timeline import TimelineSlice, build_timeline, parse_payload_safe
from engine.twin.simulator.simulate import AttentionSimulator
from engine.twin.simulator.stages import SimulatorStages


@pytest.fixture
def temp_db():
    with tempfile.NamedTemporaryFile(suffix=".db", delete=False) as f:
        db_path = Path(f.name)
    db = Database(db_path=db_path)
    yield db
    try:
        if db_path.exists():
            os.remove(db_path)
    except Exception:
        pass


def test_db_and_ntp_sync(temp_db):
    # Test NTP 4-timestamp handshake calculation
    t1 = 1000
    t2 = 1050
    t3 = 1060
    t4 = 1110
    offset, delay = temp_db.calculate_ntp_offset(t1, t2, t3, t4)
    # offset = ((1050-1000) + (1060-1110)) // 2 = (50 - 50) // 2 = 0
    # delay = (1110-1000) - (1060-1050) = 110 - 10 = 100
    assert offset == 0
    assert delay == 100

    # One-way handshake
    offset_1w, _ = temp_db.calculate_ntp_offset(1000, 1025)
    assert offset_1w == 25

    # Record sync
    temp_db.record_device_sync("phone_test", offset_1w, 50)
    stored_offset = temp_db.get_device_offset("phone_test")
    assert stored_offset == 25


def test_synth_persona_generation():
    persona = get_persona("Rahul")
    assert persona.name == "Rahul"
    assert persona.role == "developer"
    assert 10 in persona.peak_hours

    data = generate_dataset(persona, days=2, seed=123)
    assert len(data["events"]) > 0
    assert len(data["input_summaries"]) > 0
    assert len(data["notifications"]) > 0
    assert len(data["focus_blocks"]) > 0
    assert len(data["notif_outcomes"]) > 0
    assert len(data["nudge_telemetry"]) > 0


def test_sessionize_safe_parsing_and_equal_horizon():
    # Bug Fix 1: Safe JSON parsing test
    assert parse_payload_safe(None) == {}
    assert parse_payload_safe("") == {}
    assert parse_payload_safe('{"idle_s": 42.5}') == {"idle_s": 42.5}
    assert parse_payload_safe({"idle_s": 12.0}) == {"idle_s": 12.0}

    # Timeline slices
    events = [
        {"ts": 1000000, "payload": '{"idle_s": 10.0, "keystrokes": 150, "mouse_px": 500, "category": "work"}'},
        {"ts": 1060000, "payload": '{"idle_s": 5.0, "keystrokes": 180, "mouse_px": 600, "category": "work"}'},
    ]
    slices = build_timeline(events)
    assert len(slices) == 2
    assert slices[0].keystrokes == 150

    # Focus blocks
    blocks = extract_focus_blocks(slices, min_duration_s=60.0, max_gap_s=120.0)
    assert len(blocks) == 1
    assert blocks[0].focus_depth > 0.0

    # Outcomes equal-horizon counterfactual labeling
    notifs = [
        {"notif_id": "n1", "post_time": 1000000, "action_taken": "ALLOW"},
        {"notif_id": "n2", "post_time": 1000000, "action_taken": "HOLD"},
    ]
    outcomes = label_outcomes(notifs, slices, horizon_s=3600.0)
    assert len(outcomes) == 2
    assert outcomes[0].shown == 1
    assert outcomes[1].shown == 0
    assert outcomes[0].horizon_s == 3600.0
    assert outcomes[1].horizon_s == 3600.0


def test_cross_device_pickup_and_excursion():
    laptop_slices = [
        TimelineSlice(
            start_ts=1000000,
            end_ts=1060000,
            duration_s=60.0,
            idle_s=5.0,
            keystrokes=120,
            mouse_px=400.0,
            active_app="code.exe",
            category="coding",
            device_id="laptop",
        ),
        TimelineSlice(
            start_ts=1060000,
            end_ts=1120000,
            duration_s=60.0,
            idle_s=45.0,
            keystrokes=10,
            mouse_px=50.0,
            active_app="code.exe",
            category="coding",
            device_id="laptop",
        ),
        TimelineSlice(
            start_ts=1120000,
            end_ts=1180000,
            duration_s=60.0,
            idle_s=5.0,
            keystrokes=130,
            mouse_px=450.0,
            active_app="code.exe",
            category="coding",
            device_id="laptop",
        ),
    ]

    phone_events = [
        {"ts": 1065000, "event_type": "ACTION_SCREEN_ON", "payload": '{"app_pkg": "com.whatsapp"}'},
        {"ts": 1070000, "event_type": "ACTION_USER_PRESENT", "payload": '{"app_pkg": "com.whatsapp"}'},
        {"ts": 1095000, "event_type": "ACTION_SCREEN_OFF", "payload": '{"app_pkg": "com.whatsapp"}'},
    ]

    notifs = [
        {
            "notif_id": "notif_msg_1",
            "post_time": 1060000,
            "action_taken": "ALLOW",
            "app_pkg": "com.whatsapp",
        }
    ]

    outcomes = label_outcomes(notifs, laptop_slices, phone_events=phone_events, horizon_s=3600.0)
    assert len(outcomes) == 1
    out = outcomes[0]
    assert out.attended == 1
    assert out.react_latency_s == 5.0  # 1065000 - 1060000 = 5000 ms = 5s
    assert out.dest_app == "com.whatsapp"
    assert out.excursion_s == 30.0  # 1095000 - 1065000 = 30000 ms = 30s
    assert out.resume_lag_s >= 0.0



def test_six_learners():
    # 1. Focus Learner
    focus = FocusLearner()
    blocks = [
        {"start_ts": 1700000000000, "focus_depth": 0.85},
        {"start_ts": 1700003600000, "focus_depth": 0.90},
    ]
    focus.fit(blocks)
    assert focus.fitted is True
    top_wins = focus.get_top_windows(dow=0)
    assert len(top_wins) == 2

    # 2. Interruption Learner
    inter = InterruptionLearner()
    inter.fit(blocks)
    assert inter.fitted is True
    h = inter.predict_hazard(fatigue=0.5, is_peak=True, duration_hours=1.0)
    assert 0.0 <= h <= 1.0

    # 3. Stickiness Learner
    stick = StickinessLearner()
    outcomes = [
        {"excursion_s": 600.0, "category": "social"},
        {"excursion_s": 900.0, "category": "short_video"},
    ]
    stick.fit(outcomes)
    assert stick.fitted is True
    # Vectorized excursion survival inversion
    u_noise = np.array([0.2, 0.5, 0.8])
    exc_vals = stick.excursion_for_category(None, "social", "notification", u_noise)
    assert len(exc_vals) == 3
    assert np.all(exc_vals > 0.0)

    # 4. Notification Value Learner
    nval = NotificationValueLearner()
    notifs = [
        {"category": "work", "is_vip": True, "urgency_score": 0.9},
        {"category": "promo", "is_vip": False, "urgency_score": 0.1},
    ]
    nval.fit(notifs)
    assert nval.fitted is True
    assert nval.score_utility("work", is_vip=True) > 0.9
    assert nval.score_utility("promo", is_vip=False) < 0.5

    # 5. Fragmentation Learner
    frag = FragmentationLearner()
    inputs = [
        {"active_app": "code.exe", "idle_s": 10.0},
        {"active_app": "browser.exe", "idle_s": 25.0},
        {"active_app": "slack.exe", "idle_s": 40.0},
    ]
    frag.fit(inputs)
    assert frag.fitted is True
    assert 0.0 <= frag.fragmentation_score <= 1.0

    # 6. Fatigue Learner
    fatigue = FatigueLearner()
    fatigue.fit(inputs)
    assert fatigue.fitted is True
    score, probs = fatigue.infer_fatigue_p(keystrokes=200, mouse_px=2500, idle_s=20)
    assert 0.0 <= score <= 1.0
    assert len(probs) == 3


def test_causal_engine_and_abduction():
    rng = np.random.RandomState(42)
    dml = CausalForestDML()
    X = rng.uniform(0, 1, size=(50, 5))
    T = rng.binomial(1, 0.5, size=50)
    Y = 5.0 + (5.0 * T) + (1.0 * X[:, 0]) + rng.normal(0, 0.2, size=50)
    dml.fit(X, T, Y)
    assert dml.fitted is True

    cates = dml.predict_cate(X[:5])
    assert len(cates) == 5

    # Conformal
    conformal = SplitConformal(alpha=0.10)
    conformal.calibrate(y_true=Y, y_pred=Y + 0.5)
    assert conformal.calibrated is True
    low, high = conformal.predict_interval(10.0)
    assert low <= 10.0 <= high

    # Abduction Engine
    abd = AbductionEngine(dml=dml, conformal=conformal)
    replay = abd.replay_notification(
        notif_id="test_n1",
        factual_record={"category": "social", "urgency_score": 0.2, "action_taken": "ALLOW"},
        outcome_record={"y_observed": 900.0, "attended": 1},
    )
    assert replay["factual"]["action"] == "ALLOW"
    assert replay["counterfactual"]["action"] == "HOLD"
    assert replay["counterfactual"]["attention_saved_min"] > 0



def test_policy_and_auto_backoff(temp_db):
    gk = GatekeeperPolicy()
    # Absolute fail open on VIP
    dec_vip = gk.decide({"is_vip": True}, {"focus_depth": 0.95})
    assert dec_vip["action"] == "ALLOW"

    # Non-vip in deep focus gets held
    dec_work = gk.decide({"category": "social", "is_vip": False, "urgency_score": 0.2}, {"focus_depth": 0.85})
    assert dec_work["action"] == "HOLD"

    # Nudge auto-backoff test (Requirement #4)
    backoff = NudgeBackoffEngine(db=temp_db)
    # Simulate 5 dismissals
    for _ in range(5):
        backoff.record_nudge_shown(stage=2)
        stats = backoff.record_feedback("dismissed", stage=2)

    # Multiplier should have increased by 1.5x
    assert stats.threshold_multiplier >= 1.5
    thresholds = backoff.get_effective_distraction_thresholds()
    assert thresholds["stage1_brow_alert_s"] > 5.0


def test_api_endpoints():
    with TestClient(app) as client:
        # 1. Health
        res = client.get("/health")
        assert res.status_code == 200
        assert res.json()["service"] == "dhyan-twin-sidecar"

        # 2. Reset database endpoint (starts clean with 0 fake events)
        res = client.post("/reset")
        assert res.status_code == 200
        assert res.json()["status"] == "reset_complete"
        assert res.json()["events_count"] == 0

        # 3. Simulate Apps Dynamic Selector
        res = client.get("/simulate/apps")
        assert res.status_code == 200
        assert len(res.json()["apps"]) > 0

        # 4. Ingest Batch with real events and real notifications
        batch_payload = {
            "device_id": "laptop_test",
            "client_send_ts": int(time.time() * 1000) - 20,
            "events": [
                {
                    "device_id": "laptop_test",
                    "ts": int(time.time() * 1000),
                    "event_type": "input",
                    "payload": '{"idle_s": 5.0, "active_app": "Code.exe"}',
                }
            ],
            "notifications": [
                {
                    "notif_id": "real_n1",
                    "post_time": int(time.time() * 1000),
                    "app_pkg": "com.whatsapp",
                    "category": "communication",
                    "urgency_score": 0.7,
                    "action_taken": "ALLOW",
                }
            ],
        }
        res = client.post("/ingest/batch", json=batch_payload)
        assert res.status_code == 200
        assert res.json()["accepted"] >= 2

        # 5. WhatIf Simulation
        res = client.post("/whatif", json={"threshold_multiplier": 1.0, "urgency_cutoff": 0.4})
        assert res.status_code == 200
        assert res.json()["status"] in ("success", "warming_up")
        assert "apps" in res.json()

        # 6. Decide
        res = client.post(
            "/decide",
            json={
                "notification": {"category": "social", "urgency_score": 0.2},
                "context": {"focus_depth": 0.85},
            },
        )
        assert res.status_code == 200
        assert res.json()["action"] in ("ALLOW", "HOLD")

        # 7. Focus Lease Endpoints
        res = client.post("/twin/lease", json={"focus": "deep", "leaseMs": 90000})
        assert res.status_code == 200
        assert res.json()["lease"]["focus"] == "deep"
        assert res.json()["lease"]["leaseMs"] > 0

        # 8. Feedback
        res = client.post("/feedback", json={"action": "accepted", "stage": 2})
        assert res.status_code == 200
        assert res.json()["status"] == "recorded"

        # 9. Policy Cache (contains thresholds & lease)
        res = client.get("/policy/cache")
        assert res.status_code == 200
        assert "thresholds" in res.json()
        assert "lease" in res.json()
        assert res.json()["lease"]["focus"] == "deep"

        # 10. Twin Maturity & State
        res = client.get("/twin/maturity")
        assert res.status_code == 200
        assert "Warming up: learned from" in res.json()["status"]
        assert res.json()["notifications_count"] >= 1

        res = client.get("/twin/state")
        assert res.status_code == 200
        data = res.json()
        assert "focus_depth" in data
        assert "pet" in data
        assert "lease" in data
        assert data["lease"]["focus"] == "deep"
        assert data["pet"]["mode"] in (
            "GHOST_FLOW",
            "COMPANION_EDGE",
            "INTERVENTION_WALK_IN",
            "HIDDEN",
        )

        # 11. Profile Learner endpoints
        for endpoint in ["focus", "interruptions", "stickiness", "notif_value", "fragmentation", "fatigue"]:
            res = client.get(f"/profile/{endpoint}")
            assert res.status_code == 200
            assert res.json()["fitted"] is True

        # 12. Wipe Reset via feedback
        res = client.post("/feedback", json={"action": "wipe_reset"})
        assert res.status_code == 200
        assert res.json()["status"] == "reset"

