"""Logical Circadian Day Boundary & Monotonic Timers.

Prevents splitting midnight coding sessions across two calendar days by treating
hours before 4:00 AM as belonging to the current logical work day.
"""

from __future__ import annotations

import datetime
from typing import Union


def get_logical_day_key(
    ts: Union[float, int, datetime.datetime, None] = None,
    rollover_hour: int = 4,
) -> str:
    """Returns 'YYYY-MM-DD' logical work day string.

    Hours 00:00 to (rollover_hour - 1) belong to the previous calendar day.
    Example: 2026-11-13 01:30 AM -> '2026-11-12'.
    Example: 2026-11-13 05:00 AM -> '2026-11-13'.
    """
    if ts is None:
        dt = datetime.datetime.now()
    elif isinstance(ts, (int, float)):
        sec = ts / 1000.0 if ts > 1e11 else float(ts)
        dt = datetime.datetime.fromtimestamp(sec)
    elif isinstance(ts, datetime.datetime):
        dt = ts
    else:
        dt = datetime.datetime.now()

    if dt.hour < rollover_hour:
        dt = dt - datetime.timedelta(days=1)
    return dt.strftime("%Y-%m-%d")


def get_logical_day_start_ts(
    ts: Union[float, int, datetime.datetime, None] = None,
    rollover_hour: int = 4,
) -> int:
    """Returns millisecond timestamp for the start of the logical work day (rollover_hour:00:00.000)."""
    if ts is None:
        dt = datetime.datetime.now()
    elif isinstance(ts, (int, float)):
        sec = ts / 1000.0 if ts > 1e11 else float(ts)
        dt = datetime.datetime.fromtimestamp(sec)
    elif isinstance(ts, datetime.datetime):
        dt = ts
    else:
        dt = datetime.datetime.now()

    if dt.hour < rollover_hour:
        logical_date = dt.date() - datetime.timedelta(days=1)
    else:
        logical_date = dt.date()

    start_dt = datetime.datetime.combine(logical_date, datetime.time(hour=rollover_hour, minute=0, second=0))
    return int(start_dt.timestamp() * 1000)


def clamp_monotonic_duration(duration: Union[int, float]) -> float:
    """Clamps calculated interval durations to non-negative floats to prevent clock-skew errors."""
    return max(0.0, float(duration))
