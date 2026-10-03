package com.blue.hush.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.blue.hush.session.StateSample
import com.blue.hush.ui.theme.HushColors
import java.util.Locale

internal fun StateSample.chartCalmness(): Double? = calmness?.takeIf { valid && it.isFinite() && it in 0.0..1.0 }

/** Borderless live trend; playback and seeking belong to ReplayChart. */
@Composable
internal fun CalmnessChart(samples: List<StateSample>, elapsedSeconds: Int, plotHeight: Dp = 88.dp) {
    val points = remember(samples) { smoothedCurvePoints(samples) { it.chartCalmness() } }
    val latest = points.lastOrNull()
    val current = latest?.takeIf { it.first == elapsedSeconds }
    val description = "Calmness trend, current ${current?.let { String.format(Locale.US, "%.0f", it.second * 100) } ?: "unavailable"} out of 100"
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Calmness", style = MaterialTheme.typography.labelMedium, color = HushColors.Muted)
            Text(current?.let { String.format(Locale.US, "%.0f", it.second * 100) } ?: "—",
                style = MaterialTheme.typography.headlineSmall, color = HushColors.Lavender)

        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(28.dp).height(plotHeight), verticalArrangement = Arrangement.SpaceBetween) {
            Text("100", style = MaterialTheme.typography.labelSmall, color = HushColors.Muted)
            Text("50", style = MaterialTheme.typography.labelSmall, color = HushColors.Muted)
            Text("0", style = MaterialTheme.typography.labelSmall, color = HushColors.Muted)
        }
        Canvas(Modifier.weight(1f).height(plotHeight).semantics { contentDescription = description }) {
            val inset = 5.dp.toPx()
            val plotWidth = (size.width - 2 * inset).coerceAtLeast(0f)
            val plotHeightPx = (size.height - 2 * inset).coerceAtLeast(0f)
            fun point(second: Int, level: Double) = Offset(
                inset + second.toFloat() / elapsedSeconds.coerceAtLeast(1) * plotWidth,
                inset + (1 - level.toFloat()) * plotHeightPx)
            drawLine(HushColors.Muted.copy(alpha = 0.35f), point(0, 0.5), point(elapsedSeconds, 0.5),
                1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
            drawSampleCurve(samples, elapsedSeconds, HushColors.Lavender,
                levelAt = { it.chartCalmness() }, pointAt = ::point)
            current?.let { drawCircle(HushColors.Lavender, 3.dp.toPx(), point(it.first, it.second)) }
        }
        }
    }
}
