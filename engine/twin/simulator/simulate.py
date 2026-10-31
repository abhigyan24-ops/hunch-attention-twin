from dataclasses import dataclass, field
from typing import Any, Callable, Dict, List, Optional

import numpy as np

from .stages import SemiMarkovState, SimulatorStages


@dataclass
class SimulationResult:
    focus_minutes_baseline: float
    focus_minutes_counterfactual: float
    focus_minutes_saved: float
    interruptions_baseline: int
    interruptions_counterfactual: int
    interruptions_prevented: int
    notifications_held: int
    notifications_total: int
    details: List[Dict[str, Any]] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "focus_minutes_baseline": round(self.focus_minutes_baseline, 1),
            "focus_minutes_counterfactual": round(self.focus_minutes_counterfactual, 1),
            "focus_minutes_saved": round(self.focus_minutes_saved, 1),
            "interruptions_baseline": self.interruptions_baseline,
            "interruptions_counterfactual": self.interruptions_counterfactual,
            "interruptions_prevented": self.interruptions_prevented,
            "notifications_held": self.notifications_held,
            "notifications_total": self.notifications_total,
            "details": self.details[:50],  # sample details
        }


class AttentionSimulator:
    """
    Monte Carlo Attention Simulator using Common Random Numbers (CRN).
    Couples baseline and counterfactual policy trajectories to evaluate
    true causal effects without sample variance confounding.
    """

    def __init__(self, stages: Optional[SimulatorStages] = None):
        self.stages = stages or SimulatorStages()

    def run_counterfactual_simulation(
        self,
        notifications: List[Dict[str, Any]],
        policy_fn: Callable[[Dict[str, Any]], str],  # returns "ALLOW" or "HOLD"
        seed: int = 42,
    ) -> SimulationResult:
        if not notifications:
            return SimulationResult(0.0, 0.0, 0.0, 0, 0, 0, 0, 0)

        rng = np.random.RandomState(seed)
        n_trials = len(notifications)
        noise = self.stages.draw_noise(n_trials, rng)

        lost_min_baseline = 0.0
        lost_min_cf = 0.0
        interruptions_base = 0
        interruptions_cf = 0
        held_count = 0
        details: List[Dict[str, Any]] = []

        for i, notif in enumerate(notifications):
            cat = str(notif.get("category", "communication"))
            urgency = float(notif.get("urgency_score", 0.3))
            attend_prob = min(0.95, 0.4 + (0.4 * urgency))

            single_noise = {k: float(v[i]) for k, v in noise.items()}

            # 1. Baseline: observed or default action (usually ALLOW)
            base_action = notif.get("action_taken", "ALLOW")
            _, base_dur = self.stages.compute_transition(
                current_state=SemiMarkovState.FOCUSED,
                action=base_action,
                category=cat,
                attend_prob=attend_prob,
                noise=single_noise,
            )
            lost_min_baseline += base_dur
            if base_dur > 1.0:
                interruptions_base += 1

            # 2. Counterfactual: evaluated under policy_fn with exact same noise
            cf_action = policy_fn(notif)
            if cf_action in ("HOLD", "SILENT", "DELAY"):
                held_count += 1

            _, cf_dur = self.stages.compute_transition(
                current_state=SemiMarkovState.FOCUSED,
                action=cf_action,
                category=cat,
                attend_prob=attend_prob,
                noise=single_noise,
            )
            lost_min_cf += cf_dur
            if cf_dur > 1.0:
                interruptions_cf += 1

            details.append(
                {
                    "notif_id": notif.get("notif_id", f"n_{i}"),
                    "category": cat,
                    "baseline_action": base_action,
                    "cf_action": cf_action,
                    "baseline_cost_min": round(base_dur, 2),
                    "cf_cost_min": round(cf_dur, 2),
                    "time_saved_min": round(base_dur - cf_dur, 2),
                }
            )

        saved_min = max(0.0, lost_min_baseline - lost_min_cf)
        prevented = max(0, interruptions_base - interruptions_cf)

        return SimulationResult(
            focus_minutes_baseline=round(lost_min_baseline, 1),
            focus_minutes_counterfactual=round(lost_min_cf, 1),
            focus_minutes_saved=round(saved_min, 1),
            interruptions_baseline=interruptions_base,
            interruptions_counterfactual=interruptions_cf,
            interruptions_prevented=prevented,
            notifications_held=held_count,
            notifications_total=n_trials,
            details=details,
        )
