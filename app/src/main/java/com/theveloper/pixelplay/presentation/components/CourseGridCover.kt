package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import kotlin.math.abs

/**
 * Material 3 tonal palette pair for deterministic course covers.
 */
data class M3CoursePalette(
    val container: Color,
    val onContainer: Color
)

object CourseCoverPalettes {
    val PALETTES = listOf(
        // 1. Sage & Deep Forest (Earthy Green)
        M3CoursePalette(Color(0xFFDCE7D4), Color(0xFF141F10)),
        // 2. Lavender & Deep Violet (Academic Purple)
        M3CoursePalette(Color(0xFFEADDFF), Color(0xFF21005D)),
        // 3. Terracotta & Warm Almond (Warm Humanities)
        M3CoursePalette(Color(0xFFFFDBCF), Color(0xFF380D00)),
        // 4. Ocean Teal & Deep Pine (Mathematics / Logic)
        M3CoursePalette(Color(0xFFCCE8E7), Color(0xFF051F1F)),
        // 5. Honey Amber & Deep Bronze (Economics / Business)
        M3CoursePalette(Color(0xFFFFDF9E), Color(0xFF261A00)),
        // 6. Soft Plum & Dark Violet (Literature / Arts)
        M3CoursePalette(Color(0xFFF3DAFF), Color(0xFF2B0052)),
        // 7. Sky Blue & Midnight Navy (Technology / Coding)
        M3CoursePalette(Color(0xFFD1E4FF), Color(0xFF001D36)),
        // 8. Mint Emerald & Deep Pine (Natural Sciences)
        M3CoursePalette(Color(0xFFBCF0D3), Color(0xFF002114)),
        // 9. Coral Rose & Burgundy (Design / Creative)
        M3CoursePalette(Color(0xFFFFD9E2), Color(0xFF3E001D)),
        // 10. Warm Sand & Espresso (History / Philosophy)
        M3CoursePalette(Color(0xFFEDE0D4), Color(0xFF2A1805)),
        // 11. Steel Slate & Indigo (Engineering)
        M3CoursePalette(Color(0xFFDDE2ED), Color(0xFF131C2B)),
        // 12. Goldenrod & Espresso (Languages)
        M3CoursePalette(Color(0xFFFEE1A0), Color(0xFF241A00))
    )

    fun resolve(title: String): M3CoursePalette {
        val index = abs(title.hashCode()) % PALETTES.size
        return PALETTES[index]
    }
}

/**
 * Extracts up to 4 significant characters from a course or playlist title.
 */
fun extractCourseFourChars(name: String): String {
    if (name.isBlank()) return "课程"

    // Clean title: remove file extensions, numbers at start, brackets, etc.
    var cleaned = name
        .replace(Regex("""\.(mp3|m4a|flac|wav|ogg|aac|opus)$""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""^[0-9]+[.\-_\s]*"""), "")
        .replace(Regex("""^(第[0-9一二三四五六七八九十]+[章节集讲课课时]*|Lesson\s*[0-9]+|Chapter\s*[0-9]+|Part\s*[0-9]+)[.\-_\s]*""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""[【\[(（].*?[）)\]】]"""), "")
        .trim()

    if (cleaned.isBlank()) cleaned = name.trim()

    // Detect if predominantly CJK or single word without spaces
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
 * Material 3 expressive 2x2 character grid cover for Courser.
 */
@Composable
fun CourseGridCover(
    title: String,
    modifier: Modifier = Modifier,
    customText: String? = null,
    colorArgb: Int? = null,
    shape: Shape = RoundedCornerShape(10.dp)
) {
    val textToDisplay = remember(title, customText) {
        if (!customText.isNullOrBlank()) {
            customText.take(4)
        } else {
            extractCourseFourChars(title)
        }
    }

    val palette = remember(title, colorArgb) {
        if (colorArgb != null) {
            val bg = Color(colorArgb)
            val luminance = (0.299 * bg.red + 0.587 * bg.green + 0.114 * bg.blue)
            val fg = if (luminance > 0.5) Color(0xFF1A1A1A) else Color(0xFFFFFFFF)
            M3CoursePalette(container = bg, onContainer = fg)
        } else {
            CourseCoverPalettes.resolve(title)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .clip(shape)
            .background(palette.container),
        contentAlignment = Alignment.Center
    ) {
        val size = maxWidth
        val fontSize = (size.value * 0.28f).sp

        when (textToDisplay.length) {
            4 -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GridChar(textToDisplay[0], fontSize, palette.onContainer)
                        GridChar(textToDisplay[1], fontSize, palette.onContainer)
                    }
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GridChar(textToDisplay[2], fontSize, palette.onContainer)
                        GridChar(textToDisplay[3], fontSize, palette.onContainer)
                    }
                }
            }
            3 -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GridChar(textToDisplay[0], fontSize, palette.onContainer)
                        GridChar(textToDisplay[1], fontSize, palette.onContainer)
                    }
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GridChar(textToDisplay[2], fontSize, palette.onContainer)
                    }
                }
            }
            2 -> {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GridChar(textToDisplay[0], (size.value * 0.36f).sp, palette.onContainer)
                    GridChar(textToDisplay[1], (size.value * 0.36f).sp, palette.onContainer)
                }
            }
            else -> {
                Text(
                    text = textToDisplay,
                    color = palette.onContainer,
                    fontSize = (size.value * 0.44f).sp,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun GridChar(
    char: Char,
    fontSize: TextUnit,
    color: Color
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.padding(horizontal = 1.5.dp, vertical = 0.5.dp)
    ) {
        Text(
            text = char.toString(),
            color = color,
            fontSize = fontSize,
            fontFamily = GoogleSansRounded,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}
