package com.teleprompter.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class PlaybackCommand(
    val watchSessionId: String,
    val commandId: String,
    val sequenceNumber: Long,
    val action: PlaybackAction,
)

@Serializable
enum class PlaybackAction {
    PLAY,
    PAUSE,
    INCREASE_SPEED,
    DECREASE_SPEED,
    PREVIOUS_SECTION,
    NEXT_SECTION,
    GO_TO_START,
    REWIND_START,
    REWIND_RENEW,
    REWIND_STOP,
}

@Serializable
data class PlaybackAcknowledgement(
    val commandId: String,
    val applied: Boolean,
    val reason: String? = null,
    val resultingStateRevision: Long,
)
