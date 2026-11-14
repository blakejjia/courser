package com.theveloper.pixelplay.data.ai

import com.theveloper.pixelplay.data.model.Song
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiPlaylistReorderService @Inject constructor(
    private val aiHandler: AiHandler,
    private val json: Json
) {
    suspend fun reorderSongs(songs: List<Song>): Result<List<String>> {
        if (songs.size <= 1) {
            return Result.success(songs.map { it.id })
        }

        return try {
            val songPayload = songs.mapIndexed { _, s ->
                val cleanTitle = s.title.replace("\"", "'").take(100)
                """{"id":"${s.id}","name":"$cleanTitle"}"""
            }.joinToString(",", prefix = "[", postfix = "]")

            val prompt = """
                Analyze the following list of audio tracks or course lessons and determine their most logical sequential order.
                For each track, provide a 1-based integer 'rank' reflecting its sequential position.

                Input tracks:
                $songPayload

                Output Format:
                {"track_id": {"rank": 1}, ...}
            """.trimIndent()

            val rawResponse = aiHandler.generateContent(
                prompt = prompt,
                type = AiSystemPromptType.COURSE_REORDER,
                temperature = 0.1f
            )

            val parsedRanks = parseRanks(rawResponse)
            if (parsedRanks.isEmpty()) {
                throw IllegalStateException("AI returned an empty or unparseable reorder response.")
            }

            // Sort original songs by rank; unranked songs keep their original relative order at the end
            val sortedSongIds = songs.mapIndexed { originalIndex, song ->
                val rank = parsedRanks[song.id] ?: (10000 + originalIndex)
                Pair(song.id, rank)
            }.sortedWith(compareBy({ it.second }))
             .map { it.first }

            Result.success(sortedSongIds)
        } catch (e: Exception) {
            Timber.e(e, "Failed to reorder playlist with AI")
            Result.failure(e)
        }
    }

    internal fun parseRanks(rawResponse: String): Map<String, Int> {
        val cleaned = AiResponseCleaner.cleanJsonResponse(rawResponse)
        val jsonText = AiResponseCleaner.extractJsonObject(cleaned) ?: cleaned

        val rankMap = mutableMapOf<String, Int>()
        try {
            val rootElement = json.parseToJsonElement(jsonText).jsonObject
            for ((id, value) in rootElement) {
                if (value is JsonObject) {
                    val rank = value["rank"]?.jsonPrimitive?.intOrNull
                    if (rank != null) {
                        rankMap[id] = rank
                    }
                } else if (value is JsonPrimitive) {
                    // In case the AI returns direct integer mapping {"id": 1, "id2": 2}
                    value.intOrNull?.let { rankMap[id] = it }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed parsing JSON element with kotlinx.serialization, attempting fallback regex")
            val regex = """"([^"]+)"\s*:\s*(?:\{\s*"rank"\s*:\s*(\d+)|\s*(\d+))""".toRegex()
            regex.findAll(jsonText).forEach { match ->
                val id = match.groupValues[1]
                val rankStr = match.groupValues[2].ifEmpty { match.groupValues[3] }
                rankStr.toIntOrNull()?.let { rankMap[id] = it }
            }
        }
        return rankMap
    }
}
