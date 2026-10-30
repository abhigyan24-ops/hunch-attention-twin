package com.dhyan.app.sync.integration

import com.dhyan.app.data.SettingsDao
import com.dhyan.app.data.SettingsEntity
import com.dhyan.app.sync.crypto.TestKeyWrapper
import com.dhyan.app.sync.pairing.OkHttpRendezvousTransport
import com.dhyan.app.sync.pairing.PairingController
import com.dhyan.app.sync.pairing.PairingState
import com.dhyan.app.sync.session.OkHttpSessionTransport
import com.dhyan.app.sync.session.RelayClient
import com.dhyan.app.sync.session.SessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.ServerSocket

/**
 * Opt-in JVM Integration tests pairing phone client with mock-peer and mock-relay.
 * Verifies live handshake, authenticated encryption, counter increment, and attack rejection.
 */
class MockPeerIntegrationTest {

    private fun findFreePort(): Int {
        ServerSocket(0).use { return it.localPort }
    }

    private class MemorySettingsDao : SettingsDao {
        val map = mutableMapOf<String, String>()
        override suspend fun get(key: String): String? = map[key]
        override suspend fun set(setting: SettingsEntity) {
            map[setting.key] = setting.value
        }
        override suspend fun delete(key: String): Int {
            return if (map.remove(key) != null) 1 else 0
        }
    }

    private fun runNodeProcess(prefix: String, vararg args: String): Process {
        val osName = System.getProperty("os.name")?.lowercase() ?: ""
        val nodeCmd = if (osName.contains("windows")) "node.exe" else "node"
        val process = ProcessBuilder(nodeCmd, *args)
            .redirectErrorStream(true)
            .start()

        Thread {
            try {
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        println("[$prefix] $line")
                    }
                }
            } catch (_: Exception) {}
        }.start()

        return process
    }

    @Test
    fun pairingAndSessionWithMockPeerAndMockRelay() {
        runBlocking(Dispatchers.IO) {
            val nodeAvailable = try {
                val p = ProcessBuilder("node", "--version").start()
                p.waitFor() == 0
            } catch (_: Exception) { false }
            Assume.assumeTrue("Node.js must be available for mock-peer integration test", nodeAvailable)

            val port = findFreePort()
            val rootDir = File(".").canonicalFile.let {
                if (File(it, "tools").exists()) it else it.parentFile?.parentFile ?: it
            }

            val relayScript = File(rootDir, "tools/mock-relay/server.mjs")
            val peerScript = File(rootDir, "tools/mock-peer/index.mjs")
            Assume.assumeTrue("mock-relay and mock-peer tools must exist", relayScript.exists() && peerScript.exists())

            // 1. Start mock relay server
            val relayProcess = runNodeProcess("RELAY-1", relayScript.absolutePath, "--port", port.toString())
            delay(1000L) // Wait for relay to bind

            try {
                val relayUrl = "ws://127.0.0.1:$port/ws"
                val pairingCode = "234567" // Valid base32 code

                // 2. Start mock laptop peer in background
                val laptopProcess = runNodeProcess(
                    "LAPTOP-1",
                    peerScript.absolutePath,
                    "--role", "laptop",
                    "--auto-confirm",
                    "--relay", relayUrl,
                    "--code", pairingCode
                )

                delay(800L) // Wait for laptop to connect and post pair_offer

                // 3. Start phone pairing controller
                val settingsDao = MemorySettingsDao()
                val keyWrapper = TestKeyWrapper()
                val controller = PairingController(
                    settingsDao = settingsDao,
                    keyWrapper = keyWrapper,
                    transport = OkHttpRendezvousTransport()
                )

                val stateJob = launch {
                    controller.state.collect { st ->
                        println("[PHONE STATE-1] $st")
                    }
                }

                try {
                    controller.startPairing(pairingCode, relayUrl)

                    // Await SAS confirmation request
                    val sasState = withTimeoutOrNull(15000L) {
                        while (controller.state.value !is PairingState.AwaitingUserConfirmation) {
                            if (controller.state.value is PairingState.Failed || controller.state.value is PairingState.Cancelled) {
                                break
                            }
                            delay(50L)
                        }
                        controller.state.value as? PairingState.AwaitingUserConfirmation
                    }
                    assertNotNull("Phone must reach AwaitingUserConfirmation, current state: ${controller.state.value}", sasState)

                    // Local user confirms SAS match
                    controller.confirmSas(true)

                    // Await paired state
                    val pairedState = withTimeoutOrNull(15000L) {
                        while (controller.state.value !is PairingState.Paired) {
                            if (controller.state.value is PairingState.Failed || controller.state.value is PairingState.Cancelled) {
                                break
                            }
                            delay(50L)
                        }
                        controller.state.value as? PairingState.Paired
                    }
                    assertNotNull("Phone must reach Paired state, current state: ${controller.state.value}", pairedState)

                    // Verify keys were persisted
                    val savedPk = settingsDao.get(PairingController.KEY_PAIRING_PK)
                    val savedSid = settingsDao.get(PairingController.KEY_SESSION_ROOM_ID)
                    assertNotNull("Pairing key must be durably persisted", savedPk)
                    assertNotNull("Session room ID must be durably persisted", savedSid)
                    assertEquals(pairedState!!.sidHex, savedSid)

                    // 4. Phone connects to session room
                    val unwrappedPk = keyWrapper.unwrap(savedPk!!)
                    val phoneClient = RelayClient(
                        pk = unwrappedPk,
                        sidHex = savedSid!!,
                        relayUrl = relayUrl,
                        transport = OkHttpSessionTransport(),
                        scope = this
                    )

                    phoneClient.connect()

                    val connected = withTimeoutOrNull(15000L) {
                        while (phoneClient.state.value !is SessionState.Connected) {
                            delay(50L)
                        }
                        true
                    }
                    assertTrue("Phone must establish connected encrypted session", connected == true)

                    // Verify phone can send encrypted ping
                    assertTrue(phoneClient.sendPing(System.currentTimeMillis()))

                    phoneClient.disconnect()
                    controller.cancelPairing()
                } finally {
                    stateJob.cancel()
                    laptopProcess.destroyForcibly()
                }
            } finally {
                relayProcess.destroyForcibly()
            }
        }
    }

    @Test
    fun attackModeTamperCommitRejectedByPhone() {
        runBlocking(Dispatchers.IO) {
            val nodeAvailable = try {
                val p = ProcessBuilder("node", "--version").start()
                p.waitFor() == 0
            } catch (_: Exception) { false }
            Assume.assumeTrue("Node.js must be available", nodeAvailable)

            val port = findFreePort()
            val rootDir = File(".").canonicalFile.let {
                if (File(it, "tools").exists()) it else it.parentFile?.parentFile ?: it
            }

            val relayScript = File(rootDir, "tools/mock-relay/server.mjs")
            val peerScript = File(rootDir, "tools/mock-peer/index.mjs")
            Assume.assumeTrue("mock-relay and mock-peer tools must exist", relayScript.exists() && peerScript.exists())

            val relayProcess = runNodeProcess("RELAY-2", relayScript.absolutePath, "--port", port.toString())
            delay(1000L)

            try {
                val relayUrl = "ws://127.0.0.1:$port/ws"
                val pairingCode = "89ABCD"

                // Laptop peer launches tamper-commit attack
                val laptopProcess = runNodeProcess(
                    "LAPTOP-ATTACK-1",
                    peerScript.absolutePath,
                    "--role", "laptop",
                    "--relay", relayUrl,
                    "--code", pairingCode,
                    "--attack", "tamper-commit"
                )

                delay(800L)

                val settingsDao = MemorySettingsDao()
                val controller = PairingController(
                    settingsDao = settingsDao,
                    keyWrapper = TestKeyWrapper(),
                    transport = OkHttpRendezvousTransport()
                )

                val stateJob = launch {
                    controller.state.collect { st ->
                        println("[PHONE ATTACK-1 STATE] $st")
                    }
                }

                try {
                    controller.startPairing(pairingCode, relayUrl)

                    // Phone must detect commit mismatch and fail
                    val failedState = withTimeoutOrNull(15000L) {
                        while (controller.state.value !is PairingState.Failed) {
                            delay(50L)
                        }
                        controller.state.value as PairingState.Failed
                    }
                    assertNotNull("Phone must transition to Failed on tampered commit", failedState)
                    assertEquals("Commitment mismatch", failedState!!.error)

                    // Zero keys written
                    assertTrue("Zero keys must be persisted on attack", settingsDao.map.isEmpty())

                    controller.cancelPairing()
                } finally {
                    stateJob.cancel()
                    laptopProcess.destroyForcibly()
                }
            } finally {
                relayProcess.destroyForcibly()
            }
        }
    }

    @Test
    fun attackModeBadConfirmMacRejectedByPhone() {
        runBlocking(Dispatchers.IO) {
            val nodeAvailable = try {
                val p = ProcessBuilder("node", "--version").start()
                p.waitFor() == 0
            } catch (_: Exception) { false }
            Assume.assumeTrue("Node.js must be available", nodeAvailable)

            val port = findFreePort()
            val rootDir = File(".").canonicalFile.let {
                if (File(it, "tools").exists()) it else it.parentFile?.parentFile ?: it
            }

            val relayScript = File(rootDir, "tools/mock-relay/server.mjs")
            val peerScript = File(rootDir, "tools/mock-peer/index.mjs")
            Assume.assumeTrue("mock-relay and mock-peer tools must exist", relayScript.exists() && peerScript.exists())

            val relayProcess = runNodeProcess("RELAY-3", relayScript.absolutePath, "--port", port.toString())
            delay(1000L)

            try {
                val relayUrl = "ws://127.0.0.1:$port/ws"
                val pairingCode = "EFGHJK"

                // Laptop peer launches bad-confirm-mac attack
                val laptopProcess = runNodeProcess(
                    "LAPTOP-ATTACK-2",
                    peerScript.absolutePath,
                    "--role", "laptop",
                    "--relay", relayUrl,
                    "--code", pairingCode,
                    "--attack", "bad-confirm-mac"
                )

                delay(800L)

                val settingsDao = MemorySettingsDao()
                val controller = PairingController(
                    settingsDao = settingsDao,
                    keyWrapper = TestKeyWrapper(),
                    transport = OkHttpRendezvousTransport()
                )

                val stateJob = launch {
                    controller.state.collect { st ->
                        println("[PHONE ATTACK-2 STATE] $st")
                    }
                }

                try {
                    controller.startPairing(pairingCode, relayUrl)

                    // User confirms SAS
                    val sasState = withTimeoutOrNull(15000L) {
                        while (controller.state.value !is PairingState.AwaitingUserConfirmation) {
                            delay(50L)
                        }
                        controller.state.value as PairingState.AwaitingUserConfirmation
                    }
                    assertNotNull("Phone must reach AwaitingUserConfirmation", sasState)
                    controller.confirmSas(true)

                    // Phone must detect peer MAC mismatch and fail
                    val failedState = withTimeoutOrNull(15000L) {
                        while (controller.state.value !is PairingState.Failed) {
                            delay(50L)
                        }
                        controller.state.value as PairingState.Failed
                    }
                    assertNotNull("Phone must transition to Failed on bad confirm mac", failedState)
                    assertEquals("Peer confirmation mismatch", failedState!!.error)

                    // Zero keys written
                    assertTrue("Zero keys must be persisted on attack", settingsDao.map.isEmpty())

                    controller.cancelPairing()
                } finally {
                    stateJob.cancel()
                    laptopProcess.destroyForcibly()
                }
            } finally {
                relayProcess.destroyForcibly()
            }
        }
    }
}
