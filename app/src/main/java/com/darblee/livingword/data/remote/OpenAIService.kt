package com.darblee.livingword.data.remote

import android.util.Log
import com.darblee.livingword.AISettings
import com.darblee.livingword.data.BibleVerseRef
import com.darblee.livingword.data.Verse
import com.darblee.livingword.ui.viewmodels.ScoreData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Service object for interacting with the OpenAI API.
 * Implemented as a singleton to match GeminiAIService structure.
 * This service provides fallback functionality when Gemini AI fails.
 */
object OpenAIService {

    private var openAiApiKey: String? = null
    private var initializationErrorMessage: String? = null
    private var isConfigured = false
    private var currentAISettings: AISettings? = null

    // JSON parser for handling responses from the OpenAI API
    private val jsonParser = Json { 
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * Configures the OpenAI service with the provided settings.
     */
    fun configure(settings: AISettings) {
        try {
            currentAISettings = settings
            openAiApiKey = settings.openAiApiKey
            
            if (openAiApiKey.isNullOrBlank()) {
                initializationErrorMessage = "OpenAI API Key missing in configuration"
                isConfigured = false
                Log.e("OpenAIService", "Configuration failed: API Key missing")
            } else {
                isConfigured = true
                initializationErrorMessage = null
                Log.i("OpenAIService", "OpenAI service configured successfully")
            }
            
        } catch (e: Exception) {
            initializationErrorMessage = "Failed to initialize OpenAI service: ${e.localizedMessage}"
            isConfigured = false
            Log.e("OpenAIService", "Configuration error", e)
        }
    }

    /**
     * Checks if the service is properly initialized and ready for use.
     */
    fun isInitialized(): Boolean = isConfigured && !openAiApiKey.isNullOrBlank() && initializationErrorMessage == null

    /**
     * Gets the initialization error message, if any.
     */
    fun getInitializationError(): String? = initializationErrorMessage

    /**
     * Fetches scripture verses for a given reference and translation.
     */
    suspend fun fetchScripture(
        verseRef: BibleVerseRef, 
        translation: String,
        systemInstruction: String,
        userPrompt: String = ""
    ): AiServiceResult<List<Verse>> {
        return withContext(Dispatchers.IO) {
            try {
                if (!isInitialized()) {
                    return@withContext AiServiceResult.Error("OpenAI service not initialized: ${getInitializationError()}")
                }

                Log.d("OpenAIService", "Fetching scripture for $verseRef in $translation using centralized prompts")
                Log.d("OpenAIService", "Using prompt: $userPrompt")

                val response = callOpenAI(userPrompt, maxTokens = 500, systemInstruction = systemInstruction)
                
                when (response) {
                    is AiServiceResult.Success -> {
                        try {
                            val verses = parseScriptureResponse(response.data)
                            AiServiceResult.Success(verses)
                        } catch (e: Exception) {
                            Log.e("OpenAIService", "Failed to parse scripture response", e)
                            AiServiceResult.Error("Failed to parse scripture response: ${e.message}", e)
                        }
                    }
                    is AiServiceResult.Error -> response
                }
            } catch (e: Exception) {
                Log.e("OpenAIService", "Error fetching scripture", e)
                AiServiceResult.Error("Error fetching scripture: ${e.message}", e)
            }
        }
    }

    /**
     * Gets a key takeaway from a Bible verse.
     */
    suspend fun getKeyTakeaway(
        verseRef: String,
        systemInstruction: String,
        userPrompt: String = ""
    ): AiServiceResult<String> {
        return withContext(Dispatchers.IO) {
            try {
                if (!isInitialized()) {
                    return@withContext AiServiceResult.Error("OpenAI service not initialized: ${getInitializationError()}")
                }

                Log.d("OpenAIService", "Getting key takeaway for $verseRef using centralized prompts")

                val response = callOpenAI(userPrompt, maxTokens = 300, systemInstruction = systemInstruction)
                
                when (response) {
                    is AiServiceResult.Success -> AiServiceResult.Success(response.data.trim())
                    is AiServiceResult.Error -> response
                }
            } catch (e: Exception) {
                Log.e("OpenAIService", "Error getting key takeaway", e)
                AiServiceResult.Error("Error getting key takeaway: ${e.message}", e)
            }
        }
    }

    /**
     * Gets AI score and feedback for user's memorized verse.
     * This is the main function that needs to match GeminiAIService behavior.
     */
    suspend fun getAIScore(
        verseRef: String, 
        userApplicationComment: String,
        systemInstruction: String,
        userPrompt: String,
        applicationFeedbackPrompt: String
    ): AiServiceResult<ScoreData> {
        return withContext(Dispatchers.IO) {
            try {
                if (!isInitialized()) {
                    return@withContext AiServiceResult.Error("OpenAI service not initialized: ${getInitializationError()}")
                }

                Log.d("OpenAIService", "Getting AI score for $verseRef")
                Log.d("OpenAIService", "Sending optimized prompt to OpenAI (DirectQuote fields removed for token reduction)")

                val response = callOpenAI(userPrompt, maxTokens = 800, systemInstruction = systemInstruction)
                
                when (response) {
                    is AiServiceResult.Success -> {
                        try {
                            // Clean the JSON response
                            var cleanedJson = response.data
                                .replace("```json", "")
                                .replace("```", "")
                                .replace("**JSON:**", "")
                                .replace("**Response:**", "")
                                .trim()
                            
                            // Remove any leading/trailing text that isn't JSON
                            val jsonStart = cleanedJson.indexOf('{')
                            val jsonEnd = cleanedJson.lastIndexOf('}')
                            
                            if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
                                cleanedJson = cleanedJson.substring(jsonStart, jsonEnd + 1)
                            }
                            
                            Log.d("OpenAIService", "Cleaned JSON for parsing: $cleanedJson")
                            
                            val parseResponse = jsonParser.decodeFromString<ScoreData>(cleanedJson)
                            
                            // Note: DirectQuoteScore and DirectQuoteExplanation fields have been removed to reduce token usage
                            
                            // Get application feedback separately using centralized prompt
                            val feedbackSystemInstruction = "You are an expert in theology. You provide insightful and encouraging feedback on how users apply Bible verses to their lives."
                            val applicationFeedback = getApplicationFeedback(verseRef, feedbackSystemInstruction, applicationFeedbackPrompt)
                            parseResponse.ApplicationFeedback = applicationFeedback

                            Log.d("OpenAIService", "OpenAI response processed with hardcoded DirectQuote values (token optimization)")
                            AiServiceResult.Success(parseResponse)
                            
                        } catch (e: Exception) {
                            Log.e("OpenAIService", "Failed to parse AI response JSON: ${response.data}", e)
                            AiServiceResult.Error("OpenAI returned invalid JSON format. Please try again.", e)
                        }
                    }
                    is AiServiceResult.Error -> response
                }
            } catch (e: Exception) {
                Log.e("OpenAIService", "Error calling OpenAI API", e)
                AiServiceResult.Error("Could not get AI score from OpenAI (${e.javaClass.simpleName})", e)
            }
        }
    }

    /**
     * Gets application feedback for user's verse application.
     */
    suspend fun getApplicationFeedback(
        verseRef: String, 
        systemInstruction: String,
        userPrompt: String = ""
    ): String {
        return withContext(Dispatchers.IO) {
            try {
                if (!isInitialized()) {
                    Log.w("OpenAIService", "Service not initialized, returning empty feedback")
                    return@withContext ""
                }

                Log.d("OpenAIService", "Getting application feedback for $verseRef")

                val response = callOpenAI(userPrompt, maxTokens = 300, systemInstruction = systemInstruction)
                
                when (response) {
                    is AiServiceResult.Success -> response.data.trim()
                    is AiServiceResult.Error -> {
                        Log.w("OpenAIService", "Failed to get application feedback: ${response.message}")
                        ""
                    }
                }
            } catch (e: Exception) {
                Log.e("OpenAIService", "Error getting application feedback when calling OpenAI API: ${e.message}", e)
                ""
            }
        }
    }

    /**
     * Validates a key takeaway response.
     */
    suspend fun validateKeyTakeawayResponse(
        systemInstruction: String,
        userPrompt: String = ""
    ): AiServiceResult<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                if (!isInitialized()) {
                    return@withContext AiServiceResult.Error("OpenAI service not initialized: ${getInitializationError()}")
                }

                Log.d("OpenAIService", "Validating takeaway using centralized prompts")

                val response = callOpenAI(userPrompt, maxTokens = 50, systemInstruction = systemInstruction)
                
                when (response) {
                    is AiServiceResult.Success -> {
                        val result = response.data.trim().lowercase() == "true"
                        AiServiceResult.Success(result)
                    }
                    is AiServiceResult.Error -> response
                }
            } catch (e: Exception) {
                Log.e("OpenAIService", "Error validating takeaway", e)
                AiServiceResult.Error("Error validating takeaway: ${e.message}", e)
            }
        }
    }

    /**
     * Gets new verses based on a description.
     */
    suspend fun getNewVersesBasedOnDescription(
        description: String,
        systemInstruction: String,
        userPrompt: String = ""
    ): AiServiceResult<List<BibleVerseRef>> {
        return withContext(Dispatchers.IO) {
            try {
                if (!isInitialized()) {
                    return@withContext AiServiceResult.Error("OpenAI service not initialized: ${getInitializationError()}")
                }

                Log.d("OpenAIService", "Finding verses for description: $description using centralized prompts")

                val response = callOpenAI(userPrompt, maxTokens = 400, systemInstruction = systemInstruction)
                
                when (response) {
                    is AiServiceResult.Success -> {
                        try {
                            val verses = parseVerseReferences(response.data)
                            AiServiceResult.Success(verses)
                        } catch (e: Exception) {
                            Log.e("OpenAIService", "Failed to parse verse references", e)
                            AiServiceResult.Error("Failed to parse verse references: ${e.message}", e)
                        }
                    }
                    is AiServiceResult.Error -> response
                }
            } catch (e: Exception) {
                Log.e("OpenAIService", "Error getting verses by description", e)
                AiServiceResult.Error("Error getting verses by description: ${e.message}", e)
            }
        }
    }

    // Private helper methods

    /**
     * Makes a call to the OpenAI API with optional system instruction.
     */
    private fun callOpenAI(prompt: String, maxTokens: Int = 500, systemInstruction: String? = null): AiServiceResult<String> {
        return try {
            val url = URL("https://api.openai.com/v1/chat/completions")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $openAiApiKey")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            
            val jsonBody = JSONObject().apply {
                put("model", "gpt-4o-mini") // Using GPT-4o Mini as GPT-5 Mini might not be available yet
                put("messages", org.json.JSONArray().apply {
                    // Add system instruction if provided
                    systemInstruction?.let { sysInst ->
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", sysInst)
                        })
                    }
                    // Add user prompt
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                })
                put("max_tokens", maxTokens) // GPT-4o Mini uses max_tokens
                put("temperature", 0.7)
            }
            
            Log.d("OpenAIService", "Sending request to OpenAI: ${jsonBody.toString()}")
            
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(jsonBody.toString())
                writer.flush()
            }
            
            val responseCode = connection.responseCode
            val responseText = if (responseCode == 200) {
                connection.inputStream.bufferedReader().readText()
            } else {
                val errorText = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                Log.e("OpenAIService", "OpenAI API error $responseCode: $errorText")
                return AiServiceResult.Error("OpenAI API error $responseCode: $errorText")
            }
            
            Log.d("OpenAIService", "OpenAI response code: $responseCode")
            Log.d("OpenAIService", "Full OpenAI response: $responseText")
            
            // Parse the OpenAI response to extract the content
            val jsonResponse = JSONObject(responseText)
            Log.d("OpenAIService", "Parsed JSON response: $jsonResponse")
            
            val choices = jsonResponse.getJSONArray("choices")
            Log.d("OpenAIService", "Choices array length: ${choices.length()}")
            
            if (choices.length() > 0) {
                val choice = choices.getJSONObject(0)
                Log.d("OpenAIService", "First choice: $choice")
                
                val message = choice.getJSONObject("message")
                Log.d("OpenAIService", "Message object: $message")
                
                val content = message.getString("content")
                Log.d("OpenAIService", "Extracted content: '$content'")
                
                AiServiceResult.Success(content)
            } else {
                AiServiceResult.Error("No choices returned from OpenAI")
            }
            
        } catch (e: Exception) {
            Log.e("OpenAIService", "Error calling OpenAI API", e)
            AiServiceResult.Error("Network error calling OpenAI: ${e.message}", e)
        }
    }

    /**
     * Parses scripture response from OpenAI.
     */
    private fun parseScriptureResponse(jsonResponse: String): List<Verse> {
        return try {
            Log.d("OpenAIService", "Raw OpenAI Response: $jsonResponse")
            
            // Check for empty response first
            if (jsonResponse.isBlank()) {
                throw Exception("Received empty response from OpenAI")
            }
            
            // Clean the response using same logic as GeminiAIService
            val cleanedJson = jsonResponse
                .replace("```json", "")
                .replace("```", "")
                .trim()
            
            Log.d("OpenAIService", "Cleaned JSON for parsing: $cleanedJson")
            
            // Check if cleaned JSON is empty
            if (cleanedJson.isBlank()) {
                throw Exception("Response became empty after cleaning")
            }
            
            jsonParser.decodeFromString<List<Verse>>(cleanedJson)
        } catch (e: Exception) {
            Log.e("OpenAIService", "Error parsing scripture response: $jsonResponse", e)
            throw e
        }
    }

    /**
     * Parses verse references from OpenAI response.
     */
    private fun parseVerseReferences(jsonResponse: String): List<BibleVerseRef> {
        return try {
            Log.d("OpenAIService", "Raw OpenAI Response: $jsonResponse")
            
            // Check for empty response first
            if (jsonResponse.isBlank()) {
                throw Exception("Received empty response from OpenAI")
            }
            
            // Clean the response using same logic as GeminiAIService
            val cleanedJson = jsonResponse
                .replace("```json", "")
                .replace("```", "")
                .trim()
            
            Log.d("OpenAIService", "Cleaned JSON for parsing: $cleanedJson")
            
            // Check if cleaned JSON is empty
            if (cleanedJson.isBlank()) {
                throw Exception("Response became empty after cleaning")
            }
            
            jsonParser.decodeFromString<List<BibleVerseRef>>(cleanedJson)
        } catch (e: Exception) {
            Log.e("OpenAIService", "Error parsing verse references: $jsonResponse", e)
            throw e
        }
    }
    
    /**
     * Test method to perform a basic scripture lookup of John 3:16.
     * @return true if successful, false if failed
     */
    suspend fun test(): Boolean {
        return try {
            // Create John 3:16 reference for testing
            val testVerseRef = com.darblee.livingword.data.BibleVerseRef(
                book = "John",
                chapter = 3,
                startVerse = 16,
                endVerse = 16
            )
            val testTranslation = "ESV"
            val testSystemInstruction = "You are a Biblical scholar with deep knowledge of scripture. Your task is to provide accurate Bible verses in the requested translation format."
            val testUserPrompt = """
                Please provide the Bible verse for John 3:16 in the ESV translation.

                Return ONLY a JSON array in the following format:
                [
                    {
                        "verse_num": 16,
                        "verse_string": "verse_text"
                    }
                ]

                Do not include any other text or explanations.
                """.trimIndent()
            
            Log.d("OpenAIService", "Running test: fetching John 3:16 in $testTranslation")
            val result = fetchScripture(testVerseRef, testTranslation, testSystemInstruction, testUserPrompt)
            
            when (result) {
                is AiServiceResult.Success -> {
                    Log.d("OpenAIService", "Test successful: Retrieved ${result.data.size} verse(s)")
                    true
                }
                is AiServiceResult.Error -> {
                    Log.e("OpenAIService", "Test failed: ${result.message}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e("OpenAIService", "Test failed with exception: ${e.message}", e)
            false
        }
    }
}