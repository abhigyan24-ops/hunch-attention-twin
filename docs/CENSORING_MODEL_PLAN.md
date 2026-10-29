# Censor-aware pickup learning: design request

Status: proposed design only; no new estimator, prior, pseudo-count or frozen model-contract change is implemented.

## Problem and invariant

Section25.4 forbids treating ownership transfer at a newer notification as an ordinary observed ignore. Excluding those unresolved episodes previously yielded a synthetic replay pickup estimate0.7247279352031226 versus0.62 ground truth, error0.10472793520312262 above the existing0.08 limit. Restoring the older ignore update made that test green but violated the overriding measurement rule. Neither result validates a censor-aware estimator.

Keep the original test, seed and tolerance. Report every observed result. Do not tune an observation threshold, prior or seed to recover the example number.

## Required investigation before implementation

1. Audit the estimand: pickup within the configured30minute outcome window versus the2minute self-initiated ownership rule. Track notification-triggered and competing self-initiated pickups separately. Do not equate these probabilities silently.
2. Produce diagnostics from the actual event stream: fully observed ignores, observed pickups, superseded-unresolved episodes, local-gap censoring, observed exposure and context bucket. Do not expose hidden persona outcomes to production learning.
3. Check whether the synthetic generator's notification schedule and pickup-latency distribution match the learner's stated assumptions. An outcome-selected set of completed episodes is not automatically a representative sample of all notifications.
4. Compare proposed likelihoods on independently generated histories, including informative censoring. A survival/mixture likelihood can represent no pickup before censoring without declaring a negative completed outcome; its observation-process assumptions must be explicit. This is a candidate approach, not an approved model.
5. Specify how censored exposure persists across restart, how decay applies, what effective sample size means, and how model version/provenance reach the existing public snapshot. Keep ordinary Bernoulli success/failure counts distinct from unresolved evidence.

## Review and validation scope

The implementation proposal must name exact episode/parameter-store/persistence paths and request any frozen model-contract changes before editing them. No changes to core-spec or root package.json are part of this task.

Validation must retain the existing recovery check and add multiple personas/seeds, zero-pickup, high-baseline-hazard, uncensored and censored matched histories, informative-censoring stress cases, deterministic replay and restart equivalence. Test data must be separate from model-selection data. Report failures and uncertainty; do not select an estimator because it happens to pass one persona seed.

Until that reviewed model implementation passes the unchanged recovery check, model validation and overall readiness remain blocked. Sync/security conformance checks can be verified independently.
