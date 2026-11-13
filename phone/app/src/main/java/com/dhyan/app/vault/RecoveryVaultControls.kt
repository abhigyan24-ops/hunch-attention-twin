package com.dhyan.app.vault

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.dhyan.app.wellbeing.BedtimeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
fun RecoveryVaultControls() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var target by remember { mutableStateOf<Uri?>(null) }
    var restoringBackup by remember { mutableStateOf(false) }
    var hasBackup by remember { mutableStateOf(EncryptedVaultManager.hasBackup(context)) }
    var exporting by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {
        exporting = true
        target = it
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        exporting = false
        target = it
    }
    Text("Encrypted bedtime recovery")
    Text("Keep a recovery file and its passphrase to restore your bedtime after reinstalling.")
    TextButton(enabled = !busy, onClick = { create.launch("hunch-recovery.hunchvault") }) {
        Text("Export Encrypted Recovery File")
    }
    TextButton(enabled = !busy, onClick = { open.launch(arrayOf("application/octet-stream", "application/*")) }) {
        Text("Restore from Recovery File")
    }
    if (hasBackup) {
        TextButton(enabled = !busy, onClick = {
            exporting = false
            restoringBackup = true
        }) { Text("Restore from Android Backup") }
    }
    result?.let { Text(it) }
    val uri = target
    if (uri != null || restoringBackup) {
        AlertDialog(
            onDismissRequest = { target = null; restoringBackup = false; password = "" },
            title = { Text(if (exporting) "Choose a recovery passphrase" else "Enter the recovery passphrase") },
            text = {
                OutlinedTextField(
                    value = password, onValueChange = { password = it.take(1024) },
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text("Recovery passphrase") }, singleLine = true,
                )
            },
            dismissButton = { TextButton(onClick = { target = null; restoringBackup = false; password = "" }) { Text("Cancel") } },
            confirmButton = {
                TextButton(enabled = password.isNotBlank() && (!exporting || password.length >= 16), onClick = {
                    val passphrase = password
                    val export = exporting
                    val internalBackup = restoringBackup
                    restoringBackup = false
                    target = null
                    password = ""
                    busy = true
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                val manager = BedtimeManager(context)
                                if (export) {
                                    val bedtime = manager.getBedtimeSchedule()
                                    val bytes = JSONObject().apply {
                                        put("bedtimeSchedule", JSONObject().apply {
                                            put("bedHour", bedtime.bedtimeHour)
                                            put("bedMin", bedtime.bedtimeMinute)
                                            put("wakeHour", bedtime.wakeHour)
                                            put("wakeMin", bedtime.wakeMinute)
                                            put("isAutoDetected", bedtime.isAutoDetected)
                                        })
                                        put("exportedAtMillis", System.currentTimeMillis())
                                    }.toString().toByteArray(Charsets.UTF_8)
                                    EncryptedVaultManager.exportEncryptedVaultToUri(context, requireNotNull(uri), bytes, passphrase)
                                    EncryptedVaultManager.writeBackup(context, bytes, passphrase)
                                } else {
                                    val data = if (internalBackup) {
                                        EncryptedVaultManager.readBackup(context, passphrase)
                                    } else {
                                        EncryptedVaultManager.importEncryptedVaultFromUri(context, requireNotNull(uri), passphrase)
                                    }
                                    val schedule = JSONObject(data.toString(Charsets.UTF_8)).getJSONObject("bedtimeSchedule")
                                    val bedHour = schedule.getInt("bedHour")
                                    val bedMin = schedule.getInt("bedMin")
                                    val wakeHour = schedule.getInt("wakeHour")
                                    val wakeMin = schedule.getInt("wakeMin")
                                    require(bedHour in 0..23 && wakeHour in 0..23 && bedMin in 0..59 && wakeMin in 0..59)
                                    manager.setManualBedtime(bedHour, bedMin, wakeHour, wakeMin)
                                }
                            }
                            if (export) hasBackup = true
                            result = if (export) "Encrypted recovery file saved." else "Bedtime restored as a manual schedule."
                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            result = "Recovery could not be completed. Check the file and passphrase."
                        } finally {
                            busy = false
                        }
                    }
                }) { Text(if (exporting) "Encrypt and save" else "Restore bedtime") }
            },
        )
    }
}
