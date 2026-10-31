import math
import time
from typing import Any, Dict, Optional

from ..causal.cate import CausalForestDML
from ..learners.notif_value import NotificationValueLearner


class GatekeeperPolicy:
    """
    Utility-Maximizing Causal Gatekeeper.
    Compares immediate interruption utility u_now against deferred delivery utility u_defer.
    Strict Invariant: Absolute fail-open for VIPs, OTPs, and emergencies.
    """

    def __init__(
        self,
        value_learner: Optional[NotificationValueLearner] = None,
        dml: Optional[CausalForestDML] = None,
        default_hold_minutes: int = 25,
    ):
        self.value_learner = value_learner or NotificationValueLearner()
        self.dml = dml or CausalForestDML()
        self.default_hold_minutes = default_hold_minutes

    def decide(
        self,
        notification: Dict[str, Any],
        context: Dict[str, Any],
        now_ms: Optional[int] = None,
    ) -> Dict[str, Any]:
        if now_ms is None:
            now_ms = int(time.time() * 1000)

        # 1. Absolute Fail-Open Safety Guards
        if notification.get("is_vip") or notification.get("is_emergency") or notification.get("is_otp"):
            return {
                "action": "ALLOW",
                "reason": "safety_override_vip_or_emergency",
                "hold_until": None,
                "u_now": 1.0,
                "u_defer": 0.0,
            }

        cat = str(notification.get("category", "communication"))
        sender_hash = notification.get("sender_hash")
        urgency = float(notification.get("urgency_score", 0.0))

        # Context features
        focus_depth = float(context.get("focus_depth", 0.5))
        fatigue = float(context.get("fatigue", 0.3))
        in_focus_block = bool(context.get("in_focus_block", focus_depth > 0.6))

        # If user is completely idle or outside deep focus, deliver directly
        if not in_focus_block and focus_depth < 0.4:
            return {
                "action": "ALLOW",
                "reason": "user_not_in_deep_focus",
                "hold_until": None,
                "u_now": 0.8,
                "u_defer": 0.5,
            }

        # 2. Information Value V in [0, 1]
        v_info = self.value_learner.score_utility(cat, sender_hash, is_vip=False)
        v_info = max(v_info, urgency)

        # 3. Interruption Cost C in utility units (scaled from minutes of flow destroyed)
        # Deep focus penalty is steep (up to 0.85 utility points)
        c_interrupt = (focus_depth * 0.70) + (fatigue * 0.20)

        # Immediate utility
        u_now = v_info - c_interrupt

        # Deferred utility (information decays slightly, but delivered during natural break where C ~ 0.05)
        decay_factor = math.exp(-0.015 * self.default_hold_minutes)  # ~30% decay over 25 min
        u_defer = (v_info * decay_factor) - 0.08

        if u_now >= u_defer:
            return {
                "action": "ALLOW",
                "reason": "immediate_utility_exceeds_deferral",
                "hold_until": None,
                "u_now": round(u_now, 3),
                "u_defer": round(u_defer, 3),
            }
        else:
            hold_until = now_ms + (self.default_hold_minutes * 60 * 1000)
            return {
                "action": "HOLD",
                "reason": "flow_preservation_exceeds_delay_cost",
                "hold_until": hold_until,
                "u_now": round(u_now, 3),
                "u_defer": round(u_defer, 3),
            }
