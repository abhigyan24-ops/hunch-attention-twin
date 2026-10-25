package com.dhyan.app.personas.world

import com.dhyan.app.personas.*
import com.dhyan.app.personas.rng.GenRng

/**
 * 4A-3: Logging policies.
 *
 * ShowAll  – always show; pi = 1.0; arm = SHOW.
 * RandomizedHold – hold (suppress) with probability eps if the notification is eligible:
 *   - not inSession
 *   - sender not in importantSenders
 *   - importance proxy (urgencyScore) < maxImportance
 * Policy draws come from their own fork (tag=500, index=notifIndex) so toggling
 * the policy does not change the world draws.
 */
object LoggingPolicyEngine {

    fun decide(
        policy: LoggingPolicy,
        notification: NotificationArrival,
        persona: PersonaDef,
        config: GeneratorConfig,
        inSession: Boolean,
        notifIndex: Int
    ): PolicyDecision {
        if (policy == LoggingPolicy.ShowAll || inSession) {
            return PolicyDecision(RealizedArm.SHOW, 1.0, eligibleForExploration = false)
        }
        val hold = policy as? LoggingPolicy.RandomizedHold ?: return PolicyDecision(RealizedArm.SHOW, 1.0, false)

        val important = notification.important
        val importanceProxy = notification.urgencyScore
        val eligible = !important && importanceProxy < hold.maxImportance

        if (!eligible) {
            return PolicyDecision(RealizedArm.SHOW, 1.0, eligibleForExploration = false)
        }

        // Draw from a separate fork: tag=500, index=notifIndex
        val rng = GenRng(config.seed).fork(500L, notifIndex.toLong())
        val u = rng.nextDouble()
        return if (u < hold.eps) {
            PolicyDecision(RealizedArm.SUPPRESS, hold.eps, eligibleForExploration = true)
        } else {
            PolicyDecision(RealizedArm.SHOW, 1.0 - hold.eps, eligibleForExploration = true)
        }
    }
}

data class PolicyDecision(
    val arm: RealizedArm,
    val propensity: Double,
    val eligibleForExploration: Boolean
)
