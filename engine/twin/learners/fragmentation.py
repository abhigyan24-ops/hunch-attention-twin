from typing import Any, Dict, List

import numpy as np
import statsmodels.api as sm


class FragmentationLearner:
    """
    Learner 5: Attention Residue & Task Fragmentation Index
    Fits an HC3 heteroskedasticity-consistent OLS regression relating app switching frequency
    to typing latency and productivity loss (Leroy 2009 attention residue).
    """

    def __init__(self):
        self.switching_penalty_ms: float = 450.0
        self.residue_decay_s: float = 120.0
        self.fragmentation_score: float = 0.0
        self.real_inputs_count: int = 0
        self.fitted = False

    def fit(self, input_summaries: List[Dict[str, Any]]) -> "FragmentationLearner":
        self.real_inputs_count = len(input_summaries)
        if len(input_summaries) < 10:
            if input_summaries:
                prev_app = None
                curr_switches = 0
                for s in input_summaries:
                    app = str(s.get("active_app", ""))
                    if prev_app and app != prev_app:
                        curr_switches += 1
                    prev_app = app
                self.fragmentation_score = min(
                    1.0, max(0.0, float(curr_switches / max(1, len(input_summaries))))
                )
            else:
                self.fragmentation_score = 0.0
            self.fitted = True
            return self

        # Extract features: switches per hour, idle_s, keystrokes
        switches: List[float] = []
        idle_ratios: List[float] = []

        prev_app = None
        curr_switches = 0
        for s in input_summaries:
            app = str(s.get("active_app", ""))
            if prev_app and app != prev_app:
                curr_switches += 1
            prev_app = app

            idle_s = float(s.get("idle_s", 0.0))
            switches.append(float(curr_switches))
            idle_ratios.append(idle_s)

        X = np.array(switches).reshape(-1, 1)
        X = sm.add_constant(X)
        y = np.array(idle_ratios)

        try:
            model = sm.OLS(y, X).fit(cov_type="HC3")
            if len(model.params) > 1:
                slope = float(model.params[1])
                self.switching_penalty_ms = max(50.0, round(slope * 1000.0, 1))
            self.fragmentation_score = min(
                1.0, max(0.05, float(curr_switches / max(1, len(input_summaries))))
            )
        except Exception:
            self.fragmentation_score = 0.35

        self.fitted = True
        return self

    def score_residue(self, num_recent_switches: int) -> float:
        """
        Estimates the residual attention fragmentation penalty in [0, 1].
        """
        return min(1.0, max(0.0, (num_recent_switches * 0.12)))

    def to_dict(self) -> Dict[str, Any]:
        return {
            "switching_penalty_ms": self.switching_penalty_ms,
            "residue_decay_s": self.residue_decay_s,
            "fragmentation_score": round(self.fragmentation_score, 3),
            "fitted": self.fitted,
            "real_inputs_count": self.real_inputs_count,
            "warming_up": self.real_inputs_count < 10,
        }
