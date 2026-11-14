package com.theveloper.pixelplay.data.model

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val songIds: List<String>,
    val createdAt: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis(),
    val isAiGenerated: Boolean = false,
    val isQueueGenerated: Boolean = false,
    val coverImageUri: String? = null,
    val coverColorArgb: Int? = null,
    val coverIconName: String? = null,
    val coverShapeType: String? = null, // "Circle", "SmoothRect", etc. Storing as String to avoid Enum import issues if moved
    val coverShapeDetail1: Float? = null, // e.g., CornerRadius / StarCurve
    val coverShapeDetail2: Float? = null, // e.g., Smoothness / StarRotation
    val coverShapeDetail3: Float? = null, // e.g., StarScale
    val coverShapeDetail4: Float? = null, // e.g., Star Sides (Int)
    val source: String = "LOCAL", // Source: "LOCAL", "NETEASE", "TELEGRAM", "AI", etc.
    val lastPlayedAt: Long? = null, // Epoch ms when course/playlist was last studied
    val completionPercentage: Float = 0f, // Ratio of completed lessons [0.0f..1.0f]
    val lastPlayedSongId: String? = null, // ID of last active song in playlist
    val lastPlayedSongTitle: String? = null // Title of last active song
) {
    val completionProgressPercent: Int
        get() = (completionPercentage * 100).toInt()
}

enum class PlaylistShapeType {
    Circle,
    SmoothRect,
    RotatedPill,
    Star
}
