package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.gdrive.GDriveRepository
import com.theveloper.pixelplay.data.jellyfin.JellyfinRepository
import com.theveloper.pixelplay.data.navidrome.NavidromeRepository
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.telegram.TelegramRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi

enum class ExternalServiceAccount {
    TELEGRAM,
    GOOGLE_DRIVE,
    NAVIDROME,
    JELLYFIN
}

data class ExternalAccountUiModel(
    val service: ExternalServiceAccount,
    val title: String,
    val accountLabel: String,
    val syncedContentLabel: String,
    val isLoggingOut: Boolean
)

data class AccountsUiState(
    val connectedAccounts: List<ExternalAccountUiModel> = emptyList(),
    val disconnectedServices: List<ExternalServiceAccount> = emptyList()
)

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val telegramRepository: TelegramRepository,
    private val musicRepository: MusicRepository,
    private val gDriveRepository: GDriveRepository,
    private val navidromeRepository: NavidromeRepository,
    private val jellyfinRepository: JellyfinRepository
) : ViewModel() {

    private val loggingOutServices = MutableStateFlow<Set<ExternalServiceAccount>>(emptySet())

    private val telegramStateFlow = combine(
        telegramRepository.authorizationState
            .map { it is TdApi.AuthorizationStateReady }
            .distinctUntilChanged(),
        musicRepository.getAllTelegramChannels().map { it.size }
    ) { connected, channelCount ->
        connected to channelCount
    }

    private val gDriveStateFlow = combine(
        gDriveRepository.isLoggedInFlow,
        gDriveRepository.getFolders().map { it.size }
    ) { connected, folderCount ->
        connected to folderCount
    }

    private val navidromeStateFlow = combine(
        navidromeRepository.isLoggedInFlow,
        navidromeRepository.getPlaylists().map { it.size }
    ) { connected, playlistCount ->
        connected to playlistCount
    }

    private val jellyfinStateFlow = combine(
        jellyfinRepository.isLoggedInFlow,
        jellyfinRepository.getPlaylists().map { it.size }
    ) { connected, playlistCount ->
        connected to playlistCount
    }

    val uiState: StateFlow<AccountsUiState> = combine(
        gDriveStateFlow,
        loggingOutServices
    ) { (gDriveConnected, gDriveFolderCount), activeLogouts ->
        val connectedAccounts = if (gDriveConnected) {
            listOf(
                ExternalAccountUiModel(
                    service = ExternalServiceAccount.GOOGLE_DRIVE,
                    title = "Google Drive",
                    accountLabel = gDriveRepository.userDisplayName
                        ?.takeIf { it.isNotBlank() }
                        ?: gDriveRepository.userEmail
                            ?.takeIf { it.isNotBlank() }
                        ?: "Google account connected",
                    syncedContentLabel = formatCount(
                        count = gDriveFolderCount,
                        singular = "synced folder",
                        plural = "synced folders"
                    ),
                    isLoggingOut = ExternalServiceAccount.GOOGLE_DRIVE in activeLogouts
                )
            )
        } else {
            emptyList()
        }

        val disconnectedServices = if (!gDriveConnected) {
            listOf(ExternalServiceAccount.GOOGLE_DRIVE)
        } else {
            emptyList()
        }

        AccountsUiState(
            connectedAccounts = connectedAccounts,
            disconnectedServices = disconnectedServices
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsUiState())

    fun logout(service: ExternalServiceAccount) {
        if (service in loggingOutServices.value) return

        viewModelScope.launch {
            loggingOutServices.update { it + service }
            try {
                runCatching {
                    when (service) {
                        ExternalServiceAccount.TELEGRAM -> {
                            telegramRepository.logout()
                            telegramRepository.clearMemoryCache()
                            musicRepository.clearTelegramData()
                        }
                        ExternalServiceAccount.GOOGLE_DRIVE -> gDriveRepository.logout()
                        ExternalServiceAccount.NAVIDROME -> navidromeRepository.logout()
                        ExternalServiceAccount.JELLYFIN -> jellyfinRepository.logout()
                    }
                }
            } finally {
                loggingOutServices.update { it - service }
            }
        }
    }

    private fun formatCount(count: Int, singular: String, plural: String): String {
        return if (count == 1) {
            "1 $singular"
        } else {
            "$count $plural"
        }
    }
}
