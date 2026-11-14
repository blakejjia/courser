package com.theveloper.pixelplay.presentation.components

import com.theveloper.pixelplay.presentation.navigation.navigateSafely

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material3.IconButton
import androidx.compose.ui.graphics.graphicsLayer
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Topic
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Playlist
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.SortOption
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.screens.PlayerSheetCollapsedCornerRadius
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlaylistUiState
import com.theveloper.pixelplay.presentation.viewmodel.PlaylistSelectionStateHolder
import com.theveloper.pixelplay.utils.formatSongCount
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.ui.theme.LocalShowScrollbar
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.theveloper.pixelplay.data.database.CourseProgressEntity
import com.theveloper.pixelplay.presentation.viewmodel.PlaylistViewModel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlaylistContainer(
    playlistUiState: PlaylistUiState,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    bottomBarHeight: Dp,
    currentSong: Song? = null,
    navController: NavController?,
    playerViewModel: PlayerViewModel,
    isAddingToPlaylist: Boolean = false,
    selectedPlaylists: SnapshotStateMap<String, Boolean>? = null,
    filteredPlaylists: List<Playlist> = playlistUiState.playlists,
    currentSortOption: SortOption? = null,
    isSelectionMode: Boolean = false,
    isReorderMode: Boolean = false,
    selectedPlaylistIds: Set<String> = emptySet(),
    onPlaylistLongPress: (Playlist) -> Unit = {},
    onPlaylistSelectionToggle: (Playlist) -> Unit = {},
    playlistSelectionStateHolder: PlaylistSelectionStateHolder? = null
) {

    val hasCurrentSong = currentSong != null && currentSong != Song.emptySong()

    Column(modifier = Modifier.fillMaxSize()) {
        if (playlistUiState.isLoading && filteredPlaylists.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        }

        if (filteredPlaylists.isEmpty() && !playlistUiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = 28.dp,
                        end = 28.dp,
                        bottom = bottomBarHeight + (if (hasCurrentSong) MiniPlayerHeight else 0.dp) + 24.dp
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                        tonalElevation = 2.dp
                    ) {
                        Box(
                            modifier = Modifier.size(56.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.PlaylistPlay,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.playlist_container_no_playlist_created),
                            style = MaterialTheme.typography.titleLarge,
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = stringResource(R.string.playlist_container_new_playlist_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            if (isAddingToPlaylist) {
                PlaylistItems(
                    currentSong = currentSong,
                    bottomBarHeight = bottomBarHeight,
                    navController = navController,
                    playerViewModel = playerViewModel,
                    isAddingToPlaylist = true,
                    filteredPlaylists = filteredPlaylists,
                    selectedPlaylists = selectedPlaylists,
                    currentSortOption = currentSortOption
                )
            } else {
                val playlistPullToRefreshState = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    state = playlistPullToRefreshState,
                    modifier = Modifier.fillMaxSize(),
                    indicator = {
                        PullToRefreshDefaults.LoadingIndicator(
                            state = playlistPullToRefreshState,
                            isRefreshing = isRefreshing,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    }
                ) {
                    PlaylistItems(
                        bottomBarHeight = bottomBarHeight,
                        navController = navController,
                        playerViewModel = playerViewModel,
                        filteredPlaylists = filteredPlaylists,
                        currentSortOption = currentSortOption,
                        isSelectionMode = isSelectionMode,
                        isReorderMode = isReorderMode,
                        selectedPlaylistIds = selectedPlaylistIds,
                        onPlaylistLongPress = onPlaylistLongPress,
                        onPlaylistSelectionToggle = onPlaylistSelectionToggle
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface,
                                Color.Transparent
                            )
                        )
                    )
            )
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlaylistItems(
    bottomBarHeight: Dp,
    navController: NavController?,
    currentSong: Song? = null,
    playerViewModel: PlayerViewModel,
    isAddingToPlaylist: Boolean = false,
    filteredPlaylists: List<Playlist>,
    currentSortOption: SortOption? = null,
    selectedPlaylists: SnapshotStateMap<String, Boolean>? = null,
    isSelectionMode: Boolean = false,
    isReorderMode: Boolean = false,
    selectedPlaylistIds: Set<String> = emptySet(),
    onPlaylistLongPress: (Playlist) -> Unit = {},
    onPlaylistSelectionToggle: (Playlist) -> Unit = {}
) {
    val playlistViewModel: PlaylistViewModel = hiltViewModel()
    val playlistUiState by playlistViewModel.uiState.collectAsStateWithLifecycle()
    val hasCurrentSong by remember(playerViewModel) {
        playerViewModel.stablePlayerState
            .map { it.currentSong != null && it.currentSong != Song.emptySong() }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)
    val listState = rememberLazyListState()

    var localPlaylists by remember(filteredPlaylists) { mutableStateOf(filteredPlaylists) }
    LaunchedEffect(filteredPlaylists) {
        localPlaylists = filteredPlaylists
    }

    var lastMovedFrom by remember { mutableStateOf<Int?>(null) }
    var lastMovedTo by remember { mutableStateOf<Int?>(null) }

    val reorderableState = rememberReorderableLazyListState(
        lazyListState = listState,
        onMove = { from, to ->
            localPlaylists = localPlaylists.toMutableList().apply {
                add(to.index, removeAt(from.index))
            }
            if (lastMovedFrom == null) {
                lastMovedFrom = from.index
            }
            lastMovedTo = to.index
        }
    )

    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging && lastMovedFrom != null && lastMovedTo != null) {
            playlistViewModel.reorderPlaylists(lastMovedFrom!!, lastMovedTo!!)
            lastMovedFrom = null
            lastMovedTo = null
        }
    }

    val playlistFastScrollLabelProvider = remember(filteredPlaylists, currentSortOption) {
        { index: Int ->
            playlistFastScrollLabel(
                playlist = filteredPlaylists.getOrNull(index),
                sortOption = currentSortOption
            )
        }
    }
    var lastHandledPlaylistSortKey by remember { mutableStateOf(currentSortOption?.storageKey) }
    var pendingPlaylistSortScrollReset by remember { mutableStateOf(false) }

    LaunchedEffect(currentSortOption) {
        val currentSortKey = currentSortOption?.storageKey ?: return@LaunchedEffect
        if (currentSortKey == lastHandledPlaylistSortKey) return@LaunchedEffect
        lastHandledPlaylistSortKey = currentSortKey
        pendingPlaylistSortScrollReset = true
        listState.scrollToItem(0)
    }

    LaunchedEffect(filteredPlaylists, pendingPlaylistSortScrollReset) {
        if (!pendingPlaylistSortScrollReset) return@LaunchedEffect
        listState.scrollToItem(0)
        pendingPlaylistSortScrollReset = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .padding(start = 12.dp, end = if (LocalShowScrollbar.current && (listState.canScrollForward || listState.canScrollBackward)) 22.dp else 12.dp, bottom = 6.dp)
                .fillMaxSize()
                .clip(
                    RoundedCornerShape(
                        topStart = 12.dp,
                        topEnd = 12.dp,
                        bottomStart = PlayerSheetCollapsedCornerRadius,
                        bottomEnd = PlayerSheetCollapsedCornerRadius
                    )
                ),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(
                top = 8.dp,
                bottom = bottomBarHeight + (if (hasCurrentSong) MiniPlayerHeight else 0.dp) + 32.dp
            )
        ) {
            items(localPlaylists, key = { it.id }) { playlist ->
                ReorderableItem(
                    state = reorderableState,
                    key = playlist.id,
                    enabled = isReorderMode
                ) { isDragging ->
                    val scale by animateFloatAsState(
                        targetValue = if (isDragging) 1.03f else 1f,
                        label = "playlistReorderScale"
                    )
                    val rememberedOnClick = remember(playlist.id) {
                        {
                            if (isAddingToPlaylist && currentSong != null && selectedPlaylists != null) {
                                val currentSelection = selectedPlaylists[playlist.id] ?: false
                                selectedPlaylists[playlist.id] = !currentSelection
                            } else if (isSelectionMode) {
                                onPlaylistSelectionToggle(playlist)
                            } else {
                                navController?.navigateSafely(Screen.PlaylistDetail.createRoute(playlist.id))
                            }
                        }
                    }
                    val selectionIndex = remember(playlist.id, selectedPlaylistIds) {
                        if (selectedPlaylistIds.contains(playlist.id)) {
                            selectedPlaylistIds.toList().indexOf(playlist.id)
                        } else {
                            -1
                        }
                    }
                    val progress = playlistUiState.courseProgressMap[playlist.id]
                        ?: playlistUiState.courseProgressMap[playlist.name]
                    val isPinned = playlistUiState.pinnedCourseId == playlist.id

                    PlaylistItem(
                        modifier = Modifier.graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        },
                        playlist = playlist,
                        playerViewModel = playerViewModel,
                        onClick = { rememberedOnClick() },
                        onPlayClick = { playlistViewModel.playPlaylist(playlist.id, playerViewModel) },
                        isAddingToPlaylist = isAddingToPlaylist,
                        selectedPlaylists = selectedPlaylists,
                        isSelectionMode = isSelectionMode,
                        isSelected = selectedPlaylistIds.contains(playlist.id),
                        selectionIndex = selectionIndex,
                        onLongPress = { onPlaylistLongPress(playlist) },
                        onPlaylistSelectionToggle = { onPlaylistSelectionToggle(playlist) },
                        progress = progress,
                        isPinned = isPinned,
                        isReorderMode = isReorderMode,
                        dragHandle = if (isReorderMode) {
                            {
                                IconButton(
                                    onClick = {},
                                    modifier = Modifier
                                        .draggableHandle()
                                        .size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DragIndicator,
                                        contentDescription = "Reorder",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else null
                    )
                }
            }
        }
        
        val bottomPadding = if (hasCurrentSong) 
            bottomBarHeight + MiniPlayerHeight + 16.dp 
        else 
            bottomBarHeight + 16.dp

        ExpressiveScrollBar(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp, top = 16.dp, bottom = bottomPadding),
            listState = listState,
            dragLabelProvider = playlistFastScrollLabelProvider
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlaylistItem(
    playlist: Playlist,
    playerViewModel: PlayerViewModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onPlayClick: (() -> Unit)? = null,
    isAddingToPlaylist: Boolean = false,
    selectedPlaylists: SnapshotStateMap<String, Boolean>? = null,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    selectionIndex: Int = -1,
    onLongPress: () -> Unit = {},
    onPlaylistSelectionToggle: () -> Unit = {},
    progress: CourseProgressEntity? = null,
    isPinned: Boolean = false,
    isReorderMode: Boolean = false,
    dragHandle: (@Composable () -> Unit)? = null
) {
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val isPlayingThis = stablePlayerState.currentSong != null &&
        stablePlayerState.currentSong != Song.emptySong() &&
        stablePlayerState.currentSong?.id in playlist.songIds

    CourseTile(
        playlist = playlist,
        progress = progress,
        isPlaying = isPlayingThis,
        isAudioPlaying = stablePlayerState.isPlaying,
        isPinned = isPinned,
        onClick = onClick,
        onPlayClick = onPlayClick,
        onLongClick = onLongPress,
        isSelected = isSelected,
        isSelectionMode = isSelectionMode,
        selectionIndex = selectionIndex,
        onSelectionToggle = onPlaylistSelectionToggle,
        isReorderMode = isReorderMode,
        dragHandle = dragHandle,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePlaylistDialogRedesigned(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
    onGenerateClick: () -> Unit
) {
    var playlistName by remember { mutableStateOf("") }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.playlist_container_create_playlist_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    fontFamily = GoogleSansRounded,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    label = { Text(stringResource(R.string.playlist_container_create_playlist_name_label)) },
                    placeholder = { Text(stringResource(R.string.playlist_container_create_playlist_name_placeholder)) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    singleLine = true,
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.common_cancel), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    Button(
                        onClick = { onCreate(playlistName) },
                        modifier = Modifier.weight(1f),
                        enabled = playlistName.isNotEmpty(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(stringResource(R.string.common_create), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
