package com.dhyan.app.twin.decision

/** Appendix A defaults; recheck age is explicit policy assumption S5-7. */
object PolicyDefaults {
    const val KAPPA_D_PER_HOUR = 3.0
    const val LAMBDA_MISS_MIN = 5.0
    const val C_DIGEST_MIN = 0.8
    const val C_REL = 0.25
    const val I_MAX = 0.85
    const val I_SILENT = 0.16
    const val EPSILON = 0.05
    const val CONF_MIN = 0.4
    const val C_GLANCE_MIN = 0.4
    const val WINDOW_MIN = 30.0
    const val DIGEST_INTERVAL_MIN = 120
    const val MAX_DECISION_AGE_MS = 30000L
}
