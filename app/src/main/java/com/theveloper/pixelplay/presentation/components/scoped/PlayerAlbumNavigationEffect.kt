package com.theveloper.pixelplay.presentation.components.scoped

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigation.NavHostController
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.navigation.navigateSafelyReplacing
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import kotlinx.coroutines.flow.collectLatest

@Composable
internal fun PlayerAlbumNavigationEffect(
    navController: NavHostController,
    sheetCollapsedTargetY: Float,
    sheetMotionController: SheetMotionController,
    playerViewModel: PlayerViewModel
) {
    val latestSheetCollapsedTargetY by rememberUpdatedState(sheetCollapsedTargetY)
    LaunchedEffect(navController) {
        playerViewModel.courseNavigationRequests.collectLatest { courseId ->
            sheetMotionController.snapCollapsed(latestSheetCollapsedTargetY)
            playerViewModel.collapsePlayerSheet()

            navController.navigateSafelyReplacing(
                route = Screen.PlaylistDetail.createRoute(courseId),
                patternToPop = Screen.PlaylistDetail.route
            )
        }
    }
    LaunchedEffect(navController) {
        playerViewModel.albumNavigationRequests.collectLatest { albumId ->
            sheetMotionController.snapCollapsed(latestSheetCollapsedTargetY)
            playerViewModel.collapsePlayerSheet()

            val currentSong = playerViewModel.stablePlayerState.value.currentSong
            val courseId = playerViewModel.playerUiState.value.currentPlaylistId
                ?: playerViewModel.resolveCourseIdForSong(currentSong)
                ?: playerViewModel.resolveCourseIdForAlbumId(albumId)
            if (!courseId.isNullOrBlank()) {
                navController.navigateSafelyReplacing(
                    route = Screen.PlaylistDetail.createRoute(courseId),
                    patternToPop = Screen.PlaylistDetail.route
                )
            } else {
                playerViewModel.sendToast("Course not found")
            }
        }
    }
}
