package com.theveloper.pixelplay.data.service

import androidx.media3.common.Player
import com.theveloper.pixelplay.data.database.CourseProgressEntity
import com.theveloper.pixelplay.data.repository.CourseProgressRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CourseProgressTrackerTest {

    private val courseProgressRepository: CourseProgressRepository = mockk(relaxed = true)
    private val player: Player = mockk(relaxed = true)
    private lateinit var tracker: CourseProgressTracker

    @BeforeEach
    fun setUp() {
        tracker = CourseProgressTracker(courseProgressRepository)
    }

    @Test
    fun `onTrackLeaving marks track completed when threshold reached`() = runBlocking {
        every { courseProgressRepository.isCompletionThresholdReached(95_000L, 100_000L) } returns true

        tracker.setCourseContext(
            courseId = "local:/courses/math",
            courseType = CourseProgressEntity.TYPE_LOCAL_FOLDER,
            courseName = "Math",
            songIds = listOf("song_1", "song_2")
        )

        tracker.onTrackLeaving(
            songId = "song_1",
            finalPositionMs = 95_000L,
            durationMs = 100_000L
        )

        coVerify(timeout = 2000) {
            courseProgressRepository.markTrackCompleted(
                songId = "song_1",
                totalDurationMs = 100_000L,
                netListenedMs = 100_000L,
                courseId = "local:/courses/math",
                songIds = listOf("song_1", "song_2")
            )
        }
    }

    @Test
    fun `onTrackLeaving saves resume position when threshold not reached`() = runBlocking {
        every { courseProgressRepository.isCompletionThresholdReached(40_000L, 100_000L) } returns false

        tracker.setCourseContext(
            courseId = "local:/courses/math",
            courseType = CourseProgressEntity.TYPE_LOCAL_FOLDER,
            courseName = "Math",
            songIds = listOf("song_1", "song_2")
        )

        tracker.onTrackLeaving(
            songId = "song_1",
            finalPositionMs = 40_000L,
            durationMs = 100_000L,
            songTitle = "Lesson 1"
        )

        coVerify(timeout = 2000) {
            courseProgressRepository.saveCursor(
                courseId = "local:/courses/math",
                courseType = CourseProgressEntity.TYPE_LOCAL_FOLDER,
                courseName = "Math",
                songId = "song_1",
                positionMs = 40_000L,
                songDurationMs = 100_000L,
                songTitle = "Lesson 1",
                totalSongs = 2,
                songIds = listOf("song_1", "song_2"),
                refreshCompleted = true
            )
        }
    }

    @Test
    fun `onSeek debounces rapid seek events to single persist`() = runBlocking {
        every { player.isPlaying } returns false

        tracker.onTrackStarted(
            songId = "song_1",
            songTitle = "Lesson 1",
            durationMs = 100_000L,
            player = player
        )

        // Rapid seeks
        tracker.onSeek(10_000L)
        delay(100L)
        tracker.onSeek(20_000L)
        delay(100L)
        tracker.onSeek(35_000L)

        // Wait past debounce window (500ms)
        delay(650L)

        coVerify(timeout = 2000, exactly = 1) {
            courseProgressRepository.saveTrackResumePosition("song_1", 35_000L, 100_000L)
        }
        coVerify(exactly = 0) {
            courseProgressRepository.saveTrackResumePosition("song_1", 10_000L, 100_000L)
        }
        coVerify(exactly = 0) {
            courseProgressRepository.saveTrackResumePosition("song_1", 20_000L, 100_000L)
        }
    }

    @Test
    fun `flushImmediate saves position immediately`() = runBlocking {
        every { player.isPlaying } returns false
        every { player.currentPosition } returns 50_000L

        tracker.onTrackStarted(
            songId = "song_1",
            songTitle = "Lesson 1",
            durationMs = 100_000L,
            player = player
        )

        tracker.flushImmediate(player = player)

        coVerify(timeout = 2000) {
            courseProgressRepository.saveTrackResumePosition("song_1", 50_000L, 100_000L)
        }
    }

    @Test
    fun `onPlayPause when paused flushes current position immediately`() = runBlocking {
        every { player.isPlaying } returns false
        every { player.currentPosition } returns 42_000L

        tracker.onTrackStarted(
            songId = "song_1",
            songTitle = "Lesson 1",
            durationMs = 100_000L,
            player = player
        )

        tracker.onPlayPause(isPlaying = false, player = player)

        coVerify(timeout = 2000) {
            courseProgressRepository.saveTrackResumePosition("song_1", 42_000L, 100_000L)
        }
    }
}
