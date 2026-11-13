package com.theveloper.pixelplay.data.service

import androidx.media3.common.Player
import com.theveloper.pixelplay.data.database.CourseProgressEntity
import com.theveloper.pixelplay.data.repository.CourseProgressRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates writing course progress from MusicService player events.
 *
 * Call order:
 * 1. [setCourseContext] when the user opens/resumes a course.
 * 2. [onTrackStarted]  when a new track begins.
 * 3. [onPlayPause]     on every isPlaying change.
 * 4. [onTrackEnding]   when the track approaches its end (called by MusicService from onPlaybackStateChanged STATE_ENDED or onPositionDiscontinuity AUTO_TRANSITION).
 * 5. [onDestroy]       in MusicService.onDestroy() to flush and cancel the heartbeat.
 */
@Singleton
class CourseProgressTracker @Inject constructor(
    private val courseProgressRepository: CourseProgressRepository,
) {
    companion object {
        private const val TAG = "CourseProgressTracker"
        private const val HEARTBEAT_INTERVAL_MS = 30_000L
    }

    // Current active course context (nullable — only set when user is in a course)
    private var currentCourseId: String? = null
    private var currentCourseType: String = CourseProgressEntity.TYPE_LOCAL_FOLDER
    private var currentCourseName: String = ""
    private var currentCourseTotalSongs: Int = 0
    private var currentCourseSongIds: List<String> = emptyList()

    // Current track state
    private var currentSongId: String? = null
    private var currentSongTitle: String = ""
    private var currentSongDurationMs: Long = 0L

    private val trackerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var heartbeatJob: Job? = null

    // ── Course context ────────────────────────────────────────────────────────

    /**
     * Set (or update) the course context. Call this when the user taps a course
     * in the Library or Today's Learn screen to load a playlist from a course folder.
     */
    fun setCourseContext(
        courseId: String,
        courseType: String,
        courseName: String,
        songIds: List<String>,
    ) {
        currentCourseId = courseId
        currentCourseType = courseType
        currentCourseName = courseName
        currentCourseSongIds = songIds
        currentCourseTotalSongs = songIds.size
        Timber.tag(TAG).d("Course context set: id=%s name=%s songs=%d", courseId, courseName, songIds.size)
    }

    /** Clear the course context (e.g. user switches to non-course playback). */
    fun clearCourseContext() {
        stopHeartbeat()
        currentCourseId = null
        currentCourseSongIds = emptyList()
        currentCourseTotalSongs = 0
        Timber.tag(TAG).d("Course context cleared")
    }

    // ── Player event hooks (called from MusicService.playerListener) ──────────

    /**
     * Called when a new track starts playing within the current course.
     */
    fun onTrackStarted(
        songId: String,
        songTitle: String,
        durationMs: Long,
        player: Player,
        fallbackCourseId: String? = null,
        fallbackCourseType: String = CourseProgressEntity.TYPE_LOCAL_FOLDER,
        fallbackCourseName: String = "",
    ) {
        currentSongId = songId
        currentSongTitle = songTitle
        currentSongDurationMs = durationMs
        if (currentCourseId == null && !fallbackCourseId.isNullOrBlank()) {
            currentCourseId = fallbackCourseId
            currentCourseType = fallbackCourseType
            currentCourseName = fallbackCourseName
        }
        if (player.isPlaying) {
            startHeartbeat(player)
        }
    }

    /**
     * Called from onIsPlayingChanged. Starts or stops the heartbeat accordingly.
     */
    fun onPlayPause(isPlaying: Boolean, player: Player) {
        if (currentSongId == null) return
        if (isPlaying) {
            startHeartbeat(player)
        } else {
            stopHeartbeat()
            flushPosition(player)
        }
    }

    /**
     * Called when a track ends (STATE_ENDED or AUTO_TRANSITION outgoing).
     * Writes the completion record if the threshold was reached.
     *
     * @param songId        The song that ended.
     * @param finalPositionMs The player position just before the transition.
     * @param durationMs    The track's total duration.
     * @param netListenedMs Actual ms heard (from ListeningStatsTracker accumulation).
     */
    fun onTrackEnding(
        songId: String,
        finalPositionMs: Long,
        durationMs: Long,
        netListenedMs: Long,
    ) {
        val courseId = currentCourseId
        val songIds = currentCourseSongIds

        trackerScope.launch {
            val completed = courseProgressRepository.isCompletionThresholdReached(finalPositionMs, durationMs)
            if (completed) {
                courseProgressRepository.markTrackCompleted(
                    songId = songId,
                    totalDurationMs = durationMs,
                    netListenedMs = netListenedMs,
                    courseId = courseId,
                    songIds = songIds,
                )
            } else {
                // Not completed — save mid-track position
                saveCurrentPosition(player = null, positionMs = finalPositionMs)
            }
        }
    }

    // ── MusicService lifecycle ────────────────────────────────────────────────

    /** Call from MusicService.onDestroy() to flush current state synchronously-ish. */
    fun onDestroy(player: Player?) {
        stopHeartbeat()
        if (player != null) {
            flushPosition(player)
        }
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private fun startHeartbeat(player: Player) {
        if (heartbeatJob?.isActive == true) return
        heartbeatJob = trackerScope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                if (!isActive) break
                flushPosition(player)
            }
        }
        Timber.tag(TAG).v("Heartbeat started")
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun flushPosition(player: Player) {
        val songId = currentSongId ?: return
        val positionMs = try { player.currentPosition.coerceAtLeast(0L) } catch (_: Exception) { 0L }
        saveCurrentPosition(player, positionMs)
    }

    private fun saveCurrentPosition(player: Player?, positionMs: Long) {
        val songId = currentSongId ?: return

        // Try to get position from player if positionMs is 0 and player is available
        val finalPosition = if (positionMs > 0L) positionMs else {
            try { player?.currentPosition?.coerceAtLeast(0L) ?: 0L } catch (_: Exception) { 0L }
        }

        val courseId = currentCourseId
        trackerScope.launch {
            if (courseId != null) {
                courseProgressRepository.saveCursor(
                    courseId = courseId,
                    courseType = currentCourseType,
                    courseName = currentCourseName,
                    songId = songId,
                    positionMs = finalPosition,
                    songDurationMs = currentSongDurationMs,
                    songTitle = currentSongTitle,
                    totalSongs = currentCourseTotalSongs,
                    songIds = currentCourseSongIds,
                )
            } else {
                courseProgressRepository.saveTrackResumePosition(songId, finalPosition)
            }
        }
    }
}
