import random
from typing import Any, Dict, Optional, Tuple


class EpsilonExplorer:
    """
    Randomized Exploration Arm for Unconfounded Causal Estimation.
    Explores eps fraction of non-critical notifications to break observational censorship.
    """

    def __init__(self, eps: float = 0.08, seed: int = 12345):
        self.eps = eps
        self.rng = random.Random(seed)

    def should_explore(
        self, notification: Dict[str, Any], context: Dict[str, Any]
    ) -> Tuple[bool, Optional[str]]:
        # Never explore critical safety alerts
        if notification.get("is_vip") or notification.get("is_emergency") or notification.get("is_otp"):
            return False, None

        if self.rng.random() < self.eps:
            # 50/50 randomized arm assignment within exploration tranche
            assigned_arm = "HOLD" if self.rng.random() < 0.50 else "ALLOW"
            return True, assigned_arm

        return False, None
