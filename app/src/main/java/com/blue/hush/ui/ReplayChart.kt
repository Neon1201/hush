package com.blue.hush.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import com.blue.hush.session.StateSample
import com.blue.hush.ui.theme.HushColors
import com.blue.hush.ui.theme.HushSpace
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private val ReplayMetric.color: Color
    get() = when (this) {
        ReplayMetric.CALMNESS -> HushColors.Lavender
        ReplayMetric.ALPHA -> HushColors.Success
        ReplayMetric.THETA -> HushColors.Warm
        ReplayMetric.BETA -> HushColors.Star
        ReplayMetric.HEART_RATE -> HushColors.Error
        ReplayMetric.STABILITY -> HushColors.Accent
    }

@Composable
internal fun ReplayChart(
    samples: List<StateSample>, elapsedSeconds: Int, selectedSample: StateSample?,
    visibleMetrics: Set<ReplayMetric>, onMetricChanged: (ReplayMetric, Boolean) -> Unit,
    onReplaySecondSelected: (Float) -> Unit,
    autoPlay: Boolean = false,
    showMetricControls: Boolean = true,
    scaleLabel: String = "Relative level",
) {
    val onSeek by rememberUpdatedState(onReplaySecondSelected)
    val currentSelectedSample by rememberUpdatedState(selectedSample)
    var viewStart by rememberSaveable { mutableFloatStateOf(0f) }
    var viewSpan by rememberSaveable { mutableFloatStateOf(1f) }
    val viewport = ReplayViewport(viewStart, viewSpan)
    fun currentViewport() = ReplayViewport(viewStart, viewSpan)
    val updateViewport by rememberUpdatedState<(ReplayViewport) -> Unit> { next ->
        viewStart = next.start
        viewSpan = next.span
    }
    val metrics = ReplayMetric.entries.filter { it in visibleMetrics }
    val smoothedValues = remember(samples) {
        ReplayMetric.entries.associateWith { metric ->
            smoothedCurvePoints(samples, metric::value).toMap()
        }
    }
    val displayedSample = selectedSample?.let { selected ->
        fun value(metric: ReplayMetric) = smoothedValues.getValue(metric)[selected.elapsedSeconds]
        selected.copy(
            calmness = value(ReplayMetric.CALMNESS), stillness = value(ReplayMetric.STABILITY),
            alpha = value(ReplayMetric.ALPHA), theta = value(ReplayMetric.THETA),
            beta = value(ReplayMetric.BETA), heartRateBpm = value(ReplayMetric.HEART_RATE),
        )
    }

    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall
    var playing by remember { mutableStateOf(false) }
    var playbackStart by remember { mutableFloatStateOf(0f) }
    val playbackEnd = minOf(elapsedSeconds, samples.lastOrNull()?.elapsedSeconds ?: 0)
    val canPlay = samples.size > 1 && playbackEnd > (samples.firstOrNull()?.elapsedSeconds ?: 0)
    var autoStarted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(autoPlay, canPlay) {
        if (autoPlay && canPlay && !autoStarted) {
            autoStarted = true
            playbackStart = (currentSelectedSample?.elapsedSeconds ?: samples.first().elapsedSeconds).toFloat()
            if (playbackStart >= playbackEnd) playbackStart = samples.first().elapsedSeconds.toFloat()
            onSeek(playbackStart)
            playing = true
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) playing = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(playing, samples, playbackEnd) {
        if (!playing || !canPlay) return@LaunchedEffect
        val startedAt = withFrameNanos { it }
        while (playing) {
            val now = withFrameNanos { it }
            // A manual seek can pause between frame request and coroutine cancellation.
            if (!playing) break
            val second = (playbackStart + (now - startedAt) / 1_000_000_000f * 30f).coerceAtMost(playbackEnd.toFloat())
            updateViewport(currentViewport().reveal(second, elapsedSeconds))
            onSeek(second)
            if (second >= playbackEnd) playing = false
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HushSpace.xs),
            verticalAlignment = Alignment.CenterVertically) {
            Text("Replay", style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text(scaleLabel, modifier = Modifier.weight(1f), style = labelStyle,
                color = HushColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(formatDuration(selectedSample?.elapsedSeconds ?: 0), style = labelStyle, color = HushColors.Accent)
            IconButton(onClick = {
                if (playing) playing = false else {
                    playbackStart = (currentSelectedSample?.elapsedSeconds ?: 0).toFloat()
                    if (playbackStart >= playbackEnd) playbackStart = samples.first().elapsedSeconds.toFloat()
                    onSeek(playbackStart)
                    playing = true
                }
            }, enabled = canPlay, modifier = Modifier.size(48.dp).semantics {
                contentDescription = if (playing) "Pause replay" else "Play replay"
            }) {
                Canvas(Modifier.size(16.dp)) {
                    val color = if (canPlay) HushColors.Accent else HushColors.Muted
                    if (playing) {
                        drawRect(color, size = androidx.compose.ui.geometry.Size(size.width * 0.3f, size.height))
                        drawRect(color, topLeft = Offset(size.width * 0.7f, 0f),
                            size = androidx.compose.ui.geometry.Size(size.width * 0.3f, size.height))
                    } else {
                        drawPath(Path().apply {
                            moveTo(0f, 0f); lineTo(size.width, size.height / 2); lineTo(0f, size.height); close()
                        }, color)
                    }
                }
            }
            Text("30×", style = labelStyle, color = HushColors.Muted)
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val inset = with(density) { 8.dp.toPx() }
            val upperTick = measurer.measure("100", labelStyle)
            val lowerTick = measurer.measure("0", labelStyle)
            val plotWidth = with(density) { maxWidth.toPx() }
            val gap = with(density) { 4.dp.toPx() }
            val heightPx = with(density) { (304.dp * density.fontScale.coerceAtLeast(1f)).toPx() }
            val topTickSpace = upperTick.size.height + gap
            val plotHeight = with(density) { (heightPx + topTickSpace).toDp() }
            Box {
                val description = metrics.joinToString(", ") { it.label(displayedSample) }
                Canvas(Modifier.fillMaxWidth().height(plotHeight).semantics {
                    contentDescription = "Session replay"
                    stateDescription = listOf(formatDuration(selectedSample?.elapsedSeconds ?: 0), description)
                        .filter { it.isNotEmpty() }.joinToString(", ")
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        (selectedSample?.elapsedSeconds ?: 0).toFloat(), 0f..elapsedSeconds.coerceAtLeast(1).toFloat(),
                    )
                    if (samples.isNotEmpty()) setProgress { value ->
                        if (!value.isFinite()) false else {
                            playing = false
                            updateViewport(currentViewport().reveal(value, elapsedSeconds))
                            onSeek(value.coerceIn(0f, elapsedSeconds.coerceAtLeast(0).toFloat()))
                            true
                        }
                    }
                    if (samples.isNotEmpty() && elapsedSeconds > 0) customActions = listOf(
                        CustomAccessibilityAction("Zoom in") {
                            updateViewport(currentViewport().transform(2f, 0.5f, 0f, elapsedSeconds)); true
                        },
                        CustomAccessibilityAction("Zoom out") {
                            updateViewport(currentViewport().transform(0.5f, 0.5f, 0f, elapsedSeconds)); true
                        },
                        CustomAccessibilityAction("Reset zoom") { updateViewport(ReplayViewport()); true },
                    )
                }.pointerInput(elapsedSeconds, samples.isNotEmpty()) {
                    if (samples.isEmpty()) return@pointerInput
                    val width = (size.width - 2 * inset).coerceAtLeast(1f)
                    fun seek(x: Float) {
                        playing = false
                        onSeek(currentViewport().secondAt((x - inset) / width, elapsedSeconds))
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val cursorFraction = currentSelectedSample?.let {
                            (it.elapsedSeconds.toFloat() / elapsedSeconds.coerceAtLeast(1) - currentViewport().start) / currentViewport().span
                        }
                        val draggingCursor = cursorFraction != null && cursorFraction in 0f..1f &&
                                abs(down.position.x - (inset + cursorFraction * width)) <= 24.dp.toPx() &&
                                abs(down.position.y - size.height / 2f) <= 24.dp.toPx()
                        var transformed = false
                        var dragging = false
                        var scrolling = false
                        var lastPosition = down.position
                        do {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.size >= 2) {
                                transformed = true
                                val anchor = event.calculateCentroid(useCurrent = false)
                                if (anchor != Offset.Unspecified) {
                                    updateViewport(currentViewport().transform(event.calculateZoom(),
                                        (anchor.x - inset) / width, event.calculatePan().x / width, elapsedSeconds))
                                }
                                event.changes.forEach { it.consume() }
                            } else if (transformed) {
                                event.changes.forEach { it.consume() }
                            } else {
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                val deltaX = change.position.x - lastPosition.x
                                lastPosition = change.position
                                val movement = change.position - down.position
                                if (!dragging && !scrolling) {
                                    if (abs(movement.y) > viewConfiguration.touchSlop && abs(movement.y) > abs(movement.x)) scrolling = true
                                    else if (abs(movement.x) > viewConfiguration.touchSlop) dragging = true
                                }
                                if (dragging) {
                                    if (draggingCursor) seek(change.position.x)
                                    else updateViewport(currentViewport().transform(1f, 0f, deltaX / width, elapsedSeconds))
                                    change.consume()
                                }
                                if (!change.pressed && !dragging && !scrolling && !change.isConsumed) seek(lastPosition.x)
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }) {
                    val width = (size.width - 2 * inset).coerceAtLeast(0f)
                    val plotTop = inset + topTickSpace
                    val height = size.height - plotTop - inset
                    fun x(second: Int) = inset + (second.toFloat() / elapsedSeconds.coerceAtLeast(1) - viewport.start) / viewport.span * width
                    fun y(level: Float) = plotTop + (1 - level) * height
                    listOf(0f, 0.5f, 1f).forEach { fraction ->
                        drawLine(HushColors.Border.copy(alpha = 0.45f), Offset(inset, y(fraction)),
                            Offset(inset + width, y(fraction)), 1.dp.toPx())
                    }
                    clipRect(left = inset, top = plotTop, right = inset + width, bottom = plotTop + height) {
                        metrics.forEach { metric ->
                            drawSampleCurve(samples, elapsedSeconds, metric.color,
                                levelAt = { metric.value(it)?.let(metric::level) }) { second, level ->
                                Offset(x(second), y(level.toFloat()))
                            }
                        }
                    }
                    drawText(upperTick, HushColors.Muted, topLeft = Offset(inset, plotTop - upperTick.size.height - gap))
                    drawText(lowerTick, HushColors.Muted, topLeft = Offset(inset, y(0f) - lowerTick.size.height - gap))
                    // Only retain the vertical line of the cursor and the dots on the curved line, and no longer draw floating numerical labels.
                    selectedSample?.takeIf { x(it.elapsedSeconds) in inset..(inset + width) }?.let { selected ->
                        val cursorX = x(selected.elapsedSeconds)
                        drawLine(HushColors.Accent, Offset(cursorX, plotTop), Offset(cursorX, plotTop + height), 2.dp.toPx())
                        metrics.forEach { metric ->
                            val value = metric.value(displayedSample) ?: return@forEach
                            val point = Offset(cursorX, y(metric.level(value).toFloat()))
                            drawCircle(metric.color, 3.dp.toPx(), point)
                        }
                        drawCircle(HushColors.Surface, 7.dp.toPx(), Offset(cursorX, size.height / 2))
                        drawCircle(HushColors.Accent, 5.dp.toPx(), Offset(cursorX, size.height / 2))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration((viewport.start * elapsedSeconds).roundToInt()), style = labelStyle, color = HushColors.Muted)
            Text(formatDuration((viewport.end * elapsedSeconds).roundToInt()), style = labelStyle, color = HushColors.Muted)
        }
        Text("Dashed lines: no measured data", style = labelStyle, color = HushColors.Muted)
        if (showMetricControls) listOf(
            listOf(ReplayMetric.CALMNESS, ReplayMetric.STABILITY, ReplayMetric.HEART_RATE),
            listOf(ReplayMetric.ALPHA, ReplayMetric.THETA, ReplayMetric.BETA),
        ).forEach { rowMetrics ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HushSpace.xs)) {
                rowMetrics.forEach { metric ->
                    Row(Modifier.weight(1f).heightIn(min = 48.dp).toggleable(
                        value = metric in visibleMetrics, role = Role.Checkbox,
                        onValueChange = { onMetricChanged(metric, it) },
                    ), verticalAlignment = Alignment.CenterVertically) {
                        Canvas(Modifier.size(16.dp)) {
                            val checked = metric in visibleMetrics
                            drawRoundRect(if (checked) metric.color else HushColors.Muted,
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
                                style = if (checked) androidx.compose.ui.graphics.drawscope.Fill else Stroke(1.dp.toPx()))
                            if (checked) {
                                drawPath(Path().apply {
                                    moveTo(size.width * 0.2f, size.height * 0.5f)
                                    lineTo(size.width * 0.42f, size.height * 0.72f)
                                    lineTo(size.width * 0.8f, size.height * 0.28f)
                                }, HushColors.OnAccent, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
                            }
                        }
                        // Title + Value: The value is displayed only when the indicator is selected, and it is placed below the title.
                        Column(Modifier.weight(1f).padding(start = HushSpace.xs)) {
                            Text(metric.title, style = MaterialTheme.typography.labelMedium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (metric in visibleMetrics) {
                                Text(
                                    text = metric.displayValue(displayedSample),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = metric.color,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}