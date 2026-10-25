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

## Safety module — Stage 5B

NeverHoldRules consumes metadata only and runs independently of mode, kill state, cost and exploration. Precedence is call,alarm,navigation,otp,emergency,vip,allowlist. isOtpLike or explicit otp metadata triggers otp; emergency uses hasEmergencyKeyword. Category comparisons are exact, with no keyword inspection or speculative aliases. A caller changing the allow-list/VIP/critical flags after decision time is protected by recheck.

Canonical notifCat is an open nonempty string, not an enum. The canonical valid notification fixture uses message. The actual Stage2 Normalizer.classifyAndroidCategory emits msg,promo,alert,sys,social,other: notably alarm/reminder -> alert, and no explicit call/navigation preservation. 5B recognizes explicit call,alarm,navigation,otp tokens as policy inputs; it does not falsely declare all alert items to be alarms. Recognized non-critical tokens also include message/email/transport/delivery/security and Stage4A class tokens. Unknown category tokens increment only unknown_notif_cat and are not themselves a never-hold reason; another explicit VIP/OTP/emergency/allow-list rule still wins. The original category string is never copied into diagnostics.

**Integration prerequisite:** Stage6 must preserve/pass the critical category metadata to NotificationFacts, or obtain an approved conservative fallback, before enabling live holds. Current normalized events alone cannot recover the lost alarm vs reminder distinction, nor reliably reconstruct calls/navigation. This is a cross-stage request, not fixed here. It does not require broadening core-spec, whose notifCat already permits these tokens. Stage6 must test each actual category path end to end; unit safety facts do not establish platform classification accuracy.

FailOpen.guard catches Exception and produces ALLOW/failedOpen=true with only exception:<SimpleClassName> as reasonCode. No exception message/cause/stack is copied. Dedicated invalidInput and NotReady helpers use invalid_input/not_ready. safetyReason stays null for these general reasons. The guard intentionally catches Exception per the guide, not arbitrary fatal JVM Errors. No I/O, persistence or clock. This is a primitive, not an implemented DecisionPipeline. Future5C owns its order and use.

DecisionDiagnostics uses atomic numeric counters with fixed keys only: exception,invalid_input,not_ready,unknown_notif_cat and recheck_abort_{mode_not_live,kill_switch,allow,failed_open,never_hold,identity_mismatch,decision_expired,clock_backwards,invalid_input,exception}. Snapshot is a read-only map of numeric counts with individually atomic reads, not a transactional multi-counter snapshot. Exception class names are present only in the controlled decision reason, not dynamic counter keys.

RecheckLogic returns Proceed only for LIVE,kill-off,non-failed-open hold decisions after metadata/settings validation, never-hold checks, exact keyHash/postedTsMs identity, valid holdMinutes/propensity/eligibleActions and nonnegative decision age <=30000ms. Fixed abort reasons follow the counter suffixes. Failed-open and ALLOW abort before actuation. Pure SafetyValidation adds shared numeric/range checks for recheck and future5C (finite positive kappaD,epsilon[0,1],digest interval1..1440,nonblank package/hash/category,canonical appCat,valid urgency/repeat count and nonfuture postedTs). Negative/backward/extreme times cannot overflow into Proceed. Exceptions from malformed collection implementations abort and increment fixed diagnostics; no message content escapes.

DefaultExpiryPolicy returns decidedAt+10min for OTP-like/explicit otp, otherwise+24h. Negative start times clamp0; addition saturates at Long.MAX_VALUE instead of wrapping. This is metadata computation only, not a release or delete action. Stage6 must surface/release/log expired items according to the shared contract; expiry never justifies silent discard. These timestamp edge choices are explicit robustness details, not a hardware claim.

SafetyMatrixTest covers the seven pure primitive rows: urgent flags/categories, malformed ambiguous facts, guarded classifier/importance exceptions, real TwinEngine stale-context NotReady without persistence, invalid settings, repost identity,kill activation. It does not claim classifier semantics or complete pipeline integration;5C adds actual pipeline safety/order tests. Device/process/storage/scheduler rows remain Stage6 acceptance work. No actuation and nothing verified on target.

## Counterfactual estimation — Stage 5D

The estimator targets mean SHOW-minus-SUPPRESS focus loss per notification within caller-defined, logged eligible contexts with 0<pShow<1. SHOW=ALLOW; all three hold actions are SUPPRESS for the initial 30-minute opportunity window. Only non-censored observed outcomes with confirmed non-failed actuation and valid probabilities are usable. Never-hold/VIP/high-importance/strict-session contexts are not identified by randomized low-importance exploration. No extrapolation into those populations. User-day totals are not automatically causal saved minutes.

Hajek (primary) normalizes arm-specific inverse-probability weights; HT reports sum(signed weightedY)/N. AIPW/DR is mean(muShow-muSupp + 1SHOW*(Y-muShow)/pShow - 1SUPPRESS*(Y-muSupp)/(1-pShow)). Model predictions must be available at decision time; no future-event features or outcome-trained predictions in the same row. The test's deliberately imperfect persona predictor is fixed at25/18 before outcomes, not the actual counterfactual realization. Required assumptions: no hidden confounding beyond logged context, stationarity within bucket, suppression not changing later behavior, correct logged propensity or correct outcome model for DR.

Filters are exclusive in order censored,actuation failed,not eligible,invalid/nonfinite numeric,positivity (p outside the open interval),nonrepresentable inverse weights. NonFinite counts also invalid negative loss/predictions. Reports retain total/used/filter counts. Below20 usable rows or a missing sampled arm: MODEL_BASED_ONLY with simulated model mean only. No undefined Hajek value is invented. At20+ and both arms observed: ESTIMATED with separate IPW/HT/DR, observed realized mean, overlap and sensitivity.

Bootstrap uses seeded SplitMix64 record resampling, B1000 default, percentile p10/p90 labelled 80% bootstrap interval. Replicates missing an arm are omitted (Hajek undefined); valid count is reported. Diagnostics report per-arm ESS, min/max pShow and top5% observed-weight share. Sensitivity caps none/20/10/5 apply to weights and AIPW residual weights, visibly allowing bias. Numeric overflow in estimator arithmetic is rejected with fixed numerical_overflow rather than emitted as an estimate. This offline estimator is not the no-throw decision interface.

Regret uses J(chosen)-minJ only when costs were actually computed; bypassed safety decisions cannot be assigned fabricated regret. ExplorationCost sums J(ALLOW)-minJ for explored rows. SavedMinutes counts only confirmed HELD/SNOOZED/RELEASED hold decisions, retaining signed model delta. SCHEDULED/UNKNOWN/FAILED_OPEN/EXPIRED-without-confirmation are excluded. delayCost uses actual held hours and the strict-session kappa multiplier; it is a modelled harm estimate, not a directly observed loss. EstimatedSavedMin is a separately supplied DR-supported total for a matching eligible population, never silently netted or inferred for unsupported contexts. Model,delay,DR totals are separate labelled values.

Outcome-window integration caveat: the twin/persona default charges complete dwell+resume for onset in W=30min (untruncated), whereas 'loss within W' can mean time-clipped loss. Production episode-to-ExplorationRecord mapping must choose and document a common Y and matching prediction window; do not mix clipped and untruncated outcomes in a bucket. The synthetic numerical comparison uses the generator's actual complete-cost truth. This does not validate a future Room outcome mapping.

The committed4A TrueEffect helper uses constant baseline hazard while HistoryGenerator integrates hourly modulation. The test prints both the exact finite-fixture mean of actual counterfactual records and TrueEffect's constant-hazard reference for the same observed context mix. They are deliberately not relabelled identical ground truth. A generous-interval coverage assertion uses the actual generated per-notification truth. Reconciling the reference helper is read-only cross-stage work, not persona tuning. Monte Carlo IPW/DR estimates need not exactly equal either reference in a finite sample; wide intervals and MODEL_BASED_ONLY are honest outcomes.
