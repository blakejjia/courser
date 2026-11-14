package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theveloper.pixelplay.presentation.screens.search.components.GenreTypography
import com.theveloper.pixelplay.presentation.screens.search.components.getGenreImageResource
import com.theveloper.pixelplay.ui.theme.GenreThemeColor
import com.theveloper.pixelplay.ui.theme.GenreThemeUtils
import com.theveloper.pixelplay.ui.theme.LocalPixelPlayDarkTheme
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Extracts up to 4 significant characters from a course or playlist title.
 */
fun extractCourseFourChars(name: String): String {
    if (name.isBlank()) return "课程"

    var cleaned = name
        .replace(Regex("""\.(mp3|m4a|flac|wav|ogg|aac|opus)$""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""^[0-9]+[.\-_\s]*"""), "")
        .replace(Regex("""^(第[0-9一二三四五六七八九十]+[章节集讲课课时]*|Lesson\s*[0-9]+|Chapter\s*[0-9]+|Part\s*[0-9]+)[.\-_\s]*""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""[【\[(（].*?[）)\]】]"""), "")
        .trim()

    if (cleaned.isBlank()) cleaned = name.trim()

    val hasSpaces = cleaned.contains(" ")
    val isCjk = cleaned.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }

    return if (isCjk || !hasSpaces) {
        val chars = cleaned.filter { it.isLetterOrDigit() }
        when {
            chars.length >= 4 -> chars.substring(0, 4)
            chars.isNotEmpty() -> chars
            else -> cleaned.take(4)
        }
    } else {
        val words = cleaned.split(Regex("""\s+"""))
            .filter { it.isNotBlank() }
            .filterNot { it.lowercase() in setOf("a", "an", "the", "and", "or", "of", "in", "for", "to", "with", "on", "at") }

        val candidate = if (words.size >= 2) {
            words.mapNotNull { it.firstOrNull { c -> c.isLetterOrDigit() } }.joinToString("")
        } else {
            cleaned.filter { it.isLetterOrDigit() }
        }

        candidate.take(4).uppercase()
    }
}

/**
 * Dynamic Material 3 Expressive Poster Cover for Courser.
 *
 * Implements the PixelPlayer automatic card generation system:
 * - 20-tone adaptive dark/light Material 3 palette from [GenreThemeUtils]
 * - Stylized variable typography engine using `gflex_variable` / `genre_variable`
 * - Bottom-right oversized semi-transparent watermark illustration bleeding off edges
 * - Proportional optical scaling for small thumbnails (46dp) up to hero banners (240dp+)
 */
@Composable
fun CourseGridCover(
    title: String,
    modifier: Modifier = Modifier,
    customText: String? = null,
    colorArgb: Int? = null,
    shape: Shape = RoundedCornerShape(10.dp)
) {
    CoursePosterCover(
        title = title,
        modifier = modifier,
        customText = customText,
        colorArgb = colorArgb,
        shape = shape
    )
}

/**
 * Procedural artistic cover generator adapted from PixelPlayer's genre card visual engine.
 */
@Composable
fun CoursePosterCover(
    title: String,
    modifier: Modifier = Modifier,
    customText: String? = null,
    colorArgb: Int? = null,
    shape: Shape = RoundedCornerShape(10.dp)
) {
    val isDark = LocalPixelPlayDarkTheme.current
    val effectiveTitle = title.ifBlank { "Courser" }

    // 1. Theme Color (Adaptive M3 20-tone palette or user custom color)
    val themeColor = remember(effectiveTitle, isDark, colorArgb) {
        if (colorArgb != null) {
            val bg = Color(colorArgb)
            val luminance = (0.299 * bg.red + 0.587 * bg.green + 0.114 * bg.blue)
            val fg = if (luminance > 0.5) Color(0xFF1B1B20) else Color(0xFFF2F1F6)
            GenreThemeColor(container = bg, onContainer = fg)
        } else {
            GenreThemeUtils.getGenreThemeColor(genreId = effectiveTitle, isDark = isDark)
        }
    }
    val backgroundColor = themeColor.container
    val onBackgroundColor = themeColor.onContainer

    // 2. Watermark Icon (Dynamic keyword mapping from PixelPlayer's GenreIconProvider)
    val iconRes = remember(effectiveTitle) {
        getGenreImageResource(effectiveTitle)
    }

    BoxWithConstraints(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
    ) {
        val width = maxWidth
        val density = LocalDensity.current

        // Reference size for typography scaling (standard genre card is ~160dp width)
        val referenceSize = 160.dp
        val scale = (width / referenceSize).coerceIn(0.24f, 2.2f)

        // 3. Watermark illustration in bottom-right corner (PixelPlayer visual signature)
        val iconSize = width * 0.58f
        val iconOffset = width * 0.12f

        Box(
            modifier = Modifier
                .size(iconSize)
                .align(Alignment.BottomEnd)
                .offset(x = iconOffset, y = iconOffset)
        ) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.42f),
                colorFilter = ColorFilter.tint(onBackgroundColor),
                contentScale = ContentScale.Crop
            )
        }

        // 4. Stylized typography in top-left corner
        val textMeasurer = rememberTextMeasurer()
        val textToPresent = customText?.takeIf { it.isNotBlank() } ?: effectiveTitle

        val startPadding = (14.dp * scale).coerceAtLeast(4.dp)
        val topPadding = (12.dp * scale).coerceAtLeast(3.dp)
        val endPadding = (10.dp * scale).coerceAtLeast(4.dp)

        val titlePresentation = remember(
            effectiveTitle,
            textToPresent,
            scale,
            density.density,
            density.fontScale
        ) {
            val standardWidthPx = with(density) { referenceSize.roundToPx() }
            val standardPaddingPx = with(density) { 14.dp.roundToPx() }
            GenreTypography.resolveTitlePresentation(
                genreId = effectiveTitle,
                genreName = textToPresent,
                isGridView = true,
                cardWidthPx = standardWidthPx,
                horizontalPaddingPx = standardPaddingPx,
                textMeasurer = textMeasurer
            )
        }

        // Apply optical scale to font size and line height
        val scaledStyle = remember(titlePresentation.style, scale) {
            titlePresentation.style.copy(
                fontSize = (titlePresentation.style.fontSize.value * scale).sp,
                lineHeight = (titlePresentation.style.lineHeight.value * scale).sp,
                letterSpacing = (titlePresentation.style.letterSpacing.value * scale).sp
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(start = startPadding, top = topPadding, end = endPadding),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Text(
                text = titlePresentation.firstLine,
                style = scaledStyle,
                color = onBackgroundColor,
                softWrap = false,
                minLines = 1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            titlePresentation.secondLine?.let { secondLine ->
                Text(
                    text = secondLine,
                    style = scaledStyle,
                    color = onBackgroundColor,
                    softWrap = false,
                    minLines = 1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(titlePresentation.secondLineWidthFraction)
                )
            }
        }
    }
}
