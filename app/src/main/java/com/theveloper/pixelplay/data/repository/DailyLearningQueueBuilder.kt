package com.theveloper.pixelplay.data.repository

import com.theveloper.pixelplay.data.database.CourseProgressEntity
import com.theveloper.pixelplay.data.model.Playlist
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.PlaylistPreferencesRepository
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

data class PlannedLearningSession(
    val songs: List<Song>,
    val startSong: Song,
    val startPositionMs: Long = 0L,
    val sessionTitle: String,
    val sourceCourseId: String? = null
)

/**
 * Builds the daily learning queue based on:
 * 1. Daily goal targets if configured.
 * 2. Continuing playback from the last-played course if no goal is set.
 * 3. Chaining user-selected priority playlists (#1, #2, #3) after goal tracks.
 */
@Singleton
class DailyLearningQueueBuilder @Inject constructor(
    private val courseProgressRepository: CourseProgressRepository,
    private val playlistPreferencesRepository: PlaylistPreferencesRepository,
    private val musicRepository: MusicRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    suspend fun buildLearningQueue(): PlannedLearningSession? {
        val allPlaylists = playlistPreferencesRepository.userPlaylistsFlow.first()
        if (allPlaylists.isEmpty()) return null

        val allSongsMap = musicRepository.getAllSongsOnce().associateBy { it.id }
        val playlistMap = allPlaylists.associateBy { it.id }
        val dailyGoalTargets = userPreferencesRepository.dailyGoalCourseTargetsFlow.first()
        val priorityCourseIds = userPreferencesRepository.priorityCourseIdsFlow.first()

        // ────────────────────────────────────────────────────────────────────
        // CASE A: User has NOT set any daily goal targets (or goal list is empty)
        // ────────────────────────────────────────────────────────────────────
        if (dailyGoalTargets.isEmpty()) {
            return buildContinueLearningSession(
                allPlaylists = allPlaylists,
                playlistMap = playlistMap,
                allSongsMap = allSongsMap,
                priorityCourseIds = priorityCourseIds
            )
        }

        // ────────────────────────────────────────────────────────────────────
        // CASE B: User HAS set daily goals
        // 1. Gather today's target tracks for each planned course (continuing from progress).
        // 2. Append priority courses (#1, #2, #3) to smoothly transition once goals finish.
        // ────────────────────────────────────────────────────────────────────
        val goalSongs = mutableListOf<Song>()
        var primaryStartSong: Song? = null
        var primaryStartPositionMs = 0L
        var firstCourseId: String? = null

        for ((courseId, targetCount) in dailyGoalTargets) {
            val playlist = playlistMap[courseId] ?: continue
            val courseSongs = playlist.songIds.mapNotNull { allSongsMap[it] }
            if (courseSongs.isEmpty()) continue

            val progress = courseProgressRepository.getProgress(courseId)
            val (startIndex, startPos) = resolveCourseResumePoint(courseSongs, progress)

            if (firstCourseId == null) {
                firstCourseId = courseId
            }

            val countToTake = targetCount.coerceAtLeast(1)
            for (i in 0 until countToTake) {
                val songIndex = (startIndex + i) % courseSongs.size
                val song = courseSongs[songIndex]
                if (goalSongs.none { it.id == song.id }) {
                    if (primaryStartSong == null) {
                        primaryStartSong = song
                        primaryStartPositionMs = if (i == 0) startPos else 0L
                    }
                    goalSongs.add(song)
                }
            }
        }

        // Fallback to continue learning if no goal songs found
        if (goalSongs.isEmpty()) {
            return buildContinueLearningSession(
                allPlaylists = allPlaylists,
                playlistMap = playlistMap,
                allSongsMap = allSongsMap,
                priorityCourseIds = priorityCourseIds
            )
        }

        // Append priority playlists in order (#1, #2, #3)
        val chainedSongs = ArrayList<Song>(goalSongs)
        for (priorityId in priorityCourseIds) {
            val priorityPlaylist = playlistMap[priorityId] ?: continue
            val prioritySongs = priorityPlaylist.songIds.mapNotNull { allSongsMap[it] }
            if (prioritySongs.isEmpty()) continue

            val progress = courseProgressRepository.getProgress(priorityId)
            val (priorityStartIndex, _) = resolveCourseResumePoint(prioritySongs, progress)

            for (i in prioritySongs.indices) {
                val songIndex = (priorityStartIndex + i) % prioritySongs.size
                val song = prioritySongs[songIndex]
                if (chainedSongs.none { it.id == song.id }) {
                    chainedSongs.add(song)
                }
            }
        }

        val startSong = primaryStartSong ?: chainedSongs.first()
        return PlannedLearningSession(
            songs = chainedSongs,
            startSong = startSong,
            startPositionMs = primaryStartPositionMs,
            sessionTitle = "Today's Learning Plan (${goalSongs.size} tracks)",
            sourceCourseId = firstCourseId
        )
    }

    private suspend fun buildContinueLearningSession(
        allPlaylists: List<Playlist>,
        playlistMap: Map<String, Playlist>,
        allSongsMap: Map<String, Song>,
        priorityCourseIds: List<String>
    ): PlannedLearningSession? {
        val recentProgress = courseProgressRepository.getRecentlyPlayedCourses(1).firstOrNull()
        var targetPlaylist: Playlist? = null

        if (recentProgress != null) {
            targetPlaylist = playlistMap[recentProgress.courseId]
        }

        // Fallback to priority courses #1, #2, #3
        if (targetPlaylist == null) {
            for (priorityId in priorityCourseIds) {
                val p = playlistMap[priorityId]
                if (p != null) {
                    targetPlaylist = p
                    break
                }
            }
        }

        // Fallback to first available course
        if (targetPlaylist == null) {
            targetPlaylist = allPlaylists.firstOrNull() ?: return null
        }

        val songs = targetPlaylist.songIds.mapNotNull { allSongsMap[it] }
        if (songs.isEmpty()) return null

        val progress = courseProgressRepository.getProgress(targetPlaylist.id)
        val (startIndex, startPos) = resolveCourseResumePoint(songs, progress)
        val startSong = songs.getOrElse(startIndex) { songs.first() }

        return PlannedLearningSession(
            songs = songs,
            startSong = startSong,
            startPositionMs = startPos,
            sessionTitle = "Continue: ${targetPlaylist.name}",
            sourceCourseId = targetPlaylist.id
        )
    }

    private fun resolveCourseResumePoint(
        songs: List<Song>,
        progress: CourseProgressEntity?
    ): Pair<Int, Long> {
        if (progress == null || songs.isEmpty()) return 0 to 0L

        val lastIndex = songs.indexOfFirst { it.id == progress.currentSongId }
        if (lastIndex == -1) return 0 to 0L

        val duration = songs[lastIndex].duration
        val position = progress.currentPositionMs

        val isFinished = duration > 0L && (position >= duration * 0.95f || (duration - position) <= 5_000L)
        return if (isFinished) {
            ((lastIndex + 1) % songs.size) to 0L
        } else {
            lastIndex to position
        }
    }
}
