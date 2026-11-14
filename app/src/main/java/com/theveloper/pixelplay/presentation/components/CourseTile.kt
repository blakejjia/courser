package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.database.CourseProgressEntity
import com.theveloper.pixelplay.data.model.Playlist
import com.theveloper.pixelplay.presentation.components.subcomps.PlayingEqIcon
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded

/**
 * Unified course card component for Courser.
 * Reused identically across Home, Library, Today's Learn, and Search.
 *
 * Features:
 * - Course title & section progress (e.g. "#13 Continue • 45 lessons", no raw song titles)
 * - Zero-margin bottom progress bar showing course completion ratio
 * - Right action area indicating active playing status ("Playing #23" + EQ icon), pin state, or play action
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CourseTile(
    playlist: Playlist,
    progress: CourseProgressEntity? = null,
    isPlaying: Boolean = false,
    playingSectionNumber: Int? = null,
    isAudioPlaying: Boolean = true,
    isPinned: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onPlayClick: (() -> Unit)? = null,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    selectionIndex: Int = -1,
    onLongClick: (() -> Unit)? = null,
    onSelectionToggle: (() -> Unit)? = null,
    isReorderMode: Boolean = false,
    dragHandle: (@Composable () -> Unit)? = null
) {
    val selectionScale by animateFloatAsState(
        targetValue = if (isSelected) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "courseSelectionScale"
    )

    val selectionBorderWidth by animateDpAsState(
        targetValue = if (isSelected) 2.5.dp else 0.dp,
        animationSpec = tween(durationMillis = 250),
        label = "courseSelectionBorderWidth"
    )

    val containerColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.secondaryContainer
            isPlaying -> MaterialTheme.colorScheme.surfaceContainerHigh
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = tween(durationMillis = 300),
        label = "courseContainerColor"
    )

    val selectionBorderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0f),
        animationSpec = tween(durationMillis = 250),
        label = "courseBorderColor"
    )

    val completionRatio = progress?.completionRatio ?: playlist.completionPercentage
    val subtitleText = "${playlist.songIds.size} total"

    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(selectionScale)
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = selectionBorderWidth,
                        color = selectionBorderColor,
                        shape = RoundedCornerShape(14.dp)
                    )
                } else {
                    Modifier
                }
            )
            .combinedClickable(
                onClick = {
                    if (isSelectionMode && onSelectionToggle != null) {
                        onSelectionToggle()
                    } else {
                        onClick()
                    }
                },
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Course Cover / Icon: Use custom cover if set, otherwise fallback to default music picture
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.size(46.dp)
                ) {
                    if (!playlist.coverImageUri.isNullOrBlank()) {
                        SmartImage(
                            model = playlist.coverImageUri,
                            contentDescription = playlist.name,
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(10.dp),
                            contentScale = ContentScale.Crop,
                            placeholderResId = R.drawable.ic_music_placeholder,
                            errorResId = R.drawable.ic_music_placeholder
                        )
                    } else if (playlist.coverIconName?.startsWith("text:") == true) {
                        CourseGridCover(
                            title = playlist.name,
                            customText = playlist.coverIconName.removePrefix("text:"),
                            colorArgb = playlist.coverColorArgb,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (playlist.coverColorArgb != null && !playlist.coverIconName.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(playlist.coverColorArgb)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = getCourseIconByName(playlist.coverIconName) ?: Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    } else {
                        CourseGridCover(
                            title = playlist.name,
                            colorArgb = playlist.coverColorArgb,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Title and Lesson progress subtitle
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = GoogleSansRounded),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Action area: Pin icon, Drag handle (in reorder mode), Play button, or Selection check
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isReorderMode && dragHandle != null) {
                        dragHandle()
                    } else {
                        if (isPinned) {
                            Icon(
                                imageVector = Icons.Rounded.PushPin,
                                contentDescription = "Pinned course",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        if (isSelectionMode) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .background(
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selectionIndex >= 0) {
                                    Text(
                                        text = (selectionIndex + 1).toString(),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        if (onPlayClick != null && !isSelectionMode) {
                            if (isPlaying) {
                                IconButton(
                                    onClick = onPlayClick,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    PlayingEqIcon(
                                        modifier = Modifier.size(22.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        isPlaying = isAudioPlaying
                                    )
                                }
                            } else {
                                IconButton(
                                    onClick = onPlayClick,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PlayArrow,
                                        contentDescription = "Play course",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Zero-margin 2dp bottom progress indicator border
            if (completionRatio > 0f) {
                LinearProgressIndicator(
                    progress = { completionRatio.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f)
                )
            }
        }
    }
}

private fun getCourseIconByName(name: String?): ImageVector? {
    return when (name) {
        "MusicNote" -> Icons.Rounded.MusicNote
        "Headphones" -> Icons.Rounded.Headphones
        "Album" -> Icons.Rounded.Album
        "Mic" -> Icons.Rounded.MicExternalOn
        "Speaker" -> Icons.Rounded.Speaker
        "Favorite" -> Icons.Rounded.Favorite
        "Piano" -> Icons.Rounded.Piano
        "Queue" -> Icons.AutoMirrored.Rounded.QueueMusic
        else -> Icons.Rounded.MusicNote
    }
}
