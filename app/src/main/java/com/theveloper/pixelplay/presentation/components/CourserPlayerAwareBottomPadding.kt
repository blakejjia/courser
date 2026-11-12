package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel

/**
 * Calculates the exact bottom padding required for scrollable lists so that their
 * last items can be scrolled completely above both the bottom navigation bar (if present)
 * and the floating mini player (when active), with smooth animation when playback states change.
 *
 * @param playerViewModel PlayerViewModel to observe playback and nav bar state.
 * @param hasBottomNavBar True if the screen is a main root tab (Home, Library, Settings) where the
 *                        custom bottom navigation bar is rendered.
 * @param includeSystemNavBarInset Set to true if the list does NOT already consume system navigation
 *                                 bar insets via outer modifier padding.
 * @param extraPadding Additional breathing room in dp (default 24.dp).
 */
@Composable
fun rememberPlayerAwareBottomPadding(
    playerViewModel: PlayerViewModel,
    hasBottomNavBar: Boolean = false,
    includeSystemNavBarInset: Boolean = true,
    extraPadding: Dp = 24.dp
): State<Dp> {
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val isMiniPlayerVisible = stablePlayerState.currentSong != null && stablePlayerState.currentSong != Song.emptySong()
    val navBarCompactMode by playerViewModel.navBarCompactMode.collectAsStateWithLifecycle()
    val systemNavBarInset = if (includeSystemNavBarInset) {
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    } else {
        0.dp
    }

    val targetPadding = if (hasBottomNavBar) {
        val navBarOccupiedHeight = resolveNavBarOccupiedHeight(systemNavBarInset, navBarCompactMode)
        navBarOccupiedHeight + (if (isMiniPlayerVisible) MiniPlayerHeight else 0.dp) + extraPadding
    } else {
        systemNavBarInset + (if (isMiniPlayerVisible) MiniPlayerHeight else 0.dp) + extraPadding
    }

    return animateDpAsState(
        targetValue = targetPadding,
        label = "playerAwareBottomPadding"
    )
}

/**
 * Unified LazyColumn that automatically applies player-aware bottom padding.
 * Ensures the bottom-most list items are never obscured by the bottom navigation bar
 * or the active mini-player.
 */
@Composable
fun CourserLazyColumn(
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    hasBottomNavBar: Boolean = false,
    includeSystemNavBarInset: Boolean = true,
    extraBottomPadding: Dp = 24.dp,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical = if (!reverseLayout) Arrangement.Top else Arrangement.Bottom,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    userScrollEnabled: Boolean = true,
    content: LazyListScope.() -> Unit
) {
    val playerBottomPadding by rememberPlayerAwareBottomPadding(
        playerViewModel = playerViewModel,
        hasBottomNavBar = hasBottomNavBar,
        includeSystemNavBarInset = includeSystemNavBarInset,
        extraPadding = extraBottomPadding
    )

    val layoutDirection = LocalLayoutDirection.current
    val effectiveContentPadding = PaddingValues(
        start = contentPadding.calculateStartPadding(layoutDirection),
        top = contentPadding.calculateTopPadding(),
        end = contentPadding.calculateEndPadding(layoutDirection),
        bottom = contentPadding.calculateBottomPadding() + playerBottomPadding
    )

    LazyColumn(
        modifier = modifier,
        state = state,
        contentPadding = effectiveContentPadding,
        reverseLayout = reverseLayout,
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        flingBehavior = flingBehavior,
        userScrollEnabled = userScrollEnabled,
        content = content
    )
}
