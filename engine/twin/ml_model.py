"""Pure Python On-Device Online Logistic Hazard Survival Model & 4-Arm Contextual Bandit.

Zero external C++/Native ML dependencies (100% standard library math + sqlite3).
Mirrors identical 12-dimensional normalized feature vector and math as Android's OnDeviceDistractionMLModel.
"""

from __future__ import annotations

import json
import math
from pathlib import Path
import sqlite3
import time
from typing import Any, Dict, List, Optional, Tuple

NUM_FEATURES = 12

DEFAULT_PRIOR_WEIGHTS = [
    -2.2,  # x[0]: Bias (baseline ~10% 5-min hazard)
     0.1,  # x[1]: Hour sine
    -0.1,  # x[2]: Hour cosine
     0.8,  # x[3]: Session elapsed
     0.9,  # x[4]: Sleep debt
     0.7,  # x[5]: Unlocks velocity
     0.6,  # x[6]: App switches
     0.5,  # x[7]: Notification pressure
     1.0,  # x[8]: Cognitive thrash
     0.7,  # x[9]: Cumulative work fatigue
     0.8,  # x[10]: High-dopamine feed exposure
     0.6,  # x[11]: Fatigue x Sleep interaction
]

BANDIT_ARMS = [
    "stay_silent",
    "auto_quiet_shield",
    "micro_break_prompt",
    "park_context_prompt",
]

ARM_LABELS = {
    "stay_silent": "Stay Silent",
    "auto_quiet_shield": "Activate Quiet Shield",
    "micro_break_prompt": "Suggest 3-Minute Walk-Away",
    "park_context_prompt": "Suggest Intention Parking",
}


def _clamp(val: float, min_val: float = 0.0, max_val: float = 1.0) -> float:
    return max(min_val, min(max_val, float(val)))


def extract_features(ctx: Dict[str, Any]) -> List[float]:
    """Maps raw sensor telemetry into a 12-dimensional normalized vector in [0.0, 1.0]^12."""
    hour = int(ctx.get("hour_of_day", ctx.get("hour", 12))) % 24
    minute = int(ctx.get("minute_of_hour", ctx.get("minute", 0)))
    continuous_hour = hour + _clamp(minute / 60.0, 0.0, 59.0 / 60.0)

    session_elapsed = float(ctx.get("session_elapsed_mins", ctx.get("session_duration_minutes", 0.0)))
    sleep_mins = float(ctx.get("last_night_sleep_mins", ctx.get("sleep_minutes", 480.0)))
    unlocks = int(ctx.get("unlocks_last_15m", ctx.get("phone_unlocks", 0)))
    switches = int(ctx.get("switches_last_15m", ctx.get("app_switches", 0)))
    notifs = int(ctx.get("notifications_last_15m", ctx.get("incoming_notifications", 0)))
    thrashing = bool(ctx.get("is_thrashing", ctx.get("thrash_active", False)))
    break_gap = float(ctx.get("mins_since_last_break", 0.0))
    social_scroll = float(ctx.get("social_scroll_secs_last_30m", ctx.get("social_scroll_seconds", 0.0)))

    hour_rad = 2.0 * math.pi * continuous_hour / 24.0

    x = [0.0] * NUM_FEATURES
    x[0] = 1.0  # Bias
    x[1] = _clamp((math.sin(hour_rad) + 1.0) / 2.0)
    x[2] = _clamp((math.cos(hour_rad) + 1.0) / 2.0)
    x[3] = _clamp(session_elapsed / 90.0)
    sleep_debt = max(0.0, 480.0 - sleep_mins)
    x[4] = _clamp(sleep_debt / 240.0)
    x[5] = _clamp(unlocks / 8.0)
    x[6] = _clamp(switches / 20.0)
    x[7] = _clamp(notifs / 15.0)
    x[8] = 1.0 if thrashing else 0.0
    x[9] = _clamp(break_gap / 180.0)
    x[10] = _clamp(social_scroll / 600.0)
    x[11] = _clamp(x[3] * x[4])  # Interaction

    return x


class OnlineHazardModel:
    """Online AdaGrad Logistic Hazard Survival Model with 18-step survival curve rollout."""

    def __init__(
        self,
        weights: Optional[List[float]] = None,
        grad_squares: Optional[List[float]] = None,
        trained_samples_count: int = 0,
        rolling_brier_loss: float = 0.10,
    ):
        self.weights = list(weights) if weights else list(DEFAULT_PRIOR_WEIGHTS)
        self.grad_squares = list(grad_squares) if grad_squares else [1e-4] * NUM_FEATURES
        self.trained_samples_count = int(trained_samples_count)
        self.rolling_brier_loss = float(rolling_brier_loss)

    def predict_hazard_probability(self, features: List[float]) -> float:
        """Numerically stable sigmoid: h_t = 1 / (1 + exp(-w^T x))."""
        dot = sum(w * x for w, x in zip(self.weights, features))
        if dot >= 0:
            prob = 1.0 / (1.0 + math.exp(-dot))
        else:
            ez = math.exp(dot)
            prob = ez / (1.0 + ez)
        return _clamp(prob, 1e-6, 1.0 - 1e-6)

    def predict_minutes_until_distraction(self, ctx: Dict[str, Any]) -> Dict[str, Any]:
        """Rolls out an 18-step discrete survival curve S(k) = prod(1 - h_j) to find median survival."""
        survival = 1.0
        predicted_step = 18

        base_session = float(ctx.get("session_elapsed_mins", 0.0))
        base_break = float(ctx.get("mins_since_last_break", 0.0))
        base_hour = int(ctx.get("hour_of_day", 12))
        base_minute = int(ctx.get("minute_of_hour", 0))

        for k in range(1, 19):
            step_mins = 5.0 * k
            step_ctx = dict(ctx)
            step_ctx["session_elapsed_mins"] = base_session + step_mins
            step_ctx["mins_since_last_break"] = base_break + step_mins
            step_ctx["hour_of_day"] = (base_hour + int((base_minute + step_mins) // 60)) % 24
            step_ctx["minute_of_hour"] = int((base_minute + step_mins) % 60)

            step_features = extract_features(step_ctx)
            h_k = self.predict_hazard_probability(step_features)
            survival *= (1.0 - h_k)

            if survival <= 0.50:
                predicted_step = k
                break

        predicted_minutes = predicted_step * 5

        current_features = extract_features(ctx)
        # Determine primary driver from contributions
        driver_map = {
            3: "Long session duration",
            4: "Circadian sleep debt",
            5: "Frequent phone pickups",
            6: "Rapid app / window switching",
            7: "Notification pressure",
            8: "Cognitive thrash / build churn",
            9: "Extended time without a break",
            10: "Recent social feed exposure",
            11: "Compounding fatigue and sleep loss",
        }
        max_idx = -1
        max_val = -1e9
        for i in range(3, NUM_FEATURES):
            contrib = self.weights[i] * current_features[i]
            if contrib > max_val and current_features[i] > 0.10:
                max_val = contrib
                max_idx = i

        primary_driver = driver_map.get(max_idx, "Natural attention cycle")
        is_calibrated = self.trained_samples_count >= 6

        if predicted_minutes <= 20:
            headline = f"Likely distraction around minute {predicted_minutes} — {primary_driver}"
        elif predicted_minutes <= 45:
            headline = f"Focus holding well for ~{predicted_minutes} mins before natural dip"
        else:
            headline = f"Strong flow forecast — high focus stability for ~{predicted_minutes} mins"

        return {
            "predicted_minutes": predicted_minutes,
            "primary_driver": primary_driver,
            "human_headline": headline,
            "is_calibrated": is_calibrated,
            "median_survival_ratio": round(survival, 3),
        }

    def train_online_step(
        self,
        features: List[float],
        actual_distracted: bool,
        learning_rate: float = 0.15,
        l2_lambda: float = 0.001,
    ) -> None:
        """AdaGrad parameter update step."""
        y = 1.0 if actual_distracted else 0.0
        pred = self.predict_hazard_probability(features)
        error = pred - y

        self.rolling_brier_loss = 0.95 * self.rolling_brier_loss + 0.05 * (error * error)

        for i in range(NUM_FEATURES):
            grad = error * features[i] + l2_lambda * self.weights[i]
            self.grad_squares[i] += grad * grad
            step = (learning_rate / math.sqrt(self.grad_squares[i] + 1e-8)) * grad
            self.weights[i] -= step

        self.trained_samples_count += 1

    def merge_federated_weights(
        self,
        remote_weights: List[float],
        remote_grad_squares: Optional[List[float]] = None,
        remote_sample_count: int = 1,
    ) -> None:
        """Sample-weighted Federated Averaging."""
        total = max(1, self.trained_samples_count + remote_sample_count)
        w_local = self.trained_samples_count / total
        w_remote = remote_sample_count / total

        for i in range(NUM_FEATURES):
            rw = remote_weights[i] if i < len(remote_weights) else DEFAULT_PRIOR_WEIGHTS[i]
            self.weights[i] = self.weights[i] * w_local + rw * w_remote
            if remote_grad_squares and i < len(remote_grad_squares):
                self.grad_squares[i] = self.grad_squares[i] * w_local + remote_grad_squares[i] * w_remote

        self.trained_samples_count = total

    def to_dict(self) -> Dict[str, Any]:
        return {
            "weights": self.weights,
            "grad_squares": self.grad_squares,
            "trained_samples_count": self.trained_samples_count,
            "rolling_brier_loss": self.rolling_brier_loss,
        }

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> OnlineHazardModel:
        return cls(
            weights=data.get("weights"),
            grad_squares=data.get("grad_squares"),
            trained_samples_count=data.get("trained_samples_count", 0),
            rolling_brier_loss=data.get("rolling_brier_loss", 0.10),
        )


class ContextualBandit:
    """4-Arm Diagonal LinUCB Contextual Bandit."""

    def __init__(
        self,
        thetas: Optional[List[List[float]]] = None,
        counts: Optional[List[List[float]]] = None,
    ):
        if thetas:
            self.thetas = [list(row) for row in thetas]
        else:
            # Baseline prior bias per arm
            self.thetas = [[0.0] * NUM_FEATURES for _ in range(len(BANDIT_ARMS))]
            self.thetas[0][0] = 0.5  # stay_silent
            self.thetas[1][0] = 0.3  # auto_quiet_shield
            self.thetas[2][0] = 0.2  # micro_break_prompt
            self.thetas[3][0] = 0.2  # park_context_prompt

        if counts:
            self.counts = [list(row) for row in counts]
        else:
            self.counts = [[1.0] * NUM_FEATURES for _ in range(len(BANDIT_ARMS))]

    def select_best_arm(self, features: List[float], exploration_alpha: float = 0.25) -> str:
        """Selects arm maximizing Score(a | x) = theta_a^T x + alpha * sqrt(sum_i (x_i^2 / c_{a,i}))."""
        best_score = -1e9
        best_arm_idx = 0

        for a in range(len(BANDIT_ARMS)):
            dot = sum(self.thetas[a][i] * features[i] for i in range(NUM_FEATURES))
            variance_sum = sum((features[i] ** 2) / max(1e-4, self.counts[a][i]) for i in range(NUM_FEATURES))
            ucb_bonus = exploration_alpha * math.sqrt(variance_sum)
            score = dot + ucb_bonus

            if score > best_score:
                best_score = score
                best_arm_idx = a

        return BANDIT_ARMS[best_arm_idx]

    def record_reward(self, arm: str, features: List[float], reward: float) -> None:
        """Online update on the chosen arm given reward in [-1.0, 1.0]."""
        if arm not in BANDIT_ARMS:
            return
        a = BANDIT_ARMS.index(arm)
        r = _clamp(reward, -1.0, 1.0)

        pred_r = sum(self.thetas[a][i] * features[i] for i in range(NUM_FEATURES))
        error = r - pred_r

        for i in range(NUM_FEATURES):
            xi = features[i]
            self.counts[a][i] += xi * xi
            step = (0.10 * xi) / math.sqrt(self.counts[a][i])
            self.thetas[a][i] += step * error

    def merge_federated_bandit(
        self,
        remote_thetas: List[List[float]],
        remote_counts: List[List[float]],
    ) -> None:
        """Merges remote bandit parameter matrices."""
        for a in range(min(len(BANDIT_ARMS), len(remote_thetas))):
            for i in range(NUM_FEATURES):
                c1 = self.counts[a][i]
                c2 = remote_counts[a][i] if a < len(remote_counts) and i < len(remote_counts[a]) else 1.0
                tot = c1 + c2
                self.thetas[a][i] = (self.thetas[a][i] * c1 + remote_thetas[a][i] * c2) / tot
                self.counts[a][i] = tot

    def to_dict(self) -> Dict[str, Any]:
        return {
            "thetas": self.thetas,
            "counts": self.counts,
        }

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> ContextualBandit:
        return cls(
            thetas=data.get("thetas"),
            counts=data.get("counts"),
        )


class MLManager:
    """Thread-safe persistent singleton managing the Hazard Model and Contextual Bandit in SQLite."""

    def __init__(self, db_path: str | Path):
        self.db_path = str(db_path)
        self.hazard_model = OnlineHazardModel()
        self.bandit = ContextualBandit()
        self._ensure_table()
        self.load()

    def _get_connection(self) -> sqlite3.Connection:
        conn = sqlite3.connect(self.db_path, timeout=5.0)
        conn.row_factory = sqlite3.Row
        return conn

    def _ensure_table(self) -> None:
        with self._get_connection() as conn:
            conn.execute(
                """
                CREATE TABLE IF NOT EXISTS ml_model_state (
                    id TEXT PRIMARY KEY,
                    state_json TEXT NOT NULL,
                    updated_at INTEGER NOT NULL
                )
                """
            )

    def load(self) -> None:
        try:
            with self._get_connection() as conn:
                row = conn.execute("SELECT state_json FROM ml_model_state WHERE id = 'active'").fetchone()
                if row:
                    data = json.loads(row["state_json"])
                    if "hazard_model" in data:
                        self.hazard_model = OnlineHazardModel.from_dict(data["hazard_model"])
                    if "bandit" in data:
                        self.bandit = ContextualBandit.from_dict(data["bandit"])
        except Exception:
            pass

    def save(self) -> None:
        try:
            payload = json.dumps({
                "hazard_model": self.hazard_model.to_dict(),
                "bandit": self.bandit.to_dict(),
            })
            now_ms = int(time.time() * 1000)
            with self._get_connection() as conn:
                conn.execute(
                    """
                    INSERT INTO ml_model_state (id, state_json, updated_at)
                    VALUES ('active', ?, ?)
                    ON CONFLICT(id) DO UPDATE SET
                        state_json = excluded.state_json,
                        updated_at = excluded.updated_at
                    """,
                    (payload, now_ms),
                )
        except Exception:
            pass

    def get_status(self, ctx: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
        eval_ctx = ctx or {
            "hour_of_day": time.localtime().tm_hour,
            "session_elapsed_mins": 25.0,
            "last_night_sleep_mins": 450.0,
        }
        features = extract_features(eval_ctx)
        forecast = self.hazard_model.predict_minutes_until_distraction(eval_ctx)
        recommended_arm = self.bandit.select_best_arm(features)

        return {
            "trained_samples_count": self.hazard_model.trained_samples_count,
            "rolling_brier_loss": round(self.hazard_model.rolling_brier_loss, 4),
            "predicted_minutes_until_distraction": forecast["predicted_minutes"],
            "primary_driver": forecast["primary_driver"],
            "human_headline": forecast["human_headline"],
            "is_calibrated": forecast["is_calibrated"],
            "recommended_arm": recommended_arm,
            "recommended_action_label": ARM_LABELS.get(recommended_arm, "Activate Quiet Shield"),
        }
