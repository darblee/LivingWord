package com.darblee.livingword.data.remote

import android.util.Log
import com.darblee.livingword.AISettings // Import AISettings
import com.darblee.livingword.data.BibleVerseRef
import com.darblee.livingword.data.ScriptureContent
import com.darblee.livingword.data.Verse
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.GenerateContentResponse
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.serialization.json.Json

/**
 * Sealed class to represent the result of an AI service call,
 * allowing for structured success or error handling.
 */
sealed class AiServiceResult<out T> {
    data class Success<out T>(val data: T) : AiServiceResult<T>()
    data class Error(val message: String, val cause: Throwable? = null) : AiServiceResult<Nothing>()
}

/**
 * Service object for interacting with the Gemini AI API.
 * Implemented as a singleton.
 * This service needs to be configured using the `configure` method before use.
 */
object GeminiAIService {

    private var generativeModel: GenerativeModel? = null
    private var initializationErrorMessage: String? = null
    private var isConfigured = false
    private var currentAISettings: AISettings? = null

    // JSON parser for handling responses from the Gemini API.
    private val jsonParser = Json { ignoreUnknownKeys = true }


    /**
     * Configures and initializes the Gemini GenerativeModel with the provided settings.
     * This method should be called before using the service, typically on app startup
     * and whenever the AI settings are changed by the user.
     *
     * @param settings The AI settings (model name, API key, temperature).
     */
    fun configure(settings: AISettings) {
        currentAISettings = settings
        isConfigured = true // Mark as configured attempt
        initializationErrorMessage = null // Reset error message
        generativeModel = null // Reset model

        Log.i("GeminiAIService", "Configuring with Model: ${settings.modelName}, Temp: ${settings.temperature}, API Key Present: ${settings.apiKey.isNotBlank()}")

        try {
            if (settings.apiKey.isNotBlank()) {
                generativeModel = GenerativeModel(
                    modelName = settings.modelName,
                    apiKey = settings.apiKey,
                    generationConfig = generationConfig {
                        temperature = settings.temperature
                        // You can add other generationConfig properties here if needed
                        // topK = 1
                        // topP = 0.95f
                        // maxOutputTokens = 8192
                    }
                )
                Log.i("GeminiAIService", "GenerativeModel initialized successfully with new settings.")
            } else {
                val errorMsg = "Gemini API Key is missing. AI Service cannot be initialized."
                Log.w("GeminiAIService", errorMsg)
                initializationErrorMessage = errorMsg
                generativeModel = null // Ensure model is null if API key is missing
            }
        } catch (e: Exception) {
            val errorMsg = "Failed to initialize AI Model with new settings: ${e.message}"
            Log.e("GeminiAIService", "Error initializing GenerativeModel", e)
            initializationErrorMessage = errorMsg
            generativeModel = null // Ensure model is null on error
        }
    }

    /**
     * Checks if the GenerativeModel was configured and initialized successfully.
     * @return True if the model is configured and initialized, false otherwise.
     */
    fun isInitialized(): Boolean = isConfigured && generativeModel != null && initializationErrorMessage == null

    /**
     * Gets the error message if model configuration or initialization failed.
     * @return The initialization error message, or null if successful.
     */
    fun getInitializationError(): String? = initializationErrorMessage

    /**
     * Fetches scripture text for a given Bible verse reference.
     * This function delegates to the appropriate service based on the requested translation.
     * If the ESV service fails, it falls back to using Gemini.
     *
     * @param verseRef The Bible verse reference object.
     * @param translation The desired Bible translation.
     * @return [AiServiceResult.Success] with [ScriptureContent], or [AiServiceResult.Error].
     */
    suspend fun fetchScriptureJson(verseRef: BibleVerseRef, translation: String): AiServiceResult<ScriptureContent> {
        // Route to the dedicated ESV service if translation is ESV
        if (translation.equals("ESV", ignoreCase = true)) {
            Log.i("GeminiAIService", "ESV translation requested. Routing to ESVBibleLookupService.")
            val esvService = ESVBibleLookupService()
            val esvResult = esvService.fetchScripture(verseRef)

            return when (esvResult) {
                is AiServiceResult.Success -> esvResult // Return successful ESV result immediately
                is AiServiceResult.Error -> {
                    // If ESV service fails, log it and fall back to Gemini
                    Log.w("GeminiAIService", "ESV service failed: ${esvResult.message}. Falling back to Gemini.")
                    fetchScriptureWithGemini(verseRef, translation)
                }
            }
        } else {
            // Use Gemini for all other translations
            return fetchScriptureWithGemini(verseRef, translation)
        }
    }

    /**
     * Private helper function to fetch scripture using the Gemini AI service.
     */
    private suspend fun fetchScriptureWithGemini(verseRef: BibleVerseRef, translation: String): AiServiceResult<ScriptureContent> {
        Log.i("GeminiAIService", "$translation translation requested. Using Gemini.")
        if (!isConfigured) {
            return AiServiceResult.Error("GeminiAIService has not been configured.")
        }
        if (generativeModel == null) {
            return AiServiceResult.Error(initializationErrorMessage ?: "Gemini model not initialized or API key missing.")
        }

        val collectedVerses = mutableListOf<Verse>()
        val maxRetries = 3

        return try {
            for (verseNum in verseRef.startVerse..verseRef.endVerse) {
                val singleVerseRef = "${verseRef.book} ${verseRef.chapter}:$verseNum"
                val prompt = "Provide the scripture text for $singleVerseRef from the $translation translation. Respond with only the verse text and nothing else."
                var verseText: String? = null
                var success = false

                for (attempt in 1..maxRetries) {
                    Log.d("GeminiAIService", "Fetching verse: $singleVerseRef (Attempt $attempt/$maxRetries)")
                    try {
                        val response = generativeModel!!.generateContent(prompt)
                        val currentVerseText = response.text?.trim()
                        if (!currentVerseText.isNullOrBlank()) {
                            verseText = currentVerseText
                            success = true
                            break
                        } else {
                            Log.w("GeminiAIService", "Received empty response for $singleVerseRef on attempt $attempt.")
                        }
                    } catch (e: Exception) {
                        Log.e("GeminiAIService", "Exception on attempt $attempt for $singleVerseRef: ${e.message}")
                        if (attempt == maxRetries) throw e
                    }
                }

                if (success) {
                    collectedVerses.add(Verse(verseNum = verseNum, verseString = verseText!!))
                } else {
                    Log.e("GeminiAIService", "Failed to fetch verse $singleVerseRef after $maxRetries attempts.")
                    collectedVerses.add(Verse(verseNum = verseNum, verseString = "[$singleVerseRef failed to load]"))
                }
            }

            if (collectedVerses.isEmpty()) {
                AiServiceResult.Error("Failed to fetch any verses for the specified range.")
            } else {
                val scriptureContent = ScriptureContent(translation = translation, verses = collectedVerses)
                AiServiceResult.Success(scriptureContent)
            }
        } catch (e: Exception) {
            Log.e("GeminiAIService", "Error calling Gemini during verse loop: ${e.message}", e)
            AiServiceResult.Error("Could not get scripture from AI (${e.javaClass.simpleName}).", e)
        }
    }


    /**
     * Fetches a key take-away for the given Bible verse reference from the Gemini API.
     *
     * @param verseRef The Bible verse reference (e.g., "John 3:16-17").
     * @return [AiServiceResult.Success] with the take-away text, or [AiServiceResult.Error] if an issue occurs.
     */
    suspend fun getKeyTakeaway(verseRef: String): AiServiceResult<String> {
        if (!isConfigured) {
            return AiServiceResult.Error("GeminiAIService has not been configured.")
        }
        if (generativeModel == null) {
            return AiServiceResult.Error(initializationErrorMessage ?: "Gemini model not initialized or API key missing.")
        }

        return try {
            val prompt = "Tell me the key take-away for $verseRef"
            Log.d("GeminiAIService", "Sending prompt to Gemini: \"$prompt\"")

            val response: GenerateContentResponse = generativeModel!!.generateContent(prompt)
            val responseText = response.text

            Log.d("GeminiAIService", "Gemini Response: $responseText")

            if (responseText != null) {
                AiServiceResult.Success(responseText)
            } else {
                AiServiceResult.Error("Received empty response from AI.")
            }
        } catch (e: Exception) {
            Log.e("GeminiAIService", "Error calling Gemini API: ${e.message}", e)
            AiServiceResult.Error("Could not get take-away from AI (${e.javaClass.simpleName}).", e)
        }
    }

    /**
     * Fetches a memorization score for the given Bible verse reference from the Gemini API.
     *
     * @param verseRef The Bible verse reference (e.g., "John 3:16-17").
     * @param directQuoteToEvaluate for the Direct Quote text.
     * @return [AiServiceResult.Success] with the take-away text, or [AiServiceResult.Error] if an issue occurs.
     */
    suspend fun getAIScore(verseRef: String, directQuoteToEvaluate: String, contextToEvaluate: String): AiServiceResult<String> {
        if (!isConfigured) {
            return AiServiceResult.Error("GeminiAIService has not been configured.")
        }
        if (generativeModel == null) {
            return AiServiceResult.Error(initializationErrorMessage ?: "Gemini model not initialized or API key missing.")
        }

        return try {
            val prompt = """
            Provide % scores of provided text for Bible verse $verseRef.
            "Direct Quote Score" is based on direct quote accuracy on the following provided direct quote text:

            $directQuoteToEvaluate

            "Direct Quote Explanation" is explanation on Direct Quote Score.

            "Context Score" is based on contextual accuracy on the provided context text:

            $contextToEvaluate

            "Context Explanation" is the explanation on Context Score.

            Respond in the following JSON format:
            {
             "DirectQuoteScore" : integer between 0 to 100,
             "ContextScore" : integer between 0 to 100,
             "DirectQuoteExplanation": "This is sample text"
             "ContextExplanation": "This is sample text"
            }
            """
            Log.d("GeminiAIService", "Sending memorized score prompt to Gemini: \"$prompt\"")

            val response: GenerateContentResponse = generativeModel!!.generateContent(prompt)
            val responseText = (response.text)?.trimIndent()

            Log.d("GeminiAIService", "Gemini Response: $responseText")

            if (responseText != null) {
                AiServiceResult.Success(responseText)
            } else {
                AiServiceResult.Error("Received empty response from AI.")
            }
        } catch (e: Exception) {
            Log.e("GeminiAIService", "Error calling Gemini API: ${e.message}", e)
            AiServiceResult.Error("Could not get AI score from AI (${e.javaClass.simpleName}).", e)
        }
    }

    /**
     * Fetches a list of Bible verse references based on a textual description.
     *
     * @param description A natural language description of the verses to find.
     * @return [AiServiceResult.Success] with a list of [BibleVerseRef] objects,
     * or [AiServiceResult.Error] if an issue occurs.
     */
    suspend fun getNewVersesBasedOnDescription(description: String): AiServiceResult<List<BibleVerseRef>> {
        if (!isConfigured) {
            return AiServiceResult.Error("GeminiAIService has not been configured.")
        }
        if (generativeModel == null) {
            return AiServiceResult.Error(initializationErrorMessage ?: "Gemini model not initialized or API key missing.")
        }

        return try {
            val prompt = """
            Please get me a list of verses that meet this description: "$description"

            Respond in the following JSON format, which is an array of verse reference objects.
            If no verses are found, return an empty array [].
            [
              {
                "book": "BookName",
                "chapter": 1,
                "startVerse": 1,
                "endVerse": 2
              }
            ]
            """.trimIndent()

            Log.d("GeminiAIService", "Sending prompt to Gemini for description-based verses: \"$prompt\"")

            val response = generativeModel!!.generateContent(prompt)
            val responseText = response.text

            Log.d("GeminiAIService", "Gemini Response: $responseText")

            if (responseText != null) {
                // The model might wrap the JSON in markdown backticks, so we clean it.
                val cleanedJson = responseText.replace("```json", "").replace("```", "").trim()
                val verses = jsonParser.decodeFromString<List<BibleVerseRef>>(cleanedJson)
                AiServiceResult.Success(verses)
            } else {
                AiServiceResult.Error("Received empty response from AI.")
            }
        } catch (e: Exception) {
            Log.e("GeminiAIService", "Error calling Gemini API or parsing verse response: ${e.message}", e)
            AiServiceResult.Error("Could not get verses from AI (${e.javaClass.simpleName}).", e)
        }
    }
}