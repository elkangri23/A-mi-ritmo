package com.teleprompter.phone.wear

import com.teleprompter.shared.model.PlaybackAcknowledgement
import com.teleprompter.shared.model.PlaybackCommand
import com.teleprompter.shared.model.PlaybackState
import com.teleprompter.shared.model.PlaybackStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class ReaderCommandRequest(
    val command: PlaybackCommand,
    val acknowledgement: CompletableDeferred<PlaybackAcknowledgement>,
)

object ReaderCommandBridge {
    private val mutableRequests = Channel<ReaderCommandRequest>(capacity = REQUEST_QUEUE_CAPACITY)
    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<PlaybackAcknowledgement>>()
    val requests = mutableRequests.receiveAsFlow()
    private val mutableStopRewind = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val stopRewindRequests = mutableStopRewind.asSharedFlow()
    @Volatile var readerActive = false
        private set
    @Volatile var phoneEpochId: String = UUID.randomUUID().toString()
        private set
    @Volatile var latestState: PlaybackState = PlaybackState(
        phoneEpochId = phoneEpochId,
        revision = 1,
        scriptId = null,
        sectionId = null,
        sectionOrdinal = 0,
        status = PlaybackStatus.IDLE,
        speedMultiplier = 1.0,
        progress = 0.0,
        remainingSeconds = null,
    )
        private set

    @Synchronized
    fun registerReader() { readerActive = true }

    @Synchronized
    fun unregisterReader() {
        readerActive = false
        stopRewind()
        latestState = latestState.copy(
            revision = latestState.revision + 1,
            scriptId = null,
            sectionId = null,
            sectionOrdinal = 0,
            status = PlaybackStatus.IDLE,
            progress = 0.0,
            remainingSeconds = null,
        )
        pendingRequests.forEach { (commandId, acknowledgement) ->
            acknowledgement.complete(
                PlaybackAcknowledgement(
                    commandId = commandId,
                    applied = false,
                    reason = "NO_ACTIVE_READER",
                    resultingStateRevision = latestState.revision,
                ),
            )
        }
        while (true) {
            val queued = mutableRequests.tryReceive().getOrNull() ?: break
            queued.acknowledgement.complete(
                PlaybackAcknowledgement(
                    commandId = queued.command.commandId,
                    applied = false,
                    reason = "NO_ACTIVE_READER",
                    resultingStateRevision = latestState.revision,
                ),
            )
        }
    }

    fun stopRewind() { mutableStopRewind.tryEmit(Unit) }

    fun publish(state: PlaybackState) {
        latestState = state.copy(phoneEpochId = phoneEpochId, revision = latestState.revision + 1)
    }

    @Synchronized
    fun submit(command: PlaybackCommand): CompletableDeferred<PlaybackAcknowledgement>? {
        if (!readerActive) return null
        val deferred = CompletableDeferred<PlaybackAcknowledgement>()
        pendingRequests[command.commandId] = deferred
        deferred.invokeOnCompletion { pendingRequests.remove(command.commandId, deferred) }
        if (mutableRequests.trySend(ReaderCommandRequest(command, deferred)).isFailure) {
            deferred.complete(
                PlaybackAcknowledgement(
                    commandId = command.commandId,
                    applied = false,
                    reason = "COMMAND_QUEUE_FULL",
                    resultingStateRevision = latestState.revision,
                ),
            )
        }
        return deferred
    }

    private const val REQUEST_QUEUE_CAPACITY = 8
}
