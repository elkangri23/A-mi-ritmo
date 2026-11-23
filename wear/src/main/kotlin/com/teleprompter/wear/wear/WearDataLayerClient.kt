package com.teleprompter.wear.wear

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.CapabilityClient
import com.teleprompter.shared.model.PlaybackAction
import com.teleprompter.shared.model.PlaybackCommand
import com.teleprompter.shared.model.PlaybackState
import com.teleprompter.shared.model.WearMessage
import com.teleprompter.shared.model.WearMessageType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

class WearDataLayerClient(context: Context) : MessageClient.OnMessageReceivedListener {
    private val appContext = context.applicationContext
    private val json = Json { encodeDefaults = true }
    private var node: Node? = null
    private var sequence = 0L
    private var phoneEpochId: String? = null
    private var pendingSnapshotRequestId: String? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var reconnectJob: Job? = null
    private val pendingCommands = mutableMapOf<String, PendingWearCommand>()
    private var latestCommandId: String? = null
    private var listenerRegistered = false
    private var lastHelloAtMs = 0L
    private var capabilityConnectionGeneration = 0L
    private var capabilityQuerySequence = 0L
    private var lastAppliedCapabilityQuerySequence = 0L
    val watchSessionId: String = UUID.randomUUID().toString()

    private data class PendingWearCommand(
        val target: Node,
        val phoneEpochId: String,
        val message: WearMessage,
        var retryJob: Job? = null,
    )

    var connected by mutableStateOf(false)
        private set
    var confirmedState by mutableStateOf<PlaybackState?>(null)
        private set
    var statusMessage by mutableStateOf("Buscando teléfono…")
        private set

    fun connect() {
        if (!listenerRegistered) {
            Wearable.getMessageClient(appContext).addListener(this)
            listenerRegistered = true
        }
        reconnectJob?.cancel()
        val connectionGeneration = ++capabilityConnectionGeneration
        lastAppliedCapabilityQuerySequence = 0L
        reconnectJob = scope.launch {
            while (isActive) {
                val querySequence = ++capabilityQuerySequence
                Wearable.getCapabilityClient(appContext).getCapability(PHONE_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
                    .addOnSuccessListener { nodes ->
                        if (connectionGeneration != capabilityConnectionGeneration ||
                            querySequence <= lastAppliedCapabilityQuerySequence
                        ) return@addOnSuccessListener
                        lastAppliedCapabilityQuerySequence = querySequence
                        val next = nodes.nodes.firstOrNull()
                        val changed = next?.id != node?.id
                        if (changed) {
                            clearPendingCommands()
                            pendingSnapshotRequestId = null
                            phoneEpochId = null
                            confirmedState = null
                        }
                        node = next
                        connected = next != null
                        if (next == null) {
                            clearConnection("Instala y abre A mi ritmo en el teléfono")
                        }
                        else if (changed || phoneEpochId == null || System.currentTimeMillis() - lastHelloAtMs >= 10_000) {
                            confirmedState = null
                            statusMessage = "Conectando…"
                            lastHelloAtMs = System.currentTimeMillis()
                            requestHandshake(next)
                        } else requestSnapshot(next)
                    }
                    .addOnFailureListener {
                        if (connectionGeneration == capabilityConnectionGeneration &&
                            querySequence > lastAppliedCapabilityQuerySequence
                        ) {
                            lastAppliedCapabilityQuerySequence = querySequence
                            clearConnection("Teléfono no disponible")
                        }
                    }
                delay(2_000)
            }
        }
    }

    fun disconnect() {
        reconnectJob?.cancel()
        capabilityConnectionGeneration += 1
        clearConnection("Buscando teléfono…")
        if (listenerRegistered) Wearable.getMessageClient(appContext).removeListener(this)
        listenerRegistered = false
    }

    fun command(action: PlaybackAction) {
        if (!connected) return
        val target = node ?: return
        val state = confirmedState
        val targetPhoneEpochId = phoneEpochId ?: return
        if (state == null) return
        sequence += 1
        val command = PlaybackCommand(watchSessionId, UUID.randomUUID().toString(), sequence, action)
        val message = WearMessage(WearMessageType.COMMAND, targetPhoneEpochId, watchSessionId, command = command)
        latestCommandId = command.commandId
        val pending = PendingWearCommand(target, targetPhoneEpochId, message)
        pendingCommands[command.commandId] = pending
        statusMessage = "Enviando…"
        send(message, target)
        pending.retryJob = scope.launch {
            delay(2_000)
            if (pendingCommands[command.commandId] !== pending) return@launch
            if (node?.id != target.id || this@WearDataLayerClient.phoneEpochId != pending.phoneEpochId) return@launch
            send(message, target) // retry with the same ID and sequence; phone caches acknowledgements
            delay(2_000)
            if (pendingCommands.remove(command.commandId) === pending) {
                if (latestCommandId == command.commandId) {
                    statusMessage = "No confirmado"
                    node?.let(::requestSnapshot)
                }
            }
        }
    }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != PATH || event.sourceNodeId != node?.id) return
        val message = runCatching { json.decodeFromString<WearMessage>(event.data.decodeToString()) }.getOrNull() ?: return
        if (message.watchSessionId != watchSessionId) return
        when (message.type) {
            WearMessageType.SNAPSHOT -> {
                if (message.requestId != pendingSnapshotRequestId) return
                pendingSnapshotRequestId = null
                val state = message.state ?: return
                if (phoneEpochId != message.phoneEpochId) {
                    confirmedState = null
                    clearPendingCommands()
                }
                phoneEpochId = message.phoneEpochId
                if (confirmedState == null || state.revision >= (confirmedState?.revision ?: 0)) {
                    confirmedState = state
                    connected = true
                    statusMessage = "Conectado"
                }
            }
            WearMessageType.ACKNOWLEDGEMENT -> {
                val ack = message.acknowledgement ?: return
                val pending = pendingCommands[ack.commandId] ?: return
                if (pending.phoneEpochId != message.phoneEpochId || pending.target.id != event.sourceNodeId) return
                pendingCommands.remove(ack.commandId)
                pending.retryJob?.cancel()
                if (ack.commandId == latestCommandId) {
                    statusMessage = if (ack.applied) "Confirmado" else "No confirmado: ${ack.reason ?: "error"}"
                }
                node?.let(::requestSnapshot)
            }
            else -> Unit
        }
    }

    private fun send(message: WearMessage, target: Node) {
        Wearable.getMessageClient(appContext).sendMessage(target.id, PATH, json.encodeToString(message).encodeToByteArray())
            .addOnFailureListener {
                val commandId = message.command?.commandId
                if (message.type != WearMessageType.COMMAND || commandId == latestCommandId && pendingCommands.containsKey(commandId)) {
                    statusMessage = "No confirmado"
                }
            }
    }

    private fun clearPendingCommands() {
        pendingCommands.values.forEach { it.retryJob?.cancel() }
        pendingCommands.clear()
    }

    private fun clearConnection(message: String) {
        node = null
        phoneEpochId = null
        pendingSnapshotRequestId = null
        clearPendingCommands()
        connected = false
        confirmedState = null
        statusMessage = message
    }

    private fun requestHandshake(target: Node) {
        val requestId = UUID.randomUUID().toString()
        pendingSnapshotRequestId = requestId
        send(WearMessage(type = WearMessageType.HELLO, watchSessionId = watchSessionId, requestId = requestId), target)
    }

    private fun requestSnapshot(target: Node) {
        val requestId = UUID.randomUUID().toString()
        pendingSnapshotRequestId = requestId
        send(WearMessage(WearMessageType.STATE_REQUEST, phoneEpochId, watchSessionId, requestId = requestId), target)
    }

    companion object {
        const val PATH = "/a-mi-ritmo/session"
        const val PHONE_CAPABILITY = "a_mi_ritmo_phone"
    }
}
