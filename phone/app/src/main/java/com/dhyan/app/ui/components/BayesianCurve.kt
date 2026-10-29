package com.dhyan.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dhyan.app.ui.theme.BorderSubtle
import com.dhyan.app.ui.theme.DhyanPrimary
import com.dhyan.app.ui.theme.DhyanSecondary
import com.dhyan.app.ui.theme.SurfaceCard
import com.dhyan.app.ui.theme.TextMuted
import com.dhyan.app.ui.theme.TextPrimary
import com.dhyan.app.ui.theme.TextSecondary
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow

import java.util.Locale

@Composable
fun BayesianCurveCard(
    alpha: Double,
    beta: Double,
    lognormMu: Double,
    lognormSigma: Double,
    updateCount: Int,
    nEff: Double = 0.0,
    modelVersion: Long = 0L,
    priorAlpha: Double = 1.0,
    priorBeta: Double = 1.0,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceCard)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "TWIN LAB: POSTERIOR CONVERGENCE",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.0.sp
                    ),
                    color = TextSecondary
                )
                Text(
                    text = "Resolved ModelSnapshot parameters (prior vs posterior)",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF0D1117))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "v$modelVersion · nEff=${String.format(Locale.US, "%.1f", nEff)}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = DhyanPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Prior vs Posterior summary
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF161B22))
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "POPULATION PRIOR",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextMuted
                )
                Text(
                    text = "Beta(\u03B1=${String.format(Locale.US, "%.1f", priorAlpha)}, \u03B2=${String.format(Locale.US, "%.1f", priorBeta)})",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = TextSecondary
                )
                Text(
                    text = "E[p] = ${String.format(Locale.US, "%.2f", priorAlpha / (priorAlpha + priorBeta))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "RESOLVED POSTERIOR",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = DhyanPrimary
                )
                Text(
                    text = "Beta(\u03B1=${String.format(Locale.US, "%.1f", alpha)}, \u03B2=${String.format(Locale.US, "%.1f", beta)})",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Text(
                    text = "E[p] = ${String.format(Locale.US, "%.2f", alpha / (alpha + beta))}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = DhyanPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Beta distribution curve (P_pickup posterior)
        Text(
            text = "Pickup Probability Belief: Beta(\u03B1=${String.format(Locale.US, "%.1f", alpha)}, \u03B2=${String.format(Locale.US, "%.1f", beta)})",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Expected p_pickup: ${String.format(Locale.US, "%.2f", alpha / (alpha + beta))} (pure TwinEngine conjugate posterior)",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )

        Spacer(modifier = Modifier.height(8.dp))

        BetaDistributionCanvas(
            alpha = alpha,
            beta = beta,
            lineColor = DhyanPrimary,
            fillColor = DhyanPrimary.copy(alpha = 0.2f),
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(BorderSubtle)
        )
        Spacer(modifier = Modifier.height(14.dp))

        // LogNormal dwell distribution curve
        val medianDwell = exp(lognormMu)
        Text(
            text = "Dwell Duration: LogNormal(\u03BC=${String.format(Locale.US, "%.2f", lognormMu)}, \u03C3=${String.format(Locale.US, "%.2f", lognormSigma)})",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Median dwell if lured: ${String.format(Locale.US, "%.1f", medianDwell)} min",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )

        Spacer(modifier = Modifier.height(8.dp))

        LogNormalDistributionCanvas(
            mu = lognormMu,
            sigma = lognormSigma,
            lineColor = DhyanSecondary,
            fillColor = DhyanSecondary.copy(alpha = 0.2f),
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
        )
    }
}

@Composable
fun BetaDistributionCanvas(
    alpha: Double,
    beta: Double,
    lineColor: Color,
    fillColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        val steps = 80
        val points = mutableListOf<Offset>()

        // Evaluate unnormalized log-pdf to prevent numerical overflow
        val logVals = DoubleArray(steps)
        var maxLogVal = -Double.MAX_VALUE

        for (i in 1 until steps) {
            val x = i.toDouble() / steps
            val logVal = (alpha - 1.0) * ln(x) + (beta - 1.0) * ln(1.0 - x)
            logVals[i] = logVal
            if (logVal > maxLogVal) maxLogVal = logVal
        }

        for (i in 0..steps) {
            val xPx = (i.toFloat() / steps) * width
            val normVal = if (i == 0 || i == steps) 0.0 else exp(logVals[i] - maxLogVal)
            val yPx = height - (normVal.toFloat() * (height - 12.dp.toPx())) - 6.dp.toPx()
            points.add(Offset(xPx, yPx))
        }

        // Draw filled area
        val fillPath = Path().apply {
            moveTo(points.first().x, height)
            for (p in points) lineTo(p.x, p.y)
            lineTo(points.last().x, height)
            close()
        }
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                listOf(fillColor, fillColor.copy(alpha = 0.02f))
            )
        )

        // Draw stroke line
        val strokePath = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
        }
        drawPath(
            path = strokePath,
            color = lineColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

@Composable
fun LogNormalDistributionCanvas(
    mu: Double,
    sigma: Double,
    lineColor: Color,
    fillColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        val steps = 80
        val maxMinutes = 60.0
        val points = mutableListOf<Offset>()

        var maxVal = 0.0
        val vals = DoubleArray(steps)

        for (i in 1 until steps) {
            val x = (i.toDouble() / steps) * maxMinutes
            val logDiff = ln(x) - mu
            val v = (1.0 / (x * sigma)) * exp(-(logDiff.pow(2.0)) / (2.0 * sigma * sigma))
            vals[i] = v
            if (v > maxVal) maxVal = v
        }

        for (i in 0..steps) {
            val xPx = (i.toFloat() / steps) * width
            val normVal = if (i == 0 || maxVal <= 0.0) 0.0 else vals[i] / maxVal
            val yPx = height - (normVal.toFloat() * (height - 12.dp.toPx())) - 6.dp.toPx()
            points.add(Offset(xPx, yPx))
        }

        val fillPath = Path().apply {
            moveTo(points.first().x, height)
            for (p in points) lineTo(p.x, p.y)
            lineTo(points.last().x, height)
            close()
        }
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                listOf(fillColor, fillColor.copy(alpha = 0.02f))
            )
        )

        val strokePath = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
        }
        drawPath(
            path = strokePath,
            color = lineColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}
