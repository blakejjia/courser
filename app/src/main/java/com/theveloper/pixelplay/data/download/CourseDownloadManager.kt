package com.theveloper.pixelplay.data.download

import android.content.Context
import android.os.Environment
import com.theveloper.pixelplay.data.gdrive.GDriveRepository
import com.theveloper.pixelplay.data.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

sealed interface DownloadStatus {
    data object Idle : DownloadStatus
    data class Downloading(val progress: Float) : DownloadStatus
    data object Completed : DownloadStatus
    data class Failed(val error: String) : DownloadStatus
}

@Singleton
class CourseDownloadManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val gdriveRepository: GDriveRepository,
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val DOWNLOAD_DIR_NAME = "offline_courses"

        fun sanitizeFileName(raw: String): String {
            return raw.replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_").trim('_')
        }

        fun extractDriveFileId(uriString: String): String? {
            if (uriString.startsWith("gdrive://", ignoreCase = true)) {
                val id = uriString.removePrefix("gdrive://").substringBefore('?').substringBefore('/')
                if (id.isNotBlank()) return id
            }
            return null
        }

        fun buildOfflineFileName(driveFileId: String, title: String, mimeType: String?): String {
            val sanitized = sanitizeFileName(title).take(50).ifBlank { "track" }
            val ext = when (mimeType?.lowercase()) {
                "audio/mp4", "audio/m4a", "audio/x-m4a", "audio/aac" -> "m4a"
                "audio/flac", "audio/x-flac" -> "flac"
                "audio/ogg", "audio/opus" -> "opus"
                "audio/wav", "audio/x-wav" -> "wav"
                else -> "mp3"
            }
            return "${driveFileId}_${sanitized}.$ext"
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _downloadStatusMap = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())
    val downloadStatusMap: StateFlow<Map<String, DownloadStatus>> = _downloadStatusMap.asStateFlow()

    fun getDownloadDir(): File {
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
        return File(baseDir, DOWNLOAD_DIR_NAME).apply {
            if (!exists()) mkdirs()
        }
    }

    fun getOfflineFile(song: Song): File? {
        val driveFileId = song.gdriveFileId ?: extractDriveFileId(song.contentUriString) ?: return null
        return getOfflineFileByDriveId(driveFileId)
    }

    fun getOfflineFileByDriveId(driveFileId: String): File? {
        val dir = getDownloadDir()
        if (!dir.exists()) return null
        val matched = dir.listFiles { _, name -> name.startsWith("${driveFileId}_") }
        val found = matched?.firstOrNull { it.exists() && it.length() > 0 }
        return found
    }

    fun isDownloaded(song: Song): Boolean {
        val file = getOfflineFile(song)
        return file != null && file.exists() && file.length() > 0
    }

    fun getStatusForSong(songId: String): DownloadStatus {
        return _downloadStatusMap.value[songId] ?: DownloadStatus.Idle
    }

    fun deleteOfflineCopy(song: Song): Boolean {
        val file = getOfflineFile(song) ?: return false
        val deleted = file.delete()
        if (deleted) {
            _downloadStatusMap.update { it - song.id }
        }
        return deleted
    }

    fun downloadSong(song: Song) {
        val driveFileId = song.gdriveFileId ?: extractDriveFileId(song.contentUriString) ?: return
        if (isDownloaded(song)) {
            _downloadStatusMap.update { it + (song.id to DownloadStatus.Completed) }
            return
        }

        val currentStatus = _downloadStatusMap.value[song.id]
        if (currentStatus is DownloadStatus.Downloading) return

        _downloadStatusMap.update { it + (song.id to DownloadStatus.Downloading(0f)) }

        scope.launch {
            try {
                val fileName = buildOfflineFileName(driveFileId, song.title, song.mimeType)
                val targetFile = File(getDownloadDir(), fileName)
                val tempFile = File(getDownloadDir(), "$fileName.tmp")

                val streamUrl = gdriveRepository.getStreamUrl(driveFileId)
                gdriveRepository.ensureValidToken()
                val authHeader = gdriveRepository.getAuthHeader()

                val request = Request.Builder()
                    .url(streamUrl)
                    .header("Authorization", authHeader)
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    _downloadStatusMap.update {
                        it + (song.id to DownloadStatus.Failed("HTTP ${response.code}"))
                    }
                    response.close()
                    return@launch
                }

                val body = response.body ?: run {
                    _downloadStatusMap.update {
                        it + (song.id to DownloadStatus.Failed("Empty response body"))
                    }
                    response.close()
                    return@launch
                }

                val contentLength = body.contentLength()
                var totalBytesRead = 0L

                response.use {
                    body.byteStream().use { input ->
                        FileOutputStream(tempFile).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var bytesRead: Int
                            var lastProgressEmit = 0L
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                totalBytesRead += bytesRead
                                if (contentLength > 0) {
                                    val progress = (totalBytesRead.toFloat() / contentLength).coerceIn(0f, 1f)
                                    val now = System.currentTimeMillis()
                                    if (now - lastProgressEmit > 200) {
                                        lastProgressEmit = now
                                        _downloadStatusMap.update {
                                            it + (song.id to DownloadStatus.Downloading(progress))
                                        }
                                    }
                                }
                            }
                            output.flush()
                        }
                    }
                }

                if (tempFile.exists() && tempFile.length() > 0) {
                    if (targetFile.exists()) targetFile.delete()
                    val success = tempFile.renameTo(targetFile)
                    if (success) {
                        _downloadStatusMap.update {
                            it + (song.id to DownloadStatus.Completed)
                        }
                        Timber.d("CourseDownloadManager: Downloaded ${song.title} to ${targetFile.absolutePath}")
                    } else {
                        _downloadStatusMap.update {
                            it + (song.id to DownloadStatus.Failed("Failed to rename temp file"))
                        }
                    }
                } else {
                    _downloadStatusMap.update {
                        it + (song.id to DownloadStatus.Failed("Downloaded file is empty"))
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "CourseDownloadManager: Download failed for ${song.title}")
                _downloadStatusMap.update {
                    it + (song.id to DownloadStatus.Failed(e.localizedMessage ?: "Unknown error"))
                }
            }
        }
    }

    fun downloadSongs(songs: List<Song>) {
        scope.launch {
            for (song in songs) {
                if (!isDownloaded(song)) {
                    downloadSong(song)
                }
            }
        }
    }
}
