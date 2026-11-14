package com.theveloper.pixelplay.data.ai

import com.google.common.truth.Truth.assertThat
import com.theveloper.pixelplay.data.model.Song
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AiPlaylistReorderServiceTest {

    private val aiHandler: AiHandler = mockk()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private lateinit var service: AiPlaylistReorderService

    @BeforeEach
    fun setUp() {
        service = AiPlaylistReorderService(aiHandler, json)
    }

    private fun createMockSong(id: String, title: String): Song {
        val song = mockk<Song>(relaxed = true)
        every { song.id } returns id
        every { song.title } returns title
        return song
    }

    @Test
    fun `parseRanks correctly parses standard JSON rank format`() {
        val raw = """{"song1": {"rank": 2}, "song2": {"rank": 1}}"""
        val result = service.parseRanks(raw)

        assertThat(result["song1"]).isEqualTo(2)
        assertThat(result["song2"]).isEqualTo(1)
    }

    @Test
    fun `parseRanks strips markdown code blocks`() {
        val raw = """
            ```json
            {
               "ch1": {"rank": 1},
               "ch2": {"rank": 2}
            }
            ```
        """.trimIndent()
        val result = service.parseRanks(raw)

        assertThat(result["ch1"]).isEqualTo(1)
        assertThat(result["ch2"]).isEqualTo(2)
    }

    @Test
    fun `parseRanks handles direct integer mapping`() {
        val raw = """{"trackA": 2, "trackB": 1}"""
        val result = service.parseRanks(raw)

        assertThat(result["trackA"]).isEqualTo(2)
        assertThat(result["trackB"]).isEqualTo(1)
    }

    @Test
    fun `reorderSongs reorders list according to ranks`() = runBlocking {
        val song1 = createMockSong(id = "id1", title = "Lesson 02 - Intro")
        val song2 = createMockSong(id = "id2", title = "Lesson 01 - Overview")
        val song3 = createMockSong(id = "id3", title = "Lesson 03 - Advanced")

        coEvery { aiHandler.generateContent(any(), AiSystemPromptType.COURSE_REORDER, any()) } returns
            """{"id1": {"rank": 2}, "id2": {"rank": 1}, "id3": {"rank": 3}}"""

        val result = service.reorderSongs(listOf(song1, song2, song3))

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).containsExactly("id2", "id1", "id3").inOrder()
    }

    @Test
    fun `reorderSongs handles single song list without calling AI`() = runBlocking {
        val song1 = createMockSong(id = "id1", title = "Single")
        val result = service.reorderSongs(listOf(song1))

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).containsExactly("id1")
    }
}
