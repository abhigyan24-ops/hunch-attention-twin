package com.dhyan.app.safety

import com.dhyan.app.twin.decision.NotificationFacts

/** S5-8. Computes expiry only; never deletes or discards a notification. */
class DefaultExpiryPolicy : ExpiryPolicy {
    override fun expiresAtMs(facts: NotificationFacts, decidedAtMs: Long): Long {
        val durationMs=if(facts.isOtpLike || facts.notifCat=="otp") OTP_EXPIRY_MS else ORDINARY_EXPIRY_MS
        val start=decidedAtMs.coerceAtLeast(0L)
        return if(start > Long.MAX_VALUE-durationMs)Long.MAX_VALUE else start+durationMs
    }
    companion object {
        const val OTP_EXPIRY_MS=10L*60*1000
        const val ORDINARY_EXPIRY_MS=24L*60*60*1000
    }
}
