import hashlib
import json
import math
import random
import time
from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional, Tuple

import numpy as np

from ..twin.ingest.db import Database, get_db


@dataclass
class SyntheticPersona:
    name: str = "Rahul"
    role: str = "developer"
    peak_hours: List[int] = field(default_factory=lambda: [10, 11, 15, 21])
    fatigue_rates: List[float] = field(default_factory=lambda: [1.0, 1.2, 1.5])
    self_rate_per_hour: float = 1.4
    attend_base: Dict[str, float] = field(
        default_factory=lambda: {
            "social_notif": 0.62,
            "msg_known": 0.55,
            "promo": 0.12,
            "work": 0.85,
            "system": 0.25,
            "entertainment": 0.58,
        }
    )
    excursion_params: Dict[str, Tuple[float, float]] = field(
        default_factory=lambda: {
            "social": (2.6, 0.7),
            "short_video": (2.8, 0.9),
            "communication": (1.8, 0.5),
            "shopping": (2.0, 0.6),
            "work": (1.5, 0.4),
        }
    )
    resume_mu: float = 2.0
    resume_sigma: float = 0.6
    glance_cost_min: float = 0.4


PERSONAS: Dict[str, SyntheticPersona] = {
    "Rahul": SyntheticPersona(
        name="Rahul",
        role="developer",
        peak_hours=[10, 11, 15, 21],
        fatigue_rates=[1.0, 1.2, 1.5],
        self_rate_per_hour=1.4,
        attend_base={"social_notif": 0.62, "msg_known": 0.55, "promo": 0.12, "work": 0.85, "system": 0.25},
    ),
    "Anika": SyntheticPersona(
        name="Anika",
        role="student",
        peak_hours=[21, 22, 23, 0],
        fatigue_rates=[1.0, 1.15, 1.4],
        self_rate_per_hour=1.2,
        attend_base={"lecture_watch": 0.04, "shorts": 0.78, "social_notif": 0.58, "msg_known": 0.38, "promo": 0.08},
    ),
    "Kabir": SyntheticPersona(
        name="Kabir",
        role="gamer-dev",
        peak_hours=[10, 15, 22],
        fatigue_rates=[1.0, 1.2, 1.5],
        self_rate_per_hour=4.5,
        attend_base={"social_notif": 0.10, "msg_known": 0.12, "promo": 0.03, "work": 0.70, "gaming": 0.90},
    ),
}


def get_persona(name: str = "Rahul") -> SyntheticPersona:
    return PERSONAS.get(name, PERSONAS["Rahul"])


def hash_sender(name: str, salt: str = "dhyan-salt-persona") -> str:
    return hashlib.sha256(f"{salt}:{name}".encode("utf-8")).hexdigest()[:16]


def generate_dataset(
    persona: SyntheticPersona,
    days: int = 7,
    start_ts: Optional[int] = None,
    seed: int = 42,
) -> Dict[str, List[Dict[str, Any]]]:
    """
    Generates a deterministic simulated timeline conforming to docs/PERSONA_WORLD.md.
    Equal-horizon H=3600s counterfactual tracking is generated for shown and held arms.
    """
    rng = np.random.RandomState(seed)
    py_rng = random.Random(seed)

    if start_ts is None:
        start_ts = int(time.time() * 1000) - (days * 86400 * 1000)

    events: List[Dict[str, Any]] = []
    input_summaries: List[Dict[str, Any]] = []
    notifications: List[Dict[str, Any]] = []
    focus_blocks: List[Dict[str, Any]] = []
    outcomes: List[Dict[str, Any]] = []
    telemetry: List[Dict[str, Any]] = []

    categories = list(persona.attend_base.keys())
    apps = {
        "social_notif": "com.instagram.android",
        "msg_known": "org.thoughtcrime.securesms",
        "promo": "in.amazon.mShop.android.shopping",
        "work": "com.slack",
        "system": "android",
        "entertainment": "com.google.android.youtube",
        "shorts": "com.google.android.youtube",
        "lecture_watch": "org.coursera.android",
        "gaming": "com.epicgames.portal",
    }

    curr_ts = start_ts
    notif_seq = 0
    block_seq = 0

    n_shown = 0
    n_accepted = 0
    n_dismissed = 0
    mult = 1.0

    for day in range(days):
        day_offset = day * 86400 * 1000
        for hour in range(24):
            hour_ts = start_ts + day_offset + (hour * 3600 * 1000)
            is_peak = hour in persona.peak_hours
            fatigue_idx = 0 if hour < 12 else (1 if hour < 18 else 2)
            fatigue_mult = persona.fatigue_rates[fatigue_idx]

            # 1. Focus Blocks during working/peak hours
            if (9 <= hour <= 12) or (14 <= hour <= 18) or (is_peak and hour >= 20):
                block_duration = py_rng.randint(25 * 60, 50 * 60)
                block_id = f"blk_{day}_{hour}_{block_seq}"
                block_seq += 1
                interrupt_cnt = py_rng.randint(0, 3)
                depth = max(0.1, min(1.0, 0.75 + (0.15 if is_peak else -0.1) - (0.05 * fatigue_idx)))
                fatigue_score = min(1.0, 0.2 + (0.25 * fatigue_idx) + (0.05 * interrupt_cnt))

                focus_blocks.append(
                    {
                        "block_id": block_id,
                        "start_ts": hour_ts,
                        "end_ts": hour_ts + (block_duration * 1000),
                        "duration_s": float(block_duration),
                        "interruptions_count": interrupt_cnt,
                        "focus_depth": round(depth, 3),
                        "primary_category": "coding" if persona.role == "developer" else "studying",
                        "fatigue_score": round(fatigue_score, 3),
                    }
                )

            # 2. Input Summaries (5-minute windows)
            for win_idx in range(12):
                win_start = hour_ts + (win_idx * 300 * 1000)
                win_end = win_start + (300 * 1000)
                is_working = (9 <= hour <= 18) or is_peak
                if is_working:
                    keystrokes = int(rng.normal(240, 60)) if is_working else int(rng.normal(20, 15))
                    mouse_px = float(rng.normal(3200, 800)) if is_working else float(rng.normal(400, 200))
                    idle_s = max(0.0, float(rng.normal(15.0, 8.0)))
                    category = "coding" if persona.role == "developer" else "work"
                    active_app = "Code.exe"
                else:
                    keystrokes = max(0, int(rng.normal(10, 8)))
                    mouse_px = max(0.0, float(rng.normal(120, 80)))
                    idle_s = min(300.0, max(0.0, float(rng.normal(210.0, 40.0))))
                    category = "idle"
                    active_app = "browser.exe"

                input_summaries.append(
                    {
                        "device_id": "laptop",
                        "window_start": win_start,
                        "window_end": win_end,
                        "keystrokes": max(0, keystrokes),
                        "mouse_px": max(0.0, round(mouse_px, 1)),
                        "idle_s": max(0.0, round(idle_s, 1)),
                        "active_app": active_app,
                        "category": category,
                    }
                )

                # Raw events
                events.append(
                    {
                        "device_id": "laptop",
                        "ts": win_start,
                        "event_type": "input_aggregate",
                        "payload": json.dumps(
                            {
                                "keystrokes": max(0, keystrokes),
                                "mouse_px": max(0.0, round(mouse_px, 1)),
                                "idle_s": max(0.0, round(idle_s, 1)),
                                "active_app": active_app,
                                "category": category,
                            }
                        ),
                    }
                )

            # 3. Notifications arriving during this hour
            hourly_rate = 2.0 if is_peak else 0.8
            n_notifs = rng.poisson(hourly_rate)
            for i in range(n_notifs):
                notif_seq += 1
                notif_id = f"notif_{day}_{hour}_{i}_{notif_seq}"
                notif_time = hour_ts + int(rng.uniform(0, 3500) * 1000)
                cat = py_rng.choice(categories)
                base_prob = persona.attend_base.get(cat, 0.4)
                p_attend = min(0.99, base_prob * fatigue_mult)

                # Exploration policy: 15% random holds
                is_held = py_rng.random() < 0.15
                action_taken = "HOLD" if is_held else "ALLOW"
                hold_until = (notif_time + 1800 * 1000) if is_held else None

                sender = f"user_{py_rng.randint(1, 8)}"
                sender_hash = hash_sender(sender)
                urgency = round(float(rng.beta(2, 5)), 2)

                notifications.append(
                    {
                        "notif_id": notif_id,
                        "device_id": "phone",
                        "post_time": notif_time,
                        "app_pkg": apps.get(cat, "com.unknown.app"),
                        "category": cat,
                        "sender_hash": sender_hash,
                        "is_vip": 1 if urgency > 0.7 else 0,
                        "urgency_score": urgency,
                        "action_taken": action_taken,
                        "hold_until": hold_until,
                    }
                )

                # Competing-risk outcome modeling (equal horizon H = 3600s)
                # Dwell / excursion distribution
                dest_cat = "short_video" if "short" in cat or py_rng.random() < 0.4 else "social"
                mu, sigma = persona.excursion_params.get(dest_cat, (2.6, 0.7))
                excursion_min = float(math.exp(rng.normal(mu, sigma)))
                excursion_s = excursion_min * 60.0

                resume_min = float(math.exp(rng.normal(persona.resume_mu, persona.resume_sigma)))
                resume_s = resume_min * 60.0

                if not is_held:
                    # Shown arm
                    attended = 1 if (py_rng.random() < p_attend) else 0
                    if attended:
                        cost_s = (persona.glance_cost_min * 60.0) + excursion_s + resume_s
                    else:
                        cost_s = persona.glance_cost_min * 60.0
                else:
                    # Held arm (competing risk: spontaneous drift without notif)
                    # Self-initiated arrival rate: persona.self_rate_per_hour
                    lambda_s = persona.self_rate_per_hour / 3600.0
                    u_self = max(1e-5, py_rng.random())
                    t_self = -math.log(u_self) / lambda_s
                    attended = 0
                    if t_self < 3600.0:
                        # Spontaneous break occurred within horizon
                        cost_s = excursion_s + resume_s
                    else:
                        cost_s = 0.0

                outcomes.append(
                    {
                        "notif_id": notif_id,
                        "shown": 0 if is_held else 1,
                        "excursion_s": round(excursion_s, 2),
                        "resume_s": round(resume_s, 2),
                        "attended": attended,
                        "y_observed": round(cost_s, 2),
                        "horizon_s": 3600.0,
                    }
                )

            # 4. Nudge telemetry & auto-tuning simulation (Stage 4 requirement)
            if is_peak and hour % 3 == 0:
                n_shown += 1
                # Self-tuning counter simulation: user accepts refocus when in deep focus
                if py_rng.random() < 0.7:
                    n_accepted += 1
                    ans = "accept"
                else:
                    n_dismissed += 1
                    ans = "dismiss"

                # Check auto-backoff condition:
                # If dismissed > accepted and dismissal rate > 60% after >= 5 nudges,
                # multiplier raises by 1.5x up to 3.0x
                total_nudges = n_accepted + n_dismissed
                if total_nudges >= 5 and (n_dismissed / total_nudges) > 0.60:
                    mult = min(3.0, mult * 1.5)
                elif total_nudges >= 5 and (n_accepted / total_nudges) > 0.70:
                    mult = max(1.0, mult * 0.9)

                telemetry.append(
                    {
                        "ts": hour_ts + 1800 * 1000,
                        "nudges_shown": 1,
                        "nudges_accepted": 1 if ans == "accept" else 0,
                        "nudges_dismissed": 1 if ans == "dismiss" else 0,
                        "threshold_multiplier": round(mult, 2),
                        "stage": 3 if ans == "dismiss" else 2,
                        "context": f"simulated_day_{day}_hr_{hour}",
                    }
                )

    return {
        "events": events,
        "input_summaries": input_summaries,
        "notifications": notifications,
        "focus_blocks": focus_blocks,
        "notif_outcomes": outcomes,
        "nudge_telemetry": telemetry,
    }


def seed_db_if_needed(db: Optional[Database] = None, force: bool = False) -> bool:
    if db is None:
        db = get_db()
    cnt = db.get_event_count()
    if cnt >= 50 and not force:
        return False

    persona = get_persona("Rahul")
    data = generate_dataset(persona, days=7, seed=42)

    db.insert_events(data["events"])
    db.insert_input_summaries(data["input_summaries"])

    with db.get_connection() as conn:
        for notif in data["notifications"]:
            conn.execute(
                """
                INSERT OR REPLACE INTO notifications (
                    notif_id, device_id, post_time, app_pkg, category,
                    sender_hash, is_vip, urgency_score, action_taken, hold_until
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    notif["notif_id"],
                    notif["device_id"],
                    notif["post_time"],
                    notif["app_pkg"],
                    notif["category"],
                    notif["sender_hash"],
                    notif["is_vip"],
                    notif["urgency_score"],
                    notif["action_taken"],
                    notif["hold_until"],
                ),
            )

        for blk in data["focus_blocks"]:
            conn.execute(
                """
                INSERT OR REPLACE INTO focus_blocks (
                    block_id, start_ts, end_ts, duration_s, interruptions_count,
                    focus_depth, primary_category, fatigue_score
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    blk["block_id"],
                    blk["start_ts"],
                    blk["end_ts"],
                    blk["duration_s"],
                    blk["interruptions_count"],
                    blk["focus_depth"],
                    blk["primary_category"],
                    blk["fatigue_score"],
                ),
            )

        for out in data["notif_outcomes"]:
            conn.execute(
                """
                INSERT INTO notif_outcomes (
                    notif_id, shown, excursion_s, resume_s, attended, y_observed, horizon_s
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    out["notif_id"],
                    out["shown"],
                    out["excursion_s"],
                    out["resume_s"],
                    out["attended"],
                    out["y_observed"],
                    out["horizon_s"],
                ),
            )

        for t in data["nudge_telemetry"]:
            conn.execute(
                """
                INSERT INTO nudge_telemetry (
                    ts, nudges_shown, nudges_accepted, nudges_dismissed,
                    threshold_multiplier, stage, context
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    t["ts"],
                    t["nudges_shown"],
                    t["nudges_accepted"],
                    t["nudges_dismissed"],
                    t["threshold_multiplier"],
                    t["stage"],
                    t["context"],
                ),
            )
        conn.commit()

    print(
        f"[Twin Seeder] Seeded database with {len(data['events'])} events, "
        f"{len(data['notifications'])} notifications, {len(data['focus_blocks'])} focus blocks, "
        f"{len(data['notif_outcomes'])} outcomes, and {len(data['nudge_telemetry'])} nudge telemetry records."
    )
    return True
