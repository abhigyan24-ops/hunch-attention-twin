from enum import Enum
from typing import Any, Dict, List, Optional, Tuple, Union

import numpy as np

from ..learners.stickiness import StickinessLearner


class SemiMarkovState(str, Enum):
    FOCUSED = "FOCUSED"
    GLANCING = "GLANCING"
    EXCURSION = "EXCURSION"
    RESUMING = "RESUMING"
    IDLE_BREAK = "IDLE_BREAK"


class SimulatorStages:
    """
    Semi-Markov stage transitions with Common Random Numbers (CRN)
    and vectorized survival inversion for counterfactual consistency.
    """

    def __init__(self, stickiness: Optional[StickinessLearner] = None):
        self.stickiness = stickiness or StickinessLearner()

    def draw_noise(self, n_trials: int, rng: np.random.RandomState) -> Dict[str, np.ndarray]:
        """
        Draws fixed Common Random Numbers (CRN) for coupled counterfactual paths.
        CRN guarantees that delta = Y(1) - Y(0) isolates pure policy intervention
        rather than stochastic simulation variance.
        """
        return {
            "u_attend": rng.uniform(0.0, 1.0, size=n_trials),
            "u_exc": rng.uniform(0.0, 1.0, size=n_trials),
            "u_resume": rng.uniform(0.0, 1.0, size=n_trials),
            "u_self_break": rng.uniform(0.0, 1.0, size=n_trials),
        }

    def sample_excursions(
        self,
        categories: Union[List[str], np.ndarray],
        trigger: str,
        noise_u: np.ndarray,
    ) -> np.ndarray:
        """
        Vectorized excursion duration calculation.
        Partitioning by unique categories avoids heterogeneous array bugs in lifelines/Weibull.
        """
        cats_arr = np.array(categories)
        durations = np.zeros(len(cats_arr), dtype=float)

        unique_cats = np.unique(cats_arr)
        for cat in unique_cats:
            mask = cats_arr == cat
            sub_noise = noise_u[mask]
            durations[mask] = self.stickiness.excursion_for_category(
                x=None, category=str(cat), trigger=trigger, noise_u=sub_noise
            )

        return durations

    def compute_transition(
        self,
        current_state: SemiMarkovState,
        action: str,  # "ALLOW" vs "HOLD"
        category: str,
        attend_prob: float,
        noise: Dict[str, float],
        glance_cost_min: float = 0.4,
    ) -> Tuple[SemiMarkovState, float]:
        """
        Single-step semi-Markov transition.
        Returns (next_state, duration_minutes).
        """
        if current_state == SemiMarkovState.FOCUSED:
            if action in ("HOLD", "SILENT", "DELAY"):
                # Held notification: check for spontaneous distraction
                if noise["u_self_break"] < 0.12:
                    return SemiMarkovState.IDLE_BREAK, 5.0
                return SemiMarkovState.FOCUSED, 30.0

            # Shown notification: glance cost is incurred
            if noise["u_attend"] < attend_prob:
                # Transition into excursion
                exc_min = float(
                    self.stickiness.excursion_for_category(
                        None, category, "notification", noise["u_exc"]
                    )
                )
                return SemiMarkovState.EXCURSION, glance_cost_min + exc_min
            else:
                # Brief glance, returned to work immediately
                return SemiMarkovState.FOCUSED, glance_cost_min

        elif current_state == SemiMarkovState.EXCURSION:
            # Transition to resuming
            resume_min = max(0.5, float(np.exp(2.0 + 0.6 * np.clip(noise["u_resume"], 0.05, 0.95))))
            return SemiMarkovState.RESUMING, resume_min

        elif current_state == SemiMarkovState.RESUMING:
            return SemiMarkovState.FOCUSED, 15.0

        elif current_state == SemiMarkovState.IDLE_BREAK:
            return SemiMarkovState.RESUMING, 2.0

        return SemiMarkovState.FOCUSED, 10.0
