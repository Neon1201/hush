@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.blue.hush.ui

import androidx.activity.compose.BackHandler
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.blue.hush.muse.MuseDeviceManager
import com.blue.hush.processing.SessionScoreCalculator
import com.blue.hush.replay.ReplayCursor
import com.blue.hush.session.*
import com.blue.hush.ui.theme.*
import com.choosemuse.libmuse.ConnectionState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

enum class AppTab(val title: String) { MEDITATE("Home"), HISTORY("History") }
data class ConnectionUiState(
    val hasBluetoothPermission: Boolean = false,
    val bluetoothEnabled: Boolean = true,
    val isScanning: Boolean = false,
    val devices: List<MuseDeviceManager.MuseDevice> = emptyList(),
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val connectedDeviceAddress: String? = null,
    val errorMessage: String? = null,
    val simulationMode: Boolean = false,
    val simulationDataAvailable: Boolean = false,
    val automaticConnectionPaused: Boolean = false,
) {
    val ready get() = if (simulationMode) simulationDataAvailable else connectionState == ConnectionState.CONNECTED
    val status get() = when {
        simulationMode -> if (simulationDataAvailable) "Simulation · 10 min" else "Simulation unavailable"
        !hasBluetoothPermission -> "Bluetooth permission needed"
        !bluetoothEnabled -> "Turn on Bluetooth"
        connectionState == ConnectionState.CONNECTED -> "Muse connected"
        connectionState == ConnectionState.CONNECTING -> "Connecting…"
        automaticConnectionPaused -> "Connection paused"
        isScanning -> "Searching for Muse…"
        else -> "Connect your Muse"
    }
}

@Composable
internal fun Page(title: String, onBack: (() -> Unit)? = null, content: @Composable () -> Unit) {
    if (onBack != null) BackHandler(onBack = onBack)
    Scaffold(topBar = { TopAppBar(title = { Text(title, style = MaterialTheme.typography.headlineMedium) },
        navigationIcon = { if (onBack != null) IconButton(onClick = onBack,
            modifier = Modifier.semantics { contentDescription = "Back" }) {
            Canvas(Modifier.size(24.dp)) {
                val stroke = 2.dp.toPx()
                drawLine(HushColors.Text, Offset(size.width * 0.85f, center.y), Offset(size.width * 0.15f, center.y), stroke, StrokeCap.Round)
                drawLine(HushColors.Text, Offset(size.width * 0.45f, size.height * 0.2f), Offset(size.width * 0.15f, center.y), stroke, StrokeCap.Round)
                drawLine(HushColors.Text, Offset(size.width * 0.45f, size.height * 0.8f), Offset(size.width * 0.15f, center.y), stroke, StrokeCap.Round)
            }
        } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = HushColors.Background)) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) { content() }
    }
}

@Composable
fun HushApp(
    sessionState: SessionState, history: List<SessionSummary>, activeTab: AppTab,
    selectedDurationSeconds: Int, selectedTrack: MusicTrack, detailSummary: SessionSummary?,
    detailSamples: List<StateSample>, replayProgress: Float, connectionState: ConnectionUiState,
    previewTrack: MusicTrack?, onTabSelected: (AppTab) -> Unit, onDurationSelected: (Int) -> Unit,
    onTrackSelected: (MusicTrack) -> Unit, onStartScanning: () -> Unit,
    onConnect: (MuseDeviceManager.MuseDevice) -> Unit, onDisconnect: () -> Unit,
    onStartSession: () -> Unit, onSimulationModeChanged: (Boolean) -> Unit,
    onPause: () -> Unit, onResume: () -> Unit, onFinish: () -> Unit, onStartNewSession: () -> Unit,
    onVolumeChanged: (Float) -> Unit, onOpenDetail: (SessionSummary) -> Unit,
    onCloseDetail: () -> Unit, onReplayProgressChanged: (Float) -> Unit,
    onPreviewTrack: (MusicTrack) -> Unit, onStopPreview: () -> Unit,
    onDeleteSession: (SessionSummary) -> Unit,
) {
    var deviceSheet by rememberSaveable { mutableStateOf(false) }
    var musicSheet by rememberSaveable { mutableStateOf(false) }
    // Kept above route returns so details and configuration changes preserve dismissal.
    var resultsSheet by rememberSaveable(sessionState.sessionId) { mutableStateOf(true) }
    val galaxyMotion = rememberGalaxyMotion()
    if (sessionState.phase in listOf(SessionPhase.RUNNING, SessionPhase.PAUSED)) {
        MeditationGalaxyScreen(sessionState, onPause, onResume, onFinish, onVolumeChanged, galaxyMotion, onTrackSelected)
        return
    }
    if (detailSummary != null) {
        SessionDetailScreen(detailSummary, detailSamples, replayProgress, onCloseDetail, onReplayProgressChanged)
        return
    }
    if (sessionState.phase == SessionPhase.FINISHED) {
        val summary = history.firstOrNull { it.id == sessionState.sessionId }
        CompletionScreen(sessionState, galaxyMotion, resultsSheet,
            onShowResults = { resultsSheet = true }, onDismissResults = { resultsSheet = false },
            onBack = onStartNewSession, detailAvailable = summary != null,
            onDetails = { summary?.let { resultsSheet = false; onOpenDetail(it) } })
        return
    }
    Scaffold(containerColor = HushColors.Background, bottomBar = {
        NavigationBar(containerColor = HushColors.Background) {
            AppTab.entries.forEach { tab -> NavigationBarItem(selected = activeTab == tab, onClick = { onTabSelected(tab) },
                icon = { HushNavIcon(tab) }, label = { Text(tab.title) }) }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            if (activeTab == AppTab.MEDITATE) {
                LazyColumn(Modifier.widthIn(max = HushSpace.contentWidth).fillMaxSize(), contentPadding = PaddingValues(horizontal = HushSpace.lg, vertical = HushSpace.sm), verticalArrangement = Arrangement.spacedBy(HushSpace.sm)) {
                    item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Hush", style = MaterialTheme.typography.headlineLarge)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ChartHelpButton()
                            MusicButton(selectedTrack.title, onClick = { musicSheet = true })
                        }
                    } }
                    item { GalaxyParticleField(null, dataGap = false, paused = false,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp).aspectRatio(1f), state = galaxyMotion, preview = true) }
                    item { Text("A moment of stillness", style = MaterialTheme.typography.titleMedium) }
                    item { SessionPreparationPanel(selectedDurationSeconds, connectionState,
                        onDurationSelected, onDeviceSelected = { deviceSheet = true }, onStart = {
                            if (connectionState.ready) onStartSession() else { deviceSheet = true; onStartScanning() }
                        }) }
                }
            } else HistoryScreen(history, onOpenDetail, onDeleteSession)
        }
    }
    if (deviceSheet) ModalBottomSheet(onDismissRequest = { deviceSheet = false }, containerColor = HushColors.Surface) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(HushSpace.xl), verticalArrangement = Arrangement.spacedBy(HushSpace.lg)) {
            item { Text("Your Muse", style = MaterialTheme.typography.headlineMedium); Text(connectionState.status, color = HushColors.Muted) }
            connectionState.errorMessage?.let { message -> item { Text(message, color = HushColors.Error) } }
            if (!connectionState.simulationMode) {
                if (!connectionState.ready) item { PrimaryAction(if (connectionState.hasBluetoothPermission) "Connect Muse" else "Allow Bluetooth", onStartScanning) }
                items(connectionState.devices, key = { it.macAddress }) { device ->
                    OutlinedButton(onClick = { onConnect(device) }, enabled = connectionState.connectionState != ConnectionState.CONNECTING && connectionState.connectedDeviceAddress != device.macAddress, modifier = Modifier.fillMaxWidth()) {
                        Column { Text(device.name.ifBlank { "Muse 2" }); Text(device.macAddress.takeLast(5), style = MaterialTheme.typography.bodySmall) }
                    }
                }
                if (connectionState.ready) item { TextButton(onClick = onDisconnect) { Text("Disconnect") } }
            }
            item { HorizontalDivider(color = HushColors.Border) }
            item { Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Try a simulation"); Text("Saved session · 10 min", style = MaterialTheme.typography.bodySmall, color = HushColors.Muted) }
                Switch(modifier = Modifier.semantics { contentDescription = "Use saved simulation data" }, checked = connectionState.simulationMode, onCheckedChange = onSimulationModeChanged, enabled = connectionState.simulationDataAvailable)
            } }
        }
    }
    if (musicSheet) {
        DisposableEffect(Unit) { onDispose { onStopPreview() } }
        SoundscapeSheet(selectedTrack, onTrackSelected, onDismiss = { musicSheet = false }, previewTrack = previewTrack, onPreviewTrack = onPreviewTrack)
    }
}

@Composable
internal fun SoundscapeSheet(
    selectedTrack: MusicTrack,
    onTrackSelected: (MusicTrack) -> Unit,
    onDismiss: () -> Unit,
    previewTrack: MusicTrack? = null,
    onPreviewTrack: ((MusicTrack) -> Unit)? = null,
    volume: Float? = null,
    onVolumeChanged: (Float) -> Unit = {},
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = HushColors.Surface) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(HushSpace.xl), verticalArrangement = Arrangement.spacedBy(HushSpace.lg)) {
            item { Text("Soundscapes", style = MaterialTheme.typography.headlineMedium) }
            if (volume != null) item {
                Slider(value = volume, onValueChange = onVolumeChanged,
                    modifier = Modifier.semantics { contentDescription = "Meditation volume" })
            }
            items(MusicTrack.soundscapes, key = { it.name }) { track -> HushPanel(Modifier.fillMaxWidth()) {
                Text(track.title, style = MaterialTheme.typography.titleLarge)
                Text(track.subtitle, color = HushColors.Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(HushSpace.sm)) {
                    FilterChip(selected = selectedTrack == track, onClick = { onTrackSelected(track) }, label = { Text(if (selectedTrack == track) "Selected" else "Select") })
                    if (onPreviewTrack != null) TextButton(onClick = { onPreviewTrack(track) }) { Text(if (previewTrack == track) "Stop preview" else "Preview") }
                }
            } }
        }
    }
}

@Composable
internal fun HistoryScreen(history: List<SessionSummary>, onOpen: (SessionSummary) -> Unit, onDelete: (SessionSummary) -> Unit) {
    var pendingDeleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    val pendingDelete = history.firstOrNull { it.id == pendingDeleteId }
    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Delete session?") },
            text = { Text("This session and its replay data will be permanently deleted.") },
            confirmButton = { TextButton(onClick = {
                pendingDeleteId = null
                onDelete(pendingDelete)
            }) { Text("Delete", color = HushColors.Error) } },
            dismissButton = { TextButton(onClick = { pendingDeleteId = null }) { Text("Cancel") } },
            containerColor = HushColors.Surface,
        )
    }
    LazyColumn(Modifier.widthIn(max = HushSpace.contentWidth).fillMaxSize(), contentPadding = PaddingValues(HushSpace.xl), verticalArrangement = Arrangement.spacedBy(HushSpace.lg)) {
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text("History", style = MaterialTheme.typography.headlineLarge)
            ChartHelpButton()
        } }
        if (history.isEmpty()) item { HushPanel(Modifier.fillMaxWidth()) { Text("Your quiet moments, collected."); Text("Complete a session to see it here.", color = HushColors.Muted) } }
        items(history, key = { it.id }) { summary ->
            val dismissState = rememberSwipeToDismissBoxState(positionalThreshold = { it * 0.4f })
            LaunchedEffect(dismissState.settledValue) {
                if (dismissState.settledValue == SwipeToDismissBoxValue.EndToStart) {
                    pendingDeleteId = summary.id
                    // Restore the card while confirmation is shown, including cancellation or failure.
                    dismissState.reset()
                }
            }
            SwipeToDismissBox(
                state = dismissState,
                modifier = Modifier.animateItem().clip(HushShapes.Panel),
                enableDismissFromStartToEnd = false,
                gesturesEnabled = pendingDelete == null,
                backgroundContent = {
                    Surface(Modifier.fillMaxSize(), color = HushColors.Error.copy(alpha = 0.15f), shape = HushShapes.Panel) {
                        Box(Modifier.fillMaxSize().padding(HushSpace.lg), contentAlignment = Alignment.CenterEnd) {
                            Text("Delete", color = HushColors.Error, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                },
            ) {
                Surface(onClick = { onOpen(summary) }, shape = HushShapes.Panel, color = HushColors.Surface, border = BorderStroke(1.dp, HushColors.Border)) {
                    Row(Modifier.fillMaxWidth().semantics {
                        customActions = listOf(CustomAccessibilityAction("Delete session") {
                            pendingDeleteId = summary.id
                            true
                        })
                    }.padding(HushSpace.lg), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HushSpace.sm)) {
                        MindprintThumbnail(summary.id, Modifier.size(56.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HushSpace.xs)) {
                            Text("${formatDate(summary.startedAt)} · ${formatTime(summary.startedAt)}", style = MaterialTheme.typography.titleMedium)
                            Text("${formatDuration(summary.actualSeconds)} · ${summary.track.title}", color = HushColors.Muted, style = MaterialTheme.typography.bodySmall)
                            Text(if (summary.resultSampleCount >= 2) summary.result.title else "Not enough signal", style = MaterialTheme.typography.labelSmall)
                        }
                        Column(Modifier.widthIn(min = 40.dp).semantics {
                            contentDescription = "Calm score: ${summary.calm?.roundToInt()?.let { "$it out of 100" } ?: "unavailable"}"
                        }, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(HushSpace.xs)) {
                            Text("Calm", style = MaterialTheme.typography.labelSmall, color = HushColors.Muted)
                            Text(summary.calm?.roundToInt()?.toString() ?: "—", style = MaterialTheme.typography.headlineSmall, color = HushColors.Lavender)
                        }
                    }
                }
            }
        }
    }
}
@Composable
internal fun SessionDetailScreen(summary: SessionSummary, samples: List<StateSample>, progress: Float, onBack: () -> Unit, onProgress: (Float) -> Unit) {
    var visibleMask by rememberSaveable(summary.id) { mutableStateOf((1 shl ReplayMetric.entries.size) - 1) }
    val visibleMetrics = ReplayMetric.entries.filter { visibleMask and (1 shl it.ordinal) != 0 }.toSet()
    val cursor = remember(samples) { ReplayCursor(samples) }
    val scores = remember(samples) { SessionScoreCalculator.calculate(samples) }
    val sample = cursor.sampleAt(progress)
    val retainedSample = remember(samples, sample) {
        samples.lastOrNull { it.elapsedSeconds <= (sample?.elapsedSeconds ?: 0) && galaxyAgitation(it) != null }
    }
    Page("Session details", onBack) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = HushSpace.xs, bottom = HushSpace.xl), verticalArrangement = Arrangement.spacedBy(HushSpace.xl)) {
            item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(Modifier.widthIn(max = HushSpace.contentWidth).fillMaxWidth().padding(horizontal = HushSpace.xl), verticalArrangement = Arrangement.spacedBy(HushSpace.sm)) {
                    Text("${formatDate(summary.startedAt)} · ${formatTime(summary.startedAt)} · ${formatDuration(summary.actualSeconds)}",
                        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall, color = HushColors.Muted)
                    HushPanel(Modifier.fillMaxWidth()) {
                        SessionScoreSummary(summary.actualSeconds, scores, compact = true)
                    }
                }
            } }
            item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.widthIn(max = HushSpace.contentWidth).fillMaxWidth().padding(horizontal = HushSpace.xl)) {
                    ParticlePanel(sample, sample?.valid != true, animate = true, retainedSample = retainedSample)
                }
            } }
            item { HushPanel(Modifier.fillMaxWidth().padding(horizontal = HushSpace.xs),
                contentPadding = PaddingValues(horizontal = HushSpace.sm, vertical = HushSpace.xs)) {
                key(summary.id) {
                    ReplayChart(samples, summary.actualSeconds, sample, visibleMetrics,
                        onMetricChanged = { metric, checked ->
                            val bit = 1 shl metric.ordinal
                            visibleMask = if (checked) visibleMask or bit else visibleMask and bit.inv()
                        },
                        onReplaySecondSelected = { second -> onProgress(cursor.progressAtSecond(second)) },
                        autoPlay = true)
                }
            } }
            item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                HushPanel(Modifier.widthIn(max = HushSpace.contentWidth).fillMaxWidth()
                    .padding(horizontal = HushSpace.xl)) {
                    DataCoverageSummary(samples, summary.actualSeconds)
                }
            } }
        }
    }
}
internal fun formatDuration(seconds: Int): String = String.format(Locale.US, "%02d:%02d", seconds.coerceAtLeast(0) / 60, seconds.coerceAtLeast(0) % 60)
private fun formatDate(timestamp: Long): String = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH).format(Date(timestamp))
private fun formatTime(timestamp: Long): String = SimpleDateFormat("HH:mm", Locale.ENGLISH).format(Date(timestamp))
