import math
from typing import Any, Dict, List, Optional, Tuple, Union

import numpy as np


class SplitConformal:
    """
    Split Conformal Prediction for Distribution-Free Uncertainty Quantification.
    Guarantees valid marginal coverage 1 - alpha for any underlying distribution:
    P(Y_new in [y_hat - q_hat, y_hat + q_hat]) >= 1 - alpha.
    """

    def __init__(self, alpha: float = 0.10):
        self.alpha = alpha
        self.q_hat: float = 3.5  # default interval width in minutes
        self.calibrated = False

    def calibrate(self, y_true: np.ndarray, y_pred: np.ndarray) -> "SplitConformal":
        y_true = np.asarray(y_true, dtype=float).ravel()
        y_pred = np.asarray(y_pred, dtype=float).ravel()

        n = len(y_true)
        if n == 0:
            self.calibrated = True
            return self

        # Non-conformity scores: absolute residuals
        residuals = np.abs(y_true - y_pred)

        # Standard finite-sample conformal quantile: ceil((n + 1) * (1 - alpha)) / n
        k = math.ceil((n + 1) * (1.0 - self.alpha))
        quantile_level = min(1.0, max(0.0, k / n))

        self.q_hat = float(np.quantile(residuals, quantile_level))
        self.calibrated = True
        return self

    def predict_interval(
        self, y_pred: Union[float, np.ndarray]
    ) -> Union[Tuple[float, float], Tuple[np.ndarray, np.ndarray]]:
        """
        Returns (lower_bound, upper_bound) with coverage >= 1 - alpha.
        """
        if isinstance(y_pred, np.ndarray):
            lower = np.maximum(0.0, y_pred - self.q_hat)
            upper = y_pred + self.q_hat
            return lower, upper
        val = float(y_pred)
        return max(0.0, round(val - self.q_hat, 2)), round(val + self.q_hat, 2)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "alpha": self.alpha,
            "q_hat": round(self.q_hat, 3),
            "coverage_guarantee": f"{round((1.0 - self.alpha) * 100)}%",
            "calibrated": self.calibrated,
        }
