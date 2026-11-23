package com.teleprompter.phone.wear

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.teleprompter.shared.model.PlaybackAcknowledgement
import com.teleprompter.shared.model.PlaybackState
import com.teleprompter.shared.model.PlaybackStatus
import com.teleprompter.shared.model.WearMessage
import com.teleprompter.shared.model.WearMessageType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

class PhoneWearListenerService : WearableListenerService() {
    @Synchronized
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != WearProtocol.PATH) return
        val incoming = runCatching { WearProtocol.json.decodeFromString<WearMessage>(event.data.decodeToString()) }
            .getOrNull() ?: return

        if (incoming.type == WearMessageType.COMMAND) {
            val command = incoming.command ?: return
            val sessionId = incoming.watchSessionId?.takeIf { it == command.watchSessionId } ?: return
            if (incoming.phoneEpochId != ReaderCommandBridge.phoneEpochId) {
                sendCommandAck(event.sourceNodeId, sessionId, PlaybackAcknowledgement(
                    command.commandId,
                    false,
                    "STALE_PHONE_EPOCH",
                    ReaderCommandBridge.latestState.revision,
                ))
                return
            }
            when (val claim = WearSession.claim(
                event.sourceNodeId,
                sessionId,
                command.commandId,
                command.sequenceNumber,
            )) {
                is CommandClaim.Cached -> sendCommandAck(event.sourceNodeId, sessionId, claim.acknowledgement)
                is CommandClaim.InFlight -> {
                    val acknowledgement = runBlocking {
                        withTimeoutOrNull(DUPLICATE_COMMAND_WAIT_MS) { claim.result.await() }
                    } ?: return
                    if (WearSession.isActive(event.sourceNodeId, sessionId)) {
                        sendCommandAck(event.sourceNodeId, sessionId, acknowledgement)
                    }
                }
                CommandClaim.New -> {
                    val request = ReaderCommandBridge.submit(command)
                    val acknowledgement = if (request == null) {
                        PlaybackAcknowledgement(command.commandId, false, "NO_ACTIVE_READER", ReaderCommandBridge.latestState.revision)
                    } else {
                        runBlocking {
                            withTimeoutOrNull(COMMAND_EXECUTION_TIMEOUT_MS) { request.await() } ?: run {
                                request.cancel()
                                PlaybackAcknowledgement(command.commandId, false, "COMMAND_TIMEOUT", ReaderCommandBridge.latestState.revision)
                            }
                        }
                    }
                    if (WearSession.complete(event.sourceNodeId, sessionId, command.commandId, acknowledgement)) {
                        sendCommandAck(event.sourceNodeId, sessionId, acknowledgement)
                    }
                }
                CommandClaim.Rejected -> sendCommandAck(event.sourceNodeId, sessionId, PlaybackAcknowledgement(
                    command.commandId,
                    false,
                    "STALE_SESSION_OR_SEQUENCE",
                    ReaderCommandBridge.latestState.revision,
                ))
            }
            return
        }

        val reply = when (incoming.type) {
            WearMessageType.HELLO -> {
                val sessionId = incoming.watchSessionId?.takeIf(String::isNotBlank) ?: return
                if (WearSession.activate(event.sourceNodeId, sessionId)) ReaderCommandBridge.stopRewind()
                WearMessage(
                    type = WearMessageType.SNAPSHOT,
                    phoneEpochId = ReaderCommandBridge.phoneEpochId,
                    watchSessionId = sessionId,
                    state = ReaderCommandBridge.latestState,
                    requestId = incoming.requestId,
                )
            }
            WearMessageType.STATE_REQUEST -> {
                if (!WearSession.isActive(event.sourceNodeId, incoming.watchSessionId)) return
                WearMessage(
                    type = WearMessageType.SNAPSHOT,
                    phoneEpochId = ReaderCommandBridge.phoneEpochId,
                    watchSessionId = WearSession.sessionId,
                    state = ReaderCommandBridge.latestState,
                    requestId = incoming.requestId,
                )
            }
            WearMessageType.COMMAND -> return
            WearMessageType.SNAPSHOT, WearMessageType.ACKNOWLEDGEMENT -> return
        }
        sendReply(event.sourceNodeId, reply)
    }

    private fun sendReply(node: String, message: WearMessage) {
        Wearable.getMessageClient(this).sendMessage(node, WearProtocol.PATH, WearProtocol.json.encodeToString(message).encodeToByteArray())
    }

    private fun sendCommandAck(node: String, sessionId: String, acknowledgement: PlaybackAcknowledgement) {
        sendReply(node, WearMessage(
            type = WearMessageType.ACKNOWLEDGEMENT,
            phoneEpochId = ReaderCommandBridge.phoneEpochId,
            watchSessionId = sessionId,
            acknowledgement = acknowledgement,
        ))
    }

    private companion object {
        const val DUPLICATE_COMMAND_WAIT_MS = 2_500L
        const val COMMAND_EXECUTION_TIMEOUT_MS = 1_500L
    }
}

internal object WearProtocol {
    const val PATH = "/a-mi-ritmo/session"
    val json = Json { ignoreUnknownKeys = false; encodeDefaults = true }
}

private sealed interface CommandClaim {
    data class Cached(val acknowledgement: PlaybackAcknowledgement) : CommandClaim
    data class InFlight(val result: CompletableDeferred<PlaybackAcknowledgement>) : CommandClaim
    data object New : CommandClaim
    data object Rejected : CommandClaim
}

private object WearSession {
    @Volatile var sessionId: String? = null
        private set
    @Volatile var nodeId: String? = null
        private set
    @Volatile var lastSequence: Long = 0
        private set
    private data class PendingCommand(
        val nodeId: String,
        val sessionId: String,
        val sequence: Long,
        val result: CompletableDeferred<PlaybackAcknowledgement>,
    )

    private data class CachedCommand(
        val sequence: Long,
        val acknowledgement: PlaybackAcknowledgement,
    )

    private val acknowledgements = LinkedHashMap<String, CachedCommand>()
    private val inFlight = mutableMapOf<String, PendingCommand>()
    @Synchronized fun activate(node: String, session: String): Boolean {
        val changed = sessionId != session || nodeId != node
        if (changed) {
            lastSequence = 0
            acknowledgements.clear()
            inFlight.values.forEach { it.result.cancel() }
            inFlight.clear()
        }
        nodeId = node
        sessionId = session
        return changed
    }

    @Synchronized fun isActive(node: String, session: String?): Boolean = nodeId == node && sessionId == session

    @Synchronized fun claim(node: String, session: String, commandId: String, sequence: Long): CommandClaim {
        if (!isActive(node, session)) return CommandClaim.Rejected
        acknowledgements[commandId]?.let { cached ->
            return if (cached.sequence == sequence) CommandClaim.Cached(cached.acknowledgement) else CommandClaim.Rejected
        }
        inFlight[commandId]?.let { pending ->
            return if (pending.sequence == sequence) CommandClaim.InFlight(pending.result) else CommandClaim.Rejected
        }
        if (sequence <= lastSequence) return CommandClaim.Rejected
        lastSequence = sequence
        val result = CompletableDeferred<PlaybackAcknowledgement>()
        inFlight[commandId] = PendingCommand(node, session, sequence, result)
        return CommandClaim.New
    }

    @Synchronized fun complete(node: String, session: String, commandId: String, acknowledgement: PlaybackAcknowledgement): Boolean {
        val pending = inFlight[commandId] ?: return false
        if (pending.nodeId != node || pending.sessionId != session || !isActive(node, session)) return false
        inFlight.remove(commandId)
        pending.result.complete(acknowledgement)
        acknowledgements[commandId] = CachedCommand(pending.sequence, acknowledgement)
        while (acknowledgements.size > 32) acknowledgements.remove(acknowledgements.keys.first())
        return true
    }
}
