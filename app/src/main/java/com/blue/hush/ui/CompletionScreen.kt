@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.blue.hush.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.blue.hush.session.SessionScores
import com.blue.hush.session.SessionState
import com.blue.hush.replay.ReplayCursor
import com.blue.hush.ui.theme.*
import kotlin.math.roundToInt

@Composable
internal fun CompletionScreen(
    state: SessionState, motion: GalaxyMotion, showResults: Boolean,
    onShowResults: () -> Unit, onDismissResults: () -> Unit, onBack: () -> Unit,
    detailAvailable: Boolean, onDetails: () -> Unit,
) {
    val samples = state.trendSamples
    val cursor = remember(samples) { ReplayCursor(samples) }
    var replayProgress by rememberSaveable(state.sessionId) { mutableFloatStateOf(1f) }
    var replayStarted by rememberSaveable(state.sessionId) { mutableStateOf(false) }
    val sample = cursor.sampleAt(replayProgress)
    val retainedSample = remember(samples, sample) {
        samples.lastOrNull { it.elapsedSeconds <= (sample?.elapsedSeconds ?: 0) && galaxyAgitation(it) != null }
    }
    Page("Finished", onBack) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val visualHeight = (maxHeight * 0.34f).coerceIn(120.dp, 440.dp)
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = HushSpace.lg),
                verticalArrangement = Arrangement.spacedBy(HushSpace.lg)) {
                item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(Modifier.widthIn(max = HushSpace.contentWidth).fillMaxWidth().padding(horizontal = HushSpace.lg)) {
                        if (replayStarted) ParticlePanel(sample, sample?.valid != true,
                            maxHeight = visualHeight, animate = true, retainedSample = retainedSample)
                        else ParticlePanel(state.latestSample, state.latestSample?.valid != true, motion, maxHeight = visualHeight)
                    }
                } }
                item { HushPanel(Modifier.fillMaxWidth().padding(horizontal = HushSpace.xs),
                    contentPadding = PaddingValues(horizontal = HushSpace.sm, vertical = HushSpace.xs)) {
                    key(state.sessionId) {
                        ReplayChart(samples, state.elapsedSeconds, sample, setOf(ReplayMetric.CALMNESS),
                            onMetricChanged = { _, _ -> },
                            onReplaySecondSelected = { second ->
                                replayStarted = true
                                replayProgress = cursor.progressAtSecond(second)
                            },
                            autoPlay = true, showMetricControls = false, scaleLabel = "Calmness")
                    }
                } }
                item { HushPanel(Modifier.fillMaxWidth().padding(horizontal = HushSpace.lg)) {
                    DataCoverageSummary(samples, state.elapsedSeconds)
                } }
                item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    PrimaryAction("Results", onShowResults,
                        Modifier.widthIn(max = HushSpace.contentWidth).padding(horizontal = HushSpace.lg))
                } }
            }
        }
    }
    if (showResults) {
        ModalBottomSheet(
            onDismissRequest = onDismissResults,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = HushColors.Surface,
        ) {
            Column(Modifier.testTag("completionResults").widthIn(max = HushSpace.contentWidth).fillMaxWidth()
                .align(Alignment.CenterHorizontally).verticalScroll(rememberScrollState())
                .padding(horizontal = HushSpace.lg).padding(bottom = HushSpace.lg),
                verticalArrangement = Arrangement.spacedBy(HushSpace.lg)) {
                SessionScoreSummary(state.elapsedSeconds, state.scores)
                DataCoverageSummary(samples, state.elapsedSeconds)
                TextButton(onClick = onDetails, enabled = detailAvailable, modifier = Modifier.fillMaxWidth()) {
                    Text("More Details")
                }
            }
        }
    }
}

@Composable
internal fun SessionScoreSummary(seconds: Int, scores: SessionScores, compact: Boolean = false) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(HushSpace.lg)) {
        if (!compact) Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Total Time", style = MaterialTheme.typography.labelLarge, color = HushColors.Muted)
            Text(formatDuration(seconds), style = MaterialTheme.typography.displayLarge)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HushSpace.sm)) {
            listOf("Calm" to scores.calm, "Stability" to scores.stability, "Heart Rate" to scores.heartRateBpm).forEachIndexed { index, (label, value) ->
                if (compact && index > 0) VerticalDivider(Modifier.height(56.dp), color = HushColors.Border.copy(alpha = 0.6f))
                val isHeartRate = label == "Heart Rate"
                val rounded = value?.roundToInt()
                val description = if (isHeartRate) "Heart Rate: ${rounded?.let { "$it BPM" } ?: "unavailable"}"
                    else "$label score: ${rounded?.let { "$it out of 100" } ?: "unavailable"}"
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(HushSpace.xs)) {
                    Text(label, style = MaterialTheme.typography.labelMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Surface(Modifier.fillMaxWidth(), shape = HushShapes.Control, color = if (compact) Color.Transparent else HushColors.SurfaceRaised) {
                        Box(Modifier.heightIn(min = if (compact) 36.dp else 56.dp).padding(if (compact) 0.dp else HushSpace.sm)
                            .semantics { contentDescription = description },
                            contentAlignment = Alignment.Center) {
                            if (isHeartRate) {
                                Row(horizontalArrangement = Arrangement.spacedBy(HushSpace.xs), verticalAlignment = Alignment.CenterVertically) {
                                    Text(rounded?.toString() ?: "—", style = MaterialTheme.typography.headlineMedium)
                                    Text("BPM", style = MaterialTheme.typography.labelSmall, color = HushColors.Muted)
                                }
                            } else {
                                Text(rounded?.toString() ?: "—", style = MaterialTheme.typography.headlineMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
