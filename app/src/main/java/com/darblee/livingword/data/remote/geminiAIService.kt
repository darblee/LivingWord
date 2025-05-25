package com.darblee.livingword.data.remote // You can choose an appropriate package name

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.GenerateContentResponse
import com.google.ai.client.generativeai.type.generationConfig
import com.darblee.livingword.BuildConfig

/**
 * Sealed class to represent the result of an AI service call,
 * allowing for structured success or error handling.
 */
sealed class AiServiceResult<out T> {
    data class Success<out T>(val data: T) : AiServiceResult<T>()
    data class Error(val message: String, val cause: Throwable? = null) : AiServiceResult<Nothing>()
}

/**
 * Service class for interacting with the Gemini AI API.
 *
 */
class GeminiAIService() {

    val apiKey = BuildConfig.GEMINI_API_KEY

    private var generativeModel: GenerativeModel? = null
    private var initializationErrorMessage: String? = null

    init {
        initializeGeminiModel()
    }

    /**
     * Initializes the Gemini GenerativeModel.
     * Sets [initializationErrorMessage] if an error occurs.
     */
    private fun initializeGeminiModel() {
        try {
            if (apiKey.isNotBlank()) {
                generativeModel = GenerativeModel(
                    modelName = "gemini-1.5-flash", // Or your desired model
                    apiKey = apiKey,
                    generationConfig = generationConfig {
                        temperature = 0.7f // Adjust creativity as needed
                    }
                )
                Log.i("GeminiAIService", "GenerativeModel initialized successfully.")
            } else {
                val errorMsg = "Gemini API Key is missing. Cannot get take-away."
                Log.w("GeminiAIService", errorMsg)
                initializationErrorMessage = errorMsg
            }
        } catch (e: Exception) {
            val errorMsg = "Failed to initialize AI Model: ${e.message}"
            Log.e("GeminiAIService", "Error initializing GenerativeModel", e)
            initializationErrorMessage = errorMsg
        }
    }

    /**
     * Checks if the GenerativeModel was initialized successfully.
     * @return True if the model is initialized, false otherwise.
     */
    fun isInitialized(): Boolean = generativeModel != null

    /**
     * Gets the error message if model initialization failed.
     * @return The initialization error message, or null if initialization was successful.
     */
    fun getInitializationError(): String? = initializationErrorMessage

    /**
     * Fetches a key take-away for the given Bible verse reference from the Gemini API.
     *
     * @param verseRef The Bible verse reference (e.g., "John 3:16-17").
     * @return [AiServiceResult.Success] with the take-away text, or [AiServiceResult.Error] if an issue occurs.
     */
    suspend fun getKeyTakeaway(verseRef: String): AiServiceResult<String> {
        if (generativeModel == null) {
            return AiServiceResult.Error(initializationErrorMessage ?: "Gemini model not initialized.")
        }

        return try {
            val prompt = "Tell me the key take-away for $verseRef"
            Log.d("GeminiAIService", "Sending prompt to Gemini: \"$prompt\"")

            // Call the Gemini API
            val response: GenerateContentResponse = generativeModel!!.generateContent(prompt) // Safe call due to the check above
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
     * @param memorizedText for the memorized text.
     * @return [AiServiceResult.Success] with the take-away text, or [AiServiceResult.Error] if an issue occurs.
     */
    suspend fun getMemorizedScore(verseRef: String, memorizedText: String): AiServiceResult<String> {
        if (generativeModel == null) {
            return AiServiceResult.Error(initializationErrorMessage ?: "Gemini model not initialized.")
        }

        return try {
            val prompt: String = """
            Provide % score of memorized text for Bible verse $verseRef. Score is based on contextual accuracy and less on direct quote.
            Respond in the following format:
            {
            "Score" : integer between 0 to 100,
            "Explanation": "This is sample text"
            }
            Here is the memorized text:
            $memorizedText
            """
            Log.d("GeminiAIService", "Sending memorized score prompt to Gemini: \"$prompt\"")

            // Call the Gemini API
            val response: GenerateContentResponse = generativeModel!!.generateContent(prompt) // Safe call due to the check above
            val responseText = (response.text)?.trimIndent()

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
}