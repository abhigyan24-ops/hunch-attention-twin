package com.dhyan.app.sync.crypto

import com.dhyan.app.personas.json.JsonReader
import com.dhyan.app.sync.SyncTestVectorsLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.util.Base64

@Suppress("UNCHECKED_CAST")
class SyncCryptoVectorsTest {

    private val vectors: Map<String, Any?> by lazy {
        JsonReader.parse(SyncTestVectorsLoader.readText()) as Map<String, Any?>
    }

    private fun obj(key: String, map: Map<String, Any?> = vectors): Map<String, Any?> =
        map[key] as Map<String, Any?>

    private fun str(key: String, map: Map<String, Any?>): String =
        map[key] as String

    private fun num(key: String, map: Map<String, Any?>): Long =
        (map[key] as Number).toLong()

    private fun String.fromHex(): ByteArray {
        val len = length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(this[i], 16) shl 4) + Character.digit(this[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    private fun ByteArray.toHex(): String {
        val sb = StringBuilder(size * 2)
        for (b in this) {
            sb.append(String.format("%02x", b.toInt() and 0xFF))
        }
        return sb.toString()
    }

    @Test
    fun step1_constantsAndRendezvousRoomId() {
        val constants = obj("constants")
        val code = str("pairingCode", constants)
        val expectedRoomId = str("rendezvousRoomId", constants)

        val derivedRoomId = SyncCrypto.getRendezvousRoomId(code)
        assertEquals(expectedRoomId, derivedRoomId)
    }

    @Test
    fun step2_keyGenerationAndSEC1UncompressedEncoding() {
        val laptop = obj("laptopKeys")
        val scalarLaptop = str("privateScalarHex", laptop).fromHex()
        val privL = SyncCrypto.privateKeyFromScalar(scalarLaptop)
        assertNotNull(privL)

        val pkLHex = str("publicKeyHex", laptop)
        val pkLBytes = pkLHex.fromHex()
        val pubL = SyncCrypto.decodePublicKeyUncompressed(pkLBytes)
        val encodedPubL = SyncCrypto.encodePublicKeyUncompressed(pubL)
        assertEquals(pkLHex.lowercase(), encodedPubL.toHex().lowercase())
        assertEquals(str("publicKeyBase64", laptop), Base64.getEncoder().encodeToString(encodedPubL))

        val phone = obj("phoneKeys")
        val scalarPhone = str("privateScalarHex", phone).fromHex()
        val privP = SyncCrypto.privateKeyFromScalar(scalarPhone)
        assertNotNull(privP)

        val pkPHex = str("publicKeyHex", phone)
        val pkPBytes = pkPHex.fromHex()
        val pubP = SyncCrypto.decodePublicKeyUncompressed(pkPBytes)
        val encodedPubP = SyncCrypto.encodePublicKeyUncompressed(pubP)
        assertEquals(pkPHex.lowercase(), encodedPubP.toHex().lowercase())
        assertEquals(str("publicKeyBase64", phone), Base64.getEncoder().encodeToString(encodedPubP))
    }

    @Test
    fun step3_commitComputation() {
        val laptop = obj("laptopKeys")
        val pkL = str("publicKeyHex", laptop).fromHex()
        val nL = str("nonceHex", laptop).fromHex()
        val expectedCommitHex = str("commitHex", laptop)
        val expectedCommitBase64 = str("commitBase64", laptop)

        val commit = SyncCrypto.computePairCommit(pkL, nL)
        assertEquals(expectedCommitHex.lowercase(), commit.toHex().lowercase())
        assertEquals(expectedCommitBase64, Base64.getEncoder().encodeToString(commit))
    }

    @Test
    fun step4_ecdhAndTranscriptHashAndSas() {
        val laptop = obj("laptopKeys")
        val phone = obj("phoneKeys")
        val pairing = obj("pairingAgreement")

        val privP = SyncCrypto.privateKeyFromScalar(str("privateScalarHex", phone).fromHex())
        val pubL = SyncCrypto.decodePublicKeyUncompressed(str("publicKeyHex", laptop).fromHex())

        // ECDH
        val sharedSecret = SyncCrypto.computeECDH(privP, pubL)
        val expectedSecret = str("sharedSecretHex", pairing)
        assertEquals(expectedSecret.lowercase(), sharedSecret.toHex().lowercase())

        // Transcript Hash
        val pkL = str("publicKeyHex", laptop).fromHex()
        val pkP = str("publicKeyHex", phone).fromHex()
        val nL = str("nonceHex", laptop).fromHex()
        val nP = str("nonceHex", phone).fromHex()

        val t = SyncCrypto.computeTranscriptHash(pkL, pkP, nL, nP)
        val expectedTranscript = str("transcriptHashHex", pairing)
        assertEquals(expectedTranscript.lowercase(), t.toHex().lowercase())

        // SAS Digits
        val sas = SyncCrypto.computeSas(t)
        val expectedSas = str("sasDigits", pairing)
        assertEquals(expectedSas, sas)
    }

    @Test
    fun step5_confirmMacsAndPermanentPairingKeyAndRoom() {
        val pairing = obj("pairingAgreement")
        val secret = str("sharedSecretHex", pairing).fromHex()
        val t = str("transcriptHashHex", pairing).fromHex()

        val macL = SyncCrypto.computePairConfirmMac(secret, t, "laptop")
        assertEquals(str("confirmMacLaptopBase64", pairing), Base64.getEncoder().encodeToString(macL))

        val macP = SyncCrypto.computePairConfirmMac(secret, t, "phone")
        assertEquals(str("confirmMacPhoneBase64", pairing), Base64.getEncoder().encodeToString(macP))

        val keyAndRoom = SyncCrypto.derivePairingKeyAndRoom(secret, t)
        val expectedPk = str("permanentPairingKeyHex", pairing)
        val expectedSid = str("sessionRoomIdHex", pairing)

        assertEquals(expectedPk.lowercase(), keyAndRoom.pk.toHex().lowercase())
        assertEquals(expectedSid.lowercase(), keyAndRoom.sidHex.lowercase())
    }

    @Test
    fun step6_sessionHandshakeAndSessionKeys() {
        val pairing = obj("pairingAgreement")
        val handshake = obj("sessionHandshake")
        val pk = str("permanentPairingKeyHex", pairing).fromHex()

        val snLBase64 = str("laptopSessionNonceBase64", handshake)
        val snPBase64 = str("phoneSessionNonceBase64", handshake)
        val lastAckL = num("lastSeqAckedLaptop", handshake)
        val lastAckP = num("lastSeqAckedPhone", handshake)

        val helloMacL = SyncCrypto.computeHelloMac(pk, "laptop", snLBase64, lastAckL)
        assertEquals(str("helloMacLaptopBase64", handshake), Base64.getEncoder().encodeToString(helloMacL))

        val helloMacP = SyncCrypto.computeHelloMac(pk, "phone", snPBase64, lastAckP)
        assertEquals(str("helloMacPhoneBase64", handshake), Base64.getEncoder().encodeToString(helloMacP))

        val sessionKeys = SyncCrypto.deriveSessionKeys(
            pk,
            Base64.getDecoder().decode(snLBase64),
            Base64.getDecoder().decode(snPBase64)
        )
        assertEquals(str("keyL2PHex", handshake).lowercase(), sessionKeys.kL2P.toHex().lowercase())
        assertEquals(str("keyP2LHex", handshake).lowercase(), sessionKeys.kP2L.toHex().lowercase())
    }

    @Test
    fun step7_frameEncryptionAndDecryptionL2PAndP2L() {
        val handshake = obj("sessionHandshake")
        val kL2P = str("keyL2PHex", handshake).fromHex()
        val kP2L = str("keyP2LHex", handshake).fromHex()

        // Frame L2P
        val fL = obj("frameL2P")
        val tL = str("t", fL)
        val dL = str("d", fL)
        val ctrL = num("ctr", fL)
        val cL = str("ciphertextWithTagBase64", fL)
        val plainL = str("plaintextJson", fL)

        val decryptedL = SyncCrypto.decryptFramePayload(kL2P, tL, dL, ctrL, cL)
        assertEquals(plainL, String(decryptedL, StandardCharsets.UTF_8))

        // Frame P2L
        val fP = obj("frameP2L")
        val tP = str("t", fP)
        val dP = str("d", fP)
        val ctrP = num("ctr", fP)
        val cP = str("ciphertextWithTagBase64", fP)
        val plainP = str("plaintextJson", fP)

        val decryptedP = SyncCrypto.decryptFramePayload(kP2L, tP, dP, ctrP, cP)
        assertEquals(plainP, String(decryptedP, StandardCharsets.UTF_8))

        // Roundtrip test
        val encryptedP = SyncCrypto.encryptFramePayload(kP2L, tP, dP, ctrP, decryptedP)
        val roundtrip = SyncCrypto.decryptFramePayload(kP2L, tP, dP, ctrP, encryptedP)
        assertEquals(plainP, String(roundtrip, StandardCharsets.UTF_8))
    }
}
