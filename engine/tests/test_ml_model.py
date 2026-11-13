"""Tests for On-Device Machine Learning & Contextual Bandit Engine.

Verifies pure Python Online Hazard Survival Model, 4-Arm Diagonal LinUCB Bandit,
SQLite persistence, and FastAPI synchronization endpoints.
"""

import math
import sqlite3
import pytest
from fastapi.testclient import TestClient

from engine.twin.ml_model import (
    NUM_FEATURES,
    DEFAULT_PRIOR_WEIGHTS,
    BANDIT_ARMS,
    extract_features,
    OnlineHazardModel,
    ContextualBandit,
    MLManager,
)
from engine.twin.distraction_predictor import DistractionPredictor
from engine.twin.api.app import app
from engine.twin.ingest.db import Database
import engine.twin.ingest.db as db_module
import engine.twin.api.app as app_module


@pytest.fixture
def test_db_path(tmp_path):
    return tmp_path / "test_ml_hunch.db"


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


def test_feature_extraction_clamps_and_encodes_cyclical_time():
    """Maps arbitrary sensor telemetry to 12 normalized floats in [0.0, 1.0]."""
    ctx = {
        "hour_of_day": 14,
        "minute_of_hour": 30,
        "session_elapsed_mins": 120.0,  # Clamps to 1.0 (max 90m)
        "last_night_sleep_mins": 300.0,  # 5 hours -> 180m debt / 240m = 0.75
        "unlocks_last_15m": 12,  # Clamps to 1.0 (max 8)
        "switches_last_15m": 30,  # Clamps to 1.0 (max 20)
        "notifications_last_15m": 20,  # Clamps to 1.0 (max 15)
        "is_thrashing": True,
        "mins_since_last_break": 200.0,  # Clamps to 1.0 (max 180m)
        "social_scroll_secs_last_30m": 700.0,  # Clamps to 1.0 (max 600s)
    }

    features = extract_features(ctx)
    assert len(features) == NUM_FEATURES
    assert features[0] == 1.0  # Bias

    for i, val in enumerate(features):
        assert 0.0 <= val <= 1.0, f"Feature {i} out of bounds: {val}"

    # x[11] is interaction x[3] * x[4]
    assert math.isclose(features[11], features[3] * features[4], rel_tol=1e-5)


def test_online_hazard_model_training_and_survival_rollout():
    """Online AdaGrad steps increase hazard for repeated distraction outcomes."""
    model = OnlineHazardModel()
    ctx = {"session_elapsed_mins": 45.0, "last_night_sleep_mins": 360.0, "unlocks_last_15m": 4}
    features = extract_features(ctx)

    initial_hazard = model.predict_hazard_probability(features)

    for _ in range(25):
        model.train_online_step(features, actual_distracted=True)

    trained_hazard = model.predict_hazard_probability(features)
    assert trained_hazard > initial_hazard
    assert model.trained_samples_count == 25
    assert model.rolling_brier_loss < 0.20

    # Survival rollout returns plain-English forecast
    forecast = model.predict_minutes_until_distraction(ctx)
    assert 5 <= forecast["predicted_minutes"] <= 90
    assert "human_headline" in forecast
    assert forecast["is_calibrated"] is True  # > 6 samples


def test_fatigued_context_predicts_fewer_minutes_until_distraction():
    """Tired context with high sleep debt predicts shorter focus window than well-rested."""
    model = OnlineHazardModel()

    tired_ctx = {
        "hour_of_day": 15,
        "session_elapsed_mins": 50.0,
        "last_night_sleep_mins": 240.0,  # 4 hours sleep
        "unlocks_last_15m": 6,
        "switches_last_15m": 12,
        "mins_since_last_break": 90.0,
    }

    fresh_ctx = {
        "hour_of_day": 10,
        "session_elapsed_mins": 10.0,
        "last_night_sleep_mins": 480.0,  # 8 hours sleep
        "unlocks_last_15m": 0,
        "switches_last_15m": 2,
        "mins_since_last_break": 10.0,
    }

    tired_forecast = model.predict_minutes_until_distraction(tired_ctx)
    fresh_forecast = model.predict_minutes_until_distraction(fresh_ctx)

    assert tired_forecast["predicted_minutes"] < fresh_forecast["predicted_minutes"]


def test_contextual_bandit_converges_to_rewarded_arm():
    """LinUCB bandit converges to repeatedly rewarded action."""
    bandit = ContextualBandit()
    features = extract_features({"session_elapsed_mins": 60.0, "is_thrashing": True})

    # Heavily reward micro_break_prompt and penalize stay_silent
    for _ in range(30):
        bandit.record_reward("micro_break_prompt", features, reward=1.0)
        bandit.record_reward("stay_silent", features, reward=-0.5)

    best_arm = bandit.select_best_arm(features, exploration_alpha=0.05)
    assert best_arm == "micro_break_prompt"


def test_ml_manager_sqlite_persistence(tmp_path):
    """MLManager persists parameters to SQLite and reloads correctly."""
    db_file = tmp_path / "persist_test.db"
    mgr1 = MLManager(db_file)
    features = extract_features({"session_elapsed_mins": 30.0})

    # Train a few steps and reward an arm
    for _ in range(10):
        mgr1.hazard_model.train_online_step(features, actual_distracted=True)
    mgr1.bandit.record_reward("park_context_prompt", features, 0.9)
    mgr1.save()

    # Re-instantiate from same SQLite database
    mgr2 = MLManager(db_file)
    assert mgr2.hazard_model.trained_samples_count == 10
    assert math.isclose(mgr2.hazard_model.weights[0], mgr1.hazard_model.weights[0], rel_tol=1e-5)
    assert math.isclose(mgr2.bandit.thetas[3][0], mgr1.bandit.thetas[3][0], rel_tol=1e-5)


def test_sync_weights_endpoint(client):
    """POST /v1/ml/sync-weights performs federated averaging and returns status."""
    remote_weights = [1.5] * NUM_FEATURES
    remote_grads = [0.05] * NUM_FEATURES
    res = client.post(
        "/v1/ml/sync-weights",
        json={
            "weights": remote_weights,
            "grad_squares": remote_grads,
            "trained_samples_count": 8,
            "thetas": [[0.1] * NUM_FEATURES for _ in range(4)],
            "counts": [[2.0] * NUM_FEATURES for _ in range(4)],
        },
    )
    assert res.status_code == 200
    data = res.json()
    assert data["status"] == "synchronized"
    assert data["trained_samples_count"] >= 8
    assert len(data["merged_weights"]) == 12


def test_record_outcome_endpoint(client):
    """POST /v1/ml/record-outcome trains online step and updates status."""
    res = client.post(
        "/v1/ml/record-outcome",
        json={
            "actual_distracted": True,
            "selected_arm": "auto_quiet_shield",
            "reward": 0.8,
            "context": {
                "session_elapsed_mins": 40.0,
                "unlocks_last_15m": 3,
            },
        },
    )
    assert res.status_code == 200
    data = res.json()
    assert data["status"] == "recorded"
    assert data["trained_samples_count"] >= 1


def test_ml_status_endpoint(client):
    """GET /v1/ml/status returns plain-English prediction and LinUCB recommendation."""
    res = client.get("/v1/ml/status")
    assert res.status_code == 200
    data = res.json()
    assert "predicted_minutes_until_distraction" in data
    assert "primary_driver" in data
    assert "human_headline" in data
    assert "recommended_arm" in data
    assert "recommended_action_label" in data


def test_human_summary_includes_ml_forecast(client):
    """GET /v1/insights/human-summary includes mlForecast with plain-English insights."""
    res = client.get("/v1/insights/human-summary?current_streak_minutes=25")
    assert res.status_code == 200
    data = res.json()

    forecast = data["distractionForecast"]
    assert "mlForecast" in forecast
    ml_f = forecast["mlForecast"]
    assert "predictedMinutesUntilDistraction" in ml_f
    assert "recommendedActionLabel" in ml_f
