package com.dhyan.app.sync.session

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * Transport abstraction for the session room WebSocket connection.
 * Facilitates unit testing with loopback mock transport.
 */
interface SessionTransport {
    fun connect(
        url: String,
        roomId: String,
        device: String,
        listener: SessionTransportListener
    )
    fun send(text: String): Boolean
    fun close(code: Int = 1000, reason: String = "Normal closure")
}

interface SessionTransportListener {
    fun onOpen()
    fun onMessage(text: String)
    fun onFailure(t: Throwable)
    fun onClosed(code: Int, reason: String)
}

/**
 * Production OkHttp WebSocket implementation of [SessionTransport].
 */
class OkHttpSessionTransport(
    private val client: OkHttpClient = OkHttpClient()
) : SessionTransport {

    private var webSocket: WebSocket? = null

    override fun connect(
        url: String,
        roomId: String,
        device: String,
        listener: SessionTransportListener
    ) {
        close()
        val wsUrl = if (url.contains("?")) {
            "$url&room=$roomId&device=$device"
        } else {
            "$url?room=$roomId&device=$device"
        }
        val request = Request.Builder().url(wsUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                listener.onOpen()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                listener.onMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                listener.onFailure(t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                listener.onClosed(code, reason)
            }
        })
    }

    override fun send(text: String): Boolean {
        return webSocket?.send(text) ?: false
    }

    override fun close(code: Int, reason: String) {
        try {
            webSocket?.close(code, reason)
        } catch (_: Exception) {
        } finally {
            webSocket = null
        }
    }
}
