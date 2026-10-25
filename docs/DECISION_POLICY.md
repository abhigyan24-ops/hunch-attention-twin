# Decision policy — Stage 5A

This document describes implementation outside the frozen version1 contract. Cost optimization is not a safety policy: future 5B/5C must run never-hold, fail-open and importance overrides first. No pipeline or actuation is wired here.

## Expected focus-minute costs

Inputs Eshow and Esupp are nonnegative means in focus-minutes, I in [0,1], kappaD is positive focus-minutes/hour at I=1, and tauRelease/tauDigest are nonnegative minutes. Eshow already includes glance cost; do not add it again. Strict session multiplies kappaD by .5. Constants come from the frozen PolicyDefaults, checked against core-spec AppendixA.

```
J(ALLOW)  = Eshow
J(DELAY)  = Esupp + effectiveKappaD * I^2 * (tauRelease / 60.0) + cRel * Eshow
J(DIGEST) = Esupp + effectiveKappaD * I^2 * (tauDigest / 60.0) + cDigest
J(SILENT) = Esupp + effectiveKappaD * I^2 * (tauDigest / 60.0) + lambdaMiss * I
```

CostBreakdown retains each J and each additive term. No percentile, simulation tail or P(L>1) enters J. Invalid ranges, nonfinite inputs and nonfinite overflow results throw fixed-token IllegalArgumentException internally; future pipeline must convert problems to ALLOW failedOpen. They are not standalone never-throw contract entry points.

The SILENT–DIGEST difference is lambdaMiss*I-cDigest, so SILENT is strictly cheaper below .16, equal at .16, and DIGEST cheaper above. The global selector's 1e-9 tie tolerance also applies: infinitesimally below the boundary, costs within tolerance deliberately tie, favouring DIGEST. Do not claim strict mathematical crossover overrides numerical tie handling.

Selection compares each cost to the global minimum, then picks the first within1e-9 in frozen Action order ALLOW,DELAY,DIGEST,SILENT. This avoids chained pairwise near-ties. Margin is the runner-up cost minus selected cost, floored at0 for numerical ties. Returned selection retains the complete cost breakdown.

Formula-derived regimes in tests: ALLOW (Eshow1,Esupp.9,I.5) gives J=[1,1.525,3.2,4.9]; SILENT (20,1,.1) gives [20,6.015,1.86,1.56]; DIGEST (20,1,.3) gives [20,6.135,2.34,3.04]; DELAY (4,.5,.6,release15,digest120) gives [4,1.77,3.46,5.66]. These are controlled unit-test cases, not persona predictions or pitch metrics.

## Hold times and shared scheduling

HoldTimeEstimator accepts the immutable settings snapshot and an explicit zone, and calls the frozen DigestSchedule with settings.digestIntervalMin. Stage6 release scheduling MUST pass the same interval and zone. TauDigest is minutes to the next boundary clamped to [1,intervalMin]. S5-2 release assumptions live only in HoldTimeConfig: break/recovering or laptop idle_open/away ->15min; deep_focus or laptop flowing ->45min; otherwise30min. Break/away takes priority when sources disagree. Release is capped at tauDigest. This is an assumed context table, not a learned break predictor.

holdMinutesFor returns null for ALLOW, tauRelease for DELAY, tauDigest for DIGEST/SILENT. The helper expects the validated HoldTimes output. Invalid digest intervals are programmer errors rejected by DigestSchedule; malformed policy snapshots must fail open in the eventual pipeline. No release jobs, effects, snapshot freshness policy or database writes are implemented in 5A.

S5-1 tie policy and S5-2 release table follow the supplied guide; owner approved proceeding after contract review. Numerical tie behaviour, break precedence, range and overflow rejection are explicit implementation details. Hardware/ColorOS behavior and on-device latency: not verified on target.
