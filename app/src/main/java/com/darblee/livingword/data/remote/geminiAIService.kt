package com.darblee.livingword.data.remote

import android.util.Log
import com.darblee.livingword.AISettings // Import AISettings
import com.darblee.livingword.data.BibleVerseRef
import com.darblee.livingword.data.Verse
import com.darblee.livingword.ui.viewmodels.ScoreData
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.GenerateContentResponse
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.SafetySetting
import com.google.ai.client.generativeai.type.generationConfig
import com.google.ai.client.generativeai.type.content
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable // Add this import at the top of the file


/**
 * Sealed class to represent the result of an AI service call,
 * allowing for structured success or error handling.
 */
sealed class AiServiceResult<out T> {
    data class Success<out T>(val data: T) : AiServiceResult<T>()
    data class Error(val message: String, val cause: Throwable? = null) : AiServiceResult<Nothing>()
}

// 1. Define a data class that matches the expected JSON structure.
@Serializable
data class KeyTakeawayResponse(
    val key_takeaway_text: String,
    val sources: List<String>
)

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
    private val jsonParser = Json { 
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

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
                // Define the system instruction
                val instruction = content(role = "system") {
                    text ("You are an expert in theology. Respond in English.".trimIndent())
                }

                generativeModel = GenerativeModel(
                    modelName = settings.modelName,
                    apiKey = settings.apiKey,
                    generationConfig = generationConfig {
                        temperature = settings.temperature
                        responseMimeType = "application/json"
                        // You can add other generationConfig properties here if needed
                        // topK = 1
                        // topP = 0.95f
                        // maxOutputTokens = 8192
                    },
                    systemInstruction = instruction
                )
                Log.i("GeminiAIService", "GenerativeModel initialized successfully with new settings and system instruction.")
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
     * Fetches scripture text using Gemini AI for any translation.
     * ESV logic has been moved to the top-level AIService for cleaner architecture.
     *
     * @param verseRef The Bible verse reference object.
     * @param translation The desired Bible translation.
     * @param systemInstruction The system instruction for the AI model.
     * @param userPrompt The user prompt for the AI model.
     * @return [AiServiceResult.Success] with [List<Verse>], or [AiServiceResult.Error].
     */
    suspend fun fetchScripture(
        verseRef: BibleVerseRef, 
        translation: String,
        systemInstruction: String,
        userPrompt: String = ""
    ): AiServiceResult<List<Verse>> {
        Log.d("GeminiAIService", "Fetching scripture for $verseRef in $translation")

        if (!isConfigured) {
            return AiServiceResult.Error("GeminiAIService has not been configured.")
        }
        if (generativeModel == null) {
            return AiServiceResult.Error(initializationErrorMessage ?: "Gemini model not initialized or API key missing.")
        }

        // Use external system instruction
        val systemPrompt = content(role = "system") {
            text(systemInstruction)
        }

        val retrieveScriptureModel = GenerativeModel(
            modelName = currentAISettings!!.modelName,
            apiKey = currentAISettings!!.apiKey,
            generationConfig = generationConfig {
                temperature = 0.1f // Lower temperature for more deterministic evaluation
                responseMimeType = "application/json"
            },
            systemInstruction = systemPrompt
        )

        return try { 
            Log.d("GeminiAIService", "Sending prompt to Gemini for scripture range using centralized prompts")

            val response = retrieveScriptureModel.generateContent(userPrompt)

            val responseText = response.text

            Log.d("GeminiAIService", "Gemini Response: $responseText")

            if (responseText != null) {
                // The model might wrap the JSON in markdown backticks, so we clean it.
                val cleanedJson = responseText.replace("```json", "").replace("```", "").trim()
                val verses = jsonParser.decodeFromString<List<Verse>>(cleanedJson)
                AiServiceResult.Success(verses)
            } else {
                AiServiceResult.Error("Received empty response from AI for verse range.")
            }
        } catch (e: Exception) {
            Log.e("GeminiAIService", "Error calling Gemini or parsing scripture response: ${e.message}", e)
            AiServiceResult.Error("Could not get scripture from AI (${e.javaClass.simpleName}).", e)
        }
    }


    /**
     * Fetches a key take-away for the given Bible verse reference from the Gemini API.
     *
     * @param verseRef The Bible verse reference (e.g., "John 3:16-17").
     * @param systemInstruction The system instruction for the AI model.
     * @param userPrompt The user prompt for the AI model.
     * @return [AiServiceResult.Success] with the take-away text, or [AiServiceResult.Error] if an issue occurs.
     */
    suspend fun getKeyTakeaway(
        verseRef: String,
        systemInstruction: String,
        userPrompt: String = ""
    ): AiServiceResult<String> {
        if (!isConfigured) {
            return AiServiceResult.Error("GeminiAIService has not been configured.")
        }
        if (generativeModel == null) {
            return AiServiceResult.Error(initializationErrorMessage ?: "Gemini model not initialized or API key missing.")
        }

        return try {
            // Use external system instruction
            Log.d("GeminiAIService", "Getting key takeaway for $verseRef using centralized prompts")

            val takeAwaySystemPrompt = content (role = "system") {
                text(systemInstruction)
            }

            val harassmentSafety = SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.LOW_AND_ABOVE)
            val hateSpeechSafety = SafetySetting(HarmCategory.HATE_SPEECH, BlockThreshold.LOW_AND_ABOVE)
            val explicitSexSafety = SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.LOW_AND_ABOVE)
            val dangerSafety = SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.LOW_AND_ABOVE)

            val takeAwayModel = GenerativeModel(
                modelName = currentAISettings!!.modelName,
                apiKey = currentAISettings!!.apiKey,
                generationConfig = generationConfig {
                    temperature = 0.5f // Higher  temperature to provide more variation on take-away response
                },
                safetySettings = listOf(harassmentSafety, hateSpeechSafety, explicitSexSafety, dangerSafety),
                systemInstruction = takeAwaySystemPrompt
            )

            Log.d("GeminiAIService", "Sending prompt to Gemini using centralized prompts")

            var takeAwayResponseText: String? = ""
            var attempts = 0
            while (attempts < 3) {
                val response: GenerateContentResponse = takeAwayModel.generateContent(userPrompt)
                takeAwayResponseText = response.text
                if (takeAwayResponseText != null) {
                    break
                }
                attempts++
            }

            Log.d("GeminiAIService", "Gemini Response: $takeAwayResponseText")

            if (takeAwayResponseText != null) {
                val cleanedJson = takeAwayResponseText.replace("```json", "").replace("```", "").trim()
                val parsedResponse = jsonParser.decodeFromString<KeyTakeawayResponse>(cleanedJson)

                // 3. Return the human-readable string from the parsed object
                AiServiceResult.Success(parsedResponse.key_takeaway_text)
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
     * @return [AiServiceResult.Success] with the take-away text, or [AiServiceResult.Error] if an issue occurs.
     */
    suspend fun getAIScore(
        verseRef: String,
        userApplicationComment: String,
        systemInstruction: String,
        userPrompt: String,
        applicationFeedbackPrompt: String
    ): AiServiceResult<ScoreData> {
        if (!isConfigured) {
            return AiServiceResult.Error("GeminiAIService has not been configured.")
        }
        if (generativeModel == null) {
            return AiServiceResult.Error(initializationErrorMessage ?: "Gemini model not initialized or API key missing.")
        }

        return try {
            // Use external system instruction
            val scoreSystemPrompt = content(role = "system") {
                text(systemInstruction)
            }

            // It's good practice to create a specific model for this specific task
            // This ensures the system prompts do not conflict
            val scoreModel = GenerativeModel(
                modelName = currentAISettings!!.modelName,
                apiKey = currentAISettings!!.apiKey,
                generationConfig = generationConfig {
                    temperature = 0.5f // Middle temperature for more deterministic evaluation
                },
                systemInstruction = scoreSystemPrompt
            )

            Log.d("GeminiAIService", "Sending optimized prompt to Gemini (DirectQuote fields removed for token reduction): \"$userPrompt\"")

            val response: GenerateContentResponse = scoreModel.generateContent(userPrompt)
            val responseText = (response.text)?.trimIndent()

            Log.d("GeminiAIService", "Gemini Response: $responseText")

            var applicationFeedback: String? = null
            var attempts = 0
            while (attempts < 3) {
                val feedbackSystemInstruction = "You are an expert in theology. You provide insightful and encouraging feedback on how users apply Bible verses to their lives."
                val applicationFeedbackResponseText =
                    getApplicationFeedback(verseRef, feedbackSystemInstruction, applicationFeedbackPrompt)
                applicationFeedback = applicationFeedbackResponseText.trimIndent()
                if (applicationFeedback.isNotEmpty()) {
                    break
                }
                attempts++
            }

            if (responseText != null) {
                // More aggressive JSON cleaning - remove various markdown artifacts
                var cleanedJson = responseText
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
                
                Log.d("GeminiAIService", "Cleaned JSON for parsing: $cleanedJson")
                
                try {
                    val parseResponse = jsonParser.decodeFromString<ScoreData>(cleanedJson)
                    
                    // Ensure hardcoded values are set correctly to reduce token usage
                    parseResponse.DirectQuoteScore = 0
                    parseResponse.DirectQuoteExplanation = ""
                    
                    if (applicationFeedback != null) {
                        parseResponse.ApplicationFeedback = applicationFeedback
                    }

                    AiServiceResult.Success(parseResponse)
                } catch (serializationException: Exception) {
                    Log.e("GeminiAIService", "Failed to parse AI response JSON: $cleanedJson", serializationException)
                    Log.e("GeminiAIService", "Original AI response: $responseText")
                    AiServiceResult.Error("AI returned invalid JSON format. Please try again.", serializationException)
                }
            } else {
                AiServiceResult.Error("Received empty response from AI.")
            }
        } catch (e: Exception) {
            Log.e("GeminiAIService", "Error calling Gemini API: ${e.message}", e)
            AiServiceResult.Error("Could not get AI score from AI (${e.javaClass.simpleName}).", e)
        }
    }


    suspend fun getApplicationFeedback(
        verseRef: String, 
        systemInstruction: String,
        userPrompt: String = ""
    ): String {
        if (!isConfigured) {
            return ""
        }
        if (generativeModel == null) {
            return ""
        }

        return try {

            Log.d("GeminiAIService", "Getting application feedback for $verseRef")

            // Use external system instruction
            val scoreSystemPrompt = content(role = "system") {
                text(systemInstruction)
            }

            // It's good practice to create a specific model for this specific task
            // This ensures the system prompts do not conflict
            val scoreModel = GenerativeModel(
                modelName = currentAISettings!!.modelName,
                apiKey = currentAISettings!!.apiKey,
                generationConfig = generationConfig {
                    temperature = 0.6f // Lower temperature for more deterministic evaluation
                },
                systemInstruction = scoreSystemPrompt
            )

            Log.d("GeminiAIService", "Sending user application prompt to Gemini: \"$userPrompt\"")

            var applicationFeedback: String? = null
            var attempts = 0
            while (attempts < 3) {
                val response: GenerateContentResponse = scoreModel.generateContent(userPrompt)
                applicationFeedback = (response.text)?.trimIndent()
                if (!applicationFeedback.isNullOrEmpty()) {
                    break
                }
                attempts++
            }

            Log.d("GeminiAIService", "Gemini Response: $applicationFeedback")

            applicationFeedback ?: ""
        } catch (e: Exception) {
            Log.e("GeminiAIService", "Error getting application feedback when calling Gemini API: ${e.message}", e)
            ""
        }
    }

    /**
     * Evaluates a given take-away text for a specific verse to determine if it is acceptable.
     * This function uses a separate Gemini model configured with an evaluator system prompt.
     *
     * @param systemInstruction The system instruction for the AI model.
     * @param userPrompt The user prompt for the AI model.
     * @return [AiServiceResult.Success] with a Boolean indicating if the takeaway is acceptable,
     * or [AiServiceResult.Error] if an issue occurs.
     */
    suspend fun validateKeyTakeawayResponse(
        systemInstruction: String,
        userPrompt: String = ""
    ): AiServiceResult<Boolean> {
        if (!isConfigured || currentAISettings == null) {
            return AiServiceResult.Error("GeminiAIService has not been configured.")
        }
        if (generativeModel == null) {
            return AiServiceResult.Error(initializationErrorMessage ?: "Gemini model not initialized or API key missing.")
        }

        try {
            // Use external system instruction
            val evaluatorSystemPrompt = content(role = "system") {
                text(systemInstruction)
            }

            // It's good practice to create a specific model for this specific task
            // This ensures the system prompts do not conflict
            val evaluatorModel = GenerativeModel(
                modelName = currentAISettings!!.modelName,
                apiKey = currentAISettings!!.apiKey,
                generationConfig = generationConfig {
                    temperature = 0.2f // Lower temperature for more deterministic evaluation
                },
                systemInstruction = evaluatorSystemPrompt
            )

            Log.d("GeminiAIService", "Sending evaluation prompt to Gemini using centralized prompts")

            val response = evaluatorModel.generateContent(userPrompt)
            val responseText = response.text

            Log.d("GeminiAIService", "Gemini Evaluation Response: $responseText")

            if (responseText != null) {
                // Parse simple "true"/"false" response to match centralized prompt format
                val result = responseText.trim().lowercase() == "true"
                return AiServiceResult.Success(result)
            } else {
                return AiServiceResult.Error("Received empty response from evaluator AI.")
            }

        } catch (e: Exception) {
            Log.e("GeminiAIService", "Error during take-away validation: ${e.message}", e)
            return AiServiceResult.Error("Could not validate take-away from AI (${e.javaClass.simpleName}).", e)
        }
    }

    /**
     * Fetches a list of Bible verse references based on a textual description.
     *
     * @param description A natural language description of the verses to find.
     * @param systemInstruction The system instruction for the AI model.
     * @param userPrompt The user prompt for the AI model.
     * @return [AiServiceResult.Success] with a list of [BibleVerseRef] objects,
     * or [AiServiceResult.Error] if an issue occurs.
     */
    suspend fun getNewVersesBasedOnDescription(
        description: String,
        systemInstruction: String,
        userPrompt: String = ""
    ): AiServiceResult<List<BibleVerseRef>> {
        if (!isConfigured) {
            return AiServiceResult.Error("GeminiAIService has not been configured.")
        }

        return try {

            Log.d("GeminiAIService", "Finding verses for description: $description using centralized prompts")

            // Use external system instruction
            val systemPrompt = content(role = "system") {
                text(systemInstruction)
            }

            val getCorrespondingVersesGenerativeModel = GenerativeModel(
                modelName =   currentAISettings!!.modelName,
                apiKey = currentAISettings!!.apiKey,
                generationConfig = generationConfig {
                    temperature = 0.3f // Lower temperature for more deterministic evaluation
                },
                systemInstruction = systemPrompt
            )

            Log.d("GeminiAIService", "Sending prompt to Gemini for description-based verses using centralized prompts")

            val response = getCorrespondingVersesGenerativeModel.generateContent(userPrompt)
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
            
            Log.d("GeminiAIService", "Running test: fetching John 3:16 in $testTranslation")
            val result = fetchScripture(testVerseRef, testTranslation, testSystemInstruction, testUserPrompt)
            
            when (result) {
                is AiServiceResult.Success -> {
                    Log.d("GeminiAIService", "Test successful: Retrieved ${result.data.size} verse(s)")
                    true
                }
                is AiServiceResult.Error -> {
                    Log.e("GeminiAIService", "Test failed: ${result.message}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e("GeminiAIService", "Test failed with exception: ${e.message}", e)
            false
        }
    }
    
    /**
     * Gets the initialization error message, if any.
     */
    fun getInitializationError(): String? = initializationErrorMessage
}