package com.theveloper.pixelplay.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tracks a user's progress through a course (a folder or playlist treated as a learning unit).
 *
 * courseId format:
 *   - Local folder:    "local:<absolute_folder_path>"
 *   - GDrive folder:   "gdrive:<folder_id>"
 *   - Playlist:        "playlist:<playlist_uuid>"
 *   - Telegram topic:  "telegram:<chat_id>:<topic_id>"
 */
@Entity(
    tableName = "course_progress",
    indices = [
        Index(value = ["last_played_at"]),
        Index(value = ["course_type"])
    ]
)
data class CourseProgressEntity(
    /** Unique identifier for the course (see format above). */
    @PrimaryKey
    @ColumnInfo(name = "course_id")
    val courseId: String,

    /** Source type: "LOCAL_FOLDER" | "GDRIVE_FOLDER" | "PLAYLIST" | "TELEGRAM_TOPIC" */
    @ColumnInfo(name = "course_type", defaultValue = "LOCAL_FOLDER")
    val courseType: String = "LOCAL_FOLDER",

    /** Display name of the course (folder name or playlist name). */
    @ColumnInfo(name = "course_name", defaultValue = "")
    val courseName: String = "",

    /** The song_id of the last played / in-progress lesson. */
    @ColumnInfo(name = "current_song_id")
    val currentSongId: String? = null,

    /** Playback position within [currentSongId] in milliseconds. */
    @ColumnInfo(name = "current_position_ms", defaultValue = "0")
    val currentPositionMs: Long = 0L,

    /** Duration of [currentSongId] in milliseconds (snapshot for UI without DB join). */
    @ColumnInfo(name = "current_song_duration_ms", defaultValue = "0")
    val currentSongDurationMs: Long = 0L,

    /** Display title of [currentSongId] (snapshot for UI without DB join). */
    @ColumnInfo(name = "current_song_title", defaultValue = "")
    val currentSongTitle: String = "",

    /** Unix epoch ms when the user last studied this course. Used for "2 days ago" labels. */
    @ColumnInfo(name = "last_played_at", defaultValue = "0")
    val lastPlayedAt: Long = 0L,

    /** Total number of lessons/tracks in this course (snapshot at save time). */
    @ColumnInfo(name = "total_songs", defaultValue = "0")
    val totalSongs: Int = 0,

    /** Number of lessons marked as completed (is_completed = true in song_engagements). */
    @ColumnInfo(name = "completed_songs_count", defaultValue = "0")
    val completedSongsCount: Int = 0,

    /** Unix epoch ms when this progress record was first created. */
    @ColumnInfo(name = "created_at", defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis()
) {
    /** Progress ratio [0.0, 1.0]. Returns 0 if totalSongs is 0. */
    val completionRatio: Float
        get() = if (totalSongs > 0) completedSongsCount.toFloat() / totalSongs else 0f

    /** True if every lesson in the course has been completed. */
    val isCourseCompleted: Boolean
        get() = totalSongs > 0 && completedSongsCount >= totalSongs

    companion object {
        const val TYPE_LOCAL_FOLDER = "LOCAL_FOLDER"
        const val TYPE_GDRIVE_FOLDER = "GDRIVE_FOLDER"
        const val TYPE_PLAYLIST = "PLAYLIST"
        const val TYPE_TELEGRAM_TOPIC = "TELEGRAM_TOPIC"

        fun localFolderCourseId(folderPath: String): String = "local:$folderPath"
        fun gdriveFolderCourseId(folderId: String): String = "gdrive:$folderId"
        fun playlistCourseId(playlistId: String): String = "playlist:$playlistId"
        fun telegramCourseId(chatId: Long, topicId: Long): String = "telegram:$chatId:$topicId"
    }
}
