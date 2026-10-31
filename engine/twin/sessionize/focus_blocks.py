import math
from dataclasses import dataclass
from typing import Any, Dict, List, Optional

from .timeline import TimelineSlice


@dataclass
class FocusBlock:
    block_id: str
    start_ts: int
    end_ts: int
    duration_s: float
    interruptions_count: int
    focus_depth: float
    primary_category: str
    fatigue_score: float


def extract_focus_blocks(
    slices: List[TimelineSlice],
    min_duration_s: float = 600.0,
    max_gap_s: float = 180.0,
) -> List[FocusBlock]:
    """
    Extracts continuous high-engagement focus blocks from timeline slices.
    """
    if not slices:
        return []

    blocks: List[FocusBlock] = []
    curr_slices: List[TimelineSlice] = []

    for s in slices:
        # A slice is considered active if idle_s is low or user is typing/moving mouse
        is_active = (s.idle_s < (s.duration_s * 0.75)) and (s.keystrokes > 5 or s.mouse_px > 100)

        if not is_active:
            continue

        if not curr_slices:
            curr_slices.append(s)
            continue

        gap = (s.start_ts - curr_slices[-1].end_ts) / 1000.0
        if gap <= max_gap_s:
            curr_slices.append(s)
        else:
            block = _finalize_block(curr_slices, min_duration_s, len(blocks))
            if block:
                blocks.append(block)
            curr_slices = [s]

    if curr_slices:
        block = _finalize_block(curr_slices, min_duration_s, len(blocks))
        if block:
            blocks.append(block)

    return blocks


def _finalize_block(
    slices: List[TimelineSlice], min_duration_s: float, block_idx: int
) -> Optional[FocusBlock]:
    if not slices:
        return None

    start_ts = slices[0].start_ts
    end_ts = slices[-1].end_ts
    duration_s = max(0.0, (end_ts - start_ts) / 1000.0)

    if duration_s < min_duration_s:
        return None

    # Count category switches as micro-interruptions
    categories = [s.category for s in slices if s.category and s.category != "idle"]
    if not categories:
        categories = ["work"]

    from collections import Counter

    counts = Counter(categories)
    primary_cat = counts.most_common(1)[0][0]

    interruptions = 0
    prev_cat = slices[0].category
    for s in slices[1:]:
        if s.category != prev_cat and s.category in ("social", "entertainment", "distraction"):
            interruptions += 1
        prev_cat = s.category

    # Focus depth: ratio of active time to total time, penalized by interruptions
    total_idle = sum(s.idle_s for s in slices)
    active_ratio = max(0.1, 1.0 - (total_idle / max(1.0, duration_s)))
    depth = max(0.05, min(1.0, (active_ratio * 0.85) - (interruptions * 0.05)))

    # Fatigue increases with sustained duration (e.g. >45 min = 2700s)
    fatigue = min(1.0, max(0.0, (duration_s / 5400.0) + (interruptions * 0.08)))

    block_id = f"fb_{start_ts}_{block_idx}"

    return FocusBlock(
        block_id=block_id,
        start_ts=start_ts,
        end_ts=end_ts,
        duration_s=round(duration_s, 1),
        interruptions_count=interruptions,
        focus_depth=round(depth, 3),
        primary_category=primary_cat,
        fatigue_score=round(fatigue, 3),
    )
