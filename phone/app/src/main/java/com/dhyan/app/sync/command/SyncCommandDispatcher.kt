package com.dhyan.app.sync.command

import com.dhyan.app.actuator.ControlResult
import com.dhyan.app.actuator.HeldItemsView
import com.dhyan.app.actuator.PhoneControl
import com.dhyan.app.data.CmdLogDao
import com.dhyan.app.data.CmdLogEntity
import com.dhyan.app.personas.json.JsonReader
import com.dhyan.app.sync.session.RelayClient

/**
 * Dispatches and authorizes remote commands from laptop peer via relay,
 * enforces expiry and 24h idempotency via [CmdLogDao], and mirrors held
 * notifications and pet events (Milestone M6).
 * References: Contract section 8.
 */
class SyncCommandDispatcher(
    private val phoneControl: PhoneControl,
    private val cmdLogDao: CmdLogDao,
    private val relayClient: RelayClient,
    private val heldItemsView: HeldItemsView? = null,
    private val timeProvider: () -> Long = System::currentTimeMillis
) {

    /**
     * Handles an incoming command frame body.
     */
    suspend fun handleInboundCommand(bodyJson: String): Boolean {
        val parsed = try {
            JsonReader.parse(bodyJson) as? Map<*, *>
        } catch (_: Exception) {
            return false
        } ?: return false

        val cmdId = parsed["cmdId"] as? String ?: return false
        val cmd = parsed["cmd"] as? String ?: return false
        val args = parsed["args"] as? Map<*, *> ?: emptyMap<String, Any?>()
        val issuedTs = (parsed["issuedTs"] as? Number)?.toLong() ?: 0L
        val exp = (parsed["exp"] as? Number)?.toLong() ?: 0L

        val now = timeProvider()

        // 1. Expiry check: exp - issuedTs <= 60s, local clock check
        if (now > exp || exp - issuedTs > 60_000L || now < issuedTs - 30_000L) {
            recordLog(cmdId, "laptop", cmd, "expired", "command_expired", issuedTs, now)
            sendCmdAck(cmdId, "expired", "command_expired")
            return true
        }

        // 2. Idempotency store check (24h)
        val existing = cmdLogDao.getById(cmdId)
        if (existing != null) {
            sendCmdAck(cmdId, "duplicate", existing.reason)
            return true
        }

        // 3. Authorization and execution via PhoneControl facade
        when (cmd) {
            "kill_switch" -> {
                val on = args["on"] as? Boolean ?: true
                if (!on) {
                    // Contract section 8: kill_switch with on:false is accepted ONLY from the phone
                    recordLog(cmdId, "laptop", cmd, "rejected", "unauthorized", issuedTs, now)
                    sendCmdAck(cmdId, "rejected", "unauthorized")
                    return true
                }

                // on:true is fail-open and always allowed from peer
                val result = phoneControl.setKillSwitch(true)
                handleResult(cmdId, "laptop", cmd, result, issuedTs, now)
            }

            "show_now" -> {
                val all = args["all"] as? Boolean ?: false
                val heldId = args["heldId"] as? String
                val result = phoneControl.showNow(heldId = heldId, all = all)
                handleResult(cmdId, "laptop", cmd, result, issuedTs, now)
            }

            "declare_session" -> {
                val task = args["task"] as? String ?: "coding"
                val plannedMin = (args["plannedMin"] as? Number)?.toDouble() ?: 25.0
                val strict = args["strict"] as? Boolean ?: false
                val result = phoneControl.declareSession(task, plannedMin, strict)
                handleResult(cmdId, "laptop", cmd, result, issuedTs, now)
            }

            "end_session" -> {
                val result = phoneControl.endSession()
                handleResult(cmdId, "laptop", cmd, result, issuedTs, now)
            }

            else -> {
                recordLog(cmdId, "laptop", cmd, "rejected", "unsupported_command", issuedTs, now)
                sendCmdAck(cmdId, "rejected", "unsupported_command")
            }
        }
        return true
    }

    /**
     * Mirrors active held notifications to laptop companion.
     * ZERO notification text, titles, URLs or sender names are included.
     */
    suspend fun sendHeldList(): Boolean {
        val view = heldItemsView ?: return false
        val items = view.currentHeldItems().take(50)
        val itemsList = items.map { item ->
            mapOf(
                "heldId" to item.heldId,
                "appCat" to item.appCat,
                "heldAt" to item.heldAt,
                "expiresAt" to item.expiresAt,
                "status" to item.status.name.lowercase()
            )
        }
        val heldListMap = mapOf(
            "total" to items.size,
            "items" to itemsList
        )
        return relayClient.sendEncrypted("held_list", RelayClient.serializeJson(heldListMap))
    }

    /**
     * Mirrors a pet event (nudge, hold, release, celebrate) to the laptop.
     */
    fun sendPetEvent(kind: String, count: Int = 1): Boolean {
        val bodyJson = """{"kind":"$kind","count":$count}"""
        return relayClient.sendEncrypted("pet_event", bodyJson)
    }

    private suspend fun handleResult(
        cmdId: String,
        source: String,
        action: String,
        result: ControlResult,
        issuedTs: Long,
        nowMs: Long
    ) {
        val status = if (result.applied) "applied" else "rejected"
        recordLog(cmdId, source, action, status, result.reason, issuedTs, nowMs)
        sendCmdAck(cmdId, status, result.reason)
    }

    private suspend fun recordLog(
        cmdId: String,
        source: String,
        action: String,
        status: String,
        reason: String?,
        issuedTs: Long,
        nowMs: Long
    ) {
        cmdLogDao.insert(
            CmdLogEntity(
                cmdId = cmdId,
                source = source,
                action = action,
                status = status,
                reason = reason,
                tsMs = issuedTs,
                executedAtMs = nowMs
            )
        )
    }

    private fun sendCmdAck(cmdId: String, status: String, reason: String?) {
        val reasonPart = if (reason != null) ""","reason":"$reason"""" else ""
        val ackJson = """{"cmdId":"$cmdId","status":"$status"$reasonPart}"""
        relayClient.sendEncrypted("cmd_ack", ackJson)
    }
}
