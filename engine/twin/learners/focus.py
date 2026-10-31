from datetime import datetime, timezone
from typing import Any, Dict, List, Optional, Tuple

import numpy as np
import pandas as pd


class FocusLearner:
    """
    Learner 1: Focus Capacity & Heatmap
    Computes an Empirical Bayes shrunk (24 x 7) hour x day-of-week focus probability matrix
    and extracts the user's top daily focus windows.
    """

    def __init__(self, prior_mean: float = 0.50, prior_weight: float = 10.0):
        self.prior_mean = prior_mean
        self.prior_weight = prior_weight
        # 7 days (0=Mon .. 6=Sun) x 24 hours
        self.heatmap: np.ndarray = np.full((7, 24), prior_mean, dtype=float)
        self.sample_counts: np.ndarray = np.zeros((7, 24), dtype=int)
        self.fitted: bool = False

    def fit(self, focus_blocks: List[Dict[str, Any]]) -> "FocusLearner":
        if not focus_blocks:
            self._apply_default_priors()
            self.fitted = True
            return self

        obs_sums = np.zeros((7, 24), dtype=float)
        obs_counts = np.zeros((7, 24), dtype=int)

        for b in focus_blocks:
            start_ts = int(b.get("start_ts", 0)) / 1000.0
            dt = datetime.fromtimestamp(start_ts, tz=timezone.utc)
            dow = dt.weekday()  # 0 to 6
            hour = dt.hour  # 0 to 23
            depth = float(b.get("focus_depth", 0.5))

            obs_sums[dow, hour] += depth
            obs_counts[dow, hour] += 1

        total_obs = obs_counts.sum()
        if total_obs > 0:
            grand_mean = obs_sums.sum() / total_obs
            cell_means = np.divide(
                obs_sums, obs_counts, out=np.full_like(obs_sums, grand_mean), where=obs_counts > 0
            )
            # Empirical Bayes shrinkage towards grand mean
            shrinkage = obs_counts / (obs_counts + self.prior_weight)
            self.heatmap = (shrinkage * cell_means) + ((1.0 - shrinkage) * grand_mean)
        else:
            self._apply_default_priors()

        self.sample_counts = obs_counts
        self.fitted = True
        return self

    def _apply_default_priors(self) -> None:
        # Default baseline rhythm (higher during working hours 10-12, 14-17)
        for d in range(7):
            for h in range(24):
                if d < 5:  # Weekday
                    if 10 <= h <= 12 or 14 <= h <= 17:
                        self.heatmap[d, h] = 0.82
                    elif 9 <= h <= 18:
                        self.heatmap[d, h] = 0.65
                    elif 21 <= h <= 23:
                        self.heatmap[d, h] = 0.45
                    else:
                        self.heatmap[d, h] = 0.20
                else:  # Weekend
                    if 11 <= h <= 16:
                        self.heatmap[d, h] = 0.40
                    else:
                        self.heatmap[d, h] = 0.15

    def get_top_windows(self, dow: Optional[int] = None, top_k: int = 2) -> List[Dict[str, Any]]:
        """
        Identifies the top daily focus windows (2-hour blocks) with highest focus potential.
        """
        if dow is None:
            dow = datetime.now(timezone.utc).weekday()
        dow = int(dow) % 7
        scores = self.heatmap[dow]

        windows: List[Tuple[float, int, int]] = []
        for h in range(0, 23):
            # 2-hour window average score
            avg_score = float((scores[h] + scores[h + 1]) / 2.0)
            windows.append((avg_score, h, h + 2))

        windows.sort(key=lambda x: x[0], reverse=True)

        selected: List[Dict[str, Any]] = []
        for score, start_h, end_h in windows:
            # Avoid direct overlap with already selected windows
            if any(abs(start_h - s["start_hour"]) < 2 for s in selected):
                continue
            selected.append(
                {
                    "start_hour": start_h,
                    "end_hour": end_h,
                    "score": round(score, 3),
                    "label": f"{start_h:02d}:00 - {end_h:02d}:00",
                }
            )
            if len(selected) >= top_k:
                break

        return selected

    def to_dict(self) -> Dict[str, Any]:
        return {
            "heatmap": self.heatmap.round(3).tolist(),
            "top_windows": self.get_top_windows(),
            "fitted": self.fitted,
        }
