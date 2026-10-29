package com.dhyan.app.sync.pairing

sealed class PairingState {
    object Idle : PairingState()
    data class Connecting(val roomId: String) : PairingState()
    object WaitingForLaptopOffer : PairingState()
    object WaitingForLaptopReveal : PairingState()
    data class AwaitingUserConfirmation(val sasDigits: String) : PairingState()
    object ExchangingConfirmation : PairingState()
    data class Paired(val sidHex: String) : PairingState()
    data class Cancelled(val reason: String) : PairingState()
    data class Failed(val error: String) : PairingState()
}
