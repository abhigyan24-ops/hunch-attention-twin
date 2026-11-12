package com.dhyan.app.sync

import org.json.JSONObject

/**
 * mDNS discovery manager for _hunch._tcp service.
 * NsdManager requires Android context so cannot be unit-tested with pure JVM.
 * parsePairingPayload is a pure companion function — fully testable.
 */
class DiscoveryManager {

    data class PairingConfig(
        val host: String,
        val port: Int,
        val token: String,
        val service: String
    )

    companion object {
        /**
         * Pure function: parses a raw JSON pairing payload into PairingConfig.
         * Returns null on any parse error or missing required fields.
         */
        fun parsePairingPayload(raw: String): PairingConfig? {
            val trimmed = raw.trim()
            if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return null
            return try {
                try {
                    val obj = JSONObject(raw)
                    val host = obj.optString("host", "").takeIf { it.isNotBlank() } ?: return null
                    val port = obj.optInt("port", 0).takeIf { it in 1..65535 } ?: return null
                    val token = obj.optString("token", "").takeIf { it.isNotBlank() } ?: return null
                    val service = obj.optString("service", "_hunch._tcp").takeIf { it.isNotBlank() } ?: "_hunch._tcp"
                    PairingConfig(host = host, port = port, token = token, service = service)
                } catch (t: Throwable) {
                    val hostMatch = Regex(""""host"\s*:\s*"([^"]+)"""").find(trimmed)
                    val portMatch = Regex(""""port"\s*:\s*(\d+)""").find(trimmed)
                    val tokenMatch = Regex(""""token"\s*:\s*"([^"]+)"""").find(trimmed)
                    val serviceMatch = Regex(""""service"\s*:\s*"([^"]+)"""").find(trimmed)

                    val host = hostMatch?.groupValues?.get(1)?.takeIf { it.isNotBlank() } ?: return null
                    val port = portMatch?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
                    val token = tokenMatch?.groupValues?.get(1)?.takeIf { it.isNotBlank() } ?: return null
                    val service = serviceMatch?.groupValues?.get(1)?.takeIf { it.isNotBlank() } ?: "_hunch._tcp"

                    PairingConfig(host = host, port = port, token = token, service = service)
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}
