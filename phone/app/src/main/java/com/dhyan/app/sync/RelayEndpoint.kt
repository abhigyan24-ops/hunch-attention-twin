package com.dhyan.app.sync

import java.net.URI

object RelayEndpoint {
    fun isAllowed(value: String, debug: Boolean): Boolean = runCatching {
        val uri = URI(value)
        uri.userInfo == null && uri.query == null && uri.fragment == null && uri.path == "/ws" &&
            !uri.host.isNullOrBlank() && (uri.scheme == "wss" || debug && uri.scheme == "ws" &&
            uri.host in setOf("localhost", "127.0.0.1", "10.0.2.2"))
    }.getOrDefault(false)
}
