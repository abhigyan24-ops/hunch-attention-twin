package com.dhyan.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*

enum class PastelTone { WHITE, SAGE, LAVENDER, SAND, SKY }

@Composable
fun pastelSurface(tone: PastelTone): Color = when (tone) {
    PastelTone.WHITE -> MaterialTheme.colorScheme.surface
    PastelTone.SAGE -> MaterialTheme.colorScheme.primaryContainer
    PastelTone.LAVENDER -> MaterialTheme.colorScheme.tertiaryContainer
    PastelTone.SAND -> MaterialTheme.colorScheme.secondaryContainer
    PastelTone.SKY -> if (MaterialTheme.colorScheme.background.luminance() < .5f) Color(0xFF183B40) else Color(0xFFE3F4F4)
}

@Composable
fun PastelSquircleCard(
    modifier: Modifier = Modifier, tone: PastelTone = PastelTone.WHITE,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(modifier, shape = RoundedCornerShape(28.dp), color = pastelSurface(tone),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content)
    }
}

@Composable
fun StatusPillBadge(text: String, modifier: Modifier = Modifier, active: Boolean = false) {
    Surface(modifier, shape = CircleShape, color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(Modifier.size(7.dp).background(if (active) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant, CircleShape))
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Screen-local input only. The same bounded mapping serves touch and accessibility. */
internal fun dialFraction(position: Offset, center: Offset): Float {
    if (!position.x.isFinite() || !position.y.isFinite()) return 0f
    val angle = atan2((center.y - position.y).coerceAtLeast(0f), position.x - center.x)
    return (1f - angle / PI.toFloat()).coerceIn(0f, 1f)
}

@Composable
fun SemicircularTickDial(
    value: Float, range: ClosedFloatingPointRange<Float>, valueLabel: String, caption: String,
    modifier: Modifier = Modifier, onValueChange: ((Float) -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val currentCallback by rememberUpdatedState(onValueChange)
    val safeValue = if (value.isFinite()) value.coerceIn(range.start, range.endInclusive) else range.start
    val fraction = ((safeValue - range.start) / (range.endInclusive - range.start).coerceAtLeast(.001f))
        .coerceIn(0f, 1f)
    val touch = if (onValueChange == null) Modifier else Modifier
        .pointerInput(range) {
            fun update(p: Offset) {
                val f = dialFraction(p, Offset(size.width / 2f, size.height * .64f))
                currentCallback?.invoke(range.start + f * (range.endInclusive - range.start))
            }
            detectTapGestures(onTap = ::update)
        }
        .pointerInput(range) {
            detectDragGestures { change, _ ->
                val f = dialFraction(change.position, Offset(size.width / 2f, size.height * .64f))
                currentCallback?.invoke(range.start + f * (range.endInclusive - range.start))
                change.consume()
            }
        }
    Box(modifier.fillMaxWidth().heightIn(min = 214.dp).then(touch).semantics(mergeDescendants = true) {
        contentDescription = caption
        stateDescription = valueLabel
        progressBarRangeInfo = ProgressBarRangeInfo(safeValue, range)
        if (onValueChange != null) setProgress { requested ->
            if (!requested.isFinite()) false else {
                onValueChange(requested.coerceIn(range.start, range.endInclusive)); true
            }
        }
    }) {
        Canvas(Modifier.fillMaxWidth().height(146.dp)) {
            val center = Offset(size.width / 2f, size.height - 8.dp.toPx())
            val radius = min(size.width * .45f, size.height * .88f)
            repeat(41) { index ->
                val angle = PI.toFloat() * (1f - index / 40f)
                val direction = Offset(cos(angle), -sin(angle))
                val length = if (index % 5 == 0) 13.dp.toPx() else 6.dp.toPx()
                drawLine(if (index / 40f <= fraction) colors.onSurface else colors.outlineVariant,
                    center + direction * (radius - length), center + direction * radius,
                    2.dp.toPx(), StrokeCap.Round)
            }
            val angle = PI.toFloat() * (1f - fraction)
            val tip = center + Offset(cos(angle), -sin(angle)) * (radius - 21.dp.toPx())
            val marker = Path().apply {
                moveTo(tip.x, tip.y - 5.dp.toPx())
                lineTo(tip.x - 4.dp.toPx(), tip.y + 4.dp.toPx())
                lineTo(tip.x + 4.dp.toPx(), tip.y + 4.dp.toPx()); close()
            }
            drawPath(marker, colors.onSurface)
        }
        Column(Modifier.align(Alignment.BottomCenter).padding(top = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(valueLabel, fontSize = 56.sp, lineHeight = 64.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
            Text(caption, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

data class ChartBar(val label: String, val value: Float)

internal fun recordedShare(part: Int, total: Int): Float? =
    if (total <= 0 || part < 0 || part > total) null else part.toFloat() / total

@Composable
fun RecordedShareRing(part: Int, total: Int, label: String) {
    val colors = MaterialTheme.colorScheme
    val share = recordedShare(part, total)
    Box(Modifier.size(108.dp).semantics(mergeDescendants = true) {
        contentDescription = if (share == null) "$label: no recorded share" else "$label: $part of $total recorded actions"
    }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(8.dp)) {
            drawArc(colors.onPrimaryContainer.copy(alpha = .12f), 0f, 360f, false, style = Stroke(9.dp.toPx()))
            if (share != null && share > 0f) drawArc(colors.onPrimaryContainer, -90f, share * 360f, false,
                style = Stroke(9.dp.toPx(), cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(share?.let { "${(it * 100).roundToInt()}%" } ?: "—", style = MaterialTheme.typography.headlineSmall)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun RoundedBarChart(bars: List<ChartBar>, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val safe = bars.map { it.copy(value = if (it.value.isFinite()) it.value.coerceAtLeast(0f) else 0f) }
    val max = safe.maxOfOrNull { it.value } ?: 0f
    Column(modifier.semantics { stateDescription = safe.joinToString { "${it.label}: ${it.value.toInt()}" } }) {
        if (max == 0f) Text("No recorded actions yet", style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant, modifier = Modifier.padding(vertical = 20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            safe.forEach { bar ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(bar.value.toInt().toString(), style = MaterialTheme.typography.labelLarge)
                    Canvas(Modifier.width(30.dp).height(104.dp)) {
                        val radius = androidx.compose.ui.geometry.CornerRadius(size.width / 2)
                        drawRoundRect(colors.surfaceContainerHigh, cornerRadius = radius)
                        val height = if (max > 0) size.height * bar.value / max else 0f
                        if (height > 0f) drawRoundRect(colors.onPrimaryContainer,
                            Offset(0f, size.height - height), androidx.compose.ui.geometry.Size(size.width, height), radius)
                    }
                    Text(bar.label, style = MaterialTheme.typography.labelSmall, maxLines = 2)
                }
            }
        }
    }
}

@Composable
fun MiniSparklineCard(
    title: String, value: String, series: List<Float>, modifier: Modifier = Modifier,
    tone: PastelTone = PastelTone.WHITE
) {
    val accent = MaterialTheme.colorScheme.onPrimaryContainer
    PastelSquircleCard(modifier, tone) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        val points = series.filter { it.isFinite() }
        if (points.size < 2) Text("No trend history", Modifier.heightIn(min = 40.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else Canvas(Modifier.fillMaxWidth().height(40.dp)) {
            val low = points.min(); val span = (points.max() - low).coerceAtLeast(.001f)
            val line = Path()
            points.forEachIndexed { i, v ->
                val x = size.width * i / points.lastIndex
                val y = size.height * (.9f - .8f * (v - low) / span)
                if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
            }
            drawPath(line, accent, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        }
        Text(value, style = MaterialTheme.typography.headlineLarge)
        Text("Recorded count", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun FloatingCapsuleNavBar(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier.widthIn(max = 480.dp).fillMaxWidth(), shape = RoundedCornerShape(36.dp),
        color = colors.surface, shadowElevation = 10.dp, border = BorderStroke(1.dp, colors.outlineVariant)) {
        Row(Modifier.padding(7.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Focus", "Insights", "Lab").forEachIndexed { index, label ->
                Column(Modifier.weight(1f).heightIn(min = 62.dp)
                    .background(if (selected == index) colors.primaryContainer else Color.Transparent, CircleShape)
                    .selectable(selected == index, role = Role.Tab, onClick = { onSelect(index) })
                    .testTag("nav-${label.lowercase()}").padding(vertical = 9.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Canvas(Modifier.size(22.dp)) {
                        val stroke = 1.8.dp.toPx()
                        when (index) {
                            0 -> { drawCircle(colors.onSurface, size.width * .38f, style = Stroke(stroke))
                                drawCircle(colors.onSurface, size.width * .1f) }
                            2 -> { drawRoundRect(colors.onSurface, cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()), style = Stroke(stroke))
                                drawLine(colors.onSurface, Offset(size.width * .25f, size.height * .5f), Offset(size.width * .75f, size.height * .5f), stroke) }
                            1 -> repeat(3) { i ->
                                drawLine(colors.onSurface, Offset(size.width * (i+1) / 4, size.height * .85f),
                                    Offset(size.width * (i+1) / 4, size.height * (.65f - i * .2f)), stroke, StrokeCap.Round)
                            }
                        }
                    }
                    Text(label, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
fun CalendarDayStrip(today: java.time.DayOfWeek, modifier: Modifier = Modifier) {
    val days = listOf(java.time.DayOfWeek.SUNDAY, java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.TUESDAY,
        java.time.DayOfWeek.WEDNESDAY, java.time.DayOfWeek.THURSDAY, java.time.DayOfWeek.FRIDAY, java.time.DayOfWeek.SATURDAY)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        days.forEach { day ->
            Surface(shape = CircleShape, color = if (day == today) MaterialTheme.colorScheme.primaryContainer else Color.Transparent) {
                Text(day.name.take(1), Modifier.padding(horizontal = 12.dp, vertical = 14.dp)
                    .semantics { contentDescription = day.name.lowercase() + if (day == today) ", today" else "" },
                    style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
