package com.dhyan.app.sync.pairing

/**
 * Transport abstraction for the ephemeral rendezvous room WebSocket.
 * Enables loopback unit testing and OkHttp production implementation.
 */
interface RendezvousTransport {
    fun connect(url: String, roomId: String, onMessage: (String) -> Unit, onError: (Throwable) -> Unit, onClose: () -> Unit)
    fun send(messageJson: String)
    fun close()
}
