package com.darblee.livingword.data.remote

import android.util.Log
import com.darblee.livingword.AIServiceConfig
import com.darblee.livingword.AIServiceType
import com.darblee.livingword.data.BibleVerseRef
import com.darblee.livingword.data.Verse
import com.darblee.livingword.ui.viewmodels.ScoreData
import kotlinx.coroutines.withTimeoutOrNull

/**
 * AIServiceProvider implementation that delegates to [GeminiAIService] for all AI operations.
 * Wraps each call with a 10-second timeout and translates exceptions into [AiServiceResult.Error].
 */
class GeminiAIServiceProvider : AIServiceProvider {
    
    override val providerId: String = "gemini_ai"
    override val displayName: String = "Gemini AI"
    override val serviceType: AIServiceType = AIServiceType.GEMINI
    override val defaultModel: String = AIServiceType.GEMINI.defaultModel
    override val priority: Int = 3
    
    private var currentConfig: AIServiceConfig? = null
    private var initializationError: String? = null
    
    /**
     * Builds the legacy [com.darblee.livingword.AISettings] structure required by [GeminiAIService] and applies it.
     *
     * @param config Provider-agnostic configuration containing the API key, model name, and temperature.
     * @return `true` if [GeminiAIService] reports successful initialization after configuration; `false` on any exception.
     */
    override fun configure(config: AIServiceConfig): Boolean {
        return try {
            Log.d("GeminiAIServiceProvider", "Received config - ServiceType: ${config.serviceType}, Model: ${config.modelName}, ApiKey length: ${config.apiKey.length}")
            currentConfig = config
            
            // Create AISettings for backward compatibility with existing GeminiAIService
            val geminiDynamicConfig = com.darblee.livingword.DynamicAIConfig(
                providerId = "gemini_ai",
                displayName = "Gemini AI",
                serviceType = config.serviceType,
                modelName = config.modelName,
                apiKey = config.apiKey,
                temperature = config.temperature,
                isEnabled = true
            )
            
            val legacySettings = com.darblee.livingword.AISettings(
                selectedService = AIServiceType.GEMINI,
                selectedProviderId = "gemini_ai",
                dynamicConfigs = mapOf("gemini_ai" to geminiDynamicConfig)
            )
            
            GeminiAIService.configure(legacySettings)
            initializationError = null
            
            val success = GeminiAIService.isInitialized()
            Log.d("GeminiAIServiceProvider", "Configuration result: $success")
            success
            
        } catch (e: Exception) {
            initializationError = "Failed to configure Gemini AI: ${e.message}"
            Log.e("GeminiAIServiceProvider", "Configuration failed", e)
            false
        }
    }
    
    /** Delegates initialization check directly to [GeminiAIService]. */
    override fun isInitialized(): Boolean {
        return GeminiAIService.isInitialized()
    }
    
    /** Sends a lightweight connectivity test to Gemini. Returns false if the service is unreachable or throws. */
    override suspend fun test(): Boolean {
        return try {
            Log.d("GeminiAIServiceProvider", "Running test")
            GeminiAIService.test()
        } catch (e: Exception) {
            Log.e("GeminiAIServiceProvider", "Test failed", e)
            false
        }
    }
    
    /**
     * Fetches scripture verses for a given reference via Gemini, timing out after 10 seconds.
     *
     * @param verseRef The book, chapter, and verse range to retrieve.
     * @param translation Bible translation code (e.g. "ESV") passed to the AI prompt.
     * @param systemInstruction System-role prompt that sets the AI's behaviour and output format.
     * @param userPrompt User-role prompt containing the specific fetch request.
     * @return [AiServiceResult.Success] with the parsed verse list, or [AiServiceResult.Error] on timeout or failure.
     */
    override suspend fun fetchScripture(
        verseRef: BibleVerseRef,
        translation: String,
        systemInstruction: String,
        userPrompt: String
    ): AiServiceResult<List<Verse>> {
        return try {
            if (!isInitialized()) {
                AiServiceResult.Error("Gemini AI provider not initialized")
            } else {
                withTimeoutOrNull(10000L) {
                    GeminiAIService.fetchScripture(verseRef, translation, systemInstruction, userPrompt)
                } ?: AiServiceResult.Error("Gemini AI error: Timed out waiting for 10000 ms")
            }
        } catch (e: Exception) {
            Log.e("GeminiAIServiceProvider", "fetchScripture failed", e)
            AiServiceResult.Error("Gemini AI error: ${e.message}", e)
        }
    }
    
    /**
     * Requests a key takeaway summary for a scripture verse via Gemini, timing out after 10 seconds.
     *
     * @param verseRef Human-readable verse reference string (e.g. "John 3:16").
     * @param systemInstruction System-role prompt that sets the AI's behaviour and output format.
     * @param userPrompt User-role prompt specifying what takeaway to generate.
     * @return [AiServiceResult.Success] with the takeaway text, or [AiServiceResult.Error] on timeout or failure.
     */
    override suspend fun getKeyTakeaway(
        verseRef: String,
        systemInstruction: String,
        userPrompt: String
    ): AiServiceResult<String> {
        return try {
            if (!isInitialized()) {
                AiServiceResult.Error("Gemini AI provider not initialized")
            } else {
                withTimeoutOrNull(10000L) {
                    GeminiAIService.getKeyTakeaway(verseRef, systemInstruction, userPrompt)
                } ?: AiServiceResult.Error("Gemini AI error: Timed out waiting for 10000 ms")
            }
        } catch (e: Exception) {
            Log.e("GeminiAIServiceProvider", "getKeyTakeaway failed", e)
            AiServiceResult.Error("Gemini AI error: ${e.message}", e)
        }
    }
    
    /**
     * Scores the user's application comment against a scripture verse via Gemini, timing out after 10 seconds.
     *
     * @param verseRef Human-readable verse reference string (e.g. "Romans 8:28").
     * @param userApplicationComment The user's written reflection or application of the verse being evaluated.
     * @param systemInstruction System-role prompt that sets the AI's scoring behaviour and output format.
     * @param userPrompt User-role prompt containing the scoring request.
     * @param applicationFeedbackPrompt Additional prompt used to request written feedback alongside the score.
     * @return [AiServiceResult.Success] with a [ScoreData] containing the numeric score and feedback, or [AiServiceResult.Error] on timeout or failure.
     */
    override suspend fun getAIScore(
        verseRef: String,
        userApplicationComment: String,
        systemInstruction: String,
        userPrompt: String,
        applicationFeedbackPrompt: String
    ): AiServiceResult<ScoreData> {
        return try {
            if (!isInitialized()) {
                AiServiceResult.Error("Gemini AI provider not initialized")
            } else {
                withTimeoutOrNull(10000L) {
                    GeminiAIService.getAIScore(verseRef, userApplicationComment, systemInstruction, userPrompt, applicationFeedbackPrompt)
                } ?: AiServiceResult.Error("Gemini AI error: Timed out waiting for 10000 ms")
            }
        } catch (e: Exception) {
            Log.e("GeminiAIServiceProvider", "getAIScore failed", e)
            AiServiceResult.Error("Gemini AI error: ${e.message}", e)
        }
    }
    
    /**
     * Asks Gemini to validate whether a previously generated key takeaway response is acceptable, timing out after 10 seconds.
     *
     * @param systemInstruction System-role prompt that sets the AI's validation criteria and output format.
     * @param userPrompt User-role prompt containing the takeaway text to be validated.
     * @return [AiServiceResult.Success] with `true` if the response is valid, `false` if not, or [AiServiceResult.Error] on timeout or failure.
     */
    override suspend fun validateKeyTakeawayResponse(
        systemInstruction: String,
        userPrompt: String
    ): AiServiceResult<Boolean> {
        return try {
            if (!isInitialized()) {
                AiServiceResult.Error("Gemini AI provider not initialized")
            } else {
                withTimeoutOrNull(10000L) {
                    GeminiAIService.validateKeyTakeawayResponse(systemInstruction, userPrompt)
                } ?: AiServiceResult.Error("Gemini AI error: Timed out waiting for 10000 ms")
            }
        } catch (e: Exception) {
            Log.e("GeminiAIServiceProvider", "validateKeyTakeawayResponse failed", e)
            AiServiceResult.Error("Gemini AI error: ${e.message}", e)
        }
    }
    
    /**
     * Asks Gemini to suggest new scripture verses that match a user-supplied description, timing out after 10 seconds.
     *
     * @param description Free-text description of the topic, theme, or concept to find verses for.
     * @param systemInstruction System-role prompt that sets the AI's search behaviour and output format.
     * @param userPrompt User-role prompt containing the verse discovery request.
     * @return [AiServiceResult.Success] with a list of matching [BibleVerseRef] objects, or [AiServiceResult.Error] on timeout or failure.
     */
    override suspend fun getNewVersesBasedOnDescription(
        description: String,
        systemInstruction: String,
        userPrompt: String
    ): AiServiceResult<List<BibleVerseRef>> {
        return try {
            if (!isInitialized()) {
                AiServiceResult.Error("Gemini AI provider not initialized")
            } else {
                withTimeoutOrNull(10000L) {
                    GeminiAIService.getNewVersesBasedOnDescription(description, systemInstruction, userPrompt)
                } ?: AiServiceResult.Error("Gemini AI error: Timed out waiting for 10000 ms")
            }
        } catch (e: Exception) {
            Log.e("GeminiAIServiceProvider", "getNewVersesBasedOnDescription failed", e)
            AiServiceResult.Error("Gemini AI error: ${e.message}", e)
        }
    }
    
    /** Returns the most recent configuration error message, checking the local field first then falling back to [GeminiAIService]. */
    override fun getInitializationError(): String? {
        return initializationError ?: GeminiAIService.getInitializationError()
    }
}