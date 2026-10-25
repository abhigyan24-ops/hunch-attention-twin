package com.dhyan.app.safety

import com.dhyan.app.twin.decision.NotificationFacts

interface ExpiryPolicy {
    /** Epoch milliseconds, null only when explicit policy says no expiry. */
    fun expiresAtMs(facts: NotificationFacts, decidedAtMs: Long): Long?
}
