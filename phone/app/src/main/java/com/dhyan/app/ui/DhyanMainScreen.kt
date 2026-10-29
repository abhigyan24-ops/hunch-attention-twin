package com.dhyan.app.ui

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dhyan.app.SchemaVersion
import com.dhyan.app.ui.model.DhyanStateHolder
import com.dhyan.app.ui.settings.SettingsBottomSheet
import com.dhyan.app.ui.tabs.InsightsTab
import com.dhyan.app.ui.tabs.LiveDigestTab
import com.dhyan.app.ui.tabs.SimulateTab
import com.dhyan.app.ui.theme.BorderSubtle
import com.dhyan.app.ui.theme.DhyanPrimary
import com.dhyan.app.ui.theme.StateDistracted
import com.dhyan.app.ui.theme.SurfaceCard
import com.dhyan.app.ui.theme.SurfaceElevated
import com.dhyan.app.ui.theme.TextMuted
import com.dhyan.app.ui.theme.TextPrimary
import com.dhyan.app.ui.theme.TextSecondary
import com.dhyan.app.ui.theme.VoidBlack

@Composable
fun DhyanMainScreen(
    stateHolder: DhyanStateHolder = remember { DhyanStateHolder() }
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var isSettingsOpen by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = VoidBlack,
        topBar = {
            TopBar(
                stateHolder = stateHolder,
                onOpenSettings = { isSettingsOpen = true }
            )
        },
        bottomBar = {
            BottomNav(
                selectedTab = selectedTab,
                onSelectTab = { selectedTab = it },
                digestCount = stateHolder.digestQueue.size
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                0 -> SimulateTab(stateHolder = stateHolder)
                1 -> LiveDigestTab(stateHolder = stateHolder)
                2 -> InsightsTab(stateHolder = stateHolder)
            }
        }
    }

    if (isSettingsOpen) {
        SettingsBottomSheet(
            stateHolder = stateHolder,
            onDismiss = { isSettingsOpen = false }
        )
    }
}

@Composable
private fun TopBar(
    stateHolder: DhyanStateHolder,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(VoidBlack)
            .statusBarsPadding()
            .border(width = 0.5.dp, color = BorderSubtle)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Lotus / Attention Dot Emblem
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (stateHolder.isKillSwitchActive) StateDistracted else DhyanPrimary)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "DHYAN",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp
                            ),
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ध्यान",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                    Text(
                        text = "v${SchemaVersion.VERSION} · Attention Digital Twin",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        color = TextSecondary
                    )
                }
            }

            // Persona & Settings Pill
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(SurfaceCard)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(100.dp))
                    .clickable(onClick = onOpenSettings)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stateHolder.selectedPersona.name.split(" ").first(),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "\u2699",
                    style = MaterialTheme.typography.bodySmall,
                    color = DhyanPrimary
                )
            }
        }
    }
}

@Composable
private fun BottomNav(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    digestCount: Int
) {
    NavigationBar(
        containerColor = SurfaceElevated,
        tonalElevation = 0.dp,
        modifier = Modifier.border(width = 0.5.dp, color = BorderSubtle)
    ) {
        // Tab 0: Simulate
        NavigationBarItem(
            selected = selectedTab == 0,
            onClick = { onSelectTab(0) },
            icon = {
                Text(
                    text = "\u25CE", // Target / Simulate symbol
                    fontSize = 18.sp,
                    color = if (selectedTab == 0) DhyanPrimary else TextMuted
                )
            },
            label = {
                Text(
                    text = "Simulate",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                )
            },
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = DhyanPrimary.copy(alpha = 0.15f),
                selectedTextColor = DhyanPrimary,
                unselectedTextColor = TextMuted
            )
        )

        // Tab 1: Live & Digest
        NavigationBarItem(
            selected = selectedTab == 1,
            onClick = { onSelectTab(1) },
            icon = {
                Box {
                    Text(
                        text = "\u25C9", // Focus Ring symbol
                        fontSize = 18.sp,
                        color = if (selectedTab == 1) DhyanPrimary else TextMuted
                    )
                    if (digestCount > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(DhyanPrimary)
                        )
                    }
                }
            },
            label = {
                Text(
                    text = if (digestCount > 0) "Live ($digestCount)" else "Live",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                )
            },
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = DhyanPrimary.copy(alpha = 0.15f),
                selectedTextColor = DhyanPrimary,
                unselectedTextColor = TextMuted
            )
        )

        // Tab 2: Insights
        NavigationBarItem(
            selected = selectedTab == 2,
            onClick = { onSelectTab(2) },
            icon = {
                Text(
                    text = "\u25E4", // Chart / Insights symbol
                    fontSize = 18.sp,
                    color = if (selectedTab == 2) DhyanPrimary else TextMuted
                )
            },
            label = {
                Text(
                    text = "Insights",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                )
            },
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = DhyanPrimary.copy(alpha = 0.15f),
                selectedTextColor = DhyanPrimary,
                unselectedTextColor = TextMuted
            )
        )
    }
}
