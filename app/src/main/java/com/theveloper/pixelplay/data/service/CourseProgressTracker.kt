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
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Coordinates writing course progress from MusicService player events.
 *
 * Implements a 2-tier persistence strategy:
 * - In-memory track state and safe Main-thread position sampling.
 * - Event-driven immediate persistence on milestone events: pause, track transition, track completion, service destroy.
 * - Throttled 15s periodic checkpoint (heartbeat) during continuous playback, only saving when delta >= 3000ms.
 * - Debounced seek (500ms) to avoid SQLite contention while scrubbing.
 */
@Singleton
class CourseProgressTracker @Inject constructor(
    private val courseProgressRepository: CourseProgressRepository,
) {
    companion object {
        private const val TAG = "CourseProgressTracker"
        const val HEARTBEAT_INTERVAL_MS = 15_000L
        const val MIN_HEARTBEAT_DELTA_MS = 3_000L
        const val SEEK_DEBOUNCE_MS = 500L
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

    // Last persisted position in ms to avoid redundant writes
    private var lastPersistedPositionMs: Long = -1L

    private val trackerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var heartbeatJob: Job? = null
    private var seekDebounceJob: Job? = null

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
        seekDebounceJob?.cancel()
        currentCourseId = null
        currentCourseSongIds = emptyList()
        currentCourseTotalSongs = 0
        lastPersistedPositionMs = -1L
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
        seekDebounceJob?.cancel()
        currentSongId = songId
        currentSongTitle = songTitle
        currentSongDurationMs = durationMs
        lastPersistedPositionMs = -1L

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
     * Called when user seeks within the current track.
     * Uses a debounce window to prevent DB thrashing while the user scrubs.
     */
    fun onSeek(positionMs: Long) {
        if (currentSongId == null) return
        seekDebounceJob?.cancel()
        seekDebounceJob = trackerScope.launch {
            delay(SEEK_DEBOUNCE_MS)
            saveCurrentPosition(positionMs = positionMs, isHeartbeat = false)
        }
    }

    /**
     * Called when leaving/transitioning away from the active track.
     * Finalizes the outgoing track's progress before switching to the next track.
     * Evaluates completion threshold (>= 90% or within 5s of end).
     */
    fun onTrackLeaving(
        songId: String,
        finalPositionMs: Long,
        durationMs: Long,
        songTitle: String = currentSongTitle,
        netListenedMs: Long = 0L,
    ) {
        seekDebounceJob?.cancel()
        stopHeartbeat()

        val courseId = currentCourseId
        val songIds = currentCourseSongIds
        val effectiveDuration = if (durationMs > 0L) durationMs else currentSongDurationMs
        val effectivePosition = finalPositionMs.coerceAtLeast(0L)

        trackerScope.launch {
            val completed = courseProgressRepository.isCompletionThresholdReached(effectivePosition, effectiveDuration)
            if (completed) {
                courseProgressRepository.markTrackCompleted(
                    songId = songId,
                    totalDurationMs = effectiveDuration,
                    netListenedMs = if (netListenedMs > 0L) netListenedMs else effectiveDuration,
                    courseId = courseId,
                    songIds = songIds,
                )
                Timber.tag(TAG).d("Track marked completed on leaving: songId=%s pos=%d dur=%d", songId, effectivePosition, effectiveDuration)
            } else if (effectivePosition > 0L) {
                if (courseId != null) {
                    courseProgressRepository.saveCursor(
                        courseId = courseId,
                        courseType = currentCourseType,
                        courseName = currentCourseName,
                        songId = songId,
                        positionMs = effectivePosition,
                        songDurationMs = effectiveDuration,
                        songTitle = songTitle.ifBlank { currentSongTitle },
                        totalSongs = currentCourseTotalSongs,
                        songIds = songIds,
                        refreshCompleted = true,
                    )
                } else {
                    courseProgressRepository.saveTrackResumePosition(songId, effectivePosition, effectiveDuration)
                }
                Timber.tag(TAG).d("Track progress saved on leaving: songId=%s pos=%d dur=%d", songId, effectivePosition, effectiveDuration)
            }
        }
    }

    /**
     * Backwards-compatible alias for onTrackLeaving.
     */
    fun onTrackEnding(
        songId: String,
        finalPositionMs: Long,
        durationMs: Long,
        netListenedMs: Long,
    ) {
        onTrackLeaving(
            songId = songId,
            finalPositionMs = finalPositionMs,
            durationMs = durationMs,
            songTitle = currentSongTitle,
            netListenedMs = netListenedMs,
        )
    }

    // ── MusicService lifecycle ────────────────────────────────────────────────

    /**
     * Synchronously/immediately commit in-memory position on pause, unload, or service destruction.
     */
    fun flushImmediate(player: Player? = null, fallbackPositionMs: Long? = null) {
        seekDebounceJob?.cancel()
        val songId = currentSongId ?: return
        val pos = fallbackPositionMs ?: run {
            try { player?.currentPosition?.coerceAtLeast(0L) } catch (_: Exception) { null }
        } ?: return

        saveCurrentPosition(positionMs = pos, isHeartbeat = false)
    }

    /** Call from MusicService.onDestroy() to flush current state and cancel timers. */
    fun onDestroy(player: Player?) {
        stopHeartbeat()
        seekDebounceJob?.cancel()
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

                // Safely sample position on Main thread
                val currentPos = withContext(Dispatchers.Main.immediate) {
                    try {
                        if (player.isPlaying) player.currentPosition.coerceAtLeast(0L) else null
                    } catch (_: Exception) {
                        null
                    }
                }

                if (currentPos != null) {
                    val last = lastPersistedPositionMs
                    if (last < 0L || abs(currentPos - last) >= MIN_HEARTBEAT_DELTA_MS) {
                        saveCurrentPosition(positionMs = currentPos, isHeartbeat = true)
                    }
                }
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
        saveCurrentPosition(positionMs = positionMs, isHeartbeat = false)
    }

    private fun saveCurrentPosition(positionMs: Long, isHeartbeat: Boolean) {
        val songId = currentSongId ?: return
        val finalPosition = positionMs.coerceAtLeast(0L)
        lastPersistedPositionMs = finalPosition

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
                    refreshCompleted = !isHeartbeat,
                )
            } else {
                courseProgressRepository.saveTrackResumePosition(songId, finalPosition, currentSongDurationMs)
            }
        }
    }
}
