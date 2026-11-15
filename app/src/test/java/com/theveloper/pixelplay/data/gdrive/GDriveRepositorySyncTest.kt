package com.theveloper.pixelplay.data.gdrive

import android.content.Context
import android.content.SharedPreferences
import com.theveloper.pixelplay.data.database.GDriveDao
import com.theveloper.pixelplay.data.database.GDriveFolderEntity
import com.theveloper.pixelplay.data.database.GDriveSongEntity
import com.theveloper.pixelplay.data.database.LocalPlaylistDao
import com.theveloper.pixelplay.data.database.MusicDao
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GDriveRepositorySyncTest {

    private val api: GDriveApiService = mockk(relaxed = true)
    private val dao: GDriveDao = mockk(relaxed = true)
    private val musicDao: MusicDao = mockk(relaxed = true)
    private val localPlaylistDao: LocalPlaylistDao = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)
    private val sharedPrefs: SharedPreferences = mockk(relaxed = true)

    @BeforeEach
    fun setup() {
        every { context.getSharedPreferences(any(), any()) } returns sharedPrefs
        every { sharedPrefs.getString("gdrive_access_token", any()) } returns "mock_token"
        every { sharedPrefs.getLong("gdrive_token_expires_at", any()) } returns System.currentTimeMillis() + 1000000L
        every { api.hasToken() } returns true
    }

    @Test
    fun syncFolderSongs_concurrentTraversal_preservesNaturalPathOrdering() = runTest {
        val folderId = "root_course_folder"
        val folderEntity = GDriveFolderEntity(id = folderId, name = "My Course", songCount = 0, lastSyncTime = 0)
        coEvery { dao.getAllFoldersList() } returns listOf(folderEntity)
        coEvery { dao.getSongsByFolderList(folderId) } returns emptyList()

        // Root folder has 2 subfolders: "Chapter 1", "Chapter 2"
        coEvery { api.listFolders(folderId, null) } returns """
            {"files": [{"id": "sub_1", "name": "Chapter 1"}, {"id": "sub_2", "name": "Chapter 2"}]}
        """.trimIndent()
        coEvery { api.listAudioFiles(folderId, null) } returns """{"files": []}"""

        // Subfolder 1 has lesson 2 and lesson 10
        coEvery { api.listFolders("sub_1", null) } returns """{"files": []}"""
        coEvery { api.listAudioFiles("sub_1", null) } returns """
            {"files": [
                {"id": "f_1_10", "name": "Lesson 10.mp3", "mimeType": "audio/mpeg", "size": 1000},
                {"id": "f_1_2", "name": "Lesson 2.mp3", "mimeType": "audio/mpeg", "size": 1000}
            ]}
        """.trimIndent()

        // Subfolder 2 has lesson 1
        coEvery { api.listFolders("sub_2", null) } returns """{"files": []}"""
        coEvery { api.listAudioFiles("sub_2", null) } returns """
            {"files": [
                {"id": "f_2_1", "name": "Lesson 1.mp3", "mimeType": "audio/mpeg", "size": 1000}
            ]}
        """.trimIndent()

        val capturedSongs = slot<List<GDriveSongEntity>>()
        coEvery { dao.insertSongs(capture(capturedSongs)) } returns Unit

        val repository = GDriveRepository(api, dao, musicDao, localPlaylistDao, context)
        val result = repository.syncFolderSongs(folderId)
        if (result.isFailure) {
            println("syncFolderSongs failed with: " + result.exceptionOrNull())
            result.exceptionOrNull()?.printStackTrace()
        }

        assertTrue(result.isSuccess, "Expected success but failed with: ${result.exceptionOrNull()?.message}")
        assertEquals(3, result.getOrNull())

        // Natural sort order must be: Chapter 1/Lesson 2.mp3, Chapter 1/Lesson 10.mp3, Chapter 2/Lesson 1.mp3
        val titles = capturedSongs.captured.map { it.title }
        assertEquals(listOf("Lesson 2", "Lesson 10", "Lesson 1"), titles)
    }
}
