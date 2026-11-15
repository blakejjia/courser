package com.theveloper.pixelplay

import com.theveloper.pixelplay.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SongProgressTest {

    private fun createSong(
        durationMs: Long = 100_000L,
        resumePositionMs: Long = 0L,
        isCompleted: Boolean = false
    ): Song {
        return Song.emptySong().copy(
            id = "test_song_1",
            title = "Lesson 1",
            artist = "Teacher",
            album = "Course 101",
            duration = durationMs,
            resumePositionMs = resumePositionMs,
            isCompleted = isCompleted
        )
    }

    @Test
    fun unplayedSong_isNotCompleted_andHasZeroPercentage() {
        val song = createSong(durationMs = 100_000L, resumePositionMs = 0L, isCompleted = false)
        assertFalse(song.isCompletedTrack)
        assertEquals(0f, song.playbackPercentage, 0.001f)
        assertEquals(0, song.playbackProgressPercent)
    }

    @Test
    fun songPlayedOver90Percent_isTreatedAsCompleted() {
        // 93% played
        val song = createSong(durationMs = 100_000L, resumePositionMs = 93_000L, isCompleted = false)
        assertTrue(song.isCompletedTrack)
        assertEquals(1.0f, song.playbackPercentage, 0.001f)
        assertEquals(100, song.playbackProgressPercent)
    }

    @Test
    fun songReListenedTo40Percent_revertsFromCompletedTo40Percent() {
        // Previously completed (isCompleted = true), but user re-listens to 40%
        val song = createSong(durationMs = 100_000L, resumePositionMs = 40_000L, isCompleted = true)
        assertFalse(song.isCompletedTrack)
        assertEquals(0.40f, song.playbackPercentage, 0.001f)
        assertEquals(40, song.playbackProgressPercent)
    }

    @Test
    fun completedSongWithZeroResumePosition_isTreatedAsCompleted() {
        // e.g. Ended naturally where resume position was cleared to 0
        val song = createSong(durationMs = 100_000L, resumePositionMs = 0L, isCompleted = true)
        assertTrue(song.isCompletedTrack)
        assertEquals(1.0f, song.playbackPercentage, 0.001f)
        assertEquals(100, song.playbackProgressPercent)
    }

    @Test
    fun songAt89Percent_isNotCompleted() {
        val song = createSong(durationMs = 100_000L, resumePositionMs = 89_000L, isCompleted = false)
        assertFalse(song.isCompletedTrack)
        assertEquals(0.89f, song.playbackPercentage, 0.001f)
        assertEquals(89, song.playbackProgressPercent)
    }
}
