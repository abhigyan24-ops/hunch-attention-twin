package com.dhyan.app.vault

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import java.io.InputStream
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.BadPaddingException
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class VaultBedtime(val bedHour: Int, val bedMin: Int, val wakeHour: Int, val wakeMin: Int, val isAutoDetected: Boolean)

data class VaultPayload(
    val bedtimeSchedule: VaultBedtime,
    val vipPackages: List<String>,
    val learnedTypicalStreakMinutes: Int,
    val realMinutesTracked: Int,
    val hourlyDistractionWeights: Map<Int, Float>,
    val exportedAtMillis: Long,
) {
    fun toJsonBytes(): ByteArray = JSONObject().apply {
        put("bedtimeSchedule", JSONObject().apply {
            put("bedHour", bedtimeSchedule.bedHour)
            put("bedMin", bedtimeSchedule.bedMin)
            put("wakeHour", bedtimeSchedule.wakeHour)
            put("wakeMin", bedtimeSchedule.wakeMin)
            put("isAutoDetected", bedtimeSchedule.isAutoDetected)
        })
        put("vipPackages", JSONArray(vipPackages))
        put("learnedTypicalStreakMinutes", learnedTypicalStreakMinutes)
        put("realMinutesTracked", realMinutesTracked)
        put("hourlyDistractionWeights", JSONObject(hourlyDistractionWeights.mapKeys { it.key.toString() }))
        put("exportedAtMillis", exportedAtMillis)
    }.toString().toByteArray(Charsets.UTF_8)

    companion object {
        fun fromJsonBytes(bytes: ByteArray): VaultPayload {
            val json = JSONObject(bytes.toString(Charsets.UTF_8))
            val bed = json.getJSONObject("bedtimeSchedule")
            val schedule = VaultBedtime(
                bed.getInt("bedHour"), bed.getInt("bedMin"),
                bed.getInt("wakeHour"), bed.getInt("wakeMin"), bed.getBoolean("isAutoDetected"),
            )
            require(schedule.bedHour in 0..23 && schedule.wakeHour in 0..23)
            require(schedule.bedMin in 0..59 && schedule.wakeMin in 0..59)
            val packages = json.optJSONArray("vipPackages") ?: JSONArray()
            val weights = json.optJSONObject("hourlyDistractionWeights") ?: JSONObject()
            return VaultPayload(
                schedule, List(packages.length()) { packages.getString(it) },
                json.optInt("learnedTypicalStreakMinutes"), json.optInt("realMinutesTracked"),
                weights.keys().asSequence().associate { it.toInt() to weights.getDouble(it).toFloat() },
                json.getLong("exportedAtMillis"),
            ).also { payload ->
                require(payload.learnedTypicalStreakMinutes >= 0 && payload.realMinutesTracked >= 0)
                require(payload.hourlyDistractionWeights.all { (hour, weight) -> hour in 0..23 && weight.isFinite() })
            }
        }
    }
}

object EncryptedVaultManager {
    private val magic = "HNCHV1".toByteArray(Charsets.US_ASCII)
    private val random = SecureRandom()
    private const val MAX_BYTES = 256 * 1024
    private const val HEADER_BYTES = 34
    private const val OVERHEAD = HEADER_BYTES + 16
    const val BACKUP_NAME = "hunch_vault_backup.enc"

    fun encryptPayload(jsonBytes: ByteArray, passphrase: String): ByteArray {
        require(jsonBytes.isNotEmpty() && jsonBytes.size <= MAX_BYTES - OVERHEAD)
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        return magic + salt + iv + crypt(Cipher.ENCRYPT_MODE, jsonBytes, passphrase, salt, iv)
    }

    fun decryptPayload(envelopeBytes: ByteArray, passphrase: String): ByteArray {
        if (envelopeBytes.size !in (OVERHEAD + 1)..MAX_BYTES ||
            !envelopeBytes.copyOfRange(0, magic.size).contentEquals(magic)
        ) throw SecurityException("Invalid recovery key or tampered vault")
        return try {
            crypt(
                Cipher.DECRYPT_MODE, envelopeBytes.copyOfRange(HEADER_BYTES, envelopeBytes.size),
                passphrase, envelopeBytes.copyOfRange(6, 22), envelopeBytes.copyOfRange(22, 34),
            )
        } catch (_: BadPaddingException) {
            throw SecurityException("Invalid recovery key or tampered vault")
        }
    }

    private fun crypt(mode: Int, bytes: ByteArray, passphrase: String, salt: ByteArray, iv: ByteArray): ByteArray {
        require(passphrase.isNotBlank() && passphrase.length <= 1024)
        val chars = passphrase.toCharArray()
        val spec = PBEKeySpec(chars, salt, 210_000, 256)
        chars.fill('\u0000')
        val key = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
        try {
            return Cipher.getInstance("AES/GCM/NoPadding").run {
                init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
                doFinal(bytes)
            }
        } finally {
            key.fill(0)
        }
    }

    private fun file(context: Context) = AtomicFile(context.filesDir.resolve(BACKUP_NAME))

    fun writeBackup(context: Context, jsonBytes: ByteArray, passphrase: String) {
        val encrypted = encryptPayload(jsonBytes, passphrase)
        val backup = file(context)
        val stream = backup.startWrite()
        try {
            stream.write(encrypted)
            backup.finishWrite(stream)
        } catch (failure: Throwable) {
            backup.failWrite(stream)
            throw failure
        }
    }

    fun readBackup(context: Context, passphrase: String): ByteArray =
        file(context).openRead().use { decryptPayload(readBounded(it), passphrase) }

    fun exportEncryptedVaultToUri(context: Context, uri: Uri, jsonBytes: ByteArray, passphrase: String) {
        val encrypted = encryptPayload(jsonBytes, passphrase)
        requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { it.write(encrypted) }
    }

    fun importEncryptedVaultFromUri(context: Context, uri: Uri, passphrase: String): ByteArray =
        requireNotNull(context.contentResolver.openInputStream(uri)).use {
            decryptPayload(readBounded(it), passphrase)
        }

    fun saveEncryptedInternalBackup(context: Context, payload: VaultPayload, passphrase: String) =
        writeBackup(context, payload.toJsonBytes(), passphrase)

    fun loadEncryptedInternalBackup(context: Context, passphrase: String): VaultPayload =
        VaultPayload.fromJsonBytes(readBackup(context, passphrase))

    fun exportToUri(context: Context, uri: Uri, payload: VaultPayload, passphrase: String) =
        exportEncryptedVaultToUri(context, uri, payload.toJsonBytes(), passphrase)

    fun importFromUri(context: Context, uri: Uri, passphrase: String): VaultPayload =
        VaultPayload.fromJsonBytes(importEncryptedVaultFromUri(context, uri, passphrase))

    fun hasBackup(context: Context): Boolean = file(context).baseFile.isFile

    fun deleteBackup(context: Context) = file(context).delete()

    private fun readBounded(stream: InputStream): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = stream.read(buffer)
            if (count == -1) break
            if (out.size() + count > MAX_BYTES) throw SecurityException("Recovery vault too large")
            out.write(buffer, 0, count)
        }
        return out.toByteArray()
    }
}
