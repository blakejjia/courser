package com.theveloper.pixelplay.utils

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MicExternalOn
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Piano
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.ui.graphics.vector.ImageVector
import com.theveloper.pixelplay.data.database.CourseProgressEntity
import com.theveloper.pixelplay.data.model.Playlist
import com.theveloper.pixelplay.data.model.Song

data class ResolvedCourseDetails(
    val courseId: String,
    val title: String,
    val coverImageUri: String? = null,
    val coverColorArgb: Int? = null,
    val coverIconName: String? = null
)

object CourseResolutionUtils {

    private val GENERIC_TITLES = setOf(
        "all songs",
        "all songs (shuffled)",
        "library",
        "none",
        "search results",
        "selected songs",
        "selected genres",
        "current context",
        "external",
        "unknown",
        "<unknown>"
    )

    /**
     * Resolves the canonical course details (title, cover URI, color, icon, ID)
     * for the currently active song or playlist context.
     *
     * Ensures that the player never displays generic placeholders like "All Songs" as the course cover.
     */
    fun resolveCourseDetails(
        song: Song?,
        playlistId: String? = null,
        allPlaylists: List<Playlist> = emptyList(),
        explicitQueueName: String? = null
    ): ResolvedCourseDetails {
        // 1. Try matching explicit playlistId against allPlaylists
        if (!playlistId.isNullOrBlank() && playlistId != CourseProgressEntity.DEFAULT_PLAYLIST_ID) {
            val playlist = allPlaylists.find { it.id == playlistId }
            if (playlist != null) {
                return ResolvedCourseDetails(
                    courseId = playlist.id,
                    title = playlist.name,
                    coverImageUri = playlist.coverImageUri,
                    coverColorArgb = playlist.coverColorArgb,
                    coverIconName = playlist.coverIconName
                )
            }
        }

        // 2. Try finding if song belongs to any known playlist/course
        if (song != null && song.id != "-1") {
            val playlist = allPlaylists.find { song.id in it.songIds }
            if (playlist != null) {
                return ResolvedCourseDetails(
                    courseId = playlist.id,
                    title = playlist.name,
                    coverImageUri = playlist.coverImageUri,
                    coverColorArgb = playlist.coverColorArgb,
                    coverIconName = playlist.coverIconName
                )
            }
        }

        // 3. Check for folder-based course
        val targetFolder = when {
            playlistId?.startsWith("folder_playlist:") == true -> {
                runCatching { Uri.decode(playlistId.removePrefix("folder_playlist:")) }.getOrNull()
            }
            song?.path?.isNotBlank() == true -> {
                song.path.substringBeforeLast('/', "").takeIf { it.isNotBlank() }
            }
            else -> null
        }

        if (!targetFolder.isNullOrBlank()) {
            val folderName = targetFolder.substringAfterLast('/')
            if (folderName.isNotBlank()) {
                val resolvedId = playlistId?.takeIf { it.isNotBlank() }
                    ?: song?.resolveCoursePlaylistId()
                    ?: CourseProgressEntity.DEFAULT_PLAYLIST_ID

                // Check if any playlist matches this folder name
                val nameMatch = allPlaylists.find { it.name.equals(folderName, ignoreCase = true) }
                if (nameMatch != null) {
                    return ResolvedCourseDetails(
                        courseId = nameMatch.id,
                        title = nameMatch.name,
                        coverImageUri = nameMatch.coverImageUri,
                        coverColorArgb = nameMatch.coverColorArgb,
                        coverIconName = nameMatch.coverIconName
                    )
                }

                return ResolvedCourseDetails(
                    courseId = resolvedId,
                    title = folderName
                )
            }
        }

        // 4. Check explicitQueueName if it's meaningful and not generic
        val cleanQueueName = explicitQueueName?.trim()
        if (!cleanQueueName.isNullOrBlank() && cleanQueueName.lowercase() !in GENERIC_TITLES) {
            return ResolvedCourseDetails(
                courseId = playlistId ?: song?.resolveCoursePlaylistId() ?: CourseProgressEntity.DEFAULT_PLAYLIST_ID,
                title = cleanQueueName
            )
        }

        // 5. Check song album if meaningful
        val album = song?.album?.trim()
        if (!album.isNullOrBlank() && album.lowercase() !in GENERIC_TITLES) {
            return ResolvedCourseDetails(
                courseId = playlistId ?: song.resolveCoursePlaylistId(),
                title = album
            )
        }

        // 6. Final fallback
        return ResolvedCourseDetails(
            courseId = playlistId ?: CourseProgressEntity.DEFAULT_PLAYLIST_ID,
            title = "Course"
        )
    }

    /**
     * Resolves an icon name from Playlist metadata to an ImageVector.
     */
    fun resolveCourseIcon(name: String?): ImageVector {
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
}
