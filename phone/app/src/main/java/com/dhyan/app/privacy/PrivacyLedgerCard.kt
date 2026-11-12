package com.dhyan.app.privacy

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dhyan.app.ui.components.PastelSquircleCard

@Composable
fun PrivacyLedgerCard(
    snapshot: LedgerSnapshot,
    onForgetToday: () -> Unit
) {
    PastelSquircleCard {
        Text(
            "\uD83D\uDD10 Privacy Ledger \u2014 Today",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        LedgerRow("App Transitions Observed", snapshot.appTransitionsCount)
        LedgerRow("Notifications Held", snapshot.notificationsHeldCount)
        LedgerRow("OTPs Passed Through", snapshot.otpBypassedCount)
        LedgerRow("Circadian Syncs", snapshot.circadianSyncCount)
        Spacer(Modifier.height(4.dp))
        HorizontalDivider()
        Spacer(Modifier.height(4.dp))
        LedgerRow("Keystrokes Logged", snapshot.rawKeystrokesLogged, zeroLabel = "\u2705 None \u2014 by design")
        LedgerRow("Notification Text Stored", snapshot.rawNotificationTextBytesStored, zeroLabel = "\u2705 Zero bytes \u2014 volatile only")
        LedgerRow("Source Code Captured", snapshot.sourceCodeBytesCaptured, zeroLabel = "\u2705 None \u2014 by design")
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onForgetToday,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        ) {
            Text("\uD83D\uDDD1\uFE0F Forget Today's Data", color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Composable
private fun LedgerRow(label: String, count: Int, zeroLabel: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(
            text = if (count == 0 && zeroLabel != null) zeroLabel else count.toString(),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}
