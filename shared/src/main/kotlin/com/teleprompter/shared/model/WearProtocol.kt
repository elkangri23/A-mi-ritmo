package com.teleprompter.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class WearMessageType {
    HELLO,
    STATE_REQUEST,
    COMMAND,
    SNAPSHOT,
    ACKNOWLEDGEMENT,
}

/** Small transport envelope. It intentionally has no script, title, cue, or text fields. */
@Serializable
data class WearMessage(
    val type: WearMessageType,
    val phoneEpochId: String? = null,
    val watchSessionId: String? = null,
    val state: PlaybackState? = null,
    val command: PlaybackCommand? = null,
    val acknowledgement: PlaybackAcknowledgement? = null,
    val requestId: String? = null,
)
