package com.theveloper.pixelplay.data.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.theveloper.pixelplay.data.database.AlbumEntity
import com.theveloper.pixelplay.data.database.ArtistEntity
import com.theveloper.pixelplay.data.database.GDriveDao
import com.theveloper.pixelplay.data.database.LocalPlaylistDao
import com.theveloper.pixelplay.data.database.MusicDao
import com.theveloper.pixelplay.data.database.PlaylistEntity
import com.theveloper.pixelplay.data.database.SongArtistCrossRef
import com.theveloper.pixelplay.data.database.SongEntity
import com.theveloper.pixelplay.data.database.SourceType
import com.theveloper.pixelplay.data.gdrive.GDriveRepository
import com.theveloper.pixelplay.data.media.AudioMetadataReader
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.di.AppScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue

data class LocalFolderEntry(
    val file: File,
    val name: String,
    val audioCount: Int,
    val isChecked: Boolean
)

data class DriveFolderEntry(
    val id: String,
    val name: String,
    val isChecked: Boolean
)

@Singleton
class CourseFolderManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @AppScope private val appScope: CoroutineScope,
    private val localPlaylistDao: LocalPlaylistDao,
    private val musicDao: MusicDao,
    private val gdriveRepository: GDriveRepository,
    private val gdriveDao: GDriveDao,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    companion object {
        const val LOCAL_COURSE_PREFIX = "local_course_"
        val SUPPORTED_AUDIO_EXTENSIONS = setOf(
            "mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "wma", "mka", "mp4"
        )

        fun toLocalCoursePlaylistId(canonicalPath: String): String {
            return "$LOCAL_COURSE_PREFIX${canonicalPath.hashCode().absoluteValue}"
        }

        fun naturalCompare(s1: String, s2: String): Int {
            val regex = "(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)".toRegex()
            val parts1 = s1.split(regex)
            val parts2 = s2.split(regex)
            val minLength = minOf(parts1.size, parts2.size)
            for (i in 0 until minLength) {
                val p1 = parts1[i]
                val p2 = parts2[i]
                val n1 = p1.toLongOrNull()
                val n2 = p2.toLongOrNull()
                val cmp = if (n1 != null && n2 != null) {
                    n1.compareTo(n2)
                } else {
                    p1.compareTo(p2, ignoreCase = true)
                }
                if (cmp != 0) return cmp
            }
            return parts1.size.compareTo(parts2.size)
        }
    }

    private fun normalizePath(path: String): String =
        runCatching { File(path).canonicalPath }.getOrElse { File(path).absolutePath }

    // --- Querying Added Courses ---

    fun getAddedLocalCoursePaths(): Flow<Set<String>> {
        return userPreferencesRepository.allowedDirectoriesFlow.map { set ->
            set.map(::normalizePath).toSet()
        }
    }

    fun getAddedDriveFolderIds(): Flow<Set<String>> {
        return gdriveDao.getAllFolders().map { list ->
            list.map { it.id }.toSet()
        }
    }

    // --- Browsing ---

    suspend fun listLocalFolderEntries(
        directory: File,
        checkedPaths: Set<String>
    ): List<LocalFolderEntry> = withContext(Dispatchers.IO) {
        var rawDirs = directory.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }

        // Fallback: If raw listFiles() is empty (e.g. Scoped Storage restrictions on root),
        // synthesize immediate children from musicDao's indexed songs
        if (rawDirs.isNullOrEmpty()) {
            val dirPath = normalizePath(directory.path)
            val dbParentDirs = musicDao.getDistinctParentDirectories()
            val childDirNames = mutableSetOf<String>()
            for (p in dbParentDirs) {
                val norm = normalizePath(p)
                if (norm.startsWith(dirPath) && norm.length > dirPath.length) {
                    val relative = norm.removePrefix(dirPath).trimStart(File.separatorChar, '/')
                    val firstSegment = relative.substringBefore(File.separatorChar).substringBefore('/')
                    if (firstSegment.isNotBlank() && !firstSegment.startsWith(".")) {
                        childDirNames.add(firstSegment)
                    }
                }
            }
            if (childDirNames.isNotEmpty()) {
                rawDirs = childDirNames.map { File(directory, it) }
            }
        }

        if (rawDirs.isNullOrEmpty()) return@withContext emptyList()

        val normalizedChecked = checkedPaths.map(::normalizePath).toSet()

        coroutineScope {
            rawDirs
                .sortedWith { a, b -> naturalCompare(a.name, b.name) }
                .map { dir ->
                    async {
                        val canonical = normalizePath(dir.path)
                        val audioCount = countAudioFiles(dir)
                        LocalFolderEntry(
                            file = dir,
                            name = dir.name,
                            audioCount = audioCount,
                            isChecked = canonical in normalizedChecked
                        )
                    }
                }
                .awaitAll()
        }
    }

    private fun countAudioFiles(directory: File, maxDepth: Int = 2, maxFiles: Int = 100): Int {
        val skipped = setOf("android", "dcim", "pictures", "movies", "alarms", "notifications", "ringtones", "cache", ".thumbnails")
        if (directory.name.startsWith(".") || directory.name.lowercase() in skipped) {
            return 0
        }
        var count = 0
        val queue = ArrayDeque<Pair<File, Int>>()
        queue.add(directory to 0)
        while (queue.isNotEmpty() && count < maxFiles) {
            val (curr, depth) = queue.removeFirst()
            val list = curr.listFiles() ?: continue
            for (f in list) {
                if (f.name.startsWith(".")) continue
                if (f.isDirectory) {
                    if (depth < maxDepth && f.name.lowercase() !in skipped) {
                        queue.add(f to depth + 1)
                    }
                } else if (f.isFile && f.extension.lowercase() in SUPPORTED_AUDIO_EXTENSIONS) {
                    count++
                }
            }
        }
        return count
    }

    suspend fun listDriveFolderEntries(
        parentId: String = "root",
        checkedIds: Set<String>
    ): Result<List<DriveFolderEntry>> = withContext(Dispatchers.IO) {
        val result = gdriveRepository.listDriveFolders(parentId)
        result.map { driveFolders ->
            driveFolders.map { folder ->
                DriveFolderEntry(
                    id = folder.id,
                    name = folder.name,
                    isChecked = folder.id in checkedIds
                )
            }
        }
    }

    // --- Course Ingestion (Local) ---

    suspend fun addLocalCourse(folder: File): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val canonicalPath = normalizePath(folder.path)
            val audioFiles = mutableListOf<File>()
            val queue = ArrayDeque<File>()
            queue.add(folder)

            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                val list = current.listFiles() ?: continue
                for (f in list) {
                    if (f.isDirectory && !f.name.startsWith(".")) {
                        queue.add(f)
                    } else if (f.isFile && f.extension.lowercase() in SUPPORTED_AUDIO_EXTENSIONS) {
                        audioFiles.add(f)
                    }
                }
            }

            if (audioFiles.isEmpty()) {
                Timber.w("addLocalCourse: No audio files found in ${folder.path}")
            }

            // Natural alphanumeric sort by relative path
            val sortedFiles = audioFiles.sortedWith { a, b ->
                val relA = runCatching { a.relativeTo(folder).path }.getOrElse { a.name }
                val relB = runCatching { b.relativeTo(folder).path }.getOrElse { b.name }
                naturalCompare(relA, relB)
            }

            // Map each file to SongEntity
            val songs = ArrayList<SongEntity>(sortedFiles.size)
            val artists = LinkedHashMap<Long, ArtistEntity>()
            val albums = LinkedHashMap<Long, AlbumEntity>()
            val crossRefs = mutableListOf<SongArtistCrossRef>()

            val courseAlbumId = toLocalCourseAlbumId(canonicalPath)
            val courseAlbumName = folder.name
            val defaultArtistId = toLocalCourseArtistId(courseAlbumName)
            val defaultArtistName = courseAlbumName

            artists.putIfAbsent(
                defaultArtistId,
                ArtistEntity(
                    id = defaultArtistId,
                    name = defaultArtistName,
                    trackCount = 0,
                    imageUrl = null
                )
            )

            albums.putIfAbsent(
                courseAlbumId,
                AlbumEntity(
                    id = courseAlbumId,
                    title = courseAlbumName,
                    artistName = defaultArtistName,
                    artistId = defaultArtistId,
                    songCount = sortedFiles.size,
                    dateAdded = System.currentTimeMillis() / 1000L,
                    year = 0,
                    albumArtUriString = null
                )
            )

            for ((index, file) in sortedFiles.withIndex()) {
                val song = createOrResolveSongEntity(
                    file = file,
                    courseAlbumId = courseAlbumId,
                    courseAlbumName = courseAlbumName,
                    defaultArtistId = defaultArtistId,
                    defaultArtistName = defaultArtistName,
                    trackIndex = index + 1
                )
                songs.add(song)
                crossRefs.add(
                    SongArtistCrossRef(
                        songId = song.id,
                        artistId = song.artistId,
                        isPrimary = true
                    )
                )
            }

            val finalAlbums = albums.values.map { album ->
                val trackCount = songs.count { it.albumId == album.id }
                album.copy(songCount = trackCount)
            }

            musicDao.incrementalSyncMusicData(
                songs = songs,
                albums = finalAlbums,
                artists = artists.values.toList(),
                crossRefs = crossRefs,
                deletedSongIds = emptyList()
            )

            // Create / Update Course Playlist
            val playlistId = toLocalCoursePlaylistId(canonicalPath)
            val existing = localPlaylistDao.getPlaylistById(playlistId)
            val now = System.currentTimeMillis()
            val playlist = existing?.copy(
                name = folder.name,
                lastModified = now
            ) ?: PlaylistEntity(
                id = playlistId,
                name = folder.name,
                createdAt = now,
                lastModified = now,
                source = "LOCAL"
            )
            localPlaylistDao.upsertPlaylist(playlist)
            localPlaylistDao.replacePlaylistSongs(playlistId, songs.map { it.id.toString() })

            // Whitelist this folder in allowedDirectories
            val currentAllowed = userPreferencesRepository.allowedDirectoriesFlow.first()
            userPreferencesRepository.updateAllowedDirectories(currentAllowed + canonicalPath)

            Timber.i("addLocalCourse: Successfully added ${folder.name} with ${songs.size} lessons")
            Result.success(songs.size)
        } catch (e: Exception) {
            Timber.e(e, "addLocalCourse failed for ${folder.path}")
            Result.failure(e)
        }
    }

    private suspend fun createOrResolveSongEntity(
        file: File,
        courseAlbumId: Long,
        courseAlbumName: String,
        defaultArtistId: Long,
        defaultArtistName: String,
        trackIndex: Int
    ): SongEntity {
        val canonicalFilePath = normalizePath(file.path)
        val existing = musicDao.getSongByPath(canonicalFilePath)
            ?: musicDao.getSongByPath(file.absolutePath)

        if (existing != null) {
            return existing.copy(
                albumId = courseAlbumId,
                albumName = courseAlbumName,
                trackNumber = if (existing.trackNumber > 0) existing.trackNumber else trackIndex
            )
        }

        // Try reading metadata via AudioMetadataReader
        val metadata = runCatching { AudioMetadataReader.read(file, readArtwork = false) }.getOrNull()
        val title = metadata?.title?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension
        val artist = metadata?.artist?.takeIf { it.isNotBlank() } ?: defaultArtistName
        val duration = metadata?.durationMs?.takeIf { it > 0 } ?: 0L

        // Generate stable 64-bit ID from canonical path
        val songId = toLocalSongId(canonicalFilePath)
        val parentDir = file.parent?.let(::normalizePath) ?: ""

        return SongEntity(
            id = songId,
            title = title,
            artistName = artist,
            artistId = defaultArtistId,
            albumArtist = metadata?.albumArtist,
            albumName = courseAlbumName,
            albumId = courseAlbumId,
            contentUriString = Uri.fromFile(file).toString(),
            albumArtUriString = null,
            duration = duration,
            genre = metadata?.genre,
            filePath = canonicalFilePath,
            parentDirectoryPath = parentDir,
            trackNumber = metadata?.trackNumber ?: trackIndex,
            year = metadata?.year ?: 0,
            dateAdded = file.lastModified() / 1000L,
            mimeType = "audio/*",
            sourceType = SourceType.LOCAL
        )
    }

    suspend fun removeLocalCourse(folderPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val canonicalPath = normalizePath(folderPath)
            val playlistId = toLocalCoursePlaylistId(canonicalPath)

            localPlaylistDao.deletePlaylist(playlistId)

            val currentAllowed = userPreferencesRepository.allowedDirectoriesFlow.first()
            val updated = currentAllowed.map(::normalizePath).filterNot { it == canonicalPath }.toSet()
            userPreferencesRepository.updateAllowedDirectories(updated)

            Timber.i("removeLocalCourse: Removed course for $canonicalPath")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "removeLocalCourse failed for $folderPath")
            Result.failure(e)
        }
    }

    // --- Course Ingestion (Drive) ---

    suspend fun addDriveCourse(folderId: String, folderName: String): Result<Int> =
        withContext(Dispatchers.IO) {
            gdriveRepository.addFolder(folderId, folderName)
            gdriveRepository.syncFolderSongs(folderId)
        }

    suspend fun removeDriveCourse(folderId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            gdriveRepository.removeFolder(folderId)
            Result.success(Unit)
        }

    // --- Batch Apply ---

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

    fun applyBatchChangesInBackground(
        addedLocalFolders: List<File>,
        removedLocalPaths: List<String>,
        addedDriveFolders: List<Pair<String, String>>,
        removedDriveIds: List<String>
    ) {
        appScope.launch(Dispatchers.IO) {
            try {
                _isSyncing.value = true
                // Immediately update allowedDirectories so library and whitelist reflect the choice at once
                val currentAllowed = userPreferencesRepository.allowedDirectoriesFlow.first().map(::normalizePath).toMutableSet()
                removedLocalPaths.forEach { currentAllowed.remove(normalizePath(it)) }
                addedLocalFolders.forEach { currentAllowed.add(normalizePath(it.path)) }
                userPreferencesRepository.updateAllowedDirectories(currentAllowed)

                applyBatchChanges(
                    addedLocalFolders = addedLocalFolders,
                    removedLocalPaths = removedLocalPaths,
                    addedDriveFolders = addedDriveFolders,
                    removedDriveIds = removedDriveIds
                )
            } catch (e: Exception) {
                Timber.e(e, "Background course sync failed")
            } finally {
                _isSyncing.value = false
            }
        }
    }

    suspend fun applyBatchChanges(
        addedLocalFolders: List<File>,
        removedLocalPaths: List<String>,
        addedDriveFolders: List<Pair<String, String>>,
        removedDriveIds: List<String>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Apply deletions first
            for (path in removedLocalPaths) {
                removeLocalCourse(path)
            }
            for (id in removedDriveIds) {
                removeDriveCourse(id)
            }

            // Apply additions
            for (file in addedLocalFolders) {
                addLocalCourse(file)
            }
            for ((id, name) in addedDriveFolders) {
                addDriveCourse(id, name)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "applyBatchChanges failed")
            Result.failure(e)
        }
    }

    // --- Unique ID Generators ---

    private fun toLocalSongId(canonicalPath: String): Long {
        return 1_000_000_000_000L + (canonicalPath.hashCode().toLong().absoluteValue % 1_000_000_000_000L)
    }

    private fun toLocalCourseAlbumId(canonicalPath: String): Long {
        return 2_000_000_000_000L + (canonicalPath.hashCode().toLong().absoluteValue % 1_000_000_000_000L)
    }

    private fun toLocalCourseArtistId(artistName: String): Long {
        return 3_000_000_000_000L + (artistName.hashCode().toLong().absoluteValue % 1_000_000_000_000L)
    }
}
