@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package com.theveloper.pixelplay.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import android.app.Activity
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Playlist
import com.theveloper.pixelplay.presentation.components.PlaylistContainer
import com.theveloper.pixelplay.presentation.components.PlaylistMultiSelectionBottomSheet
import com.theveloper.pixelplay.presentation.components.StreamingProviderSheet
import com.theveloper.pixelplay.presentation.components.resolveNavBarOccupiedHeight
import com.theveloper.pixelplay.presentation.components.subcomps.SelectionActionRow
import com.theveloper.pixelplay.presentation.components.subcomps.SelectionCountPill
import com.theveloper.pixelplay.presentation.gdrive.dashboard.GDriveDashboardViewModel
import com.theveloper.pixelplay.presentation.jellyfin.dashboard.JellyfinDashboardViewModel
import com.theveloper.pixelplay.presentation.navidrome.dashboard.NavidromeDashboardViewModel
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.navigation.navigateSafely
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlaylistViewModel

val ListExtraBottomGap = 30.dp
val PlayerSheetCollapsedCornerRadius = 32.dp

/**
 * Course Library Screen combining Course search and playlist browsing.
 * Focused exclusively on courses (playlists) with live title searching and batch selection.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun LibraryScreen(
    navController: NavController,
    playerViewModel: PlayerViewModel,
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
    gdriveViewModel: GDriveDashboardViewModel = hiltViewModel(),
    navidromeViewModel: NavidromeDashboardViewModel = hiltViewModel(),
    jellyfinViewModel: JellyfinDashboardViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val playlistUiState by playlistViewModel.uiState.collectAsStateWithLifecycle()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showStreamingProviderSheet by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchInputFocusRequester = remember { FocusRequester() }
    val haptic = LocalHapticFeedback.current

    val playlistSelectionState = playerViewModel.playlistSelectionStateHolder
    val selectedPlaylistIds by playlistSelectionState.selectedPlaylistIds.collectAsStateWithLifecycle()
    val isSelectionMode by playlistSelectionState.isSelectionMode.collectAsStateWithLifecycle()
    val playbackHistory by playerViewModel.playbackHistory.collectAsStateWithLifecycle()

    val allPlaylists = playlistUiState.playlists

    // Rank courses by most recent played to least recent played
    val rankedPlaylists = remember(allPlaylists, playbackHistory) {
        val latestSongPlayMap = HashMap<String, Long>()
        for (entry in playbackHistory) {
            val existing = latestSongPlayMap[entry.songId] ?: 0L
            if (entry.timestamp > existing) {
                latestSongPlayMap[entry.songId] = entry.timestamp
            }
        }

        allPlaylists.sortedWith(
            compareByDescending<Playlist> { playlist ->
                var maxTimestamp = 0L
                for (id in playlist.songIds) {
                    val ts = latestSongPlayMap[id] ?: 0L
                    if (ts > maxTimestamp) maxTimestamp = ts
                }
                maxTimestamp
            }.thenByDescending { playlist ->
                playlist.lastModified.coerceAtLeast(playlist.createdAt)
            }
        )
    }

    val filteredPlaylists = remember(searchQuery, rankedPlaylists) {
        if (searchQuery.isBlank()) {
            rankedPlaylists
        } else {
            rankedPlaylists.filter { it.name.contains(searchQuery.trim(), ignoreCase = true) }
        }
    }

    val selectedPlaylistsList = remember(selectedPlaylistIds, rankedPlaylists) {
        rankedPlaylists.filter { it.id in selectedPlaylistIds }
    }

    var showPlaylistMultiSelectionSheet by remember { mutableStateOf(false) }

    // Clear selection on back press, or clear search query
    BackHandler(enabled = isSelectionMode || searchQuery.isNotBlank()) {
        if (isSelectionMode) {
            playlistSelectionState.clearSelection()
            showPlaylistMultiSelectionSheet = false
        } else if (searchQuery.isNotBlank()) {
            searchQuery = ""
            keyboardController?.hide()
        }
    }

    val statusBarTopInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarCompactMode by playerViewModel.navBarCompactMode.collectAsStateWithLifecycle()
    val systemNavBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val navBarOccupiedHeight = resolveNavBarOccupiedHeight(systemNavBarInset, navBarCompactMode)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Search Bar header with Cloud Streaming action
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, top = statusBarTopInset + 12.dp, end = 20.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val searchBarInputFieldColors = SearchBarDefaults.inputFieldColors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary
                )

                DockedSearchBar(
                    inputField = {
                        SearchBarDefaults.InputField(
                            modifier = Modifier.focusRequester(searchInputFocusRequester),
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            onSearch = { keyboardController?.hide() },
                            expanded = false,
                            onExpandedChange = {},
                            placeholder = {
                                Text(
                                    text = "Search courses...",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = "Search",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotBlank()) {
                                    IconButton(
                                        onClick = {
                                            searchQuery = ""
                                            keyboardController?.hide()
                                        },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(
                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                            )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Clear search",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            },
                            colors = searchBarInputFieldColors
                        )
                    },
                    expanded = false,
                    onExpandedChange = {},
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(28.dp)),
                    colors = SearchBarDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                        dividerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        inputFieldColors = searchBarInputFieldColors
                    ),
                    content = {}
                )

                // Add Course Button to select folder as course (Local Storage or Google Drive)
                FilledIconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        navController.navigateSafely(Screen.CourseFolderSelection.route)
                    },
                    modifier = Modifier.size(52.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Add Course Folders",
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            // Comfortable breathing room between search header and listing items
            Spacer(modifier = Modifier.height(8.dp))

            // Selection action bar when multi-selecting courses
            AnimatedVisibility(
                visible = isSelectionMode,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                SelectionActionRow(
                    selectedCount = selectedPlaylistIds.size,
                    onSelectAll = { playlistSelectionState.selectAll(filteredPlaylists) },
                    onDeselect = { playlistSelectionState.clearSelection() },
                    onOptionsClick = { showPlaylistMultiSelectionSheet = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }

            // Playlist / Course List
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                PlaylistContainer(
                    playlistUiState = playlistUiState,
                    filteredPlaylists = filteredPlaylists,
                    currentSortOption = playlistUiState.currentPlaylistSortOption,
                    isRefreshing = false,
                    onRefresh = {},
                    bottomBarHeight = navBarOccupiedHeight,
                    navController = navController,
                    playerViewModel = playerViewModel,
                    isSelectionMode = isSelectionMode,
                    selectedPlaylistIds = selectedPlaylistIds,
                    onPlaylistLongPress = { playlist ->
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        playlistSelectionState.toggleSelection(playlist)
                    },
                    onPlaylistSelectionToggle = { playlist ->
                        playlistSelectionState.toggleSelection(playlist)
                    }
                )

                // Selection count pill overlay
                if (isSelectionMode) {
                    SelectionCountPill(
                        selectedCount = selectedPlaylistIds.size,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)
                    )
                }
            }
        }
    }

    if (showPlaylistMultiSelectionSheet && selectedPlaylistsList.isNotEmpty()) {
        PlaylistMultiSelectionBottomSheet(
            selectedPlaylists = selectedPlaylistsList,
            onDismiss = { showPlaylistMultiSelectionSheet = false },
            onDeleteAll = {
                playlistViewModel.deletePlaylistsInBatch(selectedPlaylistsList.map { it.id })
                playlistSelectionState.clearSelection()
                showPlaylistMultiSelectionSheet = false
            },
            onExportAll = {
                playlistViewModel.exportPlaylistsAsM3u(selectedPlaylistsList.map { it.id })
                playlistSelectionState.clearSelection()
                showPlaylistMultiSelectionSheet = false
            },
            onMergeAll = {
                playlistViewModel.mergePlaylistsIntoOne(
                    selectedPlaylistsList.map { it.id },
                    "Merged Course"
                )
                playlistSelectionState.clearSelection()
                showPlaylistMultiSelectionSheet = false
            },
            onShareAll = {
                playlistViewModel.shareSelectedPlaylistsAsZip(
                    selectedPlaylistsList.map { it.id },
                    context as? Activity
                )
                showPlaylistMultiSelectionSheet = false
            }
        )
    }

    if (showStreamingProviderSheet) {
        val isNavidromeLoggedIn by navidromeViewModel.isLoggedIn.collectAsStateWithLifecycle()
        val isJellyfinLoggedIn by jellyfinViewModel.isLoggedIn.collectAsStateWithLifecycle()
        val isGDriveLoggedIn by gdriveViewModel.isLoggedIn.collectAsStateWithLifecycle()

        StreamingProviderSheet(
            onDismissRequest = { showStreamingProviderSheet = false },
            isNavidromeLoggedIn = isNavidromeLoggedIn,
            onNavigateToNavidromeDashboard = {
                navController.navigateSafely(Screen.NavidromeDashboard.route)
            },
            isJellyfinLoggedIn = isJellyfinLoggedIn,
            onNavigateToJellyfinDashboard = {
                navController.navigateSafely(Screen.JellyfinDashboard.route)
            },
            isGDriveLoggedIn = isGDriveLoggedIn,
            onNavigateToGDriveDashboard = {
                navController.navigateSafely(Screen.GDriveDashboard.route)
            }
        )
    }
}
