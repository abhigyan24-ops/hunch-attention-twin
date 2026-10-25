package com.dhyan.app.twin.context

import com.dhyan.app.twin.model.ContextSnapshot
import com.dhyan.app.twin.model.NotificationInput
import com.dhyan.app.twin.params.ParamKeys

/**
 * A fully-resolved context bucket key (spec 8.3): the full bucket and its hierarchy chain.
 *
 * @param taskType     Wire string of the task type (e.g. "coding").
 * @param hourBand     Wire string of the hour band (e.g. "evening").
 * @param fatigueBand  Wire string of the fatigue band (e.g. "med").
 * @param notifClass   Encoded notification class string.
 */
data class ContextBucket(
    val taskType: String,
    val hourBand: String,
    val fatigueBand: String,
    val notifClass: String
) {
    fun fullKey(): String = ParamKeys.betaPickupFull(taskType, hourBand, fatigueBand, notifClass)
    fun parentKey(): String = ParamKeys.betaPickupParent(notifClass)
    fun grandparentKey(): String = ParamKeys.betaPickupGrandparent()
    fun pickupChain(): List<String> = ParamKeys.betaPickupChain(taskType, hourBand, fatigueBand, notifClass)
    fun gammaHazardChain(): List<String> = ParamKeys.gammaHazardChain(taskType, hourBand, fatigueBand)
}

/**
 * A baseline bucket key (no notification class): used for self-initiated hazard estimation.
 *
 * @param taskType     Wire string of the task type.
 * @param hourBand     Wire string of the hour band.
 * @param fatigueBand  Wire string of the fatigue band.
 */
data class BaselineBucket(
    val taskType: String,
    val hourBand: String,
    val fatigueBand: String
) {
    fun fullKey(): String = ParamKeys.gammaHazardFull(taskType, hourBand, fatigueBand)
    fun parentKey(): String = ParamKeys.gammaHazardParent(taskType)
    fun grandparentKey(): String = ParamKeys.gammaHazardGrandparent()
    fun chain(): List<String> = ParamKeys.gammaHazardChain(taskType, hourBand, fatigueBand)
}

/**
 * Derives [ContextBucket] and [BaselineBucket] from a [ContextSnapshot] and a [NotificationInput].
 */
object ContextBucketer {

    fun bucket(snapshot: ContextSnapshot, notif: NotificationInput): ContextBucket {
        val notifClassStr = ParamKeys.notifClass(
            notif.appCat,
            NotifClass.urgencyBand(notif.urgencyScore),
            NotifClass.repeatBand(notif.repeatCount5m),
            notif.isVip
        )
        return ContextBucket(
            taskType    = snapshot.taskType.wire,
            hourBand    = snapshot.hourBand,
            fatigueBand = snapshot.fatigueBand,
            notifClass  = notifClassStr
        )
    }

    fun baselineBucket(snapshot: ContextSnapshot): BaselineBucket = BaselineBucket(
        taskType    = snapshot.taskType.wire,
        hourBand    = snapshot.hourBand,
        fatigueBand = snapshot.fatigueBand
    )
}
