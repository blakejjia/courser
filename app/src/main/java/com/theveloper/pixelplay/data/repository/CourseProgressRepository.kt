package com.theveloper.pixelplay.data.repository

import com.theveloper.pixelplay.data.database.CourseProgressDao
import com.theveloper.pixelplay.data.database.CourseProgressEntity
import com.theveloper.pixelplay.data.database.EngagementDao
import com.theveloper.pixelplay.data.database.SongEngagementEntity
import com.theveloper.pixelplay.data.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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

    suspend fun findProgress(courseId: String, courseName: String = ""): CourseProgressEntity? =
        courseProgressDao.findProgress(courseId, courseName)

    /**
     * Resolves the starting song and starting position (ms) to resume a course.
     * Looks at course progress first, then individual song engagement records.
     */
    suspend fun resolveCourseResume(
        courseId: String,
        courseName: String,
        songs: List<Song>
    ): Pair<Song, Long> {
        if (songs.isEmpty()) return Song.emptySong() to 0L

        // 1. Try finding course-level progress
        val progress = findProgress(courseId, courseName)
        val candidateSongId = progress?.currentSongId

        if (candidateSongId != null) {
            val idx = songs.indexOfFirst { it.id == candidateSongId }
            if (idx >= 0) {
                val candidateSong = songs[idx]
                val duration = candidateSong.duration
                val pos = progress.currentPositionMs.coerceAtLeast(0L)

                // If track is finished (>= 90% or within 5s of end), advance to next track at 0ms
                val isFinished = duration > 0L && (pos >= duration * 0.90f || (duration - pos) <= 5_000L)
                return if (isFinished) {
                    val nextIdx = (idx + 1) % songs.size
                    songs[nextIdx] to 0L
                } else {
                    candidateSong to pos
                }
            }
        }

        // 2. If no course progress, check individual song engagement bookmarks
        val engagements = getSongEngagements(songs.map { it.id })
        val inProgressSong = songs.firstOrNull { song ->
            val eng = engagements[song.id]
            eng != null && !eng.isCompleted && eng.lastResumePositionMs > 0L
        }
        if (inProgressSong != null) {
            val eng = engagements[inProgressSong.id]
            val pos = eng?.lastResumePositionMs ?: 0L
            return inProgressSong to pos
        }

        // 3. Find the first uncompleted song in the course
        val firstUncompleted = songs.firstOrNull { song ->
            val eng = engagements[song.id]
            eng == null || !eng.isCompleted
        }
        if (firstUncompleted != null) {
            val eng = engagements[firstUncompleted.id]
            val pos = eng?.lastResumePositionMs ?: 0L
            return firstUncompleted to pos
        }

        // 4. Default: first song at 0ms
        return songs.first() to 0L
    }

    suspend fun getRecentlyPlayedCourses(limit: Int = 10): List<CourseProgressEntity> =
        courseProgressDao.getRecentlyPlayedCourses(limit)

    suspend fun getResumePosition(songId: String): Long? =
        engagementDao.getResumePosition(songId)

    suspend fun getSongEngagement(songId: String): SongEngagementEntity? =
        engagementDao.getEngagement(songId)

    suspend fun getSongEngagements(songIds: List<String>): Map<String, SongEngagementEntity> {
        if (songIds.isEmpty()) return emptyMap()
        return try {
            engagementDao.getEngagementsForSongs(songIds).associateBy { it.songId }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to load song engagements")
            emptyMap()
        }
    }

    fun observeSongEngagements(songIds: List<String>): Flow<Map<String, SongEngagementEntity>> =
        engagementDao.observeEngagementsForSongs(songIds).map { list -> list.associateBy { it.songId } }

    /**
     * Enriches a list of [Song]s with their individual learning progress,
     * including last played timestamp, resume position in ms, and completion status.
     */
    suspend fun enrichSongsWithProgress(songs: List<Song>): List<Song> {
        if (songs.isEmpty()) return songs
        val engagements = getSongEngagements(songs.map { it.id })
        return songs.map { song ->
            val engagement = engagements[song.id]
            if (engagement != null) {
                song.copy(
                    lastPlayedAt = engagement.lastPlayedTimestamp.takeIf { it > 0L },
                    resumePositionMs = engagement.lastResumePositionMs,
                    isCompleted = engagement.isCompleted
                )
            } else {
                song
            }
        }
    }

    suspend fun enrichSongWithProgress(song: Song): Song {
        val engagement = getSongEngagement(song.id) ?: return song
        return song.copy(
            lastPlayedAt = engagement.lastPlayedTimestamp.takeIf { it > 0L },
            resumePositionMs = engagement.lastResumePositionMs,
            isCompleted = engagement.isCompleted
        )
    }

    // ── Write: cursor update (call on pause / track-change / 30s heartbeat) ──

    /**
     * Save just the per-track resume bookmark in [EngagementDao].
     */
    suspend fun saveTrackResumePosition(songId: String, positionMs: Long, durationMs: Long = 0L) {
        val nowMs = System.currentTimeMillis()
        val isCompleted = durationMs > 0L && isCompletionThresholdReached(positionMs, durationMs)
        try {
            engagementDao.updateResumePosition(
                songId = songId,
                positionMs = positionMs,
                timestamp = nowMs,
                isCompleted = isCompleted,
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
            val isCompleted = songDurationMs > 0L && isCompletionThresholdReached(positionMs, songDurationMs)
            engagementDao.updateResumePosition(
                songId = songId,
                positionMs = positionMs,
                timestamp = nowMs,
                isCompleted = isCompleted,
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
