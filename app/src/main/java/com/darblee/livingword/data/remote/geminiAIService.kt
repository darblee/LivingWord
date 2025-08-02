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
            text("You are a scripture retrieval assistant. Your task is to provide the scripture for a given verse in a specified translation. You must respond in JSON format as requested by the user")
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
            val verseString = if (verseRef.startVerse == verseRef.endVerse) {
                "${verseRef.book} ${verseRef.chapter}:${verseRef.startVerse}"
            } else {
                "${verseRef.book} ${verseRef.chapter}:${verseRef.startVerse}-${verseRef.endVerse}"
            }

            val prompt = if (verseRef.startVerse == verseRef.endVerse) {
                """
                 You will be provided with the verse string and the translation.
                
                Verse String: $verseString
                Translation: $translation
                
                Respond in the following JSON schema:
                ```json
                [
                  {
                    "verse_num": {$verseRef.startVerse},
                    "verse_string": "String content of the verse."
                  }
                ]
                ``` 
                """.trimIndent()
            } else {
                """
                Retrieve the scripture for the range of verses $verseString from the $translation translation.
                Your sole function is to return the content of those verses in the following JSON schema, which is an array of verse objects:
                ```json
                [
                  {
                    "verse_num": ${verseRef.startVerse},
                    "verse_string": "The content of the start verse."
                  },
                  {
                    "verse_num": {$verseRef.startVerse + 1},
                    "verse_string": "The content of the next verse."
                  }
                ]
                ``` 
                """.trimIndent()
            }

            Log.d("GeminiAIService", "Sending prompt to Gemini for scripture range: \"$prompt\"")

            val response = retrieveScriptureModel.generateContent(prompt)

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
                text("You are a scripture expert, skilled at extracting key takeaways from religious texts. Your task is to analyze a given verse reference and provide the key takeaway in a JSON format.")
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

            val prompt = """
            Analyze the following verse reference:
            $verseRef
            
            Provide the key takeaway from this verse. Your response must be in the following JSON format:
            
            ```json
            {
              "key_takeaway_text": "The key takeaway.",
              "sources": [ "source1", "source2" ]
            }
            ```
            
            *   `key_takeaway_text`: A concise summary of the main lesson or insight from the verse.
            *   `sources`: A list of any resources (e.g., commentaries, scholarly articles) you consulted to determine the key takeaway. If no external sources were used, list "None". 
        """.trimIndent()
            Log.d("GeminiAIService", "Sending prompt to Gemini: \"$prompt\"")

            var takeAwayResponseText: String? = ""
            var attempts = 0
            while (attempts < 3) {
                val response: GenerateContentResponse = takeAwayModel.generateContent(prompt)
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
     * @param directQuoteToEvaluate for the Direct Quote text.
     * @return [AiServiceResult.Success] with the take-away text, or [AiServiceResult.Error] if an issue occurs.
     */
    suspend fun getAIScore(verseRef: String, directQuoteToEvaluate: String, userApplicationComment: String): AiServiceResult<ScoreData> {
        if (!isConfigured) {
            return AiServiceResult.Error("GeminiAIService has not been configured.")
        }
        if (generativeModel == null) {
            return AiServiceResult.Error(initializationErrorMessage ?: "Gemini model not initialized or API key missing.")
        }

        return try {
            // System prompt for the evaluator model
            val scoreSystemPrompt = content(role = "system") {
                text("You are an expert in theology, You are an expert in analyzing Bible verses and determining the accuracy of direct quotes and their context. ")
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

            val prompt = """
             You will be provided with a Bible verse reference and a direct quote to evaluate.

            Bible verse reference: $verseRef
            Direct quote to evaluate: $directQuoteToEvaluate
            
            Follow these steps:
            
            1.  Calculate the `DirectQuoteScore`: Determine the accuracy of the direct quote compared to the Bible verse reference. Provide a score between 0 and 100.
            2.  Calculate the `ContextScore`: Evaluate the contextual accuracy of the direct quote. Consider whether the quote is used in a way that aligns with the original meaning and intent of the verse. Provide a score between 0 and 100.
            3.  Provide `DirectQuoteExplanation`: Explain how you derived the `DirectQuoteScore`. Detail any differences between the provided quote and the actual verse.
            4.  Provide `ContextExplanation`: Explain how you derived the `ContextScore`. Discuss the context of the verse and whether the provided quote aligns with that context.
            5.  Create `ApplicationFeedback`: Create this field with an empty string: "". This field is not used in this task.
            
            Respond ONLY in the following JSON format:
            
            ```json
            {
            "DirectQuoteScore": integer between 0 to 100,
            "ContextScore": integer between 0 to 100,
            "DirectQuoteExplanation": "Explanation of the DirectQuoteScore",
            "ContextExplanation": "Explanation of the ContextScore",
            "ApplicationFeedback": ""
            }
            ```
            
            Ensure that your response is ONLY in JSON format with no other text or explanations outside of the JSON structure. 
            """
            Log.d("GeminiAIService", "Sending memorized score prompt to Gemini: \"$prompt\"")

            val response: GenerateContentResponse = scoreModel.generateContent(prompt)
            val responseText = (response.text)?.trimIndent()

            Log.d("GeminiAIService", "Gemini Response: $responseText")

            var applicationFeedback: String? = null
            var attempts = 0
            while (attempts < 3) {
                val applicationFeedbackResponseText =
                    getApplicationFeedback(verseRef, userApplicationComment)
                applicationFeedback = applicationFeedbackResponseText.trimIndent()
                if (applicationFeedback.isNotEmpty()) {
                    break
                }
                attempts++
            }

            if (responseText != null) {
                val cleanedJson = responseText.replace("```json", "").replace("```", "").trim()
                val parseResponse = jsonParser.decodeFromString<ScoreData>(cleanedJson)
                if (applicationFeedback != null) {
                    parseResponse.ApplicationFeedback = applicationFeedback
                }

                AiServiceResult.Success(parseResponse)
            } else {
                AiServiceResult.Error("Received empty response from AI.")
            }
        } catch (e: Exception) {
            Log.e("GeminiAIService", "Error calling Gemini API: ${e.message}", e)
            AiServiceResult.Error("Could not get AI score from AI (${e.javaClass.simpleName}).", e)
        }
    }


    suspend fun getApplicationFeedback(verseRef: String, userApplicationComment: String): String {
        if (!isConfigured) {
            return ""
        }
        if (generativeModel == null) {
            return ""
        }

        return try {
            // System prompt for the evaluator model
            val scoreSystemPrompt = content(role = "system") {
                text("You are a knowledgeable theologian and biblical scholar, skilled in providing constructive feedback on the application of Bible verses in daily life. Your feedback should be insightful, encouraging, and theologically sound.")}

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

            val prompt = """
            You will be provided with a Bible verse and a user's comment on how they are applying the scripture. Your task is to provide feedback on the user's application.
            
            Here is the Bible verse:
            $verseRef
            
            Here is the user's comment on how they are applying the scripture:
            $userApplicationComment
            
            Provide feedback that is:
            *   Insightful: Offer a deeper understanding of the verse and its implications.
            *   Encouraging: Affirm the user's efforts and provide motivation.
            *   Theologically Sound: Ensure the application aligns with biblical principles. 
            """
            Log.d("GeminiAIService", "Sending user application prompt to Gemini: \"$prompt\"")

            var applicationFeedback: String? = null
            var attempts = 0
            while (attempts < 3) {
                val response: GenerateContentResponse = scoreModel.generateContent(prompt)
                applicationFeedback = (response.text)?.trimIndent()
                if (!applicationFeedback.isNullOrEmpty()) {
                    break
                }
                attempts++
            }

            Log.d("GeminiAIService", "Gemini Response: $applicationFeedback")

            applicationFeedback ?: ""
        } catch (e: Exception) {
            Log.e("GeminiAIService", "Error calling Gemini API: ${e.message}", e)
            ""
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
                text("You are a theological text evaluator. You will be given a verse reference and a take-away text. Your task is to evaluate the take-away text in the context of the verse and respond in JSON format.")
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
            Please evaluate the following take-away text for ${verseRef}:
            Take-away: "$takeawayToEvaluate"
            
            Respond in the following JSON format:
            ```json
            {
              "is_acceptable": boolean,
              "feedback": "Your brief feedback on the evaluation, considering the context of the verse."
            }
            ```
            Consider the context of the verse when evaluating the take-away text. 
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

        return try {
            // System prompt for the evaluator model
            val systemPrompt = content(role = "system") {
                text("You are a knowledgeable assistant specialized in biblical scripture. Your task is to identify and list verses that match a given description. You must respond in a specific JSON format.")
            }

            val getCorrespondingVersesGenerativeModel = GenerativeModel(
                modelName =   currentAISettings!!.modelName,
                apiKey = currentAISettings!!.apiKey,
                generationConfig = generationConfig {
                    temperature = 0.3f // Lower temperature for more deterministic evaluation
                },
                systemInstruction = systemPrompt
            )

            val prompt = """
            You will be provided with a description of verses:
            $description
            
            Based on the description, find the corresponding verses and respond in the following JSON format:
            
            *   If no verses are found, return an empty array `[]`.
            *   If verses are found, return an array of verse reference objects.
            
            Here is the JSON format:
            
            ```json
            [
              {
                "book": "BookName",
                "chapter": 1,
                "startVerse": 1,
                "endVerse": 2
              }
            ]
            ```
            
            Example:
            
            If the description is "verses about love in the New Testament", the response might be:
            
            ```json
            [
              {
                "book": "1 Corinthians",
                "chapter": 13,
                "startVerse": 4,
                "endVerse": 8
              },
              {
                "book": "John",
                "chapter": 3,
                "startVerse": 16,
                "endVerse": 16
              }
            ]
            ``` 
            """.trimIndent()

            Log.d("GeminiAIService", "Sending prompt to Gemini for description-based verses: \"$prompt\"")

            val response = getCorrespondingVersesGenerativeModel.generateContent(prompt)
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