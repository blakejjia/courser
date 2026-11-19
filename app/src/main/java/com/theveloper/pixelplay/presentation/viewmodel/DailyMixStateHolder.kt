package com.theveloper.pixelplay.presentation.viewmodel

import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.repository.CourseProgressRepository
import com.theveloper.pixelplay.data.repository.DailyLearningQueueBuilder
import com.theveloper.pixelplay.data.repository.MusicRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Keep Listening and Learning Queue state.
 */
@Singleton
class DailyMixStateHolder @Inject constructor(
    private val dailyLearningQueueBuilder: DailyLearningQueueBuilder,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val musicRepository: MusicRepository,
    private val courseProgressRepository: CourseProgressRepository? = null
) {
    private var scope: CoroutineScope? = null
    private var updateJob: Job? = null
    private var dailyMixProgressJob: Job? = null
    private var yourMixProgressJob: Job? = null

    private val _dailyMixSongs = MutableStateFlow<ImmutableList<Song>>(persistentListOf())
    val dailyMixSongs: StateFlow<ImmutableList<Song>> = _dailyMixSongs.asStateFlow()

    private val _yourMixSongs = MutableStateFlow<ImmutableList<Song>>(persistentListOf())
    val yourMixSongs: StateFlow<ImmutableList<Song>> = _yourMixSongs.asStateFlow()

    /**
     * Initialize with coroutine scope from ViewModel.
     */
    fun initialize(coroutineScope: CoroutineScope) {
        scope = coroutineScope
    }

    private fun observeDailyMixProgress(songs: List<Song>) {
        dailyMixProgressJob?.cancel()
        if (songs.isEmpty() || courseProgressRepository == null) return
        dailyMixProgressJob = scope?.launch {
            courseProgressRepository.observeEnrichedSongs(songs).collect { enriched ->
                _dailyMixSongs.value = enriched.toImmutableList()
            }
        }
    }

    private fun observeYourMixProgress(songs: List<Song>) {
        yourMixProgressJob?.cancel()
        if (songs.isEmpty() || courseProgressRepository == null) return
        yourMixProgressJob = scope?.launch {
            courseProgressRepository.observeEnrichedSongs(songs).collect { enriched ->
                _yourMixSongs.value = enriched.toImmutableList()
            }
        }
    }

    /**
     * Remove a song from the daily mix.
     */
    fun removeFromDailyMix(songId: String) {
        _dailyMixSongs.update { currentList ->
            val updated = currentList.filterNot { it.id == songId }.toImmutableList()
            observeDailyMixProgress(updated)
            updated
        }
    }

    /**
     * Update the daily mix with new songs from DailyLearningQueueBuilder.
     */
    fun updateDailyMix(favoriteSongIdsFlow: kotlinx.coroutines.flow.Flow<Set<String>>? = null) {
        updateJob?.cancel()
        updateJob = scope?.launch(Dispatchers.IO) {
            val session = dailyLearningQueueBuilder.buildLearningQueue()
            if (session != null && session.songs.isNotEmpty()) {
                val mix = session.songs.toImmutableList()
                _dailyMixSongs.value = mix
                _yourMixSongs.value = mix
                observeDailyMixProgress(mix)
                observeYourMixProgress(mix)
                userPreferencesRepository.saveDailyMixSongIds(mix.map { it.id })
            } else {
                val allSongs = musicRepository.getAllSongsOnce()
                if (allSongs.isNotEmpty()) {
                    val mix = allSongs.take(20).toImmutableList()
                    _dailyMixSongs.value = mix
                    _yourMixSongs.value = mix
                    observeDailyMixProgress(mix)
                    observeYourMixProgress(mix)
                    userPreferencesRepository.saveDailyMixSongIds(mix.map { it.id })
                } else {
                    _yourMixSongs.value = persistentListOf()
                }
            }
        }
    }

    /**
     * Load persisted daily mix from storage or build from queue.
     */
    fun loadPersistedDailyMix() {
        scope?.launch {
            val session = dailyLearningQueueBuilder.buildLearningQueue()
            if (session != null && session.songs.isNotEmpty()) {
                val mix = session.songs.toImmutableList()
                _dailyMixSongs.value = mix
                _yourMixSongs.value = mix
                observeDailyMixProgress(mix)
                observeYourMixProgress(mix)
            } else {
                val dailyMixIds = userPreferencesRepository.dailyMixSongIdsFlow.first()
                if (dailyMixIds.isNotEmpty() && _dailyMixSongs.value.isEmpty()) {
                    val songs = withContext(Dispatchers.IO) {
                        musicRepository.getSongsByIds(dailyMixIds).first()
                    }
                    if (songs.isNotEmpty()) {
                        val songMap = songs.associateBy { it.id }
                        val orderedSongs = dailyMixIds.mapNotNull { songMap[it] }
                        val immutableSongs = orderedSongs.toImmutableList()
                        _dailyMixSongs.value = immutableSongs
                        observeDailyMixProgress(immutableSongs)
                    }
                }
            }
        }

        // Load Your Mix
        scope?.launch {
            val yourMixIds = userPreferencesRepository.yourMixSongIdsFlow.first()
            if (yourMixIds.isNotEmpty() && _yourMixSongs.value.isEmpty()) {
                val songs = withContext(Dispatchers.IO) {
                    musicRepository.getSongsByIds(yourMixIds).first()
                }
                if (songs.isNotEmpty()) {
                    val songMap = songs.associateBy { it.id }
                    val orderedSongs = yourMixIds.mapNotNull { songMap[it] }
                    val immutableSongs = orderedSongs.toImmutableList()
                    _yourMixSongs.value = immutableSongs
                    observeYourMixProgress(immutableSongs)
                }
            }
        }
    }

    /**
     * Force update the daily mix regardless of day.
     */
    fun forceUpdate(favoriteSongIdsFlow: kotlinx.coroutines.flow.Flow<Set<String>>) {
        scope?.launch {
            updateDailyMix(favoriteSongIdsFlow)
            userPreferencesRepository.saveLastDailyMixUpdateTimestamp(System.currentTimeMillis())
        }
    }

    /**
     * Check if daily mix needs updating (new day) and update if so.
     */
    fun checkAndUpdateIfNeeded(favoriteSongIdsFlow: kotlinx.coroutines.flow.Flow<Set<String>>) {
        scope?.launch {
            val lastUpdate = userPreferencesRepository.lastDailyMixUpdateFlow.first()
            val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
            val lastUpdateDay = Calendar.getInstance().apply {
                timeInMillis = lastUpdate
            }.get(Calendar.DAY_OF_YEAR)

            if (today != lastUpdateDay) {
                updateDailyMix(favoriteSongIdsFlow)
                userPreferencesRepository.saveLastDailyMixUpdateTimestamp(System.currentTimeMillis())
            }
        }
    }

    /**
     * Set the daily mix songs directly (used for AI-generated mixes).
     */
    fun setDailyMixSongs(songs: List<Song>) {
        val immutableSongs = songs.toImmutableList()
        _dailyMixSongs.value = immutableSongs
        observeDailyMixProgress(immutableSongs)
        scope?.launch {
            userPreferencesRepository.saveDailyMixSongIds(songs.map { it.id })
        }
    }

    /**
     * Get a candidate pool for AI playlist generation.
     */
    suspend fun getCandidatePool(
        allSongs: List<Song>,
        favoriteIds: Set<String>,
        maxSize: Int = 100
    ): List<Song> {
        return allSongs.take(maxSize)
    }

    fun onCleared() {
        updateJob?.cancel()
        dailyMixProgressJob?.cancel()
        yourMixProgressJob?.cancel()
        scope = null
    }
}
