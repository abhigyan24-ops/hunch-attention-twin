package com.dhyan.app.wellbeing

import android.app.TimePickerDialog
import android.content.Intent
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dhyan.app.ui.components.PastelSquircleCard
import com.dhyan.app.ui.components.PastelTone
import com.dhyan.app.ui.components.StatusPillBadge

/**
 * Interactive Bedtime & Digital Wellbeing Schedule Card matching Hunch's Daylight Studio UI.
 *
 * Allows viewing the inferred or custom bedtime window, editing bedtime/wake time via native
 * [TimePickerDialog], re-syncing with Digital Wellbeing, or granting Usage Access.
 */
@Composable
fun BedtimeSettingsCard(
    modifier: Modifier = Modifier,
    onScheduleChanged: ((BedtimeSchedule) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val bedtimeManager = remember(context) { BedtimeManager(context) }

    var hasUsageAccess by remember { mutableStateOf(bedtimeManager.hasUsageStatsPermission()) }
    var schedule by remember { mutableStateOf(bedtimeManager.getBedtimeSchedule()) }

    fun refreshState() {
        hasUsageAccess = bedtimeManager.hasUsageStatsPermission()
        schedule = bedtimeManager.getBedtimeSchedule()
        onScheduleChanged?.invoke(schedule)
    }

    // Re-check permission and refresh inferred schedule when returning from system Settings.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val statusText = when {
        !schedule.isAutoDetected -> "Custom Schedule"
        schedule.isFallbackDefault -> "Default Schedule (Grant Usage Access to Auto-Detect)"
        else -> "Synced with Digital Wellbeing"
    }
    val statusActive = !schedule.isFallbackDefault

    val durationTotal = schedule.durationMinutes()
    val durationHours = durationTotal / 60
    val durationMins = durationTotal % 60
    val durationLabel = if (durationMins == 0) "${durationHours}h rest window" else "${durationHours}h ${durationMins}m rest window"

    PastelSquircleCard(modifier = modifier, tone = PastelTone.LAVENDER) {
        StatusPillBadge(text = statusText, active = statusActive)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Bedtime Schedule",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = durationLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TimeSlotTile(
                label = "BEDTIME",
                timeText = schedule.formattedBedtime(),
                hint = "Tap to adjust",
                modifier = Modifier.weight(1f),
                onClick = {
                    val is24Hour = DateFormat.is24HourFormat(context)
                    TimePickerDialog(
                        context,
                        { _, hourOfDay, minute ->
                            schedule = bedtimeManager.setManualBedtime(
                                bedHour = hourOfDay,
                                bedMinute = minute,
                                wakeHour = schedule.wakeHour,
                                wakeMinute = schedule.wakeMinute
                            )
                            onScheduleChanged?.invoke(schedule)
                        },
                        schedule.bedtimeHour,
                        schedule.bedtimeMinute,
                        is24Hour
                    ).show()
                }
            )

            TimeSlotTile(
                label = "WAKE TIME",
                timeText = schedule.formattedWakeTime(),
                hint = "Tap to adjust",
                modifier = Modifier.weight(1f),
                onClick = {
                    val is24Hour = DateFormat.is24HourFormat(context)
                    TimePickerDialog(
                        context,
                        { _, hourOfDay, minute ->
                            schedule = bedtimeManager.setManualBedtime(
                                bedHour = schedule.bedtimeHour,
                                bedMinute = schedule.bedtimeMinute,
                                wakeHour = hourOfDay,
                                wakeMinute = minute
                            )
                            onScheduleChanged?.invoke(schedule)
                        },
                        schedule.wakeHour,
                        schedule.wakeMinute,
                        is24Hour
                    ).show()
                }
            )
        }

        if (!schedule.isAutoDetected || !hasUsageAccess) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        if (!schedule.isAutoDetected) {
            OutlinedButton(
                onClick = {
                    hasUsageAccess = bedtimeManager.hasUsageStatsPermission()
                    schedule = bedtimeManager.syncWithDigitalWellbeing()
                    onScheduleChanged?.invoke(schedule)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Re-sync with Digital Wellbeing")
            }
        }

        if (!hasUsageAccess) {
            Button(
                onClick = {
                    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Enable Digital Wellbeing Auto-Bedtime")
            }
        }
    }
}

@Composable
private fun TimeSlotTile(
    label: String,
    timeText: String,
    hint: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = timeText,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
