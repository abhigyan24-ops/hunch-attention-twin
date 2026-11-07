import math
from typing import Any, Dict, List, Optional

import numpy as np
from sklearn.linear_model import LogisticRegression


class InterruptionLearner:
    """
    Learner 2: Interruption Susceptibility & Discrete-Time Hazard Model
    Models the conditional hazard of an interruption event given work state and fatigue.
    Attributes excess breaks to notification arrivals vs spontaneous task abandonment.
    """

    def __init__(self):
        self.model = LogisticRegression(C=1.0, solver="lbfgs")
        self.fitted = False
        self.coefs: Dict[str, float] = {
            "intercept": -2.2,
            "fatigue": 1.45,
            "is_peak": -0.65,
            "duration_hours": 0.85,
        }
        self.excess_break_ratio: float = 0.0
        self.real_blocks_count: int = 0
        self.real_notifs_count: int = 0

    def fit(
        self,
        focus_blocks: List[Dict[str, Any]],
        notifications: Optional[List[Dict[str, Any]]] = None,
    ) -> "InterruptionLearner":
        self.real_blocks_count = len(focus_blocks)
        self.real_notifs_count = len(notifications or [])

        # Compute empirical breaks from whatever real blocks we have
        total_breaks = sum(int(b.get("interruptions_count", 0)) for b in focus_blocks)
        if total_breaks > 0 and notifications:
            notif_times = set(int(n.get("post_time", 0)) for n in notifications)
            notif_attributed = 0
            for b in focus_blocks:
                b_start = int(b.get("start_ts", 0))
                b_end = int(b.get("end_ts", 0))
                matched = sum(1 for n_t in notif_times if b_start <= n_t <= b_end)
                notif_attributed += min(int(b.get("interruptions_count", 0)), matched)
            self.excess_break_ratio = round(min(1.0, notif_attributed / total_breaks), 3)
        else:
            self.excess_break_ratio = 0.0

        if len(focus_blocks) < 5:
            self.fitted = True
            return self

        X: List[List[float]] = []
        y: List[int] = []

        notif_times = set(int(n.get("post_time", 0)) for n in (notifications or []))

        for b in focus_blocks:
            dur_hr = float(b.get("duration_s", 1800.0)) / 3600.0
            fatigue = float(b.get("fatigue_score", 0.3))
            start_ts = int(b.get("start_ts", 0)) / 1000.0
            hour = int((start_ts // 3600) % 24)
            is_peak = 1.0 if hour in (10, 11, 15, 21) else 0.0

            interrupted = 1 if int(b.get("interruptions_count", 0)) > 0 else 0

            X.append([fatigue, is_peak, dur_hr])
            y.append(interrupted)

        X_arr = np.array(X)
        y_arr = np.array(y)

        if len(np.unique(y_arr)) > 1:
            self.model.fit(X_arr, y_arr)
            self.coefs = {
                "intercept": float(self.model.intercept_[0]),
                "fatigue": float(self.model.coef_[0][0]),
                "is_peak": float(self.model.coef_[0][1]),
                "duration_hours": float(self.model.coef_[0][2]),
            }

        # Calculate excess break attribution
        total_breaks = sum(int(b.get("interruptions_count", 0)) for b in focus_blocks)
        if total_breaks > 0 and notifications:
            # Notifications coincident with breaks within 3 minutes
            notif_attributed = 0
            for b in focus_blocks:
                b_start = int(b.get("start_ts", 0))
                b_end = int(b.get("end_ts", 0))
                matched = sum(1 for n_t in notif_times if b_start <= n_t <= b_end)
                notif_attributed += min(int(b.get("interruptions_count", 0)), matched)
            self.excess_break_ratio = round(min(1.0, notif_attributed / total_breaks), 3)

        self.fitted = True
        return self

    def predict_hazard(self, fatigue: float, is_peak: bool, duration_hours: float) -> float:
        """
        Computes interruption hazard probability in [0, 1].
        """
        z = (
            self.coefs["intercept"]
            + self.coefs["fatigue"] * fatigue
            + self.coefs["is_peak"] * (1.0 if is_peak else 0.0)
            + self.coefs["duration_hours"] * duration_hours
        )
        return float(1.0 / (1.0 + math.exp(-max(-15.0, min(15.0, z)))))

    def to_dict(self) -> Dict[str, Any]:
        return {
            "coefficients": self.coefs,
            "excess_break_ratio": self.excess_break_ratio,
            "fitted": self.fitted,
            "real_blocks_count": self.real_blocks_count,
            "real_notifs_count": self.real_notifs_count,
            "warming_up": self.real_blocks_count < 5,
        }
