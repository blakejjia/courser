package com.theveloper.pixelplay.presentation.viewmodel

import android.os.Environment
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.gdrive.GDriveRepository
import com.theveloper.pixelplay.data.repository.CourseFolderManager
import com.theveloper.pixelplay.data.repository.DriveFolderEntry
import com.theveloper.pixelplay.data.repository.LocalFolderEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

enum class CourseSourceTab {
    LOCAL,
    GDRIVE
}

data class CourseFolderSelectionUiState(
    val currentTab: CourseSourceTab = CourseSourceTab.LOCAL,
    // Local storage
    val currentLocalDirectory: File = Environment.getExternalStorageDirectory(),
    val localBreadcrumbs: List<File> = listOf(Environment.getExternalStorageDirectory()),
    val localFolders: List<LocalFolderEntry> = emptyList(),
    val checkedLocalPaths: Set<String> = emptySet(),
    val initiallyAddedLocalPaths: Set<String> = emptySet(),
    // Google Drive
    val isGDriveLoggedIn: Boolean = false,
    val gdriveUserName: String? = null,
    val currentDriveBreadcrumb: List<GDriveRepository.DriveFolder> = listOf(
        GDriveRepository.DriveFolder("root", "My Drive")
    ),
    val driveFolders: List<DriveFolderEntry> = emptyList(),
    val checkedDriveFolderIds: Set<String> = emptySet(),
    val initiallyAddedDriveFolderIds: Set<String> = emptySet(),
    val driveFolderNamesMap: Map<String, String> = emptyMap(),
    // Status
    val isLocalLoading: Boolean = true,
    val isDriveLoading: Boolean = false,
    val isApplying: Boolean = false,
    val statusMessage: String? = null
) {
    val isLoading: Boolean
        get() = if (currentTab == CourseSourceTab.LOCAL) isLocalLoading else isDriveLoading

    val pendingAdditionsCount: Int
        get() = (checkedLocalPaths - initiallyAddedLocalPaths).size + (checkedDriveFolderIds - initiallyAddedDriveFolderIds).size

    val pendingRemovalsCount: Int
        get() = (initiallyAddedLocalPaths - checkedLocalPaths).size + (initiallyAddedDriveFolderIds - checkedDriveFolderIds).size

    val hasChanges: Boolean
        get() = (checkedLocalPaths != initiallyAddedLocalPaths) || (checkedDriveFolderIds != initiallyAddedDriveFolderIds)

    val totalSelectedCoursesCount: Int
        get() = checkedLocalPaths.size + checkedDriveFolderIds.size
}

@HiltViewModel
class CourseFolderSelectionViewModel @Inject constructor(
    private val courseFolderManager: CourseFolderManager,
    private val gdriveRepository: GDriveRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val initialTab = if (savedStateHandle.get<String>("tab")?.equals("gdrive", ignoreCase = true) == true) {
        CourseSourceTab.GDRIVE
    } else {
        CourseSourceTab.LOCAL
    }

    private val _uiState = MutableStateFlow(CourseFolderSelectionUiState(currentTab = initialTab))
    val uiState: StateFlow<CourseFolderSelectionUiState> = _uiState.asStateFlow()

    init {
        loadInitialState()
        viewModelScope.launch {
            courseFolderManager.isSyncing.collect { syncing ->
                _uiState.update { it.copy(isApplying = syncing) }
            }
        }
        viewModelScope.launch {
            gdriveRepository.isLoggedInFlow.collect { loggedIn ->
                val userName = if (loggedIn) gdriveRepository.userDisplayName ?: gdriveRepository.userEmail else null
                _uiState.update {
                    it.copy(
                        isGDriveLoggedIn = loggedIn,
                        gdriveUserName = userName
                    )
                }
                if (loggedIn && _uiState.value.currentTab == CourseSourceTab.GDRIVE && _uiState.value.driveFolders.isEmpty()) {
                    loadDriveDirectory(_uiState.value.currentDriveBreadcrumb.lastOrNull()?.id ?: "root")
                }
            }
        }
    }

    private fun loadInitialState() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLocalLoading = true) }

            val localPaths = courseFolderManager.getAddedLocalCoursePaths().first()
            val driveIds = courseFolderManager.getAddedDriveFolderIds().first()
            val isLoggedIn = gdriveRepository.isLoggedIn
            val userName = gdriveRepository.userDisplayName ?: gdriveRepository.userEmail

            _uiState.update {
                it.copy(
                    checkedLocalPaths = localPaths,
                    initiallyAddedLocalPaths = localPaths,
                    checkedDriveFolderIds = driveIds,
                    initiallyAddedDriveFolderIds = driveIds,
                    isGDriveLoggedIn = isLoggedIn,
                    gdriveUserName = userName
                )
            }

            val rootDir = Environment.getExternalStorageDirectory()
            val folders = courseFolderManager.listLocalFolderEntries(
                directory = rootDir,
                checkedPaths = localPaths
            )

            _uiState.update {
                it.copy(
                    currentLocalDirectory = rootDir,
                    localFolders = folders,
                    isLocalLoading = false
                )
            }

            if (isLoggedIn && _uiState.value.currentTab == CourseSourceTab.GDRIVE) {
                loadDriveDirectory("root")
            }
        }
    }

    fun selectTab(tab: CourseSourceTab) {
        _uiState.update { it.copy(currentTab = tab) }
        if (tab == CourseSourceTab.GDRIVE && _uiState.value.isGDriveLoggedIn && _uiState.value.driveFolders.isEmpty()) {
            loadDriveDirectory(_uiState.value.currentDriveBreadcrumb.lastOrNull()?.id ?: "root")
        }
    }

    // --- Local Folder Navigation & Selection ---

    fun loadLocalDirectory(directory: File) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLocalLoading = true) }
            val folders = courseFolderManager.listLocalFolderEntries(
                directory = directory,
                checkedPaths = _uiState.value.checkedLocalPaths
            )
            _uiState.update {
                it.copy(
                    currentLocalDirectory = directory,
                    localFolders = folders,
                    isLocalLoading = false
                )
            }
        }
    }

    fun navigateLocalInto(folder: File) {
        val currentBreadcrumbs = _uiState.value.localBreadcrumbs.toMutableList()
        currentBreadcrumbs.add(folder)
        _uiState.update { it.copy(localBreadcrumbs = currentBreadcrumbs) }
        loadLocalDirectory(folder)
    }

    fun navigateLocalBreadcrumb(targetIndex: Int) {
        val crumbs = _uiState.value.localBreadcrumbs
        if (targetIndex in crumbs.indices) {
            val targetFolder = crumbs[targetIndex]
            val newCrumbs = crumbs.subList(0, targetIndex + 1)
            _uiState.update { it.copy(localBreadcrumbs = newCrumbs) }
            loadLocalDirectory(targetFolder)
        }
    }

    fun navigateLocalUp(): Boolean {
        val crumbs = _uiState.value.localBreadcrumbs
        if (crumbs.size > 1) {
            navigateLocalBreadcrumb(crumbs.size - 2)
            return true
        }
        return false
    }

    fun toggleLocalFolderChecked(folder: File) {
        val canonical = runCatching { folder.canonicalPath }.getOrElse { folder.absolutePath }
        val current = _uiState.value.checkedLocalPaths.toMutableSet()
        if (current.contains(canonical)) {
            current.remove(canonical)
        } else {
            current.add(canonical)
        }

        val updatedFolders = _uiState.value.localFolders.map { entry ->
            val entryCanonical = runCatching { entry.file.canonicalPath }.getOrElse { entry.file.absolutePath }
            entry.copy(isChecked = entryCanonical in current)
        }

        _uiState.update {
            it.copy(
                checkedLocalPaths = current,
                localFolders = updatedFolders
            )
        }
    }

    // --- Google Drive Navigation & Selection ---

    fun loadDriveDirectory(parentId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDriveLoading = true) }
            val result = courseFolderManager.listDriveFolderEntries(
                parentId = parentId,
                checkedIds = _uiState.value.checkedDriveFolderIds
            )
            result.fold(
                onSuccess = { entries ->
                    // Cache names
                    val names = _uiState.value.driveFolderNamesMap.toMutableMap()
                    entries.forEach { names[it.id] = it.name }

                    _uiState.update {
                        it.copy(
                            driveFolders = entries,
                            driveFolderNamesMap = names,
                            isDriveLoading = false
                        )
                    }
                },
                onFailure = { error ->
                    Timber.e(error, "Failed to list Drive folders")
                    _uiState.update {
                        it.copy(
                            isDriveLoading = false,
                            statusMessage = "Failed to load Drive folders: ${error.message}"
                        )
                    }
                }
            )
        }
    }

    fun navigateDriveInto(folder: GDriveRepository.DriveFolder) {
        val crumbs = _uiState.value.currentDriveBreadcrumb.toMutableList()
        crumbs.add(folder)
        _uiState.update { it.copy(currentDriveBreadcrumb = crumbs) }
        loadDriveDirectory(folder.id)
    }

    fun navigateDriveBreadcrumb(targetIndex: Int) {
        val crumbs = _uiState.value.currentDriveBreadcrumb
        if (targetIndex in crumbs.indices) {
            val targetFolder = crumbs[targetIndex]
            val newCrumbs = crumbs.subList(0, targetIndex + 1)
            _uiState.update { it.copy(currentDriveBreadcrumb = newCrumbs) }
            loadDriveDirectory(targetFolder.id)
        }
    }

    fun navigateDriveUp(): Boolean {
        val crumbs = _uiState.value.currentDriveBreadcrumb
        if (crumbs.size > 1) {
            navigateDriveBreadcrumb(crumbs.size - 2)
            return true
        }
        return false
    }

    fun toggleDriveFolderChecked(entry: DriveFolderEntry) {
        val current = _uiState.value.checkedDriveFolderIds.toMutableSet()
        val names = _uiState.value.driveFolderNamesMap.toMutableMap()
        names[entry.id] = entry.name

        if (current.contains(entry.id)) {
            current.remove(entry.id)
        } else {
            current.add(entry.id)
        }

        val updatedFolders = _uiState.value.driveFolders.map { item ->
            item.copy(isChecked = item.id in current)
        }

        _uiState.update {
            it.copy(
                checkedDriveFolderIds = current,
                driveFolders = updatedFolders,
                driveFolderNamesMap = names
            )
        }
    }

    fun onDriveLoggedIn(identifier: String) {
        _uiState.update {
            it.copy(
                isGDriveLoggedIn = true,
                gdriveUserName = identifier
            )
        }
        loadDriveDirectory("root")
    }

    fun refreshDriveLoginState() {
        val isLoggedIn = gdriveRepository.isLoggedIn
        val userName = gdriveRepository.userDisplayName ?: gdriveRepository.userEmail
        _uiState.update {
            it.copy(
                isGDriveLoggedIn = isLoggedIn,
                gdriveUserName = userName
            )
        }
        if (isLoggedIn) {
            loadDriveDirectory(_uiState.value.currentDriveBreadcrumb.lastOrNull()?.id ?: "root")
        }
    }

    // --- Apply Batch Changes ---

    fun applyChanges(onSuccess: () -> Unit = {}) {
        val state = _uiState.value
        if (!state.hasChanges) {
            onSuccess()
            return
        }

        val newlyAddedLocalPaths = state.checkedLocalPaths - state.initiallyAddedLocalPaths
        val removedLocalPaths = (state.initiallyAddedLocalPaths - state.checkedLocalPaths).toList()
        val addedLocalFiles = newlyAddedLocalPaths.map { File(it) }

        val newlyAddedDriveIds = state.checkedDriveFolderIds - state.initiallyAddedDriveFolderIds
        val removedDriveIds = (state.initiallyAddedDriveFolderIds - state.checkedDriveFolderIds).toList()
        val addedDriveFolders = newlyAddedDriveIds.map { id ->
            val name = state.driveFolderNamesMap[id] ?: "Drive Course"
            id to name
        }

        _uiState.update {
            it.copy(
                initiallyAddedLocalPaths = state.checkedLocalPaths,
                initiallyAddedDriveFolderIds = state.checkedDriveFolderIds
            )
        }

        courseFolderManager.applyBatchChangesInBackground(
            addedLocalFolders = addedLocalFiles,
            removedLocalPaths = removedLocalPaths,
            addedDriveFolders = addedDriveFolders,
            removedDriveIds = removedDriveIds
        )

        onSuccess()
    }
}
