package com.theveloper.pixelplay.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseProgressDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: CourseProgressEntity)

    @Query("SELECT * FROM course_progress WHERE course_id = :courseId")
    suspend fun getProgress(courseId: String): CourseProgressEntity?

    @Query("SELECT * FROM course_progress WHERE course_id = :courseId")
    fun observeProgress(courseId: String): Flow<CourseProgressEntity?>

    /** All courses with any progress, most recently played first. */
    @Query("SELECT * FROM course_progress WHERE last_played_at > 0 ORDER BY last_played_at DESC")
    fun observeAllProgress(): Flow<List<CourseProgressEntity>>

    @Query("SELECT * FROM course_progress WHERE last_played_at > 0 ORDER BY last_played_at DESC LIMIT :limit")
    suspend fun getRecentlyPlayedCourses(limit: Int): List<CourseProgressEntity>

    /** Update just the playback cursor — called on pause/track-change. O(1). */
    @Query("""
        INSERT INTO course_progress (course_id, course_type, course_name, current_song_id,
            current_position_ms, current_song_duration_ms, current_song_title,
            last_played_at, total_songs, completed_songs_count, created_at)
        VALUES (:courseId, :courseType, :courseName, :songId,
            :positionMs, :songDurationMs, :songTitle,
            :nowMs, :totalSongs, 0, :nowMs)
        ON CONFLICT(course_id) DO UPDATE SET
            current_song_id = :songId,
            current_position_ms = :positionMs,
            current_song_duration_ms = :songDurationMs,
            current_song_title = :songTitle,
            last_played_at = :nowMs,
            total_songs = :totalSongs
    """)
    suspend fun updateCursor(
        courseId: String,
        courseType: String,
        courseName: String,
        songId: String,
        positionMs: Long,
        songDurationMs: Long,
        songTitle: String,
        totalSongs: Int,
        nowMs: Long
    )

    /** Refresh the completed_songs_count — call after marking a track completed. */
    @Query("""
        UPDATE course_progress
        SET completed_songs_count = :completedCount
        WHERE course_id = :courseId
    """)
    suspend fun updateCompletedCount(courseId: String, completedCount: Int)

    @Query("DELETE FROM course_progress WHERE course_id = :courseId")
    suspend fun deleteProgress(courseId: String)

    @Query("DELETE FROM course_progress")
    suspend fun clearAll()
}
