package com.theveloper.pixelplay.presentation.gdrive.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.gdrive.GDriveRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

sealed class GDriveLoginState {
    object Idle : GDriveLoginState()
    object Loading : GDriveLoginState()
    object Success : GDriveLoginState()
    data class Error(val message: String) : GDriveLoginState()
}

@HiltViewModel
class GDriveLoginViewModel @Inject constructor(
    private val repository: GDriveRepository
) : ViewModel() {

    private val _state = MutableStateFlow<GDriveLoginState>(
        if (repository.isLoggedIn) GDriveLoginState.Success else GDriveLoginState.Idle
    )
    val state: StateFlow<GDriveLoginState> = _state.asStateFlow()

    fun processCredential(
        idToken: String,
        serverAuthCode: String?,
        email: String? = null,
        displayName: String? = null,
        profilePictureUri: String? = null
    ) {
        _state.value = GDriveLoginState.Loading
        viewModelScope.launch {
            val result = repository.loginWithCredential(
                idToken = idToken,
                serverAuthCode = serverAuthCode,
                email = email,
                displayName = displayName,
                profilePictureUri = profilePictureUri
            )
            result.fold(
                onSuccess = {
                    _state.value = GDriveLoginState.Success
                },
                onFailure = { error ->
                    Timber.e(error, "GDrive login failed")
                    _state.value = GDriveLoginState.Error(error.message ?: "Login failed")
                }
            )
        }
    }

    fun loginWithAccessToken(
        accessToken: String,
        email: String? = null,
        displayName: String? = null
    ) {
        _state.value = GDriveLoginState.Loading
        viewModelScope.launch {
            val result = repository.loginWithAccessToken(
                accessToken = accessToken,
                email = email,
                displayName = displayName
            )
            result.fold(
                onSuccess = {
                    _state.value = GDriveLoginState.Success
                },
                onFailure = { error ->
                    Timber.e(error, "GDrive access token login failed")
                    _state.value = GDriveLoginState.Error(error.message ?: "Login failed")
                }
            )
        }
    }

    fun setError(message: String) {
        _state.value = GDriveLoginState.Error(message)
    }
}
