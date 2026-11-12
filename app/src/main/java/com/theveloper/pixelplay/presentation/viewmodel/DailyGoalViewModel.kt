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

data class DailyGoalUiState(
    val allCourses: List<Playlist> = emptyList(),
    val selectedCourseIds: Set<String> = emptySet(),
    val targetCount: Int = 1,
    val todaysCourses: List<Playlist> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class DailyGoalViewModel @Inject constructor(
    private val playlistPreferencesRepository: PlaylistPreferencesRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val uiState: StateFlow<DailyGoalUiState> = combine(
        playlistPreferencesRepository.userPlaylistsFlow,
        userPreferencesRepository.dailyGoalCourseIdsFlow,
        userPreferencesRepository.dailyGoalTargetCountFlow
    ) { allPlaylists, selectedIds, targetCount ->
        val candidates = if (selectedIds.isNotEmpty()) {
            allPlaylists.filter { it.id in selectedIds }
        } else {
            allPlaylists
        }

        val effectiveCount = targetCount.coerceAtLeast(1)
        val scheduled = candidates.take(effectiveCount)

        DailyGoalUiState(
            allCourses = allPlaylists,
            selectedCourseIds = selectedIds,
            targetCount = effectiveCount,
            todaysCourses = scheduled,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DailyGoalUiState()
    )

    fun toggleCourseSelection(courseId: String) {
        viewModelScope.launch {
            val current = uiState.value.selectedCourseIds
            val updated = if (courseId in current) {
                current - courseId
            } else {
                current + courseId
            }
            userPreferencesRepository.setDailyGoalCourseIds(updated)
        }
    }

    fun selectAllCourses() {
        viewModelScope.launch {
            val allIds = uiState.value.allCourses.map { it.id }.toSet()
            userPreferencesRepository.setDailyGoalCourseIds(allIds)
        }
    }

    fun clearCourseSelection() {
        viewModelScope.launch {
            userPreferencesRepository.setDailyGoalCourseIds(emptySet())
        }
    }

    fun setTargetCount(count: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setDailyGoalTargetCount(count.coerceAtLeast(1))
        }
    }
}
