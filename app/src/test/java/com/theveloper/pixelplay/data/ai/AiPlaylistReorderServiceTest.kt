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
    fun `parseRanksAndTitles parses ranks and cleaned titles`() {
        val raw = """
            {
               "song1": {"rank": 2, "cleaned_title": "02 - Second Lesson"},
               "song2": {"rank": 1, "cleaned_title": "01 - First Lesson"}
            }
        """.trimIndent()
        val (ranks, titles) = service.parseRanksAndTitles(raw)

        assertThat(ranks["song1"]).isEqualTo(2)
        assertThat(ranks["song2"]).isEqualTo(1)
        assertThat(titles["song1"]).isEqualTo("02 - Second Lesson")
        assertThat(titles["song2"]).isEqualTo("01 - First Lesson")
    }

    @Test
    fun `parseRanks strips markdown code blocks`() {
        val raw = """
            ```json
            {
               "ch1": {"rank": 1, "cleaned_title": "Chapter 1"},
               "ch2": {"rank": 2, "cleaned_title": "Chapter 2"}
            }
            ```
        """.trimIndent()
        val (ranks, titles) = service.parseRanksAndTitles(raw)

        assertThat(ranks["ch1"]).isEqualTo(1)
        assertThat(ranks["ch2"]).isEqualTo(2)
        assertThat(titles["ch1"]).isEqualTo("Chapter 1")
        assertThat(titles["ch2"]).isEqualTo("Chapter 2")
    }

    @Test
    fun `parseRanks handles direct integer mapping`() {
        val raw = """{"trackA": 2, "trackB": 1}"""
        val result = service.parseRanks(raw)

        assertThat(result["trackA"]).isEqualTo(2)
        assertThat(result["trackB"]).isEqualTo(1)
    }

    @Test
    fun `reorderSongs reorders list and captures cleaned titles`() = runBlocking {
        val song1 = createMockSong(id = "id1", title = "[Coursera] Lesson 02 - Intro (1080p).mp3")
        val song2 = createMockSong(id = "id2", title = "[Coursera] Lesson 01 - Overview (1080p).mp3")
        val song3 = createMockSong(id = "id3", title = "[Coursera] Lesson 03 - Advanced (1080p).mp3")

        coEvery { aiHandler.generateContent(any(), AiSystemPromptType.COURSE_REORDER, any()) } returns
            """{
                "id1": {"rank": 2, "cleaned_title": "02 - Intro"},
                "id2": {"rank": 1, "cleaned_title": "01 - Overview"},
                "id3": {"rank": 3, "cleaned_title": "03 - Advanced"}
            }"""

        val result = service.reorderSongs(listOf(song1, song2, song3))

        assertThat(result.isSuccess).isTrue()
        val reorderResult = result.getOrNull()!!
        assertThat(reorderResult.orderedSongIds).containsExactly("id2", "id1", "id3").inOrder()
        assertThat(reorderResult.cleanedTitles["id1"]).isEqualTo("02 - Intro")
        assertThat(reorderResult.cleanedTitles["id2"]).isEqualTo("01 - Overview")
        assertThat(reorderResult.cleanedTitles["id3"]).isEqualTo("03 - Advanced")
    }

    @Test
    fun `reorderSongs handles single song list without calling AI`() = runBlocking {
        val song1 = createMockSong(id = "id1", title = "Single")
        val result = service.reorderSongs(listOf(song1))

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()!!.orderedSongIds).containsExactly("id1")
    }

    @Test
    fun `cleanSongTitles parses titles and preserves order`() = runBlocking {
        val song1 = createMockSong(id = "id1", title = "[Bilibili] 01 - Basics.mp3")
        val song2 = createMockSong(id = "id2", title = "[Bilibili] 02 - Intermediate.mp3")

        coEvery { aiHandler.generateContent(any(), AiSystemPromptType.COURSE_RENAME, any()) } returns
            """{
                "id1": "01 - Basics",
                "id2": "02 - Intermediate"
            }"""

        val result = service.cleanSongTitles(listOf(song1, song2))

        assertThat(result.isSuccess).isTrue()
        val titleMap = result.getOrNull()!!
        assertThat(titleMap["id1"]).isEqualTo("01 - Basics")
        assertThat(titleMap["id2"]).isEqualTo("02 - Intermediate")
    }

    @Test
    fun `parseCleanedTitlesOnly handles object format`() {
        val raw = """{"song1": {"cleaned_title": "Lesson 1"}, "song2": {"title": "Lesson 2"}}"""
        val result = service.parseCleanedTitlesOnly(raw)

        assertThat(result["song1"]).isEqualTo("Lesson 1")
        assertThat(result["song2"]).isEqualTo("Lesson 2")
    }
}
