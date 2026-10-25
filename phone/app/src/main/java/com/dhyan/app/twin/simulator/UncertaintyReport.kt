package com.dhyan.app.twin.simulator

/**
 * Diagnostic breakdown of uncertainty sources in the twin model (spec 25.2).
 *
 * Uncertainty is NEVER collapsed into a single number. Sources are reported separately:
 * 1. [epistemicVariance]: Uncertainty about the true model parameters (reducible with more data).
 * 2. [processVariance]: Inherent randomness in human behavior (irreducible aleatory variability).
 * 3. [monteCarloStdError]: Numerical error due to finite simulation samples N.
 * 4. [modelUncertaintyFlags]: Qualitative flags noting structural assumptions, priors in use,
 *    or hierarchy fallback levels.
 */
data class UncertaintyReport(
    val epistemicVariance: Double,
    val processVariance: Double,
    val monteCarloStdError: Double,
    val modelUncertaintyFlags: List<String>
)
