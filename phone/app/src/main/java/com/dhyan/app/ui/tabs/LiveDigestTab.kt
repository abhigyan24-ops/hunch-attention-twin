package com.dhyan.app.ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dhyan.app.ui.components.FocusRing
import com.dhyan.app.ui.components.LaptopStateBadge
import com.dhyan.app.ui.model.ActionVerdict
import com.dhyan.app.ui.model.DecisionFeedItem
import com.dhyan.app.ui.model.DhyanStateHolder
import com.dhyan.app.ui.model.DigestItem
import com.dhyan.app.ui.theme.BorderSubtle
import com.dhyan.app.ui.theme.DhyanPrimary
import com.dhyan.app.ui.theme.StateDeepFocus
import com.dhyan.app.ui.theme.StateDistracted
import com.dhyan.app.ui.theme.SurfaceCard
import com.dhyan.app.ui.theme.SurfaceElevated
import com.dhyan.app.ui.theme.TextMuted
import com.dhyan.app.ui.theme.TextPrimary
import com.dhyan.app.ui.theme.TextSecondary
import com.dhyan.app.ui.theme.VerdictAllow
import com.dhyan.app.ui.theme.VerdictDelay
import com.dhyan.app.ui.theme.VerdictDigest
import com.dhyan.app.ui.theme.VerdictSilent
import com.dhyan.app.ui.theme.VoidBlack

@Composable
fun LiveDigestTab(
    stateHolder: DhyanStateHolder,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VoidBlack)
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Cross-Device Status Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Live Attention",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Text(
                    text = "Dual-device telemetry synchronized",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
            LaptopStateBadge(presence = stateHolder.laptopPresence)
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Center Animated Focus Ring
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            FocusRing(
                state = stateHolder.currentAttentionState,
                sessionMinutes = stateHolder.sessionElapsedMin,
                ringSize = 200.dp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Held Digest Queue Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "DIGEST QUEUE",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.0.sp
                    ),
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(DhyanPrimary.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${stateHolder.digestQueue.size} held",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = DhyanPrimary
                    )
                }
            }
            if (stateHolder.digestQueue.isNotEmpty()) {
                Text(
                    text = "Release All",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = DhyanPrimary,
                    modifier = Modifier.clickable {
                        stateHolder.digestQueue.clear()
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (stateHolder.digestQueue.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceCard)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Digest Queue Clear",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = StateDeepFocus
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "No notifications held. All incoming alerts triaged cleanly.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                stateHolder.digestQueue.forEach { item ->
                    DigestItemCard(
                        item = item,
                        onShowNow = { stateHolder.showDigestNow(item) },
                        onImportant = { stateHolder.markImportant(item) },
                        onNoise = { stateHolder.markNoise(item) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Recent Real-Time Decisions Stream
        Text(
            text = "RECENT TWIN DECISIONS",
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.0.sp
            ),
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(10.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            stateHolder.decisionFeed.forEach { decision ->
                DecisionCard(decision = decision)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun DigestItemCard(
    item: DigestItem,
    onShowNow: () -> Unit,
    onImportant: () -> Unit,
    onNoise: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceCard)
            .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.appName,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "(${item.sender})",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Text(
                text = item.receivedAgo,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = item.heldReason,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Action Buttons: Show Now, Important (+feedback), Noise (-feedback)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onShowNow,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DhyanPrimary,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(text = "Show Now", style = MaterialTheme.typography.labelSmall)
            }

            OutlinedButton(
                onClick = onImportant,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = StateDeepFocus)
            ) {
                Text(text = "Important", style = MaterialTheme.typography.labelSmall)
            }

            OutlinedButton(
                onClick = onNoise,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = StateDistracted)
            ) {
                Text(text = "Noise", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun DecisionCard(decision: DecisionFeedItem) {
    val (actionColor, actionBg) = when (decision.action) {
        ActionVerdict.ALLOW -> Pair(VerdictAllow, Color(0xFF0F2D18))
        ActionVerdict.DIGEST -> Pair(VerdictDigest, Color(0xFF0C2444))
        ActionVerdict.DELAY -> Pair(VerdictDelay, Color(0xFF3D2E0B))
        ActionVerdict.SILENT -> Pair(VerdictSilent, Color(0xFF1E2228))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceElevated)
            .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(actionBg)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = decision.action.label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = actionColor
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = decision.appName,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = TextPrimary
                )
                Text(
                    text = decision.timestamp,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = decision.reason,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}
