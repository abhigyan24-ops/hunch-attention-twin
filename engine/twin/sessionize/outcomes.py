from dataclasses import dataclass
from typing import Any, Dict, List, Optional

from .timeline import TimelineSlice, extract_phone_intervals


@dataclass
class NotifOutcome:
    notif_id: str
    shown: int
    excursion_s: float
    resume_s: float
    attended: int
    y_observed: float
    horizon_s: float = 3600.0
    react_latency_s: float = 0.0
    dest_app: str = ""
    resume_lag_s: float = 0.0

    def to_dict(self) -> Dict[str, Any]:
        return {
            "notif_id": self.notif_id,
            "shown": self.shown,
            "excursion_s": self.excursion_s,
            "resume_s": self.resume_s,
            "attended": self.attended,
            "y_observed": self.y_observed,
            "horizon_s": self.horizon_s,
            "react_latency_s": self.react_latency_s,
            "dest_app": self.dest_app,
            "resume_lag_s": self.resume_lag_s,
        }


def label_outcomes(
    notifications: List[Dict[str, Any]],
    slices: List[TimelineSlice],
    phone_events: Optional[List[Dict[str, Any]]] = None,
    horizon_s: float = 3600.0,
    glance_cost_s: float = 24.0,
) -> List[NotifOutcome]:
    """
    Labels counterfactual outcomes for notifications across both shown (A=1) and held (A=0) arms
    over an equal horizon H (default 3600s / 1 hour).
    Measures cross-device pickup, react latency, destination app, excursion, and resume lag
    (time until laptop typing returns to >= 50% baseline).
    """
    if not notifications:
        return []

    # Map slices by timestamp for fast range lookup
    slices_by_time = sorted(slices, key=lambda s: s.start_ts)
    phone_intervals = extract_phone_intervals(phone_events) if phone_events else []
    outcomes: List[NotifOutcome] = []

    for n in notifications:
        notif_id = str(n.get("notif_id", ""))
        post_ts = int(n.get("post_time", 0))
        action = str(n.get("action_taken", "ALLOW")).upper()
        is_shown = 0 if action in ("HOLD", "SILENT", "DELAY") else 1
        horizon_end_ts = post_ts + int(horizon_s * 1000)

        # Baseline laptop typing rate in the 5 min (300s) prior to notification
        prior_slices = [
            s for s in slices_by_time
            if s.device_id != "phone" and (post_ts - 300_000) <= s.start_ts <= post_ts
        ]
        prior_dur = sum(s.duration_s for s in prior_slices)
        prior_keys = sum(s.keystrokes for s in prior_slices)
        baseline_rate = (prior_keys / prior_dur) if prior_dur > 0 else 2.0  # default 2 keys/s
        baseline_50_pct = 0.5 * baseline_rate

        # Slices in horizon window [post_ts, post_ts + horizon_s]
        win_slices = [s for s in slices_by_time if post_ts <= s.start_ts <= horizon_end_ts]

        if is_shown == 1:
            # Shown arm: check for phone pickup or distraction within 5 minutes (300s)
            matching_phone = [
                p for p in phone_intervals
                if post_ts <= p["start_ts"] <= post_ts + 300_000
            ]

            attended = 0
            react_latency_s = 0.0
            dest_app = str(n.get("app_pkg", ""))
            excursion_s = 0.0
            resume_lag_s = 0.0
            excursion_end_ts = post_ts

            if matching_phone:
                p = matching_phone[0]
                attended = 1
                react_latency_s = max(0.0, (p["start_ts"] - post_ts) / 1000.0)
                dest_app = p["dest_app"] or dest_app or "phone_app"
                excursion_s = max(0.0, (p["end_ts"] - p["start_ts"]) / 1000.0)
                excursion_end_ts = p["end_ts"]
            else:
                immediate_slices = [s for s in win_slices if s.start_ts <= post_ts + 300_000]
                for s in immediate_slices:
                    if s.phone_unlocked or s.category in ("social", "communication", "entertainment", "distraction"):
                        attended = 1
                        react_latency_s = max(0.0, (s.start_ts - post_ts) / 1000.0)
                        dest_app = s.phone_app or s.active_app or dest_app or "distraction"
                        excursion_end_ts = s.end_ts
                        break

                if attended:
                    in_excursion = True
                    for s in win_slices:
                        if s.start_ts >= post_ts:
                            if in_excursion:
                                if s.phone_unlocked or s.category in ("social", "communication", "entertainment", "distraction"):
                                    excursion_s += s.duration_s
                                    excursion_end_ts = s.end_ts
                                else:
                                    in_excursion = False
                                    break

            if attended:
                # Calculate resume lag: time until laptop typing returns to >= 50% baseline
                after_slices = [
                    s for s in win_slices
                    if s.start_ts >= excursion_end_ts and s.device_id != "phone"
                ]
                resumed = False
                for asl in after_slices:
                    typing_rate = asl.keystrokes / max(1.0, asl.duration_s)
                    if typing_rate >= baseline_50_pct or asl.category in ("coding", "work", "learning", "terminal", "docs"):
                        resumed = True
                        resume_lag_s = max(0.0, (asl.start_ts - excursion_end_ts) / 1000.0) + min(30.0, asl.idle_s)
                        break
                if not resumed:
                    resume_lag_s = min(600.0, max(30.0, (horizon_end_ts - excursion_end_ts) / 1000.0))

                y_cost = glance_cost_s + excursion_s + resume_lag_s
            else:
                y_cost = glance_cost_s

            outcomes.append(
                NotifOutcome(
                    notif_id=notif_id,
                    shown=1,
                    excursion_s=round(excursion_s, 2),
                    resume_s=round(resume_lag_s, 2),
                    attended=attended,
                    y_observed=round(y_cost, 2),
                    horizon_s=horizon_s,
                    react_latency_s=round(react_latency_s, 2),
                    dest_app=dest_app,
                    resume_lag_s=round(resume_lag_s, 2),
                )
            )

        else:
            # Held arm: observe spontaneous drift or pickup over the exact same equal horizon H
            attended = 0
            drift_detected = False
            excursion_s = 0.0
            resume_lag_s = 0.0
            react_latency_s = 0.0
            dest_app = ""
            excursion_end_ts = post_ts

            matching_phone = [
                p for p in phone_intervals
                if post_ts <= p["start_ts"] <= horizon_end_ts
            ]
            if matching_phone:
                p = matching_phone[0]
                drift_detected = True
                react_latency_s = max(0.0, (p["start_ts"] - post_ts) / 1000.0)
                dest_app = p["dest_app"]
                excursion_s = max(0.0, (p["end_ts"] - p["start_ts"]) / 1000.0)
                excursion_end_ts = p["end_ts"]
            else:
                for s in win_slices:
                    if (s.phone_unlocked or s.category in ("social", "entertainment", "distraction")) and (s.duration_s - s.idle_s) > 30.0:
                        drift_detected = True
                        excursion_s += s.duration_s
                        dest_app = s.phone_app or s.active_app or "distraction"
                        excursion_end_ts = s.end_ts
                    elif drift_detected and s.category in ("coding", "work", "learning", "docs", "terminal"):
                        break

            if drift_detected:
                after_slices = [
                    s for s in win_slices
                    if s.start_ts >= excursion_end_ts and s.device_id != "phone"
                ]
                resumed = False
                for asl in after_slices:
                    typing_rate = asl.keystrokes / max(1.0, asl.duration_s)
                    if typing_rate >= baseline_50_pct or asl.category in ("coding", "work", "learning", "docs", "terminal"):
                        resumed = True
                        resume_lag_s = max(0.0, (asl.start_ts - excursion_end_ts) / 1000.0) + min(30.0, asl.idle_s)
                        break
                if not resumed:
                    resume_lag_s = min(600.0, max(20.0, (horizon_end_ts - excursion_end_ts) / 1000.0))
                y_cost = excursion_s + resume_lag_s
            else:
                y_cost = 0.0

            outcomes.append(
                NotifOutcome(
                    notif_id=notif_id,
                    shown=0,
                    excursion_s=round(excursion_s, 2),
                    resume_s=round(resume_lag_s, 2),
                    attended=0,
                    y_observed=round(y_cost, 2),
                    horizon_s=horizon_s,
                    react_latency_s=round(react_latency_s, 2),
                    dest_app=dest_app,
                    resume_lag_s=round(resume_lag_s, 2),
                )
            )

    return outcomes

