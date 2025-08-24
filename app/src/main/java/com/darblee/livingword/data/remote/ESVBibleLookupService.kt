package com.darblee.livingword.data.remote

import android.util.Log
import com.darblee.livingword.BuildConfig
import com.darblee.livingword.data.BibleVerseRef
import com.darblee.livingword.data.Verse
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
     * Fetches scripture text for a given Bible verse reference.
     *
     * @param verse The BibleVerseRef object containing book, chapter, and verse numbers.
     * @return An AiServiceResult containing the List<Verse> on success, or an Error on failure.
     */
    suspend fun fetchScripture(verse: BibleVerseRef): AiServiceResult<List<Verse>> {
        if (esvApiKey.isBlank()) {
            Log.e("ESVBibleLookupService", "ESV API Key is missing.")
            return AiServiceResult.Error("Error: ESV API Key is missing.")
        }

        return withContext(Dispatchers.IO) {
            try {
                val encodedBook = URLEncoder.encode(verse.book, "UTF-8")
                val passageQuery = "$encodedBook ${verse.chapter}:${verse.startVerse}-${verse.endVerse}"
                val urlString = "https://api.esv.org/v3/passage/text/?q=$passageQuery&include-passage-references=false&include-verse-numbers=true&include-footnotes=false&include-headings=false"
                val url = URL(urlString)

                val connection = url.openConnection()
                connection.setRequestProperty("Authorization", "Token $esvApiKey")
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.connect()

                val responseText = connection.getInputStream().bufferedReader().use { it.readText() }
                Log.d("ESVBibleLookupService", "Raw ESV API Response: $responseText")
                val apiResponse = jsonParser.decodeFromString<EsvApiResponse>(responseText)

                val passagesList = apiResponse.passages
                if (passagesList.isNullOrEmpty()) {
                    return@withContext AiServiceResult.Error("Error: Passage not found in ESV response.")
                }

                // The API returns a single string with all verses. We need to parse it.
                val combinedPassage = passagesList.joinToString(" ").trim()
                val cleanedPassage = if (combinedPassage.endsWith(" (ESV)")) {
                    combinedPassage.removeSuffix(" (ESV)").trim()
                } else {
                    combinedPassage
                }

                val collectedVerses = mutableListOf<Verse>()
                // This regex finds a verse number like [1], captures the number, and then the text.
                val verseRegex = Regex("""\[(\d+)]\s*([^\[]*)""")

                verseRegex.findAll(cleanedPassage).forEach { matchResult ->
                    val verseNum = matchResult.groupValues[1].toIntOrNull()
                    val verseText = matchResult.groupValues[2].trim()

                    if (verseNum != null && verseText.isNotEmpty()) {
                        collectedVerses.add(Verse(verseNum, verseText))
                    }
                }

                if (collectedVerses.isEmpty() && cleanedPassage.isNotEmpty()) {
                    // Fallback for single-verse queries that might not have a number prefix
                    Log.d("ESVBibleLookupService", "Regex found no verses. Treating response as a single verse.")
                    collectedVerses.add(Verse(verse.startVerse, cleanedPassage))
                }

                AiServiceResult.Success(collectedVerses)

            } catch (e: IOException) {
                Log.e("ESVBibleLookupService", "Network error fetching scripture: ${e.message}", e)
                AiServiceResult.Error("Error: Network request failed fetching scripture.", e)
            } catch (e: SerializationException) {
                Log.e("ESVBibleLookupService", "ESV JSON Parsing Error: ${e.message}", e)
                AiServiceResult.Error("Error: Could not parse scripture response.", e)
            } catch (e: Exception) {
                Log.e("ESVBibleLookupService", "Unexpected error fetching scripture: ${e.message}", e)
                AiServiceResult.Error("Error: An unexpected error occurred fetching scripture.", e)
            }
        }
    }
    
    /**
     * Test method to perform a basic scripture lookup of John 3:16.
     * @return true if successful, false if failed
     */
    suspend fun test(): Boolean {
        return try {
            // Create John 3:16 reference for testing
            val testVerseRef = BibleVerseRef(
                book = "John",
                chapter = 3,
                startVerse = 16,
                endVerse = 16
            )
            
            Log.d("ESVBibleLookupService", "Running test: fetching John 3:16")
            val result = fetchScripture(testVerseRef)
            
            when (result) {
                is AiServiceResult.Success -> {
                    Log.d("ESVBibleLookupService", "Test successful: Retrieved ${result.data.size} verse(s)")
                    true
                }
                is AiServiceResult.Error -> {
                    Log.e("ESVBibleLookupService", "Test failed: ${result.message}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e("ESVBibleLookupService", "Test failed with exception: ${e.message}", e)
            false
        }
    }
}