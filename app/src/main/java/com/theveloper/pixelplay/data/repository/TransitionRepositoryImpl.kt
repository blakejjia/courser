package com.theveloper.pixelplay.data.repository

import com.theveloper.pixelplay.data.model.TransitionResolution
import com.theveloper.pixelplay.data.model.TransitionRule
import com.theveloper.pixelplay.data.model.TransitionSettings
import com.theveloper.pixelplay.data.model.TransitionSource
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransitionRepositoryImpl @Inject constructor(
    private val userPreferences: UserPreferencesRepository
) : TransitionRepository {

    override fun resolveTransitionSettings(
        playlistId: String,
        fromTrackId: String,
        toTrackId: String
    ): Flow<TransitionResolution> {
        return userPreferences.globalTransitionSettingsFlow.map { settings ->
            TransitionResolution(
                settings = settings,
                source = TransitionSource.GLOBAL_DEFAULT,
            )
        }
    }

    override fun getAllRulesForPlaylist(playlistId: String): Flow<List<TransitionRule>> {
        return flowOf(emptyList())
    }

    override fun getPlaylistDefaultRule(playlistId: String): Flow<TransitionRule?> {
        return flowOf(null)
    }

    override suspend fun saveRule(rule: TransitionRule) {
        // No-op: per-track transition rules removed for course player
    }

    override suspend fun deleteRule(ruleId: Long) {
        // No-op
    }

    override suspend fun deletePlaylistDefaultRule(playlistId: String) {
        // No-op
    }

    override fun getGlobalSettings(): Flow<TransitionSettings> {
        return userPreferences.globalTransitionSettingsFlow
    }

    override suspend fun saveGlobalSettings(settings: TransitionSettings) {
        userPreferences.saveGlobalTransitionSettings(settings)
    }
}
