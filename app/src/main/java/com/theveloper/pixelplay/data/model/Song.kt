package com.theveloper.pixelplay.data.model

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import com.theveloper.pixelplay.data.database.CourseProgressEntity
import kotlinx.parcelize.Parcelize

@Immutable
@Parcelize
data class Song(
    val id: String,
    val title: String,
    /**
     * Legacy artist display string.
     * - With multi-artist parsing enabled by default, this typically contains only the primary artist for backward compatibility.
     * For accurate display of all artists, use the [artists] list and [displayArtist] property.
     */
    val artist: String,
    val artistId: Long, // Primary artist ID for backward compatibility
    val artists: List<ArtistRef> = emptyList(), // All artists for multi-artist support
    val album: String,
    val albumId: Long,
    val albumArtist: String? = null, // Album artist from metadata
    val path: String, // Added for direct file system access
    val contentUriString: String,
    val albumArtUriString: String?,
    val duration: Long,
    val genre: String? = null,
    val lyrics: String? = null,
    val isFavorite: Boolean = false,
    val trackNumber: Int = 0,
    val discNumber: Int? = null,
    val year: Int = 0,
    val dateAdded: Long = 0,
    val dateModified: Long = 0,
    val mimeType: String?,
    val bitrate: Int?,
    val sampleRate: Int?,
    val telegramFileId: Int? = null, // ID of the file in Telegram
    val telegramChatId: Long? = null, // ID of the chat where the file is located
    val neteaseId: Long? = null, // Netease Cloud Music song ID
    val gdriveFileId: String? = null, // Google Drive file ID
    val qqMusicMid: String? = null, // QQ Music song MID
    val navidromeId: String? = null, // Navidrome song ID
    val jellyfinId: String? = null, // Jellyfin item ID
    val lastPlayedAt: Long? = null, // Epoch ms when this song was last played
    val resumePositionMs: Long = 0L, // Saved playback position in ms
    val isCompleted: Boolean = false, // Whether the lesson/song was listened to >= 90%
) : Parcelable {
    /**
     * Whether this track is treated as completed/already played based on latest playback state.
     * Over 90% played means it has already been played.
     * If the user re-listens to it and the latest record is under 90%, it reverts to in-progress.
     */
    val isCompletedTrack: Boolean
        get() = when {
            duration > 0L && resumePositionMs > 0L -> (resumePositionMs.toFloat() / duration) >= 0.90f
            isCompleted -> true
            else -> false
        }

    val isLocal: Boolean
        get() = telegramFileId == null &&
            neteaseId == null &&
            gdriveFileId == null &&
            qqMusicMid == null &&
            navidromeId == null &&
            jellyfinId == null &&
            !contentUriString.startsWith("http://") &&
            !contentUriString.startsWith("https://")

    /**
     * Playback progress ratio in range [0.0f, 1.0f].
     * Returns 1.0f if completed (>= 90%), or (resumePositionMs / duration) if duration > 0.
     */
    val playbackPercentage: Float
        get() = when {
            isCompletedTrack -> 1.0f
            duration > 0L && resumePositionMs > 0L -> (resumePositionMs.toFloat() / duration).coerceIn(0f, 1f)
            isCompleted -> 1.0f
            else -> 0f
        }

    /**
     * Integer percentage [0..100] for UI display.
     */
    val playbackProgressPercent: Int
        get() = when {
            isCompletedTrack -> 100
            duration > 0L && resumePositionMs > 0L -> {
                val ratio = (resumePositionMs.toFloat() / duration).coerceIn(0f, 1f)
                if (ratio >= 0.90f) 100 else (ratio * 100).toInt().coerceIn(0, 100)
            }
            isCompleted -> 100
            else -> 0
        }

    /**
     * Returns the display string for artists.
     * If multiple artists exist (populated during sync), joins them with ", ".
     * Falls back to the raw artist field (splitting is done at sync time, not display time).
     */
    val displayArtist: String
        get() {
            if (artists.isNotEmpty()) {
                return artists.sortedByDescending { it.isPrimary }.joinToString(", ") { it.name }
            }
            return artist
        }

    /**
     * Returns the primary artist from the artists list,
     * or creates one from the legacy artist field.
     */
    val primaryArtist: ArtistRef
        get() = artists.find { it.isPrimary }
            ?: artists.firstOrNull()
            ?: ArtistRef(id = artistId, name = artist, isPrimary = true)

    /**
     * Resolves the canonical course/playlist ID for this song.
     * In Courser, every audio track belongs to a course folder.
     * If no fallback/context playlistId is provided, parses the parent folder path.
     * If neither exists, falls back to [CourseProgressEntity.DEFAULT_PLAYLIST_ID].
     */
    fun resolveCoursePlaylistId(fallbackPlaylistId: String? = null): String {
        if (!fallbackPlaylistId.isNullOrBlank()) return fallbackPlaylistId
        val folderPath = path.substringBeforeLast('/', "").takeIf { it.isNotBlank() }
        return if (folderPath != null) {
            val encoded = runCatching { android.net.Uri.encode(folderPath) }.getOrNull()
                ?: java.net.URLEncoder.encode(folderPath, "UTF-8")
            "folder_playlist:$encoded"
        } else {
            CourseProgressEntity.DEFAULT_PLAYLIST_ID
        }
    }

    companion object {
        fun emptySong(): Song {
            return Song(
                id = "-1",
                title = "",
                artist = "",
                artistId = -1L,
                artists = emptyList(),
                album = "",
                albumId = -1L,
                albumArtist = null,
                path = "",
                contentUriString = "",
                albumArtUriString = null,
                duration = 0L,
                genre = null,
                lyrics = null,
                isFavorite = false,
                trackNumber = 0,
                discNumber = null,
                year = 0,
                dateAdded = 0,
                dateModified = 0,
                mimeType = "-",
                bitrate = 0,
                sampleRate = 0,
                telegramFileId = null,
                telegramChatId = null,
                neteaseId = null,
                gdriveFileId = null,
                qqMusicMid = null,
                navidromeId = null,
                jellyfinId = null,
                lastPlayedAt = null,
                resumePositionMs = 0L,
                isCompleted = false
            )
        }
    }
}
