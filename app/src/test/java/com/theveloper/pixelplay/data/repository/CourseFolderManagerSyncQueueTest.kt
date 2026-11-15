package com.theveloper.pixelplay.data.repository

import android.content.Context
import com.theveloper.pixelplay.MainCoroutineExtension
import com.theveloper.pixelplay.data.database.GDriveDao
import com.theveloper.pixelplay.data.database.LocalPlaylistDao
import com.theveloper.pixelplay.data.database.MusicDao
import com.theveloper.pixelplay.data.gdrive.GDriveRepository
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainCoroutineExtension::class)
class CourseFolderManagerSyncQueueTest {

    @Test
    fun enqueueBatchChanges_executesSequentiallyViaQueue() = runTest {
        val appScope = CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

        val context = mockk<Context>(relaxed = true)
        val localPlaylistDao = mockk<LocalPlaylistDao>(relaxed = true)
        val musicDao = mockk<MusicDao>(relaxed = true)
        val gdriveRepository = mockk<GDriveRepository>(relaxed = true)
        val gdriveDao = mockk<GDriveDao>(relaxed = true)
        val userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)

        every { gdriveRepository.syncProgressFlow } returns MutableStateFlow(GDriveRepository.GDriveSyncProgress())
        every { userPreferencesRepository.allowedDirectoriesFlow } returns flowOf(emptySet())
        coEvery { userPreferencesRepository.updateAllowedDirectories(any()) } returns Unit
        coEvery { gdriveRepository.removeFolder(any()) } returns Unit

        val concurrentExecutionCount = AtomicInteger(0)
        val maxConcurrentObserved = AtomicInteger(0)
        val completedCount = AtomicInteger(0)
        val latch = java.util.concurrent.CountDownLatch(2)

        coEvery { gdriveRepository.addFolder(any(), any()) } returns Unit
        coEvery { gdriveRepository.syncFolderSongs(any(), any()) } coAnswers {
            val count = concurrentExecutionCount.incrementAndGet()
            maxConcurrentObserved.updateAndGet { maxOf(it, count) }
            Thread.sleep(50) // simulate network work on IO thread
            concurrentExecutionCount.decrementAndGet()
            completedCount.incrementAndGet()
            latch.countDown()
            Result.success(1)
        }

        val manager = CourseFolderManager(
            context = context,
            appScope = appScope,
            localPlaylistDao = localPlaylistDao,
            musicDao = musicDao,
            gdriveRepository = gdriveRepository,
            gdriveDao = gdriveDao,
            userPreferencesRepository = userPreferencesRepository
        )

        // Enqueue two batches concurrently
        manager.applyBatchChangesInBackground(
            addedLocalFolders = emptyList(),
            removedLocalPaths = emptyList(),
            addedDriveFolders = listOf("folder_1" to "Course 1"),
            removedDriveIds = emptyList()
        )

        manager.applyBatchChangesInBackground(
            addedLocalFolders = emptyList(),
            removedLocalPaths = emptyList(),
            addedDriveFolders = listOf("folder_2" to "Course 2"),
            removedDriveIds = emptyList()
        )

        assertTrue(latch.await(5, java.util.concurrent.TimeUnit.SECONDS), "Timed out waiting for sync queue to process")

        assertEquals(2, completedCount.get(), "Both queued batches should have completed")
        assertEquals(1, maxConcurrentObserved.get(), "There should never be more than 1 sync running concurrently")
    }
}
