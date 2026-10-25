# Stage 7A open decisions

- Spec 12.4 does not give numeric criteria for errors rising, edits low or long pauses with deletions. No thresholds are invented. `setStuckEvidence(boolean)` is the explicit hook for reviewed extension evidence in 7C; automatic count-to-stuck mapping is pending.
- Rule confidence is uncalibrated: FocusRunner emits 0 rather than an invented probability. A reviewed confidence policy is pending.
- The table has no state for communication/utility/unknown or rapidly switching work. These return offline conservatively; a distinct unknown state would require a shared contract change. They are never counted as flowing.
- Continuous activity currently means fresh process and idle reports, idle under 60 seconds, and fewer than 3 switches in the last minute. Exact edit/activity continuity metrics remain pending 7C. Initial idle transition threshold is 60 seconds, aligned with the idle_open boundary, not a measured AW hardware fact.
- Real AW was unavailable on 127.0.0.1:5600. Fake HTTP tests exercise a bucket-ID keyed object and events arrays with timestamp, duration, data. This shape still needs live owner verification; no raw response was logged.
- JSONL startup scans each retained file to recover the maximum sequence; tail-only optimized recovery remains planned before high-volume target validation. Recovery currently keeps recent rows from the active file in the ring, not all rotated history.
- Storage retry is bounded exponential backoff, driven every 5 seconds by the runtime. Full disk keeps bounded pending/ring memory and visibly pauses persistence. Abrupt termination can lose unpersisted memory; the failure state is never reported as durable success.
- C0's existing videoState/ACK concerns are outside this branch. 7C/8L must resolve them before dependent integration, even though C0 has been merged by the owner.
