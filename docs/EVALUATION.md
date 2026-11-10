# Evaluation & Benchmark Verification

Hunch evaluates attention loss using reproducible synthetic persona simulations.

## Running the Benchmark
```bash
cd engine
pytest tests/ -k "test_evaluation" -v
```

## Metrics
- **Decision Latency:** Evaluated at < 10ms on modern ARM/x86 processors.
- **Privacy Leakage:** Evaluated by property tests asserting 0 raw content violations across 10,000 synthetic event sequences.
- **Counterfactual Calibration:** Estimated via double machine learning models comparing predicted resumption delay against observed intervals.
