package com.dhyan.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.dhyan.app.ui.model.DhyanStateHolder
import com.dhyan.app.ui.settings.SettingsBottomSheet
import com.dhyan.app.ui.tabs.*
import com.dhyan.app.ui.components.FloatingCapsuleNavBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DhyanMainScreen(stateHolder: DhyanStateHolder? = null) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val owner = context as androidx.lifecycle.ViewModelStoreOwner
    val provider = remember(owner) {
        val db = com.dhyan.app.data.DhyanDatabase.getInstance(context)
        androidx.lifecycle.ViewModelProvider(owner, object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                val model: androidx.lifecycle.ViewModel = when (modelClass) {
                    com.dhyan.app.ui.viewmodel.SimulateViewModel::class.java ->
                        com.dhyan.app.ui.viewmodel.SimulateViewModel()
                    com.dhyan.app.ui.viewmodel.LiveDigestViewModel::class.java ->
                        com.dhyan.app.ui.viewmodel.LiveDigestViewModel(
                            db.heldNotifDao(), db.decisionDao(), db.feedbackDao(),
                            com.dhyan.app.actuator.KillSwitchController(
                                com.dhyan.app.actuator.RoomHeldStore(db.heldNotifDao()),
                                com.dhyan.app.actuator.release.WorkManagerReleaseJobScheduler(context)
                            ), isFakeProvider = false
                        )
                    com.dhyan.app.ui.viewmodel.InsightsViewModel::class.java ->
                        com.dhyan.app.ui.viewmodel.InsightsViewModel(db.decisionDao(), db.heldNotifDao())
                    com.dhyan.app.ui.viewmodel.SettingsViewModel::class.java ->
                        com.dhyan.app.ui.viewmodel.SettingsViewModel(
                            vipDao = db.vipDao(), settingsDao = db.settingsDao(),
                            digestPoster = com.dhyan.app.actuator.digest.AndroidDigestPoster(context),
                            syncManager = runCatching { com.dhyan.app.DhyanApplication.syncManager }.getOrNull()
                        )
                    else -> error("Unsupported Dhyan UI ViewModel")
                }
                return model as T
            }
        })
    }
    val simulate = remember(provider) { provider[com.dhyan.app.ui.viewmodel.SimulateViewModel::class.java] }
    val live = remember(provider) { provider[com.dhyan.app.ui.viewmodel.LiveDigestViewModel::class.java] }
    val insights = remember(provider) { provider[com.dhyan.app.ui.viewmodel.InsightsViewModel::class.java] }
    val settings = remember(provider) { provider[com.dhyan.app.ui.viewmodel.SettingsViewModel::class.java] }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Column {
                    Text("Dhyan", style = MaterialTheme.typography.titleLarge)
                    Text("A little space to focus", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } },
                actions = {
                    TextButton(onClick = { settingsOpen = true }) { Text("Settings") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center) {
                FloatingCapsuleNavBar(selectedTab, { selectedTab = it })
            }
        }
    ) { insets ->
        Box(Modifier.fillMaxSize().padding(insets)) {
            when (selectedTab) {
                0 -> LiveDigestTab(viewModel = live)
                1 -> InsightsTab(viewModel = insights)
                else -> SimulateTab(viewModel = simulate)
            }
        }
    }
    if (settingsOpen) SettingsBottomSheet(viewModel = settings, onDismiss = { settingsOpen = false })
}

@Composable
private fun DestinationMark(index: Int) {
    val color = LocalContentColor.current
    Canvas(Modifier.size(24.dp)) {
        val stroke = 2.dp.toPx()
        when (index) {
            0 -> {
                drawCircle(color, radius = size.minDimension * 0.38f, style = Stroke(stroke))
                drawCircle(color, radius = size.minDimension * 0.12f)
            }
            2 -> {
                drawRoundRect(color, cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()),
                    style = Stroke(stroke))
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * .25f, size.height * .5f),
                    androidx.compose.ui.geometry.Offset(size.width * .75f, size.height * .5f), stroke)
            }
            1 -> repeat(3) { bar ->
                val x = (bar + 1) * size.width / 4
                drawLine(color, androidx.compose.ui.geometry.Offset(x, size.height * .85f),
                    androidx.compose.ui.geometry.Offset(x, size.height * (.65f - bar * .22f)), stroke)
            }
        }
    }
}
