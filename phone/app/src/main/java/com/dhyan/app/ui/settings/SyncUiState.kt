package com.dhyan.app.ui.settings

import com.dhyan.app.sync.pairing.PairingState
import com.dhyan.app.sync.session.SessionState

/**
 * First-class UI status for cross-device pairing and synchronization (Milestone M3).
 * Spec sections: 11.6, 14.3, 25.13.
 */
sealed class SyncUiStatus {
    object NotPaired : SyncUiStatus()
    object Joining : SyncUiStatus()
    object WaitingOffer : SyncUiStatus()
    data class SasVerification(val sasDigits: String) : SyncUiStatus()
    object Paired : SyncUiStatus()
    object Connecting : SyncUiStatus()
    data class Connected(val peerLastSeq: Long) : SyncUiStatus()
    object Unsynced : SyncUiStatus()
    object RelayDown : SyncUiStatus()
    object WrongCode : SyncUiStatus()
    object Expired : SyncUiStatus()
    object Cancelled : SyncUiStatus()
    data class Error(val message: String) : SyncUiStatus()

    fun description(): String = when (this) {
        is NotPaired -> "Not paired with laptop companion"
        is Joining -> "Connecting to pairing rendezvous room..."
        is WaitingOffer -> "Waiting for laptop companion offer..."
        is SasVerification -> "Verify 6-digit confirmation code on laptop"
        is Paired -> "Pairing agreement confirmed. Initializing session..."
        is Connecting -> "Connecting secure session to relay..."
        is Connected -> "Synchronized with laptop companion"
        is Unsynced -> "Unsynced: laptop telemetry is stale (>15s)"
        is RelayDown -> "Relay down: reconnecting with backoff..."
        is WrongCode -> "Pairing failed: incorrect code or role mismatch"
        is Expired -> "Pairing offer expired. Please generate a new code"
        is Cancelled -> "Pairing was cancelled by user"
        is Error -> "Sync error: $message"
    }
}

object SyncUiStateMapper {

    /**
     * Pure function mapping [PairingState], [SessionState], and staleness into [SyncUiStatus].
     */
    fun map(
        pairingState: PairingState,
        sessionState: SessionState,
        isLaptopStale: Boolean,
        hasStoredCredentials: Boolean
    ): SyncUiStatus {
        return when (pairingState) {
            is PairingState.AwaitingUserConfirmation -> SyncUiStatus.SasVerification(pairingState.sasDigits)
            is PairingState.Connecting -> SyncUiStatus.Joining
            is PairingState.WaitingForLaptopOffer -> SyncUiStatus.WaitingOffer
            is PairingState.WaitingForLaptopReveal -> SyncUiStatus.WaitingOffer
            is PairingState.ExchangingConfirmation -> SyncUiStatus.Paired
            is PairingState.Paired -> {
                if (hasStoredCredentials) {
                    when (sessionState) {
                        is SessionState.Connected -> {
                            if (isLaptopStale) SyncUiStatus.Unsynced else SyncUiStatus.Connected(sessionState.peerLastSeqAcked)
                        }
                        is SessionState.Connecting, is SessionState.Handshaking -> SyncUiStatus.Connecting
                        is SessionState.Reconnecting -> SyncUiStatus.RelayDown
                        is SessionState.Failed -> SyncUiStatus.Error(sessionState.reason)
                        is SessionState.Disconnected -> SyncUiStatus.Connecting
                    }
                } else {
                    SyncUiStatus.Paired
                }
            }
            is PairingState.Cancelled -> SyncUiStatus.Cancelled
            is PairingState.Failed -> {
                when {
                    pairingState.error.contains("wrong_code", ignoreCase = true) -> SyncUiStatus.WrongCode
                    pairingState.error.contains("expired", ignoreCase = true) -> SyncUiStatus.Expired
                    pairingState.error.contains("cancel", ignoreCase = true) -> SyncUiStatus.Cancelled
                    else -> SyncUiStatus.Error(pairingState.error)
                }
            }
            is PairingState.Idle -> {
                if (!hasStoredCredentials) {
                    SyncUiStatus.NotPaired
                } else {
                    when (sessionState) {
                        is SessionState.Connected -> {
                            if (isLaptopStale) SyncUiStatus.Unsynced else SyncUiStatus.Connected(sessionState.peerLastSeqAcked)
                        }
                        is SessionState.Connecting, is SessionState.Handshaking -> SyncUiStatus.Connecting
                        is SessionState.Reconnecting -> SyncUiStatus.RelayDown
                        is SessionState.Failed -> SyncUiStatus.Error(sessionState.reason)
                        is SessionState.Disconnected -> SyncUiStatus.RelayDown
                    }
                }
            }
        }
    }
}
