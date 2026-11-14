@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.theveloper.pixelplay.presentation.screens

import android.content.Intent
import androidx.activity.compose.ReportDrawnWhen
import com.theveloper.pixelplay.data.repository.CourseSyncProgress
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Playlist
import com.theveloper.pixelplay.presentation.components.BetaInfoBottomSheet
import com.theveloper.pixelplay.presentation.components.ChangelogBottomSheet
import com.theveloper.pixelplay.presentation.components.AddCourseSourceBottomSheet
import com.theveloper.pixelplay.presentation.components.HomeGradientTopBar
import com.theveloper.pixelplay.presentation.components.CourseTile
import com.theveloper.pixelplay.presentation.components.CourserLazyColumn
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.PlaylistItem
import com.theveloper.pixelplay.presentation.components.StreamingProviderSheet
import com.theveloper.pixelplay.presentation.gdrive.auth.GDriveLoginActivity
import com.theveloper.pixelplay.presentation.gdrive.dashboard.GDriveDashboardViewModel
import com.theveloper.pixelplay.presentation.jellyfin.dashboard.JellyfinDashboardViewModel
import com.theveloper.pixelplay.presentation.navidrome.dashboard.NavidromeDashboardViewModel
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.navigation.navigateSafely
import com.theveloper.pixelplay.presentation.viewmodel.DailyGoalViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlannedCourse
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlaylistViewModel
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import kotlinx.collections.immutable.toImmutableList
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

/**
 * Course Player Home Screen.
 * Displays "Today's Learn" (all enrolled courses once with custom journey annotation)
 * and "All Courses" (all course playlists from Google Drive and local storage).
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun HomeScreen(
    navController: NavController,
    paddingValuesParent: PaddingValues,
    playerViewModel: PlayerViewModel = hiltViewModel(),
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
    gdriveViewModel: GDriveDashboardViewModel = hiltViewModel(),
    navidromeViewModel: NavidromeDashboardViewModel = hiltViewModel(),
    jellyfinViewModel: JellyfinDashboardViewModel = hiltViewModel(),
    dailyGoalViewModel: DailyGoalViewModel = hiltViewModel(),
    onOpenSidebar: () -> Unit = {}
) {
    val context = LocalContext.current
    val playlistUiState by playlistViewModel.uiState.collectAsStateWithLifecycle()
    val dailyGoalUiState by dailyGoalViewModel.uiState.collectAsStateWithLifecycle()
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val isSyncing by playerViewModel.isCourseSyncing.collectAsStateWithLifecycle()
    val syncProgress by playerViewModel.courseSyncProgress.collectAsStateWithLifecycle()
    val allCourses = playlistUiState.playlists
    val plannedCourses = dailyGoalUiState.plannedCourses
    val keepSession = dailyGoalUiState.keepListeningSession
    val pinnedCourseId = playlistUiState.pinnedCourseId
    val courseProgressMap = playlistUiState.courseProgressMap

    val activeCourse = remember(keepSession, allCourses, pinnedCourseId) {
        allCourses.find { it.id == keepSession?.sourceCourseId }
            ?: allCourses.find { it.id == pinnedCourseId }
            ?: allCourses.firstOrNull()
    }

    val listState = rememberSaveable(saver = androidx.compose.foundation.lazy.LazyListState.Saver) {
        androidx.compose.foundation.lazy.LazyListState()
    }
    val density = LocalDensity.current
    val scrollThresholdPx = remember(density) { with(density) { 180.dp.toPx() } }
    val isScrolledPastThreshold = remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > scrollThresholdPx }
    }

    var showChangelogBottomSheet by remember { mutableStateOf(false) }
    var showBetaInfoBottomSheet by remember { mutableStateOf(false) }
    var showStreamingProviderSheet by remember { mutableStateOf(false) }
    var showAddCourseSourceSheet by remember { mutableStateOf(false) }

    ReportDrawnWhen {
        allCourses.isNotEmpty() || !playlistUiState.isLoading
    }

    val cardShape = AbsoluteSmoothCornerShape(
        cornerRadiusTR = 24.dp, cornerRadiusTL = 24.dp,
        cornerRadiusBR = 24.dp, cornerRadiusBL = 24.dp,
        smoothnessAsPercentTR = 60, smoothnessAsPercentTL = 60,
        smoothnessAsPercentBR = 60, smoothnessAsPercentBL = 60
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            HomeGradientTopBar(
                isScrolled = isScrolledPastThreshold.value,
                isSyncing = isSyncing,
                syncText = if (syncProgress.total > 0) "Syncing (${syncProgress.current}/${syncProgress.total})..." else "Syncing..."
            )
        }
    ) { innerPadding ->
        CourserLazyColumn(
            playerViewModel = playerViewModel,
            hasBottomNavBar = true,
            extraBottomPadding = 36.dp,
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding()
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ────────────────────────────────────────────────────────────────
            // 1. EMPTY STATE (When no courses are enrolled at all)
            // ────────────────────────────────────────────────────────────────
            if (allCourses.isEmpty()) {
                item(key = "home_empty_enrolled") {
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.size(56.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.School,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            Text(
                                text = "Welcome to Courser",
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = GoogleSansRounded,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = "Select a course folder from Google Drive or local storage. Each folder is automatically organized into a full course.",
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = GoogleSansRounded,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )

                            FilledTonalButton(
                                onClick = {
                                    showAddCourseSourceSheet = true
                                },
                                shape = MaterialTheme.shapes.small
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Add Course Folders",
                                    fontFamily = GoogleSansRounded,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            } else {
                // ────────────────────────────────────────────────────────────────
                // 2. KEEP LISTENING SECTION (Unified CourseTile for active course in progress)
                // ────────────────────────────────────────────────────────────────
                if (activeCourse != null) {
                    item(key = "keep_listening_header") {
                        Text(
                            text = "Keep Listening",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = GoogleSansRounded,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }

                    item(key = "keep_listening_tile") {
                        val progress = courseProgressMap[activeCourse.id] ?: courseProgressMap[activeCourse.name]
                        val isPlayingThis = stablePlayerState.currentSong != null &&
                            stablePlayerState.currentSong != com.theveloper.pixelplay.data.model.Song.emptySong() &&
                            stablePlayerState.currentSong?.id in activeCourse.songIds

                        CourseTile(
                            playlist = activeCourse,
                            progress = progress,
                            isPlaying = isPlayingThis,
                            isAudioPlaying = stablePlayerState.isPlaying,
                            isPinned = pinnedCourseId == activeCourse.id,
                            onClick = {
                                navController.navigateSafely(Screen.PlaylistDetail.createRoute(activeCourse.id))
                            },
                            onPlayClick = {
                                dailyGoalViewModel.startLearning(playerViewModel)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                        )
                    }
                }

                // ────────────────────────────────────────────────────────────────
                // 3. TODAY'S LEARN SECTION (Only when daily goals are scheduled)
                // ────────────────────────────────────────────────────────────────
                if (plannedCourses.isNotEmpty()) {
                    item(key = "todays_learn_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Today's Learn",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontFamily = GoogleSansRounded,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "${plannedCourses.size} planned ${if (plannedCourses.size == 1) "course" else "courses"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = GoogleSansRounded,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            FilledTonalButton(
                                onClick = { navController.navigateSafely(Screen.TodaysLearn.route) },
                                shape = MaterialTheme.shapes.small,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Manage",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = "Manage today's courses",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    items(plannedCourses, key = { "today_${it.playlist.id}" }) { planned ->
                        val progress = courseProgressMap[planned.playlist.id] ?: courseProgressMap[planned.playlist.name]
                        val isPlayingThis = stablePlayerState.currentSong != null &&
                            stablePlayerState.currentSong != com.theveloper.pixelplay.data.model.Song.emptySong() &&
                            stablePlayerState.currentSong?.id in planned.playlist.songIds

                        CourseTile(
                            playlist = planned.playlist,
                            progress = progress,
                            isPlaying = isPlayingThis,
                            isAudioPlaying = stablePlayerState.isPlaying,
                            isPinned = pinnedCourseId == planned.playlist.id,
                            onClick = {
                                navController.navigateSafely(Screen.PlaylistDetail.createRoute(planned.playlist.id))
                            },
                            onPlayClick = {
                                dailyGoalViewModel.playCourse(planned.playlist.id, playerViewModel)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                        )
                    }
                }
            }
        }
    }

    if (showChangelogBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showChangelogBottomSheet = false }
        ) {
            ChangelogBottomSheet()
        }
    }

    if (showBetaInfoBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBetaInfoBottomSheet = false }
        ) {
            BetaInfoBottomSheet()
        }
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
                navController.navigateSafely(Screen.CourseFolderSelection.createRoute("gdrive"))
            }
        )
    }

    if (showAddCourseSourceSheet) {
        AddCourseSourceBottomSheet(
            onDismissRequest = { showAddCourseSourceSheet = false },
            onSelectLocal = {
                showAddCourseSourceSheet = false
                navController.navigateSafely(Screen.CourseFolderSelection.createRoute("local"))
            },
            onSelectGDrive = {
                showAddCourseSourceSheet = false
                navController.navigateSafely(Screen.CourseFolderSelection.createRoute("gdrive"))
            }
        )
    }
}

