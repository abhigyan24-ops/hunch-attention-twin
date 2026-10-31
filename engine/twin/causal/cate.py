from typing import Any, Dict, List, Optional, Tuple, Union

import numpy as np
from sklearn.ensemble import GradientBoostingRegressor, RandomForestRegressor
from sklearn.model_selection import KFold


class CausalForestDML:
    """
    Double Machine Learning (DML) / R-Learner for Conditional Average Treatment Effect (CATE).
    Estimates tau(X) = E[Y(1) - Y(0) | X] using Neyman-orthogonal score equations and cross-fitting.
    Y: attentional cost in minutes (time lost)
    T: treatment indicator (1 = shown, 0 = held)
    X: context vector [urgency, focus_depth, fatigue, is_peak, prior_duration]
    """

    def __init__(self, n_splits: int = 3, random_state: int = 42):
        self.n_splits = n_splits
        self.random_state = random_state
        self.model_y = GradientBoostingRegressor(n_estimators=50, max_depth=3, random_state=random_state)
        self.model_t = GradientBoostingRegressor(n_estimators=50, max_depth=3, random_state=random_state)
        self.model_tau = RandomForestRegressor(n_estimators=50, max_depth=4, random_state=random_state)
        self.fitted = False
        self.feature_names = ["urgency", "focus_depth", "fatigue", "is_peak", "prior_duration_min"]

    def fit(self, X: np.ndarray, T: np.ndarray, Y: np.ndarray) -> "CausalForestDML":
        X = np.asarray(X, dtype=float)
        T = np.asarray(T, dtype=float).ravel()
        Y = np.asarray(Y, dtype=float).ravel()

        n_samples = len(X)
        if n_samples < 15:
            # Under small sample size, fit directly without cross-fitting
            self.model_y.fit(X, Y)
            self.model_t.fit(X, T)
            y_res = Y - self.model_y.predict(X)
            t_res = T - self.model_t.predict(X)
            # Avoid division by zero in R-learner weights
            weights = np.maximum(1e-4, t_res**2)
            pseudo_outcomes = y_res / np.where(np.abs(t_res) < 1e-3, 1e-3, t_res)
            self.model_tau.fit(X, pseudo_outcomes, sample_weight=weights)
            self.fitted = True
            return self

        kf = KFold(n_splits=self.n_splits, shuffle=True, random_state=self.random_state)
        y_res = np.zeros(n_samples)
        t_res = np.zeros(n_samples)

        for train_idx, val_idx in kf.split(X):
            X_tr, X_val = X[train_idx], X[val_idx]
            Y_tr, Y_val = Y[train_idx], Y[val_idx]
            T_tr, T_val = T[train_idx], T[val_idx]

            m_y = GradientBoostingRegressor(n_estimators=30, max_depth=3, random_state=self.random_state)
            m_t = GradientBoostingRegressor(n_estimators=30, max_depth=3, random_state=self.random_state)

            m_y.fit(X_tr, Y_tr)
            m_t.fit(X_tr, T_tr)

            y_res[val_idx] = Y_val - m_y.predict(X_val)
            t_res[val_idx] = T_val - m_t.predict(X_val)

        weights = np.maximum(1e-4, t_res**2)
        pseudo_outcomes = y_res / np.where(np.abs(t_res) < 1e-3, 1e-3, t_res)

        self.model_tau.fit(X, pseudo_outcomes, sample_weight=weights)
        self.fitted = True
        return self

    def predict_cate(self, X: np.ndarray) -> np.ndarray:
        """
        Predicts tau(X) = E[Y(1) - Y(0) | X] (minutes of attention lost by showing vs holding).
        Positive tau indicates holding saves attention.
        """
        X = np.asarray(X, dtype=float)
        if not self.fitted:
            # Baseline heuristic: if in deep focus, showing costs ~12 min more than holding
            focus = X[:, 1] if X.ndim > 1 else np.array([X[1]])
            return np.maximum(1.0, 5.0 + (10.0 * focus))
        return self.model_tau.predict(X)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "fitted": self.fitted,
            "feature_names": self.feature_names,
            "n_splits": self.n_splits,
        }
