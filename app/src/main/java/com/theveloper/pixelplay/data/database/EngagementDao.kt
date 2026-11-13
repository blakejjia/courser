package com.theveloper.pixelplay.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * DAO for song engagement statistics.
 * Provides efficient database operations for tracking play counts and durations.
 */
@Dao
interface EngagementDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEngagement(engagement: SongEngagementEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEngagements(engagements: List<SongEngagementEntity>)

    @Query("SELECT * FROM song_engagements WHERE song_id = :songId")
    suspend fun getEngagement(songId: String): SongEngagementEntity?

    @Query("SELECT * FROM song_engagements")
    suspend fun getAllEngagements(): List<SongEngagementEntity>

    @Query("SELECT * FROM song_engagements")
    fun getAllEngagementsFlow(): Flow<List<SongEngagementEntity>>

    @Query("SELECT play_count FROM song_engagements WHERE song_id = :songId")
    suspend fun getPlayCount(songId: String): Int?

    @Query("DELETE FROM song_engagements WHERE song_id = :songId")
    suspend fun deleteEngagement(songId: String)

    @Query("DELETE FROM song_engagements WHERE song_id NOT IN (SELECT CAST(id AS TEXT) FROM songs)")
    suspend fun deleteOrphanedEngagements()

    @Query("DELETE FROM song_engagements")
    suspend fun clearAllEngagements()

    /**
     * Increments play count and updates last played timestamp atomically.
     * More efficient than read-modify-write pattern.
     */
    @Query("""
        INSERT INTO song_engagements (song_id, play_count, total_play_duration_ms, last_played_timestamp,
            last_resume_position_ms, is_completed, completion_count, last_completed_timestamp, net_listened_ms)
        VALUES (:songId, 1, :durationMs, :timestamp, 0, 0, 0, 0, :durationMs)
        ON CONFLICT(song_id) DO UPDATE SET
            play_count = play_count + 1,
            total_play_duration_ms = total_play_duration_ms + :durationMs,
            last_played_timestamp = :timestamp,
            net_listened_ms = net_listened_ms + :durationMs
    """)
    suspend fun recordPlay(songId: String, durationMs: Long, timestamp: Long)

    /**
     * Atomically updates resume position for a track (called on pause / track-change).
     */
    @Query("""
        INSERT INTO song_engagements (song_id, play_count, total_play_duration_ms, last_played_timestamp,
            last_resume_position_ms, is_completed, completion_count, last_completed_timestamp, net_listened_ms)
        VALUES (:songId, 0, 0, 0, :positionMs, 0, 0, 0, 0)
        ON CONFLICT(song_id) DO UPDATE SET
            last_resume_position_ms = :positionMs,
            last_played_timestamp = MAX(last_played_timestamp, :timestamp)
    """)
    suspend fun updateResumePosition(songId: String, positionMs: Long, timestamp: Long)

    /**
     * Mark a track as completed. Increments completion_count, clears resume position.
     */
    @Query("""
        INSERT INTO song_engagements (song_id, play_count, total_play_duration_ms, last_played_timestamp,
            last_resume_position_ms, is_completed, completion_count, last_completed_timestamp, net_listened_ms)
        VALUES (:songId, 1, :totalDurationMs, :timestamp, 0, 1, 1, :timestamp, :netListenedMs)
        ON CONFLICT(song_id) DO UPDATE SET
            is_completed = 1,
            completion_count = completion_count + 1,
            last_completed_timestamp = :timestamp,
            last_resume_position_ms = 0,
            last_played_timestamp = MAX(last_played_timestamp, :timestamp),
            net_listened_ms = net_listened_ms + :netListenedMs
    """)
    suspend fun markCompleted(songId: String, totalDurationMs: Long, timestamp: Long, netListenedMs: Long)

    /**
     * Update net listened ms (cumulative unique ms heard).
     */
    @Query("""
        INSERT INTO song_engagements (song_id, play_count, total_play_duration_ms, last_played_timestamp,
            last_resume_position_ms, is_completed, completion_count, last_completed_timestamp, net_listened_ms)
        VALUES (:songId, 0, 0, 0, 0, 0, 0, 0, :additionalMs)
        ON CONFLICT(song_id) DO UPDATE SET
            net_listened_ms = net_listened_ms + :additionalMs
    """)
    suspend fun addNetListenedMs(songId: String, additionalMs: Long)

    /** Get resume position for a specific song. Returns null if no record. */
    @Query("SELECT last_resume_position_ms FROM song_engagements WHERE song_id = :songId")
    suspend fun getResumePosition(songId: String): Long?

    /** Get all completed songs (is_completed = 1). */
    @Query("SELECT * FROM song_engagements WHERE is_completed = 1 ORDER BY last_completed_timestamp DESC")
    suspend fun getCompletedSongs(): List<SongEngagementEntity>

    /** Count completed songs for a list of songIds (used for course progress %). */
    @Query("SELECT COUNT(*) FROM song_engagements WHERE song_id IN (:songIds) AND is_completed = 1")
    suspend fun countCompletedInList(songIds: List<String>): Int

    /**
     * Get top songs by play count for quick access.
     */
    @Query("SELECT * FROM song_engagements ORDER BY play_count DESC LIMIT :limit")
    suspend fun getTopPlayedSongs(limit: Int): List<SongEngagementEntity>

    /**
     * Get recently played songs ordered by last played timestamp.
     */
    @Query("SELECT * FROM song_engagements WHERE last_played_timestamp > 0 ORDER BY last_played_timestamp DESC LIMIT :limit")
    suspend fun getRecentlyPlayedSongs(limit: Int): List<SongEngagementEntity>

    @Transaction
    suspend fun replaceAll(engagements: List<SongEngagementEntity>) {
        clearAllEngagements()
        if (engagements.isNotEmpty()) upsertEngagements(engagements)
    }
}
