import math
import time
from typing import Any, Dict, List, Optional, Tuple


class NotificationValueLearner:
    """
    Learner 4: Notification Information Value & Sender Utility Model
    Hierarchical Beta-Bernoulli conjugate model with exponential time-decay (half-life = 7 days).
    Maps notification category and salted sender hashes to predicted utility values in [0, 1].
    Preserves strict privacy invariants: sender identities are salted SHA-256 hashes only.
    """

    def __init__(self, half_life_days: float = 7.0, prior_alpha: float = 1.0, prior_beta: float = 1.0):
        self.half_life_ms = half_life_days * 86400 * 1000.0
        self.decay_lambda = math.log(2.0) / self.half_life_ms
        self.prior_alpha = prior_alpha
        self.prior_beta = prior_beta

        # Category level: cat -> (alpha, beta, last_update_ts)
        # Bayesian shrinkage priors: Beta(1, 1) default uninformative prior: E[V] = (1.0 + successes) / (2.0 + total) = 0.50
        standard_cats = ["work", "communication", "system", "entertainment", "social", "shopping", "promo"]
        self.category_posteriors: Dict[str, Tuple[float, float, int]] = {
            c: (self.prior_alpha, self.prior_beta, 0) for c in standard_cats
        }
        # Sender hash level: sender_hash -> (alpha, beta, last_update_ts)
        self.sender_posteriors: Dict[str, Tuple[float, float, int]] = {}
        self.real_notifs_count: int = 0
        self.fitted = False

    def fit(self, notifications: List[Dict[str, Any]], now_ms: Optional[int] = None) -> "NotificationValueLearner":
        self.real_notifs_count = len(notifications)
        # Reset to base Beta(1,1) priors before re-fitting
        standard_cats = ["work", "communication", "system", "entertainment", "social", "shopping", "promo"]
        self.category_posteriors = {
            c: (self.prior_alpha, self.prior_beta, 0) for c in standard_cats
        }
        self.sender_posteriors = {}

        if not notifications:
            self.fitted = True
            return self

        if now_ms is None:
            now_ms = int(time.time() * 1000)

        for n in notifications:
            cat = str(n.get("category", "unknown"))
            sender = n.get("sender_hash")
            is_vip = bool(n.get("is_vip", False))
            urgency = float(n.get("urgency_score", 0.0))
            ts = int(n.get("post_time", now_ms))

            # Weight by age: exp(-lambda * delta_t)
            age_ms = max(0, now_ms - ts)
            w = math.exp(-self.decay_lambda * age_ms)

            # Utility target: VIP is 1.0, urgency provides base signal
            target = 1.0 if is_vip else min(1.0, max(0.0, urgency))

            # Update category posterior: BetaValue(1, 1) shrinkage: (1.0 + successes) / (2.0 + total)
            c_alpha, c_beta, _ = self.category_posteriors.get(
                cat, (self.prior_alpha, self.prior_beta, now_ms)
            )
            self.category_posteriors[cat] = (
                c_alpha + (w * target),
                c_beta + (w * (1.0 - target)),
                now_ms,
            )

            # Update sender hash posterior if present
            if sender:
                s_alpha, s_beta, _ = self.sender_posteriors.get(
                    sender, (c_alpha * 0.5, c_beta * 0.5, now_ms)
                )
                self.sender_posteriors[sender] = (
                    s_alpha + (w * target),
                    s_beta + (w * (1.0 - target)),
                    now_ms,
                )

        self.fitted = True
        return self

    def score_utility(
        self,
        category: str,
        sender_hash: Optional[str] = None,
        is_vip: bool = False,
    ) -> float:
        """
        Computes the expected posterior utility E[V] = alpha / (alpha + beta) in [0, 1].
        """
        if is_vip:
            return 0.95

        if sender_hash and sender_hash in self.sender_posteriors:
            alpha, beta, _ = self.sender_posteriors[sender_hash]
            return float(alpha / (alpha + beta))

        alpha, beta, _ = self.category_posteriors.get(
            category, (self.prior_alpha, self.prior_beta, 0)
        )
        return float(alpha / (alpha + beta))

    def to_dict(self) -> Dict[str, Any]:
        return {
            "categories": {
                k: round(alpha / (alpha + beta), 3)
                for k, (alpha, beta, _) in self.category_posteriors.items()
            },
            "sender_count": len(self.sender_posteriors),
            "fitted": self.fitted,
            "real_notifs_count": self.real_notifs_count,
            "warming_up": self.real_notifs_count < 5,
        }
