package com.blue.hush.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import java.util.Locale
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.blue.hush.session.StateSample
import com.blue.hush.ui.theme.HushColors
import kotlin.math.roundToInt

internal data class MetricCoverage(val measuredSeconds: Int, val totalSeconds: Int) {
    val fraction: Float get() = if (totalSeconds <= 0) 0f else measuredSeconds.toFloat() / totalSeconds
    val status: CoverageStatus get() = when {
        totalSeconds <= 0 || measuredSeconds * 100L < totalSeconds * 50L -> CoverageStatus.LOW
        measuredSeconds * 100L < totalSeconds * 70L -> CoverageStatus.LIMITED
        else -> CoverageStatus.AVAILABLE
    }
    val percent: Int get() = if (totalSeconds <= 0) 0 else
        (measuredSeconds * 100.0 / totalSeconds).roundToInt()
}

internal enum class CoverageStatus { LOW, LIMITED, AVAILABLE }

internal fun metricCoverage(samples: List<StateSample>, seconds: Int, metric: ReplayMetric): MetricCoverage {
    val total = seconds.coerceAtLeast(0)
    val measured = samples.asSequence().filter {
        it.elapsedSeconds in 1..total && it.algorithmVersion > 0 && metric.value(it) != null
    }.map { it.elapsedSeconds }.distinct().count()
    return MetricCoverage(measured, total)
}

@Composable
internal fun DataCoverageSummary(samples: List<StateSample>, seconds: Int) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Data coverage", style = MaterialTheme.typography.titleMedium, color = HushColors.Text)
        Text("How much of your session had usable measurements.",
            style = MaterialTheme.typography.bodySmall, color = HushColors.Muted)
        listOf(ReplayMetric.CALMNESS, ReplayMetric.STABILITY, ReplayMetric.HEART_RATE).forEach { metric ->
            val coverage = remember(samples, seconds, metric) { metricCoverage(samples, seconds, metric) }
            val color = when (coverage.status) {
                CoverageStatus.LOW -> HushColors.Error
                CoverageStatus.LIMITED -> HushColors.Warm
                CoverageStatus.AVAILABLE -> HushColors.Accent
            }
            val status = when (coverage.status) {
                CoverageStatus.LOW -> "Low coverage · insufficient data"
                CoverageStatus.LIMITED -> "Limited coverage · data may be insufficient"
                CoverageStatus.AVAILABLE -> "Good coverage"
            }
            Surface(color = HushColors.SurfaceRaised, shape = MaterialTheme.shapes.medium) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(metric.title, modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall, color = HushColors.Text)
                        Text(String.format(Locale.US, "%.1f%%", coverage.fraction * 100),
                            style = MaterialTheme.typography.headlineSmall, color = color)
                    }
                    LinearProgressIndicator(progress = { coverage.fraction }, modifier = Modifier.fillMaxWidth(),
                        color = color, trackColor = HushColors.Border)
                    Text("${formatDuration(coverage.measuredSeconds)} measured / ${formatDuration(coverage.totalSeconds)} total",
                        style = MaterialTheme.typography.bodySmall, color = HushColors.Muted)
                    Text(status, style = MaterialTheme.typography.bodySmall, color = color)
                }
            }
        }
        Text("Below 70%: data may be insufficient. Below 50%: low coverage. Missing seconds are excluded from averages.",
            style = MaterialTheme.typography.bodySmall, color = HushColors.Muted)
    }
}

@Composable
internal fun ChartHelpButton() {
    var open by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = Modifier.size(48.dp)
        .semantics { contentDescription = "Help with curves and data coverage" }) {
        Text("?", style = MaterialTheme.typography.titleMedium, color = HushColors.Accent)
    }
    if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text("Understanding your data") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(
                    "Calmness" to "An experimental EEG-only trend based on relative Alpha, Theta and Beta, compared with a fixed resting reference. Higher values indicate a calmer pattern under this model. 50 is the reference level, not proof of a calm mental state.",
                    "Stability" to "Head stillness estimated from acceleration. Higher values mean less head movement; this does not measure whole-body stillness or meditation quality.",
                    "Alpha, Theta and Beta" to "Relative EEG band levels, displayed as proportions multiplied by 100. They are not separate meditation scores. Unavailable or rejected EEG stays missing.",
                    "Heart Rate" to "BPM estimated from optical PPG using an approximately eight-second signal window. Poor or interrupted signals can leave gaps. On the combined 0–100 axis, 40 BPM maps to 0 and 180 BPM maps to 100; the legend shows actual BPM.",
                    "Solid lines and dots" to "Curves use a three-second average: previous, current and next measured second within a continuous run. Cursor dots and legend values use the same average. At a run's edge, only available adjacent seconds are averaged. The selected dot sits on its curve.",
                    "Dashed lines and —" to "A faint dashed line in each metric's color connects the last measured point before a gap to the first one after it. It is a decorative curve, not a prediction or measurement. A trailing gap uses a horizontal guide. Missing seconds show — and no metric dot; guides never enter calculations.",
                    "Data coverage" to "The percentage of elapsed session seconds with a valid recorded value, counted independently for each metric before chart smoothing. For example, 30 measured seconds in a 60-second session is 50%. Coverage describes availability, not measurement accuracy. Summary averages require at least 30 measured seconds per metric and exclude gaps.",
                    "Replay and galaxy" to "Tap the plot or drag the cursor handle to select time. Play advances at 30×; pinch to zoom and drag horizontally to pan. The large center dot is a drag handle, not a measurement. The galaxy responds to recorded Calmness, retaining its appearance during gaps. Simulation replays saved measurements; it does not collect new sensor data.",
                    "Interpretation" to "These metrics are experimental feedback, not a validated meditation-quality or medical assessment. Heart rate does not contribute to Calmness or Stability."
                ).forEach { (title, body) ->
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(body, style = MaterialTheme.typography.bodySmall)
                }
            }
        }, confirmButton = { TextButton(onClick = { open = false }) { Text("Got it") } })
}
