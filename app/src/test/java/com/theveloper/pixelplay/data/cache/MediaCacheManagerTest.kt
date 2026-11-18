package com.theveloper.pixelplay.data.cache

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MediaCacheManagerTest {

    @Test
    fun resolveCacheKey_fromGDriveScheme_returnsCanonicalKey() {
        val key = MediaCacheManager.extractKeyFromUriString("gdrive://1a2b3c4d5e")
        assertThat(key).isEqualTo("gdrive:1a2b3c4d5e")
    }

    @Test
    fun resolveCacheKey_fromLocalProxyUrl_returnsCanonicalKeyRegardlessOfPort() {
        val key1 = MediaCacheManager.extractKeyFromUriString("http://127.0.0.1:45678/gdrive/1a2b3c4d5e")
        val key2 = MediaCacheManager.extractKeyFromUriString("http://127.0.0.1:58921/gdrive/1a2b3c4d5e")
        assertThat(key1).isEqualTo("gdrive:1a2b3c4d5e")
        assertThat(key2).isEqualTo("gdrive:1a2b3c4d5e")
    }

    @Test
    fun resolveCacheKey_fromStandardUri_returnsUriString() {
        val uri = "https://example.com/audio/lesson1.mp3"
        assertThat(MediaCacheManager.extractKeyFromUriString(uri)).isEqualTo(uri)
    }
}
