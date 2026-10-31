from dataclasses import dataclass
from typing import Any, Dict, List, Optional

from .timeline import TimelineSlice


@dataclass
class NotifOutcome:
    notif_id: str
    shown: int
    excursion_s: float
    resume_s: float
    attended: int
    y_observed: float
    horizon_s: float = 3600.0


def label_outcomes(
    notifications: List[Dict[str, Any]],
    slices: List[TimelineSlice],
    horizon_s: float = 3600.0,
    glance_cost_s: float = 24.0,
) -> List[NotifOutcome]:
    """
    Labels counterfactual outcomes for notifications across both shown (A=1) and held (A=0) arms
    over an equal horizon H (default 3600s / 1 hour).
    Prevents optimistic bias in held arms by measuring spontaneous drift without notifications.
    """
    if not notifications:
        return []

    # Map slices by timestamp for fast range lookup
    slices_by_time = sorted(slices, key=lambda s: s.start_ts)
    outcomes: List[NotifOutcome] = []

    for n in notifications:
        notif_id = str(n.get("notif_id", ""))
        post_ts = int(n.get("post_time", 0))
        action = str(n.get("action_taken", "ALLOW")).upper()
        is_shown = 0 if action in ("HOLD", "SILENT", "DELAY") else 1
        horizon_end_ts = post_ts + int(horizon_s * 1000)

        # Slices in horizon window [post_ts, post_ts + horizon_s]
        win_slices = [s for s in slices_by_time if post_ts <= s.start_ts <= horizon_end_ts]

        if is_shown == 1:
            # Shown arm: check for immediate engagement within 5 minutes (300s)
            immediate_slices = [s for s in win_slices if s.start_ts <= post_ts + 300_000]
            attended = 0
            excursion_s = 0.0
            resume_s = 0.0

            for s in immediate_slices:
                if s.category in ("social", "communication", "entertainment", "distraction"):
                    attended = 1
                    excursion_s += s.duration_s - s.idle_s
                    break

            if attended:
                # Accumulate excursion across contiguous distraction slices
                in_excursion = True
                for s in win_slices:
                    if in_excursion:
                        if s.category in ("social", "communication", "entertainment", "distraction"):
                            excursion_s += s.duration_s
                        else:
                            in_excursion = False
                            # Resume latency until active keystrokes/work resumed
                            resume_s = min(600.0, max(30.0, s.idle_s + 45.0))
                            break

                y_cost = glance_cost_s + excursion_s + resume_s
            else:
                y_cost = glance_cost_s

            outcomes.append(
                NotifOutcome(
                    notif_id=notif_id,
                    shown=1,
                    excursion_s=round(excursion_s, 2),
                    resume_s=round(resume_s, 2),
                    attended=attended,
                    y_observed=round(y_cost, 2),
                    horizon_s=horizon_s,
                )
            )

        else:
            # Held arm: observe spontaneous drift over the exact same equal horizon H
            attended = 0
            drift_detected = False
            excursion_s = 0.0
            resume_s = 0.0

            for s in win_slices:
                if s.category in ("social", "entertainment", "distraction") and (s.duration_s - s.idle_s) > 30.0:
                    drift_detected = True
                    excursion_s += s.duration_s
                elif drift_detected and s.category in ("coding", "work", "learning"):
                    resume_s = min(600.0, max(20.0, s.idle_s + 30.0))
                    break

            if drift_detected:
                y_cost = excursion_s + resume_s
            else:
                y_cost = 0.0

            outcomes.append(
                NotifOutcome(
                    notif_id=notif_id,
                    shown=0,
                    excursion_s=round(excursion_s, 2),
                    resume_s=round(resume_s, 2),
                    attended=0,
                    y_observed=round(y_cost, 2),
                    horizon_s=horizon_s,
                )
            )

    return outcomes
