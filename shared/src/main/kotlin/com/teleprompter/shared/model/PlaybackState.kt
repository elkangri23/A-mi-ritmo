package com.teleprompter.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class PlaybackState(
    val phoneEpochId: String,
    val revision: Long,
    val scriptId: String?,
    val sectionId: String?,
    val sectionOrdinal: Int,
    val status: PlaybackStatus,
    val speedMultiplier: Double,
    val progress: Double,
    val remainingSeconds: Long?,
)

@Serializable
enum class PlaybackStatus {
    IDLE,
    COUNTDOWN,
    PLAYING,
    PAUSED,
    FINISHED,
}
