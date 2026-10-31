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
