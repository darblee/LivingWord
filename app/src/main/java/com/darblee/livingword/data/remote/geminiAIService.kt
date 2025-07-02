package com.darblee.livingword.data.remote

import android.util.Log
import com.darblee.livingword.AISettings // Import AISettings
import com.darblee.livingword.data.BibleVerseRef
import com.darblee.livingword.data.Verse
import com.darblee.livingword.domain.model.ScoreData
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.GenerateContentResponse
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.SafetySetting
import com.google.ai.client.generativeai.type.generationConfig
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.Tool
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

@Serializable
data class EvaluationResponse(
    val is_acceptable: Boolean,
    val feedback: String
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
                // Define the system instruction
                val instruction = content(role = "system") {
                    text ("You are an expert in theology. Respond in English. Your answers must align closely with core Judeo-Christian values.".trimIndent())
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
     * @return [AiServiceResult.Success] with [List<Verse>], or [AiServiceResult.Error].
     */
    suspend fun fetchScripture(verseRef: BibleVerseRef, translation: String): AiServiceResult<List<Verse>> {
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
    private suspend fun fetchScriptureWithGemini(verseRef: BibleVerseRef, translation: String): AiServiceResult<List<Verse>> {
        Log.i("GeminiAIService", "$translation translation requested. Using Gemini.")
        if (!isConfigured) {
            return AiServiceResult.Error("GeminiAIService has not been configured.")
        }
        if (generativeModel == null) {
            return AiServiceResult.Error(initializationErrorMessage ?: "Gemini model not initialized or API key missing.")
        }

        val systemPrompt = content(role = "system") {
            text("You are an AI assistant that retrieves specific biblical scripture verse(s)")
        }

        val retrieveScriptureModel = GenerativeModel(
            modelName = currentAISettings!!.modelName,
            apiKey = currentAISettings!!.apiKey,
            generationConfig = generationConfig {
                temperature = 0.2f // Lower temperature for more deterministic evaluation
                responseMimeType = "application/json"
            },
            systemInstruction = systemPrompt
        )

        return try {
            val verseString = if (verseRef.startVerse == verseRef.endVerse) {
                "${verseRef.book} ${verseRef.chapter}:${verseRef.startVerse}"
            } else {
                "${verseRef.book} ${verseRef.chapter}:${verseRef.startVerse}-${verseRef.endVerse}"
            }

            val prompt = if (verseRef.startVerse == verseRef.endVerse) {
                """
                Provide the scripture for $verseString from the $translation translation.
                Respond in the following JSON schema:
                [
                  {
                    "verse_num": ${verseRef.startVerse},
                    "verse_string": "String content of the verse."
                  }
                ]
                """.trimIndent()
            } else {
                """
                Retrieve the scripture for the range of verses $verseString from the $translation translation.
                Your sole function is to return the content of those verses in the following JSON schema, which is an array of verse objects:
                [
                  {
                    "verse_num": ${verseRef.startVerse},
                    "verse_string": "The content of the start verse."
                  },
                  {
                    "verse_num": ${verseRef.startVerse + 1},
                    "verse_string": "The content of the next verse."
                  }
                ]
                """.trimIndent()
            }

            Log.d("GeminiAIService", "Sending prompt to Gemini for scripture range: \"$prompt\"")

            val response = retrieveScriptureModel!!.generateContent(prompt)

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
            val takeAwaySystemPrompt = content (role = "system") {
                text("You are highly respected minister and Bible Scholar, who loves Jesus deeply and desire to guide other to have deep relationship with God and Jesus. You desire to guide everyone to follow Jesus teaching.  You will align to core Judeo-Christian values")
            }

            val harassmentSafety = SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.LOW_AND_ABOVE)
            val hateSpeechSafety = SafetySetting(HarmCategory.HATE_SPEECH, BlockThreshold.LOW_AND_ABOVE)
            val explicitSexSafety = SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.LOW_AND_ABOVE)
            val dangerSafety = SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.LOW_AND_ABOVE)


            // TODO: In the future. we will add GoogleSearch tool support which will reduce AI hallucination
            val groundingTool = Tool(
                functionDeclarations = listOf()
            )

            val takeAwayModel = GenerativeModel(
                modelName = currentAISettings!!.modelName,
                apiKey = currentAISettings!!.apiKey,
                generationConfig = generationConfig {
                    temperature = 0.2f // Lower temperature for more deterministic evaluation
                },
                safetySettings = listOf(harassmentSafety, hateSpeechSafety, explicitSexSafety, dangerSafety),
                systemInstruction = takeAwaySystemPrompt
            )

            val prompt = """
        Tell me the key take-away for $verseRef.
        Respond in the following JSON format:
        {
          "key_takeaway_text": "The key takeaway."
          "sources": [ "source1", "source2" ]
        }
        """.trimIndent()
            Log.d("GeminiAIService", "Sending prompt to Gemini: \"$prompt\"")

            val response: GenerateContentResponse = takeAwayModel.generateContent(prompt)
            val responseText = response.text

            Log.d("GeminiAIService", "Gemini Response: $responseText")

            if (responseText != null) {
                val cleanedJson = responseText.replace("```json", "").replace("```", "").trim()
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
     * @param directQuoteToEvaluate for the Direct Quote text.
     * @return [AiServiceResult.Success] with the take-away text, or [AiServiceResult.Error] if an issue occurs.
     */
    suspend fun getAIScore(verseRef: String, directQuoteToEvaluate: String, contextToEvaluate: String): AiServiceResult<ScoreData> {
        if (!isConfigured) {
            return AiServiceResult.Error("GeminiAIService has not been configured.")
        }
        if (generativeModel == null) {
            return AiServiceResult.Error(initializationErrorMessage ?: "Gemini model not initialized or API key missing.")
        }

        return try {
            // System prompt for the evaluator model
            val scoreSystemPrompt = content(role = "system") {
                text("You are an Bible scholar that need to do an assessment based on core Judeo-Christian values and theological accuracy for the specific verse provided.")
            }

            // It's good practice to create a specific model for this specific task
            // This ensures the system prompts do not conflict
            val scoreModel = GenerativeModel(
                modelName = currentAISettings!!.modelName,
                apiKey = currentAISettings!!.apiKey,
                generationConfig = generationConfig {
                    temperature = 0.2f // Lower temperature for more deterministic evaluation
                },
                systemInstruction = scoreSystemPrompt
            )

            val prompt = """
            Bible verse is $verseRef.
            DirectQuoteScore - Calculate direct quote accuracy score from range of 0 to 100 on the following  text:

            $directQuoteToEvaluate

            DirectQuoteExplanation - This the explanation on how DirectQuoteScore was derived.

            ContextScore - Calculate the contextual accuracy on the provided text:

            $contextToEvaluate

            ContextExplanation - This the explanation on how ContextScore was derived.

            Only respond in the following JSON format with no other text:
            {
             "DirectQuoteScore" : integer between 0 to 100,
             "ContextScore" : integer between 0 to 100,
             "DirectQuoteExplanation": "This is sample text"
             "ContextExplanation": "This is sample text"
            }
            """
            Log.d("GeminiAIService", "Sending memorized score prompt to Gemini: \"$prompt\"")

            val response: GenerateContentResponse = scoreModel.generateContent(prompt)
            val responseText = (response.text)?.trimIndent()

            Log.d("GeminiAIService", "Gemini Response: $responseText")

            if (responseText != null) {
                val cleanedJson = responseText.replace("```json", "").replace("```", "").trim()
                val parseResponse = jsonParser.decodeFromString<ScoreData>(cleanedJson)

                AiServiceResult.Success(parseResponse)
            } else {
                AiServiceResult.Error("Received empty response from AI.")
            }
        } catch (e: Exception) {
            Log.e("GeminiAIService", "Error calling Gemini API: ${e.message}", e)
            AiServiceResult.Error("Could not get AI score from AI (${e.javaClass.simpleName}).", e)
        }
    }

    /**
     * Evaluates a given take-away text for a specific verse to determine if it is acceptable.
     * This function uses a separate Gemini model configured with an evaluator system prompt.
     *
     * @param verseRef The Bible verse reference (e.g., "John 3:16") that the takeaway is for.
     * @param takeawayToEvaluate The string text of the take-away to be evaluated.
     * @return [AiServiceResult.Success] with a Boolean indicating if the takeaway is acceptable,
     * or [AiServiceResult.Error] if an issue occurs.
     */
    suspend fun validateKeyTakeawayResponse(verseRef: String, takeawayToEvaluate: String): AiServiceResult<Boolean> { // MODIFIED signature
        if (!isConfigured || currentAISettings == null) {
            return AiServiceResult.Error("GeminiAIService has not been configured.")
        }
        if (generativeModel == null) {
            return AiServiceResult.Error(initializationErrorMessage ?: "Gemini model not initialized or API key missing.")
        }

        try {
            // System prompt for the evaluator model
            val evaluatorSystemPrompt = content(role = "system") {
                text("You are an evaluator that decides whether a take-away answer is acceptable or not based on its alignment with core Judeo-Christian values and theological accuracy for the specific verse provided.")
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

            val prompt = """
            Please evaluate the following take-away text for $verseRef.
            Take-away: "$takeawayToEvaluate"

            Respond in the following JSON format:
            {
              "is_acceptable": boolean,
              "feedback": "Your brief feedback on the evaluation, considering the context of the verse."
            }
            """.trimIndent()

            Log.d("GeminiAIService", "Sending evaluation prompt to Gemini: \"$prompt\"")

            val response = evaluatorModel.generateContent(prompt)
            val responseText = response.text

            Log.d("GeminiAIService", "Gemini Evaluation Response: $responseText")

            if (responseText != null) {
                val cleanedJson = responseText.replace("```json", "").replace("```", "").trim()
                val parsedResponse = jsonParser.decodeFromString<EvaluationResponse>(cleanedJson)

                // Return just the boolean 'is_acceptable' value
                return AiServiceResult.Success(parsedResponse.is_acceptable)
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