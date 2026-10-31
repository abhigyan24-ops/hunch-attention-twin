from dataclasses import dataclass
from typing import Any, Dict, Optional

from ..ingest.db import Database, get_db


@dataclass
class NudgeStats:
    nudges_shown: int = 0
    nudges_accepted: int = 0
    nudges_dismissed: int = 0
    threshold_multiplier: float = 1.0


class NudgeBackoffEngine:
    """
    Self-Tuning Validation Counters & Auto-Backoff Engine (Requirement #4).
    Dynamically modulates the pet distraction threshold multiplier based on user interactions:
    - nudges_shown increments whenever a distraction nudge is presented.
    - nudges_accepted increments when the refocus CTA is clicked.
    - nudges_dismissed increments when dismissed or dragged/flung away within 4 seconds.
    - Auto-backoff: if dismissed > accepted and dismissal rate > 60% after >= 5 nudges,
      multiplier increases by 1.5x (capped at 3.0x).
    """

    def __init__(self, db: Optional[Database] = None):
        self.db = db or get_db()
        self._load_from_db()

    def _load_from_db(self) -> None:
        totals = self.db.get_nudge_totals()
        self.stats = NudgeStats(
            nudges_shown=totals["nudges_shown"],
            nudges_accepted=totals["nudges_accepted"],
            nudges_dismissed=totals["nudges_dismissed"],
            threshold_multiplier=totals["threshold_multiplier"],
        )

    def record_nudge_shown(self, stage: int = 2, context: str = "") -> NudgeStats:
        self.stats.nudges_shown += 1
        self.db.insert_nudge_telemetry(
            shown_delta=1,
            multiplier=self.stats.threshold_multiplier,
            stage=stage,
            context=context,
        )
        return self.stats

    def record_feedback(self, action: str, stage: int = 2, context: str = "") -> NudgeStats:
        """
        Action is either 'accepted' (clicked refocus CTA) or 'dismissed' (clicked X or flung away).
        """
        acc_delta = 0
        dism_delta = 0

        if action in ("accepted", "back_to_task", "refocus"):
            self.stats.nudges_accepted += 1
            acc_delta = 1
        elif action in ("dismissed", "flung", "dragged_away", "dismiss"):
            self.stats.nudges_dismissed += 1
            dism_delta = 1

        total_interactions = self.stats.nudges_accepted + self.stats.nudges_dismissed
        if total_interactions >= 5:
            dismissal_rate = self.stats.nudges_dismissed / total_interactions
            if self.stats.nudges_dismissed > self.stats.nudges_accepted and dismissal_rate > 0.60:
                # User is annoyed: back off by 1.5x
                self.stats.threshold_multiplier = min(3.0, round(self.stats.threshold_multiplier * 1.5, 2))
            elif dismissal_rate < 0.30:
                # User welcomes interventions: gently restore sensitivity
                self.stats.threshold_multiplier = max(1.0, round(self.stats.threshold_multiplier * 0.9, 2))

        self.db.insert_nudge_telemetry(
            shown_delta=0,
            accepted_delta=acc_delta,
            dismissed_delta=dism_delta,
            multiplier=self.stats.threshold_multiplier,
            stage=stage,
            context=context,
        )
        return self.stats

    def get_effective_distraction_thresholds(
        self,
        stage1_base_s: float = 5.0,
        stage2_base_s: float = 12.0,
        stage3_base_s: float = 30.0,
    ) -> Dict[str, float]:
        """
        Returns modulated stage thresholds scaled by the self-tuning multiplier.
        """
        m = self.stats.threshold_multiplier
        return {
            "multiplier": m,
            "stage1_brow_alert_s": round(stage1_base_s * m, 1),
            "stage2_edge_peek_s": round(stage2_base_s * m, 1),
            "stage3_walk_in_s": round(stage3_base_s * m, 1),
            "nudges_shown": self.stats.nudges_shown,
            "nudges_accepted": self.stats.nudges_accepted,
            "nudges_dismissed": self.stats.nudges_dismissed,
        }
