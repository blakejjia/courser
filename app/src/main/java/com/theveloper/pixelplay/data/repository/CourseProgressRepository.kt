package com.theveloper.pixelplay.data.repository

import com.theveloper.pixelplay.data.database.CourseProgressDao
import com.theveloper.pixelplay.data.database.CourseProgressEntity
import com.theveloper.pixelplay.data.database.EngagementDao
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for reading and writing course-level learning progress.
 *
 * A "course" in Courser is any folder (local or cloud) or playlist that
 * the user treats as a learning unit. This repository tracks:
 *  - Which lesson is currently in progress and at what position
 *  - When the user last studied ("2 days ago")
 *  - How many lessons have been completed (for the % progress bar)
 */
@Singleton
class CourseProgressRepository @Inject constructor(
    private val courseProgressDao: CourseProgressDao,
    private val engagementDao: EngagementDao,
) {
    companion object {
        private const val TAG = "CourseProgressRepo"
        /** Mark a track completed when this fraction of its duration has been heard. */
        const val COMPLETION_THRESHOLD = 0.90f
    }

    // ── Observe ───────────────────────────────────────────────────────────────

    fun observeProgress(courseId: String): Flow<CourseProgressEntity?> =
        courseProgressDao.observeProgress(courseId)

    fun observeAllProgress(): Flow<List<CourseProgressEntity>> =
        courseProgressDao.observeAllProgress()

    suspend fun getProgress(courseId: String): CourseProgressEntity? =
        courseProgressDao.getProgress(courseId)

    suspend fun getRecentlyPlayedCourses(limit: Int = 10): List<CourseProgressEntity> =
        courseProgressDao.getRecentlyPlayedCourses(limit)

    suspend fun getResumePosition(songId: String): Long? =
        engagementDao.getResumePosition(songId)

    // ── Write: cursor update (call on pause / track-change / 30s heartbeat) ──

    /**
     * Save just the per-track resume bookmark in [EngagementDao].
     */
    suspend fun saveTrackResumePosition(songId: String, positionMs: Long) {
        val nowMs = System.currentTimeMillis()
        try {
            engagementDao.updateResumePosition(
                songId = songId,
                positionMs = positionMs,
                timestamp = nowMs,
            )
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to save track resume position for songId=%s", songId)
        }
    }

    /**
     * Persist the user's current playback position within a course.
     * Also persists a per-track resume bookmark in [EngagementDao].
     *
     * Call this on:
     *  - Pause / audio-focus loss
     *  - Track change (write position of the *outgoing* track)
     *  - Every 30 s while playing (heartbeat for force-kill protection)
     *  - App going to background (onStop)
     */
    suspend fun saveCursor(
        courseId: String,
        courseType: String,
        courseName: String,
        songId: String,
        positionMs: Long,
        songDurationMs: Long,
        songTitle: String,
        totalSongs: Int,
        songIds: List<String> = emptyList(), // pass to refresh completedCount
    ) {
        val nowMs = System.currentTimeMillis()
        try {
            // Update the course-level cursor
            courseProgressDao.updateCursor(
                courseId = courseId,
                courseType = courseType,
                courseName = courseName,
                songId = songId,
                positionMs = positionMs,
                songDurationMs = songDurationMs,
                songTitle = songTitle,
                totalSongs = totalSongs,
                nowMs = nowMs,
            )

            // Update the per-track resume bookmark
            engagementDao.updateResumePosition(
                songId = songId,
                positionMs = positionMs,
                timestamp = nowMs,
            )

            // If we have the full song list, refresh the completed count
            if (songIds.isNotEmpty()) {
                refreshCompletedCount(courseId, songIds)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to save course cursor for courseId=%s", courseId)
        }
    }

    // ── Write: completion ──────────────────────────────────────────────────────

    /**
     * Mark a track as completed and update the course progress accordingly.
     *
     * @param songId        The finished lesson's ID.
     * @param totalDurationMs The track's full duration (for net_listened_ms accounting).
     * @param netListenedMs Actual ms heard this session.
     * @param courseId      The course this lesson belongs to (optional — skips course update if null).
     * @param songIds       All song IDs in the course (for recomputing completedSongsCount).
     */
    suspend fun markTrackCompleted(
        songId: String,
        totalDurationMs: Long,
        netListenedMs: Long,
        courseId: String? = null,
        songIds: List<String> = emptyList(),
    ) {
        val nowMs = System.currentTimeMillis()
        try {
            engagementDao.markCompleted(
                songId = songId,
                totalDurationMs = totalDurationMs,
                timestamp = nowMs,
                netListenedMs = netListenedMs,
            )
            Timber.tag(TAG).d("Marked song %s as completed", songId)

            if (courseId != null && songIds.isNotEmpty()) {
                refreshCompletedCount(courseId, songIds)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to mark track %s completed", songId)
        }
    }

    /**
     * Recomputes and persists [CourseProgressEntity.completedSongsCount] for [courseId]
     * by querying [EngagementDao] for all completed song IDs in [songIds].
     */
    suspend fun refreshCompletedCount(courseId: String, songIds: List<String>) {
        if (songIds.isEmpty()) return
        try {
            val completed = engagementDao.countCompletedInList(songIds)
            courseProgressDao.updateCompletedCount(courseId, completed)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to refresh completed count for courseId=%s", courseId)
        }
    }

    /**
     * Returns true if the given position exceeds the completion threshold for the duration.
     * Use this to decide when to call [markTrackCompleted].
     */
    fun isCompletionThresholdReached(positionMs: Long, durationMs: Long): Boolean {
        if (durationMs <= 0L) return false
        return (positionMs.toFloat() / durationMs) >= COMPLETION_THRESHOLD
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    suspend fun deleteProgress(courseId: String) =
        courseProgressDao.deleteProgress(courseId)

    suspend fun clearAll() =
        courseProgressDao.clearAll()
}
