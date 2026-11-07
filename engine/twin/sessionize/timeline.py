import json
from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional, Union

import pandas as pd


@dataclass
class TimelineSlice:
    start_ts: int
    end_ts: int
    duration_s: float
    idle_s: float
    keystrokes: int
    mouse_px: float
    active_app: str
    category: str
    device_id: str
    phone_unlocked: bool = False
    phone_app: str = ""


def parse_payload_safe(payload: Union[str, Dict[str, Any], None]) -> Dict[str, Any]:
    """
    Safely parses JSON payload from SQLite events table.
    Guarantees no TypeError on raw strings.
    """
    if payload is None:
        return {}
    if isinstance(payload, dict):
        return payload
    if isinstance(payload, str):
        payload_str = payload.strip()
        if not payload_str:
            return {}
        try:
            parsed = json.loads(payload_str)
            return parsed if isinstance(parsed, dict) else {}
        except Exception:
            return {}
    return {}


def build_timeline(
    events: List[Dict[str, Any]],
    input_summaries: Optional[List[Dict[str, Any]]] = None,
    session_idle_threshold_s: float = 300.0,
) -> List[TimelineSlice]:
    """
    Converts raw events and input summaries into a continuous timeline of active slices.
    """
    slices: List[TimelineSlice] = []

    # Priority 1: Use structured input summaries if available
    if input_summaries:
        for s in input_summaries:
            w_start = int(s.get("window_start", 0))
            w_end = int(s.get("window_end", 0))
            dur = max(0.0, (w_end - w_start) / 1000.0)
            idle = float(s.get("idle_s", 0.0))
            keys = int(s.get("keystrokes", 0))
            mouse = float(s.get("mouse_px", 0.0))
            app = str(s.get("active_app", "unknown"))
            cat = str(s.get("category", "unknown"))
            dev = str(s.get("device_id", "laptop"))

            slices.append(
                TimelineSlice(
                    start_ts=w_start,
                    end_ts=w_end,
                    duration_s=dur,
                    idle_s=idle,
                    keystrokes=keys,
                    mouse_px=mouse,
                    active_app=app,
                    category=cat,
                    device_id=dev,
                )
            )

    # Priority 2: Extract from raw events table using safe payload parsing
    if not slices and events:
        for ev in events:
            ts = int(ev.get("ts", 0))
            dev = str(ev.get("device_id", "unknown"))
            ev_type = str(ev.get("event_type", ""))
            payload = parse_payload_safe(ev.get("payload"))

            idle_s = float(payload.get("idle_s", 0.0))
            keys = int(payload.get("keystrokes", 0))
            mouse = float(payload.get("mouse_px", 0.0))
            app = str(payload.get("active_app", ev_type))
            cat = str(payload.get("category", "work" if keys > 0 else "idle"))

            slices.append(
                TimelineSlice(
                    start_ts=ts,
                    end_ts=ts + 60000,
                    duration_s=60.0,
                    idle_s=idle_s,
                    keystrokes=keys,
                    mouse_px=mouse,
                    active_app=app,
                    category=cat,
                    device_id=dev,
                )
            )

    slices.sort(key=lambda s: s.start_ts)
    return slices


def extract_phone_intervals(
    phone_events: List[Dict[str, Any]],
) -> List[Dict[str, Any]]:
    """
    Extracts phone active / unlocked intervals from phone events:
    ACTION_SCREEN_ON, ACTION_USER_PRESENT, ACTION_SCREEN_OFF, notif_opened, app_opened.
    """
    if not phone_events:
        return []

    sorted_events = sorted(phone_events, key=lambda e: int(e.get("ts", 0)))
    intervals: List[Dict[str, Any]] = []
    current_unlock: Optional[int] = None
    current_app = ""

    for ev in sorted_events:
        ts = int(ev.get("ts", 0))
        ev_type = str(ev.get("event_type", ""))
        payload = parse_payload_safe(ev.get("payload"))
        app = str(
            payload.get("app_pkg")
            or payload.get("active_app")
            or ev.get("app_pkg", "")
            or payload.get("dest_app", "")
        )

        if ev_type in ("ACTION_SCREEN_ON", "ACTION_USER_PRESENT", "notif_opened", "app_opened", "pickup"):
            if current_unlock is None:
                current_unlock = ts
                current_app = app or "phone_launcher"
            elif app:
                current_app = app
        elif ev_type in ("ACTION_SCREEN_OFF", "screen_off", "phone_idle"):
            if current_unlock is not None:
                intervals.append({
                    "start_ts": current_unlock,
                    "end_ts": max(current_unlock + 1000, ts),
                    "dest_app": current_app or "unknown",
                })
                current_unlock = None
                current_app = ""

    if current_unlock is not None:
        # Default 60-second window if no explicit SCREEN_OFF
        intervals.append({
            "start_ts": current_unlock,
            "end_ts": current_unlock + 60_000,
            "dest_app": current_app or "unknown",
        })

    return intervals


def merge_cross_device_timeline(
    laptop_slices: List[TimelineSlice],
    phone_events: List[Dict[str, Any]],
    resolution_s: float = 1.0,
) -> List[TimelineSlice]:
    """
    Merges laptop slices and phone pickup/screen events at 1s resolution.
    Annotates whether phone was unlocked/used during the focus block.
    """
    if not phone_events:
        return laptop_slices

    phone_intervals = extract_phone_intervals(phone_events)
    if not phone_intervals:
        return laptop_slices

    merged: List[TimelineSlice] = []
    for s in laptop_slices:
        # Check if any phone unlock interval overlaps this slice
        overlapping = [
            p for p in phone_intervals
            if max(s.start_ts, p["start_ts"]) < min(s.end_ts, p["end_ts"])
        ]
        if not overlapping:
            merged.append(s)
            continue

        # Split or annotate slice with phone unlock details
        dest_app = overlapping[0]["dest_app"]
        annotated_slice = TimelineSlice(
            start_ts=s.start_ts,
            end_ts=s.end_ts,
            duration_s=s.duration_s,
            idle_s=s.idle_s,
            keystrokes=s.keystrokes,
            mouse_px=s.mouse_px,
            active_app=s.active_app,
            category=s.category,
            device_id=s.device_id,
            phone_unlocked=True,
            phone_app=dest_app,
        )
        merged.append(annotated_slice)

    merged.sort(key=lambda x: x.start_ts)
    return merged

