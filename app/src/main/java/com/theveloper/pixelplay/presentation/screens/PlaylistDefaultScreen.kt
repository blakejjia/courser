@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.theveloper.pixelplay.presentation.screens

import com.theveloper.pixelplay.presentation.navigation.navigateSafely
import com.theveloper.pixelplay.presentation.navigation.navigateSafelyReplacing

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.ui.theme.LocalPixelPlayDarkTheme
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.ui.theme.PixelPlayStatusBarStyle
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import coil.compose.AsyncImagePainter
import coil.size.Size
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Playlist
import androidx.compose.foundation.shape.RoundedCornerShape
import com.theveloper.pixelplay.presentation.components.CourseGridCover
import com.theveloper.pixelplay.presentation.components.CollapsibleCommonTopBar
import com.theveloper.pixelplay.presentation.components.ExpressiveScrollBar
import com.theveloper.pixelplay.ui.theme.LocalShowScrollbar
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.PlaylistBottomSheet
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.SongInfoBottomSheet
import com.theveloper.pixelplay.presentation.components.resolveNavBarOccupiedHeight
import com.theveloper.pixelplay.presentation.components.subcomps.EnhancedSongListItem
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlaylistViewModel
import com.theveloper.pixelplay.utils.shapes.RoundedStarShape
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource

/**
 * Default Course screen in Courser (adapted from the legacy Album detail view).
 * Presents a clean, immersive header, course title, total count, play/resume FAB,
 * and an Edit action button in the top-right corner to open [PlaylistEditScreen].
 */
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlaylistDefaultScreen(
    playlistId: String,
    navController: NavController,
    playerViewModel: PlayerViewModel,
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
    onBackClick: () -> Unit = { navController.popBackStack() },
    onEditClick: () -> Unit = {
        navController.navigateSafely(Screen.PlaylistEdit.createRoute(playlistId))
    }
) {
    DisposableEffect(playlistId) {
        playlistViewModel.loadPlaylistDetails(playlistId)
        onDispose {
            playlistViewModel.clearPlaylistDetails()
        }
    }

    val playlistUiState by playlistViewModel.uiState.collectAsStateWithLifecycle()
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val favoriteIds by playerViewModel.favoriteSongIds.collectAsStateWithLifecycle()
    val navBarCompactMode by playerViewModel.navBarCompactMode.collectAsStateWithLifecycle()

    var showSongInfoBottomSheet by remember { mutableStateOf(false) }
    val selectedSongForInfo by playerViewModel.selectedSongForInfo.collectAsStateWithLifecycle()
    val systemNavBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomBarHeightDp = resolveNavBarOccupiedHeight(systemNavBarInset, navBarCompactMode)
    var showPlaylistBottomSheet by remember { mutableStateOf(false) }
    val isDarkTheme = LocalPixelPlayDarkTheme.current
    val baseColorScheme = MaterialTheme.colorScheme

    val currentPlaylist = playlistUiState.currentPlaylistDetails
    val songs = playlistUiState.currentPlaylistSongs

    val coverArtUri = currentPlaylist?.coverImageUri?.takeIf { it.isNotBlank() }
    val courseColorSchemeFlow = remember(coverArtUri) {
        coverArtUri?.let { playerViewModel.themeStateHolder.getAlbumColorSchemeFlow(it, eager = false) }
    }
    val courseColorSchemePair = courseColorSchemeFlow?.collectAsStateWithLifecycle()?.value
    val courseColorScheme = remember(courseColorSchemePair, isDarkTheme, baseColorScheme) {
        courseColorSchemePair?.let { pair -> if (isDarkTheme) pair.dark else pair.light }
            ?: baseColorScheme
    }

    var headerArtworkLoaded by remember(coverArtUri) { mutableStateOf(coverArtUri == null) }
    var themeRequestIssued by remember(coverArtUri) { mutableStateOf(coverArtUri == null) }

    LaunchedEffect(coverArtUri, headerArtworkLoaded, themeRequestIssued) {
        if (!themeRequestIssued && headerArtworkLoaded && coverArtUri != null) {
            themeRequestIssued = true
            playerViewModel.themeStateHolder.ensureAlbumColorScheme(coverArtUri)
        }
    }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    MaterialTheme(
        colorScheme = courseColorScheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes
    ) {
        val isMiniPlayerVisible = stablePlayerState.currentSong != null
        val fabBottomPadding by animateDpAsState(
            targetValue = if (isMiniPlayerVisible) MiniPlayerHeight + 16.dp else 16.dp,
            label = "fabPadding"
        )

        when {
            playlistUiState.isLoading && currentPlaylist == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ContainedLoadingIndicator()
                }
            }

            playlistUiState.playlistNotFound && currentPlaylist == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Course not found",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            currentPlaylist != null -> {
                val lazyListState = rememberLazyListState()

                val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                val minTopBarHeight = 64.dp + statusBarHeight
                val maxTopBarHeight = 300.dp

                val minTopBarHeightPx = with(density) { minTopBarHeight.toPx() }
                val maxTopBarHeightPx = with(density) { maxTopBarHeight.toPx() }

                val headerImageRequestSize = remember(
                    configuration.screenWidthDp,
                    density.density,
                    maxTopBarHeightPx
                ) {
                    Size(
                        width = with(density) { configuration.screenWidthDp.dp.roundToPx() },
                        height = maxTopBarHeightPx.roundToInt()
                    )
                }

                val topBarHeight = remember { Animatable(maxTopBarHeightPx) }
                val collapseFraction by remember(minTopBarHeightPx, maxTopBarHeightPx) {
                    derivedStateOf {
                        1f - ((topBarHeight.value - minTopBarHeightPx) / (maxTopBarHeightPx - minTopBarHeightPx)).coerceIn(
                            0f,
                            1f
                        )
                    }
                }

                val nestedScrollConnection = remember {
                    object : NestedScrollConnection {
                        override fun onPreScroll(
                            available: Offset,
                            source: NestedScrollSource
                        ): Offset {
                            val delta = available.y
                            val isScrollingDown = delta < 0

                            if (!isScrollingDown && (lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 0)) {
                                return Offset.Zero
                            }

                            val previousHeight = topBarHeight.value
                            val newHeight =
                                (previousHeight + delta).coerceIn(minTopBarHeightPx, maxTopBarHeightPx)
                            val consumed = newHeight - previousHeight

                            if (consumed.roundToInt() != 0) {
                                coroutineScope.launch {
                                    topBarHeight.snapTo(newHeight)
                                }
                            }

                            val canConsumeScroll = !(isScrollingDown && newHeight == minTopBarHeightPx)
                            return if (canConsumeScroll) Offset(0f, consumed) else Offset.Zero
                        }

                        override suspend fun onPostFling(
                            consumed: Velocity,
                            available: Velocity
                        ): Velocity {
                            return super.onPostFling(consumed, available)
                        }
                    }
                }

                LaunchedEffect(lazyListState.isScrollInProgress) {
                    if (!lazyListState.isScrollInProgress) {
                        val shouldExpand =
                            topBarHeight.value > (minTopBarHeightPx + maxTopBarHeightPx) / 2
                        val canExpand =
                            lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset == 0

                        val targetValue = if (shouldExpand && canExpand) {
                            maxTopBarHeightPx
                        } else {
                            minTopBarHeightPx
                        }

                        if (topBarHeight.value != targetValue) {
                            coroutineScope.launch {
                                topBarHeight.animateTo(
                                    targetValue,
                                    spring(stiffness = Spring.StiffnessMedium)
                                )
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            color = MaterialTheme.colorScheme.surface
                        )
                        .nestedScroll(nestedScrollConnection)
                ) {
                    val currentTopBarHeightDp = with(density) { topBarHeight.value.toDp() }
                    val showScrollBar =
                        LocalShowScrollbar.current &&
                        collapseFraction > 0.95f &&
                            (lazyListState.canScrollForward || lazyListState.canScrollBackward)

                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .offset {
                                val extraHeight =
                                    (topBarHeight.value - minTopBarHeightPx).roundToInt()
                                IntOffset(0, extraHeight)
                            },
                        contentPadding = PaddingValues(
                            top = minTopBarHeight + 8.dp,
                            start = 16.dp,
                            end = if (showScrollBar) 24.dp else 16.dp,
                            bottom = fabBottomPadding + 80.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = songs,
                            key = { song -> "course_song_${song.id}" },
                            contentType = { "course_song" }
                        ) { song ->
                            EnhancedSongListItem(
                                song = song,
                                isCurrentSong = stablePlayerState.currentSong?.id == song.id,
                                isPlaying = stablePlayerState.isPlaying,
                                showAlbumArt = false,
                                onMoreOptionsClick = {
                                    playerViewModel.selectSongForInfo(song)
                                    showSongInfoBottomSheet = true
                                },
                                onClick = {
                                    playerViewModel.playSongs(
                                        songsToPlay = songs,
                                        startSong = song,
                                        queueName = currentPlaylist.name,
                                        playlistId = currentPlaylist.id,
                                        playlistCoverUri = currentPlaylist.coverImageUri
                                    )
                                }
                            )
                        }
                    }

                    if (showScrollBar) {
                        ExpressiveScrollBar(
                            listState = lazyListState,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(
                                    top = minTopBarHeight + 12.dp,
                                    bottom = fabBottomPadding + 80.dp
                                )
                        )
                    }

                    // Collapsible Header Top Bar
                    SharedPlaylistTopBar(
                        playlist = currentPlaylist,
                        songsCount = songs.size,
                        collapseFraction = collapseFraction,
                        headerHeight = currentTopBarHeightDp,
                        headerImageRequestSize = headerImageRequestSize,
                        onHeaderArtworkState = { state ->
                            if (state is AsyncImagePainter.State.Success) {
                                headerArtworkLoaded = true
                            }
                        },
                        onBackPressed = onBackClick,
                        onEditClick = onEditClick,
                        onPlayClick = {
                            if (songs.isNotEmpty()) {
                                playlistViewModel.playPlaylist(currentPlaylist.id, playerViewModel)
                            }
                        }
                    )
                }
            }
        }

        if (showSongInfoBottomSheet && selectedSongForInfo != null) {
            val currentSong = selectedSongForInfo
            val isFavorite = remember(currentSong?.id, favoriteIds) {
                derivedStateOf { currentSong?.let { favoriteIds.contains(it.id) } }
            }.value ?: false

            if (currentSong != null) {
                SongInfoBottomSheet(
                    song = currentSong,
                    isFavorite = isFavorite,
                    onToggleFavorite = {
                        playerViewModel.toggleFavoriteSpecificSong(currentSong)
                    },
                    onDismiss = { showSongInfoBottomSheet = false },
                    onPlaySong = {
                        playerViewModel.showAndPlaySong(currentSong)
                    },
                    onAddToQueue = {
                        playerViewModel.addSongToQueue(currentSong)
                    },
                    onAddNextToQueue = {
                        playerViewModel.addSongNextToQueue(currentSong)
                    },
                    onAddToPlayList = {
                        showPlaylistBottomSheet = true
                    },
                    onDeleteFromDevice = playerViewModel::deleteFromDevice,
                    onNavigateToAlbum = {
                        showSongInfoBottomSheet = false
                    },
                    onNavigateToArtist = {
                        showSongInfoBottomSheet = false
                    },
                    onNavigateToArtistById = {
                        showSongInfoBottomSheet = false
                    },
                    onNavigateToGenre = {
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
                    }
                )
                if (showPlaylistBottomSheet) {
                    PlaylistBottomSheet(
                        playlistUiState = playlistUiState,
                        songs = listOf(currentSong),
                        onDismiss = { showPlaylistBottomSheet = false },
                        bottomBarHeight = bottomBarHeightDp,
                        playerViewModel = playerViewModel
                    )
                }
            }
        }
    }
}

@Composable
private fun SharedPlaylistTopBar(
    playlist: Playlist,
    songsCount: Int,
    collapseFraction: Float,
    headerHeight: Dp,
    headerImageRequestSize: Size,
    onHeaderArtworkState: ((AsyncImagePainter.State) -> Unit)? = null,
    onBackPressed: () -> Unit,
    onEditClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val statusBarColor =
        if (LocalPixelPlayDarkTheme.current) Color.Black.copy(alpha = 0.6f)
        else Color.White.copy(alpha = 0.4f)
    val solidAlpha = (collapseFraction * 2f).coerceIn(0f, 1f)
    val expandedContentAlpha = 1f - solidAlpha
    val headerOverlayBrush = remember(surfaceColor, expandedContentAlpha) {
        Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                surfaceColor.copy(alpha = 0.22f * expandedContentAlpha),
                surfaceColor.copy(alpha = 0.82f * expandedContentAlpha),
                surfaceColor
            )
        )
    }
    val statusBarBrush = remember(statusBarColor) {
        Brush.verticalGradient(colors = listOf(statusBarColor, Color.Transparent))
    }
    val expandedStatusBarFallback = remember(statusBarColor, surfaceColor) {
        statusBarColor.compositeOver(surfaceColor)
    }
    val fallbackStatusBarColor = remember(expandedStatusBarFallback, surfaceColor, solidAlpha) {
        lerpColor(expandedStatusBarFallback, surfaceColor, solidAlpha)
    }
    val titleVerticalBias = lerp(1f, -1f, collapseFraction)
    val playAlignment = BiasAlignment(horizontalBias = 1f, verticalBias = titleVerticalBias)

    PixelPlayStatusBarStyle(color = fallbackStatusBarColor)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(headerHeight)
            .clipToBounds()
    ) {
        if (expandedContentAlpha > 0.01f) {
            if (!playlist.coverImageUri.isNullOrBlank()) {
                SmartImage(
                    model = playlist.coverImageUri,
                    contentDescription = playlist.name,
                    contentScale = ContentScale.Crop,
                    targetSize = headerImageRequestSize,
                    allowHardware = true,
                    crossfadeDurationMillis = 0,
                    alpha = expandedContentAlpha,
                    onState = onHeaderArtworkState,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (playlist.coverIconName?.startsWith("text:") == true) {
                CourseGridCover(
                    title = playlist.name,
                    customText = playlist.coverIconName.removePrefix("text:"),
                    colorArgb = playlist.coverColorArgb,
                    shape = RoundedCornerShape(0.dp),
                    modifier = Modifier.fillMaxSize()
                )
            } else if (playlist.coverColorArgb != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(playlist.coverColorArgb).copy(alpha = expandedContentAlpha)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_music_placeholder),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = expandedContentAlpha),
                        modifier = Modifier.size(72.dp)
                    )
                }
            } else {
                CourseGridCover(
                    title = playlist.name,
                    colorArgb = playlist.coverColorArgb,
                    shape = RoundedCornerShape(0.dp),
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(headerOverlayBrush)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .background(statusBarBrush)
                .align(Alignment.TopCenter)
        )

        CollapsibleCommonTopBar(
            title = playlist.name,
            subtitle = "$songsCount total",
            collapseFraction = collapseFraction,
            headerHeight = headerHeight,
            onBackClick = onBackPressed,
            containerColor = surfaceColor.copy(alpha = solidAlpha),
            collapsedTitleStartPadding = 68.dp,
            expandedTitleStartPadding = 24.dp,
            collapsedTitleEndPadding = 80.dp,
            expandedTitleEndPadding = 136.dp,
            containerHeightRange = 112.dp to 56.dp,
            titleStyle = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.SemiBold,
                textGeometricTransform = TextGeometricTransform(scaleX = 1.08f)
            ),
            titleScaleRange = 1f to 1f,
            titleFontSizeRange = 30.sp to 18.sp,
            maxLines = if (collapseFraction < 0.5f) 2 else 1,
            collapsedSubtitleMaxLines = 1,
            expandedSubtitleMaxLines = 2,
            contentColor = MaterialTheme.colorScheme.onSurface,
            subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant,
            fadeSubtitleOnCollapse = false,
            syncStatusBarWithContainer = false,
            actions = {
                FilledTonalIconButton(
                    onClick = onEditClick,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Edit Course"
                    )
                }
            }
        )

        LargeExtendedFloatingActionButton(
            onClick = onPlayClick,
            shape = RoundedStarShape(sides = 8, curve = 0.05, rotation = 0f),
            modifier = Modifier
                .align(playAlignment)
                .statusBarsPadding()
                .padding(end = 16.dp)
                .graphicsLayer {
                    scaleX = expandedContentAlpha
                    scaleY = expandedContentAlpha
                    alpha = expandedContentAlpha
                }
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = "Play course")
        }
    }
}
