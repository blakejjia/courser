package com.theveloper.pixelplay.data.ai

import com.theveloper.pixelplay.data.model.Song
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class AiReorderResult(
    val orderedSongIds: List<String>,
    val cleanedTitles: Map<String, String> = emptyMap()
)

@Singleton
class AiPlaylistReorderService @Inject constructor(
    private val aiHandler: AiHandler,
    private val json: Json
) {
    suspend fun reorderSongs(songs: List<Song>): Result<AiReorderResult> {
        if (songs.size <= 1) {
            return Result.success(AiReorderResult(songs.map { it.id }))
        }

        return try {
            val songPayload = songs.mapIndexed { _, s ->
                val cleanTitle = s.title.replace("\"", "'").take(100)
                """{"id":"${s.id}","name":"$cleanTitle"}"""
            }.joinToString(",", prefix = "[", postfix = "]")

            val prompt = """
                Analyze the following list of audio tracks or course lessons, determine their most logical sequential order, and clean up messy audio/lesson titles.
                For each track:
                - Provide a 1-based integer 'rank' reflecting its sequential position.
                - Provide a 'cleaned_title' removing extraneous platform tags (e.g., "[Bilibili]", "[Coursera]"), resolution/format tags ("1080p", "HD"), download hashes, and file extensions, while preserving lesson numbering and clean topic names.

                Input tracks:
                $songPayload

                Output Format:
                {"track_id": {"rank": 1, "cleaned_title": "01 - Introduction"}, ...}
            """.trimIndent()

            val rawResponse = aiHandler.generateContent(
                prompt = prompt,
                type = AiSystemPromptType.COURSE_REORDER,
                temperature = 0.1f
            )

            val (parsedRanks, parsedTitles) = parseRanksAndTitles(rawResponse)
            if (parsedRanks.isEmpty()) {
                throw IllegalStateException("AI returned an empty or unparseable reorder response.")
            }

            // Sort original songs by rank; unranked songs keep their original relative order at the end
            val sortedSongIds = songs.mapIndexed { originalIndex, song ->
                val rank = parsedRanks[song.id] ?: (10000 + originalIndex)
                Pair(song.id, rank)
            }.sortedWith(compareBy({ it.second }))
             .map { it.first }

            Result.success(AiReorderResult(sortedSongIds, parsedTitles))
        } catch (e: Exception) {
            Timber.e(e, "Failed to reorder playlist with AI")
            Result.failure(e)
        }
    }

    internal fun parseRanks(rawResponse: String): Map<String, Int> {
        return parseRanksAndTitles(rawResponse).first
    }

    suspend fun cleanSongTitles(songs: List<Song>): Result<Map<String, String>> {
        if (songs.isEmpty()) {
            return Result.success(emptyMap())
        }

        return try {
            val songPayload = songs.mapIndexed { _, s ->
                val cleanTitle = s.title.replace("\"", "'").take(100)
                """{"id":"${s.id}","name":"$cleanTitle"}"""
            }.joinToString(",", prefix = "[", postfix = "]")

            val prompt = """
                Analyze the following list of audio tracks or course lessons and clean up messy audio/lesson titles.
                For each track:
                - Provide a 'cleaned_title' removing extraneous platform tags (e.g., "[Bilibili]", "[Coursera]"), resolution/format tags ("1080p", "HD"), download hashes, and file extensions, while preserving lesson numbering and clean topic names.
                - Do NOT change track order or ranks; only clean up their titles.

                Input tracks:
                $songPayload

                Output Format:
                {"track_id": "Cleaned Title", ...}
            """.trimIndent()

            val rawResponse = aiHandler.generateContent(
                prompt = prompt,
                type = AiSystemPromptType.COURSE_RENAME,
                temperature = 0.1f
            )

            val parsedTitles = parseCleanedTitlesOnly(rawResponse)
            if (parsedTitles.isEmpty()) {
                throw IllegalStateException("AI returned an empty or unparseable rename response.")
            }

            Result.success(parsedTitles)
        } catch (e: Exception) {
            Timber.e(e, "Failed to clean song titles with AI")
            Result.failure(e)
        }
    }

    internal fun parseCleanedTitlesOnly(rawResponse: String): Map<String, String> {
        val cleaned = AiResponseCleaner.cleanJsonResponse(rawResponse)
        val jsonText = AiResponseCleaner.extractJsonObject(cleaned) ?: cleaned

        val titleMap = mutableMapOf<String, String>()
        try {
            val rootElement = json.parseToJsonElement(jsonText).jsonObject
            for ((id, value) in rootElement) {
                if (value is JsonObject) {
                    val title = value["cleaned_title"]?.jsonPrimitive?.contentOrNull
                        ?: value["title"]?.jsonPrimitive?.contentOrNull
                    if (!title.isNullOrBlank()) {
                        titleMap[id] = title.trim()
                    }
                } else if (value is JsonPrimitive) {
                    val title = value.contentOrNull
                    if (!title.isNullOrBlank()) {
                        titleMap[id] = title.trim()
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed parsing JSON element with kotlinx.serialization, attempting fallback regex")
            val titleObjRegex = """"([^"]+)"\s*:\s*\{[^}]*"cleaned_title"\s*:\s*"([^"]+)"""".toRegex()
            titleObjRegex.findAll(jsonText).forEach { match ->
                val id = match.groupValues[1]
                val title = match.groupValues[2]
                if (title.isNotBlank()) {
                    titleMap[id] = title.trim()
                }
            }
            if (titleMap.isEmpty()) {
                val directTitleRegex = """"([^"]+)"\s*:\s*"([^"]+)"""".toRegex()
                directTitleRegex.findAll(jsonText).forEach { match ->
                    val id = match.groupValues[1]
                    val title = match.groupValues[2]
                    if (title.isNotBlank()) {
                        titleMap[id] = title.trim()
                    }
                }
            }
        }
        return titleMap
    }

    internal fun parseRanksAndTitles(rawResponse: String): Pair<Map<String, Int>, Map<String, String>> {
        val cleaned = AiResponseCleaner.cleanJsonResponse(rawResponse)
        val jsonText = AiResponseCleaner.extractJsonObject(cleaned) ?: cleaned

        val rankMap = mutableMapOf<String, Int>()
        val titleMap = mutableMapOf<String, String>()
        try {
            val rootElement = json.parseToJsonElement(jsonText).jsonObject
            for ((id, value) in rootElement) {
                if (value is JsonObject) {
                    val rank = value["rank"]?.jsonPrimitive?.intOrNull
                    if (rank != null) {
                        rankMap[id] = rank
                    }
                    val title = value["cleaned_title"]?.jsonPrimitive?.contentOrNull
                        ?: value["title"]?.jsonPrimitive?.contentOrNull
                    if (!title.isNullOrBlank()) {
                        titleMap[id] = title.trim()
                    }
                } else if (value is JsonPrimitive) {
                    // In case the AI returns direct integer mapping {"id": 1, "id2": 2}
                    value.intOrNull?.let { rankMap[id] = it }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed parsing JSON element with kotlinx.serialization, attempting fallback regex")
            val rankRegex = """"([^"]+)"\s*:\s*(?:\{\s*"rank"\s*:\s*(\d+)|\s*(\d+))""".toRegex()
            rankRegex.findAll(jsonText).forEach { match ->
                val id = match.groupValues[1]
                val rankStr = match.groupValues[2].ifEmpty { match.groupValues[3] }
                rankStr.toIntOrNull()?.let { rankMap[id] = it }
            }
            val titleRegex = """"([^"]+)"\s*:\s*\{[^}]*"cleaned_title"\s*:\s*"([^"]+)"""".toRegex()
            titleRegex.findAll(jsonText).forEach { match ->
                val id = match.groupValues[1]
                val title = match.groupValues[2]
                if (title.isNotBlank()) {
                    titleMap[id] = title.trim()
                }
            }
        }
        return Pair(rankMap, titleMap)
    }
}
