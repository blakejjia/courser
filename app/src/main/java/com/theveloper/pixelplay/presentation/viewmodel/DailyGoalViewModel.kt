package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.model.Playlist
import com.theveloper.pixelplay.data.preferences.PlaylistPreferencesRepository
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    val sortedCoursesForAdd: List<Playlist> = emptyList(), // not-in-plan at front, then in-plan
    val isLoading: Boolean = true
)

@HiltViewModel
class DailyGoalViewModel @Inject constructor(
    private val playlistPreferencesRepository: PlaylistPreferencesRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val uiState: StateFlow<DailyGoalUiState> = combine(
        playlistPreferencesRepository.userPlaylistsFlow,
        userPreferencesRepository.dailyGoalCourseTargetsFlow
    ) { allPlaylists, targets ->
        // Planned courses (only courses explicitly added with a target)
        val planned = targets.mapNotNull { (courseId, count) ->
            allPlaylists.find { it.id == courseId }?.let { playlist ->
                PlannedCourse(playlist = playlist, targetCount = count)
            }
        }

        // Available courses sorted: not-in-plan first, then in-plan
        val (inPlan, notInPlan) = allPlaylists.partition { it.id in targets.keys }
        val sortedForAdd = notInPlan + inPlan

        DailyGoalUiState(
            allCourses = allPlaylists,
            courseTargets = targets,
            plannedCourses = planned,
            sortedCoursesForAdd = sortedForAdd,
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
}
