package com.dhyan.app.onboarding

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Display after the user selects a capability. The caller checks its actual grant on return. */
@Composable
fun ProgressiveTrustDialog(
    capability: TrustCapability,
    onDismiss: () -> Unit,
    onSettingsReturned: () -> Unit,
) {
    val packageName = LocalContext.current.packageName
    var showHelp by rememberSaveable(capability) { mutableStateOf(false) }
    var returnedFromAppInfo by rememberSaveable(capability) { mutableStateOf(false) }
    var error by rememberSaveable(capability) { mutableStateOf<String?>(null) }
    val featureSettings = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { onSettingsReturned() }
    val appInfo = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { returnedFromAppInfo = true }
    val notifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { onSettingsReturned() }

    fun launchSettings(launch: (Intent) -> Unit, intent: Intent) {
        error = null
        try {
            launch(intent)
        } catch (_: ActivityNotFoundException) {
            error = "This device has no shortcut for that setting. Open Android Settings to find it."
        } catch (_: SecurityException) {
            error = "Android could not open this setting. Check your device policy in Settings."
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(capability.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(capability.disclosure)
                if (capability.canShowRestrictedSettingsHelp) {
                    TextButton(onClick = { showHelp = !showHelp }) {
                        Text(if (showHelp) "Hide restricted settings help" else "Setting unavailable?")
                    }
                    if (showHelp) {
                        Text(
                            "If Android marks the switch as restricted, open App info. " +
                                "If Android offers Allow restricted settings in the three-dot menu, " +
                                "review its warning and decide whether to allow it. Return here " +
                                "to open the feature settings again.",
                        )
                        TextButton(onClick = { launchSettings({ appInfo.launch(it) }, appDetailsIntent(packageName)) }) {
                            Text("Open App info")
                        }
                        if (returnedFromAppInfo) {
                            Text("Back from App info. Check the feature switch in Android Settings.")
                        }
                    }
                }
                if (capability == TrustCapability.BEDTIME && Build.VERSION.SDK_INT >= 33) {
                    TextButton(onClick = { notifications.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                        Text("Enable optional notifications")
                    }
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                launchSettings({ featureSettings.launch(it) }, capability.settingsIntent(packageName))
            }) { Text(if (returnedFromAppInfo) "Return to feature settings" else "Open feature settings") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}
