from typing import Any, Dict, List, Optional, Tuple

import numpy as np

try:
    from hmmlearn.hmm import GaussianHMM
    HAVE_HMM = True
except ImportError:
    HAVE_HMM = False


class FatigueLearner:
    """
    Learner 6: 3-State Gaussian Hidden Markov Model for Latent Fatigue
    State 0: Fresh / High Focus (fast, consistent typing, low idle)
    State 1: Moderate Fatigue (nominal typing, occasional pauses)
    State 2: High Fatigue / Depleted (sluggish or erratic bursts, high idle)
    """

    def __init__(self, n_states: int = 3):
        self.n_states = n_states
        self.fitted = False
        self.hmm = None
        if HAVE_HMM:
            self.hmm = GaussianHMM(
                n_components=n_states,
                covariance_type="diag",
                n_iter=50,
                random_state=42,
            )

        # Transition matrix defaults
        self.trans_mat = np.array(
            [
                [0.92, 0.07, 0.01],
                [0.05, 0.88, 0.07],
                [0.02, 0.08, 0.90],
            ]
        )
        self.means = np.array(
            [
                [220.0, 3000.0, 15.0],  # state 0: keystrokes, mouse_px, idle_s
                [140.0, 1800.0, 60.0],  # state 1
                [50.0, 600.0, 180.0],   # state 2
            ]
        )

    def fit(self, input_summaries: List[Dict[str, Any]]) -> "FatigueLearner":
        if len(input_summaries) < 15:
            self.fitted = True
            return self

        obs: List[List[float]] = []
        for s in input_summaries:
            keys = float(s.get("keystrokes", 0))
            mouse = float(s.get("mouse_px", 0.0))
            idle = float(s.get("idle_s", 0.0))
            obs.append([keys, mouse, idle])

        obs_arr = np.array(obs)

        if HAVE_HMM and self.hmm is not None:
            try:
                self.hmm.fit(obs_arr)
                # Sort components by mean keystrokes descending (state 0 = most alert)
                order = np.argsort(-self.hmm.means_[:, 0])
                self.means = self.hmm.means_[order]
                self.trans_mat = self.hmm.transmat_[order][:, order]
            except Exception:
                pass

        self.fitted = True
        return self

    def infer_fatigue_p(
        self,
        keystrokes: float,
        mouse_px: float,
        idle_s: float,
    ) -> Tuple[float, List[float]]:
        """
        Returns (fatigue_scalar in [0, 1], state_probabilities [p_fresh, p_moderate, p_exhausted]).
        """
        x = np.array([keystrokes, mouse_px, idle_s])
        # Compute distance to state means
        dists = [np.linalg.norm((x - m) / (np.abs(m) + 1.0)) for m in self.means]
        inv_dists = [1.0 / (d + 1e-4) for d in dists]
        total = sum(inv_dists)
        probs = [d / total for d in inv_dists]

        # Fatigue scalar: weighted combination (0.0*p0 + 0.5*p1 + 1.0*p2)
        fatigue_score = min(1.0, max(0.0, (0.0 * probs[0]) + (0.5 * probs[1]) + (1.0 * probs[2])))
        return round(fatigue_score, 3), [round(p, 3) for p in probs]

    def to_dict(self) -> Dict[str, Any]:
        return {
            "n_states": self.n_states,
            "transition_matrix": self.trans_mat.round(3).tolist(),
            "state_means": self.means.round(1).tolist(),
            "fitted": self.fitted,
        }
