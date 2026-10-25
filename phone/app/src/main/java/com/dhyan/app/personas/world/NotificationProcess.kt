package com.dhyan.app.personas.world

import com.dhyan.app.personas.*
import com.dhyan.app.personas.rng.GenRng
import java.security.MessageDigest

data class NotificationArrival(val index: Int, val timeMin: Double, val mix: NotificationClass,
    val senderHash: String, val important: Boolean, val urgencyScore: Double, val hasReplyAction: Boolean,
    val isMessagingStyle: Boolean, val repeatCount5m: Int) {
    val key get() = "synthetic-notif-$index"
    fun payload() = GenPayload.Notification(key, "synthetic.${mix.appCat}", mix.appCat, mix.name,
        senderHash, important, urgencyScore, hasReplyAction, repeatCount5m, isMessagingStyle)
}

object NotificationProcess {
    fun generate(persona: PersonaDef, config: GeneratorConfig): List<NotificationArrival> {
        data class Pending(val time: Double, val mix: NotificationClass, val classIndex: Int, val arrivalIndex: Int)
        val pending = mutableListOf<Pending>()
        for ((classIndex, mix) in persona.defaults.notificationMix.sortedBy { it.name }.withIndex()) {
            if (mix.ratePerHour == 0.0) continue
            val rng = GenRng(config.seed).fork(100L, classIndex.toLong())
            var time = rng.nextExp(mix.ratePerHour / 60); var index = 0
            while (time < config.days * 1440.0) {
                pending.add(Pending(time, mix, classIndex, index++)); time += rng.nextExp(mix.ratePerHour / 60)
            }
        }
        val earlier = linkedMapOf<String, MutableList<Double>>()
        return pending.sortedWith(compareBy<Pending> { it.time }.thenBy { it.classIndex }.thenBy { it.arrivalIndex }).mapIndexed { index, p ->
            val rng = GenRng(config.seed).fork(101L, index.toLong())
            val aliases = persona.importantSenders + listOf("synthetic-other-a", "synthetic-other-b", "synthetic-other-c")
            val alias = aliases[rng.categorical(List(aliases.size) { 1.0 })]
            val hash = MessageDigest.getInstance("SHA-256").digest("${config.seed}:${persona.name}:$alias".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
            val times = earlier.getOrPut("${p.mix.name}:$hash") { mutableListOf() }
            times.removeAll { it < p.time - 5.0 }; val repeats = times.size; times.add(p.time)
            val messaging = p.mix.name == "msg_known"
            NotificationArrival(index, p.time, p.mix, hash, alias in persona.importantSenders,
                if (messaging) 0.6 else if (p.mix.name == "promo") 0.1 else 0.3, messaging, messaging, repeats)
        }
    }
}
