package com.theveloper.pixelplay.presentation.screens

import com.theveloper.pixelplay.presentation.navigation.navigateSafely
import com.theveloper.pixelplay.presentation.navigation.navigateSafelyReplacing

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import coil.size.Size
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.PlaylistBottomSheet
import com.theveloper.pixelplay.presentation.components.subcomps.EnhancedSongListItem
import com.theveloper.pixelplay.presentation.components.SongPickerBottomSheet
import com.theveloper.pixelplay.presentation.components.ExpressiveScrollBar
import com.theveloper.pixelplay.ui.theme.LocalShowScrollbar
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.SongInfoBottomSheet
import com.theveloper.pixelplay.presentation.components.resolveNavBarOccupiedHeight
import com.theveloper.pixelplay.presentation.components.subcomps.TightWrapText
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlaylistViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlaylistUiState
import com.theveloper.pixelplay.presentation.viewmodel.PlaylistViewModel.Companion.FOLDER_PLAYLIST_PREFIX
import com.theveloper.pixelplay.presentation.utils.LocalAppHapticsConfig
import com.theveloper.pixelplay.presentation.utils.performAppCompatHapticFeedback
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.presentation.viewmodel.PlaylistSongsOrderMode
import com.theveloper.pixelplay.utils.formatSongCount
import com.theveloper.pixelplay.utils.formatTotalDuration
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import com.theveloper.pixelplay.presentation.components.LibrarySortBottomSheet
import com.theveloper.pixelplay.data.model.SortOption
import com.theveloper.pixelplay.data.model.PlaylistShapeType
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(
    ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class
)
@Composable
fun PlaylistEditScreen(
    playlistId: String,
    onBackClick: () -> Unit,
    onDeletePlayListClick: () -> Unit = onBackClick,
    playerViewModel: PlayerViewModel,
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
    navController: NavController
) {
    val uiState by playlistViewModel.uiState.collectAsStateWithLifecycle()
    val playerStableState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val fallbackPlaylistName = stringResource(R.string.common_playlist)
    val sortSongsLabel = stringResource(R.string.playlist_sort_songs_title)
    val moreOptionsLabel = stringResource(R.string.playlist_more_options_title)
    val playItLabel = stringResource(R.string.playlist_action_play_it)
    val addSongsCd = stringResource(R.string.playlist_cd_add_songs)
    val addLabel = stringResource(R.string.playlist_action_add_songs)
    val removeLabel = stringResource(R.string.playlist_action_remove_songs)
    val removeSongsCd = stringResource(R.string.playlist_cd_remove_songs)
    val reorderLabel = stringResource(R.string.playlist_action_reorder_songs)
    val reorderSongsCd = stringResource(R.string.playlist_cd_reorder_songs)
    val reorderSongCd = stringResource(R.string.playlist_cd_reorder_songs)
    val playlistEmptyTitle = stringResource(R.string.playlist_empty_title)
    val playlistEmptyFolder = stringResource(R.string.playlist_empty_folder_label)
    val playlistEmptyAddHint = stringResource(R.string.playlist_empty_add_hint)
    val playlistOptionsTitle = stringResource(R.string.playlist_options_title)
    val editCoverLabel = stringResource(R.string.playlist_action_edit_cover)
    val deletePlaylistLabel = stringResource(R.string.playlist_action_delete_playlist)
    val setDefaultTransitionLabel = stringResource(R.string.playlist_action_set_default_transition)
    val deletePlaylistConfirmTitle = stringResource(R.string.playlist_dialog_delete_title)
    val deletePlaylistConfirmBody = stringResource(R.string.playlist_dialog_delete_body)
    val sortSheetTitle = stringResource(R.string.playlist_sort_songs_title)
    val toastAddedToQueue = stringResource(R.string.library_toast_added_to_queue)
    val toastPlayingNext = stringResource(R.string.library_toast_playing_next)
    val currentPlaylist = uiState.currentPlaylistDetails
    val isFolderPlaylist = currentPlaylist?.id?.startsWith(FOLDER_PLAYLIST_PREFIX) == true
    val songsInPlaylist = uiState.currentPlaylistSongs

    LaunchedEffect(playlistId) {
        playlistViewModel.loadPlaylistDetails(playlistId)
    }

    var showAddSongsSheet by remember { mutableStateOf(false) }

    var isReorderModeEnabled by remember { mutableStateOf(false) }
    var isRemoveModeEnabled by remember { mutableStateOf(false) }
    var showSongInfoBottomSheet by remember { mutableStateOf(false) }
    var showPlaylistOptionsSheet by remember { mutableStateOf(false) }
    var showEditPlaylistDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    val selectedSongForInfo by playerViewModel.selectedSongForInfo.collectAsStateWithLifecycle()
    val favoriteIds by playerViewModel.favoriteSongIds.collectAsStateWithLifecycle() // Reintroducir favoriteIds aquí
    val stableOnMoreOptionsClick: (Song) -> Unit = remember {
        { song ->
            playerViewModel.selectSongForInfo(song)
            showSongInfoBottomSheet = true
        }
    }
    val systemNavBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val navBarCompactMode by playerViewModel.navBarCompactMode.collectAsStateWithLifecycle()
    val bottomBarHeightDp = resolveNavBarOccupiedHeight(systemNavBarInset, navBarCompactMode)
    var showPlaylistBottomSheet by remember { mutableStateOf(false) }
    var localReorderableSongs by remember(songsInPlaylist) { mutableStateOf(songsInPlaylist) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val appHapticsConfig = LocalAppHapticsConfig.current
    var lastMovedFrom by remember { mutableStateOf<Int?>(null) }
    var lastMovedTo by remember { mutableStateOf<Int?>(null) }

    val reorderableState = rememberReorderableLazyListState(
        lazyListState = listState,
        onMove = { from, to ->
            localReorderableSongs = localReorderableSongs.toMutableList().apply {
                add(to.index, removeAt(from.index))
            }
            if (lastMovedFrom == null) {
                lastMovedFrom = from.index
            }
            lastMovedTo = to.index
        }
    )

    LaunchedEffect(reorderableState.isAnyItemDragging, isFolderPlaylist) {
        if (!isFolderPlaylist && !reorderableState.isAnyItemDragging && lastMovedFrom != null && lastMovedTo != null) {
            currentPlaylist?.let {
                playlistViewModel.reorderSongsInPlaylist(it.id, lastMovedFrom!!, lastMovedTo!!)
            }
            lastMovedFrom = null
            lastMovedTo = null
        } else if (isFolderPlaylist && !reorderableState.isAnyItemDragging) {
            lastMovedFrom = null
            lastMovedTo = null
        }
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        modifier = Modifier.padding(start = 8.dp),
                        text = currentPlaylist?.name ?: fallbackPlaylistName,
                        fontFamily = GoogleSansRounded,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    scrolledContainerColor = Color.Transparent,
                    containerColor = Color.Transparent
                ),
                subtitle = {
                    Text(
                        modifier = Modifier.padding(start = 8.dp),
                        text = stringResource(
                            R.string.playlist_song_duration_line,
                            formatSongCount(songsInPlaylist.size),
                            formatTotalDuration(songsInPlaylist)
                        ),
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = GoogleSansRounded),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                navigationIcon = {
                    FilledTonalIconButton(
                        modifier = Modifier.padding(start = 10.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        onClick = onBackClick
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back))
                    }
                },
                actions = {
                    val isPinned = uiState.pinnedCourseId == playlistId
                    IconButton(
                        onClick = {
                            playlistViewModel.togglePinPlaylist(playlistId)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PushPin,
                            contentDescription = if (isPinned) "Unpin Course" else "Pin Course",
                            tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = {
                            playerViewModel.showSortingSheet() 
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Sort,
                            contentDescription = sortSongsLabel
                        )
                    }
                    FilledTonalIconButton(
                        modifier = Modifier.padding(end = 10.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        onClick = { showPlaylistOptionsSheet = true }
                    ) { Icon(Icons.Filled.MoreVert, moreOptionsLabel) }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        if (uiState.isLoading && currentPlaylist == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding()), Alignment.Center
            ) { CircularProgressIndicator() }
        } else if (uiState.playlistNotFound) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding()), Alignment.Center
            ) { Text(stringResource(id = R.string.playlist_not_found)) }
        } else if (currentPlaylist == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding()), Alignment.Center
            ) { CircularProgressIndicator() }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding())
            ) {
                val actionButtonsHeight = 42.dp
                val playbackControlBottomPadding = if (isFolderPlaylist) 8.dp else 6.dp
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(62.dp)
                        .padding(horizontal = 20.dp)
                        .padding(bottom = playbackControlBottomPadding),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (localReorderableSongs.isNotEmpty()) {
                                playlistViewModel.playPlaylist(currentPlaylist.id, playerViewModel)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(76.dp),
                        enabled = localReorderableSongs.isNotEmpty(),
                        shape = AbsoluteSmoothCornerShape(
                            cornerRadiusTL = 60.dp,
                            smoothnessAsPercentTR = 60,
                            cornerRadiusTR = 60.dp,
                            smoothnessAsPercentTL = 60,
                            cornerRadiusBL = 60.dp,
                            smoothnessAsPercentBR = 60,
                            cornerRadiusBR = 60.dp,
                            smoothnessAsPercentBL = 60
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                    ) {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(R.string.common_play),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        TightWrapText(
                            text = playItLabel,
                            modifier = Modifier.padding(end = 4.dp),
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 2,
                            lineHeight = 20.sp
                        )
                    }
                }

                if (!isFolderPlaylist) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, bottom = 8.dp, top = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val reorderCornerRadius by animateDpAsState(
                            targetValue = if (isReorderModeEnabled) 24.dp else 12.dp,
                            label = "reorderCornerRadius"
                        )
                        val reorderButtonColor by animateColorAsState(
                            targetValue = if (isReorderModeEnabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceContainerHigh,
                            label = "reorderButtonColor"
                        )
                        val reorderIconColor by animateColorAsState(
                            targetValue = if (isReorderModeEnabled) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurface,
                            label = "reorderIconColor"
                        )

                        val removeCornerRadius by animateDpAsState(
                            targetValue = if (isRemoveModeEnabled) 24.dp else 12.dp,
                            label = "removeCornerRadius"
                        )
                        val removeButtonColor by animateColorAsState(
                            targetValue = if (isRemoveModeEnabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceContainerHigh,
                            label = "removeButtonColor"
                        )
                        val removeIconColor by animateColorAsState(
                            targetValue = if (isRemoveModeEnabled) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurface,
                            label = "removeIconColor"
                        )

                        Button(
                            onClick = { showAddSongsSheet = true },
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                            ),
                            modifier = Modifier
                                .height(actionButtonsHeight)
                                .animateContentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = addSongsCd,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                modifier = Modifier.padding(end = 4.dp),
                                text = addLabel,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        val scrollState = rememberScrollState()
                        val showStartFade by remember { derivedStateOf { scrollState.value > 0 } }
                        val showEndFade by remember { derivedStateOf { scrollState.value < scrollState.maxValue } }

                        BoxWithConstraints(
                            modifier = Modifier
                                .weight(1f)
                                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                .drawWithContent {
                                    drawContent()
                                    val gradientWidth = 8.dp.toPx()

                                    if (showStartFade) {
                                        drawRect(
                                            brush = Brush.horizontalGradient(
                                                colors = listOf(Color.Transparent, Color.Black),
                                                endX = gradientWidth
                                            ),
                                            blendMode = BlendMode.DstIn
                                        )
                                    }

                                    if (showEndFade) {
                                        drawRect(
                                            brush = Brush.horizontalGradient(
                                                colors = listOf(Color.Black, Color.Transparent),
                                                startX = this.size.width - gradientWidth
                                            ),
                                            blendMode = BlendMode.DstIn
                                        )
                                    }
                                }
                        ) {
                            val containerWidthPx = constraints.maxWidth

                            Layout(
                                modifier = Modifier.horizontalScroll(scrollState),
                                content = {
                                    Button(
                                        onClick = { isRemoveModeEnabled = !isRemoveModeEnabled },
                                        shape = RoundedCornerShape(removeCornerRadius),
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = removeButtonColor,
                                            contentColor = removeIconColor
                                        ),
                                        modifier = Modifier
                                            .height(actionButtonsHeight)
                                            .animateContentSize()
                                            .clip(RoundedCornerShape(removeCornerRadius))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.RemoveCircleOutline,
                                            contentDescription = removeSongsCd,
                                            modifier = Modifier.size(16.dp),
                                            tint = removeIconColor
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            modifier = Modifier.padding(end = 4.dp),
                                            text = removeLabel,
                                            color = removeIconColor,
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }

                                    Button(
                                        onClick = { isReorderModeEnabled = !isReorderModeEnabled },
                                        shape = RoundedCornerShape(reorderCornerRadius),
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = reorderButtonColor,
                                            contentColor = reorderIconColor
                                        ),
                                        modifier = Modifier
                                            .height(actionButtonsHeight)
                                            .animateContentSize()
                                            .clip(RoundedCornerShape(reorderCornerRadius))
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.drag_order_icon),
                                            contentDescription = reorderSongsCd,
                                            modifier = Modifier.size(22.dp),
                                            tint = reorderIconColor
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            modifier = Modifier.padding(end = 4.dp),
                                            text = reorderLabel,
                                            color = reorderIconColor,
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            ) { measurables, childConstraints ->
                                val spacingPx = 8.dp.roundToPx()
                                val totalSpacing = spacingPx * (measurables.size - 1)

                                // Query maxIntrinsicWidth of each child without measuring
                                val totalNaturalWidth = measurables.sumOf { measurable ->
                                    measurable.maxIntrinsicWidth(Constraints.Infinity)
                                } + totalSpacing

                                val finalPlaceables = if (totalNaturalWidth <= containerWidthPx && containerWidthPx > 0) {
                                    // Stretch them equally to fill containerWidthPx
                                    val availableSpace = containerWidthPx - totalSpacing
                                    val equalWidth = (availableSpace / measurables.size).coerceAtLeast(0)
                                    measurables.map { measurable ->
                                        measurable.measure(
                                            childConstraints.copy(
                                                minWidth = equalWidth,
                                                maxWidth = equalWidth
                                            )
                                        )
                                    }
                                } else {
                                    // Measure naturally
                                    measurables.map { measurable ->
                                        measurable.measure(
                                            childConstraints.copy(
                                                minWidth = 0,
                                                maxWidth = Constraints.Infinity
                                            )
                                        )
                                    }
                                }

                                val layoutWidth = if (totalNaturalWidth <= containerWidthPx && containerWidthPx > 0) {
                                    containerWidthPx
                                } else {
                                    totalNaturalWidth
                                }

                                val height = finalPlaceables.maxOfOrNull { it.height } ?: 0

                                layout(layoutWidth, height) {
                                    var xPosition = 0
                                    finalPlaceables.forEach { placeable ->
                                        placeable.placeRelative(x = xPosition, y = 0)
                                        xPosition += placeable.width + spacingPx
                                    }
                                }
                            }
                        }
                    }
                }

                if (localReorderableSongs.isEmpty()) {
                    Box(Modifier
                        .fillMaxSize()
                        .weight(1f), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.MusicOff, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Text(playlistEmptyTitle, style = MaterialTheme.typography.titleMedium)
                            val emptyMessage = if (isFolderPlaylist) {
                                playlistEmptyFolder
                            } else {
                                playlistEmptyAddHint
                            }
                            Text(emptyMessage, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                            //.padding(horizontal = 10.dp)
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(
                                    AbsoluteSmoothCornerShape(
                                        cornerRadiusTR = 32.dp,
                                        smoothnessAsPercentTR = 60,
                                        cornerRadiusTL = 32.dp,
                                        smoothnessAsPercentTL = 60,
                                    )
                                )
                                .background(color = MaterialTheme.colorScheme.surfaceContainerHigh),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(
                                top = 12.dp,
                                start = 16.dp,
                                end = 16.dp,
                                bottom = MiniPlayerHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
                            )
                        ) {
                            itemsIndexed(
                                localReorderableSongs,
                                key = { _, item -> item.id },
                                contentType = { _, _ -> "playlist_song" }) { _, song ->
                                ReorderableItem(
                                    state = reorderableState,
                                    key = song.id,
                                ) { isDragging ->
                                    val scale by animateFloatAsState(
                                        if (isDragging) 1.05f else 1f,
                                        label = "scale"
                                    )

                                    EnhancedSongListItem(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .graphicsLayer {
                                                scaleX = scale
                                                scaleY = scale
                                            },
                                        song = song,
                                        isCurrentSong = playerStableState.currentSong?.id == song.id,
                                        isPlaying = playerStableState.isPlaying,
                                        isDragging = isDragging,
                                        showAlbumArt = false,
                                        dragHandle = if (isReorderModeEnabled) {
                                            {
                                                IconButton(
                                                    onClick = {},
                                                    modifier = Modifier
                                                        .draggableHandle(
                                                            onDragStarted = {
                                                                performAppCompatHapticFeedback(
                                                                    view,
                                                                    appHapticsConfig,
                                                                    HapticFeedbackConstantsCompat.GESTURE_START
                                                                )
                                                            },
                                                            onDragStopped = {
                                                                performAppCompatHapticFeedback(
                                                                    view,
                                                                    appHapticsConfig,
                                                                    HapticFeedbackConstantsCompat.GESTURE_END
                                                                )
                                                            }
                                                        )
                                                        .size(36.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.DragIndicator,
                                                        contentDescription = reorderSongCd,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        } else null,
                                        trailingContent = if (isRemoveModeEnabled && !isFolderPlaylist) {
                                            {
                                                IconButton(
                                                    onClick = {
                                                        currentPlaylist.let {
                                                            playlistViewModel.removeSongFromPlaylist(it.id, song.id)
                                                        }
                                                    },
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Icon(
                                                        painter = painterResource(R.drawable.rounded_close_24),
                                                        contentDescription = stringResource(R.string.queue_cd_remove_from_playlist),
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        } else null,
                                        onMoreOptionsClick = if (!isRemoveModeEnabled) stableOnMoreOptionsClick else null,
                                        onClick = {
                                            playerViewModel.playSongs(
                                                localReorderableSongs,
                                                song,
                                                currentPlaylist.name,
                                                currentPlaylist.id
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        ExpressiveScrollBar(
                            listState = listState,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(
                                    bottom = if (playerStableState.currentSong != null) MiniPlayerHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 20.dp else WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp,
                                    end = 6.dp,
                                    top = 18.dp // Increased to 16.dp as requested
                                )
                        )
                    }
                }
            }
        }
    }

    if (showAddSongsSheet && currentPlaylist != null && !isFolderPlaylist) {
        SongPickerBottomSheet(
            initiallySelectedSongIds = currentPlaylist.songIds.toSet(),
            onDismiss = { showAddSongsSheet = false },
            onConfirm = { selectedIds ->
                playlistViewModel.addSongsToPlaylist(currentPlaylist.id, selectedIds.toList())
                showAddSongsSheet = false
            }
        )
    }
    if (showPlaylistOptionsSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { showPlaylistOptionsSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 4.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = playlistOptionsTitle,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    currentPlaylist?.name?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                currentPlaylist?.let { playlist ->
                    AiCourseReorderSection(
                        uiState = uiState,
                        playlistId = playlist.id,
                        onToggleUseAiReorder = {
                            playlistViewModel.toggleUseAiReorder(playlist.id)
                        },
                        onToggleUseAiTitles = {
                            playlistViewModel.toggleUseAiCleanedTitles(playlist.id)
                        },
                        onRerunAiReorder = {
                            playlistViewModel.rerunAiReorder(playlist.id)
                        }
                    )
                }
                if (!isFolderPlaylist) {
                    PlaylistActionItem(
                        icon = painterResource(R.drawable.rounded_edit_24),
                        label = editCoverLabel,
                        onClick = {
                            showPlaylistOptionsSheet = false
                            showEditPlaylistDialog = true
                        }
                    )
                    PlaylistActionItem(
                        icon = painterResource(R.drawable.rounded_delete_24),
                        label = deletePlaylistLabel,
                        onClick = {
                            showPlaylistOptionsSheet = false
                            showDeleteConfirmation = true
                        }
                    )
                }
            }
        }
    }

    val aiReorderMessage = uiState.aiReorderMessage
    LaunchedEffect(aiReorderMessage) {
        if (!aiReorderMessage.isNullOrBlank()) {
            playerViewModel.sendToast(aiReorderMessage)
            playlistViewModel.clearAiReorderMessage()
        }
    }

    if (uiState.isAiReordering) {
        Dialog(
            onDismissRequest = { /* Non-dismissible while processing */ }
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                    Text(
                        text = stringResource(R.string.playlist_ai_reorder_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
    
    if (showEditPlaylistDialog && currentPlaylist != null) {
        val initialShapeType = try {
            currentPlaylist.coverShapeType?.let { PlaylistShapeType.valueOf(it) } ?: PlaylistShapeType.Circle
        } catch (e: Exception) {
            PlaylistShapeType.Circle
        }
        
        EditPlaylistDialog(
            visible = showEditPlaylistDialog,
            currentName = currentPlaylist.name,
            currentImageUri = currentPlaylist.coverImageUri,
            currentColor = currentPlaylist.coverColorArgb,
            currentIconName = currentPlaylist.coverIconName,
            currentShapeType = initialShapeType,
            currentShapeDetail1 = currentPlaylist.coverShapeDetail1,
            currentShapeDetail2 = currentPlaylist.coverShapeDetail2,
            currentShapeDetail3 = currentPlaylist.coverShapeDetail3,
            currentShapeDetail4 = currentPlaylist.coverShapeDetail4,
            onDismiss = { showEditPlaylistDialog = false },
            onSave = { name, imageUri, color, icon, scale, panX, panY, shapeType, d1, d2, d3, d4 ->
                playlistViewModel.updatePlaylistParameters(
                    playlistId = currentPlaylist.id,
                    name = name,
                    coverImageUri = imageUri,
                    coverColor = color,
                    coverIcon = icon,
                    cropScale = scale,
                    cropPanX = panX,
                    cropPanY = panY,
                    coverShapeType = shapeType,
                    coverShapeDetail1 = d1,
                    coverShapeDetail2 = d2,
                    coverShapeDetail3 = d3,
                    coverShapeDetail4 = d4
                )
                showEditPlaylistDialog = false
            }
        )
    }
    if (showDeleteConfirmation && currentPlaylist != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(deletePlaylistConfirmTitle) },
            text = {
                Text(deletePlaylistConfirmBody)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        playlistViewModel.deletePlaylist(currentPlaylist.id)
                        onDeletePlayListClick()
                        showDeleteConfirmation = false
                    }
                ) {
                    Text(stringResource(R.string.common_delete), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(stringResource(R.string.common_cancel), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        )
    }

    if (showSongInfoBottomSheet && selectedSongForInfo != null) {
        val currentSong = selectedSongForInfo
        val isFavorite = remember(currentSong?.id, favoriteIds) {
            derivedStateOf {
                currentSong?.let {
                    favoriteIds.contains(
                        it.id
                    )
                }
            }
        }.value ?: false

        if (currentSong != null) {
            SongInfoBottomSheet(
                song = currentSong,
                isFavorite = isFavorite,
                onToggleFavorite = {
                    // Directly use PlayerViewModel's method to toggle, which should handle UserPreferencesRepository
                    playerViewModel.toggleFavoriteSpecificSong(currentSong) // Assumes such a method exists or will be added to PlayerViewModel
                },
                onDismiss = { showSongInfoBottomSheet = false },
                onPlaySong = {
                    playerViewModel.playSongs(
                        songsToPlay = songsInPlaylist,
                        startSong = currentSong,
                        queueName = currentPlaylist?.name ?: "Course",
                        playlistId = playlistId,
                        playlistCoverUri = currentPlaylist?.coverImageUri
                    )
                },
                onAddToQueue = {
                    playerViewModel.addSongToQueue(currentSong) // Assumes such a method exists or will be added
                    playerViewModel.sendToast(toastAddedToQueue)
                },
                onAddNextToQueue = {
                    playerViewModel.addSongNextToQueue(currentSong)
                    playerViewModel.sendToast(toastPlayingNext)
                },
                onAddToPlayList = {
                    showPlaylistBottomSheet = true;
                },
                onDeleteFromDevice = playerViewModel::deleteFromDevice,
                onNavigateToAlbum = {
                    navController.navigateSafelyReplacing(
                        route = Screen.PlaylistDetail.createRoute(currentSong.resolveCoursePlaylistId(playlistId)),
                        patternToPop = Screen.PlaylistDetail.route
                    )
                    showSongInfoBottomSheet = false
                },
                onNavigateToArtist = {
                    navController.navigateSafelyReplacing(
                        route = Screen.ArtistDetail.createRoute(currentSong.artistId),
                        patternToPop = Screen.ArtistDetail.route
                    )
                    showSongInfoBottomSheet = false
                },
                onNavigateToArtistById = { artistId ->
                    navController.navigateSafelyReplacing(
                        route = Screen.ArtistDetail.createRoute(artistId),
                        patternToPop = Screen.ArtistDetail.route
                    )
                    showSongInfoBottomSheet = false
                },
                onNavigateToGenre = {
                    currentSong.genre?.let {
                        navController.navigateSafelyReplacing(
                            route = Screen.GenreDetail.createRoute(java.net.URLEncoder.encode(it, "UTF-8")),
                            patternToPop = Screen.GenreDetail.route
                        )
                    }
                    showSongInfoBottomSheet = false
                },
                onEditSong = { newTitle, newArtist, newAlbum, newAlbumArtist, newComposer, newGenre, newLyrics, newTrackNumber, newDiscNumber, replayGainTrackGainDb, replayGainAlbumGainDb, coverArtUpdate ->
                    playerViewModel.editSongMetadata(
                        currentSong,
                        newTitle,
                        newArtist,
                        newAlbum,
                        newAlbumArtist,
                        newComposer,
                        newGenre,
                        newLyrics,
                        newTrackNumber,
                        newDiscNumber,
                        replayGainTrackGainDb,
                        replayGainAlbumGainDb,
                        coverArtUpdate
                    )
                },
                removeFromListTrigger = {
                    playlistViewModel.removeSongFromPlaylist(playlistId, currentSong.id)
                }
            )
            if (showPlaylistBottomSheet) {
                val playlistUiState by playlistViewModel.uiState.collectAsStateWithLifecycle()

                PlaylistBottomSheet(
                    playlistUiState = playlistUiState,
                    songs = listOf(currentSong),
                    onDismiss = {
                        showPlaylistBottomSheet = false
                    },
                    currentPlaylistId = playlistId,
                    bottomBarHeight = bottomBarHeightDp,
                    playerViewModel = playerViewModel,
                )
            }
        }
    }

    val isSortSheetVisible by playerViewModel.isSortingSheetVisible.collectAsStateWithLifecycle()

    if (isSortSheetVisible) {
        // Check if playlist is in Manual mode (which corresponds to Default Order)
        val isManualMode = uiState.playlistSongsOrderMode is PlaylistSongsOrderMode.Manual
        val rawOption = uiState.currentPlaylistSongsSortOption
        // If in Manual mode, show SongDefaultOrder as selected; otherwise use the stored sort option
        val currentSortOption = if (isManualMode) {
            SortOption.SongDefaultOrder
        } else if (currentPlaylist != null) {
            rawOption
        } else {
            SortOption.SongTitleAZ
        }

        // Build options list inline to avoid potential static initialization issues
        val songSortOptions = listOf(
            SortOption.SongDefaultOrder,
            SortOption.SongTitleAZ,
            SortOption.SongTitleZA,
            SortOption.SongArtist,
            SortOption.SongArtistDesc,
            SortOption.SongAlbum,
            SortOption.SongAlbumDesc,
            SortOption.SongDateAdded,
            SortOption.SongDateAddedAsc,
            SortOption.SongDuration,
            SortOption.SongDurationAsc
        )

        LibrarySortBottomSheet(
            title = sortSheetTitle,
            options = songSortOptions,
            selectedOption = currentSortOption,
            onDismiss = { playerViewModel.hideSortingSheet() },
            onOptionSelected = { option ->
                 playlistViewModel.sortPlaylistSongs(option)
                 playerViewModel.hideSortingSheet()
                 // Auto-scroll to first item after sorting (delay to allow list to update)
                 scope.launch {
                     kotlinx.coroutines.delay(100)
                     listState.animateScrollToItem(0)
                 }
            },
            onDirectionToggle = { option ->
                playlistViewModel.sortPlaylistSongs(option)
                scope.launch {
                    kotlinx.coroutines.delay(100)
                    listState.animateScrollToItem(0)
                }
            },
            showViewToggle = false 
        )
    }
}


@Composable
private fun AiCourseReorderSection(
    uiState: PlaylistUiState,
    playlistId: String,
    onToggleUseAiReorder: () -> Unit,
    onToggleUseAiTitles: () -> Unit,
    onRerunAiReorder: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(true) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "chevron_rotation"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.playlist_action_ai_course_group_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.playlist_action_ai_course_group_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer { rotationZ = chevronRotation }
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp, bottom = 10.dp)
            ) {
                if (!uiState.hasAiReorderData) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = stringResource(R.string.playlist_ai_first_time_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                PlaylistNestedSwitchItem(
                    icon = rememberVectorPainter(Icons.AutoMirrored.Rounded.Sort),
                    label = stringResource(R.string.playlist_action_use_ai_reorder),
                    description = stringResource(R.string.playlist_action_use_ai_reorder_desc),
                    checked = uiState.isUsingAiReorder,
                    onCheckedChange = { onToggleUseAiReorder() }
                )

                if (uiState.hasAiCleanedTitles) {
                    PlaylistNestedSwitchItem(
                        icon = rememberVectorPainter(Icons.Rounded.DriveFileRenameOutline),
                        label = stringResource(R.string.playlist_action_use_ai_cleaned_title),
                        description = stringResource(R.string.playlist_action_use_ai_cleaned_title_desc),
                        checked = uiState.isUsingAiCleanedTitles,
                        onCheckedChange = { onToggleUseAiTitles() }
                    )
                }

                PlaylistNestedActionItem(
                    icon = rememberVectorPainter(Icons.Rounded.Refresh),
                    label = stringResource(R.string.playlist_action_rerun_ai_reorder),
                    description = stringResource(R.string.playlist_action_rerun_ai_reorder_desc),
                    onClick = onRerunAiReorder
                )
            }
        }
    }
}

@Composable
private fun PlaylistNestedSwitchItem(
    icon: Painter,
    label: String,
    description: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = icon,
                contentDescription = label,
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun PlaylistNestedActionItem(
    icon: Painter,
    label: String,
    description: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun PlaylistActionItem(
    icon: Painter,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun PlaylistSwitchItem(
    icon: Painter,
    label: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
