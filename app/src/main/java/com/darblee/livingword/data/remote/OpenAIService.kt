package com.darblee.livingword.data.remote

import android.util.Log
import com.darblee.livingword.AISettings
import com.darblee.livingword.data.BibleVerseRef
import com.darblee.livingword.data.Verse
import com.darblee.livingword.ui.viewmodels.ScoreData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
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
    suspend fun fetchScripture(verseRef: BibleVerseRef, translation: String): AiServiceResult<List<Verse>> {
        return withContext(Dispatchers.IO) {
            try {
                if (!isInitialized()) {
                    return@withContext AiServiceResult.Error("OpenAI service not initialized: ${getInitializationError()}")
                }

                Log.d("OpenAIService", "Fetching scripture for $verseRef in $translation")

                val prompt = """
                Please provide the Bible verses for ${verseRef.book} ${verseRef.chapter}:${verseRef.startVerse}-${verseRef.endVerse} in the $translation translation.

                Return ONLY a JSON array in the following format:
                [
                    {
                        "verse_num": verse_number,
                        "verse_string": "verse_text"
                    }
                ]

                Do not include any other text or explanations.
                """.trimIndent()

                val systemInstruction = "You are a Biblical scholar with deep knowledge of scripture. Your task is to provide accurate Bible verses in the requested translation format."
                val response = callOpenAI(prompt, maxTokens = 500, systemInstruction = systemInstruction)
                
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
    suspend fun getKeyTakeaway(verseRef: String): AiServiceResult<String> {
        return withContext(Dispatchers.IO) {
            try {
                if (!isInitialized()) {
                    return@withContext AiServiceResult.Error("OpenAI service not initialized: ${getInitializationError()}")
                }

                Log.d("OpenAIService", "Getting key takeaway for $verseRef")

                val prompt = """
                Please provide a key takeaway or main message from the Bible verse $verseRef.
                
                Provide a concise, meaningful explanation of the verse's core message or teaching.
                Keep the response to 2-3 sentences maximum.
                Focus on practical application and spiritual significance.
                """.trimIndent()

                val systemInstruction = "You are a scripture expert, skilled at extracting key takeaways from religious texts. Your task is to analyze a given verse reference and provide the key takeaway."
                val response = callOpenAI(prompt, maxTokens = 300, systemInstruction = systemInstruction)
                
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
    suspend fun getAIScore(verseRef: String, directQuoteToEvaluate: String, userApplicationComment: String): AiServiceResult<ScoreData> {
        return withContext(Dispatchers.IO) {
            try {
                if (!isInitialized()) {
                    return@withContext AiServiceResult.Error("OpenAI service not initialized: ${getInitializationError()}")
                }

                Log.d("OpenAIService", "Getting AI score for $verseRef")

                val prompt = """
                You will be provided with a Bible verse reference and a direct quote to evaluate.

                Bible verse reference: $verseRef
                Direct quote to evaluate: $directQuoteToEvaluate
                
                Follow these steps:
                
                1. Calculate the `ContextScore`: Evaluate the contextual accuracy of the direct quote. Consider whether the quote is used in a way that aligns with the original meaning and intent of the verse. Provide a score between 0 and 100.
                2. Provide `ContextExplanation`: Explain how you derived the `ContextScore`. Discuss the context of the verse and whether the provided quote aligns with that context.
                
                Respond ONLY in the following JSON format:
                
                {
                "DirectQuoteScore": 0,
                "ContextScore": integer between 0 to 100,
                "DirectQuoteExplanation": "",
                "ContextExplanation": "Explanation of the ContextScore",
                "ApplicationFeedback": ""
                }
                
                Note: DirectQuoteScore and DirectQuoteExplanation are hardcoded values and not used in evaluation.
                ApplicationFeedback will be populated separately.
                
                Ensure that your response is ONLY in JSON format with no other text or explanations outside of the JSON structure.
                """.trimIndent()

                Log.d("OpenAIService", "Sending optimized prompt to OpenAI (DirectQuote fields removed for token reduction)")

                val systemInstruction = "You are an expert in theology. You are an expert in analyzing Bible verses and determining the accuracy of direct quotes and their context."
                val response = callOpenAI(prompt, maxTokens = 800, systemInstruction = systemInstruction)
                
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
                            
                            // Ensure hardcoded values are set correctly to reduce token usage
                            parseResponse.DirectQuoteScore = 0
                            parseResponse.DirectQuoteExplanation = ""
                            
                            // Get application feedback separately
                            val applicationFeedback = getApplicationFeedback(verseRef, userApplicationComment)
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
    suspend fun getApplicationFeedback(verseRef: String, userApplicationComment: String): String {
        return withContext(Dispatchers.IO) {
            try {
                if (!isInitialized()) {
                    Log.w("OpenAIService", "Service not initialized, returning empty feedback")
                    return@withContext ""
                }

                Log.d("OpenAIService", "Getting application feedback for $verseRef")

                val prompt = """
                Please provide feedback on how a user is applying the Bible verse $verseRef to their life.
                
                User's application: "$userApplicationComment"
                
                Provide constructive feedback that is:
                1. Insightful: Offer a deeper understanding of the verse and its implications.
                2. Encouraging: Affirm the user's efforts and provide motivation.
                
                """.trimIndent()

                val systemInstruction = "You are a knowledgeable theologian and biblical scholar, skilled in providing constructive feedback on the application of Bible verses in daily life. Your feedback should be insightful, encouraging, and theologically sound."
                val response = callOpenAI(prompt, maxTokens = 300, systemInstruction = systemInstruction)
                
                when (response) {
                    is AiServiceResult.Success -> response.data.trim()
                    is AiServiceResult.Error -> {
                        Log.w("OpenAIService", "Failed to get application feedback: ${response.message}")
                        ""
                    }
                }
            } catch (e: Exception) {
                Log.e("OpenAIService", "Error getting application feedback", e)
                ""
            }
        }
    }

    /**
     * Validates a key takeaway response.
     */
    suspend fun validateKeyTakeawayResponse(verseRef: String, takeawayToEvaluate: String): AiServiceResult<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                if (!isInitialized()) {
                    return@withContext AiServiceResult.Error("OpenAI service not initialized: ${getInitializationError()}")
                }

                Log.d("OpenAIService", "Validating takeaway for $verseRef")

                val prompt = """
                Please evaluate whether the following takeaway accurately represents the Bible verse $verseRef:
                
                Takeaway to evaluate: "$takeawayToEvaluate"
                
                Respond with only "true" if the takeaway is accurate and appropriate, or "false" if it misrepresents the verse.
                """.trimIndent()

                val systemInstruction = "You are a theological text evaluator. You will be given a verse reference and a take-away text. Your task is to evaluate the take-away text in the context of the verse and respond accurately."
                val response = callOpenAI(prompt, maxTokens = 50, systemInstruction = systemInstruction)
                
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
    suspend fun getNewVersesBasedOnDescription(description: String): AiServiceResult<List<BibleVerseRef>> {
        return withContext(Dispatchers.IO) {
            try {
                if (!isInitialized()) {
                    return@withContext AiServiceResult.Error("OpenAI service not initialized: ${getInitializationError()}")
                }

                Log.d("OpenAIService", "Finding verses for description: $description")

                val prompt = """
                Please suggest Bible verses that relate to the following description or topic: "$description"
                
                Return ONLY a JSON array of verse references in this exact format:
                [
                    {"book": "BookName", "chapter": number, "startVerse": number, "endVerse": number}
                ]
                
                Suggest 3-5 relevant verses. Do not include any other text or explanations.
                """.trimIndent()

                val systemInstruction = "You are a Biblical scholar with comprehensive knowledge of scripture. Your task is to suggest relevant Bible verses based on topics or descriptions provided."
                val response = callOpenAI(prompt, maxTokens = 400, systemInstruction = systemInstruction)
                
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
    private suspend fun callOpenAI(prompt: String, maxTokens: Int = 500, systemInstruction: String? = null): AiServiceResult<String> {
        return try {
            val url = URL("https://api.openai.com/v1/chat/completions")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $openAiApiKey")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            
            val jsonBody = JSONObject().apply {
                put("model", "gpt-3.5-turbo")
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
                put("max_tokens", maxTokens)
                put("temperature", 0.7)
            }
            
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
            
            // Parse the OpenAI response to extract the content
            val jsonResponse = JSONObject(responseText)
            val choices = jsonResponse.getJSONArray("choices")
            if (choices.length() > 0) {
                val message = choices.getJSONObject(0).getJSONObject("message")
                val content = message.getString("content")
                AiServiceResult.Success(content)
            } else {
                AiServiceResult.Error("No response from OpenAI")
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
            // Clean the response first
            val cleanedJson = jsonResponse.trim()
                .removePrefix("```json")
                .removeSuffix("```")
                .trim()
            
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
            // Clean the response first
            val cleanedJson = jsonResponse.trim()
                .removePrefix("```json")
                .removeSuffix("```")
                .trim()
            
            jsonParser.decodeFromString<List<BibleVerseRef>>(cleanedJson)
        } catch (e: Exception) {
            Log.e("OpenAIService", "Error parsing verse references: $jsonResponse", e)
            throw e
        }
    }
}