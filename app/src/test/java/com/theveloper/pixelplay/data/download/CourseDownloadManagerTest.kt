package com.theveloper.pixelplay.data.download

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CourseDownloadManagerTest {

    @Test
    fun sanitizeFileName_removesSpecialCharacters() {
        val raw = "Lesson 1: Introduction / Overview & Basics?.mp3"
        val sanitized = CourseDownloadManager.sanitizeFileName(raw)
        assertThat(sanitized).doesNotContain(":")
        assertThat(sanitized).doesNotContain("/")
        assertThat(sanitized).doesNotContain("?")
    }

    @Test
    fun buildOfflineFileName_createsConsistentNameWithExtension() {
        val fileNameMp3 = CourseDownloadManager.buildOfflineFileName(
            driveFileId = "1a2b3c4d5e",
            title = "Lesson 1",
            mimeType = "audio/mpeg"
        )
        assertThat(fileNameMp3).isEqualTo("1a2b3c4d5e_Lesson_1.mp3")

        val fileNameM4a = CourseDownloadManager.buildOfflineFileName(
            driveFileId = "1a2b3c4d5e",
            title = "Lesson 2",
            mimeType = "audio/mp4"
        )
        assertThat(fileNameM4a).isEqualTo("1a2b3c4d5e_Lesson_2.m4a")
    }

    @Test
    fun extractDriveFileIdFromUriString_returnsDriveId() {
        val uri = "gdrive://drive_id_999"
        assertThat(CourseDownloadManager.extractDriveFileId(uri)).isEqualTo("drive_id_999")
    }
}
