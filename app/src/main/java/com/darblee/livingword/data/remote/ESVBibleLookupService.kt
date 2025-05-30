package com.darblee.livingword.data.remote

import android.util.Log
import com.darblee.livingword.BibleVerseRef
import com.darblee.livingword.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.URL
import java.net.URLEncoder

/**
 * Represents the JSON structure expected from the ESV API.
 */
@Serializable
private data class EsvApiResponse(
    val query: String? = null,
    val passages: List<String>? = null,
)

/**
 * Service class for fetching Bible scripture from the ESV API.
 */
class ESVBibleLookupService {

    private val esvApiKey = BuildConfig.ESV_BIBLE_API_KEY
    private val jsonParser = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Fetches scripture text for a given Bible verse.
     *
     * @param verse The BibleVerseT object containing book, chapter, and verse numbers.
     * @return A Result object containing the scripture text on success, or an Exception on failure.
     */
    suspend fun fetchScripture(verse: BibleVerseRef): Result<String> {
        if (esvApiKey.isBlank()) {
            Log.e("ESVBibleLookupService", "ESV API Key is missing.")
            return Result.failure(Exception("Error: ESV API Key is missing."))
        }

        return withContext(Dispatchers.IO) {
            try {
                val encodedBook = URLEncoder.encode(verse.book, "UTF-8")
                val passage = "$encodedBook ${verse.chapter}:${verse.startVerse}-${verse.endVerse}"
                val urlString = "https://api.esv.org/v3/passage/text/?q=$passage&include-passage-references=false&include-verse-numbers=true&include-footnotes=false&include-headings=false"
                val url = URL(urlString)

                val connection = url.openConnection()
                connection.setRequestProperty("Authorization", "Token $esvApiKey")
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.connect()

                val responseText = connection.getInputStream().bufferedReader().use { it.readText() }
                Log.d("ESVBibleLookupService", "Raw ESV API Response: $responseText")
                val apiResponse = jsonParser.decodeFromString<EsvApiResponse>(responseText)

                val passageText = apiResponse.passages?.joinToString("\n")?.trim()
                    ?: return@withContext Result.failure(Exception("Error: Passage not found in ESV response."))

                val cleanedText = if (passageText.endsWith(" (ESV)")) {
                    passageText.removeSuffix(" (ESV)").trim()
                } else {
                    passageText
                }
                Result.success(cleanedText)

            } catch (e: IOException) {
                Log.e("ESVBibleLookupService", "Network error fetching scripture: ${e.message}", e)
                Result.failure(Exception("Error: Network request failed fetching scripture.", e))
            } catch (e: SerializationException) {
                Log.e("ESVBibleLookupService", "ESV JSON Parsing Error: ${e.message}", e)
                Result.failure(Exception("Error: Could not parse scripture response.", e))
            } catch (e: Exception) {
                Log.e("ESVBibleLookupService", "Unexpected error fetching scripture: ${e.message}", e)
                Result.failure(Exception("Error: An unexpected error occurred fetching scripture.", e))
            }
        }
    }
}