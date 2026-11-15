package com.theveloper.pixelplay.presentation.gdrive.dashboard

import com.theveloper.pixelplay.MainCoroutineExtension
import com.theveloper.pixelplay.data.gdrive.GDriveRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainCoroutineExtension::class)
class GDriveDashboardViewModelTest {

    @Test
    fun init_doesNotTriggerAutoSync() = runTest {
        val repository = mockk<GDriveRepository>(relaxed = true)
        every { repository.getFolders() } returns flowOf(emptyList())
        every { repository.isLoggedInFlow } returns MutableStateFlow(true)

        val viewModel = GDriveDashboardViewModel(repository)
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.syncAllFoldersAndSongs() }
    }
}
