package com.teleprompter.phone.ui

import android.graphics.Color as AndroidColor
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.withFrameNanos
import com.teleprompter.phone.storage.ReadingPositionStore
import com.teleprompter.phone.wear.ReaderCommandBridge
import com.teleprompter.shared.model.PlaybackAction
import com.teleprompter.shared.model.PlaybackStatus
import com.teleprompter.shared.model.PlaybackState
import com.teleprompter.shared.model.ContentBlock
import com.teleprompter.shared.model.InlineRun
import com.teleprompter.shared.model.PreparedScript
import com.teleprompter.shared.position.PositionAnchor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

@Composable
fun ScriptReaderScreen(
    script: PreparedScript,
    isBusy: Boolean = false,
    onSpeedChange: (Double) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val view = LocalView.current
    val positionStore = remember(view.context) { ReadingPositionStore(view.context) }
    val savedAnchor = remember(script.scriptId) { positionStore.load(script.scriptId) }
    val background = Color(AndroidColor.parseColor(script.appearance.backgroundColorArgb))
    val textColor = Color(AndroidColor.parseColor(script.appearance.textColorArgb))
    val fontSize = script.appearance.fontSizeSp.sp
    val lineHeight = (script.appearance.fontSizeSp * script.appearance.lineSpacingMultiplier).sp
    val contrastRatio = remember(script.appearance.textColorArgb, script.appearance.backgroundColorArgb) {
        readerContrastRatio(script.appearance.textColorArgb, script.appearance.backgroundColorArgb)
    }
    val minimumContrast = if (script.appearance.fontSizeSp * 0.78 >= 24.0) 3.0 else 4.5
    var readingReminderAcknowledged by remember(script.scriptId) { mutableStateOf(false) }
    var contrastWarningAcknowledged by remember(script.scriptId) { mutableStateOf(false) }
    val sectionOffsets = remember { mutableStateMapOf<Int, Float>() }
    val blockPositions = remember(script.scriptId) { mutableStateMapOf<String, ReaderBlockPosition>() }
    val paragraphLayouts = remember(script.scriptId) { mutableStateMapOf<String, TextLayoutResult>() }
    var viewportRootY by remember { mutableFloatStateOf(0f) }
    var positionRestored by remember(script.scriptId) { mutableStateOf(false) }
    var unresolvedFallbackAnchor by remember(script.scriptId) { mutableStateOf<PositionAnchor?>(null) }
    var fallbackRestoreScrollValue by remember(script.scriptId) { mutableIntStateOf(-1) }
    var isPlaying by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(0) }
    var rewindLeaseUntil by remember { mutableLongStateOf(0L) }
    var speed by remember(script.scriptId) { mutableDoubleStateOf(script.speedMultiplier.coerceIn(0.5, 2.0)) }

    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    DisposableEffect(script.scriptId) {
        ReaderCommandBridge.registerReader()
        onDispose { ReaderCommandBridge.unregisterReader() }
    }

    LaunchedEffect(script.scriptId) {
        ReaderCommandBridge.stopRewindRequests.collect { rewindLeaseUntil = 0L }
    }

    LaunchedEffect(countdown) {
        if (countdown > 0) {
            delay(1_000)
            if (countdown == 1) {
                countdown = 0
                isPlaying = true
            } else {
                countdown -= 1
            }
        }
    }

    val pixelsPerSecond = with(density) { (24.dp * speed.toFloat()).toPx() }
    LaunchedEffect(isPlaying, speed, scrollState.maxValue) {
        if (!isPlaying) return@LaunchedEffect
        var previousFrame = 0L
        while (scrollState.value < scrollState.maxValue) {
            val frame = withFrameNanos { it }
            if (previousFrame != 0L) {
                val elapsedSeconds = (frame - previousFrame) / 1_000_000_000f
                scrollState.scrollTo(
                    (scrollState.value + (pixelsPerSecond * elapsedSeconds).roundToInt())
                        .coerceAtMost(scrollState.maxValue),
                )
            }
            previousFrame = frame
        }
        isPlaying = false
    }

    LaunchedEffect(rewindLeaseUntil, speed) {
        if (rewindLeaseUntil == 0L) return@LaunchedEffect
        var previousFrame = 0L
        while (SystemClock.elapsedRealtime() < rewindLeaseUntil && scrollState.value > 0) {
            val frame = withFrameNanos { it }
            if (previousFrame != 0L) {
                val elapsedSeconds = (frame - previousFrame) / 1_000_000_000f
                scrollState.scrollTo((scrollState.value - pixelsPerSecond * elapsedSeconds).roundToInt().coerceAtLeast(0))
            }
            previousFrame = frame
        }
        if (SystemClock.elapsedRealtime() >= rewindLeaseUntil) rewindLeaseUntil = 0L
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(background)
            .onGloballyPositioned { viewportRootY = it.positionInRoot().y },
    ) {
        if (!readingReminderAcknowledged) {
            AlertDialog(
                onDismissRequest = { readingReminderAcknowledged = true },
                title = { Text("Antes de empezar") },
                text = {
                    Text(
                        "Comprueba que el brillo te permita leer y revisa el ahorro de batería. A mi ritmo mantiene la pantalla activa mientras lees, pero algunos ajustes del sistema pueden interrumpirla. La app no cambia esos ajustes.",
                    )
                },
                confirmButton = {
                    Button(onClick = { readingReminderAcknowledged = true }) { Text("Continuar") }
                },
            )
        } else if (contrastRatio < minimumContrast && !contrastWarningAcknowledged) {
            AlertDialog(
                onDismissRequest = { contrastWarningAcknowledged = true },
                title = { Text("Contraste bajo") },
                text = {
                    Text(
                        "Este guion tiene un contraste de %.1f:1. Para este tamaño se recomienda al menos %.1f:1. Puedes cambiar los colores en la revisión del guion."
                            .format(java.util.Locale.getDefault(), contrastRatio, minimumContrast),
                    )
                },
                confirmButton = {
                    Button(onClick = { contrastWarningAcknowledged = true }) { Text("Continuar") }
                },
            )
        }
        val viewportHeight = maxHeight
        val markerOffset = viewportHeight * 0.4f
        val markerPx = with(density) { markerOffset.toPx() }
        val absoluteMarker = scrollState.value + markerPx
        val progress = if (scrollState.maxValue > 0) {
            scrollState.value.toFloat() / scrollState.maxValue.toFloat()
        } else 0f
        val remainingSeconds = if (pixelsPerSecond > 0f) {
            ceil((scrollState.maxValue - scrollState.value).coerceAtLeast(0) / pixelsPerSecond).toInt()
        } else 0
        val nearestLayout = blockPositions.values
            .filter { it.anchorText.isNotBlank() && it.topPx <= absoluteMarker }
            .maxByOrNull { it.topPx }
            ?: blockPositions.values.filter { it.anchorText.isNotBlank() }.minByOrNull { it.topPx }
        val currentAnchor = nearestLayout?.let { block ->
            val layout = paragraphLayouts[block.layoutKey]
            val lineOffset = layout?.let {
                val localY = (absoluteMarker - block.topPx).coerceAtLeast(0f)
                it.getLineStart(it.getLineForVerticalPosition(localY))
            }?.coerceIn(0, block.segmentLength) ?: 0
            val textOffset = (block.baseTextOffset + lineOffset).coerceIn(0, block.anchorText.length)
            PositionAnchor(
                scriptId = script.scriptId,
                sectionId = block.sectionId,
                blockId = block.blockId,
                textOffset = textOffset,
                surroundingText = block.anchorText.substring(
                    max(0, textOffset - ANCHOR_CONTEXT_BEFORE),
                    min(block.anchorText.length, textOffset + ANCHOR_CONTEXT_AFTER),
                ),
            )
        }
        val currentSectionFromLayout = script.sections.indices
            .filter { (sectionOffsets[it] ?: Float.MAX_VALUE) <= absoluteMarker }
            .maxOrNull() ?: 0
        val currentSectionFromAnchor = currentAnchor?.sectionId?.let { anchorSectionId ->
            script.sections.indexOfFirst { it.id == anchorSectionId }.takeIf { it >= 0 }
        }
        val currentSection = currentSectionFromAnchor ?: currentSectionFromLayout
        val expectedAnchorCount = script.sections.sumOf { section ->
            section.blocks.sumOf { block ->
                when (block) {
                    is ContentBlock.Paragraph -> 1
                    is ContentBlock.ListBlock -> block.items.size
                    is ContentBlock.Table -> if (block.rows.isEmpty()) block.headerCells.size else block.rows.sumOf { it.size }
                    is ContentBlock.Cue -> 0
                }
            }
        }

        fun sectionReadingOffset(sectionIndex: Int): Float? {
            val sectionId = script.sections.getOrNull(sectionIndex)?.id ?: return null
            return blockPositions.values
                .filter { it.sectionId == sectionId }
                .minOfOrNull { it.topPx }
                ?: sectionOffsets[sectionIndex]
        }

        fun anchorPositionModifier(
            sectionId: String,
            blockId: String,
            layoutKey: String,
            baseTextOffset: Int,
            anchorText: String,
            segmentLength: Int,
        ): Modifier =
            Modifier.fillMaxWidth().onGloballyPositioned { coordinates ->
                blockPositions[layoutKey] = ReaderBlockPosition(
                    sectionId = sectionId,
                    blockId = blockId,
                    layoutKey = layoutKey,
                    topPx = scrollState.value + coordinates.positionInRoot().y - viewportRootY,
                    baseTextOffset = baseTextOffset,
                    segmentLength = segmentLength,
                    anchorText = anchorText,
                )
            }

        LaunchedEffect(script.scriptId, blockPositions.size, paragraphLayouts.size, scrollState.maxValue) {
            if (
                positionRestored ||
                blockPositions.size < expectedAnchorCount ||
                paragraphLayouts.size < expectedAnchorCount
            ) return@LaunchedEffect
            val anchor = savedAnchor
            if (anchor != null) {
                val exactLayouts = blockPositions.values.filter {
                    it.blockId == anchor.blockId && it.sectionId == anchor.sectionId
                }
                val fallbackLayout = if (exactLayouts.isEmpty() && anchor.surroundingText.isNotBlank()) {
                    blockPositions.values.firstOrNull {
                        it.sectionId == anchor.sectionId && it.anchorText.contains(anchor.surroundingText)
                    }
                } else null
                val sourceLayout = exactLayouts.firstOrNull() ?: fallbackLayout
                val matchingEntries = when {
                    exactLayouts.isNotEmpty() -> exactLayouts
                    fallbackLayout != null -> blockPositions.values.filter { it.blockId == fallbackLayout.blockId }
                    else -> emptyList()
                }
                val fallbackSectionY = sectionOffsets.entries.firstOrNull { entry ->
                    script.sections.getOrNull(entry.key)?.id == anchor.sectionId
                }?.value
                val targetY = if (sourceLayout != null) {
                    val contextMatch = if (anchor.surroundingText.isBlank()) -1 else sourceLayout.anchorText.indexOf(anchor.surroundingText)
                    val mappedOffset = if (exactLayouts.isNotEmpty()) {
                        anchor.textOffset.coerceIn(0, sourceLayout.anchorText.length)
                    } else if (contextMatch >= 0) {
                        contextMatch + anchor.textOffset.coerceAtMost(ANCHOR_CONTEXT_BEFORE)
                    } else anchor.textOffset.coerceIn(0, sourceLayout.anchorText.length)
                    val matchingLayout = matchingEntries
                        .firstOrNull { mappedOffset in it.baseTextOffset..(it.baseTextOffset + it.segmentLength) }
                        ?: sourceLayout
                    val localOffset = (mappedOffset - matchingLayout.baseTextOffset).coerceIn(0, matchingLayout.segmentLength)
                    val layout = paragraphLayouts[matchingLayout.layoutKey]
                    val lineCenter = layout?.let {
                        val line = it.getLineForOffset(localOffset)
                        (it.getLineTop(line) + it.getLineBottom(line)) / 2f
                    } ?: 0f
                    matchingLayout.topPx + lineCenter
                } else fallbackSectionY ?: 0f
                scrollState.scrollTo((targetY - markerPx).roundToInt().coerceAtLeast(0))
                if (sourceLayout == null && fallbackSectionY != null) {
                    unresolvedFallbackAnchor = anchor
                    fallbackRestoreScrollValue = scrollState.value
                }
            }
            positionRestored = true
        }

        LaunchedEffect(script.scriptId, positionRestored, currentAnchor, unresolvedFallbackAnchor) {
            val anchor = currentAnchor ?: return@LaunchedEffect
            if (!positionRestored) return@LaunchedEffect
            val fallbackAnchor = unresolvedFallbackAnchor
            if (fallbackAnchor != null) {
                val fallbackSectionIndex = script.sections.indexOfFirst { it.id == fallbackAnchor.sectionId }
                val currentAnchorSectionIndex = script.sections.indexOfFirst { it.id == anchor.sectionId }
                val movedBackBeforeFallback = fallbackRestoreScrollValue >= 0 &&
                    scrollState.value < fallbackRestoreScrollValue - with(density) { 32.dp.toPx() }.roundToInt()
                val enteredValidBlockInFallbackSection = currentAnchorSectionIndex == fallbackSectionIndex
                val movedForwardPastFallbackSection = currentAnchorSectionIndex > fallbackSectionIndex
                if (!movedBackBeforeFallback && !enteredValidBlockInFallbackSection && !movedForwardPastFallbackSection) {
                    return@LaunchedEffect
                }
                unresolvedFallbackAnchor = null
            }
            delay(POSITION_SAVE_DEBOUNCE_MS)
            positionStore.save(anchor)
        }

        fun navigateTo(target: Int) {
            val resumePlayback = isPlaying
            isPlaying = false
            coroutineScope.launch {
                scrollState.animateScrollTo(target.coerceIn(0, scrollState.maxValue))
                isPlaying = resumePlayback
            }
        }

        LaunchedEffect(script.scriptId) {
            ReaderCommandBridge.requests.collect { request ->
                if (!request.acknowledgement.isActive) return@collect
                val action = request.command.action
                var rejection: String? = null
                when (action) {
                    PlaybackAction.PLAY -> {
                        rewindLeaseUntil = 0L
                        if (scrollState.value >= scrollState.maxValue) scrollState.scrollTo(0)
                        countdown = 3
                    }
                    PlaybackAction.PAUSE -> { isPlaying = false; countdown = 0; rewindLeaseUntil = 0L }
                    PlaybackAction.INCREASE_SPEED -> {
                        if (speed >= 2.0) rejection = "SPEED_LIMIT"
                        else {
                            speed = ((speed * 10).roundToInt() + 1).coerceAtMost(20) / 10.0
                            onSpeedChange(speed)
                        }
                    }
                    PlaybackAction.DECREASE_SPEED -> {
                        if (speed <= 0.5) rejection = "SPEED_LIMIT"
                        else {
                            speed = ((speed * 10).roundToInt() - 1).coerceAtLeast(5) / 10.0
                            onSpeedChange(speed)
                        }
                    }
                    PlaybackAction.PREVIOUS_SECTION -> {
                        if (currentSection <= 0) rejection = "FIRST_SECTION"
                        else navigateTo((sectionOffsets[currentSection - 1]?.minus(markerPx))?.roundToInt() ?: 0)
                    }
                    PlaybackAction.NEXT_SECTION -> {
                        if (currentSection >= script.sections.lastIndex) rejection = "LAST_SECTION"
                        else navigateTo((sectionOffsets[currentSection + 1]?.minus(markerPx))?.roundToInt() ?: scrollState.maxValue)
                    }
                    PlaybackAction.GO_TO_START -> { countdown = 0; isPlaying = false; rewindLeaseUntil = 0L; navigateTo(0) }
                    PlaybackAction.REWIND_START -> {
                        isPlaying = false
                        countdown = 0
                        rewindLeaseUntil = SystemClock.elapsedRealtime() + 2_500
                    }
                    PlaybackAction.REWIND_RENEW -> rewindLeaseUntil = SystemClock.elapsedRealtime() + 2_500
                    PlaybackAction.REWIND_STOP -> rewindLeaseUntil = 0L
                }
                val state = PlaybackState(
                    phoneEpochId = ReaderCommandBridge.phoneEpochId,
                    revision = ReaderCommandBridge.latestState.revision,
                    scriptId = script.scriptId,
                    sectionId = script.sections.getOrNull(currentSection)?.id,
                    sectionOrdinal = currentSection,
                    status = when { countdown > 0 -> PlaybackStatus.COUNTDOWN; isPlaying -> PlaybackStatus.PLAYING; else -> PlaybackStatus.PAUSED },
                    speedMultiplier = speed,
                    progress = progress.toDouble(),
                    remainingSeconds = remainingSeconds.toLong(),
                )
                ReaderCommandBridge.publish(state)
                request.acknowledgement.complete(
                    com.teleprompter.shared.model.PlaybackAcknowledgement(
                        commandId = request.command.commandId,
                        applied = rejection == null,
                        reason = rejection,
                        resultingStateRevision = ReaderCommandBridge.latestState.revision,
                    ),
                )
            }
        }

        LaunchedEffect(script.scriptId, currentSection, isPlaying, countdown, speed, progress, remainingSeconds) {
            ReaderCommandBridge.publish(
                PlaybackState(
                    phoneEpochId = ReaderCommandBridge.phoneEpochId,
                    revision = ReaderCommandBridge.latestState.revision,
                    scriptId = script.scriptId,
                    sectionId = script.sections.getOrNull(currentSection)?.id,
                    sectionOrdinal = currentSection,
                    status = when { countdown > 0 -> PlaybackStatus.COUNTDOWN; isPlaying -> PlaybackStatus.PLAYING; scrollState.value >= scrollState.maxValue -> PlaybackStatus.FINISHED; else -> PlaybackStatus.PAUSED },
                    speedMultiplier = speed,
                    progress = progress.toDouble(),
                    remainingSeconds = remainingSeconds.toLong(),
                ),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Spacer(Modifier.height(markerOffset))
            script.sections.forEachIndexed { sectionIndex, section ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coordinates ->
                            sectionOffsets[sectionIndex] =
                                coordinates.positionInRoot().y - viewportRootY + scrollState.value
                        },
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Text(
                        text = section.subtitle ?: "Sección ${sectionIndex + 1}",
                        color = textColor.copy(alpha = 0.78f),
                        fontSize = fontSize * 0.78f,
                        lineHeight = lineHeight,
                        fontWeight = FontWeight.SemiBold,
                    )
                    section.blocks.forEach { block ->
                        when (block) {
                            is ContentBlock.Paragraph -> Text(
                                text = block.runs.toReaderText(),
                                modifier = anchorPositionModifier(
                                    section.id,
                                    block.id,
                                    block.id,
                                    0,
                                    block.runs.readerPlainText(),
                                    block.runs.readerPlainText().length,
                                ),
                                color = textColor,
                                fontSize = fontSize,
                                lineHeight = lineHeight,
                                onTextLayout = { paragraphLayouts[block.id] = it },
                            )
                            is ContentBlock.ListBlock -> {
                                val itemTexts = block.items.map { it.readerPlainText() }
                                val listAnchorText = itemTexts.joinToString(" ")
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    block.items.forEachIndexed { itemIndex, item ->
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                                            val bullet = if (block.ordered) "${itemIndex + 1}." else "•"
                                            Text(bullet, color = textColor, fontSize = fontSize, lineHeight = lineHeight)
                                            val baseTextOffset = itemTexts.take(itemIndex).sumOf { it.length + 1 }
                                            val layoutKey = "${block.id}:item:$itemIndex"
                                            Text(
                                                text = item.toReaderText(),
                                                modifier = anchorPositionModifier(
                                                    section.id,
                                                    block.id,
                                                    layoutKey,
                                                    baseTextOffset,
                                                    listAnchorText,
                                                    itemTexts[itemIndex].length,
                                                ),
                                                color = textColor,
                                                fontSize = fontSize,
                                                lineHeight = lineHeight,
                                                onTextLayout = { paragraphLayouts[layoutKey] = it },
                                            )
                                        }
                                    }
                                }
                            }
                            is ContentBlock.Table -> {
                                val tableSegments = if (block.rows.isEmpty()) {
                                    block.headerCells.map { it.readerPlainText() }
                                } else {
                                    block.rows.flatMap { row ->
                                        row.mapIndexed { cellIndex, cell ->
                                            "${block.headerCells.getOrNull(cellIndex).orEmpty().readerPlainText()}: ${cell.readerPlainText()}"
                                        }
                                    }
                                }
                                val tableAnchorText = tableSegments.joinToString(" ")
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    if (block.rows.isEmpty()) {
                                        block.headerCells.forEachIndexed { headerIndex, header ->
                                            val baseTextOffset = tableSegments.take(headerIndex).sumOf { it.length + 1 }
                                            val layoutKey = "${block.id}:header:$headerIndex"
                                            Text(
                                                text = header.toReaderText(),
                                                modifier = anchorPositionModifier(
                                                    section.id,
                                                    block.id,
                                                    layoutKey,
                                                    baseTextOffset,
                                                    tableAnchorText,
                                                    tableSegments[headerIndex].length,
                                                ),
                                                color = textColor,
                                                fontSize = fontSize,
                                                lineHeight = lineHeight,
                                                onTextLayout = { paragraphLayouts[layoutKey] = it },
                                            )
                                        }
                                    }
                                    var segmentIndex = 0
                                    block.rows.forEach { row ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = CardDefaults.cardColors(containerColor = background),
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(textColor.copy(alpha = 0.07f))
                                                    .padding(12.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                row.forEachIndexed { cellIndex, cell ->
                                                    val segmentText = tableSegments[segmentIndex]
                                                    val baseTextOffset = tableSegments.take(segmentIndex).sumOf { it.length + 1 }
                                                    val layoutKey = "${block.id}:cell:$segmentIndex"
                                                    Text(
                                                        text = buildAnnotatedString {
                                                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                                                append(block.headerCells.getOrNull(cellIndex).orEmpty().readerPlainText())
                                                                append(": ")
                                                            }
                                                            append(cell.toReaderText())
                                                        },
                                                        modifier = anchorPositionModifier(
                                                            section.id,
                                                            block.id,
                                                            layoutKey,
                                                            baseTextOffset,
                                                            tableAnchorText,
                                                            segmentText.length,
                                                        ),
                                                        color = textColor,
                                                        fontSize = fontSize * 0.86f,
                                                        lineHeight = lineHeight,
                                                        onTextLayout = { paragraphLayouts[layoutKey] = it },
                                                    )
                                                    segmentIndex += 1
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            is ContentBlock.Cue -> Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = background),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(textColor.copy(alpha = 0.10f))
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Text("Ⅱ", color = textColor, fontSize = fontSize)
                                    Text(
                                        text = block.label,
                                        color = textColor,
                                        fontSize = fontSize * 0.85f,
                                        lineHeight = lineHeight,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height((viewportHeight - markerOffset).coerceAtLeast(40.dp)))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .offset(y = markerOffset - 18.dp)
                .drawBehind {
                    drawLine(
                        color = textColor.copy(alpha = 0.6f),
                        start = Offset.Zero.copy(y = size.height / 2),
                        end = Offset(size.width, size.height / 2),
                        strokeWidth = 2.dp.toPx(),
                    )
                },
        ) {
            Text(
                "LEE AQUÍ",
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .background(background, RoundedCornerShape(4.dp))
                    .border(1.dp, textColor, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                color = textColor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(background.copy(alpha = 0.96f))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${currentSection + 1}/${script.sections.size} · ${script.sections.getOrNull(currentSection)?.subtitle ?: "Sección ${currentSection + 1}"}",
                    modifier = Modifier.weight(1f),
                    color = textColor,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
                val remainingLabel = "${remainingSeconds / 60}:${(remainingSeconds % 60).toString().padStart(2, '0')}"
                Text(
                    remainingLabel,
                    modifier = Modifier.semantics { contentDescription = "Tiempo restante $remainingLabel" },
                    color = textColor,
                )
            }
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = textColor,
                trackColor = textColor.copy(alpha = 0.2f),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    enabled = !isBusy && currentSection > 0,
                    onClick = {
                        val target = (sectionReadingOffset(currentSection - 1)?.minus(markerPx))?.roundToInt()?.coerceAtLeast(0) ?: 0
                        navigateTo(target)
                    },
                    modifier = Modifier.semantics { contentDescription = "Ir a la sección anterior" },
                    colors = ButtonDefaults.textButtonColors(contentColor = textColor),
                ) { Text("Anterior") }
                OutlinedButton(
                    enabled = !isBusy,
                    onClick = {
                        if (isPlaying || countdown > 0) {
                            isPlaying = false
                            countdown = 0
                            rewindLeaseUntil = 0L
                        } else {
                            rewindLeaseUntil = 0L
                            coroutineScope.launch {
                                if (scrollState.value >= scrollState.maxValue) scrollState.scrollTo(0)
                                countdown = 3
                            }
                        }
                    },
                    modifier = Modifier.semantics {
                        contentDescription = when {
                            countdown > 0 -> "Cancelar cuenta atrás"
                            isPlaying -> "Pausar lectura"
                            else -> "Iniciar lectura"
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = textColor),
                ) {
                    Text(
                        when {
                            countdown > 0 -> countdown.toString()
                            isPlaying -> "Pausa"
                            else -> "Play"
                        },
                    )
                }
                TextButton(
                    enabled = !isBusy && currentSection < script.sections.lastIndex,
                    onClick = {
                        val target = (sectionReadingOffset(currentSection + 1)?.minus(markerPx))?.roundToInt()?.coerceAtLeast(0) ?: scrollState.maxValue
                        navigateTo(target)
                    },
                    modifier = Modifier.semantics { contentDescription = "Ir a la sección siguiente" },
                    colors = ButtonDefaults.textButtonColors(contentColor = textColor),
                ) { Text("Siguiente") }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    enabled = !isBusy && speed > 0.5,
                    onClick = {
                        val updatedSpeed = ((speed * 10).roundToInt() - 1).coerceAtLeast(5) / 10.0
                        speed = updatedSpeed
                        onSpeedChange(updatedSpeed)
                    },
                    modifier = Modifier.semantics { contentDescription = "Reducir velocidad de lectura" },
                    colors = ButtonDefaults.textButtonColors(contentColor = textColor),
                ) { Text("−") }
                Text("%.1f×".format(java.util.Locale.getDefault(), speed), color = textColor)
                TextButton(
                    enabled = !isBusy && speed < 2.0,
                    onClick = {
                        val updatedSpeed = ((speed * 10).roundToInt() + 1).coerceAtMost(20) / 10.0
                        speed = updatedSpeed
                        onSpeedChange(updatedSpeed)
                    },
                    modifier = Modifier.semantics { contentDescription = "Aumentar velocidad de lectura" },
                    colors = ButtonDefaults.textButtonColors(contentColor = textColor),
                ) { Text("+") }
                Spacer(Modifier.size(8.dp))
                TextButton(
                    enabled = !isBusy && scrollState.value > 0,
                    onClick = {
                        val resumePlayback = isPlaying
                        isPlaying = false
                        coroutineScope.launch {
                            scrollState.scrollTo((scrollState.value - (pixelsPerSecond * 3f).roundToInt()).coerceAtLeast(0))
                            isPlaying = resumePlayback
                        }
                    },
                    modifier = Modifier.semantics { contentDescription = "Rebobinar tres segundos" },
                    colors = ButtonDefaults.textButtonColors(contentColor = textColor),
                ) { Text("Rebobinar") }
                TextButton(
                    enabled = !isBusy && scrollState.value > 0,
                    onClick = {
                        isPlaying = false
                        countdown = 0
                        rewindLeaseUntil = 0L
                        navigateTo(0)
                    },
                    modifier = Modifier.semantics { contentDescription = "Volver al inicio del guion" },
                    colors = ButtonDefaults.textButtonColors(contentColor = textColor),
                ) { Text("Inicio") }
            }
            if (countdown > 0) {
                Text(
                    text = "Empieza en $countdown",
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = textColor,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

private fun List<InlineRun>.toReaderText() = buildAnnotatedString {
    forEach { run ->
        withStyle(
            SpanStyle(
                fontWeight = if (run.bold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (run.italic) FontStyle.Italic else FontStyle.Normal,
            ),
        ) {
            append(run.text)
        }
    }
}

private fun List<InlineRun>.readerPlainText() = joinToString(separator = "") { it.text }

private data class ReaderBlockPosition(
    val sectionId: String,
    val blockId: String,
    val layoutKey: String,
    val topPx: Float,
    val baseTextOffset: Int,
    val segmentLength: Int,
    val anchorText: String,
)

private const val ANCHOR_CONTEXT_BEFORE = 12
private const val ANCHOR_CONTEXT_AFTER = 28
private const val POSITION_SAVE_DEBOUNCE_MS = 450L

private fun readerContrastRatio(textArgb: String, backgroundArgb: String): Double {
    val rawText = AndroidColor.parseColor(textArgb)
    val rawBackground = AndroidColor.parseColor(backgroundArgb)
    val background = composite(rawBackground, AndroidColor.BLACK)
    val text = composite(rawText, background)
    val textLuminance = relativeLuminance(text)
    val backgroundLuminance = relativeLuminance(background)
    val lighter = max(textLuminance, backgroundLuminance)
    val darker = min(textLuminance, backgroundLuminance)
    return (lighter + 0.05) / (darker + 0.05)
}

private fun composite(foreground: Int, background: Int): Int {
    val alpha = AndroidColor.alpha(foreground) / 255.0
    val inverseAlpha = 1.0 - alpha
    val red = (AndroidColor.red(foreground) * alpha + AndroidColor.red(background) * inverseAlpha).roundToInt()
    val green = (AndroidColor.green(foreground) * alpha + AndroidColor.green(background) * inverseAlpha).roundToInt()
    val blue = (AndroidColor.blue(foreground) * alpha + AndroidColor.blue(background) * inverseAlpha).roundToInt()
    return AndroidColor.rgb(red, green, blue)
}

private fun relativeLuminance(color: Int): Double {
    fun linear(channel: Int): Double {
        val normalized = channel / 255.0
        return if (normalized <= 0.04045) normalized / 12.92 else ((normalized + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * linear(AndroidColor.red(color)) +
        0.7152 * linear(AndroidColor.green(color)) +
        0.0722 * linear(AndroidColor.blue(color))
}
