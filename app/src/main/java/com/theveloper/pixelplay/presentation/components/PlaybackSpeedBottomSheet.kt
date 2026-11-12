package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.components.player.formatPlaybackSpeed
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import kotlin.math.abs
import kotlin.math.roundToInt

private val QUICK_SPEED_OPTIONS = listOf(1.0f, 1.5f, 2.0f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackSpeedBottomSheet(
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = LocalHapticFeedback.current
    var sliderSpeed by remember(currentSpeed) { mutableFloatStateOf(currentSpeed) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = AbsoluteSmoothCornerShape(
            cornerRadiusTL = 28.dp,
            smoothnessAsPercentTL = 60,
            cornerRadiusTR = 28.dp,
            smoothnessAsPercentTR = 60,
            cornerRadiusBL = 0.dp,
            smoothnessAsPercentBL = 0,
            cornerRadiusBR = 0.dp,
            smoothnessAsPercentBR = 0
        ),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header (without reset button)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Speed,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column {
                    Text(
                        text = stringResource(R.string.player_playback_speed),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        fontFamily = GoogleSansRounded,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.player_playback_speed_global_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Prominent Current Speed Readout & Fine-Tuning Slider Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = AbsoluteSmoothCornerShape(20.dp, 60),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatPlaybackSpeed(sliderSpeed),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontSize = 36.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = GoogleSansRounded
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )

                        if (abs(sliderSpeed - 1.0f) < 0.02f) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = stringResource(R.string.player_playback_speed_normal),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    // Slide bar with - and + step buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilledTonalIconButton(
                            onClick = {
                                val newSpeed = ((sliderSpeed - 0.1f) * 100).roundToInt() / 100f
                                val clamped = newSpeed.coerceIn(0.5f, 3.0f)
                                sliderSpeed = clamped
                                onSpeedSelected(clamped)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                            enabled = sliderSpeed > 0.5f,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Remove,
                                contentDescription = "Decrease speed",
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Slider(
                            value = sliderSpeed,
                            onValueChange = { newValue ->
                                val snapped = ((newValue * 20).roundToInt() / 20f).coerceIn(0.5f, 3.0f)
                                if (abs(snapped - sliderSpeed) >= 0.04f) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                sliderSpeed = snapped
                                onSpeedSelected(snapped)
                            },
                            valueRange = 0.5f..3.0f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                        )

                        FilledTonalIconButton(
                            onClick = {
                                val newSpeed = ((sliderSpeed + 0.1f) * 100).roundToInt() / 100f
                                val clamped = newSpeed.coerceIn(0.5f, 3.0f)
                                sliderSpeed = clamped
                                onSpeedSelected(clamped)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                            enabled = sliderSpeed < 3.0f,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Increase speed",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Quick Speed Buttons (only 1.0x, 1.5x, 2.0x)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Quick Speeds",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    QUICK_SPEED_OPTIONS.forEach { preset ->
                        val isSelected = abs(sliderSpeed - preset) < 0.04f
                        val containerColor by animateColorAsState(
                            targetValue = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            label = "preset_container"
                        )
                        val contentColor by animateColorAsState(
                            targetValue = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            label = "preset_content"
                        )

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .clip(AbsoluteSmoothCornerShape(16.dp, 60))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    sliderSpeed = preset
                                    onSpeedSelected(preset)
                                },
                            shape = AbsoluteSmoothCornerShape(16.dp, 60),
                            color = containerColor
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = contentColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(
                                    text = formatPlaybackSpeed(preset),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                        fontFamily = GoogleSansRounded
                                    ),
                                    color = contentColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
