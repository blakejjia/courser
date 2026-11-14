package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.model.Playlist
import com.theveloper.pixelplay.data.preferences.PlaylistPreferencesRepository
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.repository.CourseProgressRepository
import com.theveloper.pixelplay.data.repository.DailyLearningQueueBuilder
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.repository.PlannedLearningSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlannedCourse(
    val playlist: Playlist,
    val targetCount: Int
)

data class DailyGoalUiState(
    val allCourses: List<Playlist> = emptyList(),
    val courseTargets: Map<String, Int> = emptyMap(), // courseId -> daily target count
    val plannedCourses: List<PlannedCourse> = emptyList(),
    val priorityCourseIds: List<String> = emptyList(),
    val sortedCoursesForAdd: List<Playlist> = emptyList(), // not-in-plan at front, then in-plan
    val keepListeningSession: PlannedLearningSession? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class DailyGoalViewModel @Inject constructor(
    private val playlistPreferencesRepository: PlaylistPreferencesRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val courseProgressRepository: CourseProgressRepository,
    private val dailyLearningQueueBuilder: DailyLearningQueueBuilder,
    private val musicRepository: MusicRepository
) : ViewModel() {

    val uiState: StateFlow<DailyGoalUiState> = combine(
        playlistPreferencesRepository.userPlaylistsFlow,
        userPreferencesRepository.dailyGoalCourseTargetsFlow,
        userPreferencesRepository.priorityCourseIdsFlow,
        courseProgressRepository.observeAllProgress()
    ) { allPlaylists, targets, priorityIds, _ ->
        // Planned courses (only courses explicitly added with a target)
        val planned = targets.mapNotNull { (courseId, count) ->
            allPlaylists.find { it.id == courseId }?.let { playlist ->
                PlannedCourse(playlist = playlist, targetCount = count)
            }
        }

        // Available courses sorted: not-in-plan first, then in-plan
        val (inPlan, notInPlan) = allPlaylists.partition { it.id in targets.keys }
        val sortedForAdd = notInPlan + inPlan

        val keepListening = dailyLearningQueueBuilder.buildLearningQueue()

        DailyGoalUiState(
            allCourses = allPlaylists,
            courseTargets = targets,
            plannedCourses = planned,
            priorityCourseIds = priorityIds,
            sortedCoursesForAdd = sortedForAdd,
            keepListeningSession = keepListening,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DailyGoalUiState()
    )

    fun setCourseTarget(courseId: String, targetCount: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setCourseDailyTarget(courseId, targetCount.coerceAtLeast(1))
        }
    }

    fun removeCourseFromGoal(courseId: String) {
        viewModelScope.launch {
            userPreferencesRepository.removeCourseFromDailyGoal(courseId)
        }
    }

    fun setPriorityCourses(courseIds: List<String>) {
        viewModelScope.launch {
            userPreferencesRepository.setPriorityCourseIds(courseIds)
        }
    }

    /**
     * Executes the daily learning algorithm:
     * - If goals are set, queues today's goal tracks + pinned priority course.
     * - If no goal is set, queues "Continue Learning" from where the last course left off.
     */
    fun startLearning(
        playerViewModel: PlayerViewModel,
        onSessionStarted: ((String) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val session = dailyLearningQueueBuilder.buildLearningQueue() ?: return@launch
            if (session.songs.isNotEmpty()) {
                playerViewModel.playSongs(
                    songsToPlay = session.songs,
                    startSong = session.startSong,
                    queueName = session.sessionTitle,
                    playlistId = session.sourceCourseId,
                    startPositionMs = session.startPositionMs
                )
                onSessionStarted?.invoke(session.sessionTitle)
            }
        }
    }

    /**
     * Resumes or starts playback of a specific course using its saved progress.
     */
    fun playCourse(
        courseId: String,
        playerViewModel: PlayerViewModel
    ) {
        viewModelScope.launch {
            val allPlaylists = playlistPreferencesRepository.userPlaylistsFlow.first()
            val playlist = allPlaylists.find { it.id == courseId } ?: return@launch
            val allSongsMap = musicRepository.getAllSongsOnce().associateBy { it.id }
            val songs = playlist.songIds.mapNotNull { allSongsMap[it] }
            if (songs.isNotEmpty()) {
                val progress = courseProgressRepository.getProgress(courseId)
                val currentSongId = progress?.currentSongId
                val startSong = songs.find { it.id == currentSongId } ?: songs.first()
                val startPos = if (startSong.id == currentSongId) (progress?.currentPositionMs ?: 0L) else 0L
                playerViewModel.playSongs(
                    songsToPlay = songs,
                    startSong = startSong,
                    queueName = playlist.name,
                    playlistId = playlist.id,
                    startPositionMs = startPos
                )
            }
        }
    }
}
