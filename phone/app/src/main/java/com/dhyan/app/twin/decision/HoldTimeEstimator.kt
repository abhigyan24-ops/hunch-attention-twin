package com.dhyan.app.twin.decision

import com.dhyan.app.twin.model.ContextSnapshot
import com.dhyan.app.twin.model.FocusState
import com.dhyan.app.twin.model.LaptopState
import java.time.ZoneId

/** Assumed release-time table S5-2, not a learned break predictor. */
data class HoldTimeConfig(val nearReleaseMin: Double = 15.0, val ordinaryReleaseMin: Double = 30.0, val deepReleaseMin: Double = 45.0) {
    init { require(listOf(nearReleaseMin,ordinaryReleaseMin,deepReleaseMin).all { it.isFinite() && it >= 0 }) { "invalid_hold_config" } }
}
data class HoldTimes(val tauReleaseMin: Double, val tauDigestMin: Double)
class HoldTimeEstimator(private val config: HoldTimeConfig = HoldTimeConfig()) {
    fun estimate(snapshot: ContextSnapshot, settings: PolicySettings, nowMs: Long, zone: ZoneId): HoldTimes {
        val next=DigestSchedule.nextDigestTsMs(nowMs,settings.digestIntervalMin,zone)
        val digestMin=(Math.subtractExact(next,nowMs)/60000.0).coerceIn(1.0,settings.digestIntervalMin.toDouble())
        // Break/away has precedence over deep focus if the two sources disagree.
        val releaseMin=when {
            snapshot.focusState in setOf(FocusState.BREAK,FocusState.RECOVERING) || snapshot.laptopState in setOf(LaptopState.IDLE_OPEN,LaptopState.AWAY) -> config.nearReleaseMin
            snapshot.focusState==FocusState.DEEP_FOCUS || snapshot.laptopState==LaptopState.FLOWING -> config.deepReleaseMin
            else -> config.ordinaryReleaseMin
        }
        return HoldTimes(releaseMin.coerceAtMost(digestMin),digestMin)
    }
}
fun holdMinutesFor(action: Action, tauReleaseMin: Double, tauDigestMin: Double): Double? = when(action) {
    Action.ALLOW -> null
    Action.DELAY -> tauReleaseMin
    Action.DIGEST,Action.SILENT -> tauDigestMin
}
