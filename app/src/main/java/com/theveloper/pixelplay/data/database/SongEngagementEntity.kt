package com.theveloper.pixelplay.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for storing song engagement statistics.
 * This replaces the JSON-based storage in DailyMixManager for better performance
 * and structured querying.
 */
@Entity(
    tableName = "song_engagements",
    indices = [
        Index(value = ["play_count"], unique = false)
    ]
)
data class SongEngagementEntity(
    @PrimaryKey
    @ColumnInfo(name = "song_id")
    val songId: String,

    @ColumnInfo(name = "play_count")
    val playCount: Int = 0,

    @ColumnInfo(name = "total_play_duration_ms")
    val totalPlayDurationMs: Long = 0L,

    @ColumnInfo(name = "last_played_timestamp")
    val lastPlayedTimestamp: Long = 0L,

    @ColumnInfo(name = "last_resume_position_ms", defaultValue = "0")
    val lastResumePositionMs: Long = 0L,

    @ColumnInfo(name = "is_completed", defaultValue = "0")
    val isCompleted: Boolean = false,

    @ColumnInfo(name = "completion_count", defaultValue = "0")
    val completionCount: Int = 0,

    @ColumnInfo(name = "last_completed_timestamp", defaultValue = "0")
    val lastCompletedTimestamp: Long = 0L,

    @ColumnInfo(name = "net_listened_ms", defaultValue = "0")
    val netListenedMs: Long = 0L
)
