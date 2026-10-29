package com.dhyan.app.sync.pairing

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * Transport abstraction for the ephemeral rendezvous room WebSocket.
 * Enables loopback unit testing and OkHttp production implementation.
 */
interface RendezvousTransport {
    fun connect(url: String, roomId: String, onMessage: (String) -> Unit, onError: (Throwable) -> Unit, onClose: () -> Unit)
    fun send(messageJson: String)
    fun close()
}

/**
 * Production OkHttp WebSocket implementation of [RendezvousTransport].
 */
class OkHttpRendezvousTransport(
    private val client: OkHttpClient = OkHttpClient()
) : RendezvousTransport {
    private var webSocket: WebSocket? = null
    private val pendingSends = mutableListOf<String>()
    private var isOpen = false

    override fun connect(
        url: String,
        roomId: String,
        onMessage: (String) -> Unit,
        onError: (Throwable) -> Unit,
        onClose: () -> Unit
    ) {
        close()
        isOpen = false
        pendingSends.clear()
        val wsUrl = if (url.contains("?")) {
            "$url&room=$roomId&device=phone"
        } else {
            "$url?room=$roomId&device=phone"
        }
        val request = Request.Builder().url(wsUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                synchronized(this@OkHttpRendezvousTransport) {
                    isOpen = true
                    pendingSends.forEach { webSocket.send(it) }
                    pendingSends.clear()
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                onMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                synchronized(this@OkHttpRendezvousTransport) {
                    isOpen = false
                }
                onError(t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                synchronized(this@OkHttpRendezvousTransport) {
                    isOpen = false
                }
                onClose()
            }
        })
    }

    override fun send(messageJson: String) {
        synchronized(this) {
            val ws = webSocket
            if (isOpen && ws != null) {
                ws.send(messageJson)
            } else {
                pendingSends.add(messageJson)
            }
        }
    }

    override fun close() {
        synchronized(this) {
            try {
                webSocket?.close(1000, "Normal closure")
            } catch (_: Exception) {}
            webSocket = null
            isOpen = false
            pendingSends.clear()
        }
    }
}
