package com.teleprompter.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Text
import com.teleprompter.shared.model.PlaybackAction
import com.teleprompter.shared.model.PlaybackStatus
import com.teleprompter.wear.wear.WearDataLayerClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun RemoteScreen(client: WearDataLayerClient) {
    val coroutineScope = rememberCoroutineScope()
    val state = client.confirmedState
    val active = state?.scriptId != null && client.connected
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.76f)
                .align(Alignment.Center)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("A mi ritmo", textAlign = TextAlign.Center)
            Text(client.statusMessage, textAlign = TextAlign.Center, maxLines = 3)
            Box(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text("${((state?.progress ?: 0.0) * 100).toInt()}%", modifier = Modifier.align(Alignment.CenterStart))
                Text(formatRemaining(state?.remainingSeconds), modifier = Modifier.align(Alignment.CenterEnd))
            }
            Text(if (active) "Sección ${state.sectionOrdinal + 1}" else "Abre un guion en el teléfono", textAlign = TextAlign.Center)
            Text("Velocidad %.1f×".format(Locale.getDefault(), state?.speedMultiplier ?: 1.0))
            Button(onClick = { client.command(if (state?.status == PlaybackStatus.PLAYING || state?.status == PlaybackStatus.COUNTDOWN) PlaybackAction.PAUSE else PlaybackAction.PLAY) }, enabled = active) {
                Text(if (state?.status == PlaybackStatus.PLAYING || state?.status == PlaybackStatus.COUNTDOWN) "Pausa" else "Play")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { client.command(PlaybackAction.DECREASE_SPEED) }, enabled = active) { Text("−") }
                Button(onClick = { client.command(PlaybackAction.INCREASE_SPEED) }, enabled = active) { Text("+") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { client.command(PlaybackAction.PREVIOUS_SECTION) }, enabled = active) { Text("Anterior") }
                Button(onClick = { client.command(PlaybackAction.NEXT_SECTION) }, enabled = active) { Text("Siguiente") }
            }
            Button(onClick = { client.command(PlaybackAction.GO_TO_START) }, enabled = active) { Text("Al inicio") }
            Button(
                onClick = {},
                enabled = active,
                modifier = Modifier.pointerInput(client) {
                    detectTapGestures(onPress = {
                        val renewer = coroutineScope.launch {
                            client.command(PlaybackAction.REWIND_START)
                            while (true) {
                                delay(1_000)
                                client.command(PlaybackAction.REWIND_RENEW)
                            }
                        }
                        try { tryAwaitRelease() } finally {
                            renewer.cancel()
                            client.command(PlaybackAction.REWIND_STOP)
                        }
                    })
                },
            ) { Text("Mantén: rebobinar") }
        }
    }
}

private fun formatRemaining(seconds: Long?): String {
    if (seconds == null) return "—:—"
    val mins = seconds.coerceAtLeast(0) / 60
    val secs = seconds.coerceAtLeast(0) % 60
    return "%d:%02d".format(Locale.ROOT, mins, secs)
}
