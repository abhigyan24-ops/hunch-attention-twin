package com.dhyan.app.personas.world

import com.dhyan.app.personas.*
import com.dhyan.app.personas.rng.GenRng
import kotlin.math.*

/**
 * 4A-3: Complete TruthRecord with cost fields and realized arm.
 * All draws are performed unconditionally (same order regardless of branch),
 * so costs are always computable even for inSession records.
 */
enum class Owner { NOTIF, SELF, NONE }
enum class RealizedArm { SHOW, SUPPRESS }

data class TrialDraws(
    // Fixed draw order: uSelf, destSelf, zDself, zRself, uPick, uLatency, destNotif, zDnotif, zRnotif
    val uSelf: Double, val destSelf: Double, val zDself: Double, val zRself: Double,
    val uPick: Double, val uLatency: Double, val destNotif: Double, val zDnotif: Double, val zRnotif: Double,
    val lambdaPerHour: Double, val pPickup: Double, val selfDelayMin: Double, val notifDelayMin: Double,
    val baselineExists: Boolean, val notifEventExists: Boolean, val owner: Owner,
    // 4A-3 cost fields: ALWAYS computed from the same draws, truncation honored by config
    val lShowMin: Double,
    val lSuppressMin: Double
) {
    val deltaLMin: Double get() = lShowMin - lSuppressMin
}

data class TruthRecord(
    val persona: String, val dayIndex: Int, val notificationIndex: Int, val ts: Long,
    val context: GenContext, val inSession: Boolean, val trialEligible: Boolean,
    val exclusionReason: String?, val trial: TrialDraws?,
    // 4A-3 additions
    val realizedArm: RealizedArm?,
    val propensity: Double?,          // P(realizedArm | policy, eligibility); 1.0 when ineligible or important
    val eligibleForExploration: Boolean, // true only when the policy held or could hold this notification
    val world: String                 // "MATCHED" or "MILD_MISSPEC"
)
