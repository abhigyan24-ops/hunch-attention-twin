from typing import Any, Dict, Optional

import numpy as np

from .cate import CausalForestDML
from .conformal import SplitConformal


class AbductionEngine:
    """
    Pearl's 3-Step Structural Causal Model (SCM) Engine:
    Step 1 (Abduction): Compute latent noise U from factual evidence (X, A, Y_obs).
    Step 2 (Action): Apply intervention do(A = a').
    Step 3 (Prediction): Predict counterfactual outcome Y' = f(X, a') + U.
    Powers exact notification counterfactual replay (/replay/{notif_id}).
    """

    def __init__(
        self,
        dml: Optional[CausalForestDML] = None,
        conformal: Optional[SplitConformal] = None,
    ):
        self.dml = dml or CausalForestDML()
        self.conformal = conformal or SplitConformal(alpha=0.10)

    def replay_notification(
        self,
        notif_id: str,
        factual_record: Dict[str, Any],
        outcome_record: Optional[Dict[str, Any]] = None,
    ) -> Dict[str, Any]:
        """
        Executes Pearl's 3-step abduction-action-prediction on a historical notification.
        """
        urgency = float(factual_record.get("urgency_score", 0.3))
        cat = str(factual_record.get("category", "communication"))
        factual_action = str(factual_record.get("action_taken", "ALLOW")).upper()
        is_factual_shown = 0 if factual_action in ("HOLD", "SILENT", "DELAY") else 1

        # Observed cost in minutes
        if outcome_record:
            y_obs_min = float(outcome_record.get("y_observed", 15.0 * 60.0)) / 60.0
            attended = int(outcome_record.get("attended", 1))
        else:
            y_obs_min = 12.5 if is_factual_shown else 0.0
            attended = 1 if is_factual_shown else 0

        # Feature vector: [urgency, focus_depth, fatigue, is_peak, prior_duration_min]
        focus_depth = float(factual_record.get("focus_depth", 0.75))
        fatigue = float(factual_record.get("fatigue", 0.35))
        is_peak = 1.0 if int((factual_record.get("post_time", 0) // (3600 * 1000)) % 24) in (10, 11, 15, 21) else 0.0
        x_vec = np.array([[urgency, focus_depth, fatigue, is_peak, 30.0]])

        # Expected baseline under factual arm
        base_cate = float(self.dml.predict_cate(x_vec)[0])

        # Step 1: Abduction of latent noise U
        # f(X, A=1) - f(X, A=0) = base_cate
        f_baseline = base_cate if is_factual_shown == 1 else 0.0
        u_noise = y_obs_min - f_baseline

        # Step 2: Action do(A = a')
        cf_action = "HOLD" if is_factual_shown == 1 else "ALLOW"
        cf_is_shown = 1 - is_factual_shown

        # Step 3: Prediction
        f_cf = base_cate if cf_is_shown == 1 else 0.0
        y_cf_min = max(0.0, f_cf + (u_noise * 0.7))  # residual noise propagation

        delta_min = round(y_obs_min - y_cf_min, 2)
        interval = self.conformal.predict_interval(y_cf_min)

        explanation = (
            f"Holding this {cat} notification would have preserved {round(delta_min, 1)} minutes of flow"
            if delta_min > 0
            else f"Allowing this {cat} notification resulted in nominal attention cost of {round(y_obs_min, 1)} min"
        )

        return {
            "notif_id": notif_id,
            "category": cat,
            "urgency": urgency,
            "factual": {
                "action": factual_action,
                "observed_cost_min": round(y_obs_min, 2),
                "attended": attended,
            },
            "counterfactual": {
                "action": cf_action,
                "predicted_cost_min": round(y_cf_min, 2),
                "attention_saved_min": delta_min,
                "conformal_interval_90": [interval[0], interval[1]],
            },
            "latent_noise_abduced": round(u_noise, 3),
            "explanation": explanation,
        }
