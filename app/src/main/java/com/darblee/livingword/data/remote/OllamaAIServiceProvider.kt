package com.darblee.livingword.data.remote

import android.util.Log
import com.darblee.livingword.AIServiceConfig
import com.darblee.livingword.AIServiceType
import com.darblee.livingword.data.BibleVerseRef
import com.darblee.livingword.data.Verse
import com.darblee.livingword.ui.viewmodels.ScoreData
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.serialization.json.Json
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Provider implementation for Ollama AI service
 */
class OllamaAIServiceProvider : AIServiceProvider {
    
    override val providerId: String = "ollama_ai"
    override val displayName: String = "Ollama AI"
    override val serviceType: AIServiceType = AIServiceType.OLLAMA
    override val defaultModel: String = "hf.co/mradermacher/Protestant-Christian-Bible-Expert-v2.0-12B-i1-GGUF:IQ4_XS"

    override val priority: Int = 2 // Lower priority than main services for now
    
    private var isConfigured = false
    private var initializationError: String? = null
    private val ollamaService = OllamaAIService.getInstance()

    private var currentConfig: AIServiceConfig? = null


    companion object {
        private const val TAG = "OllamaAIProvider"
    }
    
    override fun configure(config: AIServiceConfig): Boolean {
        return try {
            // Ollama service doesn't use API keys like other services
            // It connects directly to the local Ollama server

            currentConfig = config

            if (ollamaService.isInitialized()) {
                isConfigured = true
                initializationError = null
                Log.i(TAG, "Ollama AI provider configured successfully")
                true
            } else {
                initializationError = ollamaService.getInitializationError()
                isConfigured = false
                Log.e(TAG, "Failed to configure Ollama AI provider: $initializationError")
                false
            }
        } catch (e: Exception) {
            initializationError = "Configuration failed: ${e.message}"
            isConfigured = false
            Log.e(TAG, "Exception during configuration", e)
            false
        }
    }
    
    override fun isInitialized(): Boolean = isConfigured && ollamaService.isInitialized()
    
    override fun getInitializationError(): String? = initializationError
    
    override suspend fun test(): Boolean {
        if (currentConfig == null) { return false }

        return if (!isInitialized()) {
            Log.w(TAG, "Cannot test - service not initialized")
            false
        } else {
            try {
                val result = ollamaService.testConnection(currentConfig!!.modelName)
                when (result) {
                    is AiServiceResult.Success -> {
                        Log.i(TAG, "Test successful: ${result.data}")
                        true
                    }
                    is AiServiceResult.Error -> {
                        Log.e(TAG, "Test failed: ${result.message}")
                        false
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Test failed with exception", e)
                false
            }
        }
    }
    
    override suspend fun fetchScripture(
        verseRef: BibleVerseRef,
        translation: String,
        systemInstruction: String,
        userPrompt: String
    ): AiServiceResult<List<Verse>> {
        if (!isInitialized()) {
            return AiServiceResult.Error("Ollama AI not initialized: $initializationError")
        }
        
        // Ollama AI can fetch scripture using custom prompts
        val customPrompt = """
            As a Christian Bible expert, provide the exact Bible verse text for ${verseRef.book} ${verseRef.chapter}:${verseRef.startVerse}${if (verseRef.startVerse != verseRef.endVerse) "-${verseRef.endVerse}" else ""} in the $translation translation.
            
            Return ONLY a JSON array in the following format:
            [
                {
                    "verse_num": verse_number,
                    "verse_string": "exact_verse_text"
                }
            ]
            
            Do not include any commentary or explanations, only the JSON array with the verse text.
        """.trimIndent()
        
        return try {
            val request = OllamaRequest(
                model = defaultModel,
                prompt = customPrompt,
                stream = false,
                options = OllamaOptions(temperature = 0.1f, max_tokens = 500)
            )
            
            val response = withTimeoutOrNull(15000L) {
                ollamaService.ollamaService?.generateResponse(request)
            }
            
            if (response == null) {
                Log.w(TAG, "Scripture fetch timed out after 10 seconds")
                AiServiceResult.Error("Scripture fetch error: Timed out waiting for 10000 ms")
            } else if (response.isSuccessful) {
                val responseBody = response.body()
                if (responseBody != null && responseBody.done) {
                    parseScriptureResponse(responseBody.response)
                } else {
                    AiServiceResult.Error("Invalid scripture response from Ollama AI")
                }
            } else {
                AiServiceResult.Error("Scripture fetch failed: HTTP ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching scripture", e)
            AiServiceResult.Error("Scripture fetch error: ${e.message}")
        }
    }
    
    override suspend fun getKeyTakeaway(
        verseRef: String,
        systemInstruction: String,
        userPrompt: String
    ): AiServiceResult<String> {
        if (!isInitialized()) {
            return AiServiceResult.Error("Ollama AI not initialized: $initializationError")
        }
        
        // Use the OllamaAIService's built-in takeaway method which already
        // has Reformed-specific prompting
        return try {
            withTimeoutOrNull(15000L) {
                ollamaService.getKeyTakeaway(verseRef)
            } ?: AiServiceResult.Error("Key takeaway error: Timed out waiting for 10000 ms")
        } catch (e: Exception) {
            Log.e(TAG, "Error getting key takeaway", e)
            AiServiceResult.Error("Key takeaway error: ${e.message}")
        }
    }
    
    override suspend fun getAIScore(
        verseRef: String,
        userApplicationComment: String,
        systemInstruction: String,
        userPrompt: String,
        applicationFeedbackPrompt: String
    ): AiServiceResult<ScoreData> {
        if (!isInitialized()) {
            return AiServiceResult.Error("Ollama AI not initialized: $initializationError")
        }
        
        // Create a Reformed-specific scoring prompt
        val reformedScoringPrompt = """
            As a Christian Bible expert, evaluate this application of $verseRef.
            
            User's application: "$userApplicationComment"
            
            Evaluate the contextual accuracy of how the user is applying this verse.
            
            Provide a score between 0 and 100 for contextual accuracy.
            
            Respond ONLY in the following JSON format:
            {
                "DirectQuoteScore": 0,
                "ContextScore": [your score 0-100],
                "DirectQuoteExplanation": "",
                "ContextExplanation": "[Your explanation of the context score]",
                "ApplicationFeedback": "[Constructive feedback on the user's application, 10-12 sentences maximum]"
            }
            
            Ensure your response is ONLY valid JSON with no other text.
        """.trimIndent()
        
        return try {
            val request = OllamaRequest(
                model = defaultModel,
                prompt = reformedScoringPrompt,
                stream = false,
                options = OllamaOptions(temperature = 0.7f, max_tokens = 1200)
            )
            
            val response = withTimeoutOrNull(15000L) {
                ollamaService.ollamaService?.generateResponse(request)
            }
            
            if (response == null) {
                Log.w(TAG, "AI score timed out after 10 seconds")
                AiServiceResult.Error("AI score error: Timed out waiting for 10000 ms")
            } else if (response.isSuccessful) {
                val responseBody = response.body()
                if (responseBody != null && responseBody.done) {
                    parseScoreResponse(responseBody.response)
                } else {
                    AiServiceResult.Error("Invalid scoring response from Ollama AI")
                }
            } else {
                AiServiceResult.Error("Scoring failed: HTTP ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting AI score", e)
            AiServiceResult.Error("AI score error: ${e.message}")
        }
    }
    
    override suspend fun validateKeyTakeawayResponse(
        systemInstruction: String,
        userPrompt: String
    ): AiServiceResult<Boolean> {
        if (!isInitialized()) {
            return AiServiceResult.Error("Ollama AI not initialized: $initializationError")
        }
        
        // Extract verse reference and takeaway from the user prompt
        // This is a simplified extraction - in production you might want more robust parsing
        val verseRefMatch = Regex("Bible verse ([^:]+):").find(userPrompt)
        val takeawayMatch = Regex("\"([^\"]+)\"").find(userPrompt)
        
        if (verseRefMatch != null && takeawayMatch != null) {
            val verseRef = verseRefMatch.groupValues[1]
            val takeaway = takeawayMatch.groupValues[1]
            
            return try {
                withTimeoutOrNull(15000L) {
                    ollamaService.validateKeyTakeawayResponse(verseRef, takeaway)
                } ?: AiServiceResult.Error("Takeaway validation error: Timed out waiting for 10000 ms")
            } catch (e: Exception) {
                Log.e(TAG, "Error validating takeaway", e)
                AiServiceResult.Error("Takeaway validation error: ${e.message}")
            }
        } else {
            return AiServiceResult.Error("Could not parse verse reference or takeaway from validation prompt")
        }
    }
    
    override suspend fun getNewVersesBasedOnDescription(
        description: String,
        systemInstruction: String,
        userPrompt: String
    ): AiServiceResult<List<BibleVerseRef>> {
        if (!isInitialized()) {
            return AiServiceResult.Error("Ollama AI not initialized: $initializationError")
        }
        
        // Create a Reformed-specific verse search prompt
        val reformedSearchPrompt = """
            As a Christian Bible expert, suggest Bible verses that relate to the following description or topic from a theological perspective: "$description"
            
            Focus on verses that align with Biblical doctrines and provide solid biblical foundation for the topic.
            
            Return ONLY a JSON array of verse references in this exact format:
            [
                {"book": "BookName", "chapter": number, "startVerse": number, "endVerse": number}
            ]
            
            Suggest 3-5 relevant verses. Do not include any other text or explanations.
        """.trimIndent()
        
        return try {
            val request = OllamaRequest(
                model = defaultModel,
                prompt = reformedSearchPrompt,
                stream = false,
                options = OllamaOptions(temperature = 0.5f, max_tokens = 300)
            )
            
            val response = withTimeoutOrNull(15000L) {
                ollamaService.ollamaService?.generateResponse(request)
            }
            
            if (response == null) {
                Log.w(TAG, "Verse search timed out after 10 seconds")
                AiServiceResult.Error("Verse search error: Timed out waiting for 10000 ms")
            } else if (response.isSuccessful) {
                val responseBody = response.body()
                if (responseBody != null && responseBody.done) {
                    parseVerseSearchResponse(responseBody.response)
                } else {
                    AiServiceResult.Error("Invalid verse search response from Ollama AI")
                }
            } else {
                AiServiceResult.Error("Verse search failed: HTTP ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching verses", e)
            AiServiceResult.Error("Verse search error: ${e.message}")
        }
    }
    
    
    // Helper methods for parsing responses
    private fun parseScriptureResponse(response: String): AiServiceResult<List<Verse>> {
        return try {
            val cleanResponse = response.trim()
            Log.d(TAG, "Parsing scripture response: $cleanResponse")
            
            // Check if response looks like JSON
            if (!cleanResponse.startsWith("[") && !cleanResponse.startsWith("{")) {
                Log.w(TAG, "Response doesn't appear to be JSON, treating as raw text")
                // If it's not JSON, assume it's raw verse text and create a single verse
                return AiServiceResult.Success(listOf(Verse(1, cleanResponse)))
            }
            
            val json = Json { ignoreUnknownKeys = true }
            val verses = json.decodeFromString<Array<Verse>>(cleanResponse)
            
            val validVerses = verses.mapNotNull { verse ->
                when {
                    false -> {
                        Log.w(TAG, "Skipping null verse in response")
                        null
                    }
                    verse.verseString?.isBlank() != false -> {
                        Log.w(TAG, "Skipping verse with null/blank text: ${verse?.verseNum}")
                        null
                    }
                    else -> verse
                }
            }
            
            if (validVerses.isEmpty()) {
                Log.e(TAG, "No valid verses found in response: $cleanResponse")
                AiServiceResult.Error("No valid scripture verses found in response")
            } else {
                Log.d(TAG, "Successfully parsed ${validVerses.size} verses")
                AiServiceResult.Success(validVerses)
            }
            
        } catch (e: kotlinx.serialization.SerializationException) {
            Log.e(TAG, "Failed to parse scripture JSON: $response", e)
            // Try to extract any text that might be a verse
            val fallbackVerse = response.trim().takeIf { it.isNotBlank() }
            if (fallbackVerse != null) {
                Log.d(TAG, "Using fallback parsing for non-JSON response")
                AiServiceResult.Success(listOf(Verse(1, fallbackVerse)))
            } else {
                AiServiceResult.Error("Invalid scripture JSON response and no fallback text")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing scripture response", e)
            AiServiceResult.Error("Scripture parsing error: ${e.message}")
        }
    }
    
    private fun parseScoreResponse(response: String): AiServiceResult<ScoreData> {
        return try {
            val cleanResponse = response.trim()
            Log.d(TAG, "Parsing score response: $cleanResponse")
            
            val gson = Gson()
            val scoreData = gson.fromJson(cleanResponse, ScoreData::class.java)
            
            // Validate that scoreData is properly formed and handle potential null fields
            if (scoreData == null) {
                Log.e(TAG, "Gson returned null ScoreData from response: $cleanResponse")
                return AiServiceResult.Error("Failed to parse score response - null result")
            }
            
            // Validate required fields aren't null (they should be non-null but JSON parsing might still produce nulls)
            try {
                if (scoreData.ContextExplanation.isBlank() || scoreData.ApplicationFeedback.isBlank()) {
                    Log.w(TAG, "ScoreData has blank explanation or feedback fields")
                    // Still return success but with a warning
                }
                Log.d(TAG, "Successfully parsed ScoreData with ContextScore: ${scoreData.ContextScore}")
                AiServiceResult.Success(scoreData)
            } catch (e: NullPointerException) {
                Log.e(TAG, "ScoreData has null fields - creating fallback response", e)
                // Create a fallback ScoreData with default values
                val fallbackScore = ScoreData(
                    ContextScore = 0,
                    ContextExplanation = "Unable to parse explanation from  Bible AI response",
                    ApplicationFeedback = "Unable to parse feedback from  Bible AI response"
                )
                AiServiceResult.Success(fallbackScore)
            }
            
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "Failed to parse score JSON: $response", e)
            AiServiceResult.Error("Invalid score JSON response: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing score response", e)
            AiServiceResult.Error("Score parsing error: ${e.message}")
        }
    }
    
    private fun parseVerseSearchResponse(response: String): AiServiceResult<List<BibleVerseRef>> {
        return try {
            val cleanResponse = response.trim()
            val gson = Gson()
            val verses = gson.fromJson(cleanResponse, Array<BibleVerseRef>::class.java)
            AiServiceResult.Success(verses.toList())
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "Failed to parse verse search JSON: $response", e)
            AiServiceResult.Error("Invalid verse search JSON response")
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing verse search response", e)
            AiServiceResult.Error("Verse search parsing error: ${e.message}")
        }
    }
}