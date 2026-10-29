package com.dhyan.app.sync.session

/**
 * Connection states for the Dhyan Sync session client.
 * Exposed as StateFlow to UI and ViewModel.
 */
sealed class SessionState {
    /** Disconnected from relay session room. */
    object Disconnected : SessionState()

    /** Opening WebSocket connection to relay room. */
    object Connecting : SessionState()

    /** WebSocket open, waiting to complete hello handshake with peer. */
    object Handshaking : SessionState()

    /** Session active, symmetric encryption keys active, counters established. */
    data class Connected(val peerLastSeqAcked: Long) : SessionState()

    /** Waiting to reconnect after error or abnormal closure with backoff delay. */
    data class Reconnecting(val attempt: Int, val delayMs: Long) : SessionState()

    /** Closed or terminal error. */
    data class Failed(val reason: String) : SessionState()
}
