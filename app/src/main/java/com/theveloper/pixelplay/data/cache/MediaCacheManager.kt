package com.theveloper.pixelplay.data.cache

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(UnstableApi::class)
@Singleton
class MediaCacheManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val MAX_CACHE_SIZE_BYTES = 512L * 1024 * 1024 // 512 MB LRU cache
        private const val CACHE_DIR_NAME = "media_stream_cache"

        /**
         * Resolves a canonical, stable cache key for a given URI string.
         * Translates ephemeral local proxy URLs (e.g. http://127.0.0.1:<port>/gdrive/{fileId})
         * and cloud URIs (e.g. gdrive://{fileId}) to a canonical "gdrive:{fileId}".
         */
        fun extractKeyFromUriString(uriString: String): String {
            // Check for direct gdrive:// scheme
            if (uriString.startsWith("gdrive://", ignoreCase = true)) {
                val fileId = uriString.removePrefix("gdrive://").substringBefore('?').substringBefore('/')
                if (fileId.isNotBlank()) return "gdrive:$fileId"
            }

            // Check for local proxy /gdrive/{fileId}
            val gdriveIdx = uriString.indexOf("/gdrive/")
            if (gdriveIdx != -1) {
                val fileId = uriString.substring(gdriveIdx + "/gdrive/".length)
                    .substringBefore('?')
                    .substringBefore('/')
                if (fileId.isNotBlank()) return "gdrive:$fileId"
            }

            // Check for direct telegram:// scheme or proxy /telegram/{fileId}
            if (uriString.startsWith("telegram://", ignoreCase = true)) {
                val part = uriString.removePrefix("telegram://").substringBefore('?')
                if (part.isNotBlank()) return "telegram:$part"
            }
            val telegramIdx = uriString.indexOf("/telegram/")
            if (telegramIdx != -1) {
                val fileId = uriString.substring(telegramIdx + "/telegram/".length)
                    .substringBefore('?')
                    .substringBefore('/')
                if (fileId.isNotBlank()) return "telegram:$fileId"
            }

            return uriString
        }

        fun extractKeyFromUri(uri: Uri): String {
            return extractKeyFromUriString(uri.toString())
        }
    }

    private val cacheDir: File by lazy {
        File(context.cacheDir, CACHE_DIR_NAME).apply {
            if (!exists()) mkdirs()
        }
    }

    private val evictor by lazy {
        LeastRecentlyUsedCacheEvictor(MAX_CACHE_SIZE_BYTES)
    }

    private val databaseProvider by lazy {
        StandaloneDatabaseProvider(context)
    }

    val cache: SimpleCache by lazy {
        try {
            SimpleCache(cacheDir, evictor, databaseProvider)
        } catch (e: Exception) {
            Timber.e(e, "Failed to create SimpleCache, attempting cache dir recreation")
            cacheDir.deleteRecursively()
            cacheDir.mkdirs()
            SimpleCache(cacheDir, evictor, databaseProvider)
        }
    }

    fun resolveCacheKey(uri: Uri): String = extractKeyFromUri(uri)

    fun resolveCacheKey(uriString: String): String = extractKeyFromUriString(uriString)

    fun release() {
        try {
            cache.release()
        } catch (e: Exception) {
            Timber.w(e, "Error releasing MediaCacheManager SimpleCache")
        }
    }
}
