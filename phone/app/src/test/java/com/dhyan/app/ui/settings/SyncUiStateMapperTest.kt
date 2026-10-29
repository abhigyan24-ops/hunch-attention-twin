package com.dhyan.app.ui.settings

import com.dhyan.app.sync.pairing.PairingState
import com.dhyan.app.sync.session.SessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncUiStateMapperTest {

    @Test
    fun mapsPairingStatesCorrectly() {
        val waitingSas = SyncUiStateMapper.map(
            pairingState = PairingState.AwaitingUserConfirmation("790350"),
            sessionState = SessionState.Disconnected,
            isLaptopStale = false,
            hasStoredCredentials = false
        )
        assertTrue(waitingSas is SyncUiStatus.SasVerification)
        assertEquals("790350", (waitingSas as SyncUiStatus.SasVerification).sasDigits)
        assertEquals("Verify 6-digit confirmation code on laptop", waitingSas.description())

        val connecting = SyncUiStateMapper.map(
            pairingState = PairingState.Connecting("room123"),
            sessionState = SessionState.Disconnected,
            isLaptopStale = false,
            hasStoredCredentials = false
        )
        assertEquals(SyncUiStatus.Joining, connecting)

        val waitingOffer = SyncUiStateMapper.map(
            pairingState = PairingState.WaitingForLaptopOffer,
            sessionState = SessionState.Disconnected,
            isLaptopStale = false,
            hasStoredCredentials = false
        )
        assertEquals(SyncUiStatus.WaitingOffer, waitingOffer)

        val waitingReveal = SyncUiStateMapper.map(
            pairingState = PairingState.WaitingForLaptopReveal,
            sessionState = SessionState.Disconnected,
            isLaptopStale = false,
            hasStoredCredentials = false
        )
        assertEquals(SyncUiStatus.WaitingOffer, waitingReveal)

        val exchanging = SyncUiStateMapper.map(
            pairingState = PairingState.ExchangingConfirmation,
            sessionState = SessionState.Disconnected,
            isLaptopStale = false,
            hasStoredCredentials = false
        )
        assertEquals(SyncUiStatus.Paired, exchanging)

        val paired = SyncUiStateMapper.map(
            pairingState = PairingState.Paired("sid123"),
            sessionState = SessionState.Disconnected,
            isLaptopStale = false,
            hasStoredCredentials = false
        )
        assertEquals(SyncUiStatus.Paired, paired)
    }

    @Test
    fun mapsPairingFailuresCorrectly() {
        val wrongCode = SyncUiStateMapper.map(
            pairingState = PairingState.Failed("wrong_code"),
            sessionState = SessionState.Disconnected,
            isLaptopStale = false,
            hasStoredCredentials = false
        )
        assertEquals(SyncUiStatus.WrongCode, wrongCode)

        val expired = SyncUiStateMapper.map(
            pairingState = PairingState.Failed("pairing_offer_expired"),
            sessionState = SessionState.Disconnected,
            isLaptopStale = false,
            hasStoredCredentials = false
        )
        assertEquals(SyncUiStatus.Expired, expired)

        val cancelled = SyncUiStateMapper.map(
            pairingState = PairingState.Cancelled("user_cancelled"),
            sessionState = SessionState.Disconnected,
            isLaptopStale = false,
            hasStoredCredentials = false
        )
        assertEquals(SyncUiStatus.Cancelled, cancelled)

        val genericErr = SyncUiStateMapper.map(
            pairingState = PairingState.Failed("socket_timeout"),
            sessionState = SessionState.Disconnected,
            isLaptopStale = false,
            hasStoredCredentials = false
        )
        assertTrue(genericErr is SyncUiStatus.Error)
        assertEquals("socket_timeout", (genericErr as SyncUiStatus.Error).message)
    }

    @Test
    fun mapsIdleWithoutCredentialsToNotPaired() {
        val status = SyncUiStateMapper.map(
            pairingState = PairingState.Idle,
            sessionState = SessionState.Disconnected,
            isLaptopStale = false,
            hasStoredCredentials = false
        )
        assertEquals(SyncUiStatus.NotPaired, status)
    }

    @Test
    fun mapsSessionStatesWhenPaired() {
        val connecting = SyncUiStateMapper.map(
            pairingState = PairingState.Idle,
            sessionState = SessionState.Connecting,
            isLaptopStale = false,
            hasStoredCredentials = true
        )
        assertEquals(SyncUiStatus.Connecting, connecting)

        val handshaking = SyncUiStateMapper.map(
            pairingState = PairingState.Idle,
            sessionState = SessionState.Handshaking,
            isLaptopStale = false,
            hasStoredCredentials = true
        )
        assertEquals(SyncUiStatus.Connecting, handshaking)

        val connectedFresh = SyncUiStateMapper.map(
            pairingState = PairingState.Idle,
            sessionState = SessionState.Connected(peerLastSeqAcked = 15L),
            isLaptopStale = false,
            hasStoredCredentials = true
        )
        assertTrue(connectedFresh is SyncUiStatus.Connected)
        assertEquals(15L, (connectedFresh as SyncUiStatus.Connected).peerLastSeq)

        val connectedStale = SyncUiStateMapper.map(
            pairingState = PairingState.Idle,
            sessionState = SessionState.Connected(peerLastSeqAcked = 15L),
            isLaptopStale = true,
            hasStoredCredentials = true
        )
        assertEquals(SyncUiStatus.Unsynced, connectedStale)

        val reconnecting = SyncUiStateMapper.map(
            pairingState = PairingState.Idle,
            sessionState = SessionState.Reconnecting(attempt = 2, delayMs = 5000L),
            isLaptopStale = false,
            hasStoredCredentials = true
        )
        assertEquals(SyncUiStatus.RelayDown, reconnecting)

        val disconnected = SyncUiStateMapper.map(
            pairingState = PairingState.Idle,
            sessionState = SessionState.Disconnected,
            isLaptopStale = false,
            hasStoredCredentials = true
        )
        assertEquals(SyncUiStatus.RelayDown, disconnected)

        val failed = SyncUiStateMapper.map(
            pairingState = PairingState.Idle,
            sessionState = SessionState.Failed("bad_tag"),
            isLaptopStale = false,
            hasStoredCredentials = true
        )
        assertTrue(failed is SyncUiStatus.Error)
        assertEquals("bad_tag", (failed as SyncUiStatus.Error).message)
    }
}
